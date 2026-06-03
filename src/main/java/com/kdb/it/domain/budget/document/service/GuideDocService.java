package com.kdb.it.domain.budget.document.service;

import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.domain.budget.document.dto.GuideDocDto;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import com.kdb.it.common.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 가이드 문서(TPRMPP_BGDOCM) 서비스
 *
 * <p>
 * 가이드 문서 엔티티의 CRUD 비즈니스 로직을 처리합니다.
 * </p>
 *
 * <p>
 * Soft Delete 패턴: {@code DEL_YN='Y'}로 논리 삭제합니다.
 * </p>
 *
 * <p>
 * {@code @Transactional(readOnly = true)}: 조회 메서드의 기본값.
 * 쓰기 메서드는 {@code @Transactional}로 오버라이드합니다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuideDocService {

    /** 가이드 문서 데이터 접근 리포지토리 (TPRMPP_BGDOCM) */
    private final GuideDocRepository guideDocRepository;

    /**
     * 가이드 문서 목록 조회
     *
     * <p>
     * 삭제되지 않은({@code DEL_YN='N'}) 모든 가이드 문서를 조회합니다.
     * </p>
     *
     * @return 가이드 문서 응답 DTO 목록
     */
    public List<GuideDocDto.Response> getDocumentList() {
        return guideDocRepository.findAllByDelYn("N").stream()
                .map(GuideDocDto.Response::fromEntity)
                .toList();
    }

    /**
     * 가이드 문서 단건 조회
     *
     * <p>
     * 문서관리번호로 삭제되지 않은 가이드 문서를 조회합니다.
     * </p>
     *
     * @param docMngNo 문서관리번호 (예: GDOC-2026-0001)
     * @return 가이드 문서 응답 DTO
     * @throws IllegalArgumentException 해당 문서관리번호가 없는 경우
     */
    public GuideDocDto.Response getDocument(String docMngNo) {
        Bgdocm document = guideDocRepository.findByDocMngNoAndDelYn(docMngNo, "N")
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문서관리번호입니다: " + docMngNo));
        return GuideDocDto.Response.fromEntity(document);
    }

    /**
     * 가이드 문서 생성
     *
     * <p>
     * 문서관리번호({@code DOC_MNG_NO})가 없으면 Oracle 시퀀스로 자동 채번합니다.
     * </p>
     *
     * <p>
     * 자동 채번 형식: {@code GDOC-{연도}-{seq:04d}} (예: GDOC-2026-0001)
     * </p>
     *
     * <p>
     * 문서내용({@code docInf})은 XSS 방지를 위해 HTML 새니타이징을 적용합니다.
     * </p>
     *
     * @param request 가이드 문서 생성 요청 DTO
     * @return 생성된 문서관리번호
     * @throws IllegalArgumentException 제공된 문서관리번호가 이미 존재하는 경우
     */
    @Transactional
    public String createDocument(GuideDocDto.CreateRequest request) {
        String docMngNo = request.getDocMngNo();

        // 문서관리번호가 없으면 자동 채번
        if (docMngNo == null || docMngNo.isEmpty()) {
            Long nextVal = guideDocRepository.getNextSequenceValue();
            String year = String.valueOf(LocalDate.now().getYear());
            docMngNo = String.format("GDOC-%s-%04d", year, nextVal);
            request.setDocMngNo(docMngNo);
        } else {
            // 제공된 문서관리번호 중복 확인
            if (guideDocRepository.existsByDocMngNoAndDelYn(docMngNo, "N")) {
                throw new IllegalArgumentException("이미 존재하는 문서관리번호입니다: " + docMngNo);
            }
        }

        // 문서내용 XSS 새니타이징
        request.setNacTxtInf(HtmlSanitizer.sanitize(request.getNacTxtInf()));

        Bgdocm document = request.toEntity();
        guideDocRepository.save(document);
        return document.getDocMngNo();
    }

    /**
     * 가이드 문서 수정
     *
     * <p>
     * 문서관리번호로 가이드 문서를 조회하여 정보를 수정합니다.
     * 문서내용({@code docInf})은 XSS 방지를 위해 HTML 새니타이징을 적용합니다.
     * </p>
     *
     * @param docMngNo 수정할 문서관리번호
     * @param request  수정 요청 DTO
     * @return 수정된 문서관리번호
     * @throws IllegalArgumentException 해당 문서관리번호가 없는 경우
     */
    @Transactional
    public String updateDocument(String docMngNo, GuideDocDto.UpdateRequest request) {
        Bgdocm document = guideDocRepository.findByDocMngNoAndDelYn(docMngNo, "N")
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문서관리번호입니다: " + docMngNo));

        // 문서정보 XSS 새니타이징
        String sanitizedCone = HtmlSanitizer.sanitize(request.getNacTxtInf());

        // JPA Dirty Checking으로 자동 반영
        document.update(request.getDocTtlCone(), sanitizedCone);

        return docMngNo;
    }

    /**
     * 가이드 문서 삭제 (Soft Delete)
     *
     * <p>
     * {@code DEL_YN='Y'}로 논리 삭제합니다. 물리 삭제는 수행하지 않습니다.
     * </p>
     *
     * @param docMngNo 삭제할 문서관리번호
     * @throws IllegalArgumentException 해당 문서관리번호가 없는 경우
     */
    @Transactional
    public void deleteDocument(String docMngNo) {
        Bgdocm document = guideDocRepository.findByDocMngNoAndDelYn(docMngNo, "N")
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문서관리번호입니다: " + docMngNo));

        // Soft Delete (DEL_YN='Y')
        document.delete();
    }
}
