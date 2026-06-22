package com.kdb.it.domain.budget.document.service;

import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.budget.document.dto.ReviewCommentDto;
import com.kdb.it.domain.budget.document.repository.BrivgmRepository;
import com.kdb.it.domain.budget.document.util.DocVersionCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 문서 검토의견(Brivgm) 서비스
 *
 * <p>
 * 특정 문서+버전의 검토의견 조회, 생성, 해결처리 기능을 제공합니다.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class ReviewCommentService {

    private final BrivgmRepository brivgmRepository;

    /** 사용자 정보 리포지토리 (TPRMPP_CUSERI): 사번→사용자명 조회용 */
    private final UserRepository userRepository;

    /**
     * 특정 문서+버전의 미삭제 검토의견 목록을 조회합니다.
     *
     * @param docMngNo  문서관리번호
     * @param docVrsSno 문서버전
     * @return 검토의견 응답 DTO 목록 (생성일시 오름차순)
     */
    @Transactional(readOnly = true)
    public List<ReviewCommentDto.Response> getComments(String docMngNo, BigDecimal docVrsSno) {
        // 화면 소수 버전 → 저장 정수 버전(× 100)으로 변환하여 조회 (Brdocm 버전 키와 동일 규약)
        var comments = brivgmRepository
                .findByDocMngNoAndDocVrsSnoAndDelYnOrderByFstEnrDtmAsc(docMngNo, DocVersionCodec.toStored(docVrsSno), "N");

        // 작성자명 배치 조회 (N+1 제거): 사번 집합 → findByEnoIn 1회 → eno→이름 Map
        java.util.Set<String> enos = comments.stream()
                .map(com.kdb.it.domain.budget.document.entity.Brivgm::getFstEnrUsid)
                .filter(eno -> eno != null && !eno.isEmpty())
                .collect(Collectors.toSet());
        java.util.Map<String, String> nameByEno = enos.isEmpty() ? java.util.Map.of()
                : userRepository.findByEnoIn(enos).stream()
                        .collect(Collectors.toMap(
                                com.kdb.it.common.iam.entity.CuserI::getEno,
                                com.kdb.it.common.iam.entity.CuserI::getUsrNm,
                                (a, b) -> a));

        return comments.stream()
                .map(e -> new ReviewCommentDto.Response(e,
                        e.getFstEnrUsid() == null ? "" : nameByEno.getOrDefault(e.getFstEnrUsid(), e.getFstEnrUsid())))
                .toList();
    }

    /**
     * 검토의견을 신규 등록합니다.
     *
     * @param docMngNo 대상 문서관리번호
     * @param request  검토의견 생성 요청 DTO
     * @return 저장된 검토의견 응답 DTO
     */
    @Transactional
    public ReviewCommentDto.Response addComment(String docMngNo,
                                                 ReviewCommentDto.CreateRequest request) {
        var saved = brivgmRepository.save(request.toEntity(docMngNo));
        return new ReviewCommentDto.Response(saved, resolveAuthorName(saved.getFstEnrUsid()));
    }

    /**
     * 검토의견을 완료 처리합니다. FSG_YN을 'Y'로 변경합니다.
     *
     * <p>
     * URL path의 {@code docMngNo}와 실제 코멘트가 소속된 문서가 일치하는지 함께 검증하여
     * 다른 문서의 {@code ivgSno}를 이용한 교차 접근을 차단합니다.
     * </p>
     *
     * @param docMngNo 문서관리번호 (소속 검증용)
     * @param ivgSno   의견일련번호
     * @throws ResponseStatusException 해당 의견이 존재하지 않거나 다른 문서에 속한 경우 (404 NOT_FOUND)
     */
    @Transactional
    public void resolveComment(String docMngNo, Long ivgSno) {
        var comment = brivgmRepository.findByIpmOpnnSnoAndDocMngNoAndDelYn(ivgSno, docMngNo, "N")
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "검토의견을 찾을 수 없습니다: " + ivgSno));
        comment.resolve();
    }

    /**
     * 사번으로 사용자 이름을 조회합니다.
     *
     * <p>
     * {@link UserRepository#findById(Object)} 로 {@code CuserI} 를 찾고, 존재하면
     * {@code usrNm} 을 반환합니다. 사용자를 찾을 수 없는 경우 사번(eno)을 그대로 반환하여
     * UI에서 식별 가능한 값이 노출되도록 합니다.
     * </p>
     *
     * @param eno 사번
     * @return 사용자 이름 (미존재 시 사번, null 입력 시 빈 문자열)
     */
    private String resolveAuthorName(String eno) {
        if (eno == null) return "";
        return userRepository.findById(eno)
                .map(user -> user.getUsrNm())
                .orElse(eno);
    }
}
