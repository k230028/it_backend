package com.kdb.it.domain.budget.document.service;

import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.budget.document.dto.ReviewCommentDto;
import com.kdb.it.domain.budget.document.entity.Brivgm;
import com.kdb.it.domain.budget.document.repository.BrivgmRepository;
import com.kdb.it.domain.budget.document.util.DocVersionCodec;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 문서 검토의견(Brivgm) 서비스
 *
 * <p>특정 문서+버전의 검토의견 조회, 생성, 해결처리 기능을 제공합니다.
 */
@Service
@RequiredArgsConstructor
public class ReviewCommentService {

    private final BrivgmRepository brivgmRepository;

    /** 사용자 정보 리포지토리 (TPRMPP_CUSERI): 사번→작성자 정보 배치 조회용 */
    private final UserRepository userRepository;

    /**
     * 특정 문서+버전의 미삭제 검토의견 목록을 조회합니다.
     *
     * @param docMngNo 문서관리번호
     * @param docVrsSno 문서버전
     * @return 검토의견 응답 DTO 목록 (생성일시 오름차순)
     */
    @Transactional(readOnly = true)
    public List<ReviewCommentDto.Response> getComments(String docMngNo, BigDecimal docVrsSno) {
        // 화면 소수 버전 → 저장 정수 버전(× 100)으로 변환하여 조회 (Brdocm 버전 키와 동일 규약)
        var comments =
                brivgmRepository.findByDocMngNoAndDocVrsSnoAndDelYnOrderByFstEnrDtmAsc(
                        docMngNo, DocVersionCodec.toStored(docVrsSno), "N");

        return toResponses(comments);
    }

    /**
     * 검토의견을 신규 등록합니다.
     *
     * @param docMngNo 대상 문서관리번호
     * @param request 검토의견 생성 요청 DTO
     * @return 저장된 검토의견 응답 DTO
     */
    @Transactional
    public ReviewCommentDto.Response addComment(
            String docMngNo, ReviewCommentDto.CreateRequest request) {
        var saved = brivgmRepository.save(request.toEntity(docMngNo));
        return toResponses(List.of(saved)).getFirst();
    }

    /**
     * 검토의견을 완료 처리합니다. FSG_YN을 'Y'로 변경합니다.
     *
     * <p>URL path의 {@code docMngNo}와 실제 코멘트가 소속된 문서가 일치하는지 함께 검증하여 다른 문서의 {@code ivgSno}를 이용한 교차
     * 접근을 차단합니다.
     *
     * @param docMngNo 문서관리번호 (소속 검증용)
     * @param ivgSno 의견일련번호
     * @throws ResponseStatusException 해당 의견이 존재하지 않거나 다른 문서에 속한 경우 (404 NOT_FOUND)
     */
    @Transactional
    public void resolveComment(String docMngNo, Long ivgSno) {
        var comment =
                brivgmRepository
                        .findByIpmOpnnSnoAndDocMngNoAndDelYn(ivgSno, docMngNo, "N")
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "검토의견을 찾을 수 없습니다: " + ivgSno));
        comment.resolve();
    }

    /**
     * 검토의견 목록을 작성자 정보가 포함된 응답으로 변환합니다.
     *
     * <p>작성자 사번 집합을 한 번만 조회하여 의견 행마다 사용자 조회가 발생하지 않도록 합니다.
     *
     * @param comments 변환할 검토의견 목록
     * @return 작성자 이름과 팀명이 보정된 검토의견 응답 목록
     */
    private List<ReviewCommentDto.Response> toResponses(Collection<Brivgm> comments) {
        var authorByEno =
                findAuthorsByEno(
                        comments.stream()
                                .map(Brivgm::getFstEnrUsid)
                                .filter(StringUtils::hasText)
                                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return comments.stream().map(comment -> toResponse(comment, authorByEno)).toList();
    }

    /**
     * 사번 집합의 작성자 정보를 한 번에 조회합니다.
     *
     * @param enos 조회할 작성자 사번 집합
     * @return 사번을 키로 하는 작성자 정보 맵
     */
    private Map<String, UserRepository.ReviewCommentAuthorView> findAuthorsByEno(Set<String> enos) {
        if (enos.isEmpty()) {
            return Map.of();
        }
        return userRepository.findReviewCommentAuthorViewsByEnoIn(enos).stream()
                .collect(
                        Collectors.toMap(
                                UserRepository.ReviewCommentAuthorView::getEno,
                                value -> value,
                                (a, b) -> a));
    }

    /**
     * 검토의견 엔티티를 작성자 정보가 보정된 응답으로 변환합니다.
     *
     * <p>사용자 또는 이름이 없으면 사번을 이름으로 사용하고, 팀명이 없으면 빈 문자열을 반환합니다.
     *
     * @param comment 변환할 검토의견 엔티티
     * @param authorByEno 사번별 작성자 정보
     * @return 작성자 정보가 포함된 검토의견 응답
     */
    private ReviewCommentDto.Response toResponse(
            Brivgm comment, Map<String, UserRepository.ReviewCommentAuthorView> authorByEno) {
        var eno = comment.getFstEnrUsid();
        if (!StringUtils.hasText(eno)) {
            return new ReviewCommentDto.Response(comment, "", "");
        }

        var author = authorByEno.get(eno);
        if (author == null) {
            return new ReviewCommentDto.Response(comment, eno, "");
        }

        var authorName = StringUtils.hasText(author.getUsrNm()) ? author.getUsrNm() : eno;
        var authorTeam = author.getTemNm() == null ? "" : author.getTemNm();
        return new ReviewCommentDto.Response(comment, authorName, authorTeam);
    }
}
