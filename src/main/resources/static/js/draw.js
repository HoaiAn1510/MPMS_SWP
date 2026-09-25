/**
 * Không gian vẽ: công cụ, layer, frame, vùng chọn, chat AI và lưu bản vẽ.
 * API chat: GET /api/chat/history; POST /api/chat/message.
 * API lưu: POST /api/page/{pageId}/savefile; POST /api/submission/{submissionId}/savefile.
 * API nộp bản: POST /manga/mangaka/submission/{submissionId}/submit/data.
 */
const CANVAS_W = 400,
  CANVAS_H = 600;
const canvasStack = document.getElementById("canvasStack");

function normalizeDrawCanvasStack() {
  if (!canvasStack) return;
  const input = document.getElementById("drawCanvas");
  const region = document.getElementById("regionSelectBox");
  canvasStack.querySelectorAll("canvas").forEach((canvas) => {
    if (canvas !== input) canvas.remove();
  });
  if (input && region && input.nextElementSibling !== region) {
    canvasStack.insertBefore(input, region);
  }
}

normalizeDrawCanvasStack();

function updateRangeProgress(range) {
  if (!range) return;
  const min = Number(range.min || 0);
  const max = Number(range.max || 100);
  const value = Number(range.value);
  const progress = max === min ? 0 : ((value - min) / (max - min)) * 100;
  range.style.setProperty("--range-progress", progress + "%");
}

function bindRangeNumber(rangeId, numberId) {
  const range = document.getElementById(rangeId);
  const number = document.getElementById(numberId);
  if (!range || !number) return;

  const min = Number(range.min);
  const max = Number(range.max);
  const step = Number(range.step || 1);

  const applyValue = (rawValue, restoreEmpty) => {
    if (rawValue === "" && !restoreEmpty) return;
    const parsed = Number(rawValue);
    const fallback = Number(range.value);
    const safeValue = Number.isFinite(parsed) ? parsed : fallback;
    const clamped = Math.min(max, Math.max(min, safeValue));
    const stepped = Math.round(clamped / step) * step;
    range.value = stepped;
    number.value = stepped;
    updateRangeProgress(range);
  };

  range.addEventListener("input", () => {
    number.value = range.value;
    updateRangeProgress(range);
  });
  number.addEventListener("input", () => {
    if (number.value === "") return;
    const parsed = Number(number.value);
    if (!Number.isFinite(parsed) || parsed < min || parsed > max) return;
    range.value = parsed;
    updateRangeProgress(range);
  });
  number.addEventListener("change", () => applyValue(number.value, true));
  updateRangeProgress(range);
}

// Cấu trúc layer: { id, name, canvas, ctx, visible, opacity }
let layers = [];
let activeLayerIndex = 0;
let layerCounter = 0;
let numberedLayerCounter = 1;
let frameLayerCounter = 0;

function createLayerCanvas(zIndex) {
  const c = document.createElement("canvas");
  c.width = CANVAS_W;
  c.height = CANVAS_H;
  c.style.position = "absolute";
  c.style.top = "0";
  c.style.left = "0";
  c.style.zIndex = zIndex;
  c.style.pointerEvents = "none";
  return c;
}

function addLayer(name, isBackground) {
  layerCounter++;
  const canvasEl = createLayerCanvas(layers.length + 1);
  canvasStack.insertBefore(
    canvasEl,
    document.getElementById("regionSelectBox"),
  );
  const ctxL = canvasEl.getContext("2d");
  if (isBackground) {
    ctxL.fillStyle = "#ffffff";
    ctxL.fillRect(0, 0, CANVAS_W, CANVAS_H);
  }
  const layer = {
    id: layerCounter,
    name: name || "Layer " + layerCounter,
    canvas: canvasEl,
    ctx: ctxL,
    visible: true,
    opacity: 100,
  };
  layers.push(layer);
  renderLayerList();
  return layer;
}

// Layer Chính màu trắng (luôn có, không xoá được)
const baseLayer = addLayer("Ch\u00ednh", true);
baseLayer.isBase = true;
// Layer vẽ chính, đang active
const mainLayer = addLayer("Layer 1", false);
activeLayerIndex = layers.length - 1;

function getActiveLayer() {
  return layers[activeLayerIndex];
}

function renderLayerList() {
  const listEl = document.getElementById("layerList");
  listEl.innerHTML = "";
  // Hiển thị layer từ trên cùng (cuối mảng) xuống dưới.
  for (let i = layers.length - 1; i >= 0; i--) {
    const layer = layers[i];
    const item = document.createElement("div");
    item.className = "layer-item" + (i === activeLayerIndex ? " selected" : "");
    item.innerHTML = `
                    <button type="button" class="layer-toggle-visible ${layer.visible ? "" : "hidden-layer"}" data-idx="${i}">
                        <i class="fa-solid ${layer.visible ? "fa-eye" : "fa-eye-slash"}"></i>
                    </button>
                    <div class="layer-thumb"></div>
                    <span class="layer-name">${layer.name}</span>
                `;
    item.addEventListener("click", (e) => {
      if (e.target.closest(".layer-toggle-visible")) return;
      clearEditableShapeSelection();
      activeLayerIndex = i;
      renderLayerList();
    });
    listEl.appendChild(item);

    if (i === activeLayerIndex) {
      const opacityRow = document.createElement("div");
      opacityRow.className = "layer-opacity-row";
      opacityRow.innerHTML = `
                        <span style="font-size:10px;color:var(--ps-text-dim);">Opacity</span>
                        <input type="range" min="0" max="100" value="${layer.opacity}" data-idx="${i}" class="layer-opacity-slider">
                        <span>${layer.opacity}%</span>
                    `;
      listEl.appendChild(opacityRow);
    }
  }

  listEl.querySelectorAll(".layer-toggle-visible").forEach((btn) => {
    btn.addEventListener("click", () => {
      const idx = parseInt(btn.dataset.idx);
      layers[idx].visible = !layers[idx].visible;
      layers[idx].canvas.style.display = layers[idx].visible ? "block" : "none";
      renderLayerList();
      pushHistoryEntry(layers[idx].visible ? "Hiện layer" : "Ẩn layer");
    });
  });

  listEl.querySelectorAll(".layer-opacity-slider").forEach((slider) => {
    updateRangeProgress(slider);
    slider.addEventListener("input", () => {
      const idx = parseInt(slider.dataset.idx);
      layers[idx].opacity = slider.value;
      layers[idx].canvas.style.opacity = slider.value / 100;
      slider.nextElementSibling.textContent = slider.value + "%";
      updateRangeProgress(slider);
    });
    slider.addEventListener("change", () => {
      pushHistoryEntry("Đổi độ mờ layer");
    });
  });

  updateLayerActionButtons();
}

document.getElementById("btnAddLayer").addEventListener("click", () => {
  clearEditableShapeSelection();
  numberedLayerCounter++;
  const newLayer = addLayer("Layer " + numberedLayerCounter, false);
  activeLayerIndex = layers.length - 1;
  renderLayerList();
  pushHistoryEntry("Thêm layer mới");
});

// ========================================================
// PHẦN 2: Vẽ trực tiếp trên layer đang được chọn.
// ========================================================
// Dùng một lớp nhập liệu trong suốt ở trên cùng để nhận sự kiện chuột,
// sau đó chuyển thao tác vẽ vào canvas của layer đang được chọn.
const inputLayer = document.getElementById("drawCanvas");
inputLayer.style.zIndex = 999;
inputLayer.style.pointerEvents = "auto";

let isDrawing = false;
let currentTool = "pencil";
let history = [];
let redoStack = [];
let currentHistoryItem = null; // node lịch sử đang gắn class "current" (để bỏ O(1))
// Đánh dấu có thay đổi chưa lưu để cảnh báo khi rời trang vẽ (vd tác giả mở bản
// khác mà chưa lưu). markDrawClean() gọi sau khi lưu/nạp nền xong.
let drawDirty = false;
window.markDrawClean = () => {
  drawDirty = false;
};
window.addEventListener("beforeunload", (e) => {
  if (drawDirty) {
    e.preventDefault();
    e.returnValue = "";
  }
});
let shapeStart = null;
let shapeBasePixels = null;
let shapeFillMode = "outline";
let editableShape = null;
let shapeTransformOverlay = null;

const historyListEl =
  document.getElementById("historyList") || document.createElement("div");

function decorateDrawIconButtons() {
  const icons = {
    pencil: "fa-pen",
    brush: "fa-paintbrush",
    eraser: "fa-eraser",
    bucket: "fa-fill-drip",
    line: "fa-minus",
    rect: "fa-square",
    oval: "fa-circle",
    text: "fa-font",
    select: "fa-vector-square",
    "select-oval": "fa-circle-dot",
    "select-free": "fa-crop-simple",
    toolEyedrop: "fa-eye-dropper",
    toolUndo: "fa-rotate-left",
    toolClear: "fa-trash",
    btnAddLayer: "fa-plus",
    zoomOut: "fa-magnifying-glass-minus",
    zoomIn: "fa-magnifying-glass-plus",
  };

  document.querySelectorAll(".lt-btn[data-tool]").forEach((button) => {
    if (!button.querySelector("i")) {
      button.innerHTML =
        '<i class="fa-solid ' +
        (icons[button.dataset.tool] || "fa-circle") +
        '"></i>';
    }
  });

  Object.entries(icons).forEach(([id, icon]) => {
    const button = document.getElementById(id);
    if (button && !button.querySelector("i") && !button.textContent.trim()) {
      button.innerHTML = '<i class="fa-solid ' + icon + '"></i>';
    }
  });
}

decorateDrawIconButtons();

const FRAME_TEMPLATES = [
  {
    id: "single",
    name: "Một khung",
    panels: [{ x: 0.06, y: 0.04, w: 0.88, h: 0.92 }],
  },
  {
    id: "two-columns",
    name: "Hai cột",
    panels: [
      { x: 0.06, y: 0.04, w: 0.42, h: 0.92 },
      { x: 0.52, y: 0.04, w: 0.42, h: 0.92 },
    ],
  },
  {
    id: "two-rows",
    name: "Hai hàng",
    panels: [
      { x: 0.06, y: 0.04, w: 0.88, h: 0.44 },
      { x: 0.06, y: 0.52, w: 0.88, h: 0.44 },
    ],
  },
  {
    id: "three-rows",
    name: "Ba hàng",
    panels: [
      { x: 0.06, y: 0.04, w: 0.88, h: 0.28 },
      { x: 0.06, y: 0.36, w: 0.88, h: 0.28 },
      { x: 0.06, y: 0.68, w: 0.88, h: 0.28 },
    ],
  },
  {
    id: "hero-top",
    name: "Lớn trên",
    panels: [
      { x: 0.06, y: 0.04, w: 0.88, h: 0.52 },
      { x: 0.06, y: 0.60, w: 0.42, h: 0.36 },
      { x: 0.52, y: 0.60, w: 0.42, h: 0.36 },
    ],
  },
  {
    id: "hero-bottom",
    name: "Lớn dưới",
    panels: [
      { x: 0.06, y: 0.04, w: 0.42, h: 0.36 },
      { x: 0.52, y: 0.04, w: 0.42, h: 0.36 },
      { x: 0.06, y: 0.44, w: 0.88, h: 0.52 },
    ],
  },
  {
    id: "four-grid",
    name: "Lưới bốn",
    panels: [
      { x: 0.06, y: 0.04, w: 0.42, h: 0.44 },
      { x: 0.52, y: 0.04, w: 0.42, h: 0.44 },
      { x: 0.06, y: 0.52, w: 0.42, h: 0.44 },
      { x: 0.52, y: 0.52, w: 0.42, h: 0.44 },
    ],
  },
  {
    id: "manga-flow",
    name: "Nhịp manga",
    panels: [
      { x: 0.06, y: 0.04, w: 0.55, h: 0.38 },
      { x: 0.65, y: 0.04, w: 0.29, h: 0.38 },
      { x: 0.06, y: 0.46, w: 0.29, h: 0.50 },
      { x: 0.39, y: 0.46, w: 0.55, h: 0.50 },
    ],
  },
];

let frameDraft = null;
let selectedFramePanel = 0;
let editingFrameLayer = null;
let layerPeekActive = false;

function panelToPoints(panel) {
  if (Array.isArray(panel.points) && panel.points.length === 4) {
    return panel.points.map((point) => ({ x: point.x, y: point.y }));
  }
  return [
    { x: panel.x, y: panel.y },
    { x: panel.x + panel.w, y: panel.y },
    { x: panel.x + panel.w, y: panel.y + panel.h },
    { x: panel.x, y: panel.y + panel.h },
  ];
}

function cloneFramePanels(panels) {
  return panels.map((panel) => ({ points: panelToPoints(panel) }));
}

function clampFramePoint(point) {
  point.x = Math.min(1, Math.max(0, point.x));
  point.y = Math.min(1, Math.max(0, point.y));
}

function createDefaultFramePanel(index = 0) {
  const offset = Math.min(0.18, (index % 4) * 0.035);
  return {
    points: panelToPoints({
      x: 0.16 + offset,
      y: 0.18 + offset,
      w: 0.68,
      h: 0.42,
    }),
  };
}

function traceFramePath(ctx, points, cornerStyle, cornerRadius) {
  const canvasPoints = points.map((point) => ({
    x: point.x * CANVAS_W,
    y: point.y * CANVAS_H,
  }));
  ctx.beginPath();
  if (cornerStyle !== "rounded" || cornerRadius <= 0) {
    ctx.moveTo(canvasPoints[0].x, canvasPoints[0].y);
    canvasPoints.slice(1).forEach((point) => ctx.lineTo(point.x, point.y));
    ctx.closePath();
    return;
  }

  canvasPoints.forEach((current, index) => {
    const previous = canvasPoints[(index + canvasPoints.length - 1) % canvasPoints.length];
    const next = canvasPoints[(index + 1) % canvasPoints.length];
    const previousLength = Math.max(
      0.001,
      Math.hypot(previous.x - current.x, previous.y - current.y),
    );
    const nextLength = Math.max(
      0.001,
      Math.hypot(next.x - current.x, next.y - current.y),
    );
    const radius = Math.min(cornerRadius, previousLength / 2, nextLength / 2);
    const enter = {
      x: current.x + ((previous.x - current.x) / previousLength) * radius,
      y: current.y + ((previous.y - current.y) / previousLength) * radius,
    };
    const leave = {
      x: current.x + ((next.x - current.x) / nextLength) * radius,
      y: current.y + ((next.y - current.y) / nextLength) * radius,
    };
    if (index === 0) ctx.moveTo(enter.x, enter.y);
    else ctx.lineTo(enter.x, enter.y);
    ctx.quadraticCurveTo(current.x, current.y, leave.x, leave.y);
  });
  ctx.closePath();
}

function renderFrameLayer(layer) {
  if (!layer?.frameLayout) return;
  const { panels, lineWidth, color, cornerStyle, cornerRadius } = layer.frameLayout;
  layer.ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
  layer.ctx.save();
  layer.ctx.globalAlpha = 1;
  layer.ctx.strokeStyle = color || "#111111";
  layer.ctx.lineWidth = Math.max(1, Number(lineWidth) || 4);
  layer.ctx.lineJoin = cornerStyle === "rounded" ? "round" : "miter";
  panels.forEach((panel) => {
    traceFramePath(
      layer.ctx,
      panelToPoints(panel),
      cornerStyle || "sharp",
      Number(cornerRadius) || 0,
    );
    layer.ctx.stroke();
  });
  layer.ctx.restore();
}

function beginLayerPeek() {
  if (layerPeekActive || !layers.some((layer) => !layer.visible)) return;
  layerPeekActive = true;
  layers.forEach((layer) => {
    if (!layer.visible) layer.canvas.style.display = "block";
  });
  const button = document.getElementById("btnShowAllLayers");
  button?.classList.add("peeking");
  button?.setAttribute("aria-pressed", "true");
}

function endLayerPeek() {
  if (!layerPeekActive) return;
  layerPeekActive = false;
  layers.forEach((layer) => {
    layer.canvas.style.display = layer.visible ? "block" : "none";
  });
  const button = document.getElementById("btnShowAllLayers");
  button?.classList.remove("peeking");
  button?.setAttribute("aria-pressed", "false");
}

function createFrameLayerIfNeeded() {
  const activeLayer = getActiveLayer();
  if (activeLayer?.frameLayout) return activeLayer;
  frameLayerCounter++;
  const layer = addLayer(`Frame ${frameLayerCounter}`, false);
  activeLayerIndex = layers.length - 1;
  layer.frameLayout = {
    templateId: "custom",
    panels: [],
    lineWidth: 4,
    color: "#111111",
    cornerStyle: "sharp",
    cornerRadius: 18,
  };
  return layer;
}

function addFrameDirectlyToPage() {
  const layer = createFrameLayerIfNeeded();
  layer.frameLayout.panels.push(createDefaultFramePanel(layer.frameLayout.panels.length));
  renderFrameLayer(layer);
  renderLayerList();
  pushHistoryEntry("Thêm frame trực tiếp");
}

function updateLayerActionButtons() {
  const showAllButton = document.getElementById("btnShowAllLayers");
  if (showAllButton) {
    const hiddenCount = layers.filter((layer) => !layer.visible).length;
    showAllButton.disabled = hiddenCount === 0;
    showAllButton.textContent = "Xem tất cả Layers";
    showAllButton.title = hiddenCount
      ? `Nhấn giữ để xem ${hiddenCount} layer đang ẩn`
      : "Không có layer nào đang ẩn";
  }

  const frameButton = document.getElementById("btnFrameCreator");
  if (frameButton) {
    const isEditing = Boolean(getActiveLayer()?.frameLayout);
    frameButton.innerHTML = isEditing
      ? '<i class="fa-solid fa-border-all"></i> Chỉnh frame'
      : '<i class="fa-solid fa-border-all"></i> Tạo frame';
  }
}

function setFrameTemplate(templateId) {
  const template = FRAME_TEMPLATES.find((item) => item.id === templateId);
  if (!template) return;
  frameDraft.templateId = template.id;
  frameDraft.panels = cloneFramePanels(template.panels);
  selectedFramePanel = 0;
  document.querySelectorAll(".frame-template-option").forEach((button) => {
    button.classList.toggle("active", button.dataset.templateId === templateId);
    button.setAttribute(
      "aria-pressed",
      button.dataset.templateId === templateId ? "true" : "false",
    );
  });
  renderFrameEditor();
}

function syncFramePanelInputs() {
  const panel = frameDraft?.panels[selectedFramePanel];
  const label = document.getElementById("frameSelectedLabel");
  const cornerGrid = document.getElementById("frameCornerGrid");
  if (!cornerGrid) return;
  const deleteButton = document.getElementById("btnDeleteFrameInEditor");
  const applyButton = document.getElementById("btnApplyFrame");
  if (deleteButton) deleteButton.disabled = !panel;
  if (applyButton) applyButton.disabled = !frameDraft?.panels.length;
  if (!panel) {
    if (label) label.textContent = "Chưa có frame";
    cornerGrid.innerHTML =
      '<p class="frame-empty-message">Nhấn “Thêm frame” hoặc chọn một bố cục để tiếp tục.</p>';
    return;
  }
  if (label) label.textContent = `Khung ${selectedFramePanel + 1}`;
  const cornerNames = ["Trái trên", "Phải trên", "Phải dưới", "Trái dưới"];
  cornerGrid.innerHTML = panel.points
    .map(
      (point, index) => `
        <fieldset class="frame-corner-row">
          <legend>${cornerNames[index]}</legend>
          <label>X (%)<input type="number" min="0" max="100" step="1" value="${Math.round(point.x * 100)}" data-corner-index="${index}" data-axis="x"></label>
          <label>Y (%)<input type="number" min="0" max="100" step="1" value="${Math.round(point.y * 100)}" data-corner-index="${index}" data-axis="y"></label>
        </fieldset>`,
    )
    .join("");
  cornerGrid.querySelectorAll("input").forEach((input) => {
    input.addEventListener("change", () => {
      const pointIndex = Number(input.dataset.cornerIndex);
      const pointAxis = input.dataset.axis;
      panel.points[pointIndex][pointAxis] = Number(input.value) / 100;
      clampFramePoint(panel.points[pointIndex]);
      renderFrameEditor();
    });
  });
}

function renderFrameEditor() {
  const preview = document.getElementById("frameEditorPreview");
  if (!preview || !frameDraft) return;
  preview.innerHTML = "";
  const svgNamespace = "http://www.w3.org/2000/svg";
  const svg = document.createElementNS(svgNamespace, "svg");
  svg.setAttribute("viewBox", "0 0 100 150");
  svg.setAttribute("aria-label", "Chỉnh bốn điểm góc của frame");
  svg.classList.add("frame-editor-svg");

  frameDraft.panels.forEach((panel, index) => {
    panel.points = panelToPoints(panel);
    const pointsValue = panel.points
      .map((point) => `${point.x * 100},${point.y * 150}`)
      .join(" ");
    const polygon = document.createElementNS(svgNamespace, "polygon");
    polygon.setAttribute("points", pointsValue);
    polygon.setAttribute("tabindex", "0");
    polygon.setAttribute("role", "button");
    polygon.setAttribute("aria-label", `Khung ${index + 1}, kéo để di chuyển`);
    polygon.setAttribute("stroke", frameDraft.color);
    polygon.setAttribute("stroke-width", String(Math.max(0.7, frameDraft.lineWidth / 3)));
    polygon.setAttribute(
      "stroke-linejoin",
      frameDraft.cornerStyle === "rounded" ? "round" : "miter",
    );
    polygon.classList.add("frame-editor-polygon");
    if (index === selectedFramePanel) polygon.classList.add("selected");
    polygon.addEventListener("click", () => {
      selectedFramePanel = index;
      renderFrameEditor();
    });
    polygon.addEventListener("keydown", (event) => {
      const directions = {
        ArrowLeft: { x: -0.01, y: 0 },
        ArrowRight: { x: 0.01, y: 0 },
        ArrowUp: { x: 0, y: -0.01 },
        ArrowDown: { x: 0, y: 0.01 },
      };
      if (!directions[event.key]) return;
      event.preventDefault();
      const delta = directions[event.key];
      panel.points.forEach((point) => {
        point.x += delta.x;
        point.y += delta.y;
        clampFramePoint(point);
      });
      renderFrameEditor();
    });
    polygon.addEventListener("pointerdown", (event) => {
      event.preventDefault();
      selectedFramePanel = index;
      const startX = event.clientX;
      const startY = event.clientY;
      const originalPoints = panel.points.map((point) => ({ ...point }));
      const bounds = preview.getBoundingClientRect();
      const minX = Math.min(...originalPoints.map((point) => point.x));
      const maxX = Math.max(...originalPoints.map((point) => point.x));
      const minY = Math.min(...originalPoints.map((point) => point.y));
      const maxY = Math.max(...originalPoints.map((point) => point.y));

      const move = (moveEvent) => {
        const requestedX = (moveEvent.clientX - startX) / bounds.width;
        const requestedY = (moveEvent.clientY - startY) / bounds.height;
        const dx = Math.max(-minX, Math.min(1 - maxX, requestedX));
        const dy = Math.max(-minY, Math.min(1 - maxY, requestedY));
        panel.points = originalPoints.map((point) => ({
          x: point.x + dx,
          y: point.y + dy,
        }));
        renderFrameEditor();
      };
      const stop = () => {
        window.removeEventListener("pointermove", move);
        window.removeEventListener("pointerup", stop);
      };
      window.addEventListener("pointermove", move);
      window.addEventListener("pointerup", stop, { once: true });
    });
    svg.appendChild(polygon);

    const center = panel.points.reduce(
      (value, point) => ({ x: value.x + point.x / 4, y: value.y + point.y / 4 }),
      { x: 0, y: 0 },
    );
    const number = document.createElementNS(svgNamespace, "text");
    number.setAttribute("x", String(center.x * 100));
    number.setAttribute("y", String(center.y * 150));
    number.setAttribute("text-anchor", "middle");
    number.setAttribute("dominant-baseline", "middle");
    number.classList.add("frame-editor-number");
    number.textContent = String(index + 1);
    svg.appendChild(number);

    if (index === selectedFramePanel) {
      panel.points.forEach((point, pointIndex) => {
        const handle = document.createElementNS(svgNamespace, "circle");
        handle.setAttribute("cx", String(point.x * 100));
        handle.setAttribute("cy", String(point.y * 150));
        handle.setAttribute("r", "2.2");
        handle.setAttribute("tabindex", "0");
        handle.setAttribute("role", "slider");
        handle.setAttribute("aria-label", `Điểm góc ${pointIndex + 1} của khung ${index + 1}`);
        handle.classList.add("frame-corner-handle");
        handle.addEventListener("keydown", (event) => {
          const directions = {
            ArrowLeft: { x: -0.01, y: 0 },
            ArrowRight: { x: 0.01, y: 0 },
            ArrowUp: { x: 0, y: -0.01 },
            ArrowDown: { x: 0, y: 0.01 },
          };
          if (!directions[event.key]) return;
          event.preventDefault();
          const activePoint = panel.points[pointIndex];
          activePoint.x += directions[event.key].x;
          activePoint.y += directions[event.key].y;
          clampFramePoint(activePoint);
          renderFrameEditor();
        });
        handle.addEventListener("pointerdown", (event) => {
          event.preventDefault();
          event.stopPropagation();
          const bounds = preview.getBoundingClientRect();
          const move = (moveEvent) => {
            const activePoint = panel.points[pointIndex];
            activePoint.x = (moveEvent.clientX - bounds.left) / bounds.width;
            activePoint.y = (moveEvent.clientY - bounds.top) / bounds.height;
            clampFramePoint(activePoint);
            renderFrameEditor();
          };
          const stop = () => {
            window.removeEventListener("pointermove", move);
            window.removeEventListener("pointerup", stop);
          };
          window.addEventListener("pointermove", move);
          window.addEventListener("pointerup", stop, { once: true });
        });
        svg.appendChild(handle);
      });
    }
  });
  if (!frameDraft.panels.length) {
    const emptyText = document.createElementNS(svgNamespace, "text");
    emptyText.setAttribute("x", "50");
    emptyText.setAttribute("y", "75");
    emptyText.setAttribute("text-anchor", "middle");
    emptyText.classList.add("frame-editor-empty-text");
    emptyText.textContent = "Chưa có frame";
    svg.appendChild(emptyText);
  }
  preview.appendChild(svg);
  syncFramePanelInputs();
}

function openFrameCreator() {
  const activeLayer = getActiveLayer();
  editingFrameLayer = activeLayer?.frameLayout ? activeLayer : null;
  if (editingFrameLayer) {
    frameDraft = {
      templateId: editingFrameLayer.frameLayout.templateId || "custom",
      panels: cloneFramePanels(editingFrameLayer.frameLayout.panels),
      lineWidth: editingFrameLayer.frameLayout.lineWidth || 4,
      color: editingFrameLayer.frameLayout.color || "#111111",
      cornerStyle: editingFrameLayer.frameLayout.cornerStyle || "sharp",
      cornerRadius: editingFrameLayer.frameLayout.cornerRadius || 18,
    };
  } else {
    const firstTemplate = FRAME_TEMPLATES[0];
    frameDraft = {
      templateId: firstTemplate.id,
      panels: cloneFramePanels(firstTemplate.panels),
      lineWidth: 4,
      color: "#111111",
      cornerStyle: "sharp",
      cornerRadius: 18,
    };
  }
  selectedFramePanel = 0;
  document.querySelectorAll(".frame-template-option").forEach((button) => {
    const active = button.dataset.templateId === frameDraft.templateId;
    button.classList.toggle("active", active);
    button.setAttribute("aria-pressed", active ? "true" : "false");
  });
  document.getElementById("frameLineWidth").value = frameDraft.lineWidth;
  document.getElementById("frameColor").value = frameDraft.color;
  document.getElementById("frameCornerRadius").value = frameDraft.cornerRadius;
  document.getElementById("frameCornerRadiusControl").hidden =
    frameDraft.cornerStyle !== "rounded";
  document.querySelectorAll(".frame-corner-style-btn").forEach((button) => {
    const active = button.dataset.cornerStyle === frameDraft.cornerStyle;
    button.classList.toggle("active", active);
    button.setAttribute("aria-pressed", active ? "true" : "false");
  });
  document.getElementById("frameDialogTitle").textContent = editingFrameLayer
    ? "Chỉnh sửa frame"
    : "Tạo frame manga";
  document.getElementById("btnApplyFrame").textContent = editingFrameLayer
    ? "Cập nhật frame"
    : "Tạo layer frame";
  renderFrameEditor();
  document.getElementById("frameCreatorDialog").showModal();
}

function applyFrameDraft() {
  if (!frameDraft?.panels.length) return;
  let layer = editingFrameLayer;
  if (!layer) {
    frameLayerCounter++;
    layer = addLayer(`Frame ${frameLayerCounter}`, false);
    activeLayerIndex = layers.length - 1;
  }
  layer.frameLayout = {
    templateId: frameDraft.templateId,
    panels: cloneFramePanels(frameDraft.panels),
    lineWidth: Number(frameDraft.lineWidth),
    color: frameDraft.color,
    cornerStyle: frameDraft.cornerStyle,
    cornerRadius: Number(frameDraft.cornerRadius),
  };
  renderFrameLayer(layer);
  renderLayerList();
  pushHistoryEntry(editingFrameLayer ? "Chỉnh sửa frame" : "Tạo frame manga");
  document.getElementById("frameCreatorDialog").close();
}

function toggleDrawToolMenu(trigger, menu) {
  if (typeof menu.showPopover !== "function") {
    menu.hidden = !menu.hidden;
    return;
  }
  if (menu.matches(":popover-open")) {
    menu.hidePopover();
    return;
  }
  const triggerRect = trigger.getBoundingClientRect();
  menu.showPopover();
  const menuRect = menu.getBoundingClientRect();
  const opensFromToolbar = Boolean(trigger.closest(".left-toolbar"));
  const preferredLeft = opensFromToolbar ? triggerRect.right + 8 : triggerRect.left;
  const preferredTop = opensFromToolbar ? triggerRect.top : triggerRect.bottom + 8;
  menu.style.left = `${Math.max(8, Math.min(window.innerWidth - menuRect.width - 8, preferredLeft))}px`;
  menu.style.top = `${Math.max(8, Math.min(window.innerHeight - menuRect.height - 8, preferredTop))}px`;
}

function closeDrawToolMenu(menu) {
  if (typeof menu.hidePopover === "function") {
    if (menu.matches(":popover-open")) menu.hidePopover();
    return;
  }
  menu.hidden = true;
}

