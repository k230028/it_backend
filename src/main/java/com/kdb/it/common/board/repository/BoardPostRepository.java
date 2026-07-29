package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.entity.Cblbcm;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 게시물 리포지토리 */
public interface BoardPostRepository
        extends JpaRepository<Cblbcm, String>, BoardPostRepositoryCustom {

    Optional<Cblbcm> findByNacMngNoAndDelYn(String nacMngNo, String delYn);

    Optional<Cblbcm> findByBlbMngNoAndNacMngNoAndDelYn(
            String blbMngNo, String nacMngNo, String delYn);

    /**
     * 답글 그룹 잠금 전에 부모의 그룹 식별자만 조회합니다.
     *
     * <p>엔티티를 영속성 컨텍스트에 올리지 않아 그룹 잠금 대기 뒤 부모를 최신 상태로 다시 읽을 수 있습니다.
     */
    @Query(
            """
            SELECT c.nacUnqId
              FROM Cblbcm c
             WHERE c.blbMngNo = :blbMngNo
               AND c.nacMngNo = :nacMngNo
               AND c.delYn = :delYn
            """)
    Optional<String> findReplyGroupId(
            @Param("blbMngNo") String blbMngNo,
            @Param("nacMngNo") String nacMngNo,
            @Param("delYn") String delYn);

    /** 첨부파일 수 캐시 갱신 시 게시물 행을 잠급니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT c
              FROM Cblbcm c
             WHERE c.nacMngNo = :nacMngNo
               AND c.delYn = :delYn
            """)
    Optional<Cblbcm> findByNacMngNoAndDelYnForUpdate(
            @Param("nacMngNo") String nacMngNo, @Param("delYn") String delYn);

    /** 게시물 채번 시퀀스 — NAC-{YYYY}-{0001} */
    @Query(value = "SELECT SQ_TPRMPP_CBLBCM_1.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextSequenceValue();

    /** 조회수 갱신 시 게시판 소속을 함께 확인하고 게시물 행을 잠급니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT c
              FROM Cblbcm c
             WHERE c.blbMngNo = :blbMngNo
               AND c.nacMngNo = :nacMngNo
               AND c.delYn = :delYn
            """)
    Optional<Cblbcm> findByBlbMngNoAndNacMngNoAndDelYnForUpdate(
            @Param("blbMngNo") String blbMngNo,
            @Param("nacMngNo") String nacMngNo,
            @Param("delYn") String delYn);

    /**
     * 게시판 범위의 답글 그룹 루트 행을 잠급니다.
     *
     * <p>루트가 소프트 삭제되어도 남은 자식의 순서를 직렬화해야 하므로 삭제 여부는 조건에 포함하지 않습니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT c
              FROM Cblbcm c
             WHERE c.blbMngNo = :blbMngNo
               AND c.nacMngNo = :groupId
               AND c.nacUnqId = :groupId
            """)
    Optional<Cblbcm> findReplyGroupAnchorForUpdate(
            @Param("blbMngNo") String blbMngNo, @Param("groupId") String groupId);

    /**
     * 삽입 지점 뒤의 활성 그룹 행을 큰 순서부터 잠가 managed update 대상으로 반환합니다.
     *
     * <p>계층 깊이와 무관하게 뒤쪽 행을 모두 이동해 순서 중복을 막고 엔티티 감사 이벤트를 남깁니다. 처리 비용은 그룹 꼬리 길이에 비례합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT c
              FROM Cblbcm c
             WHERE c.blbMngNo = :blbMngNo
               AND c.nacUnqId = :groupId
               AND c.nacGrpSqn > :parentSqn
               AND c.delYn = 'N'
             ORDER BY c.nacGrpSqn DESC, c.nacMngNo DESC
            """)
    List<Cblbcm> findActiveGroupTailForUpdate(
            @Param("blbMngNo") String blbMngNo,
            @Param("groupId") String groupId,
            @Param("parentSqn") int parentSqn);
}
