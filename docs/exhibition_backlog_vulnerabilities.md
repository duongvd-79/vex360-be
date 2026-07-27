# Báo cáo: Xử lý Lỗ hổng & Điểm cần tối ưu Nghiệp vụ khi Đăng ký Triển lãm

Tài liệu này tổng kết việc xử lý toàn bộ 5 lỗ hổng nghiệp vụ (Business Vulnerabilities) và rủi ro kỹ thuật khi Nhà tổ chức (Organizer) đăng ký và quản lý triển lãm.

---

## 1. ✅ Race Condition qua mặt giới hạn 3 đơn PENDING (`pendingCount >= 3`)

### Mô tả & Rủi ro
Trước đây, kiểm tra `pendingCount >= 3` không khóa dữ liệu. Khi gửi đồng thời nhiều request `POST /createExhibition`, Organizer có thể qua mặt hệ thống tạo nhiều hơn 3 đơn `PENDING`.

### Giải pháp đã xử lý (Ponytail)
- Áp dụng Pessimistic Write Lock trên row của Organizer thông qua `userRepository.findByIdForUpdate(organizer.getId())` ngay đầu transaction `createExhibition`.

---

## 2. ✅ Khống chế thời lượng tối đa của Triển lãm (Duration Limit)

### Mô tả & Rủi ro
Trước đây chưa có giới hạn thời gian kéo dài sự kiện (`endDate` - `startDate`), cho phép tạo triển lãm kéo dài hàng chục năm.

### Giải pháp đã xử lý (Ponytail)
- Khai báo hằng số `MAX_EXHIBITION_DURATION_DAYS = 90`.
- Kiểm tra `ChronoUnit.DAYS.between(startDate, endDate) <= 90` ở cả 2 luồng `createExhibition` và `updateExhibitionForOrganizer`.

---

## 3. ✅ Giới hạn số gian hàng dự kiến tối đa (`estimatedBooths`)

### Mô tả & Rủi ro
Trước đây `CreateExhibitionRequest` chỉ kiểm tra `@Min(1)`, không giới hạn số lượng gian hàng tối đa, nguy cơ nộp số lượng gian hàng quá lớn gây lỗi hệ thống.

### Giải pháp đã xử lý (Ponytail)
- Bổ sung `@Max(value = 2000, message = "Số gian hàng dự kiến không được vượt quá 2000")` vào `CreateExhibitionRequest.java`.

---

## 4. ✅ Trùng tên triển lãm không phân biệt chữ hoa/thường (Case Insensitive Duplication Check)

### Mô tả & Rủi ro
Trước đây chỉ kiểm tra exact match `existsByName(name.trim())`, nguy cơ bị lách tên thông qua ký tự hoa/thường (`"Triển Lãm"` vs `"triển lãm"`).

### Giải pháp đã xử lý (Ponytail)
- Thêm method `existsByNameIgnoreCase(String name)` vào `ExhibitionRepository`.
- Áp dụng kiểm tra `existsByNameIgnoreCase` khi tạo mới và khi cập nhật đơn triển lãm.

---

## 5. ✅ Re-validate Giá sàn Gói dịch vụ khi Admin Phê duyệt

### Mô tả & Rủi ro
Nếu giá sàn của `PackageTemplate` được Admin điều chỉnh tăng trong thời gian đơn chờ duyệt, đơn đăng ký có thể được duyệt với giá gói thấp hơn giá sàn hiện hành.

### Giải pháp đã xử lý (Ponytail)
- Bổ sung bước kiểm tra re-validate giá sàn của tất cả các gói dịch vụ thuộc triển lãm trong hàm `approveExhibition`. Nếu `finalPrice < template.getPrice()` sẽ dừng phê duyệt và báo lỗi `VALIDATION_FAILED`.