function updateToolPropertyVisibility(tool) {
  const topBar = document.getElementById("topPropertyBar");
  const groups = {
    size: document.getElementById("penSize")?.closest(".prop-group"),
    opacity: document.getElementById("penOpacity")?.closest(".prop-group"),
    shape: document.getElementById("shapeFillGroup"),
    text: document.getElementById("textSizeGroup"),
  };
  const visibilityMap = {
    pencil: ["size", "opacity"],
    brush: ["size", "opacity"],
    eraser: ["size", "opacity"],
    bucket: [],
    line: ["size", "opacity"],
    rect: ["size", "opacity", "shape"],
    oval: ["size", "opacity", "shape"],
    text: ["opacity", "text"],
  };
  const visibleGroups = visibilityMap[tool] || [];
  Object.entries(groups).forEach(([name, group]) => {
    if (group) group.style.display = visibleGroups.includes(name) ? "flex" : "none";
  });
  topBar.querySelectorAll(".prop-divider").forEach((divider) => {
    divider.style.display = "none";
  });
  topBar.hidden = visibleGroups.length === 0;
}

function setupDrawingEnhancements() {
  const shapeGroup = document.getElementById("shapeFillGroup");
  if (shapeGroup) {
    shapeGroup.querySelector(".prop-label").textContent = "Hình dạng";
    const outlineButton = shapeGroup.querySelector('[data-fillmode="outline"]');
    const filledButton = shapeGroup.querySelector('[data-fillmode="filled"]');
    outlineButton.textContent = "Chỉ viền";
    outlineButton.title = "Vẽ đường bao, phần bên trong giữ trong suốt";
    filledButton.textContent = "Tô kín";
    filledButton.title = "Tô toàn bộ phần bên trong bằng màu đang chọn";
  }

  const paletteBar = document.querySelector(".color-palette-bar");
  const paletteCommandActions = document.createElement("div");
  paletteCommandActions.className = "palette-command-actions";
  paletteBar.appendChild(paletteCommandActions);

  const colorMenuTrigger = document.createElement("button");
  colorMenuTrigger.type = "button";
  colorMenuTrigger.id = "btnColorMenu";
  colorMenuTrigger.className = "palette-command-button color-menu-trigger";
  colorMenuTrigger.innerHTML =
    '<span class="color-menu-preview" aria-hidden="true"></span><span>Color</span><i class="fa-solid fa-chevron-down menu-caret"></i>';
  colorMenuTrigger.setAttribute("aria-haspopup", "menu");
  colorMenuTrigger.style.setProperty("--active-color", getFgColor());

  const colorMenu = document.createElement("div");
  colorMenu.id = "colorToolMenu";
  colorMenu.className = "draw-tool-menu color-tool-menu";
  colorMenu.setAttribute("popover", "auto");
  colorMenu.setAttribute("role", "menu");
  const colorMenuLabel = document.createElement("div");
  colorMenuLabel.className = "color-menu-label";
  colorMenuLabel.textContent = "Màu nhanh";
  colorMenu.appendChild(colorMenuLabel);
  const colorGrid = document.createElement("div");
  colorGrid.className = "color-menu-grid";
  paletteBar.querySelectorAll(".palette-swatch").forEach((swatch) => {
    swatch.setAttribute("role", "menuitem");
    swatch.tabIndex = 0;
    swatch.title = `Chọn màu ${swatch.dataset.color}`;
    swatch.addEventListener("click", () => {
      colorMenuTrigger.style.setProperty("--active-color", swatch.dataset.color);
      closeDrawToolMenu(colorMenu);
    });
    swatch.addEventListener("keydown", (event) => {
      if (event.key !== "Enter" && event.key !== " ") return;
      event.preventDefault();
      swatch.click();
    });
    colorGrid.appendChild(swatch);
  });
  colorMenu.appendChild(colorGrid);
  document.body.appendChild(colorMenu);
  colorMenuTrigger.addEventListener("click", () =>
    toggleDrawToolMenu(colorMenuTrigger, colorMenu),
  );
  paletteCommandActions.appendChild(colorMenuTrigger);
  paletteBar.querySelector(".palette-label")?.remove();

  const frameMenuTrigger = document.createElement("button");
  frameMenuTrigger.type = "button";
  frameMenuTrigger.id = "btnFrameMenu";
  frameMenuTrigger.className = "palette-command-button";
  frameMenuTrigger.innerHTML = '<i class="fa-solid fa-border-all"></i><span>Frame</span><i class="fa-solid fa-chevron-down menu-caret"></i>';
  frameMenuTrigger.setAttribute("aria-haspopup", "menu");

  const frameMenu = document.createElement("div");
  frameMenu.id = "frameToolMenu";
  frameMenu.className = "draw-tool-menu frame-tool-menu";
  frameMenu.setAttribute("popover", "auto");
  frameMenu.setAttribute("role", "menu");
  const frameButton = document.createElement("button");
  frameButton.type = "button";
  frameButton.id = "btnFrameCreator";
  frameButton.className = "draw-tool-menu-item";
  frameButton.setAttribute("role", "menuitem");
  frameButton.innerHTML = '<i class="fa-solid fa-border-all"></i> Tạo frame';
  frameButton.addEventListener("click", () => {
    closeDrawToolMenu(frameMenu);
    openFrameCreator();
  });
  frameMenu.appendChild(frameButton);
  const addFrameButton = document.createElement("button");
  addFrameButton.type = "button";
  addFrameButton.id = "btnAddFrameDirect";
  addFrameButton.className = "draw-tool-menu-item";
  addFrameButton.setAttribute("role", "menuitem");
  addFrameButton.innerHTML = '<i class="fa-solid fa-plus"></i> Thêm frame';
  addFrameButton.title = "Thêm ngay một frame mới vào trang hiện tại";
  addFrameButton.addEventListener("click", () => {
    closeDrawToolMenu(frameMenu);
    addFrameDirectlyToPage();
  });
  frameMenu.appendChild(addFrameButton);
  document.body.appendChild(frameMenu);
  frameMenuTrigger.addEventListener("click", () =>
    toggleDrawToolMenu(frameMenuTrigger, frameMenu),
  );
  paletteCommandActions.appendChild(frameMenuTrigger);

  const eyedropButton = document.getElementById("toolEyedrop");
  eyedropButton.classList.add("palette-action-button");
  eyedropButton.setAttribute("aria-label", "Lấy màu");
  eyedropButton.title = "Lấy màu từ canvas";
  paletteCommandActions.appendChild(eyedropButton);
  const colorPicker = document.querySelector(".lt-swatch-wrap");
  colorPicker.classList.add("palette-color-picker");
  colorPicker.title = "Màu nét vẽ";
  paletteCommandActions.appendChild(colorPicker);
  document.getElementById("penColor").addEventListener("input", (event) => {
    colorMenuTrigger.style.setProperty("--active-color", event.target.value);
  });

  const firstSelectionButton = document.querySelector('.lt-btn[data-tool="select"]');
  const selectionMenuTrigger = document.createElement("button");
  selectionMenuTrigger.type = "button";
  selectionMenuTrigger.id = "toolSelectionMenu";
  selectionMenuTrigger.className = "lt-btn selection-menu-trigger";
  selectionMenuTrigger.title = "Chọn vùng";
  selectionMenuTrigger.setAttribute("aria-haspopup", "menu");
  selectionMenuTrigger.innerHTML =
    '<i class="fa-solid fa-object-group"></i><i class="fa-solid fa-chevron-right selection-menu-caret"></i>';
  firstSelectionButton.parentNode.insertBefore(selectionMenuTrigger, firstSelectionButton);

  const selectionMenu = document.createElement("div");
  selectionMenu.id = "selectionToolMenu";
  selectionMenu.className = "draw-tool-menu selection-tool-menu";
  selectionMenu.setAttribute("popover", "auto");
  selectionMenu.setAttribute("role", "menu");
  const selectionOptions = [
    { tool: "select", label: "Chữ nhật", shape: '<rect x="3" y="5" width="14" height="10" rx="1" />' },
    { tool: "select-oval", label: "Oval", shape: '<ellipse cx="10" cy="10" rx="7" ry="5" />' },
    { tool: "select-free", label: "Tự do", shape: '<path d="M4 13c-2-4 2-8 6-7 5-2 8 2 6 6-1 3-5 4-8 3-2 0-3 1-2 3" /><path d="M6 18l3-1" />' },
    { tool: "select-triangle", label: "Tam giác", shape: '<path d="M10 3 18 17H2Z" />' },
    { tool: "select-diamond", label: "Hình thoi", shape: '<path d="m10 2 8 8-8 8-8-8Z" />' },
    { tool: "select-hexagon", label: "Lục giác", shape: '<path d="m6 3 8 0 4 7-4 7H6l-4-7Z" />' },
  ];
  selectionOptions.forEach((option) => {
    let button = document.querySelector(`.lt-btn[data-tool="${option.tool}"]`);
    if (!button) {
      button = document.createElement("button");
      button.type = "button";
      button.className = "lt-btn";
      button.dataset.tool = option.tool;
    }
    button.classList.add("draw-tool-menu-item", "selection-menu-item");
    button.setAttribute("role", "menuitemradio");
    button.setAttribute("aria-checked", "false");
    button.title = `Chọn vùng ${option.label.toLowerCase()}`;
    button.innerHTML = `<svg class="selection-shape-icon" viewBox="0 0 20 20" aria-hidden="true">${option.shape}</svg><span>${option.label}</span>`;
    button.addEventListener("click", () => closeDrawToolMenu(selectionMenu));
    selectionMenu.appendChild(button);
  });
  document.body.appendChild(selectionMenu);
  selectionMenuTrigger.addEventListener("click", () =>
    toggleDrawToolMenu(selectionMenuTrigger, selectionMenu),
  );

  const addLayerButton = document.getElementById("btnAddLayer");
  const layerActions = document.createElement("div");
  layerActions.className = "layer-primary-actions";
  addLayerButton.parentNode.insertBefore(layerActions, addLayerButton);
  layerActions.appendChild(addLayerButton);
  const showAllButton = document.createElement("button");
  showAllButton.type = "button";
  showAllButton.id = "btnShowAllLayers";
  showAllButton.className = "header-btn layer-show-all-button";
  showAllButton.setAttribute("aria-pressed", "false");
  showAllButton.addEventListener("pointerdown", (event) => {
    if (event.button !== 0) return;
    event.preventDefault();
    beginLayerPeek();
  });
  showAllButton.addEventListener("keydown", (event) => {
    if (event.key !== " " && event.key !== "Enter") return;
    event.preventDefault();
    beginLayerPeek();
  });
  showAllButton.addEventListener("keyup", (event) => {
    if (event.key === " " || event.key === "Enter") endLayerPeek();
  });
  showAllButton.addEventListener("click", (event) => event.preventDefault());
  window.addEventListener("pointerup", endLayerPeek);
  window.addEventListener("pointercancel", endLayerPeek);
  window.addEventListener("blur", endLayerPeek);
  layerActions.appendChild(showAllButton);

  const undoButton = document.getElementById("toolUndo");
  const redoButton = document.createElement("button");
  redoButton.type = "button";
  redoButton.className = "lt-btn";
  redoButton.id = "toolRedo";
  redoButton.title = "Làm lại (Ctrl+Y / Ctrl+Shift+Z)";
  redoButton.innerHTML = '<i class="fa-solid fa-rotate-right"></i>';
  undoButton.insertAdjacentElement("afterend", redoButton);

  const clearButton = document.getElementById("toolClear");
  const dividerBeforeHistory = undoButton.previousElementSibling;
  const dividerAfterHistory = clearButton.nextElementSibling;
  if (dividerBeforeHistory?.classList.contains("lt-divider")) {
    dividerBeforeHistory.remove();
  }
  if (dividerAfterHistory?.classList.contains("lt-divider")) {
    dividerAfterHistory.remove();
  }
  const paletteHistoryActions = document.createElement("div");
  paletteHistoryActions.className = "palette-history-actions";
  undoButton.setAttribute("aria-label", "Hoàn tác");
  redoButton.setAttribute("aria-label", "Làm lại");
  clearButton.setAttribute("aria-label", "Xóa nội dung layer hiện tại");
  [undoButton, redoButton, clearButton].forEach((button) => {
    button.classList.add("palette-action-button");
    paletteHistoryActions.appendChild(button);
  });
  paletteCommandActions.appendChild(paletteHistoryActions);

  const dialog = document.createElement("dialog");
  dialog.id = "frameCreatorDialog";
  dialog.className = "frame-creator-dialog";
  dialog.innerHTML = `
    <div class="frame-dialog-header">
      <div>
        <h2 id="frameDialogTitle">Tạo frame manga</h2>
        <p>Chọn bố cục, kéo cả khung để di chuyển hoặc kéo từng điểm góc để tạo hình tứ giác tự do.</p>
      </div>
      <button type="button" class="frame-dialog-close" aria-label="Đóng">&times;</button>
    </div>
    <div class="frame-template-grid" aria-label="8 mẫu frame manga"></div>
    <div class="frame-editor-layout">
      <div class="frame-editor-stage">
        <div class="frame-editor-preview" id="frameEditorPreview" aria-label="Xem trước frame"></div>
      </div>
      <div class="frame-editor-controls">
        <div class="frame-editor-heading">
          <h3 id="frameSelectedLabel">Khung 1</h3>
          <div class="frame-editor-heading-actions">
            <button type="button" class="header-btn frame-add-inside-button" id="btnAddFrameInEditor">
              <i class="fa-solid fa-plus"></i> Thêm frame
            </button>
            <button type="button" class="header-btn frame-delete-button" id="btnDeleteFrameInEditor">
              <i class="fa-solid fa-trash"></i> Xóa frame
            </button>
          </div>
        </div>
        <div class="frame-corner-grid" id="frameCornerGrid"></div>
        <div class="frame-style-control">
          <span>Góc cạnh</span>
          <div class="frame-corner-style-toggle" role="group" aria-label="Kiểu góc frame">
            <button type="button" class="frame-corner-style-btn active" data-corner-style="sharp" aria-pressed="true">Góc nhọn</button>
            <button type="button" class="frame-corner-style-btn" data-corner-style="rounded" aria-pressed="false">Bo tròn</button>
          </div>
        </div>
        <label class="frame-style-control" id="frameCornerRadiusControl" hidden>Độ bo góc
          <input id="frameCornerRadius" type="range" min="2" max="50" value="18">
        </label>
        <label class="frame-style-control">Độ dày viền
          <input id="frameLineWidth" type="range" min="1" max="20" value="4">
        </label>
        <label class="frame-style-control">Màu viền
          <input id="frameColor" type="color" value="#111111">
        </label>
      </div>
    </div>
    <div class="frame-dialog-actions">
      <button type="button" class="header-btn" id="btnCancelFrame">Hủy</button>
      <button type="button" class="header-btn primary" id="btnApplyFrame">Tạo layer frame</button>
    </div>`;
  document.body.appendChild(dialog);

  const templateGrid = dialog.querySelector(".frame-template-grid");
  FRAME_TEMPLATES.forEach((template, index) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "frame-template-option";
    button.dataset.templateId = template.id;
    button.setAttribute("aria-pressed", "false");
    const thumbnail = template.panels
      .map(
        (panel) =>
          `<span style="left:${panel.x * 100}%;top:${panel.y * 100}%;width:${panel.w * 100}%;height:${panel.h * 100}%"></span>`,
      )
      .join("");
    button.innerHTML = `<span class="frame-template-preview">${thumbnail}</span><span>${index + 1}. ${template.name}</span>`;
    button.addEventListener("click", () => setFrameTemplate(template.id));
    templateGrid.appendChild(button);
  });

  document.getElementById("btnAddFrameInEditor").addEventListener("click", () => {
    if (!frameDraft) return;
    frameDraft.templateId = "custom";
    frameDraft.panels.push(createDefaultFramePanel(frameDraft.panels.length));
    selectedFramePanel = frameDraft.panels.length - 1;
    document.querySelectorAll(".frame-template-option").forEach((button) => {
      button.classList.remove("active");
      button.setAttribute("aria-pressed", "false");
    });
    renderFrameEditor();
  });
  document.getElementById("btnDeleteFrameInEditor").addEventListener("click", () => {
    if (!frameDraft?.panels[selectedFramePanel]) return;
    frameDraft.templateId = "custom";
    frameDraft.panels.splice(selectedFramePanel, 1);
    selectedFramePanel = Math.max(
      0,
      Math.min(selectedFramePanel, frameDraft.panels.length - 1),
    );
    document.querySelectorAll(".frame-template-option").forEach((button) => {
      button.classList.remove("active");
      button.setAttribute("aria-pressed", "false");
    });
    renderFrameEditor();
  });
  document.querySelectorAll(".frame-corner-style-btn").forEach((button) => {
    button.addEventListener("click", () => {
      if (!frameDraft) return;
      frameDraft.cornerStyle = button.dataset.cornerStyle;
      document.querySelectorAll(".frame-corner-style-btn").forEach((item) => {
        const active = item === button;
        item.classList.toggle("active", active);
        item.setAttribute("aria-pressed", active ? "true" : "false");
      });
      document.getElementById("frameCornerRadiusControl").hidden =
        frameDraft.cornerStyle !== "rounded";
      renderFrameEditor();
    });
  });
  document.getElementById("frameCornerRadius").addEventListener("input", (event) => {
    if (frameDraft) {
      frameDraft.cornerRadius = Number(event.target.value);
      renderFrameEditor();
    }
  });
  document.getElementById("frameLineWidth").addEventListener("input", (event) => {
    if (frameDraft) {
      frameDraft.lineWidth = Number(event.target.value);
      renderFrameEditor();
    }
  });
  document.getElementById("frameColor").addEventListener("input", (event) => {
    if (frameDraft) {
      frameDraft.color = event.target.value;
      renderFrameEditor();
    }
  });
  dialog.querySelector(".frame-dialog-close").addEventListener("click", () => dialog.close());
  document.getElementById("btnCancelFrame").addEventListener("click", () => dialog.close());
  document.getElementById("btnApplyFrame").addEventListener("click", applyFrameDraft);
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) dialog.close();
  });

  updateToolPropertyVisibility(currentTool);
  updateLayerActionButtons();
}

