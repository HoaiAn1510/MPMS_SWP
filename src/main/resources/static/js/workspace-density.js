/**
 * Chuẩn hóa workspace giữa các role và ghép các tab ít chức năng.
 * API ranking: GET /manga/ranking?month={month}&quarter={quarter}&year={year}.
 */
(function () {
  "use strict";

  function navItemFor(tabId) {
    const escaped = window.CSS?.escape ? CSS.escape(tabId) : tabId;
    const button = document.querySelector(
      `.topnav [onclick*="${escaped}"], .topnav [data-target="${escaped}"]`,
    );
    return button?.closest("li") || null;
  }

  function removeNavigation(tabId) {
    navItemFor(tabId)?.remove();
  }

  function stripRedundantHeading(container) {
    const firstHeading = container.querySelector(":scope > h1, :scope > h2");
    firstHeading?.remove();
    const firstRule = container.querySelector(":scope > hr");
    firstRule?.remove();
  }

  function mergeAsDetails(sourceId, targetId, options) {
    const source = document.getElementById(sourceId);
    const target = document.getElementById(targetId);
    if (!source || !target || source.dataset.workspaceMerged === "true") return null;

    source.dataset.workspaceMerged = "true";
    source.classList.remove("tab-pane", "active");
    stripRedundantHeading(source);

    const details = document.createElement("details");
    details.className = "workspace-merged-details";
    details.id = `${sourceId}-merged`;
    details.open = Boolean(options.open);
    details.innerHTML = `
      <summary>
        <span>
          <strong>${options.title}</strong>
          <small>${options.description}</small>
        </span>
        <span class="workspace-merged-state" aria-hidden="true"></span>
      </summary>
      <div class="workspace-merged-body"></div>`;
    details.querySelector(".workspace-merged-body").append(...source.childNodes);
    source.replaceWith(details);
    target.appendChild(details);
    removeNavigation(sourceId);
    return details;
  }

  function mergeAsSection(sourceId, targetId, options) {
    const source = document.getElementById(sourceId);
    const target = document.getElementById(targetId);
    if (!source || !target || source.dataset.workspaceMerged === "true") return null;

    const wasActive = source.classList.contains("active");
    source.dataset.workspaceMerged = "true";
    source.classList.remove("tab-pane", "active");
    stripRedundantHeading(source);

    const section = document.createElement("section");
    section.className = "workspace-merged-section";
    section.id = `${sourceId}-merged`;
    section.innerHTML = `
      <header class="workspace-merged-header">
        <div>
          <h2>${options.title}</h2>
          <p>${options.description}</p>
        </div>
      </header>
      <div class="workspace-merged-body"></div>`;
    section.querySelector(".workspace-merged-body").append(...source.childNodes);
    source.replaceWith(section);
    target.appendChild(section);
    removeNavigation(sourceId);
    if (wasActive) target.classList.add("active");
    return section;
  }

  function addQuickActions(targetId, actions) {
    const target = document.getElementById(targetId);
    if (!target || target.querySelector(":scope > .workspace-quick-actions")) return;

    const bar = document.createElement("nav");
    bar.className = "workspace-quick-actions";
    bar.setAttribute("aria-label", "Hành động nhanh");
    actions.forEach((action) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = action.primary
        ? "workspace-quick-action is-primary"
        : "workspace-quick-action";
      button.textContent = action.label;
      button.addEventListener("click", action.run);
      bar.appendChild(button);
    });

    const heading = target.querySelector(":scope > h1, :scope > h2, :scope > section > header");
    if (heading?.parentElement === target) heading.insertAdjacentElement("afterend", bar);
    else target.prepend(bar);
  }

  function ensureSharedRankingModal() {
    let overlay = document.getElementById("sharedRankingModalOverlay");
    if (overlay) return overlay;

    overlay = document.createElement("div");
    overlay.id = "sharedRankingModalOverlay";
    overlay.className = "modal-overlay ranking-modal-overlay shared-ranking-modal";
    overlay.hidden = true;
    overlay.innerHTML = `
      <div class="modal-content ranking-modal-content" role="dialog" aria-modal="true" aria-labelledby="sharedRankingModalTitle">
        <div class="modal-header ranking-modal-header">
          <div>
            <h5 id="sharedRankingModalTitle">Ranking series</h5>
            <p>So sánh hiệu suất series theo tháng, quý hoặc cả năm.</p>
          </div>
          <button type="button" class="modal-close-btn" data-ranking-close aria-label="Đóng bảng ranking">&times;</button>
        </div>
        <div class="modal-body ranking-modal-body">
          <div class="ranking-filter-bar" aria-label="Bộ lọc ranking">
            <div class="ranking-filter-field">
              <label for="sharedRankMonth">Tháng</label>
              <select id="sharedRankMonth">
                <option value="0">-- Cả năm --</option>
                ${Array.from({ length: 12 }, (_, index) => `<option value="${index + 1}">Tháng ${index + 1}</option>`).join("")}
              </select>
            </div>
            <div class="ranking-filter-field">
              <label for="sharedRankYear">Năm</label>
              <select id="sharedRankYear"></select>
            </div>
            <div class="ranking-filter-field">
              <label for="sharedRankQuarter">Quý</label>
              <select id="sharedRankQuarter">
                <option value="0">-- Tất cả --</option>
                <option value="1">Quý 1 (T1-T3)</option>
                <option value="2">Quý 2 (T4-T6)</option>
                <option value="3">Quý 3 (T7-T9)</option>
                <option value="4">Quý 4 (T10-T12)</option>
              </select>
            </div>
            <div class="ranking-filter-actions">
              <button type="button" class="ranking-filter-button" data-ranking-load>Xem ranking</button>
              <button type="button" class="ranking-filter-button" data-ranking-collapse>Thu lại</button>
            </div>
          </div>
          <div class="ranking-results" data-ranking-results>
            <div class="ranking-table-scroll">
              <table class="data-table">
                <thead><tr><th>Hạng</th><th>Mã Series</th><th>Tên Series</th><th>Lượt xem</th><th>Like</th><th>Dislike</th></tr></thead>
                <tbody data-ranking-body></tbody>
              </table>
              <p class="empty-msg" data-ranking-message>Đang tải ranking...</p>
            </div>
          </div>
        </div>
      </div>`;
    document.body.appendChild(overlay);

    const month = overlay.querySelector("#sharedRankMonth");
    const quarter = overlay.querySelector("#sharedRankQuarter");
    const year = overlay.querySelector("#sharedRankYear");
    const currentYear = new Date().getFullYear();
    for (let value = currentYear; value >= currentYear - 4; value -= 1) {
      year.add(new Option(String(value), String(value)));
    }
    month.addEventListener("change", () => { if (month.value !== "0") quarter.value = "0"; });
    quarter.addEventListener("change", () => { if (quarter.value !== "0") month.value = "0"; });
    overlay.addEventListener("click", (event) => {
      if (event.target === overlay || event.target.closest("[data-ranking-close]")) closeSharedRankingModal();
    });
    overlay.querySelector("[data-ranking-load]").addEventListener("click", loadSharedRanking);
    overlay.querySelector("[data-ranking-collapse]").addEventListener("click", () => {
      overlay.querySelector("[data-ranking-results]").hidden = true;
    });
    return overlay;
  }

  function rankingCell(value) {
    const cell = document.createElement("td");
    cell.textContent = value == null ? "—" : String(value);
    return cell;
  }

  async function loadSharedRanking() {
    const overlay = ensureSharedRankingModal();
    const results = overlay.querySelector("[data-ranking-results]");
    const tbody = overlay.querySelector("[data-ranking-body]");
    const message = overlay.querySelector("[data-ranking-message]");
    const table = overlay.querySelector("table");
    const loadButton = overlay.querySelector("[data-ranking-load]");
    results.hidden = false;
    tbody.replaceChildren();
    table.hidden = true;
    message.hidden = false;
    message.textContent = "Đang tải ranking...";
    loadButton.disabled = true;
    const query = new URLSearchParams({
      month: overlay.querySelector("#sharedRankMonth").value,
      quarter: overlay.querySelector("#sharedRankQuarter").value,
      year: overlay.querySelector("#sharedRankYear").value,
    });
    try {
      const response = await fetch(`/manga/ranking?${query}`);
      if (!response.ok) throw new Error("Không thể tải ranking");
      const rows = await response.json();
      if (!rows.length) {
        message.textContent = "Không có dữ liệu trong khoảng thời gian này.";
        return;
      }
      const medals = ["🥇", "🥈", "🥉"];
      rows.forEach((item) => {
        const row = document.createElement("tr");
        row.append(
          rankingCell(`${medals[item.rank - 1] || ""}${medals[item.rank - 1] ? " " : ""}${item.rank}`),
          rankingCell(item.seriesId), rankingCell(item.seriesName), rankingCell(item.totalView),
          rankingCell(item.totalLike), rankingCell(item.totalDislike),
        );
        tbody.appendChild(row);
      });
      table.hidden = false;
      message.hidden = true;
    } catch (error) {
      message.textContent = "Không thể tải ranking. Vui lòng thử lại.";
    } finally {
      loadButton.disabled = false;
    }
  }

  function openSharedRankingModal() {
    const overlay = ensureSharedRankingModal();
    overlay.hidden = false;
    overlay.classList.add("show");
    document.documentElement.classList.add("app-modal-open");
    loadSharedRanking();
    requestAnimationFrame(() => overlay.querySelector("#sharedRankMonth")?.focus());
  }

  function closeSharedRankingModal() {
    const overlay = document.getElementById("sharedRankingModalOverlay");
    if (!overlay) return;
    overlay.classList.remove("show");
    overlay.hidden = true;
    document.documentElement.classList.remove("app-modal-open");
  }

  window.openSharedRankingModal = openSharedRankingModal;

  let workflowReturnFocus = null;

  function ensureSharedWorkflowModal({ id, title, description, content }) {
    let overlay = document.getElementById(id);
    if (overlay) return overlay;

    overlay = document.createElement("div");
    overlay.id = id;
    overlay.className = "modal-overlay ranking-modal-overlay shared-workflow-modal";
    overlay.hidden = true;
    overlay.innerHTML = `
      <div class="modal-content ranking-modal-content workflow-modal-content" role="dialog" aria-modal="true" aria-labelledby="${id}Title">
        <div class="modal-header ranking-modal-header">
          <div>
            <h5 id="${id}Title">${title}</h5>
            <p>${description}</p>
          </div>
          <button type="button" class="modal-close-btn" data-workflow-close aria-label="Đóng ${title}">&times;</button>
        </div>
        <div class="modal-body ranking-modal-body workflow-modal-body"></div>
      </div>`;
    overlay.querySelector(".workflow-modal-body").appendChild(content);
    overlay.addEventListener("click", (event) => {
      if (event.target === overlay || event.target.closest("[data-workflow-close]")) {
        closeSharedWorkflowModal(overlay);
      }
    });
    document.body.appendChild(overlay);
    return overlay;
  }

  function openSharedWorkflowModal(id, trigger) {
    const overlay = document.getElementById(id);
    if (!overlay) return;
    workflowReturnFocus = trigger || document.activeElement;
    overlay.hidden = false;
    overlay.classList.add("show");
    document.documentElement.classList.add("app-modal-open");
    requestAnimationFrame(() => overlay.querySelector("[data-workflow-close]")?.focus());
  }

  function closeSharedWorkflowModal(overlay) {
    const target = typeof overlay === "string" ? document.getElementById(overlay) : overlay;
    if (!target || target.hidden) return;
    target.classList.remove("show");
    target.hidden = true;
    document.documentElement.classList.remove("app-modal-open");
    workflowReturnFocus?.focus?.();
    workflowReturnFocus = null;
  }

  function removeContentTabIcons() {
    document
      .querySelectorAll(
        ".workspace-quick-actions i, .shared-workflow-modal i, [role='tablist'] > button i, .proposal-tabs-nav .proposal-tab-btn i, .approved-proposal-tabs .approved-proposal-tab i",
      )
      .forEach((icon) => icon.remove());
  }

  function removeDeadPlaceholders() {
    document.querySelectorAll("#tab-assistant, #tab-other").forEach((tab) => {
      if (!/tính năng đang phát triển/i.test(tab.textContent || "")) return;
      removeNavigation(tab.id);
      tab.remove();
    });
  }

  function initAdmin() {
    const voteTab = document.getElementById("tab-vote");
    const importTab = document.getElementById("tab-import");
    const publishTab = document.getElementById("tab-publish");
    if (!voteTab || !importTab || !publishTab) return;

    const voteHeading = voteTab.querySelector(":scope > h2");
    if (voteHeading) voteHeading.textContent = "Bình chọn";

    const cards = [...voteTab.querySelectorAll(":scope > .section-card")];
    const rankingCard = cards[0];
    const sessionsCard = cards[1];
    rankingCard?.classList.add("admin-vote-ranking-panel");
    sessionsCard?.classList.add("admin-vote-sessions-panel");

    const prepareTabContent = (tab, className) => {
      const wasActive = tab.classList.contains("active");
      tab.classList.remove("tab-pane", "active");
      stripRedundantHeading(tab);
      tab.classList.add("admin-workflow-content", className);
      removeNavigation(tab.id);
      return wasActive;
    };

    const importWasActive = prepareTabContent(importTab, "admin-import-content");
    ensureSharedWorkflowModal({
      id: "adminExcelImportModal",
      title: "Nhập dữ liệu Excel",
      description: "Cập nhật lượt xem chapter và phiếu Like/Dislike từ tệp Excel.",
      content: importTab,
    });

    const publishWasActive = prepareTabContent(publishTab, "admin-publish-content");
    ensureSharedWorkflowModal({
      id: "adminPendingPublishModal",
      title: "Chapter chờ xuất bản",
      description: "Kiểm tra các chapter đã được Tantou duyệt và hoàn tất xuất bản.",
      content: publishTab,
    });

    if (importWasActive || publishWasActive) voteTab.classList.add("active");

    addQuickActions("tab-vote", [
      {
        label: "Nhập dữ liệu Excel",
        primary: true,
        run: (event) => openSharedWorkflowModal("adminExcelImportModal", event.currentTarget),
      },
      {
        label: "Chapter chờ xuất bản",
        run: (event) => {
          window.loadPendingPublishChapters?.();
          window.loadPublishedSeries?.();
          openSharedWorkflowModal("adminPendingPublishModal", event.currentTarget);
        },
      },
    ]);

    // Tab Phân công giữ hai bảng ngay trong content; admin.html quản lý việc chuyển bảng.
  }

  // Bảng ranking hiển thị THẲNG trong trang (inline) cho hội đồng — không dùng
  // modal, để các popup con (vd "Xem thông tin series") không bị chồng lớp lên nhau.
  function buildInlineRankingPanel() {
    const panel = document.createElement("section");
    panel.innerHTML = `
      <div class="ranking-filter-bar" aria-label="Bộ lọc ranking">
        <div class="ranking-filter-field">
          <label>Tháng</label>
          <select data-erank-month>
            <option value="0">-- Cả năm --</option>
            ${Array.from({ length: 12 }, (_, i) => `<option value="${i + 1}">Tháng ${i + 1}</option>`).join("")}
          </select>
        </div>
        <div class="ranking-filter-field">
          <label>Năm</label>
          <select data-erank-year></select>
        </div>
        <div class="ranking-filter-field">
          <label>Quý</label>
          <select data-erank-quarter>
            <option value="0">-- Tất cả --</option>
            <option value="1">Quý 1 (T1-T3)</option>
            <option value="2">Quý 2 (T4-T6)</option>
            <option value="3">Quý 3 (T7-T9)</option>
            <option value="4">Quý 4 (T10-T12)</option>
          </select>
        </div>
        <div class="ranking-filter-actions">
          <button type="button" class="ranking-filter-button" data-erank-load>Xem ranking</button>
        </div>
      </div>
      <div class="ranking-table-scroll">
        <table class="data-table" hidden>
          <thead><tr><th>Hạng</th><th>Mã Series</th><th>Tên Series</th><th>Lượt xem</th><th>Like</th><th>Dislike</th></tr></thead>
          <tbody data-erank-body></tbody>
        </table>
        <p class="empty-msg" data-erank-message>Đang tải ranking...</p>
      </div>`;
    const year = panel.querySelector("[data-erank-year]");
    const currentYear = new Date().getFullYear();
    for (let value = currentYear; value >= currentYear - 4; value -= 1) {
      year.add(new Option(String(value), String(value)));
    }
    const month = panel.querySelector("[data-erank-month]");
    const quarter = panel.querySelector("[data-erank-quarter]");
    month.addEventListener("change", () => { if (month.value !== "0") quarter.value = "0"; });
    quarter.addEventListener("change", () => { if (quarter.value !== "0") month.value = "0"; });
    panel.querySelector("[data-erank-load]").addEventListener("click", () => loadInlineRanking(panel));
    return panel;
  }

  async function loadInlineRanking(panel) {
    const tbody = panel.querySelector("[data-erank-body]");
    const message = panel.querySelector("[data-erank-message]");
    const table = panel.querySelector("table");
    const loadButton = panel.querySelector("[data-erank-load]");
    tbody.replaceChildren();
    table.hidden = true;
    message.hidden = false;
    message.textContent = "Đang tải ranking...";
    loadButton.disabled = true;
    const query = new URLSearchParams({
      month: panel.querySelector("[data-erank-month]").value,
      quarter: panel.querySelector("[data-erank-quarter]").value,
      year: panel.querySelector("[data-erank-year]").value,
    });
    try {
      const response = await fetch(`/manga/ranking?${query}`);
      if (!response.ok) throw new Error("fail");
      const rows = await response.json();
      if (!rows.length) {
        message.textContent = "Không có dữ liệu trong khoảng thời gian này.";
        panel.dataset.loaded = "true";
        return;
      }
      const medals = ["🥇", "🥈", "🥉"];
      rows.forEach((item) => {
        const row = document.createElement("tr");
        row.append(
          rankingCell(`${medals[item.rank - 1] || ""}${medals[item.rank - 1] ? " " : ""}${item.rank}`),
          rankingCell(item.seriesId), rankingCell(item.seriesName), rankingCell(item.totalView),
          rankingCell(item.totalLike), rankingCell(item.totalDislike),
        );
        tbody.appendChild(row);
      });
      table.hidden = false;
      message.hidden = true;
      panel.dataset.loaded = "true";
    } catch (error) {
      message.textContent = "Không thể tải ranking. Vui lòng thử lại.";
    } finally {
      loadButton.disabled = false;
    }
  }

  function initEditor() {
    if (!document.getElementById("activeSessionsBody") || !document.getElementById("proposalVoteBody")) return;
    const home = document.getElementById("tab-home");
    const sessions = document.getElementById("activeSessionsSection");
    const sessionsHeading = sessions?.previousElementSibling;
    const proposal = document.getElementById("tab-project");
    if (!home || !sessions || !proposal) return;

    // Panel "Phiên vote": gộp tiêu đề + bảng phiên vote.
    const sessionsPanel = document.createElement("section");
    sessionsPanel.className = "editor-inline-panel";
    sessionsPanel.dataset.panel = "sessions";
    if (sessionsHeading && sessionsHeading.matches("h1, h2, h3")) sessionsPanel.appendChild(sessionsHeading);
    sessionsPanel.appendChild(sessions);

    // Panel "Đề xuất chờ duyệt".
    proposal.classList.remove("tab-pane", "active");
    stripRedundantHeading(proposal);
    removeNavigation("tab-project");
    const proposalPanel = document.createElement("section");
    proposalPanel.className = "editor-inline-panel";
    proposalPanel.dataset.panel = "proposal";
    proposalPanel.appendChild(proposal);

    // Panel "Ranking" (inline).
    const rankingPanel = buildInlineRankingPanel();
    rankingPanel.classList.add("editor-inline-panel");
    rankingPanel.dataset.panel = "ranking";

    // Khu chứa 3 panel ngay trong trang chủ.
    const host = document.createElement("div");
    host.id = "editorInlinePanels";
    host.className = "editor-inline-panels";
    host.append(rankingPanel, sessionsPanel, proposalPanel);
    home.appendChild(host);

    const buttons = {};
    function showEditorPanel(name) {
      host.querySelectorAll(":scope > .editor-inline-panel").forEach((p) => {
        p.hidden = p.dataset.panel !== name;
      });
      Object.keys(buttons).forEach((key) => {
        buttons[key] && buttons[key].classList.toggle("is-active", key === name);
      });
      if (name === "ranking" && rankingPanel.dataset.loaded !== "true") loadInlineRanking(rankingPanel);
      if (name === "sessions") window.loadActiveSessions && window.loadActiveSessions();
      if (name === "proposal") window.loadVoteProposals && window.loadVoteProposals();
    }

    addQuickActions("tab-home", [
      { label: "Ranking", run: () => showEditorPanel("ranking") },
      { label: "Phiên vote", run: () => showEditorPanel("sessions") },
      { label: "Đề xuất chờ duyệt", run: () => showEditorPanel("proposal") },
    ]);

    const bar = home.querySelector(":scope > .workspace-quick-actions");
    if (bar) {
      const btns = bar.querySelectorAll("button");
      buttons.ranking = btns[0];
      buttons.sessions = btns[1];
      buttons.proposal = btns[2];
    }

    // Mặc định mở "Đề xuất chờ duyệt".
    showEditorPanel("proposal");
  }

  /**
   * Timeline giao việc được viết trong #tab-assistant, nên mergeAsDetails() kéo
   * luôn nó vào bên trong khối gập "Theo dõi công việc của trợ lý" — phải bấm
   * dấu cộng mới thấy, và khi có task đang chạy thì lưới thẻ task đẩy timeline
   * xuống khuất. Đây là hai nội dung khác nhau: khối gập là các task ĐANG chạy,
   * timeline là toàn bộ LỊCH SỬ. Tách timeline ra thành section riêng, luôn hiện.
   */
  function liftAssignmentTimeline(details) {
    const timeline = document.getElementById("assignmentTimelineSection");
    if (!timeline || document.getElementById("assignmentTimelineWorkspace")) return;

    const anchor = details || document.getElementById("tab-assistant-merged");
    const home = document.getElementById("tab-home");
    if (!anchor && !home) return;

    // Tiêu đề chuyển lên header chuẩn của workspace section, bỏ h3 cũ để khỏi lặp.
    timeline.querySelector(":scope > h3")?.remove();

    const section = document.createElement("section");
    section.className = "workspace-merged-section";
    section.id = "assignmentTimelineWorkspace";
    section.innerHTML = `
      <header class="workspace-merged-header">
        <div>
          <h2>Timeline giao việc theo trợ lý</h2>
          <p>Toàn bộ các vòng đã giao và bản trợ lý gửi lại, kể cả vòng đã kết thúc.</p>
        </div>
      </header>
      <div class="workspace-merged-body"></div>`;
    section.querySelector(".workspace-merged-body").appendChild(timeline);

    if (anchor) {
      anchor.insertAdjacentElement("afterend", section);
    } else {
      home.appendChild(section);
    }
  }

  function initMangaka() {
    if (!document.querySelector(".home-report-hub") || !document.getElementById("assistantTasksContainer")) return;
    const details = mergeAsDetails("tab-assistant", "tab-home", {
      title: "Theo dõi công việc của trợ lý",
      description: "Kiểm tra task đang thực hiện và mở chi tiết ngay từ trang tổng quan.",
      open: false,
    });
    details?.addEventListener("toggle", () => {
      if (details.open && typeof window.loadAssistantTasks === "function") {
        window.loadAssistantTasks();
      }
    });

    liftAssignmentTimeline(details);
    // Timeline giờ nằm ngoài khối gập nên phải tự nạp, không chờ người dùng mở
    // dấu cộng như trước.
    window.loadAssignmentTimeline?.();
  }

  function initAssistant() {
    if (!document.querySelector(".assistant-task-table") || !document.getElementById("tab-home")) return;
    addQuickActions("tab-home", [
      { label: "Ranking", run: openSharedRankingModal },
      { label: "Công việc được giao", primary: true, run: () => window.openTab?.("tab-project") },
      { label: "Mở bảng vẽ", run: () => window.openTab?.("tab-draw") },
      { label: "Tin nhắn", run: () => window.openTab?.("tab-chat") },
    ]);
  }

  function initTantou() {
    if (!document.getElementById("pendingCancelTbody") || !document.getElementById("tab-home")) return;
    addQuickActions("tab-home", [
      { label: "Ranking", run: openSharedRankingModal },
      { label: "Duyệt bản thảo", primary: true, run: () => window.openTab?.("tab-proposal") },
      { label: "Duyệt chapter", run: () => window.openTab?.("tab-chapter-review") },
      { label: "Quản lý Mangaka", run: () => { window.openTab?.("tab-manage-mangaka"); window.mmLoadMangakas?.(); } },
    ]);
  }

  function directHeading(tab) {
    return Array.from(tab.children).find((child) => /^H[12]$/.test(child.tagName)) || null;
  }

  function isNativeWorkspace(tab) {
    if (tab.id === "tab-draw") return true;
    if (tab.querySelector(":scope > .proposal-workspace")) return true;
    if (tab.querySelector(":scope > .home-report-hub")) return true;
    if (tab.id === "tab-project" && tab.querySelector("#project-view-series")) return true;
    return false;
  }

  function normalizeNativeWorkspace(tab) {
    tab.classList.add("role-workspace-native");
    const nativeSurface = tab.querySelector(
      ":scope > .proposal-workspace, :scope > .home-report-hub, :scope > .project-series-surface",
    );
    nativeSurface?.classList.add("role-workspace-native-surface");
  }

  function buildWorkspaceShell(tab) {
    if (!tab || tab.dataset.workspaceShell === "true") return;
    tab.dataset.workspaceShell = "true";

    if (isNativeWorkspace(tab)) {
      normalizeNativeWorkspace(tab);
      return;
    }

    const heading = directHeading(tab);
    const title = heading?.textContent?.trim() || "Không gian làm việc";
    heading?.remove();

    const surface = document.createElement("section");
    surface.className = "role-workspace-surface";
    surface.setAttribute("aria-label", title);

    const header = document.createElement("header");
    header.className = "role-workspace-header";
    header.innerHTML = `<div><h1>${title}</h1></div>`;

    const content = document.createElement("div");
    content.className = "role-workspace-content";
    content.append(...tab.childNodes);

    surface.append(header, content);
    tab.appendChild(surface);
  }

  function normalizeWorkspaceShells() {
    document
      .querySelectorAll(".flex-container > .tab-pane")
      .forEach(buildWorkspaceShell);
    document.body.classList.add("role-workspaces-ready");
  }

  /* Tạo vùng cuộn nội bộ cho các bảng có số dòng tăng theo dữ liệu API. */
  const TABLE_SCROLL_CONTAINER_SELECTOR = [
    ".app-data-table-scroll",
    ".assistant-task-table-wrap",
    ".proposal-table-stage",
    ".ranking-table-scroll",
    ".project-series-table-scroll",
    ".chapter-list-pane",
    ".shared-workflow-modal",
  ].join(",");

  function wrapScrollableTable(table) {
    if (!(table instanceof HTMLTableElement)) return;
    if (!table.closest(".role-workspace-content")) return;
    if (table.closest(TABLE_SCROLL_CONTAINER_SELECTOR)) return;

    const wrapper = document.createElement("div");
    wrapper.className = "app-data-table-scroll";
    wrapper.setAttribute("role", "region");
    wrapper.setAttribute("aria-label", "Bảng dữ liệu có thể cuộn");
    wrapper.tabIndex = 0;
    table.parentNode?.insertBefore(wrapper, table);
    wrapper.appendChild(table);
  }

  function normalizeScrollableTables(root = document) {
    const tables = [];
    if (root instanceof HTMLTableElement && root.matches(".data-table, .profile-role-table")) {
      tables.push(root);
    }
    root
      .querySelectorAll?.("table.data-table, table.profile-role-table")
      .forEach((table) => tables.push(table));
    tables.forEach(wrapScrollableTable);
  }

  /* Theo dõi bảng được render sau khi đổi tab hoặc tải dữ liệu để không cần tải lại trang. */
  function observeScrollableTables() {
    const workspaceRoot = document.querySelector(".flex-container");
    if (!workspaceRoot) return;

    const observer = new MutationObserver((records) => {
      records.forEach((record) => {
        record.addedNodes.forEach((node) => {
          if (node.nodeType === Node.ELEMENT_NODE) normalizeScrollableTables(node);
        });
      });
    });
    observer.observe(workspaceRoot, { childList: true, subtree: true });
  }

  function init() {
    removeDeadPlaceholders();
    initAdmin();
    initEditor();
    initMangaka();
    initAssistant();
    initTantou();
    normalizeWorkspaceShells();
    normalizeScrollableTables();
    observeScrollableTables();
    removeContentTabIcons();
    document.addEventListener("keydown", (event) => {
      if (event.key !== "Escape") return;
      closeSharedRankingModal();
      document
        .querySelectorAll(".shared-workflow-modal.show")
        .forEach((overlay) => closeSharedWorkflowModal(overlay));
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init, { once: true });
  } else {
    init();
  }
})();
