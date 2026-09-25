package com.example.manga_management.controller;

import com.example.manga_management.dto.ApiResult;
import com.example.manga_management.entity.User;
import com.example.manga_management.repository.*;
import com.example.manga_management.service.EmailService;
import com.example.manga_management.service.OtpService;
import com.example.manga_management.service.SmsService;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
@Tag(name = "Account", description = "Quản lý tài khoản: xác thực OTP và đổi mật khẩu")
public class AccountController {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final MangakaRepository mangakaRepository;
    private final AssistantRepository assistantRepository;
    private final TantoEditorRepository tantoEditorRepository;
    private final BoardRepository boardRepository;
    private final SeriesRepository seriesRepository;
    private final SubmissionRepository submissionRepository;
    private final EditorialVoteRepository editorialVoteRepository;

    public AccountController(UserRepository userRepository,
                             OtpService otpService,
                             EmailService emailService,
                             SmsService smsService,
                             MangakaRepository mangakaRepository,
                             AssistantRepository assistantRepository,
                             TantoEditorRepository tantoEditorRepository,
                             BoardRepository boardRepository,
                             SeriesRepository seriesRepository,
                             SubmissionRepository submissionRepository,
                             EditorialVoteRepository editorialVoteRepository) {
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.emailService = emailService;
        this.smsService = smsService;
        this.mangakaRepository = mangakaRepository;
        this.assistantRepository = assistantRepository;
        this.tantoEditorRepository = tantoEditorRepository;
        this.boardRepository = boardRepository;
        this.seriesRepository = seriesRepository;
        this.submissionRepository = submissionRepository;
        this.editorialVoteRepository = editorialVoteRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 1: Send OTP
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(
        summary = "Gửi OTP xác thực tới email",
        description = "Gửi mã OTP 6 số tới email đã liên kết với tài khoản đang đăng nhập. OTP có hiệu lực 5 phút."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", description = "Gửi OTP thành công",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        ),
        @ApiResponse(
            responseCode = "400", description = "Email không hợp lệ hoặc chưa có email",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        ),
        @ApiResponse(
            responseCode = "401", description = "Chưa đăng nhập",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        )
    })
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResult> sendOtp(@RequestBody(required = false) Map<String, String> body,
                                             HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResult("error", "Chưa đăng nhập"));
        }
        String channel = body == null ? "email" : body.getOrDefault("channel", "email");
        ApiResult channelError = validateOtpChannel(user, channel);
        if (channelError != null) {
            return ResponseEntity.badRequest().body(channelError);
        }
        try {
            session.removeAttribute("otpVerified");
            session.removeAttribute("otpVerifiedAt");
            String otpKey = user.getId() + "_password_" + channel;
            String otp = otpService.generateOtp(otpKey);
            sendOtpByChannel(user, channel, otp);
            session.setAttribute("passwordOtpKey", otpKey);
            return ResponseEntity.ok(new ApiResult("success", channel.equals("phone")
                    ? "Đã gửi OTP tới số điện thoại của bạn"
                    : "Đã gửi OTP tới email của bạn"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new ApiResult("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 2: Verify OTP
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(
        summary = "Xác thực mã OTP",
        description = "Kiểm tra mã OTP người dùng nhập. Nếu đúng và còn hạn, đánh dấu session đã xác thực để cho phép đổi mật khẩu."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", description = "OTP hợp lệ",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        ),
        @ApiResponse(
            responseCode = "400", description = "OTP sai hoặc đã hết hạn",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        ),
        @ApiResponse(
            responseCode = "401", description = "Chưa đăng nhập",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        )
    })
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResult> verifyOtp(@RequestBody Map<String, String> body,
                                               HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResult("error", "Chưa đăng nhập"));
        }
        String otp = body.get("otp");
        if (otp == null || !otp.matches("\\d{6}")) {
            return ResponseEntity.badRequest()
                    .body(new ApiResult("error", "Vui lòng nhập đúng mã OTP gồm 6 số"));
        }
        String otpKey = (String) session.getAttribute("passwordOtpKey");
        boolean valid = otpKey != null && otpService.verifyOtp(otpKey, otp);
        if (valid) {
            session.setAttribute("otpVerified", true);
            session.setAttribute("otpVerifiedAt", System.currentTimeMillis());
            session.removeAttribute("passwordOtpKey");
            return ResponseEntity.ok(new ApiResult("success", "Xác thực OTP thành công"));
        } else {
            return ResponseEntity.badRequest()
                    .body(new ApiResult("error", "OTP không đúng hoặc đã hết hạn"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 3: Change Password
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(
        summary = "Đổi mật khẩu sau khi xác thực OTP",
        description = "Cập nhật mật khẩu mới. Yêu cầu đã verify OTP trước đó trong cùng session. Mật khẩu tối thiểu 6 ký tự."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", description = "Đổi mật khẩu thành công",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        ),
        @ApiResponse(
            responseCode = "400", description = "Mật khẩu không hợp lệ hoặc không khớp",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        ),
        @ApiResponse(
            responseCode = "403", description = "Chưa xác thực OTP",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        ),
        @ApiResponse(
            responseCode = "401", description = "Chưa đăng nhập",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiResult.class))
        )
    })
    @PostMapping("/change-password")
    public ResponseEntity<ApiResult> changePassword(@RequestBody Map<String, String> body,
                                                    HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResult("error", "Chưa đăng nhập"));
        }

        Boolean otpVerified = (Boolean) session.getAttribute("otpVerified");
        Long otpVerifiedAt = (Long) session.getAttribute("otpVerifiedAt");
        boolean otpExpired = otpVerifiedAt == null
                || System.currentTimeMillis() - otpVerifiedAt > 5 * 60 * 1000L;
        if (!Boolean.TRUE.equals(otpVerified) || otpExpired) {
            session.removeAttribute("otpVerified");
            session.removeAttribute("otpVerifiedAt");
            return ResponseEntity.status(403)
                    .body(new ApiResult("error", "Chưa xác thực OTP hoặc phiên xác thực đã hết hạn"));
        }

        String newPassword = body.get("newPassword");
        String confirmPassword = body.get("confirmPassword");

        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiResult("error", "Mật khẩu không được để trống"));
        }
        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest()
                    .body(new ApiResult("error", "Mật khẩu phải có ít nhất 6 ký tự"));
        }
        if (!newPassword.equals(confirmPassword)) {
            return ResponseEntity.badRequest()
                    .body(new ApiResult("error", "Mật khẩu nhập lại không khớp"));
        }

        user.setPassword(newPassword);
        User savedUser = userRepository.save(user);

        session.removeAttribute("otpVerified");
        session.removeAttribute("otpVerifiedAt");
        session.setAttribute("user", savedUser);

        return ResponseEntity.ok(new ApiResult("success", "Đổi mật khẩu thành công"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 4: Get Profile
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(summary = "[SWAGGER] Lấy thông tin cá nhân user đang đăng nhập")
    @GetMapping("/profile")
    @ResponseBody
    public Map<String, Object> getProfile(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }
        result.put("status", "success");
        result.put("id", user.getId());
        result.put("fullname", user.getFullname());
        result.put("username", user.getUsername());
        result.put("email", user.getEmail());
        result.put("avatar", user.getAvatar());
        result.put("profile", user.getProfile());
        result.put("phone", user.getPhone());
        result.put("phoneVerified", user.isPhoneVerified());
        result.put("socialLinks", user.getSocialLinks());
        result.put("emailVerified", user.isEmailVerified());
        result.put("role", user.getRole());

        String role = user.getRole();

        if ("mangaka".equalsIgnoreCase(role)) {
            mangakaRepository.findByUserId(user.getId()).ifPresent(mgk -> {
                result.put("profileId", mgk.getId());
                if (mgk.getEditor() != null) {
                    Map<String, Object> editorInfo = new HashMap<>();
                    editorInfo.put("editorId", mgk.getEditor().getId());
                    editorInfo.put("editorName", mgk.getEditor().getUser().getFullname());
                    result.put("editor", editorInfo);
                }
                List<Map<String, Object>> assistants = assistantRepository.findByMangakaId(mgk.getId())
                    .stream().map(a -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("assistantId", a.getId());
                        m.put("name", a.getUser().getFullname());
                        m.put("salaryPerTask", a.getSalaryPerTask());
                        m.put("status", a.getStatus());
                        return m;
                    }).toList();
                result.put("assistants", assistants);
                List<Map<String, Object>> seriesList = seriesRepository.findByProposal_Mangaka_Id(mgk.getId())
                    .stream().map(s -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("seriesId", s.getId());
                        m.put("seriesName", s.getSeriesName());
                        m.put("genre", s.getGenre());
                        m.put("status", s.getStatus());
                        m.put("startDate", s.getStartDate() != null ? s.getStartDate().toString() : null);
                        return m;
                    }).toList();
                result.put("series", seriesList);
                
                // Extra stats for mangaka
                result.put("totalSeries", seriesList.size());
                long totalAssistants = assistants.size();
                result.put("totalAssistants", totalAssistants);
            });

        } else if ("assistant".equalsIgnoreCase(role)) {
            try {
                assistantRepository.findByUserId(user.getId()).ifPresent(ast -> {
                result.put("profileId", ast.getId());
                result.put("salaryPerTask", ast.getSalaryPerTask());
                result.put("status", ast.getStatus());
                if (ast.getMangaka() != null) {
                    Map<String, Object> mgkInfo = new HashMap<>();
                    mgkInfo.put("mangakaId", ast.getMangaka().getId());
                    if (ast.getMangaka().getUser() != null) {
                        mgkInfo.put("mangakaName", ast.getMangaka().getUser().getFullname());
                    } else {
                        mgkInfo.put("mangakaName", "Không xác định");
                    }
                    result.put("mangaka", mgkInfo);
                }
                
                // Detailed task statistics
                List<com.example.manga_management.entity.Submission> submissions = submissionRepository.findByAssistant_Id(ast.getId());
                long totalTasks = submissions.size();
                long completedTasks = submissions.stream().filter(s -> "approved".equalsIgnoreCase(s.getStatus())).count();
                long pendingTasks = submissions.stream().filter(s -> "assigned".equalsIgnoreCase(s.getStatus()) || "submitted".equalsIgnoreCase(s.getStatus())).count();
                long failedTasks = submissions.stream().filter(s -> "failed".equalsIgnoreCase(s.getStatus())).count();
                
                Map<String, Object> stats = new HashMap<>();
                stats.put("totalTasks", totalTasks);
                stats.put("completedTasks", completedTasks);
                stats.put("pendingTasks", pendingTasks);
                stats.put("failedTasks", failedTasks);
                result.put("taskStats", stats);

                // Thêm danh sách task gần đây (giới hạn hiển thị vài task trên UI)
                List<Map<String, Object>> recentTasks = submissions.stream()
                    .sorted((s1, s2) -> {
                        if (s1.getDeadline() == null && s2.getDeadline() == null) return 0;
                        if (s1.getDeadline() == null) return 1;
                        if (s2.getDeadline() == null) return -1;
                        return s2.getDeadline().compareTo(s1.getDeadline()); // Mới nhất lên đầu
                    })
                    .map(s -> {
                        Map<String, Object> taskInfo = new HashMap<>();
                        taskInfo.put("taskId", s.getId());
                        taskInfo.put("status", s.getStatus());
                        taskInfo.put("deadline", s.getDeadline() != null ? s.getDeadline().toString() : "—");
                        if (s.getPageId() != null) {
                            taskInfo.put("pageNumber", s.getPageId().getPageNumber());
                            if (s.getPageId().getChapter() != null) {
                                taskInfo.put("chapterNumber", s.getPageId().getChapter().getChapterNumber());
                                if (s.getPageId().getChapter().getSeries() != null) {
                                    taskInfo.put("seriesName", s.getPageId().getChapter().getSeries().getSeriesName());
                                } else {
                                    taskInfo.put("seriesName", "?");
                                }
                            } else {
                                taskInfo.put("chapterNumber", "?");
                            }
                        } else {
                            taskInfo.put("pageNumber", "?");
                        }
                        return taskInfo;
                    }).toList();
                result.put("recentTasks", recentTasks);
            });
            } catch (Exception e) {
                result.put("status", "error");
                result.put("message", "Lỗi backend: " + e.getMessage());
            }

        } else if ("tantou".equalsIgnoreCase(role)) {
            tantoEditorRepository.findByUserId(user.getId()).ifPresent(editor -> {
                result.put("profileId", editor.getId());
                List<Map<String, Object>> mangakaList = mangakaRepository.findByEditor_Id(editor.getId())
                    .stream().map(m -> {
                        Map<String, Object> info = new HashMap<>();
                        info.put("mangakaId", m.getId());
                        info.put("mangakaName", m.getUser().getFullname());
                        info.put("seriesCount", seriesRepository.findByProposal_Mangaka_Id(m.getId()).size());
                        return info;
                    }).toList();
                result.put("managedMangaka", mangakaList);
                
                long totalSeriesManaged = mangakaList.stream().mapToLong(m -> (long) (Integer) m.get("seriesCount")).sum();
                result.put("totalSeriesManaged", totalSeriesManaged);
                result.put("totalMangakaManaged", mangakaList.size());
            });

        } else if ("board".equalsIgnoreCase(role)) {
            boardRepository.findByUser_Id(user.getId()).ifPresent(board -> {
                result.put("profileId", board.getId());
                List<com.example.manga_management.entity.EditorialVote> votes = editorialVoteRepository.findByBoard_User_IdOrderByVoteDateDesc(user.getId());
                long totalVotes = votes.size();
                long approvedVotes = votes.stream().filter(v -> "approve".equalsIgnoreCase(v.getVote())).count();
                long rejectedVotes = votes.stream().filter(v -> "reject".equalsIgnoreCase(v.getVote())).count();
                
                Map<String, Object> stats = new HashMap<>();
                stats.put("totalVotes", totalVotes);
                stats.put("approvedVotes", approvedVotes);
                stats.put("rejectedVotes", rejectedVotes);
                result.put("voteStats", stats);
            });
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 5: Send OTP xác thực email lần đầu
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(summary = "[SWAGGER] Gửi OTP xác thực email lần đầu đăng nhập")
    @PostMapping("/verify-email")
    @ResponseBody
    public Map<String, Object> verifyEmail(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }
        if (user.isEmailVerified()) {
            result.put("status", "error");
            result.put("message", "Email đã được xác thực");
            return result;
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            result.put("status", "error");
            result.put("message", "Tài khoản chưa có email");
            return result;
        }
        try {
            String otp = otpService.generateOtp(user.getId() + "_verify");
            emailService.sendOtpEmail(user.getEmail(), otp);
            result.put("status", "success");
            result.put("message", "Đã gửi OTP tới " + user.getEmail());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Không thể gửi email: " + e.getMessage());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 6: Confirm OTP xác thực email lần đầu
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(summary = "[SWAGGER] Xác nhận OTP xác thực email lần đầu")
    @PostMapping("/confirm-email-otp")
    @ResponseBody
    public Map<String, Object> confirmEmailOtp(@RequestBody Map<String, String> body,
                                               HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }
        if (user.isEmailVerified()) {
            result.put("status", "error");
            result.put("message", "Email đã được xác thực");
            return result;
        }
        String otp = body.get("otp");
        if (otp == null || otp.isBlank()) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập mã OTP");
            return result;
        }
        boolean valid = otpService.verifyOtp(user.getId() + "_verify", otp);
        if (!valid) {
            result.put("status", "error");
            result.put("message", "OTP không đúng hoặc đã hết hạn");
            return result;
        }
        try {
            user.setEmailVerified(true);
            User savedUser = userRepository.save(user);
            session.setAttribute("user", savedUser);
            result.put("status", "success");
            result.put("message", "Xác thực email thành công");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 7: Send OTP đổi email
    @Operation(summary = "[SWAGGER] Gửi OTP về email hiện tại để xác nhận đổi email")
    @PostMapping("/send-change-email-otp")
    @ResponseBody
    public Map<String, Object> sendChangeEmailOtp(@RequestBody Map<String, String> body,
                                                  HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }
        String channel = body.getOrDefault("channel", user.isPhoneVerified() ? "phone" : "email");
        ApiResult channelError = validateOtpChannel(user, channel);
        if (channelError != null) {
            result.put("status", "error");
            result.put("message", channelError.getMessage());
            return result;
        }
        String newEmail = body.get("newEmail");
        if (newEmail == null || newEmail.isBlank()) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập email mới");
            return result;
        }
        newEmail = newEmail.trim();
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            result.put("status", "error");
            result.put("message", "Email mới không được trùng email hiện tại");
            return result;
        }
        if (userRepository.findByEmail(newEmail).isPresent()) {
            result.put("status", "error");
            result.put("message", "Email này đã được sử dụng bởi tài khoản khác");
            return result;
        }
        try {
            String otpKey = user.getId() + "_changeEmail_" + channel;
            String otp = otpService.generateOtp(otpKey);
            sendOtpByChannel(user, channel, otp);
            session.setAttribute("pendingEmail", newEmail);
            session.setAttribute("pendingEmailOtpKey", otpKey);
            result.put("status", "success");
            result.put("message", channel.equals("phone")
                    ? "Đã gửi OTP về số điện thoại đã xác thực"
                    : "Đã gửi OTP về email hiện tại của bạn");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    // Endpoint 8: Confirm OTP đổi email
    @Operation(summary = "[SWAGGER] Xác nhận OTP từ email hiện tại và lưu email mới")
    @PostMapping("/confirm-change-email-otp")
    @ResponseBody
    public Map<String, Object> confirmChangeEmailOtp(@RequestBody Map<String, String> body,
                                                     HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }
        String pendingEmail = (String) session.getAttribute("pendingEmail");
        if (pendingEmail == null || pendingEmail.isBlank()) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy email đang chờ xác thực. Vui lòng thử lại");
            return result;
        }
        String otp = body.get("otp");
        if (otp == null || otp.isBlank()) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập mã OTP");
            return result;
        }
        String otpKey = (String) session.getAttribute("pendingEmailOtpKey");
        boolean valid = otpKey != null && otpService.verifyOtp(otpKey, otp);
        if (!valid) {
            result.put("status", "error");
            result.put("message", "OTP không đúng hoặc đã hết hạn");
            return result;
        }
        try {
            user.setEmail(pendingEmail);
            user.setEmailVerified(true);
            User savedUser = userRepository.save(user);
            session.setAttribute("user", savedUser);
            session.removeAttribute("pendingEmail");
            session.removeAttribute("pendingEmailOtpKey");
            result.put("status", "success");
            result.put("message", "Đổi email thành công");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    // Endpoint 9: Send OTP đổi số điện thoại
    @Operation(summary = "[SWAGGER] Gửi OTP về email hiện tại để xác nhận đổi số điện thoại")
    @PostMapping("/send-change-phone-otp")
    @ResponseBody
    public Map<String, Object> sendChangePhoneOtp(@RequestBody Map<String, String> body,
                                                   HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }
        if (user.getEmail() == null || user.getEmail().isBlank() || !user.isEmailVerified()) {
            result.put("status", "error");
            result.put("message", "Vui lòng xác thực email trước khi đổi số điện thoại");
            return result;
        }

        String newPhone = normalizePhone(body.get("newPhone"));
        if (newPhone == null || !newPhone.matches("^\\+?[0-9]{8,15}$")) {
            result.put("status", "error");
            result.put("message", "Số điện thoại phải gồm 8 đến 15 chữ số");
            return result;
        }
        if (newPhone.equals(normalizePhone(user.getPhone()))) {
            result.put("status", "error");
            result.put("message", "Số điện thoại mới không được trùng số hiện tại");
            return result;
        }

        try {
            String otp = otpService.generateOtp(user.getId() + "_changePhone");
            emailService.sendOtpEmail(user.getEmail(), otp);
            session.setAttribute("pendingPhone", newPhone);
            result.put("status", "success");
            result.put("message", "Đã gửi OTP về email hiện tại của bạn");
        } catch (Exception e) {
            session.removeAttribute("pendingPhone");
            result.put("status", "error");
            result.put("message", "Không thể gửi email: " + e.getMessage());
        }
        return result;
    }

    // Endpoint 10: Confirm OTP đổi số điện thoại
    @Operation(summary = "[SWAGGER] Xác nhận OTP và lưu số điện thoại mới")
    @PostMapping("/confirm-change-phone-otp")
    @ResponseBody
    public Map<String, Object> confirmChangePhoneOtp(@RequestBody Map<String, String> body,
                                                      HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }
        String pendingPhone = (String) session.getAttribute("pendingPhone");
        if (pendingPhone == null || pendingPhone.isBlank()) {
            result.put("status", "error");
            result.put("message", "Không tìm thấy số điện thoại đang chờ xác thực. Vui lòng thử lại");
            return result;
        }
        String otp = body.get("otp");
        if (otp == null || !otp.matches("\\d{6}")) {
            result.put("status", "error");
            result.put("message", "Vui lòng nhập đúng mã OTP gồm 6 số");
            return result;
        }
        if (!otpService.verifyOtp(user.getId() + "_changePhone", otp)) {
            result.put("status", "error");
            result.put("message", "OTP không đúng hoặc đã hết hạn");
            return result;
        }

        try {
            user.setPhone(pendingPhone);
            user.setPhoneVerified(false);
            User savedUser = userRepository.save(user);
            session.setAttribute("user", savedUser);
            session.removeAttribute("pendingPhone");
            result.put("status", "success");
            result.put("message", "Đổi số điện thoại thành công. Hãy xác thực số mới để nhận OTP qua SMS");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/send-phone-verification-otp")
    @ResponseBody
    public Map<String, Object> sendPhoneVerificationOtp(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) return errorResult("Chưa đăng nhập");
        if (user.getPhone() == null || user.getPhone().isBlank()) return errorResult("Tài khoản chưa liên kết số điện thoại");
        if (user.isPhoneVerified()) return errorResult("Số điện thoại đã được xác thực");
        try {
            String otp = otpService.generateOtp(user.getId() + "_verifyPhone");
            smsService.sendOtp(user.getPhone(), otp);
            result.put("status", "success");
            result.put("message", "Đã gửi OTP tới số điện thoại của bạn");
        } catch (Exception exception) {
            return errorResult(exception.getMessage());
        }
        return result;
    }

    @PostMapping("/confirm-phone-verification-otp")
    @ResponseBody
    public Map<String, Object> confirmPhoneVerificationOtp(@RequestBody Map<String, String> body,
                                                            HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return errorResult("Chưa đăng nhập");
        String otp = body.get("otp");
        if (otp == null || !otp.matches("\\d{6}")) return errorResult("Vui lòng nhập đúng mã OTP gồm 6 số");
        if (!otpService.verifyOtp(user.getId() + "_verifyPhone", otp)) return errorResult("OTP không đúng hoặc đã hết hạn");
        user.setPhoneVerified(true);
        User savedUser = userRepository.save(user);
        session.setAttribute("user", savedUser);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "Xác thực số điện thoại thành công");
        return result;
    }

    // Endpoint 11: Update profile
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(summary = "[SWAGGER] Cập nhật thông tin cá nhân (nickname, socialLinks)")
    @PostMapping("/update-profile")
    @ResponseBody
    public Map<String, Object> updateProfile(@RequestBody Map<String, String> body,
                                             HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }
        String nickname = body.get("nickname");
        String socialLinks = body.get("socialLinks");
        if (nickname != null && !nickname.isBlank()) {
            user.setFullname(nickname);
        }
        if (body.containsKey("phone")
                && !java.util.Objects.equals(normalizePhone(body.get("phone")), normalizePhone(user.getPhone()))) {
            result.put("status", "error");
            result.put("message", "Vui lòng dùng chức năng đổi số điện thoại và xác thực OTP");
            return result;
        }
        if (socialLinks != null) {
            user.setSocialLinks(socialLinks);
        }
        String profile = body.get("profile");
        if (profile != null) {
            user.setProfile(profile);
        }
        try {
            User savedUser = userRepository.save(user);
            session.setAttribute("user", savedUser);
            result.put("status", "success");
            result.put("message", "Cập nhật thông tin thành công");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return result;
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        return phone.trim().replaceAll("[\\s().-]", "");
    }

    private ApiResult validateOtpChannel(User user, String channel) {
        if ("phone".equals(channel)) {
            if (user.getPhone() == null || user.getPhone().isBlank() || !user.isPhoneVerified()) {
                return new ApiResult("error", "Số điện thoại chưa được liên kết hoặc chưa xác thực");
            }
            return null;
        }
        if (!"email".equals(channel)) {
            return new ApiResult("error", "Kênh nhận OTP không hợp lệ");
        }
        if (user.getEmail() == null || user.getEmail().isBlank() || !user.isEmailVerified()) {
            return new ApiResult("error", "Email chưa được liên kết hoặc chưa xác thực");
        }
        return null;
    }

    private void sendOtpByChannel(User user, String channel, String otp) {
        if ("phone".equals(channel)) {
            smsService.sendOtp(user.getPhone(), otp);
        } else {
            emailService.sendOtpEmail(user.getEmail(), otp);
        }
    }

    private Map<String, Object> errorResult(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "error");
        result.put("message", message);
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint 10: Upload Avatar
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(summary = "[SWAGGER] Upload ảnh đại diện")
    @PostMapping(
            value = "/upload-avatar",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> uploadAvatar(@RequestPart("file") MultipartFile file,
                                             HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("status", "error");
            result.put("message", "Chưa đăng nhập");
            return result;
        }
        if (file == null || file.isEmpty()) {
            result.put("status", "error");
            result.put("message", "Chưa chọn file");
            return result;
        }
        try {
            String originalName = file.getOriginalFilename();
            String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf(".")) : ".jpg";
            if (!List.of(".jpg", ".jpeg", ".png", ".gif", ".webp").contains(ext.toLowerCase())) {
                result.put("status", "error");
                result.put("message", "Chỉ chấp nhận file ảnh (jpg, png, gif, webp)");
                return result;
            }
            String fileName = "avatar_" + user.getId() + ext;
            String uploadDir = System.getProperty("user.dir") + File.separator
                + "src" + File.separator + "main" + File.separator
                + "resources" + File.separator + "static" + File.separator + "avatars" + File.separator;
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(uploadDir);
            java.nio.file.Files.createDirectories(uploadPath);
            file.transferTo(uploadPath.resolve(fileName).toFile());
            user.setAvatar("/avatars/" + fileName);
            User savedUser = userRepository.save(user);
            session.setAttribute("user", savedUser);
            result.put("status", "success");
            result.put("avatarUrl", "/avatars/" + fileName);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Lỗi upload: " + e.getMessage());
        }
        return result;
    }
}