setupDrawingEnhancements();

function snapshotAllLayers() {
    // Lưu trạng thái canvas của layer đang được chọn để phục vụ hoàn tác.
  return {
    activeLayerIndex,
    layerCounter,
    numberedLayerCounter,
    frameLayerCounter,
    layers: layers.map((layer) => ({
      id: layer.id,
      name: layer.name,
      visible: layer.visible,
      opacity: layer.opacity,
      isBase: Boolean(layer.isBase),
      frameLayout: layer.frameLayout
        ? {
            ...layer.frameLayout,
            panels: cloneFramePanels(layer.frameLayout.panels),
          }
        : null,
      data: layer.canvas.toDataURL(),
    })),
  };
}

function updateHistoryButtons() {
  const undoButton = document.getElementById("toolUndo");
  const redoButton = document.getElementById("toolRedo");
  if (undoButton) undoButton.disabled = history.length <= 1;
  if (redoButton) redoButton.disabled = redoStack.length === 0;
}

function restoreHistorySnapshot(snapshot) {
  clearEditableShapeSelection();
  layers.forEach((layer) => layer.canvas.remove());
  layers = [];
  layerCounter = snapshot.layerCounter;
  numberedLayerCounter = snapshot.numberedLayerCounter;
  frameLayerCounter = snapshot.frameLayerCounter || 0;

  snapshot.layers.forEach((savedLayer, index) => {
    const canvasEl = createLayerCanvas(index + 1);
    canvasStack.insertBefore(canvasEl, document.getElementById("regionSelectBox"));
    const ctx = canvasEl.getContext("2d");
    const layer = {
      id: savedLayer.id,
      name: savedLayer.name,
      canvas: canvasEl,
      ctx,
      visible: savedLayer.visible,
      opacity: savedLayer.opacity,
      isBase: savedLayer.isBase,
      frameLayout: savedLayer.frameLayout
        ? {
            ...savedLayer.frameLayout,
            panels: cloneFramePanels(savedLayer.frameLayout.panels),
          }
        : null,
    };
    canvasEl.style.display = layer.visible ? "block" : "none";
    canvasEl.style.opacity = layer.opacity / 100;
    layers.push(layer);

    const image = new Image();
    image.onload = () => ctx.drawImage(image, 0, 0);
    image.src = savedLayer.data;
  });

  activeLayerIndex = Math.min(snapshot.activeLayerIndex, layers.length - 1);
  renderLayerList();
  updateHistoryButtons();
}

function pushHistoryEntry(label) {
  const snapshot = snapshotAllLayers();
  snapshot.label = label;
  history.push(snapshot);
  if (history.length > 30) history.shift();
  redoStack = [];
  const item = document.createElement("div");
  item.className = "history-item current";
  item.textContent = label;
  if (historyListEl.parentNode) {
    // Chỉ bỏ class "current" ở node trước đó (O(1)) thay vì quét TOÀN BỘ node
    // (tới 30 lần đổi class mỗi nét). Việc đổi class hàng loạt trước đây tạo ra
    // hàng chục DOM mutation mỗi nét, khiến MutationObserver dùng chung (ui-alert)
    // bị dội và chạy liftFloatingModals nặng → lag khi vẽ.
    if (currentHistoryItem) currentHistoryItem.classList.remove("current");
    historyListEl.appendChild(item);
    currentHistoryItem = item;
    // Cắt DOM danh sách lịch sử về đúng cap 30 như mảng `history` (không phình DOM).
    while (historyListEl.children.length > 30) {
      historyListEl.removeChild(historyListEl.firstChild);
    }
    historyListEl.scrollTop = historyListEl.scrollHeight;
  }
  updateHistoryButtons();
}
pushHistoryEntry("T\u1ea1o canvas m\u1edbi");

function getPos(e) {
  const rect = inputLayer.getBoundingClientRect();
  const src = e.touches ? e.touches[0] : e;
  return {
    x: (src.clientX - rect.left) * (CANVAS_W / rect.width),
    y: (src.clientY - rect.top) * (CANVAS_H / rect.height),
  };
}

function getFgColor() {
  return document.getElementById("penColor").value;
}

function getOpacity() {
  return document.getElementById("penOpacity").value / 100;
}

function getSize() {
  return document.getElementById("penSize").value;
}

function constrainShapeEnd(start, end, shiftKey) {
  if (!shiftKey || !start || !end) return end;
  const dx = end.x - start.x;
  const dy = end.y - start.y;
  if (currentTool === "line") {
    const distance = Math.hypot(dx, dy);
    const angle = Math.round(Math.atan2(dy, dx) / (Math.PI / 4)) * (Math.PI / 4);
    return {
      x: start.x + Math.cos(angle) * distance,
      y: start.y + Math.sin(angle) * distance,
    };
  }
  const size = Math.max(Math.abs(dx), Math.abs(dy));
  return {
    x: start.x + Math.sign(dx || 1) * size,
    y: start.y + Math.sign(dy || 1) * size,
  };
}

function ensureActiveLayerVisible() {
  const layer = getActiveLayer();
  if (!layer || layer.visible) return;
  layer.visible = true;
  layer.canvas.style.display = "block";
  renderLayerList();
}

function drawFreehandStart(ctx, point, tool) {
  const activeLayer = getActiveLayer();
  const radius = Math.max(0.5, Number(getSize()) / 2);
  ctx.save();
  ctx.globalAlpha = tool === "brush" ? getOpacity() * 0.6 : getOpacity();
  ctx.globalCompositeOperation =
    tool === "eraser" && !activeLayer.isBase ? "destination-out" : "source-over";
  ctx.fillStyle = tool === "eraser" ? "#ffffff" : getFgColor();
  ctx.beginPath();
  ctx.arc(point.x, point.y, radius, 0, Math.PI * 2);
  ctx.fill();
  ctx.restore();
  ctx.beginPath();
  ctx.moveTo(point.x, point.y);
}

// Công cụ đổ màu theo vùng liên thông.
function floodFill(ctxTarget, startX, startY, fillColorHex) {
  startX = Math.floor(startX);
  startY = Math.floor(startY);
  const imgData = ctxTarget.getImageData(0, 0, CANVAS_W, CANVAS_H);
  const data = imgData.data;

  const fillColor = hexToRgba(fillColorHex);
  const startIdx = (startY * CANVAS_W + startX) * 4;
  const targetColor = [
    data[startIdx],
    data[startIdx + 1],
    data[startIdx + 2],
    data[startIdx + 3],
  ];

  if (colorsMatch(targetColor, fillColor)) return;

  const stack = [[startX, startY]];
  const visited = new Uint8Array(CANVAS_W * CANVAS_H);

  while (stack.length) {
    const [x, y] = stack.pop();
    if (x < 0 || x >= CANVAS_W || y < 0 || y >= CANVAS_H) continue;
    const pos = y * CANVAS_W + x;
    if (visited[pos]) continue;

    const idx = pos * 4;
    const currentColor = [
      data[idx],
      data[idx + 1],
      data[idx + 2],
      data[idx + 3],
    ];
    if (!colorsMatch(currentColor, targetColor)) continue;

    visited[pos] = 1;
    data[idx] = fillColor[0];
    data[idx + 1] = fillColor[1];
    data[idx + 2] = fillColor[2];
    data[idx + 3] = fillColor[3];

    stack.push([x + 1, y], [x - 1, y], [x, y + 1], [x, y - 1]);
  }
  ctxTarget.putImageData(imgData, 0, 0);
}

function colorsMatch(a, b) {
  return (
    Math.abs(a[0] - b[0]) < 12 &&
    Math.abs(a[1] - b[1]) < 12 &&
    Math.abs(a[2] - b[2]) < 12 &&
    Math.abs(a[3] - b[3]) < 12
  );
}

function hexToRgba(hex, alpha) {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return [r, g, b, alpha !== undefined ? Math.round(alpha * 255) : 255];
}

// ========================================================
// CÔNG CỤ CHỮ: chỉnh sửa trực tiếp với thanh điều khiển nổi.
// ========================================================
let activeTextBox = null;

const FONTS = [
  "Arial",
  "Georgia",
  "Courier New",
  "Impact",
  "Comic Sans MS",
  "Trebuchet MS",
];

