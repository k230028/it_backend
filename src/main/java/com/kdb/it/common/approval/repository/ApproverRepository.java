package com.kdb.it.common.approval.repository;

import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.entity.CdecimId;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 결재 정보(Cdecim) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA의 {@link JpaRepository}를 상속하여 결재 테이블(TPRMPP_CDECIM)에 대한 CRUD 기능을 제공합니다.
 *
 * <p>복합키 타입: {@link CdecimId} (dcdMngNo + dcrSqnSno)
 *
 * <p>주요 활용:
 *
 * <ul>
 *   <li>신청서별 결재선 목록 순서대로 조회
 *   <li>특정 결재자의 결재 정보 단건 조회
 * </ul>
 */
public interface ApproverRepository extends JpaRepository<Cdecim, CdecimId> {

    /** 결재 응답 조립에 필요한 결재선 최소 필드입니다. */
    interface ApproverReadView {
        String getDcdMngNo();

        Integer getDcrSqnSno();

        String getDcrEno();

        String getItPtlDcdStsC();

        LocalDate getDcdDtm();

        String getDcrOpnnCone();

        String getLstDcdYn();
    }

    /**
     * 신청서 한 건의 결재선을 결재 순번 오름차순으로 조회합니다.
     *
     * @param dcdMngNo 신청서 관리번호
     * @return 결재 순번 오름차순 read view 목록
     */
    List<ApproverReadView> findReadViewsByDcdMngNoOrderByDcrSqnSnoAsc(String dcdMngNo);

    /**
     * 여러 신청서의 결재선을 결재 순번 오름차순으로 일괄 조회합니다.
     *
     * @param dcdMngNos 신청서 관리번호 목록
     * @return 결재 순번 오름차순 read view 목록
     */
    List<ApproverReadView> findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(
            Collection<String> dcdMngNos);

    /**
     * 결재관리번호로 결재선 목록 조회 (결재자순서 오름차순)
     *
     * <p>특정 신청서(dcdMngNo)의 전체 결재선을 결재 순서대로 반환합니다. 결재 처리 시 현재 결재 차례를 파악하는 데 사용됩니다.
     *
     * @param dcdMngNo 신청서식별번호 (예: APF-2026-00000001)
     * @return 결재자순서(DCR_SQN_SNO) 오름차순으로 정렬된 결재선 목록
     */
    List<Cdecim> findByDcdMngNoOrderByDcrSqnSnoAsc(String dcdMngNo);

    /**
     * 결재관리번호와 결재자순서로 결재 정보 단건 조회
     *
     * <p>복합키(dcdMngNo + dcrSqnSno)로 특정 결재자의 결재 정보를 조회합니다.
     *
     * @param dcdMngNo 신청서식별번호
     * @param dcrSqnSno 결재자순서일련번호 (1부터 시작)
     * @return 해당 결재 정보 (없으면 {@link Optional#empty()})
     */
    Optional<Cdecim> findByDcdMngNoAndDcrSqnSno(String dcdMngNo, Integer dcrSqnSno);

    /**
     * 여러 결재관리번호에 대한 결재선 목록 일괄 조회 (결재자순서 오름차순)
     *
     * <p>N+1 문제 방지용 배치 조회 메서드입니다.
     *
     * @param dcdMngNos 신청서식별번호 목록
     * @return 전체 결재선 목록
     */
    List<Cdecim> findByDcdMngNoInOrderByDcrSqnSnoAsc(Collection<String> dcdMngNos);

    /** 복합키 충돌 없이 미결재 결재선의 순번을 임시 위치로 이동합니다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    "UPDATE TPRMPP_CDECIM "
                            + "SET DCR_SQN_SNO = DCR_SQN_SNO + :offset "
                            + "WHERE APF_DCM_NO = :dcdMngNo "
                            + "AND DCR_SQN_SNO IN (:sequences)",
            nativeQuery = true)
    int shiftPendingSequences(
            @Param("dcdMngNo") String dcdMngNo,
            @Param("sequences") Collection<Integer> sequences,
            @Param("offset") int offset);

    /** 임시 위치의 결재자를 실제 결재 순번으로 이동합니다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    "UPDATE TPRMPP_CDECIM SET DCR_SQN_SNO = :toSequence "
                            + "WHERE APF_DCM_NO = :dcdMngNo AND DCR_SQN_SNO = :fromSequence",
            nativeQuery = true)
    int updateSequence(
            @Param("dcdMngNo") String dcdMngNo,
            @Param("fromSequence") int fromSequence,
            @Param("toSequence") int toSequence);
}
