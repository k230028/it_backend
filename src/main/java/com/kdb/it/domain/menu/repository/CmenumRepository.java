package com.kdb.it.domain.menu.repository;

import com.kdb.it.domain.menu.entity.Cmenum;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 메뉴 기본정보의 CRUD와 활성 메뉴 계층 조회를 담당하는 저장소입니다. */
public interface CmenumRepository extends JpaRepository<Cmenum, String>, CmenumRepositoryCustom {

    /**
     * 활성 메뉴 전량을 {@code whlMnuPth}(전체 메뉴 경로) 오름차순으로 조회합니다.
     *
     * <p>{@code whlMnuPth}는 부모 경로에 자신의 {@code mnuId}를 이어붙여 만들어 트리 내에서 유일하므로, 이 한 컬럼만으로도 결정적 순서가
     * 보장됩니다({@code mnuSotSqnSno}는 형제 범위 내에서만 의미가 있고 null·중복이 가능해 전역 정렬 단독 기준으로 쓰지 않습니다). 트리를 재조립하는
     * 소비처({@code MenuQueryService} 등)는 {@link CmenumRepositoryCustom#findActiveMenuTreeRows}를 별도로
     * 쓰므로 이 정렬의 영향을 받지 않습니다.
     */
    @Query("SELECT m FROM Cmenum m WHERE m.delYn = 'N' ORDER BY m.whlMnuPth ASC")
    List<Cmenum> findAllActive();

    Optional<Cmenum> findByMnuIdAndDelYn(String mnuId, String delYn);

    @Query("SELECT COUNT(m) FROM Cmenum m WHERE m.hrkMnuId = :mnuId AND m.delYn = 'N'")
    long countActiveChildren(@Param("mnuId") String mnuId);
}
