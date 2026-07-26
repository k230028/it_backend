package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.entity.Cblbcm;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 게시물 리포지토리 */
public interface BoardPostRepository
        extends JpaRepository<Cblbcm, String>, BoardPostRepositoryCustom {

    Optional<Cblbcm> findByNacMngNoAndDelYn(String nacMngNo, String delYn);

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

    /**
     * 답변글 삽입을 위한 SQN 밀어내기 (단일 트랜잭션 + 행 단위 락 전제)
     *
     * <p>같은 그룹에서 parentSqn보다 큰 SQN을 가진 행 중 깊이가 parentLev 이하인 행이 나오기 전까지를 +1 한다.
     */
    @Modifying
    @Query(
            """
            UPDATE Cblbcm c
               SET c.nacGrpSqn = c.nacGrpSqn + 1
             WHERE c.nacUnqId  = :grpNo
               AND c.nacGrpSqn > :parentSqn
               AND c.nacGrpLev > :parentLev
               AND c.delYn     = 'N'
            """)
    int shiftGroupSqn(
            @Param("grpNo") String grpNo,
            @Param("parentSqn") int parentSqn,
            @Param("parentLev") int parentLev);
}
