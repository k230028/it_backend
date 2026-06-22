package com.kdb.it.domain.budget.document.service;

import com.kdb.it.domain.budget.document.entity.Brdocm;
import com.kdb.it.domain.budget.document.dto.ServiceRequestDocDto;
import com.kdb.it.domain.budget.document.repository.ServiceRequestDocRepository;
import com.kdb.it.domain.budget.document.util.DocVersionCodec;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.common.util.HtmlSanitizer;
import com.kdb.it.exception.CustomGeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 요구사항 정의서(TPRMPP_BRDOCM) 서비스
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
 * <b>버전 저장 규약</b>: 물리 컬럼 {@code DOC_VRS_SNO}는 {@code NUMBER(9,0)}(정수)이므로
 * 소수 버전을 그대로 저장하면 절삭되어 PK가 충돌합니다. 따라서 화면/API는 소수 버전(0.01, 1.00 ...)을
 * 사용하되, DB 저장·조회 키로 쓸 때만 {@link DocVersionCodec#toStored(BigDecimal)}(× 100)로 정수 변환하고,
 * 엔티티에서 읽어 응답할 때는 {@link DocVersionCodec#toDisplay(BigDecimal)}(÷ 100)로 소수 변환합니다.
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

    /** 요구사항 정의서 데이터 접근 리포지토리 (TPRMPP_BRDOCM) */
    private final ServiceRequestDocRepository serviceRequestDocRepository;

    /** 사용자 정보 리포지토리 (TPRMPP_CUSERI): 사번→사용자명 조회용 */
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
        List<ServiceRequestDocDto.Response> responses = serviceRequestDocRepository.findLatestVersionsAll().stream()
                .map(ServiceRequestDocDto.Response::fromEntity)
                .toList();

        // 작성자명 배치 조회 (N+1 제거): 사번 집합 → findByEnoIn 1회 → eno→이름 Map
        java.util.Set<String> enos = responses.stream()
                .map(ServiceRequestDocDto.Response::getFstEnrUsid)
                .filter(eno -> eno != null && !eno.isEmpty())
                .collect(Collectors.toSet());
        if (!enos.isEmpty()) {
            java.util.Map<String, String> nameByEno = cuserIRepository.findByEnoIn(enos).stream()
                    .collect(Collectors.toMap(
                            com.kdb.it.common.iam.entity.CuserI::getEno,
                            com.kdb.it.common.iam.entity.CuserI::getUsrNm,
                            (a, b) -> a));
            responses.forEach(r -> {
                // 원본 가드와 동일하게 사번이 null이거나 빈 문자열이면 이름을 설정하지 않음
                if (r.getFstEnrUsid() != null && !r.getFstEnrUsid().isEmpty()) {
                    r.setFstEnrUsNm(nameByEno.get(r.getFstEnrUsid()));
                }
            });
        }
        return responses;
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
                    .findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc(docMngNo, "N")
                    .orElseThrow(() -> new CustomGeneralException(
                            "존재하지 않는 문서관리번호입니다: " + docMngNo));
        } else {
            // 특정 버전 조회: 화면 소수 버전 → 저장 정수 버전(× 100)으로 변환하여 조회
            document = serviceRequestDocRepository
                    .findByDocMngNoAndDocVrsSnoAndDelYn(docMngNo, DocVersionCodec.toStored(version), "N")
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
                .findAllByDocMngNoAndDelYnOrderByDocVrsSnoDesc(docMngNo, "N").stream()
                .map(ServiceRequestDocDto.VersionResponse::fromEntity)
                .toList();
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
     * 요구사항내용({@code reqInf})은 XSS 방지를 위해 HTML 새니타이징을 적용합니다.
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
        request.setRedtConeInf(HtmlSanitizer.sanitize(request.getRedtConeInf()));

        // 복합키 (docMngNo, 최초버전)로 엔티티 생성
        // 화면 버전 0.01 → 저장 정수 1(× 100). NUMBER(9,0) 컬럼 절삭 방지.
        Brdocm document = request.toEntity(docMngNo, DocVersionCodec.toStored(INITIAL_VERSION));
        serviceRequestDocRepository.save(document);
        return document.getDocMngNo();
    }

    /**
     * 요구사항 정의서 수정
     *
     * <p>
     * 최신 버전 레코드를 대상으로 정보를 수정합니다(버전 번호는 변경되지 않음).
     * 요구사항내용({@code reqInf})은 XSS 방지를 위해 HTML 새니타이징을 적용합니다.
     * </p>
     *
     * @param docMngNo 수정할 문서관리번호
     * @param request  수정 요청 DTO
     * @param user     현재 인증 사용자 (소유권 검증용)
     * @return 수정된 문서관리번호
     * @throws CustomGeneralException 해당 문서관리번호가 없는 경우
     * @throws org.springframework.security.access.AccessDeniedException 소유자도 관리자도 아닌 경우
     */
    @Transactional
    public String updateDocument(String docMngNo, ServiceRequestDocDto.UpdateRequest request, CustomUserDetails user) {
        // 최신 버전 조회
        Brdocm document = serviceRequestDocRepository
                .findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc(docMngNo, "N")
                .orElseThrow(() -> new CustomGeneralException(
                        "존재하지 않는 문서관리번호입니다: " + docMngNo));

        // 소유권 검증: 최신 버전의 작성자 본인 또는 관리자만 수정 가능
        OwnershipVerifier.verifyOwnerOrAdmin(document.getFstEnrUsid(), user);

        // 요구사항정보 XSS 새니타이징
        String sanitizedCone = HtmlSanitizer.sanitize(request.getRedtConeInf());

        // JPA Dirty Checking으로 자동 반영
        document.update(
                request.getReqTtl(),
                sanitizedCone,
                request.getReqDttNo(),
                request.getBzDttNm(),
                request.getRvwFsgTlmDt());

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
     * @param user     현재 인증 사용자 (소유권 검증용)
     * @return 새로 생성된 버전 번호 (예: 0.02)
     * @throws CustomGeneralException 해당 문서관리번호가 없는 경우
     * @throws org.springframework.security.access.AccessDeniedException 소유자도 관리자도 아닌 경우
     */
    @Transactional
    public BigDecimal createNewVersion(String docMngNo, CustomUserDetails user) {
        // 최신 버전 조회
        Brdocm latest = serviceRequestDocRepository
                .findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc(docMngNo, "N")
                .orElseThrow(() -> new CustomGeneralException(
                        "존재하지 않는 문서관리번호입니다: " + docMngNo));

        // 소유권 검증: 최신 버전의 작성자 본인 또는 관리자만 새 버전 생성 가능
        OwnershipVerifier.verifyOwnerOrAdmin(latest.getFstEnrUsid(), user);

        // 새 버전 번호 계산: 저장 정수 → 화면 소수(÷ 100)로 환산 후 + 0.01 증가
        BigDecimal currentDisplay = DocVersionCodec.toDisplay(latest.getDocVrsSno());
        BigDecimal nextDisplay = currentDisplay.add(VERSION_INCREMENT);

        // 기존 업무 필드 복제 + 새 버전 번호(저장 정수, × 100) 지정
        Brdocm newEntity = latest.newVersion(DocVersionCodec.toStored(nextDisplay));
        serviceRequestDocRepository.save(newEntity);

        // 응답은 화면 소수 버전으로 반환 (예: 0.02)
        return nextDisplay;
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
     * @param user     현재 인증 사용자 (소유권 검증용)
     * @throws CustomGeneralException 해당 문서 또는 버전이 없는 경우
     * @throws org.springframework.security.access.AccessDeniedException 소유자도 관리자도 아닌 경우
     */
    @Transactional
    public void deleteDocument(String docMngNo, BigDecimal version, CustomUserDetails user) {
        // 소유권 검증: 최신 버전의 작성자 본인 또는 관리자만 삭제 가능 (버전 분기 이전 선행 검증)
        Brdocm latest = serviceRequestDocRepository
                .findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc(docMngNo, "N")
                .orElseThrow(() -> new CustomGeneralException(
                        "존재하지 않는 문서관리번호입니다: " + docMngNo));
        OwnershipVerifier.verifyOwnerOrAdmin(latest.getFstEnrUsid(), user);

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
            // 특정 버전만 소프트 삭제: 화면 소수 버전 → 저장 정수 버전(× 100)으로 변환하여 조회
            Brdocm document = serviceRequestDocRepository
                    .findByDocMngNoAndDocVrsSnoAndDelYn(docMngNo, DocVersionCodec.toStored(version), "N")
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
     * @param bbrC 부서코드 (TPRMPP_CUSERI.BBR_C)
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
