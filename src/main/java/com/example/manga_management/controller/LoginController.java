package com.example.manga_management.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.manga_management.entity.User;
import com.example.manga_management.repository.ProposalRepository;
import com.example.manga_management.repository.TantoEditorRepository;
import com.example.manga_management.repository.UserRepository;
import com.example.manga_management.service.EmailService;
import com.example.manga_management.service.OtpService;
import com.example.manga_management.service.UserService;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/login")
public class LoginController {

    private final UserService userService;
    private final ProposalRepository proposalRepository;
    private final TantoEditorRepository tantoEditorRepository;
    private final UserRepository userRepository;
    private final OtpService otpService;
    private final EmailService emailService;

    public LoginController(UserService userService, 
                           ProposalRepository proposalRepository, 
                           TantoEditorRepository tantoEditorRepository,
                           UserRepository userRepository,
                           OtpService otpService,
                           EmailService emailService) {
        this.userService = userService;
        this.proposalRepository = proposalRepository;
        this.tantoEditorRepository = tantoEditorRepository;
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.emailService = emailService;
    }   

    @GetMapping("")
    public String loginPage() {
        return "login";
    }

    @PostMapping("")
    public String handleLogin(
            @RequestParam String txtUsername,
            @RequestParam String txtPassword,
            HttpSession session,
            Model model) {
        User user = userService.login(txtUsername, txtPassword);

        if (user == null) {
            model.addAttribute("error", "Sai thông tin đăng nhập");
            return "login";
        }

        session.setAttribute("user", user);

        switch (user.getRole().toLowerCase()) {
            case "admin":
                return "redirect:/manga/system-admin";
            case "board":
                return "redirect:/manga/editor";
            case "tantou":
                return "redirect:/manga/tantou";
            case "mangaka":
                return "redirect:/manga/mangaka";
            case "assistant":
                return "redirect:/manga/assistant";
            default:
                model.addAttribute("error", "Vai trò người dùng không hợp lệ!");
                return "login";
        }
    }

    @PostMapping("/forgot-password/send-otp")
    public ResponseEntity<Map<String, Object>> sendForgotPasswordOtp(
            @RequestBody Map<String, String> body,
            HttpSession session) {
        String account = body.getOrDefault("account", "").trim();
        if (account.isBlank()) {
            return ResponseEntity.badRequest().body(result(false, "Vui lòng nhập Username hoặc Email"));
        }

        User user = userRepository.findByUsername(account)
                .or(() -> userRepository.findByEmail(account))
                .orElse(null);
        session.removeAttribute("forgotPasswordUserId");
        session.removeAttribute("forgotPasswordOtpKey");

        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            String otpKey = user.getId() + "_forgot_password";
            String otp = otpService.generateOtp(otpKey);
            try {
                emailService.sendOtpEmail(user.getEmail(), otp);
            } catch (MailException exception) {
                return ResponseEntity.status(503).body(result(false,
                        "Không thể gửi OTP lúc này. Vui lòng thử lại sau"));
            }
            session.setAttribute("forgotPasswordUserId", user.getId());
            session.setAttribute("forgotPasswordOtpKey", otpKey);
        }

        // Không tiết lộ tài khoản có tồn tại hay không.
        return ResponseEntity.ok(result(true,
                "Nếu tài khoản có Email liên kết, mã OTP đã được gửi đến Email đó"));
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<Map<String, Object>> resetForgottenPassword(
            @RequestBody Map<String, String> body,
            HttpSession session) {
        String otp = body.getOrDefault("otp", "").trim();
        String newPassword = body.getOrDefault("newPassword", "");
        String confirmPassword = body.getOrDefault("confirmPassword", "");
        if (!otp.matches("\\d{6}")) {
            return ResponseEntity.badRequest().body(result(false, "Mã OTP phải gồm 6 chữ số"));
        }
        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(result(false, "Mật khẩu mới phải có ít nhất 6 ký tự"));
        }
        if (!newPassword.equals(confirmPassword)) {
            return ResponseEntity.badRequest().body(result(false, "Mật khẩu xác nhận không khớp"));
        }

        String userId = (String) session.getAttribute("forgotPasswordUserId");
        String otpKey = (String) session.getAttribute("forgotPasswordOtpKey");
        if (userId == null || otpKey == null || !otpService.verifyOtp(otpKey, otp)) {
            return ResponseEntity.badRequest().body(result(false, "OTP không đúng hoặc đã hết hạn"));
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(result(false, "Không thể cập nhật mật khẩu"));
        }
        user.setPassword(newPassword);
        userRepository.save(user);
        session.removeAttribute("forgotPasswordUserId");
        session.removeAttribute("forgotPasswordOtpKey");
        return ResponseEntity.ok(result(true, "Đổi mật khẩu thành công. Bạn có thể đăng nhập ngay"));
    }

    private Map<String, Object> result(boolean success, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("message", message);
        return response;
    }

    

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
