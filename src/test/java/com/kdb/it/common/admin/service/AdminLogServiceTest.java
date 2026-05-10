package com.kdb.it.common.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.admin.dto.AdminLogDto;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.log.entity.BasctmL;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * AdminLogService 단위 테스트
 *
 * <p>로그 테이블 메타 정보, 목록 조회, 상세 조회의 공통 변환 흐름을 검증합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminLogServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminLogService adminLogService;

    @Test
    @DisplayName("getTables: 허용된 로그 테이블 메타 정보를 키 기준 정렬로 반환한다")
    void getTables_로그테이블목록_반환() {
        List<AdminLogDto.LogTableResponse> tables = adminLogService.getTables();

        assertThat(tables).isNotEmpty();
        assertThat(tables.get(0).key()).isEqualTo("basctm");
        assertThat(tables.get(0).tableName()).isEqualTo("TAAABB_BASCTL");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("getLogs: 로그 행과 사용자명 매핑을 함께 반환한다")
    void getLogs_로그목록_사용자명포함반환() {
        BasctmL log = BasctmL.builder()
                .logSno("BASCTM_0001")
                .chgTp("C")
                .chgDtm(LocalDateTime.of(2026, 5, 6, 9, 0))
                .chgUsid("10001")
                .asctId("ASCT-1")
                .prjMngNo("PRJ-2026-0001")
                .cnrcDt(LocalDate.of(2026, 5, 7))
                .build();
        TypedQuery<BasctmL> listQuery = mock(TypedQuery.class);
        TypedQuery<Long> countQuery = mock(TypedQuery.class);
        given(entityManager.createQuery("select e from BasctmL e order by e.logSno desc", BasctmL.class))
                .willReturn(listQuery);
        given(listQuery.setFirstResult(0)).willReturn(listQuery);
        given(listQuery.setMaxResults(500)).willReturn(listQuery);
        given(listQuery.getResultList()).willReturn(List.of(log));
        given(entityManager.createQuery("select count(e) from BasctmL e", Long.class)).willReturn(countQuery);
        given(countQuery.getSingleResult()).willReturn(1L);
        CuserI user = CuserI.builder().eno("10001").usrNm("홍길동").build();
        given(userRepository.findByEnoIn(any())).willReturn(List.of(user));

        AdminLogDto.LogPageResponse result = adminLogService.getLogs("basctm", PageRequest.of(0, 999));

        assertThat(result.table().key()).isEqualTo("basctm");
        assertThat(result.size()).isEqualTo(500);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).containsEntry("logSno", "BASCTM_0001");
        assertThat(result.content().get(0)).containsEntry("asctId", "ASCT-1");
        assertThat(result.userNames()).containsEntry("10001", "홍길동");
        verify(listQuery).setMaxResults(500);
    }

    @Test
    @DisplayName("getLogDetail: 로그 일련번호로 단건 상세 스냅샷을 반환한다")
    void getLogDetail_존재하는로그_상세반환() {
        BasctmL log = BasctmL.builder()
                .logSno("BASCTM_0001")
                .chgTp("U")
                .chgUsid("10001")
                .asctId("ASCT-1")
                .build();
        given(entityManager.find(eq(BasctmL.class), eq("BASCTM_0001"))).willReturn(log);

        AdminLogDto.LogDetailResponse result = adminLogService.getLogDetail("basctm", "BASCTM_0001");

        assertThat(result.row()).containsEntry("logSno", "BASCTM_0001");
        assertThat(result.row()).containsEntry("chgTp", "U");
        assertThat(result.columns()).anyMatch(AdminLogDto.LogColumnResponse::primary);
    }

    @Test
    @DisplayName("getLogDetail: 로그가 없으면 IllegalArgumentException을 던진다")
    void getLogDetail_로그없음_IllegalArgumentException발생() {
        given(entityManager.find(eq(BasctmL.class), eq("MISSING"))).willReturn(null);

        assertThatThrownBy(() -> adminLogService.getLogDetail("basctm", "MISSING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 로그");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("getLogs: 페이지 번호와 크기를 안전 범위로 보정하고 사용자 필드가 없으면 빈 이름 맵을 반환한다")
    void getLogs_페이지보정_사용자명없음() {
        TypedQuery<BasctmL> listQuery = mock(TypedQuery.class);
        TypedQuery<Long> countQuery = mock(TypedQuery.class);
        given(entityManager.createQuery("select e from BasctmL e order by e.logSno desc", BasctmL.class))
                .willReturn(listQuery);
        given(listQuery.setFirstResult(0)).willReturn(listQuery);
        given(listQuery.setMaxResults(1)).willReturn(listQuery);
        given(listQuery.getResultList()).willReturn(List.of());
        given(entityManager.createQuery("select count(e) from BasctmL e", Long.class)).willReturn(countQuery);
        given(countQuery.getSingleResult()).willReturn(0L);

        Pageable pageable = mock(Pageable.class);
        given(pageable.getPageNumber()).willReturn(-1);
        given(pageable.getPageSize()).willReturn(0);

        AdminLogDto.LogPageResponse result = adminLogService.getLogs("basctm", pageable);

        assertThat(result.number()).isZero();
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.totalPages()).isZero();
        assertThat(result.userNames()).isEmpty();
        verify(listQuery).setMaxResults(1);
    }

    @Test
    @DisplayName("getLogs: 허용되지 않은 로그 키이면 IllegalArgumentException을 던진다")
    void getLogs_허용되지않은키_IllegalArgumentException발생() {
        assertThatThrownBy(() -> adminLogService.getLogs("unknown", PageRequest.of(0, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("조회할 수 없는 로그 테이블");
    }
}
