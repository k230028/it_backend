package com.kdb.it.common.iam.service;

import com.kdb.it.common.iam.dto.OrganizationDto;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조직(부점) 관리 서비스
 *
 * <p>조직 정보(TPRMPP_CORGNI) 조회 비즈니스 로직을 처리합니다.
 *
 * <p>조직 데이터는 외부 시스템(HR 시스템 등)에서 동기화되는 마스터 데이터이므로 현재는 조회 기능만 제공합니다.
 *
 * <p>{@code @Transactional(readOnly = true)}: 읽기 전용 트랜잭션으로 실행합니다.
 */
@Service // Spring 서비스 빈으로 등록
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
@Transactional(readOnly = true) // 읽기 전용 트랜잭션
public class OrganizationService {

    /**
     * 조직 목록 표시 순서: 항목순서일련번호(ITM_SQN_SNO) 오름차순, 같은 값이면 조직코드 오름차순.
     *
     * <p>프론트 조직 트리는 이 응답 순서를 그대로 유지한 채 상위조직코드로 형제 노드를 묶으므로, 이 정렬이 화면의 형제 노드 표시 순서를 결정합니다. 항목순서일련번호가
     * 없는 조직은 뒤로 보내고, 동순위는 조직코드로 고정해 조회마다 순서가 흔들리지 않게 합니다.
     */
    private static final Sort DISPLAY_ORDER =
            Sort.by(
                    Sort.Order.asc("itmSqnSno").nullsLast(), // 항목순서일련번호 오름차순 (미지정 조직은 뒤로)
                    Sort.Order.asc("prlmOgzCCone")); // 동순위 결정 기준 (조직코드)

    /** 조직 정보 데이터 접근 리포지토리 */
    private final OrganizationRepository organizationRepository;

    /**
     * 전체 조직 목록 조회
     *
     * <p>DB의 모든 조직(부점) 정보를 조회하여 DTO 목록으로 반환합니다.
     *
     * <p>반환 데이터:
     *
     * <ul>
     *   <li>조직코드 ({@code prlmOgzCCone})
     *   <li>상위조직코드 ({@code prlmHrkOgzCCone})
     *   <li>부점명 ({@code bbrNm})
     * </ul>
     *
     * <p>정렬은 {@link #DISPLAY_ORDER}(항목순서일련번호 오름차순)를 따릅니다.
     *
     * @return 전체 조직 목록 DTO ({@link OrganizationDto.Response} 리스트)
     */
    public List<OrganizationDto.Response> getOrganizations() {
        return organizationRepository.findListViewsBy(DISPLAY_ORDER).stream() // 표시 순서로 정렬된 프로젝션 조회
                .map(OrganizationDto.Response::fromView) // 각 프로젝션 행을 DTO로 변환
                .toList(); // 리스트로 수집(불변)
    }
}