function createTextBox(x, y) {
  if (activeTextBox) commitTextBox();

  const viewport = document.getElementById("canvasViewport");
  const canvasRect = document
    .getElementById("canvasStack")
    .getBoundingClientRect();
  const viewportRect = viewport.getBoundingClientRect();

  const scaleX = canvasRect.width / CANVAS_W;
  const scaleY = canvasRect.height / CANVAS_H;

  const screenX = canvasRect.left - viewportRect.left + x * scaleX;
  const screenY = canvasRect.top - viewportRect.top + y * scaleY;

  const initFontSize =
    parseInt(document.getElementById("textSize").value) * scaleX;
  const color = getFgColor();

  // Khung bao quanh vùng chữ đang chỉnh sửa.
  const box = document.createElement("div");
  box.style.cssText = `
        position: absolute;
        left: ${screenX}px;
        top: ${screenY}px;
        width: 200px;
        min-height: 40px;
        border: 2px solid #3d8eff;
        border-radius: 2px;
        cursor: move;
        z-index: 1000;
        box-sizing: border-box;
        background: transparent;
    `;

  // Thanh công cụ chữ: font, đậm, nghiêng, căn lề và xóa.
  const toolbar = document.createElement("div");
  toolbar.style.cssText = `
        position: absolute;
        top: -38px;
        left: 0;
        display: flex;
        align-items: center;
        gap: 4px;
        background: #1e1e1e;
        border-radius: 6px;
        padding: 4px 8px;
        white-space: nowrap;
        z-index: 20;
        box-shadow: 0 2px 8px rgba(0,0,0,0.4);
        pointer-events: auto;
    `;

  // Bộ chọn font chữ.
  const fontSelect = document.createElement("select");
  fontSelect.style.cssText = `
        background:#2a2a2a; color:#fff; border:1px solid #555;
        border-radius:4px; font-size:11px; padding:2px 4px; cursor:pointer;
    `;
  FONTS.forEach((f) => {
    const opt = document.createElement("option");
    opt.value = f;
    opt.textContent = f;
    opt.style.fontFamily = f;
    fontSelect.appendChild(opt);
  });

  // Nút chữ đậm.
  const btnBold = makeToolbarBtn("<b>B</b>", "font-weight:bold;");
  let isBold = false;
  btnBold.addEventListener("mousedown", (e) => {
    e.preventDefault();
    e.stopPropagation();
    isBold = !isBold;
    ta.style.fontWeight = isBold ? "bold" : "normal";
    btnBold.style.background = isBold ? "#3d8eff" : "#2a2a2a";
  });

  // Nút chữ nghiêng.
  const btnItalic = makeToolbarBtn("<i>I</i>", "font-style:italic;");
  let isItalic = false;
  btnItalic.addEventListener("mousedown", (e) => {
    e.preventDefault();
    e.stopPropagation();
    isItalic = !isItalic;
    ta.style.fontStyle = isItalic ? "italic" : "normal";
    btnItalic.style.background = isItalic ? "#3d8eff" : "#2a2a2a";
  });

  // Các nút căn lề.
  let textAlign = "left";
  const alignBtns = ["left", "center", "right"].map((align) => {
    const icons = { left: "\u2261", center: "\u2630", right: "\u2263" };
    const btn = makeToolbarBtn(icons[align]);
    btn.title = align;
    btn.addEventListener("mousedown", (e) => {
      e.preventDefault();
      e.stopPropagation();
      textAlign = align;
      ta.style.textAlign = align;
      alignBtns.forEach((b) => (b.style.background = "#2a2a2a"));
      btn.style.background = "#3d8eff";
    });
    if (align === "left") btn.style.background = "#3d8eff";
    return btn;
  });

  // Nút xóa vùng chữ.
  const btnDelete = makeToolbarBtn("\u2715", "color:#ff5c5c;");
  btnDelete.addEventListener("mousedown", (e) => {
    e.preventDefault();
    e.stopPropagation();
    box.remove();
    activeTextBox = null;
    document.removeEventListener("mousedown", onOutsideClick);
    document.removeEventListener("mousemove", onMouseMove);
    document.removeEventListener("mouseup", onMouseUp);
  });

  toolbar.appendChild(fontSelect);
  toolbar.appendChild(btnBold);
  toolbar.appendChild(btnItalic);
  alignBtns.forEach((b) => toolbar.appendChild(b));
  toolbar.appendChild(btnDelete);
  box.appendChild(toolbar);

  // Ô nhập nội dung chữ.
  const ta = document.createElement("textarea");
  ta.style.cssText = `
        width: 100%;
        min-height: 36px;
        background: transparent;
        border: none;
        outline: none;
        resize: none;
        font-size: ${initFontSize}px;
        color: ${color};
        font-family: Arial, sans-serif;
        cursor: text;
        overflow: hidden;
        line-height: 1.2;
        padding: 4px;
        box-sizing: border-box;
        text-align: left;
    `;
  ta.placeholder = "Nh\u1eadp ch\u1eef...";

  // Đồng bộ font chữ đang chọn.
  fontSelect.addEventListener("change", () => {
    ta.style.fontFamily = fontSelect.value;
  });

  // Tự động điều chỉnh chiều cao.
  ta.addEventListener("input", () => {
    ta.style.height = "auto";
    ta.style.height = ta.scrollHeight + "px";
    box.style.height = "auto";
  });

  // Tám điểm điều chỉnh kích thước.
  const handlePositions = [
    { id: "nw", style: "top:-6px;left:-6px;cursor:nw-resize;" },
    {
      id: "n",
      style: "top:-6px;left:50%;transform:translateX(-50%);cursor:n-resize;",
    },
    { id: "ne", style: "top:-6px;right:-6px;cursor:ne-resize;" },
    {
      id: "e",
      style: "top:50%;right:-6px;transform:translateY(-50%);cursor:e-resize;",
    },
    { id: "se", style: "bottom:-6px;right:-6px;cursor:se-resize;" },
    {
      id: "s",
      style: "bottom:-6px;left:50%;transform:translateX(-50%);cursor:s-resize;",
    },
    { id: "sw", style: "bottom:-6px;left:-6px;cursor:sw-resize;" },
    {
      id: "w",
      style: "top:50%;left:-6px;transform:translateY(-50%);cursor:w-resize;",
    },
  ];

  handlePositions.forEach((h) => {
    const handle = document.createElement("div");
    handle.dataset.handle = h.id;
    handle.style.cssText = `
            position:absolute;width:12px;height:12px;
            background:#fff;border:2px solid #3d8eff;
            border-radius:2px;z-index:10;${h.style}
        `;
    box.appendChild(handle);
  });

  // Điểm điều chỉnh góc xoay.
  const rotateHandle = document.createElement("div");
  rotateHandle.style.cssText = `
        position:absolute;bottom:-28px;left:50%;
        transform:translateX(-50%);
        width:16px;height:16px;
        background:#3d8eff;border-radius:50%;
        cursor:grab;z-index:10;
        display:flex;align-items:center;justify-content:center;
        color:white;font-size:10px;user-select:none;
    `;
  rotateHandle.innerHTML = "\u21bb";
  box.appendChild(rotateHandle);

  box.appendChild(ta);
  viewport.style.position = "relative";
  viewport.appendChild(box);
  ta.focus();

  let rotation = 0;
  activeTextBox = {
    box,
    ta,
    fontSelect,
    scaleX,
    scaleY,
    rotation,
    originX: x,
    originY: y,
    isBold: () => isBold,
    isItalic: () => isItalic,
    getAlign: () => textAlign,
  };

  // Kéo để di chuyển vùng chữ.
  let dragging = false,
    dragStartX,
    dragStartY,
    boxStartX,
    boxStartY;

  box.addEventListener("mousedown", (e) => {
    if (
      e.target.dataset.handle ||
      e.target === rotateHandle ||
      e.target === ta ||
      toolbar.contains(e.target)
    )
      return;
    dragging = true;
    dragStartX = e.clientX;
    dragStartY = e.clientY;
    boxStartX = parseInt(box.style.left);
    boxStartY = parseInt(box.style.top);
    e.preventDefault();
  });

  // Thay đổi kích thước vùng chữ.
  let resizing = false,
    activeHandle = null;
  let resizeStartX,
    resizeStartY,
    resizeStartW,
    resizeStartH,
    resizeStartL,
    resizeStartT,
    resizeStartFontSize;

  box.querySelectorAll("[data-handle]").forEach((h) => {
    h.addEventListener("mousedown", (e) => {
      resizing = true;
      activeHandle = h.dataset.handle;
      resizeStartX = e.clientX;
      resizeStartY = e.clientY;
      resizeStartW = box.offsetWidth;
      resizeStartH = box.offsetHeight;
      resizeStartL = parseInt(box.style.left);
      resizeStartT = parseInt(box.style.top);
      resizeStartFontSize = parseFloat(ta.style.fontSize);
      e.preventDefault();
      e.stopPropagation();
    });
  });

  // Xoay vùng chữ.
  let rotating = false;
  rotateHandle.addEventListener("mousedown", (e) => {
    rotating = true;
    e.preventDefault();
    e.stopPropagation();
  });

  function onMouseMove(e) {
    if (dragging) {
      box.style.left = boxStartX + e.clientX - dragStartX + "px";
      box.style.top = boxStartY + e.clientY - dragStartY + "px";
    }

    if (resizing) {
      const dx = e.clientX - resizeStartX;
      const dy = e.clientY - resizeStartY;

      if (activeHandle.includes("e")) {
        box.style.width = Math.max(80, resizeStartW + dx) + "px";
      }
      if (activeHandle.includes("w")) {
        const newW = Math.max(80, resizeStartW - dx);
        box.style.width = newW + "px";
        box.style.left = resizeStartL + resizeStartW - newW + "px";
      }
    // Co giãn cỡ chữ theo tỷ lệ khi kéo điểm ở góc.
      if (activeHandle === "se" || activeHandle === "sw") {
        const scale = Math.max(0.1, (resizeStartH + dy) / resizeStartH);
        ta.style.fontSize = Math.max(8, resizeStartFontSize * scale) + "px";
      }
      if (activeHandle === "ne" || activeHandle === "nw") {
        const newH = Math.max(40, resizeStartH - dy);
        box.style.top = resizeStartT + resizeStartH - newH + "px";
        const scale = Math.max(0.1, newH / resizeStartH);
        ta.style.fontSize = Math.max(8, resizeStartFontSize * scale) + "px";
      }
      if (activeHandle === "n") {
        const newH = Math.max(40, resizeStartH - dy);
        box.style.top = resizeStartT + resizeStartH - newH + "px";
      }
      if (activeHandle === "s") {
      // Chiều cao được tính tự động theo nội dung.
      }
    }

    if (rotating) {
      const rect = box.getBoundingClientRect();
      const cx = rect.left + rect.width / 2;
      const cy = rect.top + rect.height / 2;
      const angle =
        (Math.atan2(e.clientY - cy, e.clientX - cx) * 180) / Math.PI + 90;
      rotation = angle;
      activeTextBox.rotation = angle;
      box.style.transform = `rotate(${angle}deg)`;
    }
  }

  function onMouseUp() {
    dragging = false;
    resizing = false;
    rotating = false;
  }

  document.addEventListener("mousemove", onMouseMove);
  document.addEventListener("mouseup", onMouseUp);

  // Nhấn ra ngoài để ghi nội dung chữ vào canvas.
  setTimeout(() => {
    document.addEventListener("mousedown", onOutsideClick);
  }, 100);

  function onOutsideClick(e) {
    if (!box.contains(e.target)) {
      commitTextBox();
      document.removeEventListener("mousedown", onOutsideClick);
      document.removeEventListener("mousemove", onMouseMove);
      document.removeEventListener("mouseup", onMouseUp);
    }
  }
}

// Hàm hỗ trợ tạo nút trên thanh công cụ chữ.
function makeToolbarBtn(html, extraStyle = "") {
  const btn = document.createElement("button");
  btn.innerHTML = html;
  btn.style.cssText = `
        background:#2a2a2a; color:#fff; border:1px solid #555;
        border-radius:4px; font-size:12px; padding:2px 7px;
        cursor:pointer; min-width:24px; line-height:1.4;
        ${extraStyle}
    `;
  return btn;
}

  // Ghi nội dung chữ đã chỉnh sửa lên canvas.
function commitTextBox() {
  if (!activeTextBox) return;
  const {
    box,
    ta,
    fontSelect,
    scaleX,
    scaleY,
    rotation,
    isBold,
    isItalic,
    getAlign,
  } = activeTextBox;
  const text = ta.value.trim();

  if (text) {
    const viewport = document.getElementById("canvasViewport");
    const canvasEl = document.getElementById("canvasStack");
    const canvasRect = canvasEl.getBoundingClientRect();
    const viewportRect = viewport.getBoundingClientRect();

    const boxLeft = parseInt(box.style.left);
    const boxTop = parseInt(box.style.top);
    const boxW = box.offsetWidth;

  // Tọa độ của hộp chữ và canvas đều được tính theo khung nhìn.
    const canvasOffX = canvasRect.left - viewportRect.left;
    const canvasOffY = canvasRect.top - viewportRect.top;
    const canvasX = (boxLeft - canvasOffX) / scaleX;
    const canvasY = (boxTop - canvasOffY) / scaleY;
    const fontSize = parseFloat(ta.style.fontSize) / scaleX;
    const canvasBoxW = boxW / scaleX;
    const fontFamily = fontSelect ? fontSelect.value : "Arial";
    const fontWeight = isBold() ? "bold" : "normal";
    const fontStyle = isItalic() ? "italic" : "normal";
    const align = getAlign();

    const layerCtx = getActiveLayer().ctx;
    layerCtx.save();

    if (rotation !== 0) {
      const cx = canvasX + canvasBoxW / 2;
      const cy = canvasY + fontSize / 2;
      layerCtx.translate(cx, cy);
      layerCtx.rotate((rotation * Math.PI) / 180);
      layerCtx.translate(-cx, -cy);
    }

    layerCtx.globalAlpha = getOpacity();
    layerCtx.fillStyle = ta.style.color;
    layerCtx.font = `${fontStyle} ${fontWeight} ${fontSize}px ${fontFamily}`;
    layerCtx.textBaseline = "top";
    layerCtx.textAlign = "left"; // Luôn căn trái tại canvas; vị trí hiển thị được tính ở bên dưới.

    const padding = 4;
    const maxW = canvasBoxW - padding * 2;

  // Tính các dòng trước khi vẽ để tự động xuống dòng.
    const allLines = [];
    text.split("\n").forEach((paragraph) => {
      if (paragraph.trim() === "") {
        allLines.push("");
        return;
      }
      const words = paragraph.split(" ");
      let current = "";
      words.forEach((word) => {
        const test = current ? current + " " + word : word;
        if (layerCtx.measureText(test).width > maxW && current) {
          allLines.push(current);
          current = word;
        } else {
          current = test;
        }
      });
      if (current) allLines.push(current);
    });

  // Vẽ từng dòng theo vị trí căn lề đã chọn.
    let lineY = canvasY + padding;
    allLines.forEach((line) => {
      let drawX;
      if (align === "center") {
        const lineW = layerCtx.measureText(line).width;
        drawX = canvasX + (canvasBoxW - lineW) / 2;
      } else if (align === "right") {
        const lineW = layerCtx.measureText(line).width;
        drawX = canvasX + canvasBoxW - lineW - padding;
      } else {
        drawX = canvasX + padding;
      }
      layerCtx.fillText(line, drawX, lineY);
      lineY += fontSize * 1.2;
    });

    layerCtx.restore(); // Khôi phục trạng thái canvas sau khi hoàn tất.
    pushHistoryEntry("Chèn chữ");
  }

  box.remove();
  activeTextBox = null;
}

function placeTextAt(pos) {
  createTextBox(pos.x, pos.y);
}

// Các sự kiện chuột chính của canvas.
inputLayer.addEventListener("pointerdown", (e) => {
  if (isSelectTool(currentTool)) return;
  clearEditableShapeSelection();
  ensureActiveLayerVisible();
  const p = getPos(e);
  const layerCtx = getActiveLayer().ctx;

  if (
    currentTool === "pencil" ||
    currentTool === "brush" ||
    currentTool === "eraser"
  ) {
    isDrawing = true;
    drawFreehandStart(layerCtx, p, currentTool);
  } else if (currentTool === "bucket") {
    floodFill(
      layerCtx,
      p.x,
      p.y,
      currentTool === "eraser" ? "#ffffff" : getFgColor(),
    );
    pushHistoryEntry("Đổ màu");
  } else if (
    currentTool === "line" ||
    currentTool === "rect" ||
    currentTool === "oval"
  ) {
    shapeBasePixels = layerCtx.getImageData(0, 0, CANVAS_W, CANVAS_H);
    shapeStart = p;
    isDrawing = true;
  } else if (currentTool === "text") {
    placeTextAt(p);
  }
});

inputLayer.addEventListener("pointermove", (e) => {
  if (!isDrawing) return;
  let p = getPos(e);
  const layerCtx = getActiveLayer().ctx;

  if (
    currentTool === "pencil" ||
    currentTool === "brush" ||
    currentTool === "eraser"
  ) {
    layerCtx.lineWidth = getSize();
    layerCtx.lineCap = currentTool === "brush" ? "round" : "round";
    layerCtx.lineJoin = "round";
    layerCtx.globalAlpha =
      currentTool === "brush" ? getOpacity() * 0.6 : getOpacity();
    layerCtx.strokeStyle = currentTool === "eraser" ? "rgba(0,0,0,1)" : getFgColor();
    if (currentTool === "eraser") {
    // Tẩy bằng cách vẽ màu trắng đè lên để không ảnh hưởng layer nền.
      layerCtx.globalCompositeOperation = getActiveLayer().isBase
        ? "source-over"
        : "destination-out";
      if (getActiveLayer().isBase) layerCtx.strokeStyle = "#ffffff";
    } else {
      layerCtx.globalCompositeOperation = "source-over";
    }
    layerCtx.lineTo(p.x, p.y);
    layerCtx.stroke();
    // Reset path về điểm hiện tại: mỗi lần di chuột chỉ vẽ ĐOẠN MỚI thay vì
    // stroke lại toàn bộ path đã tích luỹ (O(n²) → O(n)). Đây là nguyên nhân
    // chính gây lag khi kéo nét dài; đồng thời tránh nét alpha<1 bị đậm dần do
    // blend chồng nhiều lần. Pixel đã vẽ vẫn còn trên canvas nên nhìn y hệt.
    layerCtx.beginPath();
    layerCtx.moveTo(p.x, p.y);
  } else if (
    currentTool === "line" ||
    currentTool === "rect" ||
    currentTool === "oval"
  ) {
      // Vẽ trước hình dạng trên layer nhập liệu rồi xóa khi hoàn tất.
    p = constrainShapeEnd(shapeStart, p, e.shiftKey);
    redrawShapePreview(shapeStart, p);
  }
});

window.addEventListener("pointerup", (e) => {
  if (!isDrawing) return;
  drawDirty = true; // tác giả/trợ lý vừa vẽ → có thay đổi chưa lưu
  const layerCtx = getActiveLayer().ctx;

  if (
    currentTool === "pencil" ||
    currentTool === "brush" ||
    currentTool === "eraser"
  ) {
    layerCtx.globalAlpha = 1;
    layerCtx.globalCompositeOperation = "source-over";
    pushHistoryEntry(
      currentTool === "eraser"
        ? "Tẩy"
        : currentTool === "brush"
          ? "Vẽ cọ mềm"
          : "Vẽ nét",
    );
  } else if (
    (currentTool === "line" ||
      currentTool === "rect" ||
      currentTool === "oval") &&
    shapeStart
  ) {
    const p = constrainShapeEnd(shapeStart, getPos(e), e.shiftKey);
    const completedTool = currentTool;
    const completedStart = { ...shapeStart };
    commitShape(shapeStart, p);
    clearShapePreview();
    pushHistoryEntry("Vẽ hình " + currentTool);
    selectEditableShape(completedTool, completedStart, p, shapeBasePixels);
    shapeBasePixels = null;
    shapeStart = null;
  }
  isDrawing = false;
});

