package com.kdb.it.domain.budget.project.service;

import static com.kdb.it.domain.budget.project.service.ProjectItemChangeDetector.isItemChanged;

import com.kdb.it.common.code.CodeDefaults;
import com.kdb.it.common.util.DateFormatUtil;
import com.kdb.it.domain.budget.cost.util.BudgetAmountCalculator;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 정보화사업 품목({@link Bitemm}) 동기화 협력자 — BE-35.
 *
 * <p>{@link ProjectService}의 등록·수정 경로가 공유하는 품목 CUD 블록을 모읍니다. 채번·환율 표준 조회·외화 재계산·엔티티 조립이 두 경로에 나뉘어
 * 있으면 한쪽에서만 정규화가 빠지는 식으로 조용히 갈라지므로, 품목을 만드는 경로 자체를 이 클래스로 좁힙니다.
 *
 * <p><b>Spring 빈이 아닙니다.</b> {@link ProjectService}가 호출 시점에 직접 만들어 씁니다. 빈으로 주입하면 {@code
 * ProjectServiceTest}가 이 협력자를 mock으로 대체하게 되어 품목 동기화 검증이 조용히 무력화됩니다(같은 이유로 {@link
 * ProjectItemChangeDetector}·{@code ProjectResponseMapper}도 주입 대상이 아닙니다). 협력자를 실물로 유지해야 기존 단위 테스트가
 * 실제 동작을 계속 검증합니다.
 *
 * <p>수정 경로의 동기화 규칙:
 *
 * <ol>
 *   <li>요청의 {@code gclMngNo}가 있으면 기존 활성 레코드를 제자리 수정(Dirty Checking) — 새 레코드를 추가하지 않는다
 *   <li>요청의 {@code gclMngNo}가 없으면 신규 항목 추가
 *   <li>요청에 없는 기존 항목은 Soft Delete({@code DEL_YN='Y'})
 * </ol>
 */
final class ProjectItemSynchronizer {

    /** 품목 데이터 접근 리포지토리 (TPRMPP_BITEMM) */
    private final ProjectItemRepository itemRepository;

    /** 환율 표준 조회 헬퍼: 외화 품목 저장 전 Ccodem 단일 원천으로 xcr 덮어쓰기 (CONTEXT.md 결정 E / R3.7) */
    private final XcrLookupService xcrLookupService;

    ProjectItemSynchronizer(
            ProjectItemRepository itemRepository, XcrLookupService xcrLookupService) {
        this.itemRepository = itemRepository;
        this.xcrLookupService = xcrLookupService;
    }

    /**
     * 신규 등록 경로 — 요청 품목을 모두 새 품목으로 채번해 저장합니다.
     *
     * @param project 소속 사업 (영속 상태, abusMngNo·sno 스냅샷용)
     * @param items 요청 품목 목록. null 또는 빈 목록이면 아무것도 하지 않습니다
     */
    void createAll(Bprojm project, List<ProjectDto.BitemmDto> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        int gclSno = 0; // 품목일련번호 (1부터 시작)
        for (ProjectDto.BitemmDto itemDto : items) {
            validateItemAmounts(itemDto);
            String gclMngNo = nextGclMngNo();
            BigDecimal[] reconciled = resolveXcrAndReconcile(itemDto);
            itemRepository.save(buildBitemm(itemDto, gclMngNo, ++gclSno, project, reconciled));
        }
    }

    /**
     * 수정 경로 — 요청 품목과 기존 활성 품목을 병합합니다.
     *
     * <p>기존 활성 품목(DEL_YN='N')을 먼저 읽어 요청과 대조합니다. 요청이 관리번호를 지목한 항목은 제자리 수정(변경이 있을 때만), 지목하지 않은 항목은 신규
     * 추가, 요청에 없는 기존 항목은 논리 삭제합니다.
     *
     * @param project 소속 사업 (영속 상태)
     * @param items 요청 품목 목록. null이면 품목을 손대지 않습니다(빈 목록은 전체 논리 삭제를 뜻합니다)
     */
    void sync(Bprojm project, List<ProjectDto.BitemmDto> items) {
        if (items == null) {
            return;
        }

        // 1. 기존 품목 조회 (DEL_YN='N')
        List<Bitemm> existingItems =
                itemRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                        project.getAbusMngNo(), project.getSno(), "N");

