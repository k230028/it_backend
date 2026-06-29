package com.kdb.it.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 로컬 Oracle 가용 여부에 따라 통합 테스트 실행을 결정하는 JUnit 5 조건.
 *
 * <p>{@code ExecutionCondition}은 Spring {@code ApplicationContext} 로드보다 먼저 평가되므로,
 * 로컬 Oracle이 꺼져 있으면 컨텍스트 로드 실패(빨간 빌드) 대신 깨끗한 스킵(노란 결과)으로 처리된다.
 * {@code @BeforeAll} 단계는 이미 컨텍스트가 기동된 뒤라 이 목적에 부적합하다.</p>
 */
public class OracleAvailableCondition implements ExecutionCondition {

    private static final String DB_HOST = "127.0.0.1";
    private static final int DB_PORT = 11521;
    private static final int PROBE_TIMEOUT_MS = 1000;

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(DB_HOST, DB_PORT), PROBE_TIMEOUT_MS);
            return ConditionEvaluationResult.enabled(
                    "로컬 Oracle(" + DB_HOST + ":" + DB_PORT + ") 가동 — 통합 테스트 실행");
        } catch (IOException e) {
            return ConditionEvaluationResult.disabled(
                    "로컬 Oracle(" + DB_HOST + ":" + DB_PORT + ") 미가동 — 통합 테스트 스킵");
        }
    }
}
