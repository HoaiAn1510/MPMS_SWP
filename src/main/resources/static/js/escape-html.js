/**
 * Chống XSS khi ghép chuỗi HTML từ dữ liệu người dùng nhập (tên, nhận xét, chat...).
 * Nạp ở <head> của mọi trang để dùng được trong cả script inline lẫn callback fetch.
 * - escapeHtml(v): dùng cho nội dung text và giá trị thuộc tính HTML.
 * - jsArg(v): dùng cho tham số chuỗi trong thuộc tính onclick="fn(${jsArg(v)})".
 */
(function () {
  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }
  window.escapeHtml = escapeHtml;
  window.jsArg = function jsArg(value) {
    return escapeHtml(JSON.stringify(String(value ?? "")));
  };
})();
