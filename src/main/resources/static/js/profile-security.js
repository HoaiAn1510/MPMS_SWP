/**
 * Hồ sơ và bảo mật dùng chung cho mọi role.
 * API hồ sơ: GET /api/account/profile; POST /api/account/update-profile.
 * API OTP: /api/account/send-otp, /send-change-email-otp,
 * /confirm-change-phone-otp và /send-phone-verification-otp.
 */
(function () {
  "use strict";

  let profileData = null;
  let connections = [];

  const iconFor = (url) => {
    const value = String(url || "").toLowerCase();
    if (value.includes("facebook")) return "fa-brands fa-facebook";
    if (value.includes("instagram")) return "fa-brands fa-instagram";
    if (value.includes("github")) return "fa-brands fa-github";
    if (value.includes("youtube")) return "fa-brands fa-youtube";
    if (value.includes("discord")) return "fa-brands fa-discord";
    if (value.includes("x.com") || value.includes("twitter")) return "fa-brands fa-x-twitter";
    return "fa-solid fa-link";
  };

  function safeUrl(value) {
    try {
      const url = new URL(value);
      return ["http:", "https:"].includes(url.protocol) ? url.href : null;
    } catch (_error) {
      return null;
    }
  }

  function parseConnections(value) {
    if (!value) return [];
    try {
      const parsed = JSON.parse(value);
      if (Array.isArray(parsed)) {
        return parsed
          .map((item) => ({ name: String(item.name || "Kết nối"), url: safeUrl(item.url) }))
          .filter((item) => item.url);
      }
    } catch (_error) {
      // Tài khoản cũ có thể chỉ lưu một URL; tiếp tục hỗ trợ dữ liệu đó sau khi nâng cấp.
    }
    const url = safeUrl(value);
    return url ? [{ name: new URL(url).hostname.replace(/^www\./, ""), url }] : [];
  }

  function notify(message, type) {
    if (typeof window.showToast === "function") window.showToast(message, type || "success");
    else window.alert(message);
  }

  async function request(url, options) {
    const response = await fetch(url, options);
    const data = await response.json();
    if (!response.ok || data.status === "error") throw new Error(data.message || "Không thể xử lý yêu cầu");
    return data;
  }

  function renderConnections() {
    const list = document.getElementById("profileConnectionList");
    if (!list) return;
    list.replaceChildren();
    if (!connections.length) {
      const empty = document.createElement("p");
      empty.className = "profile-connection-empty";
      empty.textContent = "Chưa có ứng dụng hoặc trang cá nhân nào được liên kết.";
      list.appendChild(empty);
      return;
    }
    connections.forEach((connection, index) => {
      const row = document.createElement("div");
      row.className = "profile-connection-item";
      const link = document.createElement("a");
      link.href = connection.url;
      link.target = "_blank";
      link.rel = "noopener noreferrer";
      link.innerHTML = `<i class="${iconFor(connection.url)}" aria-hidden="true"></i><span></span><i class="fa-solid fa-arrow-up-right-from-square" aria-hidden="true"></i>`;
      link.querySelector("span").textContent = connection.name;
      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "profile-connection-remove";
      remove.setAttribute("aria-label", `Xóa kết nối ${connection.name}`);
      remove.innerHTML = '<i class="fa-solid fa-xmark" aria-hidden="true"></i>';
      remove.addEventListener("click", () => {
        connections.splice(index, 1);
        renderConnections();
      });
      row.append(link, remove);
      list.appendChild(row);
    });
  }

  function addConnection() {
    const nameInput = document.getElementById("profileConnectionName");
    const urlInput = document.getElementById("profileConnectionUrl");
    const url = safeUrl(urlInput.value.trim());
    if (!nameInput.value.trim() || !url) {
      notify("Nhập tên kết nối và đường dẫn http/https hợp lệ", "error");
      return;
    }
    connections.push({ name: nameInput.value.trim(), url });
    nameInput.value = "";
    urlInput.value = "";
    renderConnections();
  }

  // Chuẩn hóa hồ sơ của mọi vai trò thành một bảng hai vùng dùng chung.
  function ensureUnifiedProfileWorkspace(identityCard, formCard) {
    const roleInfo = document.getElementById("profileRoleInfo");
    let workspace = identityCard.closest(".profile-workspace");

    if (!workspace) {
      const host = identityCard.parentElement;
      workspace = document.createElement("div");
      workspace.className = "profile-workspace";

      const primaryColumn = document.createElement("div");
      primaryColumn.className = "profile-primary-column";
      host.insertBefore(workspace, identityCard);
      primaryColumn.append(identityCard, formCard);
      workspace.appendChild(primaryColumn);
      if (roleInfo) workspace.appendChild(roleInfo);
    }

    workspace.classList.add("profile-unified-table");
    roleInfo?.classList.add("profile-role-card");
  }

  // Gắn lớp bố cục ổn định cho dữ liệu nghiệp vụ được từng role tạo động.
  function normalizeProfileRoleInfo() {
    const content = document.getElementById("profileRoleInfoContent");
    if (!content) return;

    content.classList.add("profile-role-content-grid");
    Array.from(content.children).forEach((section, index) => {
      if (section.classList.contains("field")) {
        section.classList.add("profile-role-section");
      } else if (
        index === 0 &&
        section.matches("div") &&
        section.querySelectorAll(":scope > div").length > 1
      ) {
        section.classList.add("profile-role-summary");
      }
    });
    content.querySelectorAll("table").forEach((table) => {
      table.classList.add("profile-role-table");
    });
  }

  function buildProfileUi() {
    document.getElementById("btnChangePassword")?.remove();
    const displayName = document.getElementById("profileDisplayName");
    if (!displayName) return;
    const identityCard = displayName.closest(".section-card");
    const formCard = identityCard?.nextElementSibling;
    if (!identityCard || !formCard?.classList.contains("section-card")) return;

    ensureUnifiedProfileWorkspace(identityCard, formCard);

    const role = document.getElementById("profileDisplayRole");
    const id = document.getElementById("profileDisplayId");
    const details = displayName.parentElement;
    details.classList.add("profile-identity-details");
    details.replaceChildren();
    details.innerHTML = `
      <span id="profileDisplayName" hidden></span>
      <div class="profile-nickname-edit">
        <input id="profileNickname" maxlength="30" aria-label="Tên hiển thị" placeholder="Tên hiển thị" />
        <button type="button" class="btn-sm" id="saveNicknameButton"><i class="fa-solid fa-check" aria-hidden="true"></i><span>Lưu tên</span></button>
      </div>`;
    role?.remove();
    id?.remove();

    formCard.classList.add("profile-account-card");
    formCard.removeAttribute("style");
    formCard.innerHTML = `
      <div class="profile-account-row">
        <div class="profile-account-copy"><label for="profileUsername">Username</label></div>
        <input id="profileUsername" readonly />
      </div>
      <div class="profile-account-row">
        <div class="profile-account-copy"><label for="profilePassword">Password</label></div>
        <div class="profile-account-control">
          <input id="profilePassword" value="******" readonly aria-label="Mật khẩu được ẩn" />
          <button type="button" class="btn-sm btn-primary" onclick="openChangePasswordModal()">Đổi mật khẩu</button>
        </div>
      </div>
      <div class="profile-account-row">
        <div class="profile-account-copy"><label for="profileEmail">Email <span id="profileEmailVerifiedBadge"></span></label></div>
        <div class="profile-account-control"><input id="profileEmail" readonly /><button type="button" class="btn-sm btn-primary" onclick="openChangeEmailModal()">Đổi email</button></div>
      </div>
      <div class="profile-account-row">
        <div class="profile-account-copy"><label for="profilePhone">Số điện thoại <span id="profilePhoneVerifiedBadge"></span></label></div>
        <div class="profile-account-control"><input id="profilePhone" readonly placeholder="Chưa liên kết" /><button type="button" class="btn-sm" id="verifyPhoneButton" hidden>Xác thực số</button><button type="button" class="btn-sm btn-primary" onclick="openChangePhoneModal()">Đổi số</button></div>
      </div>
      <div class="profile-connections">
        <div class="profile-account-copy"><label>Kết nối</label></div>
        <div id="profileConnectionList"></div>
        <div class="profile-connection-editor"><input id="profileConnectionName" placeholder="Tên hiển thị, ví dụ: GitHub" /><input id="profileConnectionUrl" type="url" placeholder="https://..." /><button type="button" class="btn-sm" id="addConnectionButton"><i class="fa-solid fa-plus"></i> Thêm kết nối</button></div>
        <button type="button" class="btn-sm btn-primary profile-save-connections" id="saveConnectionsButton">Lưu kết nối</button>
      </div>
      <input id="profileSocialLinks" type="hidden" /><input id="profileBio" type="hidden" />`;

    document.getElementById("saveNicknameButton").addEventListener("click", saveProfile);
    document.getElementById("saveConnectionsButton").addEventListener("click", saveProfile);
    document.getElementById("addConnectionButton").addEventListener("click", addConnection);
    document.getElementById("verifyPhoneButton").addEventListener("click", openPhoneVerification);
  }

  function applyProfile(data) {
    profileData = data;
    const avatar = document.getElementById("profileAvatarImg");
    if (avatar && data.avatar) {
      avatar.src = typeof window.withFreshAssetUrl === "function"
        ? window.withFreshAssetUrl(data.avatar)
        : data.avatar;
    }
    document.getElementById("profileDisplayName").textContent = data.fullname || data.username || "";
    document.getElementById("profileNickname").value = data.fullname || "";
    document.getElementById("profileDisplayRole")?.remove();
    document.getElementById("profileDisplayId")?.remove();
    document.getElementById("profileUsername").value = data.username || "";
    document.getElementById("profileEmail").value = data.email || "";
    document.getElementById("profilePhone").value = data.phone || "";
    const emailBadge = document.getElementById("profileEmailVerifiedBadge");
    emailBadge.textContent = data.emailVerified ? "✓ Đã xác thực" : "Chưa xác thực";
    emailBadge.className = data.emailVerified ? "is-verified" : "is-unverified";
    const phoneBadge = document.getElementById("profilePhoneVerifiedBadge");
    phoneBadge.textContent = data.phoneVerified ? "✓ Đã xác thực" : data.phone ? "Chưa xác thực" : "";
    phoneBadge.className = data.phoneVerified ? "is-verified" : "is-unverified";
    document.getElementById("verifyPhoneButton").hidden = !data.phone || data.phoneVerified;
    connections = parseConnections(data.socialLinks);
    renderConnections();
    if (typeof window.renderProfileRoleInfo === "function") {
      window.renderProfileRoleInfo(data);
      normalizeProfileRoleInfo();
    }
    enhanceOtpChannels();
  }

  async function loadProfile() {
    try {
      const data = await request("/api/account/profile");
      applyProfile(data);
    } catch (error) {
      notify(error.message, "error");
    }
  }

  async function saveProfile() {
    const nickname = document.getElementById("profileNickname").value.trim();
    if (!nickname) return notify("Tên hiển thị không được để trống", "error");
    try {
      await request("/api/account/update-profile", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ nickname, socialLinks: JSON.stringify(connections) }),
      });
      document.getElementById("profileDisplayName").textContent = nickname;
      notify("Đã lưu thông tin cá nhân");
    } catch (error) {
      notify(error.message, "error");
    }
  }

  function channelPicker(id, label) {
    return `<fieldset class="otp-channel-picker" id="${id}"><legend>${label}</legend><label><input type="radio" name="${id}" value="email"> Email hiện tại</label><label class="phone-otp-choice"><input type="radio" name="${id}" value="phone"> Số điện thoại đã xác thực</label></fieldset>`;
  }

  function selectedChannel(id) {
    return document.querySelector(`#${id} input:checked`)?.value || (profileData?.phoneVerified ? "phone" : "email");
  }

  function enhanceOtpChannels() {
    const cpStep = document.getElementById("cpStep1");
    if (cpStep && !document.getElementById("passwordOtpChannel")) {
      cpStep.querySelector("p").textContent = "Chọn nơi nhận mã OTP để xác thực yêu cầu đổi mật khẩu.";
      cpStep.querySelector("p").insertAdjacentHTML("afterend", channelPicker("passwordOtpChannel", "Nhận OTP qua"));
    }
    const ceStep = document.getElementById("ceStep1");
    if (ceStep && !document.getElementById("emailOtpChannel")) {
      ceStep.querySelector("p").childNodes[0].textContent = "Nhập email mới, sau đó chọn kênh nhận OTP để xác thực yêu cầu. ";
      ceStep.querySelector("p").insertAdjacentHTML("afterend", channelPicker("emailOtpChannel", "Xác thực bằng"));
    }
    document.querySelectorAll(".phone-otp-choice").forEach((choice) => {
      choice.hidden = !profileData?.phoneVerified;
      const input = choice.querySelector("input");
      input.checked = Boolean(profileData?.phoneVerified);
    });
    document.querySelectorAll('.otp-channel-picker input[value="email"]').forEach((input) => {
      if (!profileData?.phoneVerified) input.checked = true;
    });
  }

  async function sendPasswordOtp() {
    const button = document.getElementById("btnSendOtp");
    const error = document.getElementById("cpStep1Error");
    button.disabled = true;
    try {
      await request("/api/account/send-otp", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ channel: selectedChannel("passwordOtpChannel") }) });
      window.cpGoStep(2);
    } catch (exception) {
      error.textContent = exception.message;
      error.style.display = "block";
    } finally {
      button.disabled = false;
    }
  }

  async function sendEmailOtp() {
    const email = document.getElementById("ceNewEmail").value.trim();
    if (!email) return window.ceSetError?.("ceStep1Error", "Vui lòng nhập email mới");
    try {
      const data = await request("/api/account/send-change-email-otp", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ newEmail: email, channel: selectedChannel("emailOtpChannel") }) });
      document.getElementById("cePendingEmailText").textContent = email;
      document.getElementById("ceStep1").style.display = "none";
      document.getElementById("ceStep2").style.display = "block";
      notify(data.message);
    } catch (exception) {
      const error = document.getElementById("ceStep1Error");
      error.textContent = exception.message;
      error.style.display = "block";
    }
  }

  async function confirmPhoneChange() {
    const otp = document.getElementById("cpnOtpInput").value.trim();
    const error = document.getElementById("cpnStep2Error");
    if (!/^\d{6}$/.test(otp)) {
      error.textContent = "Vui lòng nhập đúng mã OTP gồm 6 số";
      error.style.display = "block";
      return;
    }
    try {
      const data = await request("/api/account/confirm-change-phone-otp", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ otp }),
      });
      window.closeChangePhoneModal();
      await loadProfile();
      notify(data.message);
    } catch (exception) {
      error.textContent = exception.message;
      error.style.display = "block";
    }
  }

  function openPhoneVerification() {
    let overlay = document.getElementById("verifyPhoneOverlay");
    if (!overlay) {
      document.body.insertAdjacentHTML("beforeend", `<div id="verifyPhoneOverlay" class="app-floating-modal profile-verify-phone-modal"><div class="modal-box"><button class="modal-close-btn" type="button" aria-label="Đóng">×</button><h4>Xác thực số điện thoại</h4><p>Mã OTP sẽ được gửi bằng SMS tới số đang liên kết.</p><button type="button" class="btn-primary" id="sendPhoneVerifyOtp">Gửi OTP qua SMS</button><div id="phoneVerifyCodeWrap" hidden><label for="phoneVerifyCode">Mã OTP</label><input id="phoneVerifyCode" inputmode="numeric" maxlength="6" autocomplete="one-time-code"><button type="button" class="btn-primary" id="confirmPhoneVerifyOtp">Xác nhận số điện thoại</button></div><p class="profile-inline-error" id="phoneVerifyError"></p></div></div>`);
      overlay = document.getElementById("verifyPhoneOverlay");
      overlay.querySelector(".modal-close-btn").onclick = () => (overlay.style.display = "none");
      document.getElementById("sendPhoneVerifyOtp").onclick = async () => {
        try {
          await request("/api/account/send-phone-verification-otp", { method: "POST" });
          document.getElementById("phoneVerifyCodeWrap").hidden = false;
        } catch (error) { document.getElementById("phoneVerifyError").textContent = error.message; }
      };
      document.getElementById("confirmPhoneVerifyOtp").onclick = async () => {
        try {
          await request("/api/account/confirm-phone-verification-otp", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ otp: document.getElementById("phoneVerifyCode").value.trim() }) });
          overlay.style.display = "none";
          await loadProfile();
          notify("Xác thực số điện thoại thành công");
        } catch (error) { document.getElementById("phoneVerifyError").textContent = error.message; }
      };
    }
    overlay.style.display = "flex";
  }

  window.loadProfile = loadProfile;
  window.saveProfile = saveProfile;
  window.cpSendOtp = sendPasswordOtp;
  window.ceSendOtp = sendEmailOtp;
  window.cpnConfirmOtp = confirmPhoneChange;

  document.addEventListener("DOMContentLoaded", () => {
    buildProfileUi();
    if (document.getElementById("profileUsername")) loadProfile();
  });
})();
