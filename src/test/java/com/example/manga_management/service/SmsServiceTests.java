package com.example.manga_management.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Map;

class SmsServiceTests {

    @Test
    void mapsInvalidCredentialsResponse() {
        assertEquals(
                "API Key hoặc Secret Key eSMS không chính xác: Authorize Failed (mã 101)",
                SmsService.resolveEsmsError("101", "Authorize Failed"));
    }

    @Test
    void mapsInactiveBrandnameResponse() {
        assertEquals(
                "Brandname eSMS chưa đúng hoặc chưa được kích hoạt: Brand name code is not exist (mã 104)",
                SmsService.resolveEsmsError("104", "Brand name code is not exist"));
    }

    @Test
    void mapsUnregisteredTemplateResponse() {
        assertEquals(
                "Mẫu nội dung CSKH chưa được đăng ký hoặc chưa được eSMS phê duyệt: Sai template Brandname CSKH (mã 146)",
                SmsService.resolveEsmsError("146", "Sai template Brandname CSKH"));
    }

    @Test
    void buildsApprovedCustomerCarePayload() {
        SmsService service = new SmsService(
                "esms",
                "https://rest.esms.vn/MainService.svc/json/SendMultipleMessage_V4_post_json/",
                "api-key",
                "secret-key",
                "Baotrixemay",
                "2",
                false,
                true,
                "VIETTEL,MOBIFONE",
                "{OTP} la ma xac minh dang ky Baotrixemay cua ban");

        Map<String, Object> payload = service.buildPayload("0901234567", "123456");

        assertEquals("123456 la ma xac minh dang ky Baotrixemay cua ban", payload.get("Content"));
        assertEquals("Baotrixemay", payload.get("Brandname"));
        assertEquals("2", payload.get("SmsType"));
        assertEquals("0", payload.get("Sandbox"));
        assertNotEquals("", payload.get("RequestId"));
    }
}