        // 처리된 품목 관리번호 추적 (삭제 대상 식별용)
        Set<String> processedGclMngNos = new HashSet<>();
        // 현재 최대 SNO 계산 (신규 추가 시 MAX+1로 설정)
        int maxGclSno = existingItems.stream().mapToInt(Bitemm::getSno).max().orElse(0);

        // 2. 요청 품목 처리 (수정 또는 신규 추가)
        for (ProjectDto.BitemmDto itemDto : items) {
            validateItemAmounts(itemDto);
            if (itemDto.getGclMngNo() != null && !itemDto.getGclMngNo().isEmpty()) {
                updateExisting(existingItems, itemDto, processedGclMngNos);
            } else {
                String gclMngNo = nextGclMngNo();
                BigDecimal[] reconciled = resolveXcrAndReconcile(itemDto);
                // 조립은 buildBitemm 한 곳으로 모은다 — 생성 경로와 필드가 조용히 갈라지지 않도록.
                // project는 prjMngNo로 조회한 엔티티이므로 abusMngNo 스냅샷도 동일하다.
                itemRepository.save(
                        buildBitemm(itemDto, gclMngNo, ++maxGclSno, project, reconciled));
            }
        }

        // 3. 요청에 없는 기존 품목 Soft Delete 처리
        for (Bitemm existingItem : existingItems) {
            if (!processedGclMngNos.contains(existingItem.getGclMngNo())) {
                existingItem.delete(); // BaseEntity.delete() → DEL_YN='Y'
            }
        }
    }

    /**
     * 요청이 지목한 기존 활성 품목을 제자리 수정합니다.
     *
     * <p>지목한 관리번호가 활성 목록에 없으면 아무것도 하지 않습니다(이미 삭제된 품목을 되살리지 않습니다). 변경된 필드가 없으면 UPDATE와 변경로그를 만들지 않되,
     * 처리 완료 표시는 남겨 3단계의 논리 삭제 대상에서 제외합니다.
     */
    private void updateExisting(
            List<Bitemm> existingItems,
            ProjectDto.BitemmDto itemDto,
            Set<String> processedGclMngNos) {
        // gclMngNo로 현재 활성(DEL_YN='N') 항목 찾기 (existingItems는 이미 DEL_YN='N' 필터됨)
        Bitemm existingItem =
                existingItems.stream()
                        .filter(item -> item.getGclMngNo().equals(itemDto.getGclMngNo()))
                        .findFirst()
                        .orElse(null);
        if (existingItem == null) {
            return;
        }

        // 비교 전에 정규화해 기존 NULL은 0으로 보정하고, 기존 값과 같은 음수도 검증을 우회하지 못하게 한다.
        BigDecimal normalizedPlannedAmount = normalizePlannedAmount(itemDto.getMplAmt());
        itemDto.setMplAmt(normalizedPlannedAmount);

        // 변경된 필드가 있을 때만 제자리 수정 (변경 없으면 UPDATE·로그 생성 생략)
        if (isItemChanged(existingItem, itemDto)) {
            BigDecimal[] reconciled = resolveXcrAndReconcile(itemDto);
            // 기존 활성 레코드를 제자리 수정 (버저닝 폐기 — 새 레코드를 추가하지 않는다).
            // PK(GCL_MNG_NO, SNO)와 연관 필드(ABUS_MNG_NO, FNT_TB_CRY_SNO)는 유지하고 업무 필드만 갱신.
            // Dirty Checking으로 트랜잭션 종료 시 UPDATE가 실행된다.
            existingItem.update(
                    itemDto.getIoeC(), // 품목구분
                    itemDto.getGclNm(), // 품목명
                    itemDto.getQty(), // 품목수량
                    itemDto.getCurC(), // 통화
                    itemDto.getXcr(), // 환율
                    DateFormatUtil.toYmd8(itemDto.getXcrBseDt()), // 환율기준일자(yyyyMMdd 정규화)
                    itemDto.getCncdFdtnCone(), // 예산근거
                    toItdYm(itemDto.getBseYm()), // 도입시기
                    itemDto.getDfrCleC(), // 지급주기
                    itemDto.getSectSysUtzYn(), // 정보보호여부(미기재는 null 유지)
                    itemDto.getItrInfrYn(), // 통합인프라여부(미기재는 null 유지)
                    reconciled[0], // 당해 요청금액(원화, 서버 재계산)
                    reconciled[1], // 당해 외화 원금(외화 행에서만 유효)
                    normalizedPlannedAmount); // 내년 이후 요청금액(당해 금액과 독립)
        }
        processedGclMngNos.add(existingItem.getGclMngNo()); // 변경 여부와 무관하게 처리 완료 표시
    }

    /** Oracle 시퀀스로 품목관리번호를 채번합니다. 형식은 {@code GCL-{연도}-{seq:04d}}입니다. */
    private String nextGclMngNo() {
        Long gclSeq = itemRepository.getNextSequenceValue();
        return String.format("GCL-%s-%04d", LocalDate.now().getYear(), gclSeq);
    }

    /**
     * 환율을 표준값으로 덮어쓰고 외화 금액을 재계산합니다.
     *
     * <p>클라이언트가 보낸 xcr은 무시하고 Ccodem 단일 원천으로 덮어씁니다(CONTEXT.md 결정 E / R3.7). 그 환율로 {@code gclAmt =
     * fcAmt × xcr}을 정규화합니다(결정 C). 덮어쓴 xcr은 {@code itemDto}에 남으므로 이후 엔티티 조립·수정이 같은 값을 씁니다.
     *
     * @return {@link BudgetAmountCalculator#reconcileAmount}의 결과 [금액, 외화금액]
     */
    private BigDecimal[] resolveXcrAndReconcile(ProjectDto.BitemmDto itemDto) {
        itemDto.setXcr(xcrLookupService.resolveXcr(itemDto.getCurC(), LocalDate.now()));
        return BudgetAmountCalculator.reconcileAmount(
                itemDto.getFcAmt(), itemDto.getAmt(), itemDto.getCurC(), itemDto.getXcr());
    }

    /**
     * 품목 엔티티를 조립합니다.
     *
     * <p>채번(gclMngNo)·순번(gclSno)·환율 표준 조회·외화 재계산은 호출자가 먼저 수행하고, 그 결과만 이 메서드가 엔티티 필드로 옮겨 담습니다. 등록·수정
     * 두 경로가 별도로 필드를 나열하면 한쪽에서만 필드가 빠지거나 정규화가 생략되는 식으로 조용히 갈라질 수 있어, 조립 자체를 이 메서드 하나로 강제합니다.
     *
     * @param itemDto 품목 요청 DTO (xcr은 호출자가 이미 표준 조회로 덮어쓴 상태)
     * @param gclMngNo 채번된 품목관리번호
     * @param gclSno 품목일련번호
     * @param project 소속 사업 (abusMngNo·sno 스냅샷용)
     * @param reconciled {@link BudgetAmountCalculator#reconcileAmount}의 결과 [금액, 외화금액]
     * @return 조립된 품목 엔티티 (아직 저장하지 않음)
     */
    private static Bitemm buildBitemm(
            ProjectDto.BitemmDto itemDto,
            String gclMngNo,
            int gclSno,
            Bprojm project,
            BigDecimal[] reconciled) {
        return Bitemm.builder()
                .gclMngNo(gclMngNo) // 품목관리번호
                .sno(gclSno) // 품목일련번호
                .abusMngNo(project.getAbusMngNo()) // 프로젝트관리번호
                .fntTbCrySno(project.getSno()) // 프로젝트순번
                .ioeC(itemDto.getIoeC()) // 품목구분
                .gclNm(itemDto.getGclNm()) // 품목명
                .qty(itemDto.getQty()) // 품목수량
                .curC(itemDto.getCurC()) // 통화
                .xcr(itemDto.getXcr()) // 환율
                .xcrBseDt(DateFormatUtil.toYmd8(itemDto.getXcrBseDt())) // 환율기준일자(yyyyMMdd 정규화)
                .cncdFdtnCone(itemDto.getCncdFdtnCone()) // 예산근거
                .bseYm(toItdYm(itemDto.getBseYm())) // 도입시기
                .dfrCleC(CodeDefaults.orNotApplicable(itemDto.getDfrCleC())) // 지급주기
                .sectSysUtzYn(itemDto.getSectSysUtzYn()) // 정보보호여부(미기재는 null 유지)
                .itrInfrYn(itemDto.getItrInfrYn()) // 통합인프라여부(미기재는 null 유지)
                .lstYn("Y") // 최종여부
                .amt(reconciled[0]) // 당해 요청금액(원화, 서버 재계산)
                .fcAmt(reconciled[1]) // 당해 외화 원금(외화 행에서만 유효)
                .mplAmt(normalizePlannedAmount(itemDto.getMplAmt())) // 내년 이후 요청금액(당해 금액과 독립)
                .build();
    }

    /**
     * 도입시기를 DB 컬럼 형식(YYYYMM, 6자)으로 변환. 프론트에서 "YYYY-MM-DD" 또는 "YYYY-MM" 형식이 올 수 있으므로 하이픈을 제거한 뒤 앞
     * 6자만 사용한다. 빈값/null은 그대로 반환.
     */
    private static String toItdYm(String itdYm) {
        if (itdYm == null || itdYm.isBlank()) {
            return itdYm;
        }
        String normalized = itdYm.replace("-", "");
        return normalized.length() > 6 ? normalized.substring(0, 6) : normalized;
    }

    /**
     * 내년 이후 요청금액을 저장 가능한 값으로 정규화한다.
     *
     * @param mplAmt 입력 예정금액(null이면 0)
     * @return 0 이상의 예정금액
     * @throws IllegalArgumentException 예정금액이 음수일 때
     */
    private static BigDecimal normalizePlannedAmount(BigDecimal mplAmt) {
        if (mplAmt == null) {
            return BigDecimal.ZERO;
        }
        if (mplAmt.signum() < 0) {
            throw new IllegalArgumentException("예정금액은 0 이상이어야 합니다.");
        }
        return mplAmt;
    }

    /** 공용 금액 폴백 전에 사업 품목의 통화별 입력 불변식을 검증합니다. */
    private static void validateItemAmounts(ProjectDto.BitemmDto item) {
        if (item.getAmt() != null && item.getAmt().signum() < 0) {
            throw new IllegalArgumentException("당해 요청금액은 0 이상이어야 합니다.");
        }
        if (item.getFcAmt() != null && item.getFcAmt().signum() < 0) {
            throw new IllegalArgumentException("외화금액은 0 이상이어야 합니다.");
        }

        String currency = item.getCurC();
        boolean foreign =
                currency != null && !currency.isBlank() && !"KRW".equalsIgnoreCase(currency);
        if (foreign && item.getFcAmt() == null) {
            throw new IllegalArgumentException("외화 품목은 외화금액이 필요합니다.");
        }
        if (!foreign && item.getFcAmt() != null) {
            throw new IllegalArgumentException("원화 품목에는 외화금액을 입력할 수 없습니다.");
        }
    }
}
