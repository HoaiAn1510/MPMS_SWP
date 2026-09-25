/**
 * Tự gắn CSRF token vào mọi request ghi (POST/PUT/PATCH/DELETE) cùng origin.
 * Spring Security đặt token trong cookie XSRF-TOKEN (không HttpOnly); file này
 * đọc cookie đó và gửi lại ở header X-XSRF-TOKEN cho fetch, XMLHttpRequest, và
 * thêm input ẩn _csrf cho form POST truyền thống.
 * Phải nạp ở <head>, TRƯỚC mọi script khác, để các wrapper fetch khác (vd
 * ui-alert.js) bọc lên trên bản đã gắn token.
 */
(function () {
  const COOKIE_NAME = "XSRF-TOKEN";
  const HEADER_NAME = "X-XSRF-TOKEN";
  const SAFE_METHODS = ["GET", "HEAD", "OPTIONS", "TRACE"];

  function getToken() {
    const prefix = COOKIE_NAME + "=";
    const parts = document.cookie ? document.cookie.split("; ") : [];
    for (const part of parts) {
      if (part.startsWith(prefix)) {
        return decodeURIComponent(part.slice(prefix.length));
      }
    }
    return "";
  }

  function isSameOrigin(url) {
    try {
      return new URL(url, window.location.href).origin === window.location.origin;
    } catch (e) {
      return false;
    }
  }

  function needsToken(method, url) {
    return !SAFE_METHODS.includes(String(method || "GET").toUpperCase()) && isSameOrigin(url);
  }

  window.getCsrfToken = getToken;

  // ── fetch ────────────────────────────────────────────────────────────
  if (typeof window.fetch === "function") {
    const nativeFetch = window.fetch.bind(window);
    window.fetch = function csrfFetch(input, init) {
      const request = typeof Request !== "undefined" && input instanceof Request ? input : null;
      const method = (init && init.method) || (request && request.method) || "GET";
      const url = request ? request.url : String(input);
      const token = needsToken(method, url) ? getToken() : "";
      if (token) {
        const headers = new Headers((init && init.headers) || (request && request.headers) || undefined);
        if (!headers.has(HEADER_NAME)) {
          headers.set(HEADER_NAME, token);
        }
        if (request && !(init && init.headers)) {
          input = new Request(request, { headers });
        } else {
          init = Object.assign({}, init, { headers });
        }
      }
      return nativeFetch(input, init);
    };
  }

  // ── XMLHttpRequest ───────────────────────────────────────────────────
  if (typeof XMLHttpRequest !== "undefined") {
    const nativeOpen = XMLHttpRequest.prototype.open;
    const nativeSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (method, url) {
      this.__csrfMethod = method;
      this.__csrfUrl = url;
      return nativeOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function () {
      if (needsToken(this.__csrfMethod, this.__csrfUrl)) {
        const token = getToken();
        if (token) {
          try {
            this.setRequestHeader(HEADER_NAME, token);
          } catch (e) {
            // Header đã được set hoặc request chưa mở — bỏ qua.
          }
        }
      }
      return nativeSend.apply(this, arguments);
    };
  }

  // ── form POST truyền thống ───────────────────────────────────────────
  document.addEventListener(
    "submit",
    function (event) {
      const form = event.target;
      if (!form || form.tagName !== "FORM") return;
      const method = (form.getAttribute("method") || "GET").toUpperCase();
      if (SAFE_METHODS.includes(method) || !isSameOrigin(form.action || window.location.href)) return;
      const token = getToken();
      if (!token || form.querySelector('input[name="_csrf"]')) return;
      const input = document.createElement("input");
      input.type = "hidden";
      input.name = "_csrf";
      input.value = token;
      form.appendChild(input);
    },
    true,
  );

  // ── Đăng xuất: link /login/logout được chuyển thành POST (kèm CSRF token) ──
  document.addEventListener(
    "click",
    function (event) {
      const link = event.target && event.target.closest ? event.target.closest("a[href]") : null;
      if (!link || !/\/login\/logout\/?$/.test(new URL(link.href, window.location.href).pathname)) return;
      event.preventDefault();
      window
        .fetch("/login/logout", { method: "POST" })
        .catch(function () {})
        .finally(function () {
          window.location.href = "/login";
        });
    },
    true,
  );
})();
