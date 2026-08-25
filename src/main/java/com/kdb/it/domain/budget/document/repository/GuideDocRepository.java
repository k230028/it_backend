package com.kdb.it.domain.budget.document.repository;

import com.kdb.it.domain.budget.document.entity.Bgdocm;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 가이드 문서(Bgdocm) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA의 {@link JpaRepository}를 상속하여 가이드 문서 테이블(TPRMPP_BGDOCM)의 기본 CRUD 기능을 제공합니다.
 *
 * <p>Soft Delete 패턴 적용: 조회 시 {@code delYn='N'} 조건을 사용합니다.
 */
public interface GuideDocRepository extends JpaRepository<Bgdocm, String> {

    /**
     * 문서관리번호와 삭제여부로 단건 조회
     *
     * @param docMngNo 문서관리번호
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 가이드 문서
     */
    Optional<Bgdocm> findByDocMngNoAndDelYn(String docMngNo, String delYn);

    /**
     * 삭제여부로 전체 목록 조회
     *
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 가이드 문서 목록
     */
    List<Bgdocm> findAllByDelYn(String delYn);

    /**
     * 사업 유형에 맞는 본문이 있는 활성 입력 길라잡이를 조회합니다.
     *
     * <p>기존 단계별 가이드와 섞이지 않도록 {@code FDOC-} 접두사를 받고, 공백뿐인 본문은 사용자 패널에 표시하지 않습니다.
     *
     * @param prefix 입력 길라잡이 문서관리번호 접두사
     * @param guideIdPrefix 사업 유형별 길라잡이 ID 접두사
     * @return 활성·본문 보유 길라잡이 목록
     */
    @Query(
            value =
                    """
                    select *
                      from TPRMPP_BGDOCM
                     where DOC_MNG_NO like :prefix || '%'
                       and DOC_TTL_CONE like :guideIdPrefix || '%'
                       and DEL_YN = 'N'
                       and NAC_TXT_INF is not null
                       and REGEXP_LIKE(NAC_TXT_INF, '[^[:space:]]')
                    """,
            nativeQuery = true)
    List<Bgdocm> findActiveFormGuides(
            @Param("prefix") String prefix, @Param("guideIdPrefix") String guideIdPrefix);

    /**
     * 고정 ID로 활성 입력 길라잡이 하나를 찾습니다.
     *
     * @param docTtlCone 고정 길라잡이 ID
     * @param docMngNoPrefix 입력 길라잡이 문서관리번호 접두사
     * @param delYn 삭제여부
     * @return 조건에 맞는 입력 길라잡이
     */
    Optional<Bgdocm> findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
            String docTtlCone, String docMngNoPrefix, String delYn);

    /**
     * 삭제여부로 목록 조회용 경량 프로젝션 조회
     *
     * <p>본문({@code nacTxtInf}, CLOB)을 제외한 목록 화면 전용 필드만 조회하여 불필요한 CLOB 로딩을 방지합니다.
     *
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 가이드 문서 목록 프로젝션
     */
    List<GuideDocListView> findListViewsByDelYn(String delYn);

    /** 가이드 문서 목록 조회용 경량 프로젝션 (본문 {@code nacTxtInf} 제외) */
    interface GuideDocListView {

        /** 문서관리번호 */
        String getDocMngNo();

        /** 문서명 */
        String getDocTtlCone();

        /** 삭제여부 */
        String getDelYn();

        /** 최초생성시간 */
        LocalDateTime getFstEnrDtm();

        /** 최초생성자 사번 */
        String getFstEnrUsid();

        /** 마지막수정시간 */
        LocalDateTime getLstChgDtm();

        /** 마지막수정자 사번 */
        String getLstChgUsid();
    }

    /**
     * 문서관리번호와 삭제여부로 존재 여부 확인
     *
     * @param docMngNo 문서관리번호
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 존재하면 {@code true}
     */
    boolean existsByDocMngNoAndDelYn(String docMngNo, String delYn);

    /**
     * Oracle 시퀀스(SQ_TPRMPP_BGDOCM_1) 다음 값 조회
     *
     * <p>신규 가이드 문서 생성 시 문서관리번호 채번에 사용합니다. 형식: {@code GDOC-{연도}-{4자리 시퀀스}} (예: {@code
     * GDOC-2026-0001})
     *
     * @return Oracle 시퀀스(SQ_TPRMPP_BGDOCM_1)의 다음 값
     */
    @Query(value = "SELECT SQ_TPRMPP_BGDOCM_1.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextSequenceValue();
}
