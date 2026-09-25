/**
 * Hộp thoại thông báo/xác nhận dùng chung và cơ chế cập nhật giao diện tức thời.
 * API: gọi lại URL được truyền vào cấu hình bằng fetch với X-Requested-With.
 */
(function () {
  const nativeAlert = window.alert ? window.alert.bind(window) : null;
  let previousFocus = null;

  function getAlertMeta(message) {
    const text = String(message || "");
    if (/(lỗi|error|thất bại|không thể)/i.test(text)) {
      return { type: "error", title: "Lỗi" };
    }
    if (/(cảnh báo|warning|vui lòng|chưa|thiếu|không tìm thấy)/i.test(text)) {
      return {
        type: "warning",
        title: "Cảnh báo",
      };
    }
    if (/(success|thành công|hoàn thành|đã lưu|đã gửi|đã cập nhật)/i.test(text)) {
      return { type: "success", title: "Thành công" };
    }
    return { type: "info", title: "Thông báo" };
  }

  function cleanMessage(message) {
    return String(message || "")
      .replace(/^[\s✅❌⚠️ℹ️]+/u, "")
      .trim();
  }

  function ensureAlertModal() {
    let overlay = document.getElementById("uiAlertOverlay");
    if (overlay) return overlay;

    overlay = document.createElement("div");
    overlay.id = "uiAlertOverlay";
    overlay.className = "ui-alert-overlay";
    overlay.setAttribute("role", "dialog");
    overlay.setAttribute("aria-modal", "true");
    overlay.setAttribute("aria-labelledby", "uiAlertTitle");
    overlay.innerHTML = `
      <div class="ui-alert-dialog" role="document">
        <div class="ui-alert-head">
          <div class="ui-alert-title-wrap">
            <h3 id="uiAlertTitle">Thông báo</h3>
          </div>
          <button type="button" class="modal-close-btn ui-alert-close" aria-label="Đóng">×</button>
        </div>
        <div class="ui-alert-body">
          <p id="uiAlertMessage"></p>
        </div>
        <div class="ui-alert-actions">
          <button type="button" class="btn-modal-confirm ui-alert-ok">OK</button>
        </div>
      </div>
    `;

    document.body.appendChild(overlay);

    const close = () => closeAlertModal();
    overlay.querySelector(".ui-alert-close").addEventListener("click", close);
    overlay.querySelector(".ui-alert-ok").addEventListener("click", close);
    overlay.addEventListener("click", (event) => {
      if (event.target === overlay) close();
    });

    return overlay;
  }

  function closeAlertModal() {
    const overlay = document.getElementById("uiAlertOverlay");
    if (!overlay) return;
    overlay.classList.remove("show");
    overlay.style.display = "none";
    document.documentElement.classList.remove("ui-alert-open");
    if (previousFocus && typeof previousFocus.focus === "function") {
      previousFocus.focus({ preventScroll: true });
    }
    previousFocus = null;
  }

  window.showUiAlert = function showUiAlert(message, options) {
    if (!document.body) {
      if (nativeAlert) nativeAlert(message);
      return;
    }

    const overlay = ensureAlertModal();
    const inferredMeta = getAlertMeta(message);
    const config = options || {};
    const meta = {
      type: config.type || inferredMeta.type,
      title: config.title || inferredMeta.title,
    };
    const dialog = overlay.querySelector(".ui-alert-dialog");
    const title = overlay.querySelector("#uiAlertTitle");
    const messageEl = overlay.querySelector("#uiAlertMessage");

    previousFocus = document.activeElement;
    dialog.className = `ui-alert-dialog ui-alert-${meta.type}`;
    title.textContent = meta.title;
    messageEl.textContent = cleanMessage(message) || meta.title;

    overlay.style.display = "flex";
    requestAnimationFrame(() => overlay.classList.add("show"));
    document.documentElement.classList.add("ui-alert-open");

    const okButton = overlay.querySelector(".ui-alert-ok");
    if (okButton) okButton.focus({ preventScroll: true });
  };

  window.alert = window.showUiAlert;

  document.addEventListener("keydown", (event) => {
    const overlay = document.getElementById("uiAlertOverlay");
    if (!overlay || !overlay.classList.contains("show")) return;
    if (event.key === "Escape" || event.key === "Enter") {
      event.preventDefault();
      closeAlertModal();
    }
  });
})();

  /* Định dạng ngày tiếng Việt dùng chung cho mọi template role. */
