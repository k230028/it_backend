package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.entity.Ccmmtm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

/** 댓글 리포지토리 */
public interface BoardCommentRepository
        extends JpaRepository<Ccmmtm, Long>, BoardCommentRepositoryCustom {

    Optional<Ccmmtm> findByCmmtMngNoAndDelYn(Long cmmtMngNo, String delYn);

    /** 자식 댓글 존재 여부 — 소프트 삭제 시 트리 유지 판단 */
    boolean existsByHrkCmmtMngNoAndDelYn(Long hrkCmmtMngNo, String delYn);

    /** 댓글 채번 시퀀스 */
    @Query(value = "SELECT SEQ_CCMMTM.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextSequenceValue();

    /** 대댓글 삽입을 위한 SQN 밀어내기 */
    @Modifying
    @Query("""
            UPDATE Ccmmtm c
               SET c.cmmtGrpSqn = c.cmmtGrpSqn + 1
             WHERE c.cmmtGrpNo  = :grpNo
               AND c.cmmtGrpSqn > :parentSqn
               AND c.cmmtGrpLev > :parentLev
               AND c.delYn      = 'N'
            """)
    int shiftGroupSqn(
            @Param("grpNo") Long grpNo,
            @Param("parentSqn") int parentSqn,
            @Param("parentLev") int parentLev);
}
