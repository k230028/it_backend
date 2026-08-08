package com.kdb.it.domain.budget.project.repository;

import com.kdb.it.domain.budget.project.entity.Bproja;
import com.kdb.it.domain.budget.project.entity.BprojaId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 정보화사업관계(Bproja) 리포지토리.
 *
 * <p>프로젝트 대표상태는 {@code findByAbusMngNo...} 결과 중 <b>사업 자신의 행</b>({@code CNCD_RFR_NO = ABUS_MNG_NO})의
 * {@code IT_PTL_STS_TC}입니다. BPROJA는 단계 문서별 상태 테이블이라 나머지 행은 상위 계획·사업계획 등 다른 문서의 상태이며 대표상태 후보가
 * 아닙니다(BE-33). 계산은 서비스 계층({@code ProjectQueryAssembler.representativeStatus})에서 합니다. 1차에서는 BPROJA가
 * 비어 있어 결과가 비며, 대표상태는 null입니다.
 */
public interface BprojaRepository extends JpaRepository<Bproja, BprojaId> {

    /** 단일 프로젝트의 미삭제 관계 행 전체 (대표상태 계산용) */
    List<Bproja> findByAbusMngNoAndDelYn(String abusMngNo, String delYn);

    /** 다수 프로젝트의 미삭제 관계 행 전체 (목록 배치 대표상태 계산용) */
    List<Bproja> findByAbusMngNoInAndDelYn(Collection<String> abusMngNos, String delYn);
}
