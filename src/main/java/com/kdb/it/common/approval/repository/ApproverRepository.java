package com.kdb.it.common.approval.repository;

import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.entity.CdecimId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 결재 정보(Cdecim) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA의 {@link JpaRepository}를 상속하여
 * 결재 테이블(TPRMPP_CDECIM)에 대한 CRUD 기능을 제공합니다.</p>
 *
 * <p>복합키 타입: {@link CdecimId} (dcdMngNo + dcdSqn)</p>
 *
 * <p>주요 활용:</p>
 * <ul>
 *   <li>신청서별 결재선 목록 순서대로 조회</li>
 *   <li>특정 결재자의 결재 정보 단건 조회</li>
 * </ul>
 */
public interface ApproverRepository extends JpaRepository<Cdecim, CdecimId> {

    /**
     * 결재관리번호로 결재선 목록 조회 (결재순서 오름차순)
     *
     * <p>특정 신청서(dcdMngNo)의 전체 결재선을 결재 순서대로 반환합니다.
     * 결재 처리 시 현재 결재 차례를 파악하는 데 사용됩니다.</p>
     *
     * @param dcdMngNo 결재관리번호 (신청서관리번호와 동일한 값, 예: APF_202600000001)
     * @return 결재 순서(DCD_SQN) 오름차순으로 정렬된 결재선 목록
     */
    List<Cdecim> findByDcdMngNoOrderByDcdSqnAsc(String dcdMngNo);

    /**
     * 결재관리번호와 결재순서로 결재 정보 단건 조회
     *
     * <p>복합키(dcdMngNo + dcdSqn)로 특정 결재자의 결재 정보를 조회합니다.</p>
     *
     * @param dcdMngNo 결재관리번호
     * @param dcdSqn   결재순서 (1부터 시작)
     * @return 해당 결재 정보 (없으면 {@link Optional#empty()})
     */
    Optional<Cdecim> findByDcdMngNoAndDcdSqn(String dcdMngNo, Integer dcdSqn);

    /**
     * 여러 결재관리번호에 대한 결재선 목록 일괄 조회 (결재순서 오름차순)
     *
     * <p>N+1 문제 방지용 배치 조회 메서드입니다.</p>
     *
     * @param dcdMngNos 결재관리번호 목록
     * @return 전체 결재선 목록
     */
    List<Cdecim> findByDcdMngNoInOrderByDcdSqnAsc(List<String> dcdMngNos);
}
