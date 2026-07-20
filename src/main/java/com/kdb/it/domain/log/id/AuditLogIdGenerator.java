package com.kdb.it.domain.log.id;

import jakarta.persistence.Table;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 로그 테이블 PK({@code LOG_HIS_TGR_SNO}) 생성기.
 *
 * <p>로그 엔티티의 {@code @Table(name)} 값(테이블명)으로부터
 * Oracle 시퀀스 {@code SQ_{테이블명}_1.NEXTVAL}을 조회하여 {@code Long} 값을 반환한다.</p>
 *
 * <p>예: {@code TPRMPP_BPROJL} → {@code SQ_TPRMPP_BPROJL_1.NEXTVAL} (숫자)</p>
 */
public class AuditLogIdGenerator implements IdentifierGenerator {

    /**
     * 로그 엔티티의 PK({@code LOG_HIS_TGR_SNO})를 Oracle 시퀀스로 채번합니다.
     *
     * <p>채번 흐름: {@code @Table(name)} 추출 → {@code SQ_{테이블명}_1.NEXTVAL} 조회</p>
     * <p>예: {@code TPRMPP_BPROJL} → {@code SQ_TPRMPP_BPROJL_1.NEXTVAL} → Long 값 반환</p>
     *
     * <p>시퀀스 미존재(ORA-02289) 등 DB 오류 발생 시 {@link RuntimeException}으로 래핑하여 전파합니다.
     * 해당 예외는 {@link com.kdb.it.domain.log.listener.ChangeLogEntityListener}가 삼켜
     * 원본 트랜잭션 롤백을 방지합니다.</p>
     *
     * @param session 현재 Hibernate 세션 (JDBC 커넥션 획득용)
     * @param object  채번 대상 엔티티 인스턴스 ({@code @Table} 어노테이션 필수)
     * @return 채번된 시퀀스 값 ({@link Long})
     * @throws RuntimeException      시퀀스 조회 실패 시 (ORA-02289 등 포함)
     * @throws IllegalStateException {@code @Table} 어노테이션 누락 또는 NEXTVAL 결과 없음
     */
    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        String tableName = resolveTableName(object);
        return fetchNextVal(session, "SQ_" + tableName + "_1");
    }

    private String resolveTableName(Object object) {
        Table ann = object.getClass().getAnnotation(Table.class);
        if (ann == null) {
            throw new IllegalStateException("@Table 누락: " + object.getClass().getName());
        }
        return ann.name().toUpperCase();
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
