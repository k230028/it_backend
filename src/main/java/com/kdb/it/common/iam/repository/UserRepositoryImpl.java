package com.kdb.it.common.iam.repository;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.entity.QCuserI;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 사용자(CuserI) 커스텀 리포지토리 구현 클래스
 *
 * <p>{@link UserRepositoryCustom} 인터페이스의 QueryDSL 구현체입니다.
 * QueryDSL의 타입 안전(Type-Safe) 쿼리 빌더를 사용하여 동적 쿼리를 작성합니다.</p>
 *
 * <p>클래스 명명 규칙: Spring Data JPA가 자동으로 감지하려면
 * 반드시 {@code [CustomInterface명]Impl} 형태여야 합니다. ({@code UserRepositoryImpl})</p>
 *
 * <p>의존성:</p>
 * <ul>
 *   <li>{@link JPAQueryFactory}: QueryDSL 쿼리 실행기 ({@link com.kdb.it.config.QuerydslConfig}에서 빈 등록)</li>
 *   <li>{@link QCuserI}: QueryDSL이 자동 생성한 Q 타입 클래스 (컴파일 시 생성)</li>
 * </ul>
 */
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
public class UserRepositoryImpl implements UserRepositoryCustom {

    /** QueryDSL 쿼리 팩토리: JPA 쿼리 생성 및 실행 담당 */
    private final JPAQueryFactory queryFactory;

    /**
     * 사용자명으로 사용자 검색 (QueryDSL 부분 일치 검색)
     *
     * <p>QueryDSL의 {@code contains()} 메서드를 사용하여 SQL의 {@code LIKE '%name%'} 조건을
     * 타입 안전하게 표현합니다.</p>
     *
     * <p>생성되는 SQL (예시):</p>
     * <pre>{@code
     * SELECT * FROM TPRMPP_CUSERI WHERE USR_NM LIKE '%홍%'
     * }</pre>
     *
     * @param name 검색할 사용자명 (부분 일치)
     * @return 이름에 해당 문자열을 포함하는 사용자 목록
     */
    @Override
    public List<CuserI> searchByName(String name) {
        // Q 타입: QueryDSL이 컴파일 시 CuserI 엔티티로부터 자동 생성한 메타 클래스
        QCuserI cuserI = QCuserI.cuserI;

        return queryFactory.selectFrom(cuserI)    // SELECT * FROM TPRMPP_CUSERI
                .where(cuserI.usrNm.contains(name)) // WHERE USR_NM LIKE '%name%'
                .fetch();                            // 결과 목록 반환 (비어있으면 빈 리스트)
    }
}