// Xem trước hình dạng trên layer nhập liệu trước khi ghi vào layer thật.
const previewCtx = inputLayer.getContext("2d");

function clearShapePreview() {
  previewCtx.clearRect(0, 0, CANVAS_W, CANVAS_H);
}

function drawShapeOnContext(ctxTarget, start, end, shapeStyle = null) {
  const tool = shapeStyle?.tool || currentTool;
  const color = shapeStyle?.color || getFgColor();
  const size = shapeStyle?.size ?? getSize();
  const opacity = shapeStyle?.opacity ?? getOpacity();
  const fillMode = shapeStyle?.fillMode || shapeFillMode;
  ctxTarget.lineWidth = size;
  ctxTarget.strokeStyle = color;
  ctxTarget.fillStyle = color;
  ctxTarget.globalAlpha = opacity;
  ctxTarget.beginPath();

  if (tool === "line") {
    ctxTarget.moveTo(start.x, start.y);
    ctxTarget.lineTo(end.x, end.y);
    ctxTarget.stroke();
  } else if (tool === "rect") {
    const w = end.x - start.x,
      h = end.y - start.y;
    if (fillMode === "filled") ctxTarget.fillRect(start.x, start.y, w, h);
    else ctxTarget.strokeRect(start.x, start.y, w, h);
  } else if (tool === "oval") {
    const cx = (start.x + end.x) / 2,
      cy = (start.y + end.y) / 2;
    const rx = Math.abs(end.x - start.x) / 2,
      ry = Math.abs(end.y - start.y) / 2;
    ctxTarget.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2);
    if (fillMode === "filled") ctxTarget.fill();
    else ctxTarget.stroke();
  }
  ctxTarget.globalAlpha = 1;
}

function redrawShapePreview(start, end) {
  clearShapePreview();
  drawShapeOnContext(previewCtx, start, end);
}

function commitShape(start, end) {
  const layerCtx = getActiveLayer().ctx;
  drawShapeOnContext(layerCtx, start, end);
}

function clearEditableShapeSelection() {
  editableShape = null;
  if (shapeTransformOverlay) shapeTransformOverlay.remove();
  shapeTransformOverlay = null;
}

function clampCanvasPoint(point) {
  return {
    x: Math.max(0, Math.min(CANVAS_W, point.x)),
    y: Math.max(0, Math.min(CANVAS_H, point.y)),
  };
}

function renderEditableShape() {
  if (!editableShape) return;
  const layer = layers.find((item) => item.id === editableShape.layerId);
  if (!layer) {
    clearEditableShapeSelection();
    return;
  }
  layer.ctx.putImageData(editableShape.basePixels, 0, 0);
  drawShapeOnContext(
    layer.ctx,
    editableShape.start,
    editableShape.end,
    editableShape.style,
  );
  updateShapeTransformOverlay();
}

function resizeEditableShape(handle, point) {
  if (!editableShape) return;
  const next = clampCanvasPoint(point);
  if (editableShape.style.tool === "line") {
    editableShape[handle === "start" ? "start" : "end"] = next;
    renderEditableShape();
    return;
  }

  const minimumSize = 4;
  const start = { ...editableShape.start };
  const end = { ...editableShape.end };
  if (handle.includes("n")) start.y = Math.min(next.y, end.y - minimumSize);
  if (handle.includes("s")) end.y = Math.max(next.y, start.y + minimumSize);
  if (handle.includes("w")) start.x = Math.min(next.x, end.x - minimumSize);
  if (handle.includes("e")) end.x = Math.max(next.x, start.x + minimumSize);
  editableShape.start = clampCanvasPoint(start);
  editableShape.end = clampCanvasPoint(end);
  renderEditableShape();
}

function beginShapeHandleDrag(event) {
  event.preventDefault();
  event.stopPropagation();
  const handle = event.currentTarget.dataset.handle;
  let didMove = false;
  const move = (moveEvent) => {
    didMove = true;
    resizeEditableShape(handle, getPos(moveEvent));
  };
  const stop = () => {
    window.removeEventListener("pointermove", move);
    window.removeEventListener("pointerup", stop);
    if (editableShape && didMove) {
      const label = editableShape.style.tool === "line" ? "đường thẳng" : editableShape.style.tool === "rect" ? "hình chữ nhật" : "hình tròn";
      pushHistoryEntry(`Điều chỉnh kích thước ${label}`);
    }
  };
  event.currentTarget.setPointerCapture?.(event.pointerId);
  window.addEventListener("pointermove", move);
  window.addEventListener("pointerup", stop, { once: true });
}

function createShapeHandle(handle, label) {
  const element = document.createElement("button");
  element.type = "button";
  element.className = "shape-transform-handle";
  element.dataset.handle = handle;
  element.setAttribute("aria-label", label);
  element.addEventListener("pointerdown", beginShapeHandleDrag);
  return element;
}

function updateShapeTransformOverlay() {
  if (!editableShape || !shapeTransformOverlay) return;
  const { start, end, style } = editableShape;
  if (style.tool === "line") {
    const dx = end.x - start.x;
    const dy = end.y - start.y;
    const length = Math.hypot(dx, dy);
    const angle = Math.atan2(dy, dx) * (180 / Math.PI);
    const guide = shapeTransformOverlay.querySelector(".shape-line-guide");
    guide.style.left = `${start.x}px`;
    guide.style.top = `${start.y}px`;
    guide.style.width = `${length}px`;
    guide.style.transform = `rotate(${angle}deg)`;
    const startHandle = shapeTransformOverlay.querySelector('[data-handle="start"]');
    const endHandle = shapeTransformOverlay.querySelector('[data-handle="end"]');
    startHandle.style.left = `${start.x}px`;
    startHandle.style.top = `${start.y}px`;
    endHandle.style.left = `${end.x}px`;
    endHandle.style.top = `${end.y}px`;
    return;
  }

  shapeTransformOverlay.style.left = `${start.x}px`;
  shapeTransformOverlay.style.top = `${start.y}px`;
  shapeTransformOverlay.style.width = `${Math.max(4, end.x - start.x)}px`;
  shapeTransformOverlay.style.height = `${Math.max(4, end.y - start.y)}px`;
}

function selectEditableShape(tool, start, end, basePixels) {
  clearEditableShapeSelection();
  const normalizedStart = tool === "line"
    ? { ...start }
    : { x: Math.min(start.x, end.x), y: Math.min(start.y, end.y) };
  const normalizedEnd = tool === "line"
    ? { ...end }
    : { x: Math.max(start.x, end.x), y: Math.max(start.y, end.y) };
  editableShape = {
    layerId: getActiveLayer().id,
    start: normalizedStart,
    end: normalizedEnd,
    basePixels,
    style: {
      tool,
      color: getFgColor(),
      size: getSize(),
      opacity: getOpacity(),
      fillMode: shapeFillMode,
    },
  };

  shapeTransformOverlay = document.createElement("div");
  shapeTransformOverlay.className = `shape-transform-overlay shape-transform-${tool}`;
  if (tool === "line") {
    const guide = document.createElement("div");
    guide.className = "shape-line-guide";
    shapeTransformOverlay.appendChild(guide);
    shapeTransformOverlay.appendChild(createShapeHandle("start", "Điều chỉnh điểm đầu đường thẳng"));
    shapeTransformOverlay.appendChild(createShapeHandle("end", "Điều chỉnh điểm cuối đường thẳng"));
  } else {
    [
      ["nw", "Điều chỉnh góc trên trái"],
      ["ne", "Điều chỉnh góc trên phải"],
      ["se", "Điều chỉnh góc dưới phải"],
      ["sw", "Điều chỉnh góc dưới trái"],
    ].forEach(([handle, label]) =>
      shapeTransformOverlay.appendChild(createShapeHandle(handle, label)),
    );
  }
  canvasStack.appendChild(shapeTransformOverlay);
  updateShapeTransformOverlay();
}

// ========================================================
// PHẦN 3: Thanh công cụ, chế độ tô và lấy màu.
// ========================================================
document.querySelectorAll(".lt-btn[data-tool]").forEach((btn) => {
  btn.addEventListener("click", () => {
    const nextTool = btn.dataset.tool;
    if (isSelectTool(currentTool) || isSelectTool(nextTool)) {
      resetRegionSelection();
    }
    clearEditableShapeSelection();
    document
      .querySelectorAll(".lt-btn[data-tool]")
      .forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    currentTool = nextTool;

    const labels = {
      pencil: "Bút vẽ",
      brush: "Cọ mềm",
      eraser: "Tẩy",
      bucket: "Đổ màu",
      line: "Đường thẳng",
      rect: "Hình chữ nhật",
      oval: "Hình tròn/Oval",
      text: "Chèn chữ",
      select: "Chọn vùng",
      "select-oval": "Chọn vùng (oval)",
      "select-free": "Chọn vùng (tự do)",
    };
    Object.assign(labels, {
      "select-triangle": "Ch\u1ecdn v\u00f9ng (tam gi\u00e1c)",
      "select-diamond": "Ch\u1ecdn v\u00f9ng (h\u00ecnh thoi)",
      "select-hexagon": "Ch\u1ecdn v\u00f9ng (l\u1ee5c gi\u00e1c)",
    });
    document.getElementById("statusTool").textContent =
      "Công cụ: " + labels[currentTool];
    const isSelTool = isSelectTool(currentTool);
    document
      .getElementById("toolSelectionMenu")
      ?.classList.toggle("active", isSelTool);
    document
      .querySelectorAll("#selectionToolMenu [data-tool]")
      .forEach((item) =>
        item.setAttribute("aria-checked", String(item.dataset.tool === currentTool)),
      );
    if (isSelTool) {
      const selectionTrigger = document.getElementById("toolSelectionMenu");
      selectionTrigger.title = labels[currentTool];
      selectionTrigger.setAttribute("aria-label", labels[currentTool]);
      document.getElementById("statusRegion").textContent =
        `Kéo trên canvas để ${labels[currentTool].toLowerCase()}`;
    }
    const darkCrosshair =
      "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='20' height='20' viewBox='0 0 20 20'%3E%3Cline x1='10' y1='0' x2='10' y2='20' stroke='black' stroke-width='1.5'/%3E%3Cline x1='0' y1='10' x2='20' y2='10' stroke='black' stroke-width='1.5'/%3E%3C/svg%3E\") 10 10, crosshair";
    const textCursor =
      "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='20' height='24' viewBox='0 0 20 24'%3E%3Ctext x='10' y='18' text-anchor='middle' font-size='20' fill='black'%3EI%3C/text%3E%3C/svg%3E\") 10 12, text";
    inputLayer.style.cursor = isSelTool
      ? "cell"
      : currentTool === "text"
        ? textCursor
        : darkCrosshair;

  // Chỉ hiển thị thuộc tính phù hợp với công cụ đang chọn.
    updateToolPropertyVisibility(currentTool);
  });
});

document.querySelectorAll(".shape-fill-btn").forEach((btn) => {
  btn.addEventListener("click", () => {
    document
      .querySelectorAll(".shape-fill-btn")
      .forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    shapeFillMode = btn.dataset.fillmode;
  });
});

document.addEventListener("keydown", (event) => {
  const target = event.target;
  const isTyping =
    target instanceof HTMLInputElement ||
    target instanceof HTMLTextAreaElement ||
    target instanceof HTMLSelectElement ||
    target?.isContentEditable;
  if (isTyping) return;

  const key = event.key.toLowerCase();
  if ((event.ctrlKey || event.metaKey) && key === "z") {
    event.preventDefault();
    document.getElementById(event.shiftKey ? "toolRedo" : "toolUndo").click();
    return;
  }
  if ((event.ctrlKey || event.metaKey) && key === "y") {
    event.preventDefault();
    document.getElementById("toolRedo").click();
    return;
  }
  if (document.getElementById("frameCreatorDialog")?.open) return;

  const shortcuts = {
    p: "pencil",
    b: "brush",
    e: "eraser",
    g: "bucket",
    l: "line",
    r: "rect",
    o: "oval",
    t: "text",
    m: "select",
  };
  if (shortcuts[key]) {
    event.preventDefault();
    document.querySelector(`.lt-btn[data-tool="${shortcuts[key]}"]`)?.click();
    return;
  }

  if (event.key === "[" || event.key === "]") {
    event.preventDefault();
    const sizeInput = document.getElementById("penSize");
    const nextValue = Number(sizeInput.value) + (event.key === "]" ? 1 : -1);
    sizeInput.value = Math.min(Number(sizeInput.max), Math.max(Number(sizeInput.min), nextValue));
    sizeInput.dispatchEvent(new Event("input", { bubbles: true }));
  } else if (event.key === "Escape") {
    isDrawing = false;
    shapeStart = null;
    shapeBasePixels = null;
    clearEditableShapeSelection();
    resetRegionSelection();
    selectionPointerId = null;
  }
});

document.getElementById("toolEyedrop").addEventListener("click", () => {
  const handler = (e) => {
    const p = getPos(e);
    const layerCtx = getActiveLayer().ctx;
    const pixel = layerCtx.getImageData(p.x, p.y, 1, 1).data;
    const hex =
      "#" +
      [pixel[0], pixel[1], pixel[2]]
        .map((v) => v.toString(16).padStart(2, "0"))
        .join("");
    document.getElementById("penColor").value = hex;
    document.getElementById("penColor").dispatchEvent(new Event("input", { bubbles: true }));
    inputLayer.removeEventListener("click", handler);
  };
  inputLayer.addEventListener("click", handler);
});

// Bảng màu nhanh.
document.querySelectorAll(".palette-swatch").forEach((sw) => {
  sw.addEventListener("click", () => {
    document.getElementById("penColor").value = sw.dataset.color;
  });
});

// Đồng bộ hai chiều giữa thanh kéo và ô nhập số.
bindRangeNumber("penSize", "penSizeValue");
bindRangeNumber("penOpacity", "penOpacityValue");
bindRangeNumber("textSize", "textSizeValue");

// Hoàn tác và xóa nội dung.
document.getElementById("toolUndo").addEventListener("click", () => {
  if (history.length <= 1) return;
  redoStack.push(history.pop());
  restoreHistorySnapshot(history[history.length - 1]);
  if (historyListEl.lastChild)
    historyListEl.removeChild(historyListEl.lastChild);
});

document.getElementById("toolRedo").addEventListener("click", () => {
  if (!redoStack.length) return;
  const snapshot = redoStack.pop();
  history.push(snapshot);
  restoreHistorySnapshot(snapshot);
});

document.getElementById("toolClear").addEventListener("click", async () => {
  const confirmed = await window.showUiConfirm(
    "Toàn bộ nội dung của layer đang chọn sẽ bị xóa.",
    { title: "Xóa nội dung layer", okText: "Xóa layer", cancelText: "Hủy" },
  );
  if (!confirmed) return;
  clearEditableShapeSelection();
  const layer = getActiveLayer();
  layer.ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
  if (layer.isBase) {
    layer.ctx.fillStyle = "#ffffff";
    layer.ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);
  }
  pushHistoryEntry('Xóa layer "' + layer.name + '"');
});

