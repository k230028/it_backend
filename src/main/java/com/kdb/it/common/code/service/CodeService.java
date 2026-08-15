package com.kdb.it.common.code.service;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.dto.CodeDto;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CcodemResponseRow;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.i18n.model.SupportedLanguage;
import com.kdb.it.common.i18n.model.TranslationTarget;
import com.kdb.it.common.i18n.service.TranslationCatalogService;
import com.kdb.it.common.i18n.service.TranslationTargetKey;
import com.kdb.it.exception.CustomGeneralException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공통코드(Ccodem) 서비스 클래스
 *
 * <p>캐시 전략: @Cacheable('codesByCid', 'budgetPeriod')로 정적 참조 데이터 캐시 적용.
 *
 * <p>쓰기 메서드(@Transactional)는 @CacheEvict(allEntries=true)로 두 캐시를 무효화합니다.
 *
 * <p>클래스 수준 @Transactional(readOnly=true) 적용 — 쓰기 메서드는 반드시 @Transactional 오버라이드 필요.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CodeService {

    private final CodeRepository codeRepository;
    private final TranslationCatalogService translationCatalogService;

    /**
     * 공통코드 다건 조회 (코드ID 기준 카테고리 전체)
     *
     * <p>캐시 없는 직접 조회 메서드입니다. 캐시 적용이 필요하면 {@link #findCodeEntitiesByCId(String)} 사용.
     *
     * @param cId 코드ID (예: PRJ_TP, CUR)
     * @param targetDate 기준일자 (null이면 현재 날짜)
     * @return 해당 코드ID의 유효한 공통코드 응답 DTO 목록 (없으면 빈 리스트)
     */
    public List<CodeDto.Response> getCcodemsByCId(String cId, LocalDate targetDate) {
        return codeRepository.findResponseRowsByCIdWithValidDate(cId, targetDate).stream()
                .map(CodeDto.Response::fromRow)
                .toList();
    }

    /** 선택 언어로 코드ID의 공통코드 목록을 조회합니다. */
    public List<CodeDto.Response> getCcodemsByCId(
            String cId, LocalDate targetDate, SupportedLanguage language) {
        return localize(
                codeRepository.findResponseRowsByCIdWithValidDate(cId, targetDate), language);
    }

    /**
     * 공통코드 단건 조회 (코드ID + 코드값 기준)
     *
     * @param cId 코드ID
     * @param cdva 코드값
     * @param targetDate 기준일자 (null이면 현재 날짜)
     * @return 해당 코드ID·코드값의 공통코드 응답 DTO
     * @throws IllegalArgumentException 코드가 존재하지 않거나 유효기간을 벗어난 경우
     */
    public CodeDto.Response getCcodem(String cId, String cdva, LocalDate targetDate) {
        CcodemResponseRow row =
                codeRepository
                        .findResponseRowByCIdAndCdvaWithValidDate(cId, cdva, targetDate)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "유효하지 않거나 존재하지 않는 코드입니다: " + cId + "/" + cdva));
        return CodeDto.Response.fromRow(row);
    }

    /** 선택 언어로 공통코드 한 건을 조회합니다. */
    public CodeDto.Response getCcodem(
            String cId, String cdva, LocalDate targetDate, SupportedLanguage language) {
        CcodemResponseRow row =
                codeRepository
                        .findResponseRowByCIdAndCdvaWithValidDate(cId, cdva, targetDate)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "유효하지 않거나 존재하지 않는 코드입니다: " + cId + "/" + cdva));
        return localize(List.of(row), language).getFirst();
    }

    /**
     * 코드타입 기준 공통코드 다건 조회
     *
     * @param cTp 코드타입 (예: IOE_LEAFE, IOE_XPN)
     * @param targetDate 기준일자 (null이면 현재 날짜)
     * @return 해당 코드타입의 유효한 공통코드 응답 DTO 목록 (없으면 빈 리스트)
     */
    public List<CodeDto.Response> getCcodemsByCTp(String cTp, LocalDate targetDate) {
        return codeRepository.findResponseRowsByCTpWithValidDate(cTp, targetDate).stream()
                .map(CodeDto.Response::fromRow)
                .toList();
    }

    /** 선택 언어로 코드유형의 공통코드 목록을 조회합니다. */
    public List<CodeDto.Response> getCcodemsByCTp(
            String cTp, LocalDate targetDate, SupportedLanguage language) {
        return localize(
                codeRepository.findResponseRowsByCTpWithValidDate(cTp, targetDate), language);
    }

    private List<CodeDto.Response> localize(
            List<CcodemResponseRow> rows, SupportedLanguage language) {
        if (language == SupportedLanguage.KO) {
            return rows.stream().map(CodeDto.Response::fromRow).toList();
        }
        List<String> keys =
                rows.stream()
                        .map(row -> TranslationTargetKey.code(row.cId(), row.cdva(), row.sttDt()))
                        .toList();
        var translations =
                translationCatalogService.findActive(TranslationTarget.COMMON_CODE, language, keys);
        return rows.stream()
                .map(
                        row -> {
                            String key =
                                    TranslationTargetKey.code(row.cId(), row.cdva(), row.sttDt());
                            return CodeDto.Response.fromRow(
                                    row, translations.getOrDefault(key, Map.of()));
                        })
                .toList();
    }

    /**
     * 코드ID로 엔티티 목록 조회 (캐시 적용 — 정적 참조 데이터용)
     *
     * <p>캐시 키: codesByCid::{cId}. 캐시 히트 시 DB 조회 없이 즉시 반환.
     *
     * <p>유효일은 null(현재 날짜)로 고정 — 미래 코드가 캐시에 포함될 수 있음.
     *
     * @param cId 코드ID (캐시 키로 사용)
     * @return 유효한 Ccodem 엔티티 목록
     */
    @Cacheable(value = "codesByCid", key = "#p0")
    public List<Ccodem> findCodeEntitiesByCId(String cId) {
        return codeRepository.findByCIdWithValidDate(cId, null);
    }

    /**
     * 코드ID로 엔티티 목록을 캐시 없이 즉시 조회합니다.
     *
     * <p>예산 산출처럼 SQL 마이그레이션·운영 보정 직후의 공통코드 값이 바로 반영되어야 하는 경로에서 사용합니다.
     *
     * @param cId 코드ID
     * @return 유효한 Ccodem 엔티티 목록
     */
    public List<Ccodem> findCodeEntitiesByCIdWithoutCache(String cId) {
        return codeRepository.findByCIdWithValidDate(cId, null);
    }

    /**
     * 공통코드 신규 생성
     *
     * @param request 생성 요청 DTO
     * @return 생성된 cId
     */
    @Transactional
    @Caching(
            evict = {
                @CacheEvict(value = "budgetPeriod", allEntries = true),
                @CacheEvict(value = "codesByCid", allEntries = true)
            })
    public String createCcodem(CodeDto.CreateRequest request) {
        if (request.getSttDt() == null) {
            throw new IllegalArgumentException("시작일자는 필수입니다.");
        }
        if (codeRepository.existsByCIdAndCdvaAndSttDt(
                request.getCId(), request.getCdva(), request.getSttDt())) {
            throw new IllegalArgumentException(
                    "이미 존재하는 코드입니다: "
                            + request.getCId()
                            + "/"
                            + request.getCdva()
                            + ", "
                            + request.getSttDt());
        }
        Ccodem ccodem = request.toEntity();
        codeRepository.save(ccodem);
        return ccodem.getCId();
    }

    /**
     * 공통코드 수정
     *
     * @param cId 코드ID
     * @param cdva 코드값
     * @param sttDt 시작일자
     * @param request 수정 요청 DTO
     * @return 수정된 코드ID
     * @throws IllegalArgumentException 공통코드를 찾을 수 없는 경우
     */
    @Transactional
    @Caching(
            evict = {
                @CacheEvict(value = "budgetPeriod", allEntries = true),
                @CacheEvict(value = "codesByCid", allEntries = true)
            })
    public String updateCcodem(
            String cId, String cdva, String sttDt, CodeDto.UpdateRequest request) {
        Ccodem ccodem =
                codeRepository
                        .findByCIdAndCdvaAndSttDtAndDelYn(cId, cdva, sttDt, "N")
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "수정할 공통코드를 찾을 수 없습니다: "
                                                        + cId
                                                        + "/"
                                                        + cdva
                                                        + ", "
                                                        + sttDt));
        ccodem.update(
                request.getCNm(),
                request.getCdvaDes(),
                request.getCdvaDtl(),
                request.getCdvaNm(),
                request.getCTp(),
                request.getCTpDes(),
                request.getHrkC(),
                request.getCSqn(),
                request.getEndDt(),
                request.getCdvaDtlC());
        return ccodem.getCId();
    }

    /**
     * 공통코드 논리적 삭제 (DEL_YN = Y)
     *
     * @param cId 코드ID
     * @param cdva 코드값
     * @param sttDt 시작일자
     * @throws IllegalArgumentException 공통코드를 찾을 수 없거나 이미 삭제된 경우
     */
    @Transactional
    @Caching(
            evict = {
                @CacheEvict(value = "budgetPeriod", allEntries = true),
                @CacheEvict(value = "codesByCid", allEntries = true)
            })
    public void deleteCcodem(String cId, String cdva, String sttDt) {
        Ccodem ccodem =
                codeRepository
                        .findByCIdAndCdvaAndSttDtAndDelYn(cId, cdva, sttDt, "N")
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "삭제할 공통코드를 찾을 수 없거나 이미 삭제됨: "
                                                        + cId
                                                        + "/"
                                                        + cdva
                                                        + ", "
                                                        + sttDt));
        ccodem.delete();
    }

    /**
     * 예산 신청 기간 조회
     *
     * @return 예산 신청 시작일·종료일과 현재 신청 가능 여부
     * @throws IllegalArgumentException 예산 신청 기간 코드가 없는 경우
     */
    @Cacheable("budgetPeriod")
    public CodeDto.BudgetPeriodResponse getBudgetPeriod() {
        Ccodem sta =
                codeRepository
                        .findByCIdAndCdvaWithValidDate(CommonCodeGroups.BUDGET_RQS, "STA", null)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "예산 신청기간 시작일자 코드를 찾을 수 없습니다: BG_RQS/STA"));
        Ccodem end =
                codeRepository
                        .findByCIdAndCdvaWithValidDate(CommonCodeGroups.BUDGET_RQS, "END", null)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "예산 신청기간 종료일자 코드를 찾을 수 없습니다: BG_RQS/END"));
        return CodeDto.BudgetPeriodResponse.builder()
                .startDate(sta.getCdvaDtlC())
                .endDate(end.getCdvaDtlC())
                .build();
    }

    /**
     * 예산 신청 기간 내인지 검증합니다.
     *
     * <p>현재 날짜가 BG_RQS/STA ~ BG_RQS/END 범위 안에 있는지 확인합니다. 범위를 벗어나면 즉시 예외를 발생시킵니다.
     *
     * @throws com.kdb.it.exception.CustomGeneralException 예산 신청 기간이 아닌 경우 (400 반환)
     * @throws IllegalArgumentException 예산 신청 기간 코드({@code BG_RQS/STA, BG_RQS/END})가 미등록된 경우
     */
    public void validateBudgetPeriod() {
        CodeDto.BudgetPeriodResponse period = getBudgetPeriod();
        String today = LocalDate.now().toString();
        if (today.compareTo(period.getStartDate()) < 0
                || today.compareTo(period.getEndDate()) > 0) {
            throw new CustomGeneralException(
                    "예산 신청 기간이 아닙니다. ("
                            + period.getStartDate()
                            + " ~ "
                            + period.getEndDate()
                            + ")");
        }
    }
}
