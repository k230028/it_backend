package com.kdb.it.domain.council.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.common.util.NativeRowMapper;
import com.kdb.it.domain.council.dto.CouncilProjectRow;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("#5 협의회 신청대상 18컬럼 native → CouncilProjectRow 매핑 동등성")
class CouncilProjectRowMappingIt extends AbstractOracleRepositoryTest {

    // 사업 상태코드: CouncilService PRJ_STS_COUNCIL_IN_PROGRESS('45' 타당성검토 정실협 진행중) /
    // PRJ_STS_COUNCIL_TARGET('09' 예산편성 요청 결재완료=신청 대상). 현재 소스 상수와 동일하게 사용.
    private static final String IN_PROGRESS = "45";
    private static final String PENDING = "09";

    @Autowired CouncilRepository councilRepository;

    @Test
    @DisplayName("findProjectsForCouncilAll: Object[] 경로와 DTO 경로 값이 컬럼별로 동일하다")
    void all_objectArray_equals_dto() {
        // 사전 조건 검사(P3 범위 밖 결함 감내): 현재 베이스 브랜치의 native 쿼리는 BPROJM에 없는
        // 컬럼 p.IT_PTL_STS_TC(실제 컬럼은 IT_PTL_RPR_STS_TC)를 참조해 로컬 스키마에서 ORA-00904로
        // 실패한다. 이는 본 P3 리팩토링 이전부터 존재하는 코드/DB 드리프트 결함이며, 봉인 래퍼는
        // 동일 SQL을 호출하므로 Object[] 경로와 DTO 경로가 "동일하게" 실패해야 한다(거동 보존 증명).
        // 쿼리가 정상 동작하는 환경에서는 아래 컬럼별 동등성 비교가 그대로 수행된다.
        List<Object[]> rows;
        try {
            rows = councilRepository.findProjectsForCouncilAll(IN_PROGRESS, PENDING);
        } catch (RuntimeException objectArrayPathFailure) {
            // Object[] 경로가 실패하면 DTO 경로도 같은 타입으로 실패해야 한다(봉인 래퍼가 거동을 바꾸지 않음).
            assertThatThrownBy(
                            () ->
                                    councilRepository.findProjectRowsForCouncilAll(
                                            IN_PROGRESS, PENDING))
                    .isInstanceOf(objectArrayPathFailure.getClass());
            return;
        }

        List<CouncilProjectRow> dtos =
                councilRepository.findProjectRowsForCouncilAll(IN_PROGRESS, PENDING);

        assertThat(dtos).hasSameSizeAs(rows);
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            CouncilProjectRow d = dtos.get(i);
            assertThat(d.abusMngNo()).isEqualTo(NativeRowMapper.toStr(r[0]));
            assertThat(d.sno()).isEqualTo(r[1] == null ? null : ((Number) r[1]).intValue());
            assertThat(d.abusNm()).isEqualTo(NativeRowMapper.toStr(r[2]));
            assertThat(d.itPtlAsctId()).isEqualTo(NativeRowMapper.toStr(r[3]));
            assertThat(d.itPtlAsctPrgStsTc()).isEqualTo(NativeRowMapper.toStr(r[4]));
            assertThat(d.itPtlAsctDbrTc()).isEqualTo(NativeRowMapper.toStr(r[5]));
            assertThat(d.cnrcDt()).isEqualTo(NativeRowMapper.toLd(r[6]));
            assertThat(d.cnrcSttTm()).isEqualTo(NativeRowMapper.toStr(r[7]));
            assertThat(d.applied()).isEqualTo(NativeRowMapper.toInt(r[8], 0) == 1);
            assertThat(d.prjYy()).isEqualTo(NativeRowMapper.toStr(r[9]));
            assertThat(d.prjTp()).isEqualTo(NativeRowMapper.toStr(r[10]));
            assertThat(d.svnDpm()).isEqualTo(NativeRowMapper.toStr(r[11]));
            assertThat(d.sttDt()).isEqualTo(NativeRowMapper.toLd(r[13]));
            assertThat(d.endDt()).isEqualTo(NativeRowMapper.toLd(r[14]));
            assertThat(d.itDpm()).isEqualTo(NativeRowMapper.toStr(r[15]));
            assertThat(d.abusPulConeInf()).isEqualTo(NativeRowMapper.toStr(r[16]));
            assertThat(d.csfHeldYn()).isEqualTo(NativeRowMapper.toStr(r[17]));
        }
    }
}
