/**
 * Tin nhắn dùng chung cho các role.
 * API: GET /api/user-chat/contacts; GET /api/user-chat/thread/{contactId}.
 * WebSocket: /ws-chat; gửi tin qua /app/chat.send và nhận tại /user/queue/messages.
 */
(function () {
  const currentUserId = document.body.getAttribute("data-user-id");
  if (!currentUserId) return;

  const contactListEl = document.getElementById("chatContactList");
  const threadHeaderEl = document.getElementById("chatThreadHeader");
  const messagesEl = document.getElementById("chatMessages");
  const inputEl = document.getElementById("chatInput");
  const sendBtn = document.getElementById("chatSendBtn");
  const tabBadgeEl = document.getElementById("chatTabBadge");

  if (!contactListEl) return;

  let contacts = [];
  let activeContactId = null;
  let stompClient = null;

  function sameContactId(left, right) {
    return left != null && right != null && String(left) === String(right);
  }

  function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text == null ? "" : String(text);
    return div.innerHTML;
  }

  function formatTime(iso) {
    if (!iso) return "";
    const d = new Date(iso);
    if (isNaN(d.getTime())) return "";
    const hh = String(d.getHours()).padStart(2, "0");
    const mm = String(d.getMinutes()).padStart(2, "0");
    const dd = String(d.getDate()).padStart(2, "0");
    const mo = String(d.getMonth() + 1).padStart(2, "0");
    return hh + ":" + mm + " " + dd + "/" + mo;
  }

  function renderTabBadge() {
    if (!tabBadgeEl) return;
    const total = contacts.reduce((sum, c) => sum + (c.unreadCount || 0), 0);
    if (total > 0) {
      tabBadgeEl.textContent = total > 99 ? "99+" : total;
      tabBadgeEl.style.display = "flex";
    } else {
      tabBadgeEl.style.display = "none";
    }
  }

  function renderContacts() {
    if (contacts.length === 0) {
      contactListEl.innerHTML =
        '<div class="chat-empty">Không có ai để nhắn tin</div>';
      return;
    }
    contactListEl.innerHTML = contacts
      .map((c) => {
        const roleLower = (c.role || "").toLowerCase();
        const preview = c.lastMessage
          ? escapeHtml(c.lastMessage)
          : "Chưa có tin nhắn";
        const unread = c.unreadCount > 0;
        return (
          "" +
          '<div class="chat-contact-item' +
          (sameContactId(c.id, activeContactId) ? " active" : "") +
          (unread ? " unread" : "") +
          '" data-contact-id="' +
          c.id +
          '">' +
          '<div class="chat-contact-info">' +
          '<div class="chat-contact-name">' +
          escapeHtml(c.fullname) +
          ' <span class="badge-role badge-' +
          roleLower +
          '">' +
          escapeHtml(c.role) +
          "</span>" +
          "</div>" +
          '<div class="chat-contact-preview">' +
          preview +
          "</div>" +
          "</div>" +
          (unread
            ? '<div class="chat-contact-unread-badge">' +
              c.unreadCount +
              "</div>"
            : "") +
          "</div>"
        );
      })
      .join("");

    contactListEl.querySelectorAll(".chat-contact-item").forEach((el) => {
      el.addEventListener("click", () =>
        openThread(el.getAttribute("data-contact-id")),
      );
    });

    renderTabBadge();
  }

  function renderMessages(messages) {
    messagesEl.innerHTML = messages
      .map((m) => {
        const mine = sameContactId(m.senderId, currentUserId);
        return (
          "" +
          '<div class="chat-bubble ' +
          (mine ? "mine" : "theirs") +
          '">' +
          escapeHtml(m.content) +
          '<span class="chat-bubble-time">' +
          formatTime(m.createdAt) +
          "</span>" +
          "</div>"
        );
      })
      .join("");
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  function loadContacts() {
    fetch("/api/user-chat/contacts")
      .then((res) => res.json())
      .then((data) => {
        contacts = Array.isArray(data) ? data : [];
        const activeContactStillExists = contacts.some((contact) =>
          sameContactId(contact.id, activeContactId),
        );
        const shouldOpenFirstContact =
          contacts.length > 0 && !activeContactStillExists;

        if (shouldOpenFirstContact) {
          activeContactId = contacts[0].id;
        }

        renderContacts();

        if (shouldOpenFirstContact) {
          openThread(activeContactId);
        }
      })
      .catch((err) => console.error("[Chat] load contacts failed", err));
  }

  function openThread(contactId) {
    activeContactId = contactId;
    const contact = contacts.find((c) => sameContactId(c.id, contactId));
    if (threadHeaderEl) {
      threadHeaderEl.textContent = contact ? contact.fullname : "";
    }
    if (inputEl) inputEl.disabled = false;
    if (sendBtn) sendBtn.disabled = false;

    fetch("/api/user-chat/thread/" + encodeURIComponent(contactId))
      .then((res) => res.json())
      .then((messages) => {
        renderMessages(messages);
        const c = contacts.find((x) => sameContactId(x.id, contactId));
        if (c) c.unreadCount = 0;
        renderContacts();
      })
      .catch((err) => console.error("[Chat] load thread failed", err));
  }

  function sendMessage() {
    const content = inputEl.value.trim();
    if (!content || !activeContactId || !stompClient || !stompClient.connected)
      return;
    stompClient.publish({
      destination: "/app/chat.send",
      body: JSON.stringify({ receiverId: activeContactId, content: content }),
    });
    inputEl.value = "";
  }

  function handleIncoming(message) {
    const isActiveThread =
      sameContactId(message.senderId, activeContactId) ||
      sameContactId(message.receiverId, activeContactId);
    const partnerId =
      sameContactId(message.senderId, currentUserId)
        ? message.receiverId
        : message.senderId;
    let contact = contacts.find((c) => sameContactId(c.id, partnerId));

    if (contact) {
      contact.lastMessage = message.content;
      contact.lastTime = message.createdAt;
      if (
        sameContactId(message.receiverId, currentUserId) &&
        !sameContactId(activeContactId, partnerId)
      ) {
        contact.unreadCount = (contact.unreadCount || 0) + 1;
      }
    } else {
      loadContacts();
      return;
    }

    if (sameContactId(activeContactId, partnerId) && isActiveThread) {
      openThread(activeContactId);
    } else {
      renderContacts();
    }
  }

  function connectWebSocket() {
    const socket = new SockJS("/ws-chat");
    stompClient = new StompJs.Client({
      webSocketFactory: () => socket,
      reconnectDelay: 5000,
      onConnect: () => {
        stompClient.subscribe("/user/queue/messages", (frame) => {
          const message = JSON.parse(frame.body);
          handleIncoming(message);
        });
      },
    });
    stompClient.activate();
  }

  if (sendBtn) sendBtn.addEventListener("click", sendMessage);
  if (inputEl) {
    inputEl.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        sendMessage();
      }
    });
  }

  loadContacts();
  connectWebSocket();
})();
