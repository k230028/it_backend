package com.kdb.it.infra.eai.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * [테스트 전용] EAI 표준전문 응답 픽스처.
 *
 * <p>{@link EaiErrorResponseParser}의 오프셋 상수를 재사용하지 않고 표준전문 각 공통부의 <b>필드 길이 합</b>으로 위치를 유도한다. 파서와 같은
 * 매직넘버로 픽스처를 만들면 오프셋이 틀려도 테스트가 통과하므로(자기참조), 유도한 위치가 실제 전문과 맞는지는 동결 골든 전문 {@code
 * src/test/resources/eai/ums-golden.b64}과 대조해 {@link EaiStandardResponseFixtureTest}가 검증한다.
 *
 * <p>응답 본문도 골든 전문을 재사용하므로 헤더 레이아웃이 바뀌면 픽스처가 먼저 깨진다.
 */
final class EaiStandardResponseFixture {

    /** 골든 전문 경로 — 실행 기준 디렉터리는 {@code it_backend}. */
    private static final Path GOLDEN = Path.of("src/test/resources/eai/ums-golden.b64");

    /** 길이 필드(전체전문길이/헤더길이/출력매체부길이)는 각 8바이트 숫자다. */
    private static final int LEN_FIELD = 8;

    /** 헤더길이(HER_LEN) 필드 시작 위치 — 전체전문길이 다음. */
    private static final int HEADER_LEN_OFFSET = LEN_FIELD;

    /**
     * 시스템공통부 길이 — 전체전문길이 8 + 헤더길이 8 + 출력매체부길이 8 + 전문버전정보 3 + 다국어구분 2 + 시스템환경구분 1 + IP주소 40 + MAC주소
     * 12 + GUID 38 + GUID진행일련번호 4 + 최초GUID 38 + 전송시스템코드 3 + 최초전송시스템코드 3 + 예비 12.
     */
    static final int SYS_COMMON_LEN = 8 + 8 + 8 + 3 + 2 + 1 + 40 + 12 + 38 + 4 + 38 + 3 + 3 + 12;

    /**
     * 거래공통부에서 RLT_TC(결과구분코드) 앞에 놓인 필드 길이 합 — 거래ID 10 + 수신시스템코드 3 + 화면ID 10 + 연계화면ID 10 + 화면제어구분 1 +
     * 요청응답구분 1 + 세부유형구분 1 + 채널유형 2 + 메시지채널 2 + 동기처리구분 1.
     */
    static final int RLT_TC_IN_TR_COMMON = 10 + 3 + 10 + 10 + 1 + 1 + 1 + 2 + 2 + 1;

    /** RLT_TC부터 REQ_DTM(요청일시) 앞까지 — 결과구분 1 + 원페이지당조회건수 5 + 다음페이지여부 1 + 요청페이지번호 5. */
    static final int RLT_TC_TO_REQ_DTM = 1 + 5 + 1 + 5;

    /** 요청일시(REQ_DTM) 길이 — {@code yyyyMMddHHmmssSSS}. */
    static final int REQ_DTM_LEN = 17;

    /** 메시지공통부 길이 — 메시지표시방법구분 1 + 오류발생전문항목 50 + 메시지건수 3 + 기타출력데이터건수 2. */
    static final int MSG_COMMON_LEN = 1 + 50 + 3 + 2;

    /** 출력매체부 길이 — 출력매체건수 3. */
    static final int PRO_MDA_PART_LEN = 3;

    private EaiStandardResponseFixture() {}

    /** 동결 골든 표준전문 바이트. 호출자가 자유롭게 변형할 수 있도록 매번 새 배열을 만든다. */
    static byte[] standardMessage() {
        try {
            return Base64.getDecoder().decode(Files.readString(GOLDEN).trim());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 전문이 스스로 신고하는 헤더 길이(HER_LEN). 공통부 길이를 코드에 다시 적지 않기 위해 전문에서 읽는다. */
    static int headerLen(byte[] message, Charset charset) {
        return Integer.parseInt(new String(message, HEADER_LEN_OFFSET, LEN_FIELD, charset).trim());
    }

    /** 결과구분코드(RLT_TC) 위치 — 시스템공통부 뒤 거래공통부 내부 오프셋. */
    static int rltTcOffset() {
        return SYS_COMMON_LEN + RLT_TC_IN_TR_COMMON;
    }

    /** 요청일시(REQ_DTM) 위치 — RLT_TC 위치 검증용 앵커. */
    static int reqDtmOffset() {
        return rltTcOffset() + RLT_TC_TO_REQ_DTM;
    }

    /** 메시지표시방법구분코드(MSG_IDCT_TC) 위치 — 헤더 끝에서 출력매체부와 메시지공통부를 되짚어 유도한다. */
    static int msgIdctTcOffset(byte[] message, Charset charset) {
        return headerLen(message, charset) - PRO_MDA_PART_LEN - MSG_COMMON_LEN;
    }

    /** 오류 플래그와 SEEAI 코드를 모두 갖춘 EAI 오류 응답. 오류 코드는 헤더 뒤 개별부에 넣는다. */
    static byte[] errorResponse(String errorCode, Charset charset) {
        byte[] response = flaggedResponse(charset);
        byte[] code = errorCode.getBytes(charset);
        System.arraycopy(code, 0, response, headerLen(response, charset), code.length);
        return response;
    }

    /** 오류 플래그만 세우고 SEEAI 코드는 없는 응답. */
    static byte[] flaggedResponse(Charset charset) {
        byte[] response = standardMessage();
        response[rltTcOffset()] = '2';
        response[msgIdctTcOffset(response, charset)] = '1';
        return response;
    }
}
