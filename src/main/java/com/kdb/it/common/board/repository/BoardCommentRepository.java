package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.entity.Ccmmtm;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 댓글 리포지토리 */
public interface BoardCommentRepository
        extends JpaRepository<Ccmmtm, Long>, BoardCommentRepositoryCustom {

    Optional<Ccmmtm> findByCmmtMngNoAndDelYn(Long cmmtMngNo, String delYn);

    Optional<Ccmmtm> findByCmmtMngNoAndNacMngNoAndDelYn(
            Long cmmtMngNo, String nacMngNo, String delYn);

    /** 댓글 수정·삭제 시 게시물 소속을 함께 확인하고 댓글 행을 잠급니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT c
              FROM Ccmmtm c
             WHERE c.cmmtMngNo = :cmmtMngNo
               AND c.nacMngNo = :nacMngNo
               AND c.delYn = :delYn
            """)
    Optional<Ccmmtm> findByCmmtMngNoAndNacMngNoAndDelYnForUpdate(
            @Param("cmmtMngNo") Long cmmtMngNo,
            @Param("nacMngNo") String nacMngNo,
            @Param("delYn") String delYn);

    /**
     * 대댓글 그룹 잠금 전에 부모의 그룹 식별자만 조회합니다.
     *
     * <p>엔티티를 영속성 컨텍스트에 올리지 않아 그룹 잠금 대기 뒤 부모를 최신 상태로 다시 읽을 수 있습니다.
     */
    @Query(
            """
            SELECT c.cmmtGrpNo
              FROM Ccmmtm c
             WHERE c.cmmtMngNo = :cmmtMngNo
               AND c.nacMngNo = :nacMngNo
               AND c.delYn = :delYn
            """)
    Optional<Long> findReplyGroupId(
            @Param("cmmtMngNo") Long cmmtMngNo,
            @Param("nacMngNo") String nacMngNo,
            @Param("delYn") String delYn);

    /**
     * 대상 게시물 범위의 대댓글 그룹 루트 행을 잠급니다.
     *
     * <p>루트가 소프트 삭제되어도 남은 자식의 순서를 직렬화해야 하므로 삭제 여부는 조건에 포함하지 않습니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT c
              FROM Ccmmtm c
             WHERE c.nacMngNo = :nacMngNo
               AND c.cmmtMngNo = :groupId
               AND c.cmmtGrpNo = :groupId
            """)
    Optional<Ccmmtm> findReplyGroupAnchorForUpdate(
            @Param("nacMngNo") String nacMngNo, @Param("groupId") Long groupId);

    /** 자식 댓글 존재 여부 — 소프트 삭제 시 트리 유지 판단 */
    boolean existsByHrkCmmtMngNoAndDelYn(Long hrkCmmtMngNo, String delYn);

    /** 댓글 채번 시퀀스 */
    @Query(value = "SELECT SQ_TPRMPP_CCMMTM_1.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextSequenceValue();

    /**
     * 삽입 지점 뒤의 활성 그룹 행을 큰 순서부터 잠가 managed update 대상으로 반환합니다.
     *
     * <p>계층 깊이와 무관하게 뒤쪽 행을 모두 이동해 순서 중복을 막고 엔티티 감사 이벤트를 남깁니다. 처리 비용은 그룹 꼬리 길이에 비례합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT c
              FROM Ccmmtm c
             WHERE c.nacMngNo = :nacMngNo
               AND c.cmmtGrpNo = :groupId
               AND c.cmmtGrpSqn > :parentSqn
               AND c.delYn = 'N'
             ORDER BY c.cmmtGrpSqn DESC, c.cmmtMngNo DESC
            """)
    List<Ccmmtm> findActiveGroupTailForUpdate(
            @Param("nacMngNo") String nacMngNo,
            @Param("groupId") Long groupId,
            @Param("parentSqn") int parentSqn);
}
