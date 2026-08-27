package com.kdb.it.domain.migration.service;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.domain.MigrationApprovalMarker;
import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이관한 원장에 신청서 받이를 붙입니다.
 *
 * <p>일반 이관은 결재완료 상태를 기본으로 사용하고, 편성요청서 반입처럼 업무 계약이 다른 호출자는 상태를 명시합니다. 결재선({@code TPRMPP_CDECIM})은
 * 만들지 않아 실제 결재를 거친 신청서와 구분됩니다.
 *
 * <p>신청서번호는 기존 {@code APF-{연도}-{8자리}} 형식을 그대로 씁니다({@link ApplicationRepository#getNextVal()}과 같은
 * 시퀀스·형식). {@code ApplicationMapRepository}가 신청서번호 사전식 내림차순을 시간순으로 전제하므로(최신 문서번호가 먼저 오는 정렬 쿼리들) 별도
 * 접두어를 쓰면 이관 문서가 항상 최신으로 정렬되어 그 전제가 깨집니다. 대신 이관 출처는 제목과 등록자결재요청내용에 남깁니다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MigrationApprovalStamper {

    private final ApplicationRepository applicationRepository;
    private final ApplicationMapRepository applicationMapRepository;

    /**
     * 원천 한 건에 결재완료 상태의 받이를 만듭니다.
     *
     * <p>기존 이관 호출의 호환 경로이며 결재완료({@link ApprovalStatus#COMPLETED})를 기본값으로 사용합니다.
     *
     * <p>{@code TPRMPP_CAPPLM}, {@code TPRMPP_CAPPLA} 모두 최초등록자(FST_ENR_USID)·최종변경자(LST_CHG_USID)가
     * 물리 NOT NULL입니다. 이 두 필드는 보통 {@code JpaAuditConfig}의 {@code AuditorAware}가 인증된 {@code
     * SecurityContext}에서 채우지만, 이관 배치는 로그인 세션 없이 실행될 수 있어 그 경로에 기대면 값이 비어 저장 시 {@code ORA-01400}(NOT
     * NULL 위반)으로 실패합니다. 그래서 이 메서드는 두 엔티티 모두 감사자 필드를 {@code actorEno}로 직접 채워 인증 컨텍스트 유무와 무관하게
     * 안전합니다({@code AuditingHandler}는 {@code AuditorAware}가 빈 값을 반환하면 아무 것도 덮어쓰지 않으므로, 인증된 흐름에서 호출해도
     * 이 값이 유실되지 않습니다). {@link com.kdb.it.domain.entity.BaseEntity#initializeAuditActors(String)}와
     * 같은 목적이지만 이 클래스는 엔티티의 하위 클래스가 아니라 {@code protected} 메서드를 호출할 수 없어, 대신 {@code @SuperBuilder}가
     * 노출하는 {@code fstEnrUsid}/{@code lstChgUsid} 빌더 프로퍼티를 직접 설정합니다. 이 대입을 "중복"으로 보고 제거하면 무인 실행 경로에서
     * 다시 {@code ORA-01400}이 재발하므로 지우지 않습니다.
     *
     * @param fntTbNm 원천테이블명 — {@code BCOSTM}(전산업무비) 또는 {@code BPROJM}(정보화사업)
     * @param pkColNm 원천 PK (전산업무비코드 또는 사업관리번호)
     * @param fntTbCrySno 원천 일련번호
     * @param title 결재요청제목 (이관임을 알 수 있게 조립해 넘깁니다)
     * @param actorEno 업로드 사용자 사번 (결재요청사용자ID 및 두 엔티티의 감사자 필드로 기록)
     * @param bseYy 예산연도 (신청서번호 연도부에 씁니다)
     * @return 생성한 신청서식별번호 ({@code APF-{연도}-{8자리}})
     * @throws org.springframework.dao.DataAccessException 시퀀스 조회 또는 저장 중 DB 접근이 실패한 경우 (예: 연결 끊김,
     *     제약 위반). {@code @Transactional}이 적용되어 있어 신청서 마스터·연결 중 하나라도 실패하면 둘 다 롤백됩니다.
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
     * 원천 한 건에 호출자가 지정한 상태의 이관 신청서 받이를 만듭니다.
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
        Long sequence = applicationRepository.getNextVal();
        String apfDcmNo = String.format("APF-%s-%08d", bseYy, sequence);

        Capplm application =
                Capplm.builder()
                        .apfMngNo(apfDcmNo)
                        .itPtlApfPrgStsC(status.code())
                        .dcdReqTtl(title)
                        .dcdReqUsid(actorEno)
                        .dcdReqDtm(LocalDate.now())
                        .rgprDcdReqCone(MigrationApprovalMarker.NOTE)
                        .fstEnrUsid(actorEno)
                        .lstChgUsid(actorEno)
                        .build();
        applicationRepository.save(application);

        Cappla applicationMap =
                Cappla.builder()
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
}
