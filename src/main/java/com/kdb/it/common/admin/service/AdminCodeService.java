package com.kdb.it.common.admin.service;

import com.kdb.it.common.admin.dto.AdminDto;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 공통코드(TPRMPP_CCODEM) 관리 서비스
 *
 * <p>{@code AdminService}에서 공통코드 CRUD만 분리한 서비스입니다. 분리 이유는 두 가지입니다.
 *
 * <ul>
 *   <li>캐시 무효화 책임 명확화 — 이 서비스의 쓰기 경로는 {@code CodeService}와 같은 {@link CodeRepository}를 사용하므로 {@code
 *       codesByCid}·{@code budgetPeriod} 캐시를 반드시 무효화해야 합니다. 무효화 책임을 한 클래스에 모아 누락을 막습니다.
 *   <li>CQ-01 동결선 상쇄 — 캐시 무효화 애노테이션 추가로 늘어난 {@code AdminService} 분량을 기준값 상향이 아니라 동등 이상 분량 추출로
 *       상쇄합니다.
 * </ul>
 *
 * <p>의존 패키지:
 *
 * <ul>
 *   <li>{@code common/code} — 공통코드(Ccodem, CodeRepository)
 *   <li>{@code common/iam} — 사용자(UserRepository) — 감사 필드 이름 변환용
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCodeService {

    private final CodeRepository codeRepository;
    private final UserRepository userRepository;

    /**
     * 삭제되지 않은 전체 공통코드 목록을 조회합니다. 최초생성자·마지막수정자 사원번호를 이름으로 일괄 변환하여 반환합니다.
     *
     * @return 공통코드 응답 DTO 목록
     */
    public List<AdminDto.CodeResponse> getCodes() {
        List<Ccodem> codes = codeRepository.findAllActive();

        // 감사 필드의 고유 ENO를 한 번의 배치 쿼리로 이름 조회 (N+1 방지)
        Set<String> enos =
                codes.stream()
                        .flatMap(c -> Stream.of(c.getFstEnrUsid(), c.getLstChgUsid()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Map<String, String> userNameMap =
                userRepository.findNameViewsByEnoIn(enos).stream()
                        .collect(Collectors.toMap(u -> u.getEno(), u -> u.getUsrNm()));

        return codes.stream().map(c -> toCodeResponse(c, userNameMap)).toList();
    }

    /**
     * 신규 공통코드를 추가합니다. C_ID와 시작일자가 모두 중복되면 예외를 발생시킵니다.
     *
     * @param req 공통코드 생성 요청 DTO
     * @throws IllegalArgumentException 코드ID/시작일자 중복 시
     */
    @Transactional
    @Caching(
            evict = {
                @CacheEvict(value = "budgetPeriod", allEntries = true),
                @CacheEvict(value = "codesByCid", allEntries = true)
            })
    public void createCode(AdminDto.CodeRequest req) {
        validateCodeKey(req.cId(), req.cdva(), req.sttDt());
        if (codeRepository.existsByCIdAndCdvaAndSttDt(req.cId(), req.cdva(), req.sttDt())) {
            throw new IllegalArgumentException(
                    "이미 존재하는 코드입니다: " + req.cId() + "/" + req.cdva() + ", " + req.sttDt());
        }
        Ccodem code =
                Ccodem.builder()
                        .cId(req.cId())
                        .cNm(req.cNm())
                        .cdvaNm(req.cdvaNm())
                        .cdva(req.cdva())
                        .cdvaDes(req.cdvaDes())
                        .cdvaDtl(req.cdvaDtl())
                        .cdvaDtlC(req.cdvaDtlC())
                        .cTp(req.cTp())
                        .cTpDes(req.cTpDes())
                        .hrkC(req.hrkC())
                        .sttDt(req.sttDt())
                        .endDt(req.endDt())
                        .cSqn(req.cSqn())
                        .build();
        codeRepository.save(code);
    }

    /**
     * 공통코드 정보를 수정합니다.
     *
     * <p>요청의 PK(cId/cdva/sttDt)가 path PK와 동일하면 Dirty Checking으로 비PK 필드만 갱신합니다. PK가 다르면 PK rename 으로
     * 간주하여 다음 절차로 처리합니다:
     *
     * <ol>
     *   <li>새 PK 충돌 검증 — 동일 PK의 활성 행이 이미 있으면 거절
     *   <li>기존 행 soft delete ({@code DEL_YN='Y'})
     *   <li>새 PK + 새 비PK 값으로 신규 행 생성·저장
     * </ol>
     *
     * <p>두 단계 모두 {@code ChangeLogEntityListener}가 자동 기록하므로 변경 이력은 보존됩니다.
     *
     * @param cId path 원본 코드ID
     * @param cdva path 원본 코드값
     * @param sttDt path 원본 시작일자
     * @param req 공통코드 수정 요청 DTO (req 안의 PK는 새 값, path와 다르면 rename)
     * @throws IllegalArgumentException 원본 코드를 찾을 수 없거나, 새 PK가 이미 존재하는 경우
     */
    @Transactional
    @Caching(
            evict = {
                @CacheEvict(value = "budgetPeriod", allEntries = true),
                @CacheEvict(value = "codesByCid", allEntries = true)
            })
    public void updateCode(String cId, String cdva, String sttDt, AdminDto.CodeRequest req) {
        validateCodeKey(cId, cdva, sttDt);
        Ccodem code =
                codeRepository
                        .findByCIdAndCdvaAndSttDtAndDelYn(cId, cdva, sttDt, "N")
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "존재하지 않는 코드입니다: "
                                                        + cId
                                                        + "/"
                                                        + cdva
                                                        + ", "
                                                        + sttDt));

        // 요청 PK 결정: req에 값이 있으면 새 PK, 없으면 path PK 유지
        String newCId = (req.cId() != null && !req.cId().isBlank()) ? req.cId() : cId;
        String newCdva = (req.cdva() != null && !req.cdva().isBlank()) ? req.cdva() : cdva;
        String newSttDt = (req.sttDt() != null && !req.sttDt().isBlank()) ? req.sttDt() : sttDt;

        boolean pkChanged =
                !Objects.equals(newCId, cId)
                        || !Objects.equals(newCdva, cdva)
                        || !Objects.equals(newSttDt, sttDt);

        if (!pkChanged) {
            // PK 동일 — 기존 setter 기반 update (Dirty Checking)
            code.update(
                    req.cNm(),
                    req.cdvaDes(),
                    req.cdvaDtl(),
                    req.cdvaNm(),
                    req.cTp(),
                    req.cTpDes(),
                    req.hrkC(),
                    req.cSqn(),
                    req.endDt(),
                    req.cdvaDtlC());
            return;
        }

        // PK rename — 새 PK 충돌 검증
        validateCodeKey(newCId, newCdva, newSttDt);
        if (codeRepository.existsByCIdAndCdvaAndSttDt(newCId, newCdva, newSttDt)) {
            throw new IllegalArgumentException(
                    "이미 존재하는 코드입니다: " + newCId + "/" + newCdva + ", " + newSttDt);
        }

        // 기존 행 soft delete
        code.delete();

        // 새 PK로 신규 행 생성·저장
        Ccodem renamed =
                Ccodem.builder()
                        .cId(newCId)
                        .cdva(newCdva)
                        .sttDt(newSttDt)
                        .cNm(req.cNm())
                        .cdvaNm(req.cdvaNm())
                        .cdvaDes(req.cdvaDes())
                        .cdvaDtl(req.cdvaDtl())
                        .cdvaDtlC(req.cdvaDtlC())
                        .cTp(req.cTp())
                        .cTpDes(req.cTpDes())
                        .hrkC(req.hrkC())
                        .endDt(req.endDt())
                        .cSqn(req.cSqn())
                        .build();
        codeRepository.save(renamed);
    }

    /**
     * 공통코드를 논리 삭제(Soft Delete)합니다. DEL_YN='Y' 처리 — 물리 삭제 금지.
     *
     * @param cId 코드ID
     * @param cdva 코드값
     * @param sttDt 시작일자
     * @throws IllegalArgumentException 코드를 찾을 수 없는 경우
     */
    @Transactional
    @Caching(
            evict = {
                @CacheEvict(value = "budgetPeriod", allEntries = true),
                @CacheEvict(value = "codesByCid", allEntries = true)
            })
    public void deleteCode(String cId, String cdva, String sttDt) {
        validateCodeKey(cId, cdva, sttDt);
        Ccodem code =
                codeRepository
                        .findByCIdAndCdvaAndSttDtAndDelYn(cId, cdva, sttDt, "N")
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "존재하지 않는 코드입니다: "
                                                        + cId
                                                        + "/"
                                                        + cdva
                                                        + ", "
                                                        + sttDt));
        code.delete();
    }

    /**
     * 공통코드 일괄 업로드(Upsert) 처리합니다. 코드ID가 이미 존재하면 수정, 없으면 신규 생성합니다.
     *
     * @param req 일괄 업로드 요청 DTO (코드 목록)
     * @return 처리 결과 (created: 신규 건수, updated: 수정 건수)
     */
    @Transactional
    @Caching(
            evict = {
                @CacheEvict(value = "budgetPeriod", allEntries = true),
                @CacheEvict(value = "codesByCid", allEntries = true)
            })
    public Map<String, Integer> bulkUpsertCodes(AdminDto.BulkCodeRequest req) {
        for (AdminDto.CodeRequest item : req.codes()) {
            validateCodeKey(item.cId(), item.cdva(), item.sttDt());
        }

        Set<String> cIds =
                req.codes().stream().map(AdminDto.CodeRequest::cId).collect(Collectors.toSet());
        // 논리삭제된 행까지 읽는다. 활성 행만 보면 삭제된 같은 복합키를 신규로 오인해 merge(UPDATE) 경로로
        // 들어가고, 새 엔티티에는 GUID가 없어 NULL이 나가 NOT NULL 제약(ORA-01407)에 걸린다.
        Map<CodeKey, Ccodem> byKey = new LinkedHashMap<>();
        for (Ccodem code : codeRepository.findAllByCIdIn(cIds)) {
            byKey.put(new CodeKey(code.getCId(), code.getCdva(), code.getSttDt()), code);
        }

        int created = 0;
        int updated = 0;
        List<Ccodem> newCodes = new ArrayList<>();
        for (AdminDto.CodeRequest item : req.codes()) {
            CodeKey key = new CodeKey(item.cId(), item.cdva(), item.sttDt());
            Ccodem existing = byKey.get(key);
            if (existing != null) {
                // 삭제되어 있던 코드를 다시 올린 것은 사용자에게 신규 등록이므로 created로 센다.
                boolean revived = "Y".equals(existing.getDelYn());
                if (revived) {
                    existing.restore();
                }
                existing.update(
                        item.cNm(),
                        item.cdvaDes(),
                        item.cdvaDtl(),
                        item.cdvaNm(),
                        item.cTp(),
                        item.cTpDes(),
                        item.hrkC(),
                        item.cSqn(),
                        item.endDt(),
                        item.cdvaDtlC());
                if (revived) {
                    created++;
                } else {
                    updated++;
                }
            } else {
                Ccodem code =
                        Ccodem.builder()
                                .cId(item.cId())
                                .cNm(item.cNm())
                                .cdvaNm(item.cdvaNm())
                                .cdva(item.cdva())
                                .cdvaDes(item.cdvaDes())
                                .cdvaDtl(item.cdvaDtl())
                                .cdvaDtlC(item.cdvaDtlC())
                                .cTp(item.cTp())
                                .cTpDes(item.cTpDes())
                                .hrkC(item.hrkC())
                                .sttDt(item.sttDt())
                                .endDt(item.endDt())
                                .cSqn(item.cSqn())
                                .build();
                newCodes.add(code);
                byKey.put(key, code);
                created++;
            }
        }
        codeRepository.saveAll(newCodes);
        return Map.of("created", created, "updated", updated);
    }

    private record CodeKey(String cId, String cdva, String sttDt) {}

    /** 공통코드 복합키 필수값을 검증합니다. */
    private void validateCodeKey(String cId, String cdva, String sttDt) {
        if (cId == null || cId.isBlank()) {
            throw new IllegalArgumentException("코드ID는 필수입니다.");
        }
        if (cdva == null || cdva.isBlank()) {
            throw new IllegalArgumentException("코드값은 필수입니다.");
        }
        if (sttDt == null || sttDt.isBlank()) {
            throw new IllegalArgumentException("시작일자는 필수입니다.");
        }
    }

    /**
     * Ccodem 엔티티를 CodeResponse DTO로 변환합니다.
     *
     * @param c 공통코드 엔티티
     * @param userNameMap ENO → 사용자명 매핑 (배치 조회 결과)
     */
    private AdminDto.CodeResponse toCodeResponse(Ccodem c, Map<String, String> userNameMap) {
        return new AdminDto.CodeResponse(
                c.getCId(),
                c.getCdva(),
                c.getCNm(),
                c.getCdvaNm(),
                c.getCdvaDes(),
                c.getCdvaDtl(),
                c.getCdvaDtlC(),
                c.getCTp(),
                c.getCTpDes(),
                c.getHrkC(),
                c.getSttDt(),
                c.getEndDt(),
                c.getCSqn(),
                c.getFstEnrDtm(),
                c.getFstEnrUsid(),
                userNameMap.getOrDefault(c.getFstEnrUsid(), c.getFstEnrUsid()),
                c.getLstChgDtm(),
                c.getLstChgUsid(),
                userNameMap.getOrDefault(c.getLstChgUsid(), c.getLstChgUsid()));
    }
}