// ========================================================
// PHẦN 4: Chọn vùng.
// ========================================================
let selectionRect = null;
let selectionShape = "rect"; // rect | oval | free | triangle | diamond | hexagon
let selectionPath = []; // Danh sách điểm {x, y} của vùng chọn tự do.
let isSelecting = false;
let selStart = null;
let selectionPointerId = null;
const regionBox = document.getElementById("regionSelectBox");
const canvasViewport = document.getElementById("canvasViewport");

function resetRegionSelection(options = {}) {
  if (
    selectionPointerId !== null &&
    inputLayer.hasPointerCapture?.(selectionPointerId)
  ) {
    inputLayer.releasePointerCapture?.(selectionPointerId);
  }
  selectionPointerId = null;
  isSelecting = false;
  selectionRect = null;
  selectionPath = [];
  selStart = null;
  regionBox.style.display = "none";
  regionBox.style.width = "0";
  regionBox.style.height = "0";
  regionBox.style.borderRadius = "0";
  clearShapePreview();
  if (!options.keepStatus) {
    document.getElementById("statusRegion").textContent = "Vùng AI: Toàn canvas";
  }
}

function isSelectTool(tool) {
  return [
    "select",
    "select-oval",
    "select-free",
    "select-triangle",
    "select-diamond",
    "select-hexagon",
  ].includes(tool);
}

function getSelectionPolygonPoints(shape, rect) {
  const { x, y, w, h } = rect;
  const shapes = {
    triangle: [
      { x: x + w / 2, y },
      { x: x + w, y: y + h },
      { x, y: y + h },
    ],
    diamond: [
      { x: x + w / 2, y },
      { x: x + w, y: y + h / 2 },
      { x: x + w / 2, y: y + h },
      { x, y: y + h / 2 },
    ],
    hexagon: [
      { x: x + w * 0.25, y },
      { x: x + w * 0.75, y },
      { x: x + w, y: y + h / 2 },
      { x: x + w * 0.75, y: y + h },
      { x: x + w * 0.25, y: y + h },
      { x, y: y + h / 2 },
    ],
  };
  return shapes[shape] || [];
}

function traceSelectionPolygon(ctx, shape, rect) {
  const points = getSelectionPolygonPoints(shape, rect);
  if (!points.length) return false;
  ctx.moveTo(points[0].x, points[0].y);
  points.slice(1).forEach((point) => ctx.lineTo(point.x, point.y));
  ctx.closePath();
  return true;
}

function drawPolygonSelectionPreview(shape, rect) {
  clearShapePreview();
  previewCtx.save();
  previewCtx.setLineDash([6, 4]);
  previewCtx.strokeStyle =
    getComputedStyle(document.documentElement)
      .getPropertyValue("--ps-accent")
      .trim() || "#3d8eff";
  previewCtx.fillStyle = "rgba(82, 209, 255, 0.12)";
  previewCtx.lineWidth = 1.5;
  previewCtx.beginPath();
  traceSelectionPolygon(previewCtx, shape, rect);
  previewCtx.fill();
  previewCtx.stroke();
  previewCtx.restore();
}

inputLayer.addEventListener("pointerdown", (e) => {
  if (!isSelectTool(currentTool)) return;
  e.preventDefault();
  resetRegionSelection({ keepStatus: true });
  isSelecting = true;
  selectionPointerId = e.pointerId;
  inputLayer.setPointerCapture?.(e.pointerId);
  selStart = getPos(e);

  if (currentTool === "select") {
    selectionShape = "rect";
    regionBox.style.borderRadius = "0";
    regionBox.style.display = "block";
  } else if (currentTool === "select-oval") {
    selectionShape = "oval";
    regionBox.style.borderRadius = "50%";
    regionBox.style.display = "block";
  } else if (currentTool === "select-free") {
    selectionShape = "free";
    selectionPath = [selStart];
    regionBox.style.display = "none";
  } else {
    selectionShape = currentTool.replace("select-", "");
    regionBox.style.display = "none";
  }
});

inputLayer.addEventListener("pointermove", (e) => {
  if (!isSelectTool(currentTool) || !isSelecting) return;
  const p = getPos(e);

  if (selectionShape !== "free") {
    selectionRect = {
      x: Math.min(selStart.x, p.x),
      y: Math.min(selStart.y, p.y),
      w: Math.abs(p.x - selStart.x),
      h: Math.abs(p.y - selStart.y),
    };

    if (selectionShape === "rect" || selectionShape === "oval") {
      clearShapePreview();
      regionBox.style.left = selectionRect.x + "px";
      regionBox.style.top = selectionRect.y + "px";
      regionBox.style.width = selectionRect.w + "px";
      regionBox.style.height = selectionRect.h + "px";
    } else {
      drawPolygonSelectionPreview(selectionShape, selectionRect);
    }
  } else if (selectionShape === "free") {
    selectionPath.push(p);
// Xem trước vùng chọn tự do trên layer nhập liệu.
    clearShapePreview();
    previewCtx.save();
    previewCtx.setLineDash([6, 4]);
    previewCtx.strokeStyle =
      getComputedStyle(document.documentElement)
        .getPropertyValue("--ps-accent")
        .trim() || "#3d8eff";
    previewCtx.lineWidth = 1.5;
    previewCtx.beginPath();
    previewCtx.moveTo(selectionPath[0].x, selectionPath[0].y);
    for (let i = 1; i < selectionPath.length; i++) {
      previewCtx.lineTo(selectionPath[i].x, selectionPath[i].y);
    }
    previewCtx.stroke();
    previewCtx.restore();
  }
});

window.addEventListener("pointerup", (event) => {
  if (!isSelectTool(currentTool) || !isSelecting) {
    isSelecting = false;
    return;
  }

  if (selectionShape === "free" && selectionPath.length > 2) {
// Khép kín đường bao của vùng chọn tự do.
    clearShapePreview();
    previewCtx.save();
    previewCtx.setLineDash([6, 4]);
    previewCtx.strokeStyle =
      getComputedStyle(document.documentElement)
        .getPropertyValue("--ps-accent")
        .trim() || "#3d8eff";
    previewCtx.lineWidth = 1.5;
    previewCtx.beginPath();
    previewCtx.moveTo(selectionPath[0].x, selectionPath[0].y);
    for (let i = 1; i < selectionPath.length; i++) {
      previewCtx.lineTo(selectionPath[i].x, selectionPath[i].y);
    }
    previewCtx.closePath();
    previewCtx.stroke();
    previewCtx.restore();

// Tính khung bao từ đường đi của vùng chọn.
    let minX = Infinity,
      minY = Infinity,
      maxX = -Infinity,
      maxY = -Infinity;
    for (let i = 0; i < selectionPath.length; i++) {
      const pt = selectionPath[i];
      if (pt.x < minX) minX = pt.x;
      if (pt.y < minY) minY = pt.y;
      if (pt.x > maxX) maxX = pt.x;
      if (pt.y > maxY) maxY = pt.y;
    }
    selectionRect = { x: minX, y: minY, w: maxX - minX, h: maxY - minY };
  }

  const shapeLabels = {
    rect: "ch\u1eef nh\u1eadt",
    oval: "oval",
    free: "t\u1ef1 do",
    triangle: "tam gi\u00e1c",
    diamond: "h\u00ecnh thoi",
    hexagon: "l\u1ee5c gi\u00e1c",
  };
  if (selectionRect && selectionRect.w > 5 && selectionRect.h > 5) {
    document.getElementById("statusRegion").textContent =
      `Vùng AI (${shapeLabels[selectionShape]}): ${Math.round(selectionRect.w)}×${Math.round(selectionRect.h)}px`;
  } else {
    resetRegionSelection({ keepStatus: true });
    document.getElementById("statusRegion").textContent =
      "Vùng chọn quá nhỏ — hãy kéo một vùng lớn hơn trên canvas";
  }
  isSelecting = false;
  if (
    selectionPointerId !== null &&
    inputLayer.hasPointerCapture?.(selectionPointerId)
  ) {
    inputLayer.releasePointerCapture?.(selectionPointerId);
  }
  selectionPointerId = null;
});

inputLayer.addEventListener("pointercancel", () => {
  if (!isSelecting) return;
  resetRegionSelection();
  selectionPointerId = null;
});

// ========================================================
// PHẦN 5: Thu phóng canvas bằng CSS transform.
// ========================================================
let zoomLevel = 1;

function applyZoom() {
  canvasStack.style.transform = `scale(${zoomLevel})`;
  canvasStack.style.transformOrigin = "center center";
  document.getElementById("zoomValue").textContent =
    Math.round(zoomLevel * 100) + "%";
}

document.getElementById("zoomIn").addEventListener("click", () => {
  zoomLevel = Math.min(zoomLevel + 0.25, 3);
  applyZoom();
});
document.getElementById("zoomOut").addEventListener("click", () => {
  zoomLevel = Math.max(zoomLevel - 0.25, 0.25);
  applyZoom();
});
document.getElementById("zoomReset").addEventListener("click", () => {
  zoomLevel = 1;
  applyZoom();
});
canvasViewport.addEventListener("wheel", (e) => {
  if (!e.ctrlKey) return;
  e.preventDefault();
  zoomLevel = Math.min(
    Math.max(zoomLevel + (e.deltaY < 0 ? 0.1 : -0.1), 0.25),
    3,
  );
  applyZoom();
});

// ========================================================
// PHẦN 6: Chat AI hỗ trợ vẽ manga.
// ========================================================
let currentChatImageFile = null;

async function loadChatHistory() {
  try {
    const res = await fetch("/api/chat/history");
    const history = await res.json();
    const list = document.getElementById("chatHistoryList");
    if (!list) return;
    list.innerHTML = "";
    history.forEach((msg) => {
      appendChatMessage(msg.role, msg.content, msg.isImage, msg);
    });
    list.scrollTop = list.scrollHeight;
  } catch (e) {
    console.error("Lỗi tải lịch sử chat", e);
  }
}

function previewChatImage(event) {
  const file = event.target.files[0];
  if (file) {
    currentChatImageFile = file;
    const reader = new FileReader();
    reader.onload = (e) => {
      document.getElementById("chatImgPrevSrc").src = e.target.result;
      document.getElementById("chatImagePreview").style.display = "block";
    };
    reader.readAsDataURL(file);
  }
}

function removeChatImage() {
  currentChatImageFile = null;
  const input = document.getElementById("chatImageInput");
  if (input) input.value = "";
  const preview = document.getElementById("chatImagePreview");
  if (preview) preview.style.display = "none";
}

function getChatDrawContext() {
  const saveBtn = document.getElementById("btnSavePage");
  const actionBtn = document.getElementById("btnEditSubmission");
  const body = document.body;
  const roleText = (document.querySelector(".brand-stack .name")?.textContent || "").trim();
  const scriptParagraphs = Array.from(
    document.querySelectorAll("#scriptModalOverlay .u-inline-style-036"),
  );
  const frameNotes = Array.from(document.querySelectorAll("#scriptModalOverlay li"))
    .map((li) => li.textContent.trim())
    .filter(Boolean)
    .join("\n");
  const currentPageId = saveBtn?.dataset.pageId || actionBtn?.dataset.pageId || "";
  const pageCard = currentPageId
    ? document.querySelector(`.page-grid-item[data-page-id="${CSS.escape(String(currentPageId))}"]`)
    : null;

  return {
    pageId: currentPageId,
    submissionId:
      actionBtn?.dataset.submissionId || saveBtn?.dataset.submissionId || "",
    role: body?.dataset.role || roleText || "",
    pageType: pageCard?.dataset.pageType || "",
    chapterScript:
      window._chapterScript || scriptParagraphs[0]?.textContent.trim() || "",
    pageScript:
      document.getElementById("pageActionScriptText")?.textContent.trim() ||
      pageCard?.dataset.pageScript ||
      scriptParagraphs[1]?.textContent.trim() ||
      "",
    frameNotes,
    selectedRegion: selectionRect
      ? JSON.stringify({
          shape: selectionShape || "rect",
          x: Math.round(selectionRect.x),
          y: Math.round(selectionRect.y),
          w: Math.round(selectionRect.w),
          h: Math.round(selectionRect.h),
        })
      : "",
  };
}

async function canvasSnapshotFile() {
  if (currentChatImageFile) return currentChatImageFile;
  if (typeof flattenAllLayers !== "function") return null;

  const flat = flattenAllLayers();
  const hasArtwork = hasNonWhitePixels(flat);
  if (!hasArtwork) return null;

  return new Promise((resolve) => {
    flat.toBlob((blob) => {
      if (!blob) {
        resolve(null);
        return;
      }
      resolve(new File([blob], "canvas.png", { type: "image/png" }));
    }, "image/png");
  });
}

async function selectionMaskFile() {
  if (!selectionRect || selectionRect.w <= 5 || selectionRect.h <= 5) return null;

  const maskCanvas = document.createElement("canvas");
  maskCanvas.width = CANVAS_W;
  maskCanvas.height = CANVAS_H;
  const ctx = maskCanvas.getContext("2d");
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);
  ctx.globalCompositeOperation = "destination-out";
  ctx.beginPath();

  if (selectionShape === "oval") {
    ctx.ellipse(
      selectionRect.x + selectionRect.w / 2,
      selectionRect.y + selectionRect.h / 2,
      Math.max(1, selectionRect.w / 2),
      Math.max(1, selectionRect.h / 2),
      0,
      0,
      Math.PI * 2,
    );
    ctx.fill();
  } else if (selectionShape === "free" && selectionPath.length > 2) {
    ctx.moveTo(selectionPath[0].x, selectionPath[0].y);
    for (let i = 1; i < selectionPath.length; i++) {
      ctx.lineTo(selectionPath[i].x, selectionPath[i].y);
    }
    ctx.closePath();
    ctx.fill();
  } else if (["triangle", "diamond", "hexagon"].includes(selectionShape)) {
    traceSelectionPolygon(ctx, selectionShape, selectionRect);
    ctx.fill();
  } else {
    ctx.fillRect(selectionRect.x, selectionRect.y, selectionRect.w, selectionRect.h);
  }

  ctx.globalCompositeOperation = "source-over";

  return new Promise((resolve) => {
    maskCanvas.toBlob((blob) => {
      if (!blob) {
        resolve(null);
        return;
      }
      resolve(new File([blob], "selection-mask.png", { type: "image/png" }));
    }, "image/png");
  });
}

function hasNonWhitePixels(canvas) {
  try {
    const ctx = canvas.getContext("2d");
    const data = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
    for (let i = 0; i < data.length; i += 16) {
      const alpha = data[i + 3];
      if (alpha > 0 && (data[i] < 248 || data[i + 1] < 248 || data[i + 2] < 248)) {
        return true;
      }
    }
  } catch (e) {
    return true;
  }
  return false;
}

