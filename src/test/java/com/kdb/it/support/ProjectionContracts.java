package com.kdb.it.support;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * 읽기 프로젝션의 노출 계약을 리플렉션으로 단정할 때 쓰는 도우미입니다.
 *
 * <p>프로젝션 인터페이스에 승인되지 않은 필드가 슬며시 늘어나는 것을 막는 단정(`*ProjectionIt`)이 여러 도메인에 흩어져 있습니다. 그 단정들이 {@code
 * getDeclaredMethods()}를 그대로 세면 <b>커버리지를 켠 실행에서만</b> 깨집니다 — JaCoCo 에이전트가 본문 있는 메서드를 가진 타입에 합성
 * {@code $jacocoInit}을 넣기 때문입니다. 실제로 {@code AdminUserView}에 {@code default getBbrNm()}이 추가되면서 그 일이
 * 났고(MIG-13), 실패 메시지가 데이터 문제처럼 보여 진단을 헤매게 만들었습니다.
 *
 * <p>합성 메서드는 계약의 일부가 아니므로(컴파일러가 만드는 bridge 메서드도 마찬가지) 여기서 한 번 걸러내고, 모든 단정이 이 함수를 거치게 합니다.
 */
public final class ProjectionContracts {

    private ProjectionContracts() {
        throw new UnsupportedOperationException("유틸리티 — 인스턴스화 금지");
    }

    /**
     * 타입이 직접 선언한 메서드 이름을 반환합니다.
     *
     * @param type 프로젝션 인터페이스 또는 응답 타입
     * @return 합성 메서드를 제외한 선언 메서드 이름. 선언 순서는 JVM이 정하므로 순서에 기대지 않습니다
     */
    public static List<String> declaredMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(Method::getName)
                .toList();
    }
}
