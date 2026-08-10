package com.kdb.it.common.mfa.dto;

import com.kdb.it.common.mfa.domain.MfaMethod;
import com.kdb.it.common.mfa.domain.MfaPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** MFA challenge API 입출력 계약을 묶는다. */
public final class MfaDto {

    private MfaDto() {}

    /** MFA challenge 시작 요청이다. */
    @Schema(name = "MfaStartRequest", description = "MFA challenge 시작 요청")
    public record MfaStartRequest(
            @NotNull
                    @Schema(
                            description = "MFA 사용 목적",
                            allowableValues = {"LOGIN", "APPROVAL"})
                    MfaPurpose purpose,
            @NotNull
                    @Schema(
                            description = "인증 수단",
                            allowableValues = {"FINGER_VEIN", "FIDO", "MOTP"})
                    MfaMethod method) {}

    /** MFA challenge 검증 요청이다. */
    @Schema(name = "MfaVerifyRequest", description = "MFA challenge 검증 요청")
    public record MfaVerifyRequest(
            @NotBlank @Schema(description = "공급자가 발급한 challenge 식별자") String providerChallengeId,
            @Schema(description = "인증 수단별 검증값. FIDO에는 빈 문자열을 전송한다.", nullable = true)
                    String verificationValue) {}

    /** Task 5 로그인 시작 흐름이 httpOnly 쿠키로 보관할 로그인 대기 등록 결과다. */
    public record LoginPendingRegistration(UUID pendingId, long remainingSeconds) {}

    /** MFA challenge 시작 응답이다. */
    @Schema(name = "MfaChallengeResponse", description = "MFA challenge 시작 응답")
    public record MfaChallengeResponse(
            @Schema(description = "서버 MFA 거래 식별자", requiredMode = Schema.RequiredMode.REQUIRED)
                    UUID challengeId,
            @Schema(description = "공급자 challenge 식별자", requiredMode = Schema.RequiredMode.REQUIRED)
                    String providerChallengeId,
            @Schema(
                            description = "표시용 QR 데이터",
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            nullable = true)
                    String qrData,
            @Schema(description = "서버 기준 남은 유효 시간(초)", requiredMode = Schema.RequiredMode.REQUIRED)
                    long remainingSeconds) {}

    /** MFA challenge 검증 응답이다. */
    @Schema(name = "MfaVerifyResponse", description = "MFA challenge 검증 응답")
    public record MfaVerifyResponse(
            @Schema(description = "검증 성공 여부", requiredMode = Schema.RequiredMode.REQUIRED)
                    boolean verified,
            @Schema(
                            description = "서버 기준 남은 증표 유효 시간(초)",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    long remainingSeconds) {}
}
