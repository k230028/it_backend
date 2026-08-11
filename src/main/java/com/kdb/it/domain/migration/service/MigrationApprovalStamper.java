package com.kdb.it.domain.migration.service;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이관한 원장에 결재완료 받이를 붙입니다.
 *
 * <p>예산 편성·집계 조회는 결재완료 신청서가 {@code CAPPLA}로 연결된 원장만 집계하므로(§3.6), 받이가 없으면 이관 데이터가 화면에서 0으로 보입니다.
 * 결재선({@code TPRMPP_CDECIM})은 만들지 않아 실제 결재를 거친 신청서와 구분됩니다.
 *
 * <p>신청서번호는 기존 {@code APF-{연도}-{8자리}} 형식을 그대로 씁니다({@link ApplicationRepository#getNextVal()}과 같은
 * 시퀀스·형식). {@code ApplicationMapRepository}가 신청서번호 사전식 내림차순을 시간순으로 전제하므로(최신 문서번호가 먼저 오는 정렬 쿼리들) 별도
 * 접두어를 쓰면 이관 문서가 항상 최신으로 정렬되어 그 전제가 깨집니다. 대신 이관 출처는 제목과 등록자결재요청내용에 남깁니다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MigrationApprovalStamper {

    /** 이관으로 생성된 결재완료 기록임을 등록자결재요청내용에 남기는 고정 문구입니다. */
    private static final String MIGRATION_NOTE = "수기 엑셀 이관으로 생성된 결재완료 기록입니다. 실제 결재선을 거치지 않았습니다.";

    private final ApplicationRepository applicationRepository;
    private final ApplicationMapRepository applicationMapRepository;

    /**
     * 원천 한 건에 결재완료 받이를 만듭니다.
     *
     * <p>결재중 상태를 거치지 않고 곧바로 결재완료({@link ApprovalStatus#COMPLETED}) 신청서 마스터를 생성한 뒤, 원천 데이터와의
     * 연결({@link Cappla})을 같은 신청서번호로 저장합니다.
     *
     * @param fntTbNm 원천테이블명 — {@code BCOSTM}(전산업무비) 또는 {@code BPROJM}(정보화사업)
     * @param pkColNm 원천 PK (전산업무비코드 또는 사업관리번호)
     * @param fntTbCrySno 원천 일련번호
     * @param title 결재요청제목 (이관임을 알 수 있게 조립해 넘깁니다)
     * @param actorEno 업로드 사용자 사번 (결재요청사용자ID로 기록)
     * @param bseYy 예산연도 (신청서번호 연도부에 씁니다)
     * @return 생성한 신청서식별번호 ({@code APF-{연도}-{8자리}})
     */
    public String stamp(
            String fntTbNm,
            String pkColNm,
            Integer fntTbCrySno,
            String title,
            String actorEno,
            String bseYy) {
        Long sequence = applicationRepository.getNextVal();
        String apfDcmNo = String.format("APF-%s-%08d", bseYy, sequence);

        Capplm application =
                Capplm.builder()
                        .apfMngNo(apfDcmNo)
                        .itPtlApfPrgStsC(ApprovalStatus.COMPLETED.code())
                        .dcdReqTtl(title)
                        .dcdReqUsid(actorEno)
                        .dcdReqDtm(LocalDate.now())
                        .rgprDcdReqCone(MIGRATION_NOTE)
                        .build();
        applicationRepository.save(application);

        Cappla applicationMap =
                Cappla.builder()
                        .apfDcmNo(apfDcmNo)
                        .fntTbNm(fntTbNm)
                        .pkColNm(pkColNm)
                        .fntTbCrySno(fntTbCrySno)
                        .build();
        applicationMapRepository.save(applicationMap);

        return apfDcmNo;
    }
}
