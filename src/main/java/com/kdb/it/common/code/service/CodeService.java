package com.kdb.it.common.code.service;

import com.kdb.it.common.code.dto.CodeDto;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.exception.CustomGeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 공통코드(Ccodem) 서비스 클래스
 *
 * <p>
 * 공통코드에 대한 비즈니스 로직(조회, 생성, 수정, 삭제)을 처리합니다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CodeService {

    private final CodeRepository codeRepository;

    /**
     * 공통코드 단건 조회 (코드ID 기준)
     *
     * <p>
     * 주어진 기준일자(targetDate)를 포함하여 유효한 공통코드를 조회합니다.
     * (targetDate가 null이면 현재 시각 기준)
     * </p>
     *
     * @param cdId       조회할 코드ID
     * @param targetDate 기준일자 (Nullable)
     * @return 공통코드 Response DTO
     * @throws IllegalArgumentException 해당 코드ID의 유효한 공통코드가 없는 경우
     */
    public CodeDto.Response getCcodemById(String cdId, LocalDate targetDate) {
        Ccodem ccodem = codeRepository.findByCdIdWithValidDate(cdId, targetDate)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 존재하지 않는 코드ID 입니다: " + cdId));
        return CodeDto.Response.fromEntity(ccodem);
    }

    /**
     * 공통코드 다건 조회 (코드값구분 기준)
     *
     * <p>
     * 주어진 기준일자(targetDate)를 포함하여 유효한 공통코드 목록을 조회합니다.
     * </p>
     *
     * @param cttTp      조회할 코드값구분
     * @param targetDate 기준일자 (Nullable)
     * @return 공통코드 Response DTO 리스트
     */
    public List<CodeDto.Response> getCcodemByCttTp(String cttTp, LocalDate targetDate) {
        List<Ccodem> ccodems = codeRepository.findByCttTpWithValidDate(cttTp, targetDate);
        return ccodems.stream()
                .map(CodeDto.Response::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 공통코드 신규 생성
     *
     * @param request 신규 생성 요청 DTO
     * @return 생성된 코드ID
     * @throws IllegalArgumentException 코드ID가 중복될 경우
     */
    @Transactional
    public String createCcodem(CodeDto.CreateRequest request) {
        if (request.getSttDt() == null) {
            throw new IllegalArgumentException("시작일자는 필수입니다.");
        }

        if (codeRepository.existsByCdIdAndSttDt(request.getCdId(), request.getSttDt())) {
            throw new IllegalArgumentException("이미 존재하는 코드ID/시작일자 입니다: " + request.getCdId() + ", " + request.getSttDt());
        }

        Ccodem ccodem = request.toEntity();
        codeRepository.save(ccodem);
        return ccodem.getCdId();
    }

    /**
     * 공통코드 수정
     *
     * @param cdId    수정할 코드ID
     * @param sttDt   수정할 시작일자
     * @param request 수정 요청 DTO
     * @return 수정된 코드ID
     * @throws IllegalArgumentException 대상 코드ID가 존재하지 않거나 삭제된 경우
     */
    @Transactional
    public String updateCcodem(String cdId, LocalDate sttDt, CodeDto.UpdateRequest request) {
        Ccodem ccodem = codeRepository.findByCdIdAndSttDtAndDelYn(cdId, sttDt, "N")
                .orElseThrow(() -> new IllegalArgumentException("수정할 공통코드를 찾을 수 없습니다: " + cdId + ", " + sttDt));

        if (request.getSttDt() != null && !request.getSttDt().equals(sttDt)) {
            throw new IllegalArgumentException("시작일자는 기본키이므로 수정할 수 없습니다.");
        }

        ccodem.update(
                request.getCdNm(),
                request.getCdva(),
                request.getCdDes(),
                request.getCttTp(),
                request.getCttTpDes(),
                request.getCdSqn(),
                sttDt,
                request.getEndDt());

        return ccodem.getCdId();
    }

    /**
     * 공통코드 삭제 (논리적 삭제)
     *
     * <p>
     * DB에서 완전히 삭제하지 않고 DEL_YN 필드를 'Y'로 업데이트합니다.
     * </p>
     *
     * @param cdId  삭제할 코드ID
     * @param sttDt 삭제할 시작일자
     * @throws IllegalArgumentException 대상 코드ID가 존재하지 않거나 이미 삭제된 경우
     */
    @Transactional
    public void deleteCcodem(String cdId, LocalDate sttDt) {
        Ccodem ccodem = codeRepository.findByCdIdAndSttDtAndDelYn(cdId, sttDt, "N")
                .orElseThrow(() -> new IllegalArgumentException("삭제할 공통코드를 찾을 수 없거나 이미 삭제되었습니다: " + cdId + ", " + sttDt));

        ccodem.delete(); // BaseEntity의 delete() 호출 -> delYn = 'Y'
    }

    /**
     * 예산 신청 기간 조회
     *
     * <p>
     * 공통코드 BG-RQS-STA(시작일자)와 BG-RQS-END(종료일자)를 조회하여
     * 예산 신청 가능 기간을 반환합니다.
     * </p>
     *
     * @return 시작일자/종료일자를 담은 Map
     */
    /**
     * 코드값구분(cttTp)으로 공통코드 엔티티 목록 조회 (캐시 적용)
     *
     * <p>비목코드 등 정적 참조 데이터는 서버 재시작 전까지 캐시합니다.</p>
     */
    @Cacheable(value = "codesByType", key = "#p0")
    public List<Ccodem> findCodeEntitiesByCttTp(String cttTp) {
        return codeRepository.findByCttTpWithValidDate(cttTp, null);
    }

    @Cacheable("budgetPeriod")
    public CodeDto.BudgetPeriodResponse getBudgetPeriod() {
        Ccodem startCode = codeRepository.findByCdIdWithValidDate("BG-RQS-STA", null)
                .orElseThrow(() -> new IllegalArgumentException("예산 신청기간 시작일자 코드를 찾을 수 없습니다: BG-RQS-STA"));
        Ccodem endCode = codeRepository.findByCdIdWithValidDate("BG-RQS-END", null)
                .orElseThrow(() -> new IllegalArgumentException("예산 신청기간 종료일자 코드를 찾을 수 없습니다: BG-RQS-END"));

        return CodeDto.BudgetPeriodResponse.builder()
                .startDate(startCode.getCdva())
                .endDate(endCode.getCdva())
                .build();
    }

    /**
     * 예산 신청 기간 내인지 검증
     *
     * <p>
     * 현재 일자가 공통코드(BG-RQS-STA ~ BG-RQS-END) 범위 내에 있지 않으면
     * CustomGeneralException을 발생시킵니다.
     * </p>
     *
     * @throws CustomGeneralException 기간 외인 경우 400 Bad Request
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
