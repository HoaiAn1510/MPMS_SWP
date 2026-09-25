package com.example.manga_management.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Kết quả trả về chung của API")
public class ApiResult {

    @Schema(description = "Trạng thái: success hoặc error", example = "success")
    private String status;

    @Schema(description = "Thông báo kết quả", example = "Đã gửi OTP tới email của bạn")
    private String message;

    @Schema(description = "Dữ liệu bổ sung (tuỳ endpoint)", example = "user@example.com")
    private String data;

    public ApiResult(String status, String message) {
        this.status = status;
        this.message = message;
    }

    public ApiResult(String status, String message, String data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }
}
