package com.kdb.it.common.iam.service;

import com.kdb.it.common.iam.dto.OrganizationDto;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
     * @return 전체 조직 목록 DTO ({@link OrganizationDto.Response} 리스트)
     */
    public List<OrganizationDto.Response> getOrganizations() {
        return organizationRepository.findAll().stream() // 전체 조직 엔티티 조회
                .map(OrganizationDto.Response::fromEntity) // 각 엔티티를 DTO로 변환
                .toList(); // 리스트로 수집(불변)
    }
}
