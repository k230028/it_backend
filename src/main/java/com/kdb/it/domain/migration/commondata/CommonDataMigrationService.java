package com.kdb.it.domain.migration.commondata;

import com.kdb.it.common.admin.dto.AdminDto;
import com.kdb.it.common.admin.service.AdminCodeService;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.i18n.entity.Clangm;
import com.kdb.it.common.i18n.repository.ClangmRepository;
import com.kdb.it.common.iam.repository.AuthRepository;
import com.kdb.it.domain.menu.entity.Cmenua;
import com.kdb.it.domain.menu.entity.Cmenud;
import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.domain.menu.repository.CmenuaRepository;
import com.kdb.it.domain.menu.repository.CmenudRepository;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import com.kdb.it.domain.migration.commondata.dto.CommonDataMigrationDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공통 데이터 이관 업로드를 검증(dry-run)하고 단일 트랜잭션으로 업서트(commit)합니다.
 *
 * <p>반영 순서는 참조 무결성을 따릅니다: 경로 → 메뉴 → 메뉴권한 → 공통코드 → 다국어. 모든 쓰기는 JPA 엔티티 경유이므로 {@code @LogTarget}
 * 변경로그와 BaseEntity 감사 컬럼이 자동으로 채워집니다.
 */
@Service
@RequiredArgsConstructor
public class CommonDataMigrationService {

    /** 다국어 대상키 IN 조회 청크 크기. Oracle IN 1000개 제한(ORA-01795) 회피용. */
    private static final int KEY_CHUNK_SIZE = 900;

    private final CommonDataMigrationPlanner planner;
    private final CmenumRepository cmenumRepository;
    private final CmenuaRepository cmenuaRepository;
    private final CmenudRepository cmenudRepository;
    private final CodeRepository codeRepository;
    private final ClangmRepository clangmRepository;
    private final AuthRepository authRepository;
    private final AdminCodeService adminCodeService;

    /** 저장 없이 검증·분류 요약만 반환합니다. */
    @Transactional(readOnly = true)
    public CommonDataMigrationDto.Response dryRun(CommonDataMigrationDto.Request request) {
        CommonDataMigrationPlanner.Plan plan = planner.plan(request, loadSnapshot(request));
        return new CommonDataMigrationDto.Response(
                false, plan.summaries(), plan.warnings(), plan.errors());
    }

    /**
     * dry-run과 같은 검증을 재수행한 뒤 5개 테이블을 하나의 트랜잭션으로 업서트합니다.
     *
     * @throws IllegalArgumentException 검증 오류가 1건이라도 있는 경우 (400)
     */
    @Transactional
    @CacheEvict(value = "menuAuthMap", allEntries = true)
    public CommonDataMigrationDto.Response commit(CommonDataMigrationDto.Request request) {
        CommonDataMigrationPlanner.Snapshot snapshot = loadSnapshot(request);
        CommonDataMigrationPlanner.Plan plan = planner.plan(request, snapshot);
        if (!plan.errors().isEmpty()) {
            throw new IllegalArgumentException(
                    "검증 오류가 있어 반영할 수 없습니다: " + String.join(" / ", plan.errors()));
        }
        applyRoutes(request.routes(), snapshot.allRoutes());
        applyMenus(request.menus(), snapshot.allMenus());
        applyMenuAuths(request.menuAuths(), snapshot.allMenuAuths());
        applyCodes(request.codes());
        applyTranslations(request.translations(), snapshot.translationsForFileKeys());
        return new CommonDataMigrationDto.Response(
                true, plan.summaries(), plan.warnings(), plan.errors());
    }

