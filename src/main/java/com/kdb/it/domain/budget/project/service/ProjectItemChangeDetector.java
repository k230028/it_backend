package com.kdb.it.domain.budget.project.service;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * 정보화사업 품목(TPRMPP_BITEMM)의 업무 필드 변경 여부 판정 헬퍼.
 *
 * <p>{@link ProjectService}의 품목 동기화 분기가 UPDATE와 감사 변경 로그 생성을 건너뛸지 결정할 때만 사용합니다. 리포지토리·트랜잭션에 의존하지 않는
 * 순수 비교 로직이므로 서비스 본문에서 분리해 두었습니다.
 */
final class ProjectItemChangeDetector {

    private ProjectItemChangeDetector() {}

    /**
     * 품목 변경 여부 판단
     *
     * <p>기존 엔티티와 요청 DTO의 업무 필드를 비교하여, 하나라도 다르면 {@code true}를 반환합니다.
     *
     * <p>BigDecimal 필드(xcr, gclQty, gclAmt)는 scale 무관한 수치 비교를 위해 compareTo를 사용합니다.
     *
     * @param existing 현재 활성 품목 엔티티 (DEL_YN='N')
     * @param dto 클라이언트로부터 전달된 수정 요청 DTO
     * @return 변경된 필드가 하나라도 있으면 {@code true}
     */
    static boolean isItemChanged(Bitemm existing, ProjectDto.BitemmDto dto) {
        return !Objects.equals(existing.getIoeC(), dto.getIoeC())
                || !Objects.equals(existing.getGclNm(), dto.getGclNm())
                || bigDecimalChanged(existing.getQty(), dto.getQty())
                || !Objects.equals(existing.getCurC(), dto.getCurC())
                || bigDecimalChanged(existing.getXcr(), dto.getXcr())
                || !Objects.equals(existing.getXcrBseDt(), dto.getXcrBseDt())
                || !Objects.equals(existing.getCncdFdtnCone(), dto.getCncdFdtnCone())
                || !Objects.equals(existing.getBseYm(), dto.getBseYm())
                || !Objects.equals(existing.getDfrCleC(), dto.getDfrCleC())
                || !Objects.equals(existing.getSectSysUtzYn(), dto.getSectSysUtzYn())
                || !Objects.equals(existing.getItrInfrYn(), dto.getItrInfrYn())
                || bigDecimalChanged(existing.getAmt(), dto.getAmt())
                || bigDecimalChanged(existing.getMplAmt(), dto.getMplAmt())
                // fcAmt 변경 시 D/C 이력 생성 (null-safe 비교)
                || bigDecimalChanged(existing.getFcAmt(), dto.getFcAmt());
    }

    /** scale이 달라도 같은 수치면 변경으로 보지 않는 null-safe 비교. */
    private static boolean bigDecimalChanged(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return false;
        if (a == null || b == null) return true;
        return a.compareTo(b) != 0;
    }
}
