package com.example.manga_management.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;

/** CSRF: request ghi phải có token khớp cookie XSRF-TOKEN; /ws-chat được miễn. */
@SpringBootTest
@WebAppConfiguration
class CsrfProtectionTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    @Test
    void loginPageIssuesReadableXsrfCookie() throws Exception {
        Cookie cookie = mockMvc.perform(get("/login")).andReturn().getResponse().getCookie("XSRF-TOKEN");

        assertNotNull(cookie);
        assertEquals(false, cookie.isHttpOnly(), "JS phải đọc được cookie để gắn header");
    }

    @Test
    void loginFormRendersHiddenCsrfInput() throws Exception {
        String html = mockMvc.perform(get("/login")).andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("name=\"_csrf\""), "form đăng nhập phải có input _csrf");
    }

    @Test
    void postWithoutTokenIsForbidden() throws Exception {
        assertEquals(403, mockMvc.perform(post("/login").param("txtUsername", "x").param("txtPassword", "y"))
                .andReturn().getResponse().getStatus());
        assertEquals(403, mockMvc.perform(post("/api/page/PG00001/finish")).andReturn().getResponse().getStatus());
        assertEquals(403, mockMvc.perform(post("/manga/mangaka/start-series")).andReturn().getResponse().getStatus());
    }

    @Test
    void postWithMismatchedTokenIsForbidden() throws Exception {
        assertEquals(403, mockMvc.perform(post("/api/page/PG00001/finish")
                .cookie(new Cookie("XSRF-TOKEN", "cookie-token"))
                .header("X-XSRF-TOKEN", "other-token")).andReturn().getResponse().getStatus());
    }

    @Test
    void postWithMatchingHeaderOrFormTokenPassesCsrfCheck() throws Exception {
        // Qua CSRF thì mới tới bước xác thực: chưa đăng nhập → 302 về /login (không phải 403).
        int viaHeader = mockMvc.perform(post("/api/page/PG00001/finish")
                .cookie(new Cookie("XSRF-TOKEN", "abc123"))
                .header("X-XSRF-TOKEN", "abc123")).andReturn().getResponse().getStatus();
        assertNotEquals(403, viaHeader);

        int viaForm = mockMvc.perform(post("/login")
                .cookie(new Cookie("XSRF-TOKEN", "abc123"))
                .param("_csrf", "abc123")
                .param("txtUsername", "no-such-user")
                .param("txtPassword", "wrong")).andReturn().getResponse().getStatus();
        assertNotEquals(403, viaForm);
    }

    @Test
    void webSocketEndpointIsExemptFromCsrfToken() throws Exception {
        assertNotEquals(403, mockMvc.perform(post("/ws-chat/000/abcdefgh/xhr_send"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void safeMethodsNeedNoToken() throws Exception {
        assertNotEquals(403, mockMvc.perform(get("/login")).andReturn().getResponse().getStatus());
    }
}