async function sendChatMessage() {
  const input = document.getElementById("chatTextInput");
  const msg = input.value.trim();
  if (!msg && !currentChatImageFile) return;

  let userDisplay = msg;
  if (currentChatImageFile) userDisplay += " [Đã đính kèm ảnh]";
  appendChatMessage("user", userDisplay, false);

  input.value = "";
  const btnSend = document.getElementById("btnSendChat");
  btnSend.disabled = true;
  btnSend.innerHTML = "Đang xử lý...";

  try {
    const formData = new FormData();
    formData.append("message", msg);

    const imageForAi = await canvasSnapshotFile();
    if (imageForAi) {
      formData.append("image", imageForAi);
      const maskForAi = currentChatImageFile ? null : await selectionMaskFile();
      if (maskForAi) formData.append("mask", maskForAi);
    }

    const context = getChatDrawContext();
    Object.entries(context).forEach(([key, value]) => {
      if (value) formData.append(key, value);
    });

    removeChatImage();

    const res = await fetch("/api/chat/message", {
      method: "POST",
      body: formData,
    });
    const data = await res.json();

    if (data.status === "rate_limited") {
      appendChatMessage("ai", data.content || data.message || "Gemini dang bi gioi han tam thoi. Thu lai sau it giay.", false, data);
      return;
    }

    if (data.status === "error") {
      appendChatMessage("ai", data.content || data.message || "Không thể xử lý yêu cầu AI.", false, data);
      return;
    }

    appendChatMessage("ai", data.content, data.type === "image", data);

    if (data.type === "image" && data.applyMode !== "reference_only") {
      addAiImageAsNewLayer(data.content, data.intent || "Result");
    }
  } catch (e) {
    appendChatMessage("ai", "Lỗi: " + e.message, false);
  } finally {
    btnSend.disabled = false;
    btnSend.innerHTML = '<i class="fa-solid fa-paper-plane"></i>';
  }
}

function appendChatMessage(role, content, isImage, meta = {}) {
  const list = document.getElementById("chatHistoryList");
  if (!list) return;
  const msgDiv = document.createElement("div");
  msgDiv.className = "manga-chat-message";

  if (role === "user") {
    msgDiv.classList.add("is-user");
    msgDiv.textContent = content;
  } else {
    msgDiv.classList.add("is-ai");

    if (isImage) {
      const img = document.createElement("img");
      img.src = ensureImageDataUrl(content);
      img.style.maxWidth = "100%";
      img.style.borderRadius = "4px";
      img.style.marginBottom = "8px";
      msgDiv.appendChild(img);

      const note = document.createElement("div");
      note.style.fontSize = "12px";
      note.style.opacity = "0.85";
      note.style.marginBottom = "8px";
      note.textContent = meta.applyMode === "reference_only"
        ? "Ảnh AI đang ở chế độ tham khảo."
        : "Đã thêm ảnh AI vào layer mới trên canvas.";
      msgDiv.appendChild(note);

      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "btn-submit";
      btn.style.width = "100%";
      btn.style.padding = "4px";
      btn.textContent = "Thêm lại vào layer mới";
      btn.addEventListener("click", () => addAiImageAsNewLayer(content, meta.intent || "Result"));
      msgDiv.appendChild(btn);
    } else {
      msgDiv.innerHTML = (content || "").replace(/\n/g, "<br/>");
    }
  }

  list.appendChild(msgDiv);
  list.scrollTop = list.scrollHeight;
}

function addAiImageAsNewLayer(base64DataUrl, intentLabel) {
  const img = new Image();
  img.onload = () => {
    const label = String(intentLabel || "Result")
      .replace(/_/g, " ")
      .replace(/\b\w/g, (ch) => ch.toUpperCase());
    const layer = addLayer("AI - " + label, false);
    activeLayerIndex = layers.length - 1;
    layer.ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
    layer.ctx.drawImage(img, 0, 0, CANVAS_W, CANVAS_H);
    renderLayerList();
    pushHistoryEntry("AI Chat: Th\u00eam layer " + label);
  };
  img.src = ensureImageDataUrl(base64DataUrl);
}

function applyChatImageToCanvas(base64DataUrl) {
  addAiImageAsNewLayer(base64DataUrl, "Result");
}

function ensureImageDataUrl(value) {
  if (!value) return "";
  return value.startsWith("data:image") ? value : "data:image/png;base64," + value;
}

document.addEventListener("DOMContentLoaded", loadChatHistory);

// ========================================================
// PHẦN 7: Gộp các layer thành một ảnh để gửi tới API AI.
// ========================================================
function flattenAllLayers() {
  const flat = document.createElement("canvas");
  flat.width = CANVAS_W;
  flat.height = CANVAS_H;
  const flatCtx = flat.getContext("2d");
  layers.forEach((layer) => {
    if (!layer.visible) return;
    flatCtx.globalAlpha = layer.opacity / 100;
    flatCtx.drawImage(layer.canvas, 0, 0);
  });
  flatCtx.globalAlpha = 1;
  return flat;
}

// Lưu trang
// ========================================================
// PHẦN 8: Lưu trang và bản nộp.
// ========================================================
const btnSavePage = document.getElementById("btnSavePage");

// Lưu trang
if (btnSavePage) {
  btnSavePage.addEventListener("click", async () => {
    const pageId = btnSavePage.dataset.pageId; // Đọc dữ liệu tại thời điểm nhấn, không đọc khi tải tệp JavaScript.
    if (!pageId) {
      alert("Không tìm thấy pageId, vui lòng tải lại trang!");
      return;
    }

    const base64 = flattenAllLayers().toDataURL("image/png");

    btnSavePage.disabled = true;
    btnSavePage.innerHTML = "Đang lưu...";

    try {
      const res = await fetch(`/api/page/${pageId}/savefile`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ imageBase64: base64 }),
      });
      const data = await res.json();
      if (data.status === "success") {
        drawDirty = false; // đã lưu → không còn thay đổi chưa lưu
        btnSavePage.innerHTML = "Đã lưu!";
        setTimeout(() => {
          btnSavePage.innerHTML = "Lưu trang";
        }, 2000);
      } else {
        alert("" + data.message);
        btnSavePage.innerHTML = "Lưu trang";
      }
    } catch (err) {
      alert("Lỗi: " + err.message);
      btnSavePage.innerHTML = "Lưu trang";
    } finally {
      btnSavePage.disabled = false;
    }
  });
}
const btnSubmitSubmission = document.getElementById("btnSubmitSubmission");

if (btnSubmitSubmission) {
  btnSubmitSubmission.addEventListener("click", async () => {
    const submissionId = btnSubmitSubmission.dataset.submissionId;

    const base64 = flattenAllLayers().toDataURL("image/png");

    btnSubmitSubmission.disabled = true;

    btnSubmitSubmission.innerHTML = "Đang nộp...";

    try {
      const res = await fetch(`/api/submission/${submissionId}/savefile`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          imageBase64: base64,
        }),
      });

      const data = await res.json();

      if (data.status === "success") {
        drawDirty = false; // đã nộp/lưu → không còn thay đổi chưa lưu
        btnSubmitSubmission.innerHTML = "Đã nộp";

        setTimeout(() => {
          if (data.redirectUrl) {
            window.location.href = data.redirectUrl;
          }
        }, 1000);
      } else {
        alert("" + data.message);

        btnSubmitSubmission.innerHTML = "Nộp bài";
      }
    } catch (err) {
      alert("" + err.message);

      btnSubmitSubmission.innerHTML = "Nộp bài";
    } finally {
      btnSubmitSubmission.disabled = false;
    }
  });
}

// Tách tác vụ trang trong IIFE để tránh xung đột biến toàn cục.
// Mở cửa sổ tác vụ và lưu đúng page/submission đang thao tác.
(() => {
  const actionButton = document.getElementById("btnEditSubmission");
  const btnModalLoadPage = document.getElementById("btnModalLoadPage");
  const btnModalDownload = document.getElementById("btnModalDownload");
  const btnModalSaveArtwork = document.getElementById("btnModalSaveArtwork");
  const btnConfirmEdit = document.getElementById("btnConfirmEdit");
  const btnLoadPageLegacy = document.getElementById("btnLoadPage");
  const btnDownloadLegacy = document.getElementById("btnDownload");

  function getDrawContext() {
    const pageId =
      btnSavePage?.dataset.pageId || actionButton?.dataset.pageId || "";
    const submissionId =
      actionButton?.dataset.submissionId ||
      btnSavePage?.dataset.submissionId ||
      "";
    const returnUrl =
      actionButton?.dataset.returnUrl || btnSavePage?.dataset.returnUrl || "";

    return { pageId, submissionId, returnUrl };
  }

  function setButtonLoading(button, loading, label) {
    if (!button) return;
    button.disabled = loading;
    if (loading) {
      button.dataset.originalHtml = button.innerHTML;
      button.innerHTML = "Đang xử lý...";
    } else {
      button.innerHTML =
        label || button.dataset.originalHtml || button.innerHTML;
    }
  }

  async function saveArtwork(button, options = {}) {
    const { pageId, submissionId, returnUrl } = getDrawContext();

    if (!pageId && !submissionId) {
      alert("Không tìm thấy mã trang hoặc mã bài nộp để lưu bản vẽ!");
      return null;
    }

    const endpoint = submissionId
      ? `/api/submission/${submissionId}/savefile`
      : `/api/page/${pageId}/savefile`;
    const base64 = flattenAllLayers().toDataURL("image/png");

    setButtonLoading(button, true);

    try {
      const res = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ imageBase64: base64 }),
      });
      const data = await res.json();

      if (data.status !== "success") {
        throw new Error(data.message || "Không thể lưu bản vẽ.");
      }

      if (button) {
        button.innerHTML = "Đã lưu";
      }

      if (options.redirect !== false) {
        const nextUrl = returnUrl || data.redirectUrl;
        if (nextUrl) {
          setTimeout(() => {
            window.location.href = nextUrl;
          }, 700);
        }
      }

      return data;
    } catch (err) {
      alert("Lỗi: " + err.message);
      return null;
    } finally {
      setTimeout(() => setButtonLoading(button, false), 700);
    }
  }

  async function updateSubmissionMeta(button) {
    const { submissionId } = getDrawContext();
    if (!submissionId) return { status: "skipped" };

    const status = document.getElementById("subStatus")?.value || "unfinish";
    const comment = document.getElementById("subComment")?.value || "";
    const body = new URLSearchParams({ status, comment });

    const res = await fetch(
      `/manga/mangaka/submission/${submissionId}/submit/data`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
        },
        body,
      },
    );
    const data = await res.json();

    if (data.status !== "success") {
      throw new Error(data.message || "Không thể cập nhật trạng thái bài nộp.");
    }

    return data;
  }

  async function finishArtworkSave() {
    drawDirty = false; // lưu qua luồng artwork thành công → hết thay đổi chưa lưu
    closeEditSubmissionModal();

    const refreshPromise =
      typeof window.refreshDrawChapterPages === "function"
        ? Promise.resolve(window.refreshDrawChapterPages())
        : Promise.resolve();

    if (typeof window.showUiAlertAsync === "function") {
      await Promise.allSettled([
        refreshPromise,
        window.showUiAlertAsync("Lưu thành công"),
      ]);
      return;
    }

    if (typeof window.showUiAlert === "function") {
      window.showUiAlert("Lưu thành công");
    } else {
      alert("Lưu thành công");
    }
    await refreshPromise;
  }

  function openActionModal(e) {
    e?.preventDefault();
    e?.stopPropagation();
    const modal = document.getElementById("editSubmissionModal");
    if (modal) {
      modal.style.display = "flex";
    }
  }

  actionButton?.addEventListener("click", openActionModal);

  btnModalLoadPage?.addEventListener("click", () => {
    const input = document.getElementById("inputLoadPage");
    if (input) {
      input.value = "";
      input.click();
      return;
    }
    btnLoadPageLegacy?.click();
  });
  btnModalDownload?.addEventListener("click", () => btnDownloadLegacy?.click());
  btnModalSaveArtwork?.addEventListener("click", async (e) => {
    const saved = await saveArtwork(e.currentTarget, { redirect: false });
    if (saved) await finishArtworkSave();
  });

  btnConfirmEdit?.addEventListener("click", async (e) => {
    e.preventDefault();
    const button = e.currentTarget;
    const saved = await saveArtwork(button, { redirect: false });
    if (!saved) return;

    try {
      if (document.getElementById("subStatus")) {
        await updateSubmissionMeta(button);
      }
      button.innerHTML = "Hoàn tất";
      await finishArtworkSave();
    } catch (err) {
      alert("Lỗi: " + err.message);
    }
  });

  document
    .getElementById("editSubmissionModal")
    ?.addEventListener("click", (e) => {
      if (e.target.id === "editSubmissionModal") {
        closeEditSubmissionModal();
      }
    });
})();

// Hàm hỗ trợ đóng nhanh cửa sổ khi người dùng nhấn Hủy.
function closeEditSubmissionModal() {
  const modal = document.getElementById("editSubmissionModal");
  if (modal) modal.style.display = "none";
}

// Đọc tệp người dùng tải lên và đưa vào không gian vẽ.
// Lưu ý: btnLoadPage chỉ tồn tại khi Thymeleaf render th:if="${page != null}",
// nhưng inputLoadPage (và nút "Upload file" trong modal Tác vụ trang, xem
// btnModalLoadPage ở IIFE phía trên) luôn cần listener "change" hoạt động dù
// btnLoadPage có mặt hay không — nên KHÔNG được gộp điều kiện 2 nút lại.
const btnLoadPage = document.getElementById("btnLoadPage");
const inputLoadPage = document.getElementById("inputLoadPage");

if (btnLoadPage && inputLoadPage) {
  btnLoadPage.addEventListener("click", () => {
    inputLoadPage.click(); // Kích hoạt ô chọn tệp đang được ẩn khỏi giao diện.
  });
}

if (inputLoadPage) {
  inputLoadPage.addEventListener("change", (e) => {
    const file = e.target.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (ev) => {
      const img = new Image();
      img.onload = () => {
        // Chọn layer đích: ưu tiên layer 1, nếu không có thì layer nền.
        const target =
          typeof layers !== "undefined" && layers && (layers[1] || layers[0]);
        if (!target || !target.ctx) {
          alert("Chưa mở trang vẽ nên không nạp được ảnh. Hãy mở trang vẽ trước.");
          return;
        }
        // Xóa layer đích rồi vẽ ảnh co giãn vừa khung 400×600 (mọi kích thước
        // đều hiển thị đúng, không bị lệch/cắt).
        target.ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
        target.ctx.drawImage(img, 0, 0, CANVAS_W, CANVAS_H);
        pushHistoryEntry("Load file thủ công");
        drawDirty = true;
        // Đóng modal "Tác vụ trang" (nếu đang mở) để thấy ảnh trên canvas.
        if (typeof closeEditSubmissionModal === "function") {
          try { closeEditSubmissionModal(); } catch (err) {}
        }
      };
      img.onerror = () =>
        alert("Không đọc được ảnh. Vui lòng thử file PNG hoặc JPG khác.");
      img.src = ev.target.result;
    };
    reader.onerror = () => alert("Không đọc được tệp đã chọn.");
    reader.readAsDataURL(file);
  });
}
// Khởi tạo danh sách layer lần đầu.
renderLayerList();
// ========================================
// Tải toàn bộ layer dưới dạng một tệp ảnh.
// ========================================
document.getElementById("btnDownload")?.addEventListener("click", () => {
// Gộp tất cả layer thành một ảnh.
  const flatCanvas = flattenAllLayers();

  // Tạo liên kết tải xuống tự động.
  const link = document.createElement("a");
  link.download = "manga-page-" + Date.now() + ".png";
  link.href = flatCanvas.toDataURL("image/png");
  link.click();
});
