package com.example.manga_management.controller;

import com.example.manga_management.entity.User;
import com.example.manga_management.repository.AssistantRepository;
import com.example.manga_management.repository.BoardRepository;
import com.example.manga_management.repository.EditorialVoteRepository;
import com.example.manga_management.repository.MangakaRepository;
import com.example.manga_management.repository.SeriesRepository;
import com.example.manga_management.repository.SubmissionRepository;
import com.example.manga_management.repository.TantoEditorRepository;
import com.example.manga_management.repository.UserRepository;
import com.example.manga_management.service.EmailService;
import com.example.manga_management.service.OtpService;
import com.example.manga_management.service.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountControllerOtpTests {

    private UserRepository userRepository;
    private OtpService otpService;
    private EmailService emailService;
    private SmsService smsService;
    private AccountController controller;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        otpService = mock(OtpService.class);
        emailService = mock(EmailService.class);
        smsService = mock(SmsService.class);
        controller = new AccountController(
                userRepository,
                otpService,
                emailService,
                smsService,
                mock(MangakaRepository.class),
                mock(AssistantRepository.class),
                mock(TantoEditorRepository.class),
                mock(BoardRepository.class),
                mock(SeriesRepository.class),
                mock(SubmissionRepository.class),
                mock(EditorialVoteRepository.class));
    }

    @Test
    void sendsPhoneOtpToVerifiedCurrentEmailAndNormalizesPendingPhone() {
        User user = verifiedUser();
        MockHttpSession session = sessionFor(user);
        when(otpService.generateOtp("USR001_changePhone")).thenReturn("123456");

        Map<String, Object> result = controller.sendChangePhoneOtp(
                Map.of("newPhone", "0912 345-678"), session);

        assertEquals("success", result.get("status"));
        assertEquals("0912345678", session.getAttribute("pendingPhone"));
        verify(emailService).sendOtpEmail("current@example.com", "123456");
    }

    @Test
    void confirmsPhoneOtpAndUpdatesSessionUser() {
        User user = verifiedUser();
        MockHttpSession session = sessionFor(user);
        session.setAttribute("pendingPhone", "+84912345678");
        when(otpService.verifyOtp("USR001_changePhone", "123456")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = controller.confirmChangePhoneOtp(
                Map.of("otp", "123456"), session);

        assertEquals("success", result.get("status"));
        assertEquals("+84912345678", ((User) session.getAttribute("user")).getPhone());
        assertEquals(false, ((User) session.getAttribute("user")).isPhoneVerified());
        assertNull(session.getAttribute("pendingPhone"));
    }

    @Test
    void rejectsDirectPhoneChangeThroughProfileEndpoint() {
        User user = verifiedUser();
        user.setPhone("0912345678");
        MockHttpSession session = sessionFor(user);

        Map<String, Object> result = controller.updateProfile(
                Map.of("phone", "0987654321"), session);

        assertEquals("error", result.get("status"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void sendsPasswordOtpToVerifiedPhoneWhenSelected() {
        User user = verifiedUser();
        user.setPhone("+84912345678");
        user.setPhoneVerified(true);
        MockHttpSession session = sessionFor(user);
        when(otpService.generateOtp("USR001_password_phone")).thenReturn("123456");

        var response = controller.sendOtp(Map.of("channel", "phone"), session);

        assertEquals(200, response.getStatusCode().value());
        verify(smsService).sendOtp("+84912345678", "123456");
        assertEquals("USR001_password_phone", session.getAttribute("passwordOtpKey"));
    }

    @Test
    void confirmsPhoneOwnershipAndMarksNumberVerified() {
        User user = verifiedUser();
        user.setPhone("+84912345678");
        MockHttpSession session = sessionFor(user);
        when(otpService.verifyOtp("USR001_verifyPhone", "123456")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = controller.confirmPhoneVerificationOtp(Map.of("otp", "123456"), session);

        assertEquals("success", result.get("status"));
        assertTrue(((User) session.getAttribute("user")).isPhoneVerified());
    }

    private User verifiedUser() {
        User user = new User();
        user.setId("USR001");
        user.setEmail("current@example.com");
        user.setEmailVerified(true);
        return user;
    }

    private MockHttpSession sessionFor(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", user);
        return session;
    }
}