(function () {
  function parseDate(value) {
    if (!value) return null;
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }

  window.formatAppDate = function formatAppDate(value, fallback) {
    const parsed = parseDate(value);
    return parsed
      ? parsed.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" })
      : (fallback || "\u2014");
  };

  window.formatAppDateTime = function formatAppDateTime(value, fallback) {
    const parsed = parseDate(value);
    return parsed
      ? parsed.toLocaleString("vi-VN", {
          day: "2-digit",
          month: "2-digit",
          year: "numeric",
          hour: "2-digit",
          minute: "2-digit",
        })
      : (fallback || "\u2014");
  };

  window.formatVoteChoiceVi = function formatVoteChoiceVi(value) {
    const labels = {
      pass: "Thông qua",
      reject: "Từ chối",
      stop: "Dừng series",
      keep: "Giữ series",
      approve: "Duyệt bảo vệ",
      reward: "Đồng ý khen thưởng",
      against: "Không đồng ý",
    };
    return labels[value] || value || "\u2014";
  };
})();

/* Chuẩn hóa cửa sổ nổi theo toàn bộ khung nhìn. */
(function () {
  const modalSelector = [
    ".app-floating-modal",
    ".modal-overlay",
    ".ai-modal-overlay",
    ".ui-alert-overlay",
    ".frame-dialog-overlay",
    '[id$="ModalOverlay"]',
    '[id$="ImgZoomOverlay"]',
    "#proposalDetailModal",
    "#resubmitModal",
    "#startSeriesModal",
    "#assistantTaskDetailModal",
    "#assistantTaskImageZoom",
    "#editSubmissionModal",
  ].join(",");
  const floatingModalIds = [
    "scriptModalOverlay",
    "kanbanScriptModalOverlay",
    "chapterScriptModalOverlay",
    "addPageModalOverlay",
    "createChapterModalOverlay",
    "startSeriesModalOverlay",
    "proposalModalOverlay",
    "proposalDetailModal",
    "proposalDetailModalOverlay",
    "resubmitModal",
    "resubmitModalOverlay",
    "assignModalOverlay",
    "reassignModalOverlay",
    "notificationModalOverlay",
    "assistantTaskDetailModal",
    "assistantTaskDetailOverlay",
    "assistantTaskImageZoom",
    "assistantTaskImgZoomOverlay",
    "aiModalOverlay",
    "editSubmissionModal",
  ];
  const backgroundState = new Map();
  let previousModalFocus = null;

  function isOpenModal(modal) {
    if (!modal || modal.hidden || modal.getAttribute("aria-hidden") === "true") return false;
    const style = window.getComputedStyle(modal);
    return style.display !== "none" && style.visibility !== "hidden";
  }

  function restoreBackgroundInteraction() {
    backgroundState.forEach((state, element) => {
      element.inert = state.inert;
      if (state.ariaHidden === null) element.removeAttribute("aria-hidden");
      else element.setAttribute("aria-hidden", state.ariaHidden);
    });
    backgroundState.clear();
  }

  function syncModalInteraction() {
    if (!document.body) return;
    const modalRoots = Array.from(document.body.querySelectorAll(modalSelector));
    const openModals = modalRoots.filter(isOpenModal);
    const hasOpenModal = openModals.length > 0;
    document.documentElement.classList.toggle("app-modal-open", hasOpenModal);

    if (!hasOpenModal) {
      restoreBackgroundInteraction();
      if (previousModalFocus && typeof previousModalFocus.focus === "function") {
        previousModalFocus.focus({ preventScroll: true });
      }
      previousModalFocus = null;
      return;
    }

    if (!previousModalFocus) previousModalFocus = document.activeElement;

    const blockedElements = new Set(Array.from(document.body.children).filter((element) => (
      !element.matches(modalSelector) && !element.matches("script, style, #toast")
    )));

    backgroundState.forEach((state, element) => {
      if (blockedElements.has(element)) return;
      element.inert = state.inert;
      if (state.ariaHidden === null) element.removeAttribute("aria-hidden");
      else element.setAttribute("aria-hidden", state.ariaHidden);
      backgroundState.delete(element);
    });

    blockedElements.forEach((element) => {
      if (backgroundState.has(element)) return;
      backgroundState.set(element, {
        inert: element.inert,
        ariaHidden: element.getAttribute("aria-hidden"),
      });
      element.inert = true;
      element.setAttribute("aria-hidden", "true");
    });

    const activeModal = openModals[openModals.length - 1];
    if (!activeModal.contains(document.activeElement)) {
      const focusTarget = activeModal.querySelector(
        'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])',
      );
      if (focusTarget && typeof focusTarget.focus === "function") {
        focusTarget.focus({ preventScroll: true });
      }
    }
  }

  function liftFloatingModals() {
    if (!document.body) return;
    floatingModalIds.forEach((id) => {
      const modal = document.getElementById(id);
      if (!modal) return;
      modal.classList.add("app-floating-modal");
      if (modal.parentElement !== document.body) {
        document.body.appendChild(modal);
      }
    });

    document.querySelectorAll(modalSelector).forEach((modal) => {
      if (!modal.classList.contains("app-floating-modal")) modal.classList.add("app-floating-modal");
      if (!modal.classList.contains("app-viewport-modal")) modal.classList.add("app-viewport-modal");
      if (!modal.hasAttribute("role")) modal.setAttribute("role", "dialog");
      if (modal.getAttribute("aria-modal") !== "true") modal.setAttribute("aria-modal", "true");
      const fullViewportStyles = {
        position: "fixed",
        top: "0px",
        right: "0px",
        bottom: "0px",
        left: "0px",
        width: "100vw",
        height: "100dvh",
        zIndex: "5000",
      };
      Object.entries(fullViewportStyles).forEach(([property, value]) => {
        const cssProperty = property.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`);
        if (modal.style.getPropertyValue(cssProperty) !== value
          || modal.style.getPropertyPriority(cssProperty) !== "important") {
          modal.style.setProperty(cssProperty, value, "important");
        }
      });
      if (modal.parentElement !== document.body) document.body.appendChild(modal);
    });
    syncModalInteraction();
  }

  window.normalizeFloatingModals = liftFloatingModals;

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", liftFloatingModals);
  } else {
    liftFloatingModals();
  }

  // Khu vực thay đổi liên tục khi VẼ (danh sách lịch sử, canvas, lưới trang) nhưng
  // KHÔNG liên quan tới việc nâng modal. Bỏ qua để không chạy liftFloatingModals
  // (querySelectorAll + getComputedStyle trên mọi modal) mỗi nét vẽ → tránh lag.
  const DRAW_IGNORE_SELECTOR =
    "#historyList, #drawCanvas, .canvas-stack, .canvas-viewport, .draw-page-grid";
  let liftScheduled = false;
  const modalObserver = new MutationObserver((records) => {
    const affectsModals = records.some((record) => {
      const node = record.target;
      const el = node && node.nodeType === 1 ? node : node && node.parentElement;
      return !el || !el.closest || !el.closest(DRAW_IGNORE_SELECTOR);
    });
    if (!affectsModals || liftScheduled) return;
    liftScheduled = true;
    window.requestAnimationFrame(() => {
      liftScheduled = false;
      liftFloatingModals();
    });
  });
  modalObserver.observe(document.documentElement, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ["class", "style", "hidden", "aria-hidden"],
  });
})();


/* Cửa sổ xác nhận dùng chung của project. */
(function () {
  let confirmResolver = null;
  let previousConfirmFocus = null;

  function ensureConfirmModal() {
    let overlay = document.getElementById("uiConfirmOverlay");
    if (overlay) return overlay;

    overlay = document.createElement("div");
    overlay.id = "uiConfirmOverlay";
    overlay.className = "ui-alert-overlay ui-confirm-overlay app-floating-modal";
    overlay.setAttribute("role", "dialog");
    overlay.setAttribute("aria-modal", "true");
    overlay.setAttribute("aria-labelledby", "uiConfirmTitle");
    overlay.innerHTML = `
      <div class="ui-alert-dialog ui-confirm-dialog" role="document">
        <div class="ui-alert-head">
          <div class="ui-alert-title-wrap">
            <h3 id="uiConfirmTitle">X\u00e1c nh\u1eadn</h3>
          </div>
          <button type="button" class="modal-close-btn ui-confirm-close" aria-label="\u0110\u00f3ng">&times;</button>
        </div>
        <div class="ui-alert-body">
          <p id="uiConfirmMessage"></p>
        </div>
        <div class="ui-alert-actions ui-confirm-actions">
          <button type="button" class="btn-modal-cancel ui-confirm-cancel">H\u1ee7y</button>
          <button type="button" class="btn-modal-confirm ui-confirm-ok">X\u00e1c nh\u1eadn</button>
        </div>
      </div>
    `;

    document.body.appendChild(overlay);
    overlay.querySelector(".ui-confirm-close").addEventListener("click", () => closeConfirmModal(false));
    overlay.querySelector(".ui-confirm-cancel").addEventListener("click", () => closeConfirmModal(false));
    overlay.querySelector(".ui-confirm-ok").addEventListener("click", () => closeConfirmModal(true));
    overlay.addEventListener("click", (event) => {
      if (event.target === overlay) closeConfirmModal(false);
    });

    return overlay;
  }

  function closeConfirmModal(result) {
    const overlay = document.getElementById("uiConfirmOverlay");
    if (!overlay || !overlay.classList.contains("show")) return;

    overlay.classList.remove("show");
    overlay.style.display = "none";
    document.documentElement.classList.remove("ui-alert-open");

    if (previousConfirmFocus && typeof previousConfirmFocus.focus === "function") {
      previousConfirmFocus.focus({ preventScroll: true });
    }
    previousConfirmFocus = null;

    if (confirmResolver) {
      const resolve = confirmResolver;
      confirmResolver = null;
      resolve(Boolean(result));
    }
  }

  window.showUiConfirm = function showUiConfirm(message, options) {
    if (!document.body) return Promise.resolve(false);

    const overlay = ensureConfirmModal();
    const title = overlay.querySelector("#uiConfirmTitle");
    const messageEl = overlay.querySelector("#uiConfirmMessage");
    const okButton = overlay.querySelector(".ui-confirm-ok");
    const cancelButton = overlay.querySelector(".ui-confirm-cancel");
    const config = options || {};

    if (confirmResolver) closeConfirmModal(false);

    previousConfirmFocus = document.activeElement;
    title.textContent = config.title || "X\u00e1c nh\u1eadn";
    messageEl.textContent = String(message || "");
    okButton.textContent = config.okText || "X\u00e1c nh\u1eadn";
    cancelButton.textContent = config.cancelText || "H\u1ee7y";
    const isDanger = config.danger === true
      || /(xóa|từ chối|dừng|thu hồi)/i.test(okButton.textContent);
    const isWarning = config.warning === true
      || /(cảnh báo|không thể hoàn tác|top 5|khóa chỉnh sửa)/i.test(String(message || ""));
    const dialog = overlay.querySelector(".ui-confirm-dialog");
    dialog.classList.toggle("ui-confirm-danger", isDanger);
    dialog.classList.toggle("ui-confirm-warning", !isDanger && isWarning);

    overlay.style.display = "flex";
    requestAnimationFrame(() => overlay.classList.add("show"));
    document.documentElement.classList.add("ui-alert-open");
    okButton.focus({ preventScroll: true });

    return new Promise((resolve) => {
      confirmResolver = resolve;
    });
  };

  window.appConfirm = window.showUiConfirm;

/** Cập nhật vùng HTML do máy chủ render mà không tải lại toàn bộ trang. */
  window.refreshUiRegions = async function refreshUiRegions(selectors, options) {
    const config = options || {};
    const selectorList = Array.isArray(selectors) ? selectors : [selectors];
    const validSelectors = selectorList.filter(Boolean);
    if (validSelectors.length === 0) return false;

    const response = await fetch(config.url || window.location.href, {
      method: "GET",
      credentials: "same-origin",
      cache: "no-store",
      headers: { "X-Requested-With": "XMLHttpRequest" },
    });
    if (!response.ok) {
      throw new Error(`Không thể cập nhật giao diện (${response.status})`);
    }

    const html = await response.text();
    const freshDocument = new DOMParser().parseFromString(html, "text/html");
    let updatedCount = 0;

    validSelectors.forEach((selector) => {
      const current = document.querySelector(selector);
      const fresh = freshDocument.querySelector(selector);
      if (!current || !fresh) return;

      const scrollTop = current.scrollTop;
      const scrollLeft = current.scrollLeft;
      current.replaceChildren(...Array.from(fresh.childNodes, (node) => node.cloneNode(true)));
      if (config.preserveScroll !== false) {
        current.scrollTop = scrollTop;
        current.scrollLeft = scrollLeft;
      }
      updatedCount += 1;
    });

    window.dispatchEvent(new CustomEvent("app:data-updated", {
      detail: { selectors: validSelectors, updatedCount },
    }));
    return updatedCount > 0;
  };

/*
 * Kênh cập nhật tức thời dùng chung cho mọi role. Sau khi máy chủ chấp nhận
 * thao tác thay đổi dữ liệu, từng workspace chỉ tải lại vùng bị ảnh hưởng.
 */
  const liveRefreshTimers = new Map();

  window.scheduleLiveRefresh = function scheduleLiveRefresh(key, callback, delay) {
    if (typeof callback !== "function") return;
    const refreshKey = key || "default";
    const wait = Number.isFinite(delay) ? delay : 120;
    const currentTimer = liveRefreshTimers.get(refreshKey);
    if (currentTimer) window.clearTimeout(currentTimer);

    liveRefreshTimers.set(refreshKey, window.setTimeout(async () => {
      liveRefreshTimers.delete(refreshKey);
      try {
        await callback();
      } catch (error) {
        console.warn("Không thể đồng bộ dữ liệu mới lên giao diện:", error);
      }
    }, wait));
  };

  function callGlobal(name, ...args) {
    const callback = window[name];
    if (typeof callback !== "function") return undefined;
    return callback(...args);
  }

  function scheduleGlobal(key, name, ...args) {
    if (typeof window[name] !== "function") return;
    window.scheduleLiveRefresh(key, () => callGlobal(name, ...args));
  }

  function mutationPath(rawUrl) {
    try {
      return new URL(rawUrl || window.location.href, window.location.origin).pathname;
    } catch (_error) {
      return String(rawUrl || "").split("?")[0];
    }
  }

  window.withFreshAssetUrl = function withFreshAssetUrl(rawUrl) {
    if (!rawUrl || String(rawUrl).startsWith("data:") || String(rawUrl).startsWith("blob:")) {
      return rawUrl;
    }
    try {
      const url = new URL(rawUrl, window.location.origin);
      url.searchParams.set("v", Date.now().toString());
      return url.origin === window.location.origin
        ? `${url.pathname}${url.search}${url.hash}`
        : url.href;
    } catch (_error) {
      const separator = String(rawUrl).includes("?") ? "&" : "?";
      return `${rawUrl}${separator}v=${Date.now()}`;
    }
  };

  const pendingAssetPreviews = new Map();

  function settleAssetPreview(inputId, finalUrl) {
    const pending = pendingAssetPreviews.get(inputId);
    if (!pending) return;
    if (pending.objectUrl) URL.revokeObjectURL(pending.objectUrl);
    if (!finalUrl && pending.image && pending.previousSrc) {
      pending.image.src = pending.previousSrc;
    }
    pendingAssetPreviews.delete(inputId);
  }

  document.addEventListener("change", (event) => {
    const input = event.target;
    if (!(input instanceof HTMLInputElement) || input.type !== "file") return;
    const file = input.files && input.files[0];
    if (!file || !file.type.startsWith("image/")) return;

    const targetSelector = input.dataset.previewTarget
      || (input.id === "avatarFileInput" ? "#profileAvatarImg" : "");
    if (!targetSelector) return;
    const image = document.querySelector(targetSelector);
    if (!(image instanceof HTMLImageElement)) return;

    settleAssetPreview(input.id);
    const objectUrl = URL.createObjectURL(file);
    pendingAssetPreviews.set(input.id, {
      image,
      objectUrl,
      previousSrc: image.currentSrc || image.src,
    });
    image.src = objectUrl;
  });

  function showFreshAvatar(data) {
    const avatarUrl = data && (data.avatarUrl || data.avatar);
    if (!avatarUrl) return;
    settleAssetPreview("avatarFileInput", avatarUrl);
    const freshUrl = window.withFreshAssetUrl(avatarUrl);
    document.querySelectorAll("#profileAvatarImg, [data-current-user-avatar]").forEach((image) => {
      image.src = freshUrl;
    });
    const input = document.getElementById("avatarFileInput");
    if (input) input.value = "";
  }

  function refreshRoleAfterMutation(event) {
    const path = mutationPath(event.detail && event.detail.url);
    const pagePath = window.location.pathname;

    if (/^\/api\/account\/(update-profile|upload-avatar|change-password|confirm-change-email-otp|confirm-change-phone-otp)$/.test(path)) {
      if (path.endsWith("/upload-avatar")) {
        showFreshAvatar(event.detail && event.detail.data);
      }
      scheduleGlobal("account-profile", "loadProfile");
    }

    if (pagePath.startsWith("/manga/system-admin")) {
      if (/^\/manga\/system-admin\/chapters\/[^/]+\/publish$/.test(path)) {
        scheduleGlobal("admin-publish", "loadPendingPublishChapters");
      }
      if (/^\/manga\/system-admin\/(accounts\/|assign\/|import-excel)/.test(path)) {
        scheduleGlobal("admin-data", "loadData");
      }
      if (path.includes("/ranking") || path.includes("/vote")) {
        scheduleGlobal("admin-ranking", "loadVoteRanking");
        scheduleGlobal("admin-vote-sessions", "loadVoteSessionsAdmin");
      }
      return;
    }

    if (pagePath.startsWith("/manga/tantou")) {
      if (/^\/manga\/tantou\/(review|submit-to-board)$/.test(path)) {
        scheduleGlobal("tantou-proposals", "loadProposals");
        scheduleGlobal("tantou-approved-proposals", "loadApprovedProposals");
      }
      if (/^\/api\/chapter-review\//.test(path)) {
        scheduleGlobal("tantou-chapter-reviews", "loadChapterReviews");
      }
      if (/^\/manga\/tantou\/(series\/[^/]+\/submit-defense|chapters\/[^/]+\/deadline)$/.test(path)) {
        scheduleGlobal("tantou-cancel-series", "loadPendingCancelSeries");
        scheduleGlobal("tantou-chapter-reviews", "loadChapterReviews");
      }
      return;
    }

    if (pagePath.startsWith("/manga/editor")) {
      if (path.includes("/vote") || path.includes("/ranking")) {
        scheduleGlobal("editor-vote-proposals", "loadVoteProposals");
        scheduleGlobal("editor-ranking", "loadAll");
        scheduleGlobal("editor-vote-sessions", "loadActiveSessions");
      }
      return;
    }

    if (pagePath.startsWith("/manga/assistant")) {
      if (/^\/manga\/assistant\/submission\//.test(path)) {
        scheduleGlobal("assistant-tasks", "refreshAssistantTasks", "waiting");
      }
      return;
    }

    if (!pagePath.startsWith("/manga/mangaka")) return;

    if (/^\/manga\/mangaka\/(submit-proposal|resubmit-proposal|start-series)$/.test(path)) {
      scheduleGlobal("mangaka-proposals", "refreshMangakaProposalLists");
      if (path.endsWith("/start-series")) {
        scheduleGlobal("mangaka-series", "loadMySeriesList");
      }
      return;
    }

      // Ảnh bìa có cơ chế xem trước riêng nên không dựng lại bảng series khi cửa sổ xác nhận đang đóng.
    if (path.endsWith("/edit-jacket")) return;

      // Form tạo chapter tự cập nhật series hiện tại. Nếu bộ xử lý chung chạy
      // đồng thời, giao diện có thể quay nhầm về danh sách series bên ngoài.
    if (/^\/manga\/mangaka\/myseries\/[^/]+\/createchapter\/data$/.test(path)) return;

    const affectsChapter = /^\/api\/(page\/|chapter-review\/|chapters\/)/.test(path)
      || /^\/manga\/mangaka\/(myseries\/|submission\/)/.test(path);
    if (!affectsChapter || path.endsWith("/savefile")) return;

    window.scheduleLiveRefresh("mangaka-chapter", () => {
      if (window._inlineChapterMode && typeof window.refreshActiveChapterPages === "function") {
        return window.refreshActiveChapterPages();
      }
      if (window._currentSid && typeof window.loadSeriesView === "function") {
        return window.loadSeriesView(window._currentSid, window._currentCid);
      }
      return callGlobal("loadMySeriesList");
    });
  }

  window.addEventListener("app:mutation-success", refreshRoleAfterMutation);
  window.addEventListener("app:mutation-failure", (event) => {
    if (mutationPath(event.detail && event.detail.url) === "/api/account/upload-avatar") {
      settleAssetPreview("avatarFileInput");
    }
  });

  if (!window.__liveMutationFetchInstalled && typeof window.fetch === "function") {
    window.__liveMutationFetchInstalled = true;
    const nativeFetch = window.fetch.bind(window);

    window.fetch = async function liveUpdatingFetch(input, init) {
      const method = String(
        (init && init.method) || (input && input.method) || "GET"
      ).toUpperCase();
      let response;
      try {
        response = await nativeFetch(input, init);
      } catch (error) {
        if (method !== "GET" && method !== "HEAD") {
          const rawUrl = typeof input === "string" ? input : (input && input.url) || "";
          window.dispatchEvent(new CustomEvent("app:mutation-failure", {
            detail: { url: rawUrl, method, error },
          }));
        }
        throw error;
      }

      if (method !== "GET" && method !== "HEAD") {
        const responseCopy = response.clone();
        const rawUrl = typeof input === "string" ? input : (input && input.url) || "";

        Promise.resolve().then(async () => {
          let data = null;
          const contentType = responseCopy.headers.get("content-type") || "";
          if (contentType.includes("application/json")) {
            try {
              data = await responseCopy.json();
            } catch (_error) {
              data = null;
            }
          }

          const explicitlyFailed = data && (
            data.success === false
            || data.status === "error"
            || data.status === "failed"
          );
          if (!response.ok || explicitlyFailed) {
            window.dispatchEvent(new CustomEvent("app:mutation-failure", {
              detail: { url: rawUrl, method, data, status: response.status },
            }));
            return;
          }

          window.dispatchEvent(new CustomEvent("app:mutation-success", {
            detail: { url: rawUrl, method, data },
          }));
        });
      }

      return response;
    };
  }

  window.showUiAlertAsync = function showUiAlertAsync(message) {
    if (!window.showUiAlert) return Promise.resolve();
    window.showUiAlert(message);

    return new Promise((resolve) => {
      const overlay = document.getElementById("uiAlertOverlay");
      if (!overlay) {
        resolve();
        return;
      }

      const cleanup = () => {
        overlay.querySelector(".ui-alert-ok")?.removeEventListener("click", cleanup);
        overlay.querySelector(".ui-alert-close")?.removeEventListener("click", cleanup);
        overlay.removeEventListener("click", onOverlayClick);
        document.removeEventListener("keydown", onKeyDown);
        resolve();
      };
      const onKeyDown = (event) => {
        if (event.key === "Escape" || event.key === "Enter") cleanup();
      };
      const onOverlayClick = (event) => {
        if (event.target === overlay) cleanup();
      };

      overlay.querySelector(".ui-alert-ok")?.addEventListener("click", cleanup, { once: true });
      overlay.querySelector(".ui-alert-close")?.addEventListener("click", cleanup, { once: true });
      overlay.addEventListener("click", onOverlayClick);
      document.addEventListener("keydown", onKeyDown);
    });
  };

  document.addEventListener("keydown", (event) => {
    const overlay = document.getElementById("uiConfirmOverlay");
    if (!overlay || !overlay.classList.contains("show")) return;
    if (event.key === "Escape") {
      event.preventDefault();
      closeConfirmModal(false);
    }
    if (event.key === "Enter") {
      event.preventDefault();
      closeConfirmModal(true);
    }
  });
})();
