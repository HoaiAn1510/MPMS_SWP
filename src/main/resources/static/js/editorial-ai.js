/**
 * Trợ lý AI cho quy trình duyệt của Biên tập viên và Hội đồng biên tập.
 * API dữ liệu: GET /manga/tantou/proposals; GET /manga/editor/data.
 * API AI: POST /manga/tantou/ai/*; POST /manga/editor/ai/*.
 */
(function () {
  "use strict";

  const role = document.body.dataset.editorialRole;
  if (!role) return;

  const labels = role === "tantou"
    ? {
        title: "AI Review",
        subtitle: "Ph\u00e2n t\u00edch \u0111\u1ec1 xu\u1ea5t theo context c\u1ee7a Tantou",
        empty: "Ch\u1ecdn m\u1ed9t \u0111\u1ec1 xu\u1ea5t \u0111\u1ec3 b\u1eaft \u0111\u1ea7u.",
        apply: "D\u00f9ng l\u00e0m nh\u1eadn x\u00e9t",
      }
    : {
        title: "AI Brief",
        subtitle: "T\u00f3m t\u1eaft h\u1ed3 s\u01a1 v\u00e0 h\u1ed7 tr\u1ee3 quy\u1ebft \u0111\u1ecbnh c\u1ee7a Board",
        empty: "Ch\u1ecdn m\u1ed9t \u0111\u1ec1 xu\u1ea5t \u0111\u1ec3 b\u1eaft \u0111\u1ea7u.",
        apply: "D\u00f9ng l\u00e0m vote comment",
      };

  let lastResult = null;
  let proposals = [];

  document.addEventListener("DOMContentLoaded", function () {
    buildPanel();
    document.getElementById("btnEditorialAi")?.addEventListener("click", openPanel);
  });

  function buildPanel() {
    const overlay = document.createElement("div");
    overlay.id = "editorialAiOverlay";
    overlay.className = "editorial-ai-overlay";
    overlay.innerHTML = `
      <section class="editorial-ai-panel" role="dialog" aria-modal="true" aria-labelledby="editorialAiTitle">
        <header class="editorial-ai-header">
          <div>
            <span class="editorial-ai-kicker"><i class="fa-solid fa-wand-magic-sparkles"></i> SANKYUU AI</span>
            <h2 id="editorialAiTitle">${labels.title}</h2>
            <p>${labels.subtitle}</p>
          </div>
          <button type="button" class="editorial-ai-close" aria-label="\u0110\u00f3ng" title="\u0110\u00f3ng">
            <i class="fa-solid fa-xmark"></i>
          </button>
        </header>
        <div class="editorial-ai-context">
          <label for="editorialAiProposal">\u0110\u1ec1 xu\u1ea5t</label>
          <select id="editorialAiProposal"><option value="">\u0110ang t\u1ea3i...</option></select>
          <label for="editorialAiDraft">Ghi ch\u00fa / nh\u1eadn x\u00e9t nh\u00e1p</label>
          <textarea id="editorialAiDraft" rows="3" placeholder="Th\u00eam \u0111i\u1ec1u m mu\u1ed1n AI t\u1eadp trung..."></textarea>
        </div>
        <div class="editorial-ai-actions">${actionButtons()}</div>
        <div id="editorialAiResult" class="editorial-ai-result is-empty">${labels.empty}</div>
        <footer class="editorial-ai-footer">
          <span id="editorialAiStatus"></span>
          <button type="button" id="editorialAiApply" class="btn-action primary" disabled>
            <i class="fa-solid fa-arrow-turn-down"></i> ${labels.apply}
          </button>
        </footer>
      </section>`;
    document.body.appendChild(overlay);

    overlay.querySelector(".editorial-ai-close").addEventListener("click", closePanel);
    overlay.addEventListener("click", (event) => {
      if (event.target === overlay) closePanel();
    });
    overlay.querySelectorAll("[data-ai-mode]").forEach((button) => {
      button.addEventListener("click", () => runAi(button.dataset.aiMode, button.dataset.aiInstruction || ""));
    });
    document.getElementById("editorialAiProposal").addEventListener("change", syncDraftFromPage);
    document.getElementById("editorialAiApply").addEventListener("click", applyResult);
  }

  function actionButtons() {
    if (role === "tantou") {
      return `
        <button type="button" data-ai-mode="review-assist"><i class="fa-solid fa-list-check"></i>T\u1ea1o checklist review</button>
        <button type="button" data-ai-mode="feedback-polish"><i class="fa-solid fa-pen-to-square"></i>Vi\u1ebft l\u1ea1i feedback</button>
        <button type="button" data-ai-mode="review-assist" data-ai-instruction="T\u1eadp trung t\u00ecm r\u1ee7i ro v\u00e0 \u0111i\u1ec3m c\u00f2n thi\u1ebfu tr\u01b0\u1edbc khi duy\u1ec7t."><i class="fa-solid fa-triangle-exclamation"></i>T\u00ecm r\u1ee7i ro tr\u01b0\u1edbc duy\u1ec7t</button>`;
    }
    return `
      <button type="button" data-ai-mode="brief"><i class="fa-solid fa-file-lines"></i>T\u00f3m t\u1eaft proposal</button>
      <button type="button" data-ai-mode="decision-summary"><i class="fa-solid fa-scale-balanced"></i>Ph\u00e2n t\u00edch pass / reject</button>
      <button type="button" data-ai-mode="brief" data-ai-instruction="T\u1eadp trung t\u1ea1o c\u00e1c c\u00e2u h\u1ecfi quan tr\u1ecdng c\u1ea7n l\u00e0m r\u00f5 tr\u01b0\u1edbc khi vote."><i class="fa-solid fa-circle-question"></i>G\u1ee3i \u00fd c\u00e2u h\u1ecfi tr\u01b0\u1edbc vote</button>`;
  }

  async function openPanel() {
    document.getElementById("editorialAiOverlay").classList.add("show");
    document.body.classList.add("editorial-ai-open");
    await loadProposals();
  }

  function closePanel() {
    document.getElementById("editorialAiOverlay").classList.remove("show");
    document.body.classList.remove("editorial-ai-open");
  }

  async function loadProposals() {
    const select = document.getElementById("editorialAiProposal");
    const status = document.getElementById("editorialAiStatus");
    try {
      const url = role === "tantou"
        ? "/manga/tantou/proposals?tantouId=" + encodeURIComponent(document.body.dataset.tantouId || "")
        : "/manga/editor/data";
      const response = await fetch(url);
      const data = await response.json();
      if (data.status !== "success") throw new Error(data.message || "Kh\u00f4ng th\u1ec3 t\u1ea3i \u0111\u1ec1 xu\u1ea5t.");
      proposals = data.proposals || [];
      select.innerHTML = proposals.length
        ? proposals.map((item) => `<option value="${escapeHtml(item.id)}">${escapeHtml(item.seriesName)} (${escapeHtml(item.id)})</option>`).join("")
        : '<option value="">Kh\u00f4ng c\u00f3 \u0111\u1ec1 xu\u1ea5t ph\u00f9 h\u1ee3p</option>';

      const activeId = role === "tantou" ? document.getElementById("reviewModalProposalId")?.value : "";
      if (activeId && proposals.some((item) => item.id === activeId)) select.value = activeId;
      syncDraftFromPage();
      status.textContent = proposals.length + " \u0111\u1ec1 xu\u1ea5t s\u1eb5n s\u00e0ng";
    } catch (error) {
      select.innerHTML = '<option value="">L\u1ed7i t\u1ea3i d\u1eef li\u1ec7u</option>';
      status.textContent = error.message;
    }
  }

  function syncDraftFromPage() {
    const proposalId = document.getElementById("editorialAiProposal").value;
    const source = role === "tantou"
      ? document.getElementById("reviewModalComment")
      : document.getElementById("vote-comment-" + proposalId);
    if (source?.value.trim()) document.getElementById("editorialAiDraft").value = source.value;
  }

  async function runAi(mode, instruction) {
    const proposalId = document.getElementById("editorialAiProposal").value;
    if (!proposalId) return setStatus("H\u00e3y ch\u1ecdn m\u1ed9t \u0111\u1ec1 xu\u1ea5t.");

    const buttons = document.querySelectorAll("[data-ai-mode]");
    buttons.forEach((button) => (button.disabled = true));
    document.getElementById("editorialAiApply").disabled = true;
    setStatus("AI \u0111ang ph\u00e2n t\u00edch context...");
    document.getElementById("editorialAiResult").innerHTML = '<div class="editorial-ai-loading"><i class="fa-solid fa-spinner fa-spin"></i> \u0110ang t\u1ea1o k\u1ebft qu\u1ea3</div>';

    const draft = [document.getElementById("editorialAiDraft").value.trim(), instruction].filter(Boolean).join("\n");
    const endpoint = role === "tantou"
      ? (mode === "feedback-polish" ? "/manga/tantou/ai/feedback-polish" : "/manga/tantou/ai/review-assist")
      : (mode === "decision-summary" ? "/manga/editor/ai/decision-summary" : "/manga/editor/ai/brief");
    const params = new URLSearchParams({ proposalId, draft });

    try {
      const response = await fetch(endpoint, { method: "POST", body: params });
      const data = await response.json();
      if (data.status !== "success") throw new Error(data.message || "AI kh\u00f4ng th\u1ec3 x\u1eed l\u00fd l\u00fac n\u00e0y.");
      lastResult = data;
      renderResult(data);
      document.getElementById("editorialAiApply").disabled = false;
      setStatus("\u0110\u00e3 nh\u1eadn k\u1ebft qu\u1ea3 t\u1eeb REST API");
    } catch (error) {
      lastResult = null;
      document.getElementById("editorialAiResult").innerHTML = `<div class="editorial-ai-error">${escapeHtml(error.message)}</div>`;
      setStatus("Kh\u00f4ng th\u1ec3 ho\u00e0n t\u1ea5t");
    } finally {
      buttons.forEach((button) => (button.disabled = false));
    }
  }

  function renderResult(data) {
    const sections = data.sections || {};
    const groups = [
      ["Checklist", sections.checklist],
      ["\u0110i\u1ec3m m\u1ea1nh", sections.strengths],
      ["R\u1ee7i ro", sections.risks],
      ["C\u00e2u h\u1ecfi c\u1ea7n l\u00e0m r\u00f5", sections.questions],
    ];
    let html = `<p class="editorial-ai-summary">${escapeHtml(data.content || "")}</p>`;
    if (sections.feedback) html += `<div class="editorial-ai-feedback"><strong>Feedback g\u1ee3i \u00fd</strong><p>${escapeHtml(sections.feedback)}</p></div>`;
    groups.forEach(([title, items]) => {
      if (Array.isArray(items) && items.length) {
        html += `<div class="editorial-ai-group"><strong>${title}</strong><ul>${items.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ul></div>`;
      }
    });
    html += `<div class="editorial-ai-meta"><span>G\u1ee3i \u00fd: ${escapeHtml(data.recommendation || "undetermined")}</span><span>\u0110\u1ed9 tin c\u1eady: ${escapeHtml(data.confidence || "low")}</span></div>`;
    const result = document.getElementById("editorialAiResult");
    result.classList.remove("is-empty");
    result.innerHTML = html;
  }

  function applyResult() {
    if (!lastResult) return;
    const proposalId = document.getElementById("editorialAiProposal").value;
    const target = role === "tantou"
      ? document.getElementById("reviewModalComment")
      : document.getElementById("vote-comment-" + proposalId);
    if (!target) {
      setStatus(role === "tantou"
        ? "M\u1edf modal duy\u1ec7t \u0111\u00fang proposal r\u1ed3i \u00e1p d\u1ee5ng l\u1ea1i."
        : "Kh\u00f4ng t\u00ecm th\u1ea5y \u00f4 vote comment c\u1ee7a proposal n\u00e0y.");
      return;
    }
    if (role === "tantou") {
      const proposal = proposals.find((item) => item.id === proposalId);
      if (typeof openTab === "function") {
        openTab("tab-proposal");
      }
      if (typeof openReviewModal === "function") {
        openReviewModal(proposalId, proposal?.seriesName || proposalId);
      }
    }
    target.value = lastResult.sections?.feedback || lastResult.content || "";
    target.dispatchEvent(new Event("input", { bubbles: true }));
    closePanel();
    target.focus();
  }

  function setStatus(message) {
    document.getElementById("editorialAiStatus").textContent = message;
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }
})();
