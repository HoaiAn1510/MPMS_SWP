// package com.example.manga_management.controller;

// import java.util.Map;

// import org.springframework.stereotype.Controller;
// import org.springframework.ui.Model;
// import org.springframework.web.bind.annotation.GetMapping;
// import org.springframework.web.bind.annotation.PathVariable;
// import org.springframework.web.bind.annotation.PostMapping;
// import org.springframework.web.bind.annotation.RequestBody;
// import org.springframework.web.bind.annotation.RequestParam;
// import org.springframework.web.bind.annotation.ResponseBody;

// import com.example.manga_management.service.DrawService;

// /**
//  * Controller mỏng — chỉ nhận request và gọi DrawService.
//  * Toàn bộ logic xử lý file/JSON/base64 nằm ở DrawService.
//  */
// @Controller
// public class DrawController {

//     private final DrawService drawService;

//     public DrawController(DrawService drawService) {
//         this.drawService = drawService;
//     }

//     // ── Hiển thị trang vẽ ───────────────────────────────────────────────

//     @GetMapping("/draw")
//     public String drawPage(Model model) {
//         return "draw";
//     }

//     // ── Lưu ảnh đã gộp (flatten) ─────────────────────────────────────────

//     @PostMapping("/draw/save")
//     @ResponseBody
//     public Map<String, Object> saveDrawing(@RequestParam String imageBase64) {
//         return drawService.saveFlattenedImage(imageBase64);
//     }

//     // ── Lưu project nhiều layer ──────────────────────────────────────────

//     @PostMapping("/draw/project/save")
//     @ResponseBody
//     public Map<String, Object> saveProject(@RequestBody Map<String, Object> body) {
//         return drawService.saveProject(body);
//     }

//     // ── Load lại project đã lưu ─────────────────────────────────────────

//     @GetMapping("/draw/project/load/{projectId}")
//     @ResponseBody
//     public Map<String, Object> loadProject(@PathVariable String projectId) {
//         return drawService.loadProject(projectId);
//     }

//     // ── Danh sách project đã lưu ─────────────────────────────────────────

//     @GetMapping("/draw/project/list")
//     @ResponseBody
//     public Map<String, Object> listProjects() {
//         return drawService.listProjects();
//     }
    
// }
