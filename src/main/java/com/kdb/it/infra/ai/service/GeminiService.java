package com.kdb.it.infra.ai.service;

import com.kdb.it.infra.ai.dto.GeminiDto;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gemini API 연동 서비스
 *
 * <p>
 * Google Gemini API를 호출하여 AI 응답을 생성합니다.
 * {@code RestClient}를 사용한 동기 방식 HTTP 통신입니다.
 * </p>
 *
 * <p>
 * 첨부파일 메타데이터는 단건 조회로만 사용하고, 파일 시스템 I/O와 외부 Gemini API 호출은
 * 트랜잭션으로 감싸지 않습니다. 긴 외부 호출이 DB 트랜잭션을 점유하지 않도록 경계를 분리합니다.
 * </p>
 *
 * <p>
 * API 키는 {@code application.properties}의 {@code gemini.api.key}로 관리합니다.
 * </p>
 */
@Slf4j
@Service
public class GeminiService {

    /** Gemini API 키 */
    private final String apiKey;

    /** 사용할 Gemini 모델명 (예: gemini-2.0-flash) */
    private final String model;

    /** HTTP 클라이언트 */
    private final RestClient restClient;

    /** 첨부파일 메타데이터 조회용 리포지토리 */
    private final FileRepository fileRepository;

    /** Gemini 첨부 파일당 최대 허용 크기(20MB). 초과 시 해당 파일은 스킵한다. */
    private static final long MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024;

