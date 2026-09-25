/**
 * Quản lý Light mode/Dark mode và lưu lựa chọn trong localStorage.
 * Không gọi API; giao diện được cập nhật hoàn toàn tại trình duyệt.
 */
(function () {
  "use strict";

  const storageKey = "sankyuu-theme";
  const root = document.documentElement;

  function readStoredTheme() {
    try {
      const value = localStorage.getItem(storageKey);
      return value === "light" || value === "dark" ? value : null;
    } catch (_error) {
      return null;
    }
  }

  function applyTheme(theme, persist) {
    const nextTheme = theme === "light" ? "light" : "dark";
    root.dataset.theme = nextTheme;
    root.dataset.bsTheme = nextTheme;
    root.style.colorScheme = nextTheme;

    const themeColor = nextTheme === "light" ? "#f3f4f6" : "#0f172a";
    let metaThemeColor = document.querySelector('meta[name="theme-color"]');
    if (!metaThemeColor) {
      metaThemeColor = document.createElement("meta");
      metaThemeColor.name = "theme-color";
      document.head.appendChild(metaThemeColor);
    }
    metaThemeColor.content = themeColor;

    if (persist) {
      try {
        localStorage.setItem(storageKey, nextTheme);
      } catch (_error) {
      // Giao diện vẫn hoạt động khi trình duyệt không cho phép dùng bộ nhớ cục bộ.
      }
    }

    updateToggle(nextTheme);
    window.dispatchEvent(
      new CustomEvent("sankyuu:themechange", { detail: { theme: nextTheme } }),
    );
  }

  function updateToggle(theme) {
    const button = document.getElementById("themeToggle");
    if (!button) return;
    const isDark = theme === "dark";
    const nextLabel = isDark
      ? "Chuyển sang Light mode"
      : "Chuyển sang Dark mode";
    button.setAttribute("aria-label", nextLabel);
    button.setAttribute("title", nextLabel);
    button.setAttribute("aria-pressed", String(!isDark));
    button.innerHTML = isDark
      ? '<i class="fa-solid fa-sun" aria-hidden="true"></i>'
      : '<i class="fa-solid fa-moon" aria-hidden="true"></i>';
  }

  function mountToggle() {
    const header = document.querySelector(".header");
    if (!header || document.getElementById("themeToggle")) return;

    let actions = header.querySelector(":scope > .header-actions");
    if (!actions) {
      actions = document.createElement("div");
      actions.className = "header-actions";
      header.appendChild(actions);
    }

    const button = document.createElement("button");
    button.type = "button";
    button.id = "themeToggle";
    button.className = "theme-toggle";
    button.addEventListener("click", () => {
      applyTheme(root.dataset.theme === "light" ? "dark" : "light", true);
    });

    const logout = actions.querySelector('a[href*="/login/logout"]');
    actions.insertBefore(button, logout || null);
    updateToggle(root.dataset.theme || "dark");
  }

  applyTheme(readStoredTheme() || "dark", false);

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", mountToggle, { once: true });
  } else {
    mountToggle();
  }

  window.addEventListener("storage", (event) => {
    if (event.key !== storageKey) return;
    applyTheme(event.newValue === "light" ? "light" : "dark", false);
  });

  window.SankyuuTheme = {
    get: () => root.dataset.theme || "dark",
    set: (theme) => applyTheme(theme, true),
  };
})();
