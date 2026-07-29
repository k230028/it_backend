package com.kdb.it.domain.estimate.repository;

import com.kdb.it.domain.estimate.entity.Bestim;
import com.kdb.it.domain.estimate.entity.BestimId;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 소요예산 산정 마스터 Repository.
 *
 * <p>기본 CRUD는 {@link JpaRepository}가 제공하고, 동적 목록 조회는 {@link EstimateRepositoryCustom}에 위임합니다.
 */
public interface EstimateRepository
        extends JpaRepository<Bestim, BestimId>, EstimateRepositoryCustom {

    /** 상세 조회 응답 조립에 필요한 마스터 최소 필드입니다. */
    interface EstimateDetailView {
        String getRqmBgReqDocNo();

        Integer getDocVrsSno();

        String getCncdRfrNo();

        String getStsTc();

        String getReqCone();

        String getFstEnrUsid();

        java.time.LocalDateTime getFstEnrDtm();
    }

    /**
     * 문서번호·최종여부·삭제여부로 현재 유효 마스터를 조회합니다.
     *
     * @param rqmBgReqDocNo 소요예산요청문서번호
     * @param lstYn 최종여부 ("Y")
     * @param delYn 삭제여부 ("N")
     * @return 현재 유효 마스터 (없으면 empty)
     */
    Optional<Bestim> findByRqmBgReqDocNoAndLstYnAndDelYn(
            String rqmBgReqDocNo, String lstYn, String delYn);

    /**
     * 상세 응답 조립에 필요한 필드만 조회합니다 (문서번호·최종여부·삭제여부 기준).
     *
     * <p>{@link #findByRqmBgReqDocNoAndLstYnAndDelYn}과 동일한 조건이지만 엔티티 전체 대신 응답이 실제 사용하는 7개 필드만 적재하는
     * 인터페이스 프로젝션입니다. 쓰기 흐름({@code update}/{@code delete}/{@code changeStatus}/{@code saveLines})은
     * 영속성 컨텍스트 관리가 필요하므로 계속 엔티티 조회를 사용하고, 순수 읽기 전용인 상세 조회({@code get})만 이 메서드로 전환합니다.
     *
     * @param rqmBgReqDocNo 소요예산요청문서번호
     * @param lstYn 최종여부 ("Y")
     * @param delYn 삭제여부 ("N")
     * @return 현재 유효 마스터의 상세 view (없으면 empty)
     */
    Optional<EstimateDetailView> findDetailViewByRqmBgReqDocNoAndLstYnAndDelYn(
            String rqmBgReqDocNo, String lstYn, String delYn);

    /**
     * 소요예산요청문서번호 채번 — Oracle 시퀀스에서 다음 값을 조회합니다.
     *
     * @return 다음 시퀀스 값
     */
    @Query(nativeQuery = true, value = "SELECT SQ_TPRMPP_BESTIM_1.NEXTVAL FROM DUAL")
    Long nextDocSeq();

    /**
     * 동일 사업에 대해 지정 상태 중 하나인 미삭제 산정 문서가 이미 존재하는지 확인합니다.
     *
     * <p>중복 신청 방지: stsTc가 "51"(작성중) 또는 "55"(진행중)인 건이 있으면 신규 신청 불가.
     *
     * @param cncdRfrNo 관련참조번호(사업관리번호)
     * @param stsTc 확인할 상태코드 컬렉션
     * @param delYn 삭제여부 ("N")
     * @return 존재하면 true
     */
    boolean existsByCncdRfrNoAndStsTcInAndDelYn(
            String cncdRfrNo, Collection<String> stsTc, String delYn);
}
