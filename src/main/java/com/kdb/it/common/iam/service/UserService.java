package com.kdb.it.common.iam.service;

import com.kdb.it.common.iam.dto.UserDto;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 사용자(직원) 조회 서비스
 *
 * <p>사용자 정보(TPRMPP_CUSERI) 조회 비즈니스 로직을 처리합니다.</p>
 *
 * <p>부점코드({@code BBR_C})별 사용자 목록과 사번({@code ENO})별 사용자 상세 정보를
 * 제공합니다.</p>
 *
 * <p>목록·상세 응답은 필요한 사용자·조직 컬럼만 읽기 전용 프로젝션으로 조회합니다.</p>
 *
 * <p>현재는 조회 기능만 제공합니다. 사용자 생성/수정은 {@link com.kdb.it.common.system.service.AuthService#signup}에서 처리합니다.</p>
 *
 * <p>{@code @Transactional(readOnly = true)}: 읽기 전용 트랜잭션으로 성능을 최적화합니다.</p>
 */
@Service                              // Spring 서비스 빈으로 등록
@RequiredArgsConstructor              // final 필드 생성자 자동 주입 (Lombok)
@Transactional(readOnly = true)       // 읽기 전용 트랜잭션
public class UserService {

    /** 사용자 정보 데이터 접근 리포지토리 (TPRMPP_CUSERI) */
    private final UserRepository userRepository;

    /**
     * 부점코드별 사용자 목록 조회
     *
     * <p>특정 부점({@code orgCode})에 소속된 모든 사용자를 조직명과 함께 프로젝션으로 조회합니다.</p>
     *
     * <p>응답에는 사번, 부점명, 팀명, 사용자명, 직위명이 포함됩니다.</p>
     *
     * @param orgCode 조회할 부점코드 ({@code BBR_C})
     * @return 해당 부점의 사용자 목록 DTO ({@link UserDto.ListResponse} 리스트)
     */
    public List<UserDto.ListResponse> getUsersByOrganization(String orgCode) {
        // 부점코드로 사용자 목록 조회 (CorgnI JOIN FETCH로 N+1 방지)
        List<UserDto.ListRow> users = userRepository.findListRowsByBbrC(orgCode);

        // 각 사용자 엔티티를 DTO로 변환 (부점명은 연관관계에서 조회)
        return users.stream()
                .map(UserDto.ListResponse::fromRow)
                .toList();
    }

    /**
     * 사번별 사용자 상세 조회
     *
     * <p>특정 사번({@code eno})의 사용자 상세 정보를 조회합니다.
     * 목록 조회보다 더 많은 정보(내선번호, 휴대폰번호, 상세직무)를 포함합니다.</p>
     *
     * <p>PII(휴대폰번호·내선번호·이메일) 보호를 위해 본인 또는 관리자만 조회할 수 있습니다.</p>
     *
     * @param eno         조회할 사번
     * @param currentUser 현재 인증 사용자
     * @return 사용자 상세 응답 DTO ({@link UserDto.DetailResponse})
     * @throws org.springframework.security.access.AccessDeniedException 본인도 관리자도 아닌 경우
     * @throws IllegalArgumentException 해당 사번의 사용자가 없는 경우
     */
    public UserDto.DetailResponse getUser(String eno, CustomUserDetails currentUser) {
        // 권한 검증을 조회보다 먼저 수행 — 타인 사번 존재 여부 누설 방지
        OwnershipVerifier.verifyOwnerOrAdmin(eno, currentUser);

        // 사번으로 사용자 조회 (없으면 예외)
        UserDto.DetailRow user = userRepository.findDetailRowByEno(eno)
                .orElseThrow(() -> new IllegalArgumentException("User not found with eno: " + eno));

        // 엔티티를 DTO로 변환 (부점명은 연관관계에서 조회)
        return UserDto.DetailResponse.fromRow(user);
    }

    /**
     * 이름으로 사용자 검색 (자동완성용)
     *
     * <p>분기:</p>
     * <ul>
     *   <li>keyword 비어있고 orgCode 지정 → 해당 부서 사용자 전체 (멘션 default 목록용)</li>
     *   <li>keyword 비어있고 orgCode도 비어있음 → 빈 리스트 (전체 사용자 dump 방지)</li>
     *   <li>keyword 있음 → 사용자명 LIKE 검색 + orgCode 있으면 부서 필터링</li>
     * </ul>
     *
     * @param keyword 검색할 사용자명 (부분 일치, null/blank 허용)
     * @param orgCode 부서코드 (null이면 전체 부서 대상)
     * @return 검색 결과 사용자 목록 DTO
     */
    public List<UserDto.ListResponse> searchUsersByName(String keyword, String orgCode) {
        boolean keywordBlank = keyword == null || keyword.isBlank();
        boolean orgBlank     = orgCode == null || orgCode.isBlank();

        if (keywordBlank) {
            if (orgBlank) {
                return List.of();
            }
            return getUsersByOrganization(orgCode);
        }

        List<UserDto.ListRow> users = userRepository.searchListRowsByName(keyword);
        if (!orgBlank) {
            // orgBlank 검증 뒤 null 불가 값을 명시해 정적 분석 경고를 제거한다.
            final String orgFilter = Objects.requireNonNull(orgCode);
            users = users.stream()
                    .filter(u -> orgFilter.equals(u.bbrC()))
                    .toList();
        }
        return users.stream()
                .map(UserDto.ListResponse::fromRow)
                .toList();
    }
}
