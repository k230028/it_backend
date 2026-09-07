package com.kdb.it.domain.budget.document.service;

import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.util.HtmlSanitizer;
import com.kdb.it.domain.budget.document.dto.GuideDocDto;
import com.kdb.it.domain.budget.document.entity.BgdocDocumentType;
import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가이드 문서(TPRMPP_BGDOCM) 서비스
 *
 * <p>가이드 문서 엔티티의 CRUD 비즈니스 로직을 처리합니다.
 *
 * <p>Soft Delete 패턴: {@code DEL_YN='Y'}로 논리 삭제합니다.
 *
 * <p>{@code @Transactional(readOnly = true)}: 조회 메서드의 기본값. 쓰기 메서드는 {@code @Transactional}로
 * 오버라이드합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuideDocService {

    private static final String GUIDE_DOCUMENT_PREFIX = "GDOC-";
    private static final String DOCUMENT_TYPE = BgdocDocumentType.BUSINESS_GUIDE.code();

    /** 가이드 문서 데이터 접근 리포지토리 (TPRMPP_BGDOCM) */
    private final GuideDocRepository guideDocRepository;

    /** 마지막 수정자 이름 조회용 사용자 리포지토리 */
    private final UserRepository userRepository;

    private final BgdocNumberAllocator bgdocNumberAllocator;

    /**
     * 가이드 문서 목록 조회
     *
     * <p>삭제되지 않은({@code DEL_YN='N'}) 모든 가이드 문서를 조회합니다. 목록 응답은 본문({@code nacTxtInf}, CLOB)을 제외한 경량
     * 프로젝션을 사용합니다.
     *
     * @return 가이드 문서 목록 응답 DTO 목록 (본문 제외)
     */
    public List<GuideDocDto.ListResponse> getDocumentList() {
        List<GuideDocRepository.GuideDocListView> views =
                guideDocRepository.findListViewsByDocDtlItmCAndDelYn(DOCUMENT_TYPE, "N");

        Set<String> modifierEnos =
                views.stream()
                        .map(GuideDocRepository.GuideDocListView::getLstChgUsid)
                        .filter(eno -> eno != null && !eno.isBlank())
                        .collect(Collectors.toSet());

        Map<String, String> modifierNameByEno = new HashMap<>();
        if (!modifierEnos.isEmpty()) {
            userRepository
                    .findNameViewsByEnoIn(modifierEnos)
                    .forEach(
                            user -> {
                                if (user.getEno() != null) {
                                    modifierNameByEno.putIfAbsent(user.getEno(), user.getUsrNm());
                                }
                            });
        }

        return views.stream()
                .map(
                        view ->
                                GuideDocDto.ListResponse.fromView(
                                        view, modifierNameByEno.get(view.getLstChgUsid())))
                .toList();
    }

    /**
     * 가이드 문서 단건 조회
     *
     * <p>문서관리번호로 삭제되지 않은 가이드 문서를 조회합니다.
     *
     * @param docMngNo 문서관리번호 (예: GDOC-2026-0001)
     * @return 가이드 문서 응답 DTO
     * @throws IllegalArgumentException 해당 문서관리번호가 없는 경우
     */
    public GuideDocDto.Response getDocument(String docMngNo) {
        Bgdocm document =
                guideDocRepository
                        .findByDocMngNoAndDocDtlItmCAndDelYn(docMngNo, DOCUMENT_TYPE, "N")
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "존재하지 않는 문서관리번호입니다: " + docMngNo));
        return GuideDocDto.Response.fromEntity(document);
    }

    /**
     * 가이드 문서 생성
     *
     * <p>문서관리번호({@code DOC_MNG_NO})가 없으면 Oracle 시퀀스로 자동 채번합니다.
     *
     * <p>자동 채번 형식: {@code GDOC-{연도}-{seq:04d}} (예: GDOC-2026-0001)
     *
     * <p>문서내용({@code docInf})은 XSS 방지를 위해 HTML 새니타이징을 적용합니다.
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
            docMngNo = bgdocNumberAllocator.next(GUIDE_DOCUMENT_PREFIX);
            request.setDocMngNo(docMngNo);
        } else {
            if (!docMngNo.startsWith(GUIDE_DOCUMENT_PREFIX)) {
                throw new IllegalArgumentException("문서관리번호는 GDOC-로 시작해야 합니다: " + docMngNo);
            }
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
     * <p>문서관리번호로 가이드 문서를 조회하여 정보를 수정합니다. 문서내용({@code docInf})은 XSS 방지를 위해 HTML 새니타이징을 적용합니다.
     *
     * @param docMngNo 수정할 문서관리번호
     * @param request 수정 요청 DTO
     * @return 수정된 문서관리번호
     * @throws IllegalArgumentException 해당 문서관리번호가 없는 경우
     */
    @Transactional
    public String updateDocument(String docMngNo, GuideDocDto.UpdateRequest request) {
        Bgdocm document =
                guideDocRepository
                        .findByDocMngNoAndDocDtlItmCAndDelYn(docMngNo, DOCUMENT_TYPE, "N")
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "존재하지 않는 문서관리번호입니다: " + docMngNo));

        // 문서정보 XSS 새니타이징
        String sanitizedCone = HtmlSanitizer.sanitize(request.getNacTxtInf());

        // JPA Dirty Checking으로 자동 반영
        document.update(request.getDocTtlCone(), sanitizedCone);

        return docMngNo;
    }

    /**
     * 가이드 문서 삭제 (Soft Delete)
     *
     * <p>{@code DEL_YN='Y'}로 논리 삭제합니다. 물리 삭제는 수행하지 않습니다.
     *
     * @param docMngNo 삭제할 문서관리번호
     * @throws IllegalArgumentException 해당 문서관리번호가 없는 경우
     */
    @Transactional
    public void deleteDocument(String docMngNo) {
        Bgdocm document =
                guideDocRepository
                        .findByDocMngNoAndDocDtlItmCAndDelYn(docMngNo, DOCUMENT_TYPE, "N")
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "존재하지 않는 문서관리번호입니다: " + docMngNo));

        // 논리 삭제(DEL_YN='Y')
        document.delete();
    }
}
