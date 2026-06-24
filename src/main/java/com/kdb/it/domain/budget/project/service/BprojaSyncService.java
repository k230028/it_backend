package com.kdb.it.domain.budget.project.service;

import com.kdb.it.domain.budget.project.entity.Bproja;
import com.kdb.it.domain.budget.project.entity.BprojaId;
import com.kdb.it.domain.budget.project.repository.BprojaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 정보화사업관계(BPROJA) 동기화 서비스.
 *
 * <p>각 업무 단계 서비스가 문서 생성/상태변경/삭제 시 호출하는 단일 진입점이다.
 * {@code (프로젝트 ABUS_MNG_NO, 단계 자기 key, 통합 IT_PTL_STS_TC)}를 upsert 하거나
 * 단계 문서 삭제 시 대응 BPROJA 행을 Soft Delete 한다. 멱등·방어적이며, 호출자 트랜잭션에
 * 참여하므로 단계 작업이 롤백되면 BPROJA 변경도 함께 롤백된다.</p>
 */
@Service
@RequiredArgsConstructor
public class BprojaSyncService {

    private final BprojaRepository bprojaRepository;

    /**
     * BPROJA upsert: 존재하면 상태 갱신 + DEL_YN='N' 복원, 없으면 INSERT.
     *
     * @param abusMngNo  프로젝트관리번호(실제 BPROJM 사업). null/blank면 no-op.
     * @param cncdRfrNo  단계 원본문서 key. null/blank면 no-op.
     * @param itPtlStsTc 통합 IT포탈상태구분코드. null이면 no-op.
     */
    @Transactional
    public void upsert(String abusMngNo, String cncdRfrNo, String itPtlStsTc) {
        if (!StringUtils.hasText(abusMngNo) || !StringUtils.hasText(cncdRfrNo) || itPtlStsTc == null) {
            return;
        }
        bprojaRepository.findById(new BprojaId(abusMngNo, cncdRfrNo))
                .ifPresentOrElse(
                        existing -> {
                            existing.changeStatus(itPtlStsTc); // 상태 최신화
                            existing.restore();                 // DEL_YN='N' 복원(재활성)
                        },
                        () -> bprojaRepository.save(Bproja.builder()
                                .abusMngNo(abusMngNo)
                                .cncdRfrNo(cncdRfrNo)
                                .stsTc(itPtlStsTc)
                                .build()));
    }

    /**
     * 단계 문서 Soft Delete 시 대응 BPROJA 행을 DEL_YN='Y' 처리. 없으면 no-op.
     *
     * @param abusMngNo 프로젝트관리번호. null/blank면 no-op.
     * @param cncdRfrNo 단계 원본문서 key. null/blank면 no-op.
     */
    @Transactional
    public void softDelete(String abusMngNo, String cncdRfrNo) {
        if (!StringUtils.hasText(abusMngNo) || !StringUtils.hasText(cncdRfrNo)) {
            return;
        }
        bprojaRepository.findById(new BprojaId(abusMngNo, cncdRfrNo))
                .ifPresent(Bproja::delete);
    }
}
