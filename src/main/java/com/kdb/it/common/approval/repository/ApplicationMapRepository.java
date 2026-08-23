package com.kdb.it.common.approval.repository;

import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.CapplaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 신청서-원천 데이터 관계(Cappla) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA의 {@link JpaRepository}를 상속하여 기본 CRUD 기능을 제공하며, 신청서와 원천 데이터 간의 연결 관계를 조회하는 특화
 * 메서드를 제공합니다.
 *
 * <p>기본키 타입: {@link CapplaId} (신청서식별번호 + 신청서일련번호)
 *
 * <p>주요 활용:
 *
 * <ul>
 *   <li>특정 원천 데이터(프로젝트, 전산관리비 등)에 연결된 신청서 조회
 *   <li>결재중/결재완료 상태의 신청서 존재 여부 확인 (수정/삭제 제약)
 * </ul>
 */
public interface ApplicationMapRepository extends JpaRepository<Cappla, CapplaId> {

    /** 결재 응답 조립에 필요한 신청서 연결 최소 필드입니다. */
    interface ApplicationMapView {
        String getApfDcmNo();

        String getPkColNm();

        Integer getFntTbCrySno();
    }

    /**
     * 원천 테이블·키·일련번호에 연결된 신청서를 최신 문서번호 순으로 조회합니다.
     *
     * @param fntTbNm 원천 테이블명
     * @param pkColNm 원천 데이터 키
     * @param fntTbCrySno 원천 데이터 일련번호
     * @return 최신 문서번호가 먼저인 신청서 연결 view 목록
     */
    java.util.List<ApplicationMapView>
            findViewsByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                    String fntTbNm, String pkColNm, Integer fntTbCrySno);

    /**
     * 여러 원천 데이터 키에 연결된 신청서를 최신 문서번호 순으로 조회합니다.
     *
     * @param fntTbNm 원천 테이블명
     * @param pkColNms 원천 데이터 키 목록
     * @return 최신 문서번호가 먼저인 신청서 연결 view 목록
     */
    java.util.List<ApplicationMapView> findViewsByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
            String fntTbNm, java.util.List<String> pkColNms);

    /**
     * 원천 테이블명, PK컬럼명, 적재SNO로 신청서 관계 목록 조회 (최신 신청서 우선).
     *
     * <p>정렬 기준은 {@code APF_DCM_NO DESC}. 신청식별번호 포맷이 {@code APF-{YYYY}-{8자리 시퀀스}}이므로 사전식 내림차순이 시간
     * 역순과 일치합니다.
     *
     * @param fntTbNm 원천 테이블명 (예: 'BPROJM'=정보화사업)
     * @param pkColNm 원천 데이터의 PK 컬럼명
     * @param fntTbCrySno 원천 데이터의 적재 일련번호
     * @return 관련 신청서 관계 목록 (최신 신청서가 첫 번째)
     */
    java.util.List<Cappla> findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
            String fntTbNm, String pkColNm, Integer fntTbCrySno);

    /**
     * 여러 원천 데이터 PK에 대해 신청서 관계 목록 일괄 조회 (최신 신청서 우선).
     *
     * @param fntTbNm 원천 테이블명 (예: 'BPROJM', 'BCOSTM')
     * @param pkColNms 원천 데이터 PK 컬럼명 목록
     * @return 관련 신청서 관계 목록 (최신 신청서가 첫 번째)
     */
    java.util.List<Cappla> findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
            String fntTbNm, java.util.List<String> pkColNms);

    /**
     * 신청서 식별번호와 원천 테이블명으로 신청서 관계 목록 조회
     *
     * @param apfDcmNo 신청서 식별번호
     * @param fntTbNm 원천 테이블명
     * @return 해당 신청서에 연결된 원천 데이터 목록
     */
    java.util.List<Cappla> findByApfDcmNoAndFntTbNm(String apfDcmNo, String fntTbNm);

    /**
     * 신청서번호로 연결된 활성 원장 매핑을 조회합니다.
     *
     * <p>반입 원본 파일의 열람 권한 판정이 씁니다. 파일의 부모는 신청서번호이고, 판정 기준은 그 신청서가 가리키는 원장의 주관부서이기 때문입니다.
     *
     * <p>권한 판정은 실패 시 거부(fail closed)여야 하므로 논리 삭제된 매핑을 제외합니다. 매핑이 삭제되거나 다른 원장으로 재매핑된 뒤에도 구 부서 사용자가
     * 원본을 계속 열람하는 경로를 막습니다(SEC-15).
     *
     * @param apfDcmNo 신청서식별번호
     * @param delYn 삭제여부. 권한 판정은 {@code "N"}만 사용합니다
     * @return 연결된 활성 매핑 목록. 없으면 빈 목록
     */
    java.util.List<Cappla> findByApfDcmNoAndDelYn(String apfDcmNo, String delYn);

    /**
     * 원천 데이터에 특정 상태의 신청서가 존재하는지 확인
     *
     * <p>원천 테이블명, PK컬럼명, 적재SNO 조건으로 Cappla와 Capplm을 조인하여 지정한 상태 목록({@code statuses})에 해당하는 신청서가
     * 존재하는지 확인합니다.
     *
     * <p>주요 사용처: 프로젝트/전산관리비 수정·삭제 전 결재중 또는 결재완료 여부 검사
     *
     * @param fntTbNm 원천 테이블명 (예: 'BPROJM')
     * @param pkColNm 원천 데이터의 PK 컬럼명
     * @param fntTbCrySno 원천 데이터의 적재 일련번호
     * @param statuses 확인할 신청서 상태코드 목록 (예: ["01"(결재중), "02"(결재완료)])
     * @return 해당 조건의 신청서가 존재하면 true, 없으면 false
     */
    @Query(
            """
                        SELECT COUNT(c) > 0
                        FROM Cappla c
                        JOIN Capplm m ON c.apfDcmNo = m.apfMngNo
                        WHERE c.fntTbNm = :fntTbNm
                        AND c.pkColNm = :pkColNm
                        AND c.fntTbCrySno = :fntTbCrySno
                        AND m.itPtlApfPrgStsC IN :statuses
                        """)
    boolean existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
            @Param("fntTbNm") String fntTbNm,
            @Param("pkColNm") String pkColNm,
            @Param("fntTbCrySno") Integer fntTbCrySno,
            @Param("statuses") java.util.List<String> statuses);
}
