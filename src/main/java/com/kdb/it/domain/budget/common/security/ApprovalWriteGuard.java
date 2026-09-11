package com.kdb.it.domain.budget.common.security;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.system.security.OwnershipVerifier;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 연결된 신청서의 결재 상태로 예산 문서의 쓰기(수정·삭제) 가능 여부를 판정합니다.
 *
 * <p>정보화사업·경상사업({@code BPROJM})과 전산업무비({@code BCOSTM})가 같은 규칙을 쓰도록 판정을 한 곳에 모았습니다. 재상신 개정본이 생기면서
 * 관리번호 하나에 여러 순번이 존재하므로 판정은 반드시 {@code (관리번호, 순번)} 단위로 합니다 — 관리번호만으로 보면 구버전의 결재완료가 새 초안의 수정을 막거나,
 * 반대로 결재중인 초안이 열려 있는 것처럼 보입니다.
 */
@Component
@RequiredArgsConstructor
public class ApprovalWriteGuard {

    private final ApplicationMapRepository applicationMapRepository;

    /**
     * 결재 상태 때문에 쓰기가 막히는지 판정합니다.
     *
     * <p>결재중(1)은 결재선이 지금 검토 중인 내용이라 누구도 바꿀 수 없습니다. 결재완료(2)·수기등록(9)은 시스템관리자에게만 사후 정정을 허용합니다.
     * 반려(3)·회수(4)는 수정 후 재상신해야 하므로 모든 사용자에게 열어 둡니다 — 관리자 판정은 {@link
     * OwnershipVerifier#isCurrentUserAdmin()}에 위임합니다.
     *
     * @param fntTbNm 원본 테이블명 ({@code "BPROJM"} 또는 {@code "BCOSTM"})
     * @param pkColNm 관리번호 (CAPPLA.PK_COL_NM)
     * @param sno 개정 순번 (CAPPLA.FNT_TB_CRY_SNO)
     * @return 현재 사용자 기준으로 쓰기가 막히면 true
     */
    public boolean isBlocked(String fntTbNm, String pkColNm, Integer sno) {
        return applicationMapRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                fntTbNm, pkColNm, sno, blockingStatuses());
    }

    /**
     * 현재 사용자 기준으로 쓰기를 막는 결재상태 코드 목록입니다.
     *
     * <p>정보화사업 서비스가 자체 조회 경로에서 같은 규칙을 재사용할 수 있도록 공개합니다.
     *
     * @return 차단 대상 결재상태 코드
     */
    public static List<String> blockingStatuses() {
        return OwnershipVerifier.isCurrentUserAdmin()
                ? List.of(ApprovalStatus.IN_PROGRESS.code())
                : List.of(
                        ApprovalStatus.IN_PROGRESS.code(),
                        ApprovalStatus.COMPLETED.code(),
                        ApprovalStatus.MANUAL.code());
    }

    /**
     * 쓰기가 막혀 있으면 사유를 담아 중단합니다.
     *
     * @param fntTbNm 원본 테이블명
     * @param pkColNm 관리번호
     * @param sno 개정 순번
     * @param action 막힌 동작 이름 ("수정" 또는 "삭제")
     * @throws IllegalStateException 결재 상태 때문에 쓰기가 막힌 경우
     */
    public void verifyWritable(String fntTbNm, String pkColNm, Integer sno, String action) {
        if (isBlocked(fntTbNm, pkColNm, sno)) {
            throw new IllegalStateException(blockMessage(action));
        }
    }

    /** 최신 신청서가 이 상태이면 원천 개정본을 삭제할 수 있습니다. 반려·회수는 재상신 전 초안이므로 작성완료와 같이 취급합니다. */
    private static final List<String> DELETABLE_STATUSES =
            List.of(
                    ApprovalStatus.DRAFTED.code(),
                    ApprovalStatus.REJECTED.code(),
                    ApprovalStatus.RECALLED.code());

    /**
     * 원천 개정본이 임시저장(신청서 없음)·작성완료·반려·회수 상태인지 확인합니다.
     *
     * <p>삭제는 사후 정정 목적의 일반 수정과 달리 복구하기 어려우므로 관리자도 같은 상태 제한을 적용합니다. 결재중(1)은 결재선이 검토 중이고
     * 결재완료(2)·수기등록(9)은 확정된 예산이므로 삭제할 수 없습니다.
     *
     * @param fntTbNm 원본 테이블명
     * @param pkColNm 관리번호
     * @param sno 개정 순번
     * @throws IllegalStateException 최신 신청서가 결재중·결재완료·수기등록 상태인 경우
     */
    public void verifyDeletable(String fntTbNm, String pkColNm, Integer sno) {
        applicationMapRepository
                .findLatestApplicationStatus(fntTbNm, pkColNm, sno)
                .filter(status -> !DELETABLE_STATUSES.contains(status))
                .ifPresent(
                        status -> {
                            throw new IllegalStateException("임시저장·작성완료·반려·회수 상태의 문서만 삭제할 수 있습니다.");
                        });
    }

    /**
     * 결재 상태 차단 안내 문구를 만듭니다.
     *
     * <p>관리자는 결재완료가 차단 사유에서 빠지므로 사유를 결재중으로만 알립니다.
     *
     * @param action 막힌 동작 이름
     * @return 사용자에게 보일 안내 문구
     */
    public static String blockMessage(String action) {
        return OwnershipVerifier.isCurrentUserAdmin()
                ? "결재중인 문서는 " + action + "할 수 없습니다."
                : "결재중·결재완료·수기등록 문서는 " + action + "할 수 없습니다.";
    }
}
