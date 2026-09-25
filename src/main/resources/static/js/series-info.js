/**
 * Hiển thị cửa sổ thông tin series và các nhóm dữ liệu có thể thu gọn.
 * Không gọi API trực tiếp; dữ liệu được truyền từ chức năng mở cửa sổ của từng role.
 */
(function () {
  const chapterLabels = {
    total: "Tổng số chapter",
    unfinish: "Đang thực hiện",
    waitingReview: "Chờ BTV duyệt",
    pass: "Đã duyệt",
    published: "Đã xuất bản",
  };

  const voteTypeLabels = {
    stop: "Vote dừng series",
    reward: "Vote khen thưởng",
    defense: "Hồ sơ bảo vệ",
  };

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function display(value, fallback) {
    return escapeHtml(value == null || value === "" ? (fallback || "—") : value);
  }

  function safeUrl(value) {
    const url = String(value || "").trim();
    return /^(https?:\/\/|\/)/i.test(url) ? escapeHtml(url) : "";
  }

  function formatDate(value, withTime) {
    const formatter = withTime ? window.formatAppDateTime : window.formatAppDate;
    return typeof formatter === "function" ? escapeHtml(formatter(value)) : display(value);
  }

  function formatStatus(value) {
    return typeof window.formatStatusVi === "function"
      ? escapeHtml(window.formatStatusVi(value, "—"))
      : display(value);
  }

  function infoRow(label, value, options) {
    const config = options || {};
    return `<div class="series-info-row${config.wide ? " series-info-row-wide" : ""}">
      <dt>${escapeHtml(label)}</dt>
      <dd>${config.html ? value : display(value, config.fallback)}</dd>
    </div>`;
  }

  function section(title, summary, body, open) {
    return `<details class="series-info-section"${open ? " open" : ""}>
      <summary>
        <span class="series-info-section-title">${escapeHtml(title)}</span>
        <span class="series-info-section-summary">${escapeHtml(summary)}</span>
        <span class="series-info-chevron" aria-hidden="true"></span>
      </summary>
      <div class="series-info-section-body">${body}</div>
    </details>`;
  }

  function renderVoteSession(session) {
    const votes = Array.isArray(session.votes) ? session.votes : [];
    const voteRows = votes.length
      ? votes.map((vote) => {
          const choice = typeof window.formatVoteChoiceVi === "function"
            ? window.formatVoteChoiceVi(vote.choice)
            : vote.choice;
          return `<li><span>${display(vote.boardName, "Thành viên hội đồng")}</span><strong>${display(choice)}</strong><time>${formatDate(vote.votedAt, false)}</time></li>`;
        }).join("")
      : '<li class="series-info-empty">Chưa có phiếu bầu.</li>';
    const fileUrl = safeUrl(session.defenseFilePath);
    const result = session.status === "closed" && session.totalBoards != null
      ? `${session.votedCount ?? 0}/${session.totalBoards} đã vote · ${session.positivePercent ?? 0}% đồng ý · ${session.passed ? "Đạt" : "Không đạt"}`
      : "Chưa có kết quả cuối cùng";

    return `<article class="series-info-vote-item">
      <header>
        <strong>${display(voteTypeLabels[session.voteType] || session.voteType, "Phiên hội đồng")}</strong>
        <span class="series-info-status">${session.status === "active" ? "Đang mở" : "Đã đóng"}</span>
      </header>
      <dl class="series-info-grid series-info-grid-compact">
        ${infoRow("Ngày mở / nộp", formatDate(session.createdAt, false), { html: true })}
        ${infoRow("Ngày kết thúc", formatDate(session.closedAt, false), { html: true })}
        ${session.reason ? infoRow("Lý do", session.reason, { wide: true }) : ""}
        ${session.defenseNote ? infoRow("Ghi chú", session.defenseNote, { wide: true }) : ""}
        ${fileUrl ? infoRow("Hồ sơ bảo vệ", `<a href="${fileUrl}" target="_blank" rel="noopener">Mở tệp PDF</a>`, { html: true, wide: true }) : ""}
        ${infoRow("Kết quả", result, { wide: true })}
      </dl>
      <ul class="series-info-vote-list">${voteRows}</ul>
    </article>`;
  }

  window.renderSeriesInfoState = function renderSeriesInfoState(message, type) {
    const className = type === "error" ? "series-info-error" : "series-info-loading";
    return `<div class="${className}">${escapeHtml(message)}</div>`;
  };

  window.renderSeriesInfoAccordion = function renderSeriesInfoAccordion(data) {
    const source = data || {};
    const series = source.series || {};
    const proposal = source.proposal || {};
    const stats = source.chapterStats || {};
    const sessions = Array.isArray(source.voteSessions) ? source.voteSessions : [];

    const general = `<dl class="series-info-grid">
      ${infoRow("Tên series", series.seriesName)}
      ${infoRow("Thể loại", series.genre, { fallback: "Chưa cập nhật" })}
      ${infoRow("Trạng thái", formatStatus(series.status), { html: true })}
      ${infoRow("Ngày bắt đầu", formatDate(series.startDate, false), { html: true })}
      ${infoRow("Mô tả", series.description, { wide: true })}
    </dl>`;

    const proposalInfo = `<dl class="series-info-grid">
      ${infoRow("Mã đề xuất", proposal.id)}
      ${infoRow("Điểm biên tập viên", proposal.editorScore != null ? Number(proposal.editorScore).toFixed(2) : "Chưa chấm")}
      ${infoRow("Ngày nộp", formatDate(proposal.submittedAt || proposal.createdAt, true), { html: true })}
      ${infoRow("Tantou xử lý", formatDate(proposal.reviewedAt, true), { html: true })}
      ${infoRow("Nộp hội đồng", formatDate(proposal.boardSubmittedAt, true), { html: true })}
      ${infoRow("Hội đồng kết luận", formatDate(proposal.boardReviewedAt, true), { html: true })}
      ${infoRow("Nhận xét", proposal.comment || "Không có", { wide: true })}
    </dl>`;

    const chapterStats = `<div class="series-info-stat-grid">${Object.keys(chapterLabels)
      .map((key) => `<div><span>${escapeHtml(chapterLabels[key])}</span><strong>${display(stats[key] ?? 0)}</strong></div>`)
      .join("")}</div>`;

    const engagement = `<div class="series-info-stat-grid series-info-stat-grid-small">
      <div><span>Tổng lượt xem</span><strong>${display(source.totalViews ?? 0)}</strong></div>
      <div><span>Tổng lượt vote</span><strong>${display(source.totalVotes ?? 0)}</strong></div>
      <div><span>Lượt thích</span><strong>${display(source.totalLikes ?? 0)}</strong></div>
      <div><span>Không thích</span><strong>${display(source.totalDislikes ?? 0)}</strong></div>
    </div>`;

    const history = sessions.length
      ? `<div class="series-info-vote-history">${sessions.map(renderVoteSession).join("")}</div>`
      : '<div class="series-info-empty-state"><strong>Chưa có lịch sử hội đồng</strong><span>Các phiên vote hoặc bảo vệ sẽ xuất hiện tại đây.</span></div>';

    return `<div class="series-info-accordion">
      ${section("Thông tin chung", formatStatus(series.status), general, true)}
      ${section("Đề xuất gốc", proposal.id ? `Mã ${proposal.id}` : "Chưa có dữ liệu", proposalInfo, false)}
      ${section("Tiến độ chapter", `${stats.total ?? 0} chapter`, chapterStats, false)}
      ${section("Tương tác", `${source.totalViews ?? 0} lượt xem`, engagement, false)}
      ${section("Lịch sử hội đồng", `${sessions.length} phiên`, history, false)}
    </div>`;
  };
})();
