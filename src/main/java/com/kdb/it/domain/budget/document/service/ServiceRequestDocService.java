package com.kdb.it.domain.budget.document.service;

import com.kdb.it.domain.budget.document.entity.Brdocm;
import com.kdb.it.domain.budget.document.dto.ServiceRequestDocDto;
import com.kdb.it.domain.budget.document.repository.ServiceRequestDocRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.util.HtmlSanitizer;
import com.kdb.it.exception.CustomGeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 요구사항 정의서(TAAABB_BRDOCM) 서비스
 *
 * <p>
 * 요구사항 정의서 엔티티의 CRUD 및 버전 관리 비즈니스 로직을 처리합니다.
 * </p>
 *
 * <p>
 * 테이블 PK는 (DOC_MNG_NO, DOC_VRS) 복합키이며, 동일 {@code DOC_MNG_NO}에 대해
 * 여러 버전이 존재할 수 있습니다. 최초 생성 시 버전은 {@code 0.01}이며,
 * 새 버전 생성 시 기존 최신 버전 + {@code 0.01}로 증가합니다.
 * </p>
 *
 * <p>
 * Soft Delete 패턴: {@code DEL_YN='Y'}로 논리 삭제합니다. 물리 삭제는 수행하지 않습니다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceRequestDocService {

    /** 요구사항 정의서 데이터 접근 리포지토리 (TAAABB_BRDOCM) */
    private final ServiceRequestDocRepository serviceRequestDocRepository;

    /** 사용자 정보 리포지토리 (TAAABB_CUSERI): 사번→사용자명 조회용 */
    private final UserRepository cuserIRepository;

    /** 신규 문서 최초 버전 */
    private static final BigDecimal INITIAL_VERSION = new BigDecimal("0.01");

    /** 버전 증분 단위 */
    private static final BigDecimal VERSION_INCREMENT = new BigDecimal("0.01");

    /**
     * 요구사항 정의서 목록 조회
     *
     * <p>
     * 각 {@code DOC_MNG_NO} 그룹의 최신 버전({@code MAX(DOC_VRS)}) 레코드만 반환합니다.
     * 삭제되지 않은({@code DEL_YN='N'}) 행만 대상으로 합니다.
     * </p>
     *
     * @return 문서별 최신 버전 응답 DTO 목록
     */
    public List<ServiceRequestDocDto.Response> getDocumentList() {
        return serviceRequestDocRepository.findLatestVersionsAll().stream()
                .map(entity -> {
                    ServiceRequestDocDto.Response response = ServiceRequestDocDto.Response.fromEntity(entity);
                    // 최초생성자 사번 → 사용자명 매핑
                    if (response.getFstEnrUsid() != null && !response.getFstEnrUsid().isEmpty()) {
                        cuserIRepository.findById(response.getFstEnrUsid())
                                .ifPresent(user -> response.setFstEnrUsNm(user.getUsrNm()));
                    }
                    return response;
                })
                .collect(Collectors.toList());
    }

    /**
     * 요구사항 정의서 단건 조회
     *
     * <p>
     * {@code version} 파라미터가 {@code null}이면 최신 버전을, 값이 있으면 해당 버전을 반환합니다.
     * </p>
     *
     * @param docMngNo 문서관리번호 (예: DOC-2026-0001)
     * @param version  문서버전 ({@code null}이면 최신 버전 조회)
     * @return 요구사항 정의서 응답 DTO
     * @throws CustomGeneralException 해당 문서 또는 버전이 없는 경우
     */
    public ServiceRequestDocDto.Response getDocument(String docMngNo, BigDecimal version) {
        Brdocm document;
        if (version == null) {
            // 최신 버전 조회
            document = serviceRequestDocRepository
                    .findTopByDocMngNoAndDelYnOrderByDocVrsDesc(docMngNo, "N")
                    .orElseThrow(() -> new CustomGeneralException(
                            "존재하지 않는 문서관리번호입니다: " + docMngNo));
        } else {
            // 특정 버전 조회
            document = serviceRequestDocRepository
                    .findByDocMngNoAndDocVrsAndDelYn(docMngNo, version, "N")
                    .orElseThrow(() -> new CustomGeneralException(
                            "해당 버전의 문서를 찾을 수 없습니다: " + docMngNo + " (v" + version + ")"));
        }
        return ServiceRequestDocDto.Response.fromEntity(document);
    }

    /**
     * 요구사항 정의서 버전 히스토리 조회
     *
     * <p>
     * 동일 {@code docMngNo}의 전체 버전 목록을 버전 내림차순으로 반환합니다.
     * 본문(BLOB)은 제외하고 메타 정보만 포함합니다.
     * </p>
     *
     * @param docMngNo 문서관리번호
     * @return 버전 히스토리 응답 DTO 목록 (버전 내림차순)
     */
    public List<ServiceRequestDocDto.VersionResponse> getVersionHistory(String docMngNo) {
        return serviceRequestDocRepository
                .findAllByDocMngNoAndDelYnOrderByDocVrsDesc(docMngNo, "N").stream()
                .map(ServiceRequestDocDto.VersionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 요구사항 정의서 생성
     *
     * <p>
     * 문서관리번호({@code DOC_MNG_NO})가 없으면 Oracle 시퀀스로 자동 채번합니다.
     * 자동 채번 형식: {@code DOC-{연도}-{seq:04d}} (예: DOC-2026-0001).
     * 최초 버전은 {@code 0.01}로 고정됩니다.
     * </p>
     *
     * <p>
     * 요구사항내용({@code reqCone})은 XSS 방지를 위해 HTML 새니타이징을 적용합니다.
     * </p>
     *
     * @param request 요구사항 정의서 생성 요청 DTO
     * @return 생성된 문서관리번호
     * @throws CustomGeneralException 제공된 문서관리번호가 이미 존재하는 경우
     */
    @Transactional
    public String createDocument(ServiceRequestDocDto.CreateRequest request) {
        String docMngNo = request.getDocMngNo();

        // 문서관리번호가 없으면 자동 채번
        if (docMngNo == null || docMngNo.isEmpty()) {
            Long nextVal = serviceRequestDocRepository.getNextSequenceValue();
            String year = String.valueOf(LocalDate.now().getYear());
            docMngNo = String.format("DOC-%s-%04d", year, nextVal);
            request.setDocMngNo(docMngNo);
        } else {
            // 제공된 문서관리번호 중복 확인
            if (serviceRequestDocRepository.existsByDocMngNoAndDelYn(docMngNo, "N")) {
                throw new CustomGeneralException("이미 존재하는 문서관리번호입니다: " + docMngNo);
            }
        }

        // 요구사항내용 XSS 새니타이징
        request.setReqCone(HtmlSanitizer.sanitize(request.getReqCone()));

        // 복합키 (docMngNo, 0.01)로 엔티티 생성
        Brdocm document = request.toEntity(docMngNo, INITIAL_VERSION);
        serviceRequestDocRepository.save(document);
        return document.getDocMngNo();
    }

    /**
     * 요구사항 정의서 수정
     *
     * <p>
     * 최신 버전 레코드를 대상으로 정보를 수정합니다(버전 번호는 변경되지 않음).
     * 요구사항내용({@code reqCone})은 XSS 방지를 위해 HTML 새니타이징을 적용합니다.
     * </p>
     *
     * @param docMngNo 수정할 문서관리번호
     * @param request  수정 요청 DTO
     * @return 수정된 문서관리번호
     * @throws CustomGeneralException 해당 문서관리번호가 없는 경우
     */
    @Transactional
    public String updateDocument(String docMngNo, ServiceRequestDocDto.UpdateRequest request) {
        // 최신 버전 조회
        Brdocm document = serviceRequestDocRepository
                .findTopByDocMngNoAndDelYnOrderByDocVrsDesc(docMngNo, "N")
                .orElseThrow(() -> new CustomGeneralException(
                        "존재하지 않는 문서관리번호입니다: " + docMngNo));

        // 요구사항내용 XSS 새니타이징
        String sanitizedCone = HtmlSanitizer.sanitize(request.getReqCone());
        byte[] reqConeBytes = sanitizedCone != null ? sanitizedCone.getBytes(StandardCharsets.UTF_8) : null;

        // JPA Dirty Checking으로 자동 반영
        document.update(
                request.getReqNm(),
                reqConeBytes,
                request.getReqDtt(),
                request.getBzDtt(),
                request.getFsgTlm());

        return docMngNo;
    }

    /**
     * 요구사항 정의서 새 버전 생성
     *
     * <p>
     * 기존 최신 버전의 업무 필드를 복제하여 버전 번호를 {@code +0.01} 증가시킨
     * 새 레코드를 INSERT 합니다.
     * </p>
     *
     * @param docMngNo 문서관리번호
     * @return 새로 생성된 버전 번호 (예: 0.02)
     * @throws CustomGeneralException 해당 문서관리번호가 없는 경우
     */
    @Transactional
    public BigDecimal createNewVersion(String docMngNo) {
        // 최신 버전 조회
        Brdocm latest = serviceRequestDocRepository
                .findTopByDocMngNoAndDelYnOrderByDocVrsDesc(docMngNo, "N")
                .orElseThrow(() -> new CustomGeneralException(
                        "존재하지 않는 문서관리번호입니다: " + docMngNo));

        // 새 버전 번호 계산 (최신 + 0.01)
        BigDecimal nextVrs = latest.getDocVrs().add(VERSION_INCREMENT);

        // 기존 업무 필드 복제 + 새 버전 번호 지정
        Brdocm newEntity = latest.newVersion(nextVrs);
        serviceRequestDocRepository.save(newEntity);
        return nextVrs;
    }

    /**
     * 요구사항 정의서 삭제 (Soft Delete)
     *
     * <p>
     * {@code version}이 {@code null}이면 동일 {@code docMngNo}의 모든 버전을 일괄 소프트 삭제합니다.
     * {@code version}이 지정되면 해당 버전만 소프트 삭제합니다.
     * {@code DEL_YN='Y'}로 논리 삭제하며, 물리 삭제는 수행하지 않습니다.
     * </p>
     *
     * @param docMngNo 삭제할 문서관리번호
     * @param version  삭제할 문서버전 ({@code null}이면 전체 버전 일괄 삭제)
     * @throws CustomGeneralException 해당 문서 또는 버전이 없는 경우
     */
    @Transactional
    public void deleteDocument(String docMngNo, BigDecimal version) {
        if (version == null) {
            // 전체 버전 일괄 소프트 삭제
            List<Brdocm> all = serviceRequestDocRepository
                    .findAllByDocMngNoAndDelYn(docMngNo, "N");
            if (all.isEmpty()) {
                throw new CustomGeneralException("존재하지 않는 문서관리번호입니다: " + docMngNo);
            }
            // BaseEntity.delete() 호출 → DEL_YN='Y' (JPA Dirty Checking)
            all.forEach(Brdocm::delete);
        } else {
            // 특정 버전만 소프트 삭제
            Brdocm document = serviceRequestDocRepository
                    .findByDocMngNoAndDocVrsAndDelYn(docMngNo, version, "N")
                    .orElseThrow(() -> new CustomGeneralException(
                            "해당 버전의 문서를 찾을 수 없습니다: " + docMngNo + " (v" + version + ")"));
            document.delete();
        }
    }

    /**
     * 요구사항 정의서 대시보드 집계 조회
     *
     * <p>로그인 사용자의 부서코드(bbrC) 기준으로 KPI, 월별 추이,
     * 검토 중인 요청 목록을 집계하여 반환합니다.</p>
     *
     * @param bbrC 부서코드 (TAAABB_CUSERI.BBR_C)
     * @return 대시보드 집계 응답 DTO
     */
    public ServiceRequestDocDto.DashboardResponse getDashboard(String bbrC) {
        int totalCount     = serviceRequestDocRepository.countTotalByBbrC(bbrC);
        int reviewingCount = serviceRequestDocRepository.countReviewingByBbrC(bbrC);
        int completedCount = serviceRequestDocRepository.countCompletedByBbrC(bbrC);
        int overdueCount   = serviceRequestDocRepository.countOverdueByBbrC(bbrC);

        List<ServiceRequestDocDto.MonthlyCount> monthlyTrend =
            serviceRequestDocRepository.findMonthlyTrendByBbrC(bbrC).stream()
                .map(row -> ServiceRequestDocDto.MonthlyCount.builder()
                    .month((String) row[0])
                    .count(((Number) row[1]).intValue())
                    .build())
                .toList();

        LocalDate today = LocalDate.now();
        List<ServiceRequestDocDto.ReviewingItem> recentReviewing =
            serviceRequestDocRepository.findRecentReviewingByBbrC(bbrC).stream()
                .map(row -> {
                    LocalDate fsgTlmDate = null;
                    if (row[4] != null) {
                        // Oracle JDBC는 DATE를 java.sql.Date 또는 java.sql.Timestamp로 반환 가능
                        if (row[4] instanceof java.sql.Timestamp ts) {
                            fsgTlmDate = ts.toLocalDateTime().toLocalDate();
                        } else if (row[4] instanceof java.sql.Date d) {
                            fsgTlmDate = d.toLocalDate();
                        }
                    }
                    boolean delayed = fsgTlmDate != null && fsgTlmDate.isBefore(today);
                    return ServiceRequestDocDto.ReviewingItem.builder()
                        .docMngNo((String) row[0])
                        .title((String) row[1])
                        .authorName((String) row[2])
                        .createdAt((String) row[3])
                        .status(delayed ? "delayed" : "reviewing")
                        .build();
                })
                .toList();

        return ServiceRequestDocDto.DashboardResponse.builder()
            .totalCount(totalCount)
            .reviewingCount(reviewingCount)
            .completedCount(completedCount)
            .overdueCount(overdueCount)
            .monthlyTrend(monthlyTrend)
            .recentReviewing(recentReviewing)
            .build();
    }

    /**
     * 사이드바 배지용 검토 진행 중 문서 수 조회
     *
     * @param bbrC 부서코드
     * @return 배지 건수 응답 DTO
     */
    public ServiceRequestDocDto.BadgeCountResponse getBadgeCount(String bbrC) {
        return ServiceRequestDocDto.BadgeCountResponse.builder()
            .reviewingCount(serviceRequestDocRepository.countReviewingByBbrC(bbrC))
            .build();
    }
}
