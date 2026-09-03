package com.kdb.it.common.iam.service;

import com.kdb.it.common.iam.dto.UserDto;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.exception.CustomGeneralException;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자(직원) 조회 서비스
 *
 * <p>사용자 정보(TPRMPP_CUSERI) 조회 비즈니스 로직을 처리합니다.
 *
 * <p>부점코드({@code BBR_C})별 사용자 목록과 사번({@code ENO})별 사용자 상세 정보를 제공합니다.
 *
 * <p>목록·상세 응답은 필요한 사용자·조직 컬럼만 읽기 전용 프로젝션으로 조회합니다.
 *
 * <p>현재는 조회 기능만 제공합니다. 사용자 생성/수정은 {@link com.kdb.it.common.system.service.AuthService#signup}에서
 * 처리합니다.
 *
 * <p>{@code @Transactional(readOnly = true)}: 읽기 전용 트랜잭션으로 성능을 최적화합니다.
 */
@Service // Spring 서비스 빈으로 등록
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
@Transactional(readOnly = true) // 읽기 전용 트랜잭션
public class UserService {

    /** 전체 조직 대상 검색에 필요한 최소 검색어 길이 */
    private static final int MIN_KEYWORD_LENGTH = 2;

    /** 전체 조직 대상 검색의 최대 반환 건수 */
    private static final int SEARCH_RESULT_LIMIT = 200;

    /** 사용자 정보 데이터 접근 리포지토리 (TPRMPP_CUSERI) */
    private final UserRepository userRepository;

    /**
     * 부점코드별 사용자 목록 조회
     *
     * <p>특정 부점({@code orgCode})에 소속된 모든 사용자를 조직명과 함께 프로젝션으로 조회합니다.
     *
     * <p>응답에는 사번, 부점명, 팀명, 사용자명, 직위명이 포함되며 K 행번 우선, 직위코드 오름차순으로 정렬됩니다.
     *
     * <p>{@code enoPrefix}를 주면 해당 접두사로 시작하는 행번만 반환합니다. 담당자·결재자 지정처럼 특정 행번 체계만 선택해야 하는 화면이 사용합니다.
     *
     * @param orgCode 조회할 부점코드 ({@code BBR_C})
     * @param enoPrefix 행번({@code ENO}) 접두사 필터 (null·공백이면 전체)
     * @return 해당 부점의 사용자 목록 DTO ({@link UserDto.ListResponse} 리스트)
     */
    public List<UserDto.ListResponse> getUsersByOrganization(String orgCode, String enoPrefix) {
        // 사용자와 조직에서 목록 응답에 필요한 컬럼만 ListRow로 조회한다.
        List<UserDto.ListRow> users = userRepository.findListRowsByBbrC(orgCode, enoPrefix);

        // 조회된 ListRow를 엔티티 접근 없이 목록 응답 DTO로 변환한다.
        return users.stream().map(UserDto.ListResponse::fromRow).toList();
    }

    /**
     * 사번별 사용자 상세 조회
     *
     * <p>특정 사번({@code eno})의 사용자 상세 정보를 조회합니다. 목록 조회보다 더 많은 정보(내선번호, 휴대폰번호, 상세직무)를 포함합니다.
     *
     * <p>직원 정보 다이얼로그에서 사용할 수 있도록 인증된 사용자는 다른 직원의 상세 정보도 조회할 수 있습니다.
     *
     * @param eno 조회할 사번
     * @param currentUser 현재 인증 사용자
     * @return 사용자 상세 응답 DTO ({@link UserDto.DetailResponse})
     * @throws IllegalArgumentException 해당 사번의 사용자가 없는 경우
     */
    public UserDto.DetailResponse getUser(String eno, CustomUserDetails currentUser) {
        // 컨트롤러 보안 규칙과 함께 인증 주체가 없는 직접 호출도 허용하지 않는다.
        Objects.requireNonNull(currentUser, "currentUser must not be null");

        // 사번으로 사용자 조회 (없으면 예외)
        UserDto.DetailRow user =
                userRepository
                        .findDetailRowByEno(eno)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "User not found with eno: " + eno));

        List<String> qlfGrNms = userRepository.findActiveQualificationGradeNamesByEno(eno);

        return UserDto.DetailResponse.fromRow(user, qlfGrNms);
    }

    /**
     * 사용자 검색 (이름·팀명·사번 부분 일치)
     *
     * <p>분기:
     *
     * <ul>
     *   <li>keyword 비어있고 orgCode 지정 → 해당 부서 사용자 전체 (멘션 default 목록용)
     *   <li>keyword 비어있고 orgCode도 비어있음 → 빈 리스트 (전체 사용자 dump 방지)
     *   <li>keyword 있음 → 전체 조직 대상 이름·팀명·사번 LIKE 검색 + orgCode 있으면 부서 필터링
     * </ul>
     *
     * <p>전체 조직이 대상이므로 검색어는 {@link #MIN_KEYWORD_LENGTH}자 이상이어야 하며 결과는 {@link
     * #SEARCH_RESULT_LIMIT}건까지만 반환합니다.
     *
     * <p>표시 순서는 K 행번 우선, 직위코드 오름차순이며 상한 절단보다 먼저 적용됩니다.
     *
     * <p>{@code enoPrefix}를 주면 해당 접두사로 시작하는 행번만 반환하며, 필터는 결과 상한 절단보다 먼저 DB에서 적용됩니다.
     *
     * @param keyword 검색어 (이름·팀명·사번 부분 일치, null/blank 허용)
     * @param orgCode 부서코드 (null이면 전체 부서 대상)
     * @param enoPrefix 행번({@code ENO}) 접두사 필터 (null·공백이면 전체)
     * @return 검색 결과 사용자 목록 DTO (최대 {@link #SEARCH_RESULT_LIMIT}건)
     * @throws CustomGeneralException 검색어가 있으나 {@link #MIN_KEYWORD_LENGTH}자 미만인 경우
     */
    public List<UserDto.ListResponse> searchUsers(
            String keyword, String orgCode, String enoPrefix) {
        boolean keywordBlank = keyword == null || keyword.isBlank();
        boolean orgBlank = orgCode == null || orgCode.isBlank();

        if (keywordBlank) {
            if (orgBlank) {
                return List.of();
            }
            return getUsersByOrganization(orgCode, enoPrefix);
        }

        // 전체 조직 대상 LIKE 검색이므로 너무 짧은 검색어는 서버에서 차단한다.
        String trimmedKeyword = Objects.requireNonNull(keyword).trim();
        if (trimmedKeyword.length() < MIN_KEYWORD_LENGTH) {
            throw new CustomGeneralException("검색어는 " + MIN_KEYWORD_LENGTH + "자 이상 입력하세요.");
        }

        List<UserDto.ListRow> users =
                userRepository.searchListRowsByKeyword(
                        trimmedKeyword, enoPrefix, SEARCH_RESULT_LIMIT);
        if (!orgBlank) {
            // orgBlank 검증 뒤 null 불가 값을 명시해 정적 분석 경고를 제거한다.
            final String orgFilter = Objects.requireNonNull(orgCode);
            users = users.stream().filter(u -> orgFilter.equals(u.bbrC())).toList();
        }
        return users.stream().map(UserDto.ListResponse::fromRow).toList();
    }
}
