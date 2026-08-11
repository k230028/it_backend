package com.kdb.it.domain.migration.service;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이관 검증·변환에 필요한 공통코드를 한 번에 읽습니다.
 *
 * <p>비목은 코드값명 → 코드값 역방향 맵, 환율은 통화 → 예산환율 맵으로 만듭니다.
 *
 * <p>환율은 {@code XcrLookupService.resolveXcr}와 **같은 판정 기준**으로 읽어야 합니다. 그 경로가 호출하는 {@code
 * CodeRepositoryImpl.findByCIdAndCdvaWithValidDate}는 {@code C_ID}·{@code CDVA}·{@code DEL_YN}·유효일자만
 * 보고 {@code C_TP}를 필터하지 않습니다. 따라서 이 리더도 {@code C_TP}로 걸러내지 않습니다 — 걸러내면 dry-run은 "환율 없음"으로 판정하는데
 * commit은 같은 행을 찾아 저장에 성공하는 어긋남이 생깁니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MigrationIoeCatalogReader {

    private final CodeRepository codeRepository;

    /**
     * 비목 코드값명 → 코드값 맵을 만듭니다.
     *
     * @return 예: `{"국내전산임차료" → "001", "유지보수료" → "011"}`. 코드값명이 중복되면 먼저 나온 것을 씁니다
     */
    public Map<String, String> ioeCodeByName() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Ccodem code : codeRepository.findByCIdAndDelYn(CommonCodeGroups.IOE, "N")) {
            if (code.getCdvaNm() != null) {
                out.putIfAbsent(code.getCdvaNm().trim(), code.getCdva());
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
        for (Ccodem code : codeRepository.findByCIdAndDelYn(CommonCodeGroups.CURRENCY, "N")) {
            if (code.getCdvaDtlC() == null) {
                continue;
            }
            try {
                out.putIfAbsent(code.getCdva(), new BigDecimal(code.getCdvaDtlC().trim()));
            } catch (NumberFormatException e) {
                log.warn("예산환율 공통코드 값을 숫자로 읽지 못했습니다: 통화={}", code.getCdva());
            }
        }
        return out;
    }
}
