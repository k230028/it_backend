package com.kdb.it.domain.budget.project.repository;

import com.kdb.it.domain.budget.project.entity.Bproja;
import com.kdb.it.domain.budget.project.entity.BprojaId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 정보화사업관계(Bproja) 리포지토리.
 *
 * <p>프로젝트 대표상태는 {@code findByAbusMngNo...} 결과의 {@code IT_PTL_STS_TC} 최댓값으로 계산합니다 (서비스 계층에서 Java
 * max). 1차에서는 BPROJA가 비어 있어 결과가 비며, 대표상태는 null입니다.
 */
public interface BprojaRepository extends JpaRepository<Bproja, BprojaId> {

    /** 단일 프로젝트의 미삭제 관계 행 전체 (대표상태 계산용) */
    List<Bproja> findByAbusMngNoAndDelYn(String abusMngNo, String delYn);

    /** 다수 프로젝트의 미삭제 관계 행 전체 (목록 배치 대표상태 계산용) */
    List<Bproja> findByAbusMngNoInAndDelYn(Collection<String> abusMngNos, String delYn);
}
