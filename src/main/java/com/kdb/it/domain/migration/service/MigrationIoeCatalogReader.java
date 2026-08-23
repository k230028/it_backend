package com.kdb.it.domain.migration.service;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.domain.migration.dto.MigrationDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이관 검증·변환에 필요한 공통코드를 한 번에 읽습니다.
 *
 * <p>비목·추진가능성·전결권은 코드값명 → 코드값 역방향 맵, 사업코드는 코드값 → 코드값명 맵, 환율은 통화 → 예산환율 맵으로 만듭니다.
 *
 * <p>환율은 {@code XcrLookupService.resolveXcr}와 **같은 판정 기준**으로 읽어야 합니다. 그 경로가 호출하는 {@code
 * CodeRepositoryImpl.findByCIdAndCdvaWithValidDate}는 {@code C_ID}·{@code CDVA}·{@code DEL_YN}·유효일자를
 * 보고 {@code C_TP}는 필터하지 않습니다. 따라서 이 리더도 {@code C_TP}로 걸러내지 않고, 유효일자는 **같은 조건으로 필터합니다** — 유효일자를 빼면
 * 이미 만료된 환율 행까지 후보에 들어와 dry-run은 그 값을 쓰는데 commit은 유효한 다른 행을 쓰는 어긋남이 생깁니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MigrationIoeCatalogReader {

    /** 전결권 코드타입 중 자본예산 계열. 같은 코드값명(부문장 등)이 경상용과 자본용에 각각 있어 코드타입으로 갈라야 유일해집니다. */
    private static final String EDRT_CAPITAL_CTP = "EDRT_CPIT";

    private final CodeRepository codeRepository;

    /**
     * 비목 코드값명 → 코드값 맵을 만듭니다.
     *
     * @return 예: `{"국내전산임차료" → "001", "유지보수료" → "011"}`. 코드값명이 중복되면 먼저 나온 것을 씁니다
     */
    public Map<String, String> ioeCodeByName() {
        return codeByName(CommonCodeGroups.IOE, null);
    }

    /**
     * 추진가능성(`EXE_PTT_YN`) 코드값명 → 코드값 맵을 만듭니다.
     *
     * @return 예: `{"확정" → "1", "미정(검토중)" → "2"}`
     */
    public Map<String, String> exePttCodeByName() {
        return codeByName(CommonCodeGroups.EXE_POSSIBLE, null);
    }

    /**
     * 보고상태(`IT_PTL_RPR_STS_TC`) 코드값명 → 코드값 맵을 만듭니다.
     *
     * <p>1-1 시트의 `최종보고` 체크박스 문구를 코드로 되돌리는 데 씁니다. 물리 컬럼이 2자리 코드라 코드값명을 그대로 저장할 수 없습니다.
     *
     * @return 예: `{"부문(본부)장" → "04", "부서장" → "05"}`
     */
    public Map<String, String> reportStatusCodeByName() {
        return codeByName(CommonCodeGroups.REPORT_STS, null);
    }

    /**
     * 전결권(`IT_PTL_EDRT_TC`) 자본예산 계열의 코드값명 → 코드값 맵을 만듭니다.
     *
     * <p>이관 대상 시트는 자본예산 편성 요구서이므로 자본 계열({@code C_TP='EDRT_CPIT'})만 씁니다. 경상 계열({@code EDRT_MNGC})까지
     * 넣으면 `부문장`처럼 두 계열에 같은 이름이 있는 값이 어느 쪽으로 해석될지 알 수 없어집니다.
     *
     * @return 예: `{"부문장" → "22", "이사회" → "25"}`
     */
    public Map<String, String> edrtCapitalCodeByName() {
        return codeByName(CommonCodeGroups.EDRT, EDRT_CAPITAL_CTP);
    }

    /**
     * 사업코드(`BG_UNT_ABUS_C`) 코드값 → 코드값명 맵을 만듭니다.
     *
     * <p>엑셀에 이미 코드값(`571`)이 적혀 있어 이름 해석이 필요 없고, 실재 여부 확인과 미매칭 시 후보 제시에만 씁니다.
     *
     * @return 예: `{"571" → "운영시스템 유지보수"}`
     */
    public Map<String, String> abusUnitNameByCode() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Ccodem code : codeRepository.findByCIdAndDelYn(CommonCodeGroups.ABUS_UNIT, "N")) {
            if (code.getCdva() != null) {
                out.putIfAbsent(
                        code.getCdva().trim(),
                        code.getCdvaNm() == null ? code.getCdva().trim() : code.getCdvaNm().trim());
            }
        }
        return out;
    }

    /**
     * 통화 → 예산환율 맵을 만듭니다.
     *
     * @return 예: `{"GBP" → 1924, "USD" → 1432}`. 숫자로 파싱되지 않는 행은 건너뛰고 경고를 남깁니다
     */
    public Map<String, BigDecimal> xcrByCurrency() {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (Ccodem code : codeRepository.findByCIdWithValidDate(CommonCodeGroups.CURRENCY, null)) {
            if (code.getCdvaDtlC() == null) {
                continue;
            }
            try {
                BigDecimal xcr = new BigDecimal(code.getCdvaDtlC().trim());
                if (out.putIfAbsent(code.getCdva(), xcr) != null) {
                    // 같은 통화에 동시에 유효한 행이 둘 이상이면 resolveXcr(fetchOne)이
                    // NonUniqueResultException으로 실패한다. 여기서 조용히 첫 행을 쓰면 dry-run만 통과하므로 남긴다.
                    log.warn(
                            "같은 통화에 동시에 유효한 예산환율 행이 둘 이상입니다: 통화={}. 저장 경로가 실패할 수 있습니다.",
                            code.getCdva());
                }
            } catch (NumberFormatException e) {
                log.warn("예산환율 공통코드 값을 숫자로 읽지 못했습니다: 통화={}", code.getCdva());
            }
        }
        return out;
    }

    /**
     * 공통코드 그룹의 선택 후보를 만듭니다.
     *
     * <p>편성요청서의 선택 항목은 저장 형태가 둘로 갈립니다 — 업무구분·사업유형처럼 <b>코드값명</b>을 그대로 담는 컬럼과, 보고상태·추진가능성처럼
     * <b>코드</b>를 담는 컬럼입니다. 미리보기에서 고른 값이 그대로 저장값이 되어야 하므로 후보의 `code`를 저장 형태에 맞춰 만듭니다.
     *
     * @param cId 공통코드 그룹 id
     * @param storeName true면 후보값으로 코드값명을, false면 코드값을 씁니다
     * @return 후보 목록. 코드값명이 없는 행은 건너뜁니다
     */
    public List<MigrationDto.Candidate> candidates(String cId, boolean storeName) {
        return candidates(cId, null, storeName);
    }

    /**
     * 통화(`CUR_C`) 선택 후보를 <b>유효일자 필터로</b> 만듭니다.
     *
     * <p>물리 컬럼이 통화코드이므로 후보값은 <b>코드값</b>입니다({@code storeName=false}).
     *
     * <p>유효일자를 보지 않는 {@link #candidates(String, boolean)}를 쓰면 안 됩니다. 저장 경로({@code
     * CostService.createCost} → {@code XcrLookupService.resolveXcr})가 {@code
     * findByCIdAndCdvaWithValidDate}로 <b>유효일자 재조회</b>를 하고 없으면 {@code IllegalStateException}으로 롤백하기
     * 때문입니다. 조회 기준이 어긋나면 유효기간이 닫힌 통화가 선택지에 떠서 사전검증은 통과하고 반영에서 그 파일만 실패합니다(MIG-28).
     *
     * <p>{@code KRW}는 {@code resolveXcr}가 조회 없이 통과시키므로 환율값이 비어 있어도 후보에서 빼지 않습니다 — 여기서 환율 파싱 가능 여부까지
     * 거르면 가장 흔한 통화가 선택지에서 사라집니다.
     *
     * @return 예: `[{code:"KRW", label:"원화"}, {code:"USD", label:"미국 달러"}]`
     */
    public List<MigrationDto.Candidate> currencyCandidates() {
        return toCandidates(
                codeRepository.findByCIdWithValidDate(CommonCodeGroups.CURRENCY, null),
                null,
                false);
    }

    /**
     * 전결권(`IT_PTL_EDRT_TC`) 자본예산 계열의 선택 후보를 만듭니다.
     *
     * <p>물리 컬럼 `IT_PTL_EDRT_TC`가 2자리 코드이므로 후보값은 <b>코드값</b>입니다({@code storeName=false}). 계열을 자본으로
     * 좁히는 이유는 {@link #edrtCapitalCodeByName()}과 같습니다 — `부문장`처럼 경상 계열에도 있는 이름이 섞이면 화면에서 고른 값이 어느 계열의
     * 코드인지 알 수 없어집니다.
     *
     * @return 예: `[{code:"22", label:"부문장"}, {code:"25", label:"이사회"}]`
     */
    public List<MigrationDto.Candidate> edrtCapitalCandidates() {
        return candidates(CommonCodeGroups.EDRT, EDRT_CAPITAL_CTP, false);
    }

    /**
     * 코드타입으로 좁힌 선택 후보를 만듭니다.
     *
     * @param cId 공통코드 그룹 id
     * @param cTp 코드타입. null이면 좁히지 않습니다
     * @param storeName true면 후보값으로 코드값명을, false면 코드값을 씁니다
     * @return 후보 목록. 코드값명이 없는 행은 건너뜁니다
     */
    private List<MigrationDto.Candidate> candidates(String cId, String cTp, boolean storeName) {
        return toCandidates(codeRepository.findByCIdAndDelYn(cId, "N"), cTp, storeName);
    }

    /**
     * 조회된 공통코드 행을 선택 후보로 바꿉니다.
     *
     * @param codes 이미 조회된 공통코드 행. 어떤 조회 기준을 썼는지는 호출자가 정합니다
     * @param cTp 코드타입. null이면 좁히지 않습니다
     * @param storeName true면 후보값으로 코드값명을, false면 코드값을 씁니다
     * @return 후보 목록. 코드값명이 없는 행은 건너뜁니다
     */
    private List<MigrationDto.Candidate> toCandidates(
            List<Ccodem> codes, String cTp, boolean storeName) {
        List<MigrationDto.Candidate> out = new ArrayList<>();
        for (Ccodem code : codes) {
            if (code.getCdvaNm() == null || (cTp != null && !cTp.equals(code.getCTp()))) continue;
            String name = code.getCdvaNm().trim();
            out.add(new MigrationDto.Candidate(storeName ? name : code.getCdva().trim(), name));
        }
        return List.copyOf(out);
    }

    /**
     * 일반관리비 기본 편성률을 읽습니다.
     *
     * <p>{@code /budget/work} 화면이 쓰는 값과 같은 출처입니다(비목편성률 그룹 {@code DUP_IOE}의 코드인스턴스명({@code
     * CO_C_INTN_NM})이 {@code DUP_IOE_MNGC}인 행, 값은 공통코드값명({@code CO_CDVA_NM})). 종합본 `전체취합(국내외)` 시트에는
     * 조정률 열이 없으므로 이 값이 전산업무비 편성률이 됩니다. 리터럴 100을 박지 않는 이유는 이 기준이 해마다 바뀔 수 있기 때문입니다.
     *
     * @return 편성률(0~100). 코드가 없거나 숫자가 아니면 100
     */
    public BigDecimal generalExpenseRate() {
        for (Ccodem code : codeRepository.findByCIdWithValidDate("DUP_IOE", null)) {
            if (!"DUP_IOE_MNGC".equals(code.getCTp()) || code.getCdvaDtlC() == null) {
                continue;
            }
            try {
                return new BigDecimal(code.getCdvaDtlC().trim());
            } catch (NumberFormatException ignored) {
                // TODO: 코드 부재(정상 기본값)와 달리 값 오염은 설정 실수이므로, 100 폴백 전에
                // xcrByCurrency()처럼 warn 로그를 남겨 운영자가 인지할 수 있게 한다.
                break;
            }
        }
        return BigDecimal.valueOf(100);
    }

    /**
     * 코드값명 → 코드값 역방향 맵을 만듭니다.
     *
     * <p>환율과 달리 유효일자를 필터하지 않습니다 — 이 코드들은 저장 경로가 유효일자로 재조회하지 않으므로 조회 기준을 맞출 상대가 없고, 여기서만 좁히면 원장에 이미
     * 쓰여 있는 값이 이관에서만 미해석으로 떨어집니다.
     *
     * @param cId 공통코드 그룹 id
     * @param cTp null이 아니면 이 코드타입만 채택합니다
     * @return 코드값명 → 코드값. 이름이 중복되면 먼저 나온 것을 씁니다
     */
    private Map<String, String> codeByName(String cId, String cTp) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Ccodem code : codeRepository.findByCIdAndDelYn(cId, "N")) {
            if (code.getCdvaNm() == null || (cTp != null && !cTp.equals(code.getCTp()))) {
                continue;
            }
            out.putIfAbsent(code.getCdvaNm().trim(), code.getCdva());
        }
        return out;
    }
}