    /** 삭제 행 포함 현재 상태를 읽습니다. 메뉴·권한·경로는 소형 테이블이라 전량, 코드·번역은 파일 참조 키만 읽습니다. */
    private CommonDataMigrationPlanner.Snapshot loadSnapshot(
            CommonDataMigrationDto.Request request) {
        Set<String> fileCIds =
                request.codes().stream()
                        .map(CommonDataMigrationDto.CodeRow::cId)
                        .collect(Collectors.toSet());
        return new CommonDataMigrationPlanner.Snapshot(
                cmenumRepository.findAll(),
                cmenuaRepository.findAll(),
                cmenudRepository.findAll(),
                fileCIds.isEmpty() ? List.of() : loadCodesChunked(fileCIds),
                loadTranslationsChunked(request.translations()),
                authRepository.findAll().stream()
                        .filter(auth -> !"Y".equals(auth.getDelYn()))
                        .map(auth -> auth.getAthId())
                        .collect(Collectors.toSet()));
    }

    /** 공통코드 ID를 Oracle IN 절 제한보다 작은 청크로 나누어 읽습니다. */
    private List<com.kdb.it.common.code.entity.Ccodem> loadCodesChunked(Set<String> cIds) {
        List<com.kdb.it.common.code.entity.Ccodem> result = new ArrayList<>();
        List<String> keys = cIds.stream().toList();
        for (int i = 0; i < keys.size(); i += KEY_CHUNK_SIZE) {
            result.addAll(
                    codeRepository.findAllByCIdIn(
                            keys.subList(i, Math.min(i + KEY_CHUNK_SIZE, keys.size()))));
        }
        return result;
    }

    private List<Clangm> loadTranslationsChunked(List<CommonDataMigrationDto.TranslationRow> rows) {
        List<String> keys =
                rows.stream()
                        .map(CommonDataMigrationDto.TranslationRow::tcIdCone)
                        .distinct()
                        .toList();
        List<Clangm> result = new ArrayList<>();
        for (int i = 0; i < keys.size(); i += KEY_CHUNK_SIZE) {
            result.addAll(
                    clangmRepository.findAllByTcIdConeIn(
                            keys.subList(i, Math.min(i + KEY_CHUNK_SIZE, keys.size()))));
        }
        return result;
    }

    private void applyRoutes(List<CommonDataMigrationDto.RouteRow> rows, List<Cmenud> existing) {
        Map<String, Cmenud> byPath = new HashMap<>();
        existing.forEach(r -> byPath.put(r.getSrePth(), r));
        List<Cmenud> created = new ArrayList<>();
        for (CommonDataMigrationDto.RouteRow row : rows) {
            Cmenud found = byPath.get(row.srePth());
            if (found == null) {
                created.add(
                        Cmenud.builder()
                                .srePth(row.srePth())
                                .sreMnuNm(row.sreMnuNm())
                                .useYn(row.useYn())
                                .rmk(row.rmk())
                                .build());
            } else {
                found.updateForMigration(row.sreMnuNm(), row.useYn(), row.rmk());
                found.restore();
            }
        }
        cmenudRepository.saveAll(created);
    }

    private void applyMenus(List<CommonDataMigrationDto.MenuRow> rows, List<Cmenum> existing) {
        Map<String, Cmenum> byId = new HashMap<>();
        existing.forEach(m -> byId.put(m.getMnuId(), m));
        List<Cmenum> created = new ArrayList<>();
        for (CommonDataMigrationDto.MenuRow row : rows) {
            Cmenum found = byId.get(row.mnuId());
            if (found == null) {
                created.add(
                        Cmenum.builder()
                                .mnuId(row.mnuId())
                                .hrkMnuId(row.hrkMnuId())
                                .mnuNm(row.mnuNm())
                                .mnuTpC(row.mnuTpC())
                                .imkNm(row.imkNm())
                                .srePth(row.srePth())
                                .mnuSotSqnSno(row.mnuSotSqnSno())
                                .hidYn(row.hidYn())
                                .mnuDep(row.mnuDep())
                                .whlMnuPth(row.whlMnuPth())
                                .build());
            } else {
                // Cmenum은 @Setter가 열려 있는 기존 계약을 그대로 사용한다.
                found.setHrkMnuId(row.hrkMnuId());
                found.setMnuNm(row.mnuNm());
                found.setMnuTpC(row.mnuTpC());
                found.setImkNm(row.imkNm());
                found.setSrePth(row.srePth());
                found.setMnuSotSqnSno(row.mnuSotSqnSno());
                found.setHidYn(row.hidYn());
                found.setMnuDep(row.mnuDep());
                found.setWhlMnuPth(row.whlMnuPth());
                found.restore();
            }
        }
        cmenumRepository.saveAll(created);
    }

