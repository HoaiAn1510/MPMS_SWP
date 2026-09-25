package com.example.manga_management.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

// Every file upload in this app is driven by fetch()/FormData and expects a
// JSON {status, message} body back. Without this handler, exceeding the
// multipart size limit (or a malformed multipart request) lets Tomcat abort
// the connection mid-response, which the browser reports as an empty body
// ("Unexpected end of JSON input") instead of a readable error.
@RestControllerAdvice
public class GlobalUploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "error");
        result.put("message", "File quá lớn, vui lòng chọn file nhỏ hơn (tối đa 20MB)!");
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(result);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> handleMissingPart(MissingServletRequestPartException ex) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "error");
        result.put("message", "Thiếu file trong yêu cầu, vui lòng chọn file rồi thử lại!");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }
}
