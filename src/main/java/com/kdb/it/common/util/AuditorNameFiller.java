package com.kdb.it.common.util;

import com.kdb.it.common.iam.repository.UserRepository;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/**
 * 감사 필드(최초 등록자·마지막 수정자) 사번을 사용자명으로 채우는 유틸.
 *
 * <p>두 사번을 {@link UserRepository#findNameViewsByEnoIn} 한 번으로 조회해 이름 setter에 넘깁니다. 담당자 컬럼과 달리 감사 컬럼은
 * 항상 사번만 담으므로 {@link UserNameResolver}의 "저장값이 이름" 판정은 적용하지 않습니다.
 *
 * <p>조회에 실패한 사번(퇴직·미등록·DB 기본값 {@code 00000000000000})은 이름을 채우지 않고 사번도 지우지 않습니다 — 화면이 사번을 그대로 보여 주어
 * 값이 있었다는 사실을 숨기지 않게 합니다.
 */
public final class AuditorNameFiller {

    private AuditorNameFiller() {}

    /**
     * 최초 등록자·마지막 수정자 이름을 채웁니다.
     *
     * @param userRepository 사용자 조회 저장소
     * @param fstEnrUsid 최초 등록자 사번(null 허용)
     * @param lstChgUsid 마지막 수정자 사번(null 허용)
     * @param fstEnrNameSetter 최초 등록자명 setter — 조회 성공 시에만 호출
     * @param lstChgNameSetter 마지막 수정자명 setter — 조회 성공 시에만 호출
     */
    public static void fill(
            UserRepository userRepository,
            String fstEnrUsid,
            String lstChgUsid,
            Consumer<String> fstEnrNameSetter,
            Consumer<String> lstChgNameSetter) {
        Set<String> enos = new LinkedHashSet<>();
        if (StringUtils.hasText(fstEnrUsid)) {
            enos.add(fstEnrUsid);
        }
        if (StringUtils.hasText(lstChgUsid)) {
            enos.add(lstChgUsid);
        }
        if (enos.isEmpty()) {
            return;
        }
        Map<String, String> names =
                userRepository.findNameViewsByEnoIn(enos).stream()
                        .filter(view -> StringUtils.hasText(view.getUsrNm()))
                        .collect(
                                Collectors.toMap(
                                        UserRepository.UserNameView::getEno,
                                        UserRepository.UserNameView::getUsrNm,
                                        (first, second) -> first));
        if (names.containsKey(fstEnrUsid)) {
            fstEnrNameSetter.accept(names.get(fstEnrUsid));
        }
        if (names.containsKey(lstChgUsid)) {
            lstChgNameSetter.accept(names.get(lstChgUsid));
        }
    }
}
