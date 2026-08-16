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
        List<MigrationDto.Candidate> out = new ArrayList<>();
        for (Ccodem code : codeRepository.findByCIdAndDelYn(cId, "N")) {
            if (code.getCdvaNm() == null) continue;
            String name = code.getCdvaNm().trim();
            out.add(new MigrationDto.Candidate(storeName ? name : code.getCdva().trim(), name));
        }
        return List.copyOf(out);
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
