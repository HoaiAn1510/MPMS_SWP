package com.example.manga_management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SmsService {
    private static final Map<String, String> ESMS_ERROR_MESSAGES = Map.of(
            "99", "Yêu cầu gửi SMS không hợp lệ",
            "101", "API Key hoặc Secret Key eSMS không chính xác",
            "104", "Brandname eSMS chưa đúng hoặc chưa được kích hoạt",
            "124", "RequestId gửi tới eSMS đã tồn tại",
            "146", "Mẫu nội dung CSKH chưa được đăng ký hoặc chưa được eSMS phê duyệt");

    private final String provider;
    private final String endpoint;
    private final String apiKey;
    private final String secretKey;
    private final String brandname;
    private final String smsType;
    private final boolean sandbox;
    private final boolean templateReady;
    private final Set<String> supportedCarriers;
    private final String otpTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SmsService(@Value("${app.sms.provider:esms}") String provider,
                      @Value("${app.sms.esms.endpoint}") String endpoint,
                      @Value("${app.sms.esms.api-key:}") String apiKey,
                      @Value("${app.sms.esms.secret-key:}") String secretKey,
                      @Value("${app.sms.esms.brandname:}") String brandname,
                      @Value("${app.sms.esms.sms-type:2}") String smsType,
                      @Value("${app.sms.esms.sandbox:true}") boolean sandbox,
                      @Value("${app.sms.esms.template-ready:false}") boolean templateReady,
                      @Value("${app.sms.esms.supported-carriers:}") String supportedCarriers,
                      @Value("${app.sms.otp-template}") String otpTemplate) {
        this.provider = valueOrEmpty(provider);
        this.endpoint = valueOrEmpty(endpoint);
        this.apiKey = valueOrEmpty(apiKey);
        this.secretKey = valueOrEmpty(secretKey);
        this.brandname = valueOrEmpty(brandname);
        this.smsType = valueOrEmpty(smsType);
        this.sandbox = sandbox;
        this.templateReady = templateReady;
        this.supportedCarriers = parseSupportedCarriers(supportedCarriers);
        this.otpTemplate = valueOrEmpty(otpTemplate);
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void sendOtp(String phone, String otp) {
        validateConfiguration();

        String normalizedPhone = normalizeVietnameseMobile(phone);
        Map<String, Object> payload = buildPayload(normalizedPhone, otp);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(20))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("eSMS trả về HTTP " + response.statusCode());
            }

            JsonNode result = objectMapper.readTree(response.body());
            String code = result.path("CodeResult").asText("");
            if (!"100".equals(code)) {
                String providerDetail = result.path("ErrorMessage").asText("");
                throw new IllegalStateException(resolveEsmsError(code, providerDetail));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gửi SMS bị gián đoạn", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể gửi OTP qua eSMS: " + exception.getMessage(), exception);
        }
    }

    Map<String, Object> buildPayload(String normalizedPhone, String otp) {
        String content = otpTemplate.replace("{OTP}", otp);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ApiKey", apiKey);
        payload.put("SecretKey", secretKey);
        payload.put("Phone", normalizedPhone);
        payload.put("Content", content);
        payload.put("Brandname", brandname);
        payload.put("SmsType", smsType);
        payload.put("IsUnicode", "0");
        payload.put("Sandbox", sandbox ? "1" : "0");
        payload.put("RequestId", UUID.randomUUID().toString());
        return payload;
    }

    private void validateConfiguration() {
        if (!"esms".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("SMS_PROVIDER phải được đặt thành esms");
        }
        if (endpoint.isBlank() || apiKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException("API Key hoặc Secret Key của eSMS chưa được cấu hình");
        }
        if (!"2".equals(smsType)) {
            throw new IllegalStateException("Gói SMS OTP/CSKH của eSMS yêu cầu ESMS_SMS_TYPE=2");
        }
        if (brandname.isBlank()) {
            throw new IllegalStateException("ESMS_BRANDNAME chưa được cấu hình");
        }
        if (!sandbox && !templateReady) {
            throw new IllegalStateException(
                    "Mẫu OTP eSMS chưa được xác nhận. Hãy chờ eSMS duyệt rồi đặt ESMS_TEMPLATE_READY=true");
        }
        if (!otpTemplate.contains("{OTP}")) {
            throw new IllegalStateException("SMS_OTP_TEMPLATE phải chứa biến {OTP}");
        }
    }

    private String normalizeVietnameseMobile(String phone) {
        String normalized = valueOrEmpty(phone).replaceAll("[^0-9+]", "");
        if (normalized.startsWith("+84")) {
            normalized = "0" + normalized.substring(3);
        } else if (normalized.startsWith("84")) {
            normalized = "0" + normalized.substring(2);
        }
        if (!normalized.matches("0(?:3|5|7|8|9)\\d{8}")) {
            throw new IllegalArgumentException("Số điện thoại di động Việt Nam không hợp lệ");
        }

        String carrier = identifyCarrier(normalized);
        if (!supportedCarriers.isEmpty() && !supportedCarriers.contains(carrier)) {
            throw new IllegalArgumentException(
                    "Gói eSMS test hiện chỉ hỗ trợ số Viettel hoặc Mobifone");
        }
        return normalized;
    }

    private static String identifyCarrier(String phone) {
        String prefix = phone.substring(0, 3);
        if (Set.of("032", "033", "034", "035", "036", "037", "038", "039",
                "086", "096", "097", "098").contains(prefix)) {
            return "VIETTEL";
        }
        if (Set.of("070", "076", "077", "078", "079", "089", "090", "093").contains(prefix)) {
            return "MOBIFONE";
        }
        return "OTHER";
    }

    private static Set<String> parseSupportedCarriers(String value) {
        return Arrays.stream(valueOrEmpty(value).split(","))
                .map(item -> item.trim().toUpperCase(Locale.ROOT))
                .filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    static String resolveEsmsError(String code, String providerDetail) {
        String message = ESMS_ERROR_MESSAGES.getOrDefault(
                valueOrEmpty(code),
                "eSMS từ chối yêu cầu");
        String detail = valueOrEmpty(providerDetail);
        if (!detail.isBlank()
                && !message.toLowerCase(Locale.ROOT).contains(detail.toLowerCase(Locale.ROOT))) {
            message += ": " + detail;
        }
        return message + " (mã " + valueOrEmpty(code) + ")";
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
