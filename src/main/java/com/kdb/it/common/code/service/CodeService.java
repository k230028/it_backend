package com.kdb.it.common.code.service;

import com.kdb.it.common.code.dto.CodeDto;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.exception.CustomGeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 공통코드(Ccodem) 서비스 클래스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CodeService {

    private final CodeRepository codeRepository;

    /**
     * 공통코드 다건 조회 (코드ID 기준 카테고리 전체)
     *
     * @param cId        코드ID (예: PRJ_TP, CUR)
     * @param targetDate 기준일자 (null이면 현재 날짜)
     */
    public List<CodeDto.Response> getCcodemsByCId(String cId, LocalDate targetDate) {
        return codeRepository.findByCIdWithValidDate(cId, targetDate).stream()
                .map(CodeDto.Response::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 공통코드 단건 조회 (코드ID + 코드값 기준)
     *
     * @param cId        코드ID
     * @param cdva       코드값
     * @param targetDate 기준일자 (null이면 현재 날짜)
     */
    public CodeDto.Response getCcodem(String cId, String cdva, LocalDate targetDate) {
        Ccodem ccodem = codeRepository.findByCIdAndCdvaWithValidDate(cId, cdva, targetDate)
                .orElseThrow(() -> new IllegalArgumentException(
                        "유효하지 않거나 존재하지 않는 코드입니다: " + cId + "/" + cdva));
        return CodeDto.Response.fromEntity(ccodem);
    }

    /**
     * 코드ID로 엔티티 목록 조회 (캐시 적용 — 정적 참조 데이터용)
     */
    @Cacheable(value = "codesByCid", key = "#p0")
    public List<Ccodem> findCodeEntitiesByCId(String cId) {
        return codeRepository.findByCIdWithValidDate(cId, null);
    }

    /**
     * 공통코드 신규 생성
     *
     * @param request 생성 요청 DTO
     * @return 생성된 cId
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "budgetPeriod", allEntries = true),
            @CacheEvict(value = "codesByCid",   allEntries = true)
    })
    public String createCcodem(CodeDto.CreateRequest request) {
        if (request.getSttDt() == null) {
            throw new IllegalArgumentException("시작일자는 필수입니다.");
        }
        if (codeRepository.existsByCIdAndCdvaAndSttDt(
                request.getCId(), request.getCdva(), request.getSttDt())) {
            throw new IllegalArgumentException("이미 존재하는 코드입니다: "
                    + request.getCId() + "/" + request.getCdva() + ", " + request.getSttDt());
        }
        Ccodem ccodem = request.toEntity();
        codeRepository.save(ccodem);
        return ccodem.getCId();
    }

    /**
     * 공통코드 수정
     *
     * @param cId    코드ID
     * @param cdva   코드값
     * @param sttDt  시작일자
     * @param request 수정 요청 DTO
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "budgetPeriod", allEntries = true),
            @CacheEvict(value = "codesByCid",   allEntries = true)
    })
    public String updateCcodem(String cId, String cdva, LocalDate sttDt,
                               CodeDto.UpdateRequest request) {
        Ccodem ccodem = codeRepository.findByCIdAndCdvaAndSttDtAndDelYn(cId, cdva, sttDt, "N")
                .orElseThrow(() -> new IllegalArgumentException(
                        "수정할 공통코드를 찾을 수 없습니다: " + cId + "/" + cdva + ", " + sttDt));
        ccodem.update(
                request.getCNm(),
                request.getCDes(),
                request.getCdvaDtl(),
                request.getCTp(),
                request.getCTpDes(),
                request.getHrkC(),
                request.getCSqn(),
                request.getEndDt());
        return ccodem.getCId();
    }

    /**
     * 공통코드 논리적 삭제 (DEL_YN = Y)
     *
     * @param cId   코드ID
     * @param cdva  코드값
     * @param sttDt 시작일자
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "budgetPeriod", allEntries = true),
            @CacheEvict(value = "codesByCid",   allEntries = true)
    })
    public void deleteCcodem(String cId, String cdva, LocalDate sttDt) {
        Ccodem ccodem = codeRepository.findByCIdAndCdvaAndSttDtAndDelYn(cId, cdva, sttDt, "N")
                .orElseThrow(() -> new IllegalArgumentException(
                        "삭제할 공통코드를 찾을 수 없거나 이미 삭제됨: " + cId + "/" + cdva + ", " + sttDt));
        ccodem.delete();
    }

    /**
     * 예산 신청 기간 조회
     */
    @Cacheable("budgetPeriod")
    public CodeDto.BudgetPeriodResponse getBudgetPeriod() {
        Ccodem sta = codeRepository.findByCIdAndCdvaWithValidDate("BG_RQS", "STA", null)
                .orElseThrow(() -> new IllegalArgumentException("예산 신청기간 시작일자 코드를 찾을 수 없습니다: BG_RQS/STA"));
        Ccodem end = codeRepository.findByCIdAndCdvaWithValidDate("BG_RQS", "END", null)
                .orElseThrow(() -> new IllegalArgumentException("예산 신청기간 종료일자 코드를 찾을 수 없습니다: BG_RQS/END"));
        return CodeDto.BudgetPeriodResponse.builder()
                .startDate(sta.getCNm())
                .endDate(end.getCNm())
                .build();
    }

    /**
     * 예산 신청 기간 내인지 검증
     */
    public void validateBudgetPeriod() {
        CodeDto.BudgetPeriodResponse period = getBudgetPeriod();
        String today = LocalDate.now().toString();
        if (today.compareTo(period.getStartDate()) < 0 || today.compareTo(period.getEndDate()) > 0) {
            throw new CustomGeneralException(
                    "예산 신청 기간이 아닙니다. (" + period.getStartDate() + " ~ " + period.getEndDate() + ")");
        }
    }
}