    private void applyMenuAuths(
            List<CommonDataMigrationDto.MenuAuthRow> rows, List<Cmenua> existing) {
        // 복합키를 구분자로 문자열 결합해 만들면 필드 경계가 다른 값끼리 같은 키로 뭉칠 위험이 있다.
        // 게다가 결합에 흔히 쓰는 제어문자 이스케이프를 소스에 직접 적으면 그 이스케이프 자체가 실제
        // 제어 바이트로 저장되는 사고가 날 수 있어(Task 2에서 실제 발생), 원소 단위 equals/hashCode를
        // 쓰는 Arrays.asList를 키로 쓴다.
        Map<List<String>, Cmenua> byKey = new HashMap<>();
        existing.forEach(a -> byKey.put(Arrays.asList(a.getMnuId(), a.getAthId()), a));
        List<Cmenua> created = new ArrayList<>();
        for (CommonDataMigrationDto.MenuAuthRow row : rows) {
            Cmenua found = byKey.get(Arrays.asList(row.mnuId(), row.athId()));
            if (found == null) {
                created.add(Cmenua.builder().mnuId(row.mnuId()).athId(row.athId()).build());
            } else {
                found.restore();
            }
        }
        cmenuaRepository.saveAll(created);
    }

    /** 공통코드는 기존 일괄 업서트를 재사용해 부활·GUID·코드캐시 evict 처리를 물려받습니다. */
    private void applyCodes(List<CommonDataMigrationDto.CodeRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<AdminDto.CodeRequest> codes =
                rows.stream()
                        .map(
                                row ->
                                        new AdminDto.CodeRequest(
                                                row.cId(),
                                                row.cdva(),
                                                row.cNm(),
                                                row.cdvaNm(),
                                                row.cdvaDes(),
                                                row.cdvaDtl(),
                                                row.cdvaDtlC(),
                                                row.cTp(),
                                                row.cTpDes(),
                                                row.hrkC(),
                                                row.sttDt(),
                                                row.endDt(),
                                                row.cSqn()))
                        .toList();
        adminCodeService.bulkUpsertCodes(new AdminDto.BulkCodeRequest(codes));
    }

    private void applyTranslations(
            List<CommonDataMigrationDto.TranslationRow> rows, List<Clangm> existing) {
        // 메뉴권한과 같은 이유로 복합키는 Arrays.asList를 쓴다.
        Map<List<String>, Clangm> byKey = new HashMap<>();
        existing.forEach(
                t -> byKey.put(Arrays.asList(t.getTcIdCone(), t.getTcColNm(), t.getDttLanC()), t));
        List<Clangm> created = new ArrayList<>();
        for (CommonDataMigrationDto.TranslationRow row : rows) {
            Clangm found = byKey.get(Arrays.asList(row.tcIdCone(), row.tcColNm(), row.dttLanC()));
            if (found == null) {
                created.add(
                        Clangm.builder()
                                .tcIdCone(row.tcIdCone())
                                .tcColNm(row.tcColNm())
                                .dttLanC(row.dttLanC())
                                .tcDes(row.tcDes())
                                .dttNm(row.dttNm())
                                .build());
            } else {
                // update()가 번역 문구 갱신과 논리삭제 복원을 함께 수행한다.
                found.update(row.tcDes());
            }
        }
        clangmRepository.saveAll(created);
    }
}