    /**
     * Gemini가 지원하는 MIME 타입 목록
     *
     * <p>
     * Gemini API가 직접 처리 가능한 형식만 포함합니다.
     * 미지원 형식(hwp, doc 등)은 전송 시 오류가 발생하므로 사전에 필터링합니다.
     * </p>
     */
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            // 이미지
            "image/jpeg", "image/png", "image/gif", "image/webp",
            // 문서
            "application/pdf",
            // 텍스트
            "text/plain", "text/csv"
    );

    /**
     * 파일 확장자 → MIME 타입 매핑
     */
    private static final Map<String, String> EXT_TO_MIME = Map.of(
            "jpg",  "image/jpeg",
            "jpeg", "image/jpeg",
            "png",  "image/png",
            "gif",  "image/gif",
            "webp", "image/webp",
            "pdf",  "application/pdf",
            "txt",  "text/plain",
            "csv",  "text/csv"
    );

    /**
     * 생성자: application.properties에서 Gemini 설정 주입
     *
     * @param baseUrl        Gemini API 기본 URL
     * @param apiKey         Gemini API 키
     * @param model          사용할 모델명
     * @param fileRepository 첨부파일 리포지토리
     */
    public GeminiService(
            @Value("${gemini.api.base-url}") String baseUrl,
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.api.model}") String model,
            FileRepository fileRepository) {
        this.apiKey = apiKey;
        this.model = model;
        this.fileRepository = fileRepository;
        // 외부 Gemini API 응답 지연이 스레드를 무한 점유하지 않도록 connect/read 타임아웃을 강제한다.
        // JDK HttpClient의 connectTimeout(5s) + 요청별 readTimeout(60s)을 RequestFactory에 설정한다.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Gemini API에 프롬프트를 전달하고 응답 텍스트를 반환합니다.
     *
     * <p>
     * 처리 순서:
     * </p>
     * <ol>
     * <li>프론트엔드 요청 DTO를 Gemini API 요청 형식으로 변환</li>
     * <li>Gemini generateContent API 호출</li>
     * <li>응답에서 텍스트 추출 후 프론트엔드 응답 DTO로 반환</li>
     * </ol>
     *
     * @param request 프론트엔드 요청 DTO (prompt, systemInstruction)
     * @return Gemini 응답 텍스트와 모델명이 담긴 응답 DTO
     * @throws RuntimeException Gemini API 호출 실패 또는 응답 파싱 오류 시
     */
    public GeminiDto.Response generate(GeminiDto.Request request) {
        // 요청 정보 로그 (첨부파일 포함 여부 확인용)
        List<String> requestedFlMpnIds = (request.getFlMpnIds() != null) ? request.getFlMpnIds() : Collections.emptyList();
        log.info("Gemini 요청 - 모델: {}, 프롬프트 길이: {}, 요청 첨부파일 수: {}",
                model, request.getPrompt().length(), requestedFlMpnIds.size());
        if (!requestedFlMpnIds.isEmpty()) {
            log.info("Gemini 요청 - 첨부파일 flMpnIds: {}", requestedFlMpnIds);
        }

        // 파일 첨부 처리 (결과 추적)
        List<GeminiDto.Part> fileParts = new ArrayList<>();
        List<String> skippedFiles = new ArrayList<>();

        for (String flMpnId : requestedFlMpnIds) {
            FilePartResult result = buildFilePartFromFlMngNo(flMpnId);
            if (result.part() != null) {
                fileParts.add(result.part());
            } else {
                skippedFiles.add(flMpnId + ": " + result.skipReason());
            }
        }

        log.info("Gemini 파일 첨부 결과 - 성공: {}개, 건너뜀: {}개 {}",
                fileParts.size(), skippedFiles.size(), skippedFiles.isEmpty() ? "" : skippedFiles);

        // Gemini API 요청 바디 구성
        GeminiDto.GeminiApiRequest apiRequest = buildApiRequest(request, fileParts);

        // API 엔드포인트: /v1beta/models/{model}:generateContent?key={apiKey}
        String endpoint = String.format("/v1beta/models/%s:generateContent?key=%s", model, apiKey);

        try {
            GeminiDto.GeminiApiResponse apiResponse = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(apiRequest)
                    .retrieve()
                    .body(GeminiDto.GeminiApiResponse.class);

            // 응답 텍스트 추출
            String responseText = extractText(apiResponse);
            log.info("Gemini API 응답 수신 완료 - 텍스트 길이: {}", responseText.length());

            return GeminiDto.Response.builder()
                    .text(responseText)
                    .model(model)
                    .attachedFileCount(fileParts.size())
                    .skippedFiles(skippedFiles)
                    .build();

        } catch (Exception e) {
            log.error("Gemini API 호출 실패 - 모델: {}, 오류: {}", model, e.getMessage(), e);
            throw new RuntimeException("Gemini API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * Gemini API 요청 바디를 구성합니다.
     *
     * <p>
     * Parts 순서: [파일1, 파일2, ..., 텍스트 프롬프트]
     * 파일을 먼저 보내야 Gemini가 컨텍스트를 올바르게 파악합니다.
     * </p>
     *
     * @param request   프론트엔드 요청 DTO
     * @param fileParts 이미 로딩된 파일 파트 목록
     * @return Gemini API 요청 바디
     */
    private GeminiDto.GeminiApiRequest buildApiRequest(GeminiDto.Request request, List<GeminiDto.Part> fileParts) {
        // Parts 구성: [파일1, 파일2, ..., 텍스트 프롬프트] 순서
        List<GeminiDto.Part> parts = new ArrayList<>(fileParts);

        // 텍스트 프롬프트 파트 추가
        parts.add(GeminiDto.Part.builder().text(request.getPrompt()).build());

        GeminiDto.Content userContent = GeminiDto.Content.builder()
                .role("user")
                .parts(parts)
                .build();

        GeminiDto.GeminiApiRequest.GeminiApiRequestBuilder builder = GeminiDto.GeminiApiRequest.builder()
                .contents(List.of(userContent))
                .generationConfig(GeminiDto.GenerationConfig.builder()
                        .temperature(0.7)
                        .maxOutputTokens(8192)
                        .build());

        // 시스템 지시문이 있는 경우 추가
        if (request.getSystemInstruction() != null && !request.getSystemInstruction().isBlank()) {
            builder.systemInstruction(GeminiDto.SystemInstruction.builder()
                    .parts(List.of(GeminiDto.Part.builder()
                            .text(request.getSystemInstruction())
                            .build()))
                    .build());
        }

        return builder.build();
    }

    /**
     * 파일 첨부 처리 결과 래퍼
     *
     * @param part       성공 시 inlineData 파트, 실패 시 null
     * @param skipReason 건너뛴 이유 (성공 시 null)
     */
    private record FilePartResult(GeminiDto.Part part, String skipReason) {
        static FilePartResult success(GeminiDto.Part part) {
            return new FilePartResult(part, null);
        }
        static FilePartResult skip(String reason) {
            return new FilePartResult(null, reason);
        }
    }

    /**
     * 파일관리번호로 파일을 읽어 Gemini inlineData 파트를 생성합니다.
     *
     * <p>
     * 실패 시 예외 대신 {@link FilePartResult#skip(String)}을 반환하여
     * 호출자가 건너뜀 사유를 로그/응답에 포함할 수 있습니다.
     * </p>
     *
     * @param flMpnId 파일매핑ID (예: FL_00000001)
     * @return FilePartResult (성공 시 part 포함, 실패 시 skipReason 포함)
     */
    private FilePartResult buildFilePartFromFlMngNo(String flMpnId) {
        // 1. DB에서 파일 메타데이터 조회
        Cfilem cfilem = fileRepository.findByFlMpnIdAndDelYn(flMpnId, "N").orElse(null);
        if (cfilem == null) {
            return FilePartResult.skip("DB에서 파일을 찾을 수 없음 (삭제되었거나 존재하지 않는 번호)");
        }

        if (!StringUtils.hasText(cfilem.getFlKpnPth()) || !StringUtils.hasText(cfilem.getFlPysNm())) {
            return FilePartResult.skip("파일 메타데이터 불완전: " + flMpnId);
        }

        // 2. 파일명 기반 MIME 타입 감지
        String filename = StringUtils.hasText(cfilem.getFlNm())
                ? cfilem.getFlNm()
                : cfilem.getFlPysNm();
        String mimeType = detectMimeType(filename);
        if (mimeType == null) {
            return FilePartResult.skip("Gemini 미지원 형식: " + filename
                    + " (지원: jpg/png/gif/webp/pdf/txt/csv)");
        }

        // 3. 디스크에서 파일 읽기
        Path filePath = Paths.get(cfilem.getFlKpnPth()).resolve(cfilem.getFlPysNm());
        if (!Files.exists(filePath)) {
            return FilePartResult.skip("디스크에 파일 없음: " + filePath
                    + " (저장 경로와 실제 파일 위치가 다를 수 있음)");
        }

        // 대형 파일이 메모리를 점유하거나 Base64 폭증을 일으키지 않도록 읽기 전에 크기를 사전검사한다.
        try {
            long size = Files.size(filePath);
            if (size > MAX_ATTACHMENT_BYTES) {
                log.warn("[Gemini] 첨부 크기 초과로 스킵 - flMpnId: {}, size: {}MB (제한: 20MB)",
                        flMpnId, size / (1024 * 1024));
                return FilePartResult.skip("첨부 크기 초과(20MB): " + (size / (1024 * 1024)) + "MB");
            }
        } catch (IOException e) {
            log.warn("[Gemini] 첨부 크기 조회 실패로 스킵 - filePath: {}", filePath, e);
            return FilePartResult.skip("첨부 크기 조회 실패: " + e.getMessage());
        }

        byte[] fileBytes;
        try {
            fileBytes = Files.readAllBytes(filePath);
        } catch (IOException e) {
            // 파일 읽기 실패 시 해당 파일만 스킵하고 원인 예외를 warn으로 남긴다.
            log.warn("[Gemini] AI 분석 파일 읽기 실패로 스킵 - filePath: {}", filePath, e);
            return FilePartResult.skip("파일 읽기 실패: " + e.getMessage());
        }

        // 4. Base64 인코딩
        String base64Data = Base64.getEncoder().encodeToString(fileBytes);

        log.info("Gemini 파일 첨부 성공 - flMpnId: {}, 파일명: {}, MIME: {}, 크기: {}KB",
                flMpnId, filename, mimeType, fileBytes.length / 1024);

        return FilePartResult.success(
                GeminiDto.Part.builder()
                        .inlineData(GeminiDto.InlineData.builder()
                                .mimeType(mimeType)
                                .data(base64Data)
                                .build())
                        .build()
        );
    }

    /**
     * 파일명의 확장자로 MIME 타입을 감지합니다.
     *
     * @param filename 파일명 (원본 파일명)
     * @return Gemini 지원 MIME 타입 (미지원 시 null)
     */
    private String detectMimeType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        String mimeType = EXT_TO_MIME.get(ext);
        if (mimeType == null || !SUPPORTED_MIME_TYPES.contains(mimeType)) {
            return null;
        }
        return mimeType;
    }

    /**
     * Gemini API 응답에서 텍스트를 추출합니다.
     *
     * @param apiResponse Gemini API 원본 응답
     * @return 추출된 응답 텍스트
     * @throws RuntimeException 응답이 비어있거나 파싱할 수 없는 경우
     */
    private String extractText(GeminiDto.GeminiApiResponse apiResponse) {
        if (apiResponse == null
                || apiResponse.getCandidates() == null
                || apiResponse.getCandidates().isEmpty()) {
            throw new RuntimeException("Gemini API 응답이 비어있습니다.");
        }

        GeminiDto.Candidate candidate = apiResponse.getCandidates().get(0);

        if (candidate.getContent() == null
                || candidate.getContent().getParts() == null
                || candidate.getContent().getParts().isEmpty()) {
            throw new RuntimeException("Gemini 응답 내용을 파싱할 수 없습니다.");
        }

        return candidate.getContent().getParts().get(0).getText();
    }
}
