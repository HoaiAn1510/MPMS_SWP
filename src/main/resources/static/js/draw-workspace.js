/**
 * Không gian Draw: thanh công cụ hợp nhất, danh sách trang, tạo trang và chuyển chapter tại chỗ.
 * API sử dụng:
 * - GET /manga/mangaka/myseries/{seriesId}/data
 * - GET /manga/mangaka/myseries/{seriesId}/{chapterId}/data
 */
(() => {
  "use strict";

  const routePattern = /\/myseries\/([^/]+)\/([^/]+)\/([^/]+)\/edit$/;
  const workspaceMenus = new Set();
  let chapterMenu = null;
  let chapterMenuRequest = 0;

  function currentRoute() {
    const match = window.location.pathname.match(routePattern);
    return match
      ? { seriesId: match[1], chapterId: match[2], pageId: match[3] }
      : null;
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  async function readJson(response) {
    const text = await response.text();
    let data = {};
    if (text) {
      try {
        data = JSON.parse(text);
      } catch (_error) {
        throw new Error("Máy chủ trả về dữ liệu không hợp lệ.");
      }
    }
    if (!response.ok) {
      throw new Error(
        data.message || `Không thể tải dữ liệu (${response.status}).`,
      );
    }
    return data;
  }

  function normalizeImagePath(path) {
    return path ? String(path).replaceAll("\\", "/") : "";
  }

  function pageLabel(page, index) {
    return page.pageNumber || page.number || index + 1;
  }

  function pageTypeLabel(type) {
    const labels = {
      cover: "Trang bìa",
      action: "Trang hành động",
      rest: "Trang nghỉ",
      info: "Trang thông tin",
      end: "Trang cuối",
    };
    return labels[String(type || "").toLowerCase()] || "Trang";
  }

  function syncChapterContext(chapter, pages, seriesId, chapterId) {
    window._inlineChapterContexts = window._inlineChapterContexts || {};
    window._inlineChapterContexts[chapterId] = {
      sid: seriesId,
      cid: chapterId,
      script: chapter?.script || "",
      chapterName: chapter?.chapterName || "Chapter",
      pageCount: pages.length,
      hasCover: pages.some(
        (page) => String(page.pageType || "").toLowerCase() === "cover",
      ),
    };
    window._currentSid = seriesId;
    window._currentCid = chapterId;
    window._currentChapterId = chapterId;
    window._chapterScript = chapter?.script || "";
    window._chapterHasCover =
      window._inlineChapterContexts[chapterId].hasCover;
  }

  function renderDrawChapterPages(
    chapter,
    pages = [],
    seriesId,
    chapterId,
    activePageId,
  ) {
    const grid = document.getElementById("drawPageGrid");
    const count = document.getElementById("drawPageCount");
    const chapterLabel = document.querySelector(
      "#drawChapterSwitchButton > span",
    );
    if (!grid) return;

    const safePages = Array.isArray(pages) ? pages : [];
    syncChapterContext(chapter, safePages, seriesId, chapterId);

    if (count) count.textContent = `${safePages.length} trang`;
    if (chapterLabel) {
      chapterLabel.textContent = chapter?.chapterName || "Chapter";
    }

    if (safePages.length === 0) {
      grid.innerHTML =
        '<div class="draw-page-empty">Chapter này chưa có trang nào.</div>';
      return;
    }

    grid.innerHTML = safePages
      .map((page, index) => {
        const pageId = String(page.id ?? "");
        const isCurrent = pageId === String(activePageId ?? "");
        const filePath = normalizeImagePath(page.filePath);
        const number = pageLabel(page, index);
        const thumbnail = filePath
          ? `<img src="${escapeHtml(filePath)}?t=${Date.now()}" alt="Ảnh thu nhỏ trang ${escapeHtml(number)}" loading="lazy">`
          : '<span class="draw-page-placeholder"><i class="fa-regular fa-image" aria-hidden="true"></i></span>';
        const href = `/manga/mangaka/myseries/${encodeURIComponent(seriesId)}/${encodeURIComponent(chapterId)}/${encodeURIComponent(pageId)}/edit`;

        return `<a class="draw-page-card${isCurrent ? " is-current" : ""}"
            href="${href}" data-page-id="${escapeHtml(pageId)}"
            ${isCurrent ? 'aria-current="page"' : ""}>
          <span class="draw-page-thumb">${thumbnail}</span>
          <span class="draw-page-card-copy">
            <strong>Trang ${escapeHtml(number)}</strong>
            <small>${escapeHtml(pageTypeLabel(page.pageType))}</small>
          </span>
        </a>`;
      })
      .join("");
  }

  window.renderDrawChapterPages = renderDrawChapterPages;

  async function loadCurrentChapter() {
    const route = currentRoute();
    if (!route) return;

    const grid = document.getElementById("drawPageGrid");
    try {
      const response = await fetch(
        `/manga/mangaka/myseries/${encodeURIComponent(route.seriesId)}/${encodeURIComponent(route.chapterId)}/data`,
        { headers: { Accept: "application/json" } },
      );
      const data = await readJson(response);
      if (data.status !== "success") {
        throw new Error(data.message || "Không thể tải danh sách trang.");
      }
      renderDrawChapterPages(
        data.chapter,
        data.pages || [],
        route.seriesId,
        route.chapterId,
        route.pageId,
      );
    } catch (error) {
      if (grid) {
        grid.innerHTML = `<div class="draw-page-empty is-error">${escapeHtml(error.message)}</div>`;
      }
    }
  }

  // Cho phép luồng lưu bản vẽ tải lại thumbnail và dữ liệu trang ngay tại Draw.
  window.refreshDrawChapterPages = loadCurrentChapter;

  function closeWorkspaceMenu(menu, trigger) {
    if (!menu) return;
    if (typeof menu.hidePopover === "function") {
      if (menu.matches(":popover-open")) menu.hidePopover();
    } else {
      menu.hidden = true;
      menu.classList.remove("is-open");
    }
    trigger?.setAttribute("aria-expanded", "false");
  }

  function closeOtherWorkspaceMenus(activeMenu) {
    workspaceMenus.forEach(({ menu, trigger }) => {
      if (menu !== activeMenu) closeWorkspaceMenu(menu, trigger);
    });
  }

  function positionWorkspaceMenu(trigger, menu) {
    if (!trigger || !menu) return;
    const triggerRect = trigger.getBoundingClientRect();
    const menuRect = menu.getBoundingClientRect();
    const left = Math.max(
      8,
      Math.min(window.innerWidth - menuRect.width - 8, triggerRect.left),
    );
    const spaceBelow = window.innerHeight - triggerRect.bottom;
    const top =
      spaceBelow >= menuRect.height + 12
        ? triggerRect.bottom + 8
        : Math.max(8, triggerRect.top - menuRect.height - 8);
    menu.style.left = `${left}px`;
    menu.style.top = `${top}px`;
  }

  function toggleWorkspaceMenu(trigger, menu) {
    const isOpen =
      typeof menu.showPopover === "function"
        ? menu.matches(":popover-open")
        : !menu.hidden;
    if (isOpen) {
      closeWorkspaceMenu(menu, trigger);
      return;
    }

    closeOtherWorkspaceMenus(menu);
    if (typeof menu.showPopover === "function") {
      menu.showPopover();
    } else {
      menu.hidden = false;
      menu.classList.add("is-open");
    }
    trigger.setAttribute("aria-expanded", "true");
    requestAnimationFrame(() => positionWorkspaceMenu(trigger, menu));
  }

  function createWorkspaceMenu(id, extraClass = "") {
    const menu = document.createElement("div");
    menu.id = id;
    menu.className = `draw-tool-menu draw-workspace-popover ${extraClass}`.trim();
    menu.setAttribute("popover", "auto");
    menu.setAttribute("role", "dialog");
    menu.hidden = typeof menu.showPopover !== "function";
    document.body.appendChild(menu);
    return menu;
  }

  function createWorkspaceButton({
    id,
    label,
    icon,
    menu = null,
    className = "",
  }) {
    const button = document.createElement("button");
    button.type = "button";
    button.id = id;
    button.className =
      `palette-command-button draw-workspace-command ${className}`.trim();
    button.innerHTML = `<i class="${icon}" aria-hidden="true"></i><span class="draw-command-label">${label}</span>${menu ? '<i class="fa-solid fa-chevron-down menu-caret" aria-hidden="true"></i>' : ""}`;
    button.title = label;
    button.setAttribute("aria-label", label);

    if (menu) {
      button.setAttribute("aria-haspopup", "dialog");
      button.setAttribute("aria-expanded", "false");
      button.setAttribute("aria-controls", menu.id);
      button.addEventListener("click", () =>
        toggleWorkspaceMenu(button, menu),
      );
      menu.addEventListener("toggle", () => {
        button.setAttribute(
          "aria-expanded",
          menu.matches(":popover-open") ? "true" : "false",
        );
      });
      workspaceMenus.add({ menu, trigger: button });
    }
    return button;
  }

  function addMenuHeading(menu, text) {
    const heading = document.createElement("div");
    heading.className = "draw-workspace-menu-heading";
    heading.textContent = text;
    menu.appendChild(heading);
    return heading;
  }

  function addToolLabels(button) {
    if (button.querySelector(".draw-tool-label")) return;
    const labels = {
      pencil: "Bút vẽ",
      brush: "Cọ mềm",
      eraser: "Tẩy",
      bucket: "Đổ màu",
      line: "Đường thẳng",
      rect: "Chữ nhật",
      oval: "Hình tròn",
      text: "Chèn chữ",
    };
    const label = document.createElement("span");
    label.className = "draw-tool-label";
    label.textContent =
      labels[button.dataset.tool] ||
      button.title?.replace(/\s*\(.+\)$/, "") ||
      "Công cụ";
    button.appendChild(label);
  }

  function buildToolMenu(actions) {
    const toolbar = document.querySelector("#tab-draw .left-toolbar");
    if (!toolbar) return null;

    const menu = createWorkspaceMenu(
      "drawToolsMenu",
      "draw-workspace-tools-menu",
    );
    const groups = [
      {
        title: "Vẽ và chỉnh sửa",
        tools: ["pencil", "brush", "eraser", "bucket"],
      },
      {
        title: "Hình và chữ",
        tools: ["line", "rect", "oval", "text"],
      },
    ];

    groups.forEach((group) => {
      addMenuHeading(menu, group.title);
      const grid = document.createElement("div");
      grid.className = "draw-workspace-tool-grid";
      group.tools.forEach((tool) => {
        const button = toolbar.querySelector(`.lt-btn[data-tool="${tool}"]`);
        if (!button) return;
        button.classList.add("draw-tool-menu-item");
        addToolLabels(button);
        grid.appendChild(button);
      });
      menu.appendChild(grid);
    });

    const selectionTrigger = document.getElementById("toolSelectionMenu");
    if (selectionTrigger) {
      addMenuHeading(menu, "Vùng chọn");
      selectionTrigger.classList.add("draw-tool-menu-item");
      selectionTrigger.innerHTML =
        '<i class="fa-solid fa-object-group" aria-hidden="true"></i><span class="draw-tool-label">Chọn vùng</span><i class="fa-solid fa-chevron-right selection-menu-caret" aria-hidden="true"></i>';
      menu.appendChild(selectionTrigger);
    }

    const trigger = createWorkspaceButton({
      id: "btnDrawToolsMenu",
      label: "Công cụ",
      icon: "fa-solid fa-pen-ruler",
      menu,
    });
    actions.prepend(trigger);

    menu.querySelectorAll(".lt-btn[data-tool]").forEach((button) => {
      button.addEventListener("click", () => {
        const label = button.querySelector(".draw-tool-label")?.textContent;
        const triggerLabel = trigger.querySelector(".draw-command-label");
        if (label && triggerLabel) triggerLabel.textContent = label;
        closeWorkspaceMenu(menu, trigger);
        requestAnimationFrame(syncPropertyButtons);
      });
    });
    return trigger;
  }

  function buildPropertyMenu(actions, config) {
    const group = config.element;
    if (!group) return null;

    const menu = createWorkspaceMenu(
      `${config.id}Menu`,
      "draw-workspace-property-menu",
    );
    addMenuHeading(menu, config.label);
    menu.appendChild(group);
    group.classList.add("draw-property-popover-group");

    const trigger = createWorkspaceButton({
      id: config.id,
      label: config.label,
      icon: config.icon,
      menu,
      className: "draw-property-trigger",
    });
    trigger.dataset.property = config.key;

    const badge = document.createElement("span");
    badge.className = "draw-command-value";
    trigger
      .querySelector(".draw-command-label")
      .insertAdjacentElement("afterend", badge);

    const updateValue = () => {
      const input = document.getElementById(config.valueId);
      badge.textContent = config.format(input?.value);
    };
    document
      .getElementById(config.valueId)
      ?.addEventListener("input", updateValue);
    config.extraValueId &&
      document
        .getElementById(config.extraValueId)
        ?.addEventListener("input", updateValue);
    updateValue();
    actions.appendChild(trigger);
    return trigger;
  }

  let propertyEntries = [];

  function syncPropertyButtons() {
    propertyEntries.forEach(({ trigger, group }) => {
      const visible = group && group.style.display !== "none";
      trigger.hidden = !visible;
      if (!visible) {
        const entry = [...workspaceMenus].find(
          (item) => item.trigger === trigger,
        );
        if (entry) closeWorkspaceMenu(entry.menu, trigger);
      }
    });
  }

  function buildLayerMenu(actions) {
    const panel = document.querySelector(
      "#tab-draw .right-panel > .panel-section:not(.manga-chat-panel):not(.submission-update-panel)",
    );
    if (!panel) return;

    const menu = createWorkspaceMenu(
      "drawLayersMenu",
      "draw-workspace-layer-menu",
    );
    panel.classList.add("draw-layer-popover-panel");

    const title = panel.querySelector(".layer-panel-title");
    const addButton = panel.querySelector("#btnAddLayer");
    if (title && addButton && !panel.querySelector(".draw-layer-popover-header")) {
      const header = document.createElement("div");
      header.className = "draw-layer-popover-header";
      title.before(header);
      header.append(title, addButton);
    }

    menu.appendChild(panel);
    const trigger = createWorkspaceButton({
      id: "btnDrawLayersMenu",
      label: "Layer",
      icon: "fa-solid fa-layer-group",
      menu,
    });
    const badge = document.createElement("span");
    badge.className = "draw-command-count";
    trigger
      .querySelector(".draw-command-label")
      .insertAdjacentElement("afterend", badge);
    const list = panel.querySelector("#layerList");
    const updateCount = () => {
      badge.textContent = String(list?.querySelectorAll(".layer-item").length || 0);
    };
    new MutationObserver(updateCount).observe(list, {
      childList: true,
      subtree: true,
    });
    updateCount();
    actions.appendChild(trigger);
  }

  function buildAiMenu(actions) {
    const panel = document.querySelector("#tab-draw .manga-chat-panel");
    if (!panel) return;

    panel.classList.add("draw-ai-popover-panel");
    const menu = createWorkspaceMenu(
      "drawAiMenu",
      "draw-workspace-ai-menu",
    );
    menu.appendChild(panel);
    const trigger = createWorkspaceButton({
      id: "btnDrawAiMenu",
      label: "AI hỗ trợ",
      icon: "fa-solid fa-wand-magic-sparkles",
      menu,
    });
    actions.appendChild(trigger);
  }

  function buildPageTaskButton(actions) {
    const panel = document.querySelector("#tab-draw .submission-update-panel");
    const sourceButton = document.getElementById("btnEditSubmission");
    const modal = document.getElementById("editSubmissionModal");
    if (!panel || !sourceButton) return;

    if (modal && modal.parentElement !== document.body) {
      document.body.appendChild(modal);
    }
    const trigger = createWorkspaceButton({
      id: "btnDrawPageTasks",
      label: "Tác vụ trang",
      icon: "fa-solid fa-file-arrow-up",
    });
    trigger.addEventListener("click", () => sourceButton.click());
    actions.appendChild(trigger);
    panel.hidden = true;
  }

  function buildUnifiedDrawToolbar() {
    const tab = document.getElementById("tab-draw");
    const paletteBar = tab?.querySelector(".color-palette-bar");
    const actions = paletteBar?.querySelector(".palette-command-actions");
    if (!tab || !paletteBar || !actions || actions.dataset.unified === "true") {
      return;
    }

    actions.dataset.unified = "true";
    paletteBar.classList.add("draw-unified-command-bar");
    actions.classList.add("draw-unified-actions");

    const colorButton = document.getElementById("btnColorMenu");
    const frameButton = document.getElementById("btnFrameMenu");
    const eyedropButton = document.getElementById("toolEyedrop");
    const colorPicker = tab.querySelector(".palette-color-picker");
    const historyActions = tab.querySelector(".palette-history-actions");

    const toolButton = buildToolMenu(actions);
    const topBar = document.getElementById("topPropertyBar");
    propertyEntries = [
      {
        key: "size",
        id: "btnPenSizeMenu",
        label: "Cỡ nét",
        icon: "fa-solid fa-sliders",
        valueId: "penSizeValue",
        format: (value) => `${value || 6}px`,
        element: document.getElementById("penSize")?.closest(".prop-group"),
      },
      {
        key: "opacity",
        id: "btnPenOpacityMenu",
        label: "Độ mờ",
        icon: "fa-solid fa-droplet",
        valueId: "penOpacityValue",
        format: (value) => `${value || 100}%`,
        element: document.getElementById("penOpacity")?.closest(".prop-group"),
      },
      {
        key: "shape",
        id: "btnShapeMenu",
        label: "Hình dạng",
        icon: "fa-regular fa-square",
        valueId: "shapeFillGroup",
        format: () =>
          document
            .querySelector("#shapeFillGroup .shape-fill-btn.active")
            ?.textContent?.trim() || "Viền",
        element: document.getElementById("shapeFillGroup"),
      },
      {
        key: "text",
        id: "btnTextSizeMenu",
        label: "Cỡ chữ",
        icon: "fa-solid fa-font",
        valueId: "textSizeValue",
        format: (value) => `${value || 24}`,
        element: document.getElementById("textSizeGroup"),
      },
    ]
      .map((config) => ({
        group: config.element,
        trigger: buildPropertyMenu(actions, config),
      }))
      .filter((entry) => entry.group && entry.trigger);

    document
      .querySelectorAll("#shapeFillGroup .shape-fill-btn")
      .forEach((button) => button.addEventListener("click", () => {
        const badge = document.querySelector("#btnShapeMenu .draw-command-value");
        requestAnimationFrame(() => {
          if (badge) badge.textContent = button.textContent.trim();
        });
      }));

    [
      toolButton,
      ...propertyEntries.map((entry) => entry.trigger),
      colorButton,
      frameButton,
      eyedropButton,
      colorPicker,
    ].forEach((element) => element && actions.appendChild(element));

    buildLayerMenu(actions);
    buildAiMenu(actions);
    buildPageTaskButton(actions);
    if (historyActions) actions.appendChild(historyActions);

    topBar?.querySelectorAll(".prop-divider").forEach((divider) => divider.remove());
    if (topBar) topBar.hidden = true;
    tab.querySelector(".left-toolbar")?.setAttribute("hidden", "");
    tab.querySelector(".right-panel")?.setAttribute("hidden", "");

    const propertyObserver = new MutationObserver(syncPropertyButtons);
    propertyEntries.forEach(({ group }) =>
      propertyObserver.observe(group, {
        attributes: true,
        attributeFilter: ["style"],
      }),
    );
    syncPropertyButtons();

    tab.querySelectorAll(".lt-btn[data-tool]").forEach((button) => {
      button.addEventListener("click", () =>
        requestAnimationFrame(syncPropertyButtons),
      );
    });
  }

  function removeChapterMenu() {
    chapterMenuRequest += 1;
    if (chapterMenu?.matches?.(":popover-open")) {
      chapterMenu.hidePopover();
    }
    chapterMenu?.remove();
    chapterMenu = null;
    const button = document.getElementById("drawChapterSwitchButton");
    button?.setAttribute("aria-expanded", "false");
  }

  function positionChapterMenu(button) {
    if (!chapterMenu || !button) return;
    const rect = button.getBoundingClientRect();
    const width = Math.min(340, Math.max(260, rect.width));
    const left = Math.min(
      window.innerWidth - width - 12,
      Math.max(12, rect.left),
    );
    chapterMenu.style.width = `${width}px`;
    chapterMenu.style.left = `${left}px`;
    chapterMenu.style.top = `${Math.min(window.innerHeight - 90, rect.bottom + 8)}px`;
  }

  function chapterSortValue(chapter) {
    const number = Number(chapter.chapterNumber);
    if (Number.isFinite(number)) return number;
    return (
      new Date(chapter.createdAt || chapter.submittedAt || 0).getTime() || 0
    );
  }

  async function openChapter(chapter, seriesId) {
    const chapterId = String(chapter.id ?? "");
    if (!chapterId) return;

    const selected = chapterMenu?.querySelector(
      `[data-chapter-id="${CSS.escape(chapterId)}"]`,
    );
    selected?.classList.add("is-loading");

    try {
      const response = await fetch(
        `/manga/mangaka/myseries/${encodeURIComponent(seriesId)}/${encodeURIComponent(chapterId)}/data`,
        { headers: { Accept: "application/json" } },
      );
      const data = await readJson(response);
      if (data.status !== "success") {
        throw new Error(data.message || "Không thể mở chapter.");
      }

      const pages = Array.isArray(data.pages) ? data.pages : [];
      if (pages.length > 0) {
        window.location.href =
          `/manga/mangaka/myseries/${encodeURIComponent(seriesId)}/${encodeURIComponent(chapterId)}/${encodeURIComponent(pages[0].id)}/edit`;
        return;
      }

      if (String(data.chapter?.status || chapter.status || "") !== "unfinish") {
        throw new Error(
          "Chapter này chưa có trang và hiện không thể thêm trang mới.",
        );
      }

      syncChapterContext(data.chapter || chapter, pages, seriesId, chapterId);
      if (typeof window.activateInlineChapter === "function") {
        window.activateInlineChapter(seriesId, chapterId);
      }
      removeChapterMenu();
      if (typeof window.openAddPageModal === "function") {
        window.openAddPageModal(seriesId, chapterId);
      }
    } catch (error) {
      selected?.classList.remove("is-loading");
      if (window.showUiAlertAsync) {
        await window.showUiAlertAsync(error.message);
      } else {
        window.alert(error.message);
      }
    }
  }

  async function toggleChapterMenu(button) {
    if (chapterMenu) {
      removeChapterMenu();
      return;
    }

    const route = currentRoute();
    if (!route) return;
    const requestId = ++chapterMenuRequest;
    button.setAttribute("aria-expanded", "true");

    chapterMenu = document.createElement("div");
    chapterMenu.className = "draw-chapter-switch-menu";
    chapterMenu.id = "drawChapterSwitchMenu";
    chapterMenu.setAttribute("role", "menu");
    chapterMenu.setAttribute("popover", "manual");
    chapterMenu.innerHTML =
      '<div class="draw-chapter-menu-state">Đang tải chapter...</div>';
    document.body.appendChild(chapterMenu);
    if (typeof chapterMenu.showPopover === "function") {
      chapterMenu.showPopover();
    }
    positionChapterMenu(button);

    try {
      const response = await fetch(
        `/manga/mangaka/myseries/${encodeURIComponent(route.seriesId)}/data`,
        { headers: { Accept: "application/json" } },
      );
      const data = await readJson(response);
      if (requestId !== chapterMenuRequest || !chapterMenu) return;
      if (data.status !== "success") {
        throw new Error(data.message || "Không thể tải danh sách chapter.");
      }

      const chapters = [...(data.chapters || [])].sort(
        (a, b) => chapterSortValue(b) - chapterSortValue(a),
      );
      chapterMenu.innerHTML = chapters.length
        ? chapters
            .map((chapter) => {
              const isCurrent =
                String(chapter.id) === String(route.chapterId);
              return `<button type="button" class="draw-chapter-option${isCurrent ? " is-current" : ""}"
                  role="menuitem" data-chapter-id="${escapeHtml(chapter.id)}">
                <span class="draw-chapter-option-copy">
                  <strong>${escapeHtml(chapter.chapterName || "Chapter")}</strong>
                  <small>${escapeHtml(chapter.status === "unfinish" ? "Đang vẽ" : chapter.status || "")}</small>
                </span>
                ${isCurrent ? '<i class="fa-solid fa-check" aria-hidden="true"></i>' : ""}
              </button>`;
            })
            .join("")
        : '<div class="draw-chapter-menu-state">Series chưa có chapter.</div>';

      chapterMenu.querySelectorAll(".draw-chapter-option").forEach((option) => {
        option.addEventListener("click", (event) => {
          event.preventDefault();
          event.stopPropagation();
          const chapter = chapters.find(
            (item) => String(item.id) === option.dataset.chapterId,
          );
          if (chapter) openChapter(chapter, route.seriesId);
        });
      });
    } catch (error) {
      if (chapterMenu) {
        chapterMenu.innerHTML = `<div class="draw-chapter-menu-state is-error">${escapeHtml(error.message)}</div>`;
      }
    }
  }

  function initDrawWorkspace() {
    buildUnifiedDrawToolbar();

    const switchButton = document.getElementById("drawChapterSwitchButton");
    const addButton = document.getElementById("drawAddPageButton");

    switchButton?.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      toggleChapterMenu(switchButton);
    });

    addButton?.addEventListener("click", () => {
      const route = currentRoute();
      if (!route || typeof window.openAddPageModal !== "function") return;
      if (typeof window.activateInlineChapter === "function") {
        window.activateInlineChapter(route.seriesId, route.chapterId);
      }
      window.openAddPageModal(route.seriesId, route.chapterId);
    });

    document.addEventListener("click", (event) => {
      if (
        chapterMenu &&
        !chapterMenu.contains(event.target) &&
        !switchButton?.contains(event.target)
      ) {
        removeChapterMenu();
      }
    });
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && chapterMenu) {
        removeChapterMenu();
        switchButton?.focus();
      }
    });
    window.addEventListener("resize", () => {
      positionChapterMenu(switchButton);
      workspaceMenus.forEach(({ menu, trigger }) => {
        if (menu.matches?.(":popover-open") || !menu.hidden) {
          positionWorkspaceMenu(trigger, menu);
        }
      });
    });
    window.addEventListener(
      "scroll",
      () => {
        if (chapterMenu) positionChapterMenu(switchButton);
      },
      true,
    );

    loadCurrentChapter();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initDrawWorkspace);
  } else {
    initDrawWorkspace();
  }
})();
