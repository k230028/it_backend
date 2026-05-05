package com.kdb.it.domain.log.id;

import jakarta.persistence.Table;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 로그 테이블 PK({@code LOG_SNO}) 생성기.
 *
 * <p>로그 엔티티의 {@code @Table(name)} 값에서 Postfix를 추출하고,
 * Oracle 시퀀스 {@code S_{Postfix}.NEXTVAL}을 조회하여
 * {@code "{Postfix}_{22자리_0패딩}"} 형식의 VARCHAR2 값을 반환한다.</p>
 *
 * <p>예: {@code TAAABB_BPROJL} → {@code BPROJL_0000000000000000000001}</p>
 *
 * <p>시퀀스는 CYCLE 설정 (최대 22자리, 순환 후 1부터 재시작).</p>
 */
public class AuditLogIdGenerator implements IdentifierGenerator {

    private static final int SEQ_PAD_LENGTH = 22;

    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        String postfix = resolvePostfix(object);
        long nextVal = fetchNextVal(session, "S_" + postfix);
        return postfix + "_" + String.format("%0" + SEQ_PAD_LENGTH + "d", nextVal);
    }

    private String resolvePostfix(Object object) {
        Table ann = object.getClass().getAnnotation(Table.class);
        if (ann == null) {
            throw new IllegalStateException("@Table 누락: " + object.getClass().getName());
        }
        // "TAAABB_BPROJL" → "BPROJL"
        String tbl = ann.name().toUpperCase();
        int idx = tbl.indexOf('_');
        return idx >= 0 ? tbl.substring(idx + 1) : tbl;
    }

    private long fetchNextVal(SharedSessionContractImplementor session, String seqName) {
        // seqName은 @Table 어노테이션에서 파생된 값으로 사용자 입력이 아님
        try {
            Connection conn = session.getJdbcConnectionAccess().obtainConnection();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT " + seqName + ".NEXTVAL FROM DUAL")) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                throw new IllegalStateException("NEXTVAL 조회 결과 없음: " + seqName);
            } finally {
                session.getJdbcConnectionAccess().releaseConnection(conn);
            }
        } catch (Exception e) {
            throw new RuntimeException("시퀀스 조회 오류: " + seqName, e);
        }
    }
}
