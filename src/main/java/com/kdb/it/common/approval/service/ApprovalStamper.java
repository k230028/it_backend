package com.kdb.it.common.approval.service;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.domain.MigrationApprovalMarker;
import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결재선 없는 신청서 받이를 원천에 붙입니다.
 *
 * <p>두 용도가 있습니다. 엑셀 반입은 결재완료·수기등록 상태의 받이를 만들고({@link #stamp}), 작성 화면의 [저장]은 작성완료({@code 0}) 받이를
 * 만듭니다({@link #stampDrafted}). 결재선({@code TPRMPP_CDECIM})은 만들지 않아 실제 결재를 거친 신청서와 구분됩니다.
 *
 * <p>신청서번호는 기존 {@code APF-{연도}-{8자리}} 형식을 그대로 씁니다. {@code ApplicationMapRepository}가 신청서번호 사전식
 * 내림차순을 시간순으로 전제하므로 별도 접두어를 쓰지 않습니다.
 *
 * <p>연도부의 출처는 두 용도가 다릅니다. 엑셀 반입({@link #stamp})은 원천의 예산연도({@code bseYy})를 그대로 씁니다. 반면 작성 화면의
 * [저장]({@link #stampDrafted})은 이후 {@code ApplicationService#submit}이 상신 시점에 현재 연도로 채번하는 것과 어긋나지 않도록
 * 항상 현재 연도로 채번합니다. 9월 이후에는 기본 예산연도가 내년이 되어, 예산연도로 채번하면 저장 시점의 번호가 이후 상신 시점의 번호보다 사전식으로 더 커져 "최신
 * 신청서" 판정이 영구히 저장 시점 것으로 고정되는 문제가 있었습니다.
 *
 * <p>{@code TPRMPP_CAPPLM}, {@code TPRMPP_CAPPLA} 모두 최초등록자·최종변경자가 물리 NOT NULL이고 이관 배치는 로그인 세션 없이
 * 실행될 수 있어, JPA Auditing에 기대지 않고 {@code actorEno}로 두 필드를 직접 채웁니다. 이 대입을 지우면 무인 실행에서 {@code
 * ORA-01400}이 재발합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ApprovalStamper {

    private final ApplicationRepository applicationRepository;
    private final ApplicationMapRepository applicationMapRepository;

    /**
     * 원천 한 건에 결재완료 상태의 이관 받이를 만듭니다.
     *
     * @param fntTbNm 원천테이블명 — {@code BCOSTM} 또는 {@code BPROJM}
     * @param pkColNm 원천 PK
     * @param fntTbCrySno 원천 일련번호
     * @param title 결재요청제목
     * @param actorEno 업로드 사용자 사번
     * @param bseYy 예산연도 (신청서번호 연도부)
     * @return 생성한 신청서식별번호
     */
    public String stamp(
            String fntTbNm,
            String pkColNm,
            Integer fntTbCrySno,
            String title,
            String actorEno,
            String bseYy) {
        return stamp(
                fntTbNm, pkColNm, fntTbCrySno, title, actorEno, bseYy, ApprovalStatus.COMPLETED);
    }

    /**
     * 원천 한 건에 호출자가 지정한 상태의 이관 받이를 만듭니다. 등록자결재요청내용에 {@link MigrationApprovalMarker#NOTE}를 남깁니다.
     *
     * @param status 생성할 신청서 진행상태
     * @return 생성한 신청서식별번호
     */
    public String stamp(
            String fntTbNm,
            String pkColNm,
            Integer fntTbCrySno,
            String title,
            String actorEno,
            String bseYy,
            ApprovalStatus status) {
        String year = (bseYy == null || bseYy.isBlank()) ? currentYear() : bseYy;
        return create(
                fntTbNm,
                pkColNm,
                fntTbCrySno,
                title,
                actorEno,
                null,
                year,
                status,
                MigrationApprovalMarker.NOTE,
                LocalDate.now());
    }

    /**
     * 작성 화면의 [저장]에 대응하는 작성완료({@code 0}) 받이를 만들거나 갱신합니다.
     *
     * <p>같은 원천 개정본에 연결된 최신 신청서가 이미 작성완료면 제목만 갱신해 멱등하게 동작하고, 결재중이면 저장을 거부합니다. 신청서가 없거나
     * 결재완료·반려·회수·수기등록이면 새 작성완료 신청서를 만듭니다. 결재요청일시는 비워 두고 실제 상신 신청서에만 기록합니다.
     *
     * @param fntTbNm 원천테이블명 — {@code BCOSTM} 또는 {@code BPROJM}
     * @param pkColNm 관리번호
     * @param fntTbCrySno 개정 순번
     * @param title 결재요청제목. 비면 관리번호를 씁니다
     * @param actorEno 저장 사용자 사번
     * @param bbrC 결재요청부점코드 (원천의 주관부서)
     * @param bseYy 예산연도. 신청서번호 채번에는 쓰지 않습니다 — 작성완료는 이후 {@code ApplicationService#submit}의 상신 채번과
     *     시간순이 어긋나지 않도록 항상 현재 연도로 채번합니다(§클래스 JavaDoc). 호출자 시그니처를 유지하기 위해 파라미터는 남겨 두되 본문에서는 사용하지
     *     않습니다.
     * @return 작성완료 신청서식별번호
     * @throws IllegalStateException 최신 신청서가 결재중인 경우
     */
    public String stampDrafted(
            String fntTbNm,
            String pkColNm,
            Integer fntTbCrySno,
            String title,
            String actorEno,
            String bbrC,
            String bseYy) {
        List<Cappla> links =
                applicationMapRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                        fntTbNm, pkColNm, fntTbCrySno);
        if (!links.isEmpty()) {
            Capplm latest = applicationRepository.findById(links.get(0).getApfDcmNo()).orElse(null);
            if (latest != null) {
                String status = latest.getItPtlApfPrgStsC();
                if (ApprovalStatus.IN_PROGRESS.code().equals(status)) {
                    throw new IllegalStateException("결재중인 문서는 저장할 수 없습니다.");
                }
                if (ApprovalStatus.DRAFTED.code().equals(status)) {
                    latest.renewDraft(resolveTitle(title, pkColNm));
                    return latest.getApfMngNo();
                }
            }
        }
        return create(
                fntTbNm,
                pkColNm,
                fntTbCrySno,
                title,
                actorEno,
                bbrC,
                currentYear(),
                ApprovalStatus.DRAFTED,
                null,
                null);
    }

    private String create(
            String fntTbNm,
            String pkColNm,
            Integer fntTbCrySno,
            String title,
            String actorEno,
            String bbrC,
            String year,
            ApprovalStatus status,
            String note,
            LocalDate requestDate) {
        Long sequence = applicationRepository.getNextVal();
        String apfDcmNo = String.format("APF-%s-%08d", year, sequence);

        Capplm application =
                Capplm.builder()
                        .apfMngNo(apfDcmNo)
                        .itPtlApfPrgStsC(status.code())
                        .dcdReqTtl(resolveTitle(title, pkColNm))
                        .dcdReqUsid(actorEno)
                        .dcdReqBbrC(bbrC)
                        .dcdReqDtm(requestDate)
                        .rgprDcdReqCone(note)
                        .fstEnrUsid(actorEno)
                        .lstChgUsid(actorEno)
                        .build();
        applicationRepository.save(application);

        // 스탬프 신청서는 원천 한 건만 연결하므로 신청서일련번호는 1이다.
        Cappla applicationMap =
                Cappla.builder()
                        .apfSno(1L)
                        .apfDcmNo(apfDcmNo)
                        .fntTbNm(fntTbNm)
                        .pkColNm(pkColNm)
                        .fntTbCrySno(fntTbCrySno)
                        .fstEnrUsid(actorEno)
                        .lstChgUsid(actorEno)
                        .build();
        applicationMapRepository.save(applicationMap);

        return apfDcmNo;
    }

    private static String resolveTitle(String title, String fallback) {
        return (title == null || title.isBlank()) ? fallback : title;
    }

    private static String currentYear() {
        return String.valueOf(LocalDate.now().getYear());
    }
}
