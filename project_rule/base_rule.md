# BASE PROJECT RULES & COLLABORATION PROTOCOL

Tài liệu này định nghĩa các nguyên tắc cốt lõi (**Non-Negotiable Rules**) bắt buộc áp dụng đối với tất cả thành viên trong nhóm và AI Assistant tham gia phát triển dự án. Mọi thay đổi mã nguồn phải tuân thủ nghiêm ngặt từng điều khoản dưới đây.

---

## 1. Quy Trình "Bàn Trước – Code Sau" (Discussion-First Workflow)

* **Không tự ý triển khai:** Tuyệt đối không bắt tay vào viết mã hoặc chỉnh sửa file khi chưa phân tích giải pháp, làm rõ luồng nghiệp vụ và nhận được sự phê duyệt (approval) từ người phụ trách/người dùng.
* **Chủ động đặt câu hỏi:** Khi phát hiện bất kỳ điểm mờ, logic chưa rõ ràng hoặc nhận thấy phương án hiện tại có thể tối ưu hơn, phải nêu ý kiến thảo luận và đề xuất giải pháp ngay trong giai đoạn trao đổi.

---

## 2. Chia Nhỏ Task & Cơ Chế Checkpoint (Atomic Tasks & Review Gates)

* **Phân rã bài toán (Task Decomposition):**
    * Không làm cùng lúc nhiều tính năng hoặc gộp các tác vụ lớn vào một lượt xử lý để tránh mất context và phát sinh lỗi ngoài tầm kiểm soát.
    * Chia nhỏ yêu cầu thành các sub-task độc lập, rõ ràng về mặt phạm vi và mục tiêu.
* **Cơ chế Review Gate (Dừng và Chờ):**
    * Mỗi khi hoàn thành xong một sub-task hoặc một tính năng nhỏ, phải **dừng lại ngay lập tức**.
    * Báo cáo ngắn gọn nội dung đã làm, các điểm cần lưu ý và hướng dẫn kiểm thử.
    * Chờ người dùng nghiệm thu, phản hồi và xác nhận đạt yêu cầu mới được chuyển sang task tiếp theo.

---

## 3. Tính Nhất Quán Về Style & Cấu Trúc (Architecture & Consistency)

* **Tham chiếu module mẫu (Golden Standard):**
    * Mọi quy ước về cấu trúc thư mục, đặt tên, định dạng Response API và mã lỗi phải tuân thủ tuyệt đối theo phong cách của các module hoàn chỉnh sẵn có trong dự án (ví dụ: module `features/product`).
* **Không tự ý du nhập phong cách mới:**
    * Giữ nguyên thiết kế phân tầng hiện tại (`Controller -> Service -> Repository -> Model/Entity`).
    * Không tự ý áp dụng thư viện mới, pattern mới hoặc cách viết khác lạ nếu không có yêu cầu hay sự thống nhất trước.

---

## 4. Nghiệp Vụ Toàn Diện, Code Tinh Gọn & Tái Sử Dụng (Clean & Optimized Code)

* **Nghiên cứu thấu đáo nghiệp vụ:**
    * Luôn phân tích kỹ lưỡng ("xào đi xào lại") toàn bộ kịch bản nghiệp vụ trước khi triển khai: luồng thành công, luồng ngoại lệ, kiểm tra dữ liệu đầu vào (validation), xử lý `null-safe` và các trường hợp biên (edge cases).
* **Nguyên tắc DRY (Don't Repeat Yourself):**
    * Tuyệt đối tránh lặp lại các đoạn mã xử lý tương tự nhau.
    * Khuyến khích thiết kế theo dạng: **Viết một lần – Tái sử dụng nhiều lần**. Đóng gói các logic dùng chung thành các hàm private hoặc utility methods nội bộ một cách hợp lý.
* **Cân bằng giữa Tối ưu và Đầy đủ:**
    * Mã nguồn phải được tối ưu hóa về hiệu năng và tài nguyên, cú pháp ngắn gọn, súc tích nhưng phải giữ tính tường minh, dễ đọc và thể hiện đầy đủ, chi tiết logic nghiệp vụ cần có.

---

## 5. Quy Chuẩn Tầng Repository & Việc Sử Dụng `@Query`

* **Mặc định tuân theo chuẩn JPA:**
    * Ưu tiên sử dụng Query Methods (Derived Queries), Specifications hoặc các quy chuẩn truy vấn sẵn có trong dự án.
* **Quy tắc sử dụng `@Query`:**
    * `@Query` vẫn được phép sử dụng nhưng **bắt buộc phải có lý do kỹ thuật chính đáng**, bao gồm:
        * Xử lý các câu truy vấn phức tạp, liên kết nhiều bảng hoặc tổng hợp dữ liệu (Aggregate/JOIN) mà Query Methods thông thường tạo ra query không tối ưu.
        * Tối ưu hóa hiệu năng (Performance Tuning), sử dụng DTO Projection để chỉ lấy đúng các cột cần thiết, giúp giảm dung lượng bộ nhớ và tải mạng khi deploy hệ thống.
    * Phải giải trình ngắn gọn lý do kỹ thuật trước khi thêm `@Query`.
    * Đảm bảo câu lệnh JPQL/Native SQL đã được kiểm tra chỉ mục (Index) và tránh lỗi $N+1$.

---

## 6. Kỷ Luật Phạm Vi & Bảo Vệ Module Common (Scope Discipline)

* **Giới hạn phạm vi (Strict Scope):**
    * Chỉ thao tác đúng trên các tệp và đoạn mã liên quan trực tiếp đến task được giao.
    * Tuyệt đối không tự ý format lại toàn bộ file, đổi tên biến hay sửa đổi logic ở những phần mã không liên quan.
* **Hạn chế can thiệp vào `common`:**
    * Hạn chế tối đa việc chỉnh sửa các class/module dùng chung (`common`, `core`, `shared`).
    * Bất kỳ thay đổi nào tại tầng `common` đều có nguy cơ ảnh hưởng dây chuyền đến các module khác. Nếu bắt buộc phải sửa hoặc mở rộng, phải phân tích mức độ ảnh hưởng (Impact Analysis), đưa ra đề xuất và hỏi ý kiến trước khi làm.

---

## Bảng Checklist Tóm Tắt Quy Trình

| Giai đoạn | Thao tác bắt buộc |
| :--- | :--- |
| **1. Tiếp nhận** | Phân rã task nhỏ $\to$ Phân tích nghiệp vụ kỹ lưỡng $\to$ Đề xuất giải pháp $\to$ Hỏi rõ điểm mờ $\to$ **Chờ duyệt** |
| **2. Triển khai** | Soi chiếu module mẫu (như `product`) $\to$ Code tối ưu, chuẩn DRY $\to$ Giữ đúng phạm vi file $\to$ Hạn chế đụng `common` |
| **3. Tầng Data** | Tuân thủ convention Repository $\to$ Chỉ dùng `@Query` khi có lý do tối ưu xác đáng |
| **4. Bàn giao** | **Dừng lại ngay** $\to$ Báo cáo kết quả và cách test $\to$ **Chờ nghiệm thu mới sang task tiếp theo** |