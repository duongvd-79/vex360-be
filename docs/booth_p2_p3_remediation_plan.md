# Kế hoạch xử lý P2/P3 cho luồng hoàn thiện booth

## 1. Mục tiêu

Hoàn thiện các ràng buộc còn lại sau P1 để:

- Mốc đóng đăng ký và gửi duyệt booth có ý nghĩa thống nhất, không lệch ngày hoặc múi giờ.
- Exhibition tự đi qua đúng lifecycle theo ngày diễn ra.
- Organizer và exhibitor nhìn thấy deadline/readiness trước khi thao tác thất bại.
- Hệ thống có nhắc hạn, báo cáo và dấu vết đủ để vận hành.

P1 được xem là baseline đã có:

- Chỉ nhận đăng ký khi exhibition còn `REGISTRATION` và trước deadline.
- Publish bị chặn nếu còn registration hoặc booth chưa sẵn sàng.
- Race đăng ký/publish và `startEdit`/publish dùng cùng pessimistic lock.
- Booth bị `REJECTED` được nộp lại sau deadline khi exhibition vẫn còn `REGISTRATION`.

## 2. Giả định và câu hỏi cần chốt

### Giả định

1. `startDate` và `endDate` là ngày nghiệp vụ tại Việt Nam, không phải ngày UTC.
2. Deadline chuẩn bị booth được suy ra từ `startDate`, mặc định là T−3 ngày.
3. Không đổi endpoint hiện có nếu chỉ cần bổ sung field response hoặc xử lý nền.
4. Không thêm dependency/migration nếu giải pháp hiện tại đáp ứng được; mọi thay đổi schema phải được duyệt riêng.

### Open questions

1. Exhibitor có được thao tác trong toàn bộ ngày T−3 không?
   - Khuyến nghị: có, deadline kết thúc lúc `23:59:59` ngày T−3 theo `Asia/Ho_Chi_Minh`.
2. Exhibition phải được tạo trước ngày bắt đầu tối thiểu bao nhiêu ngày?
   - Khuyến nghị sản phẩm: ít nhất 7 ngày.
   - Mức kỹ thuật tối thiểu để không vô hiệu hóa luồng booth: lớn hơn khoảng T−3.
3. `PUBLISHED → ACTIVE → COMPLETED` chạy theo đầu ngày hay theo giờ cấu hình?
4. Khi scheduler chuyển trạng thái thất bại, có cần cảnh báo admin ngoài log hay không?
5. P3 có cho phép migration để thêm khóa chống gửi notification trùng không?

Không triển khai các mục phụ thuộc câu trả lời trên trước khi product owner xác nhận.

## 3. P2 — Sửa tính đúng đắn của deadline và lifecycle

### P2.1. Chuẩn hóa deadline, loại bỏ off-by-one

#### Hiện trạng

`BoothReviewPolicyService` và `ExhibitorRegistrationServiceImpl` đang dùng:

```java
LocalDate.now(clock).isBefore(exhibition.getStartDate().minusDays(3))
```

Biểu thức này từ chối toàn bộ ngày T−3. Ngoài ra `Clock.systemUTC()` khiến ngày nghiệp vụ có thể lệch 7 giờ so với Việt Nam.

#### Cách xử lý

Tạo một policy dùng chung, ví dụ `ExhibitionTimelinePolicy`, chịu trách nhiệm:

- Tính `boothReviewDeadline`.
- Kiểm tra còn nhận đăng ký hay không.
- Kiểm tra còn được gửi review/start edit hay không.
- Trả deadline và trạng thái deadline cho mapper/DTO.

Không lặp lại công thức ngày trong nhiều service.

Nếu chọn chính sách cho phép hết ngày T−3:

```java
boolean isBoothPreparationOpen(Exhibition exhibition) {
    LocalDate deadline = exhibition.getStartDate().minusDays(3);
    return !LocalDate.now(clock).isAfter(deadline);
}
```

`Clock` phải chạy theo `Asia/Ho_Chi_Minh`, hoặc policy phải chuyển `Instant` sang `ZoneId` nghiệp vụ một cách tường minh. Không dùng `LocalDate.now()` trực tiếp trong service.

#### File dự kiến

- `shared/config/TimeConfig.java`
- `features/exhibition/services/ExhibitionTimelinePolicy.java` (mới)
- `features/exhibition/services/impl/ExhibitorRegistrationServiceImpl.java`
- `features/booth/services/BoothReviewPolicyService.java`
- Test tương ứng trong module exhibition/booth.

#### Acceptance criteria

- Trước T−3: đăng ký, start edit và gửi review hoạt động.
- Trong ngày T−3: hành vi đúng với chính sách đã chốt.
- T−2 trở đi: request mới bị từ chối, trừ grace path `REJECTED` của P1.
- Cùng một `Instant` cho kết quả đúng quanh 00:00 tại Việt Nam.
- Registration và booth review dùng cùng một policy, không còn công thức deadline riêng.

### P2.2. Bảo đảm exhibition có đủ lead time

#### Rủi ro

`@FutureOrPresent` cho phép tạo exhibition quá sát ngày bắt đầu. Khi admin duyệt, deadline T−3 có thể đã hết nên không exhibitor nào có thể đăng ký/hoàn thiện booth.

#### Cách xử lý

- Kiểm tra lead time ở cả create/update và thời điểm admin approve.
- Create/update trả `VALIDATION_FAILED` nếu `startDate` không đạt minimum lead time.
- Admin approve trả `EXHIBITION_INVALID_STATUS` nếu thời gian còn lại không đủ.
- Dùng cùng `ExhibitionTimelinePolicy`; không hard-code số ngày tại ba nơi.
- Nếu muốn cấu hình số ngày, dùng application property hiện có của Spring, không thêm thư viện.

#### Acceptance criteria

- Không thể đưa exhibition sang `REGISTRATION` khi deadline booth đã hết.
- Update ngày bắt đầu không thể rút ngắn xuống dưới minimum lead time.
- Test bao phủ đúng biên `minimumLeadDays - 1`, `minimumLeadDays` và `minimumLeadDays + 1`.

### P2.3. Tự động hóa lifecycle exhibition

#### Hiện trạng

Không có scheduler/service production chuyển:

```text
PUBLISHED → ACTIVE → COMPLETED
```

`ACTIVE` và `COMPLETED` hiện chủ yếu xuất hiện từ seed/manual data. Visitor API lại chỉ cho xem booth khi exhibition là `ACTIVE`.

#### Cách xử lý

Thêm `ExhibitionLifecycleService` và scheduler mỏng:

1. Tìm `PUBLISHED` có `startDate <= businessDate`.
2. Lấy pessimistic lock từng exhibition.
3. Kiểm tra lại trạng thái/ngày sau khi có lock.
4. Chuyển sang `ACTIVE`.
5. Tìm `ACTIVE` có `endDate < businessDate`, khóa và chuyển `COMPLETED`.

Yêu cầu:

- Job idempotent: chạy lặp không phát event hoặc update thừa.
- Xử lý theo batch/page, không load toàn bộ bảng.
- Một exhibition lỗi không làm dừng toàn bộ batch.
- Không dùng readiness gate lần nữa khi kích hoạt; gate đã được thực thi tại lúc publish.
- Nếu vẫn muốn manual recovery endpoint, endpoint phải gọi cùng lifecycle service thay vì tự set status.

#### Repository dự kiến

- Finder theo `status` và ngày, có pageable.
- Finder `findByIdForUpdate()` đã có từ P1.
- Không dùng bulk update trực tiếp nếu cần event/audit cho từng exhibition.

#### Acceptance criteria

- Job chạy hai lần cho cùng ngày cho kết quả giống nhau.
- Exhibition bắt đầu hôm nay chuyển đúng một lần sang `ACTIVE`.
- Exhibition kết thúc hôm qua chuyển đúng một lần sang `COMPLETED`.
- Exhibition sai trạng thái không bị nhảy cóc.
- Hai scheduler instance đồng thời không tạo trạng thái/event trùng.

## 4. P3 — Khả năng quan sát và vận hành

### P3.1. Trả deadline và readiness qua API

#### Cách xử lý

Bổ sung các field chỉ đọc vào response phù hợp:

- `boothReviewDeadline`
- `boothPreparationOpen`
- `daysUntilBoothDeadline`
- `readinessStatus`
- `readinessBlockerCount` cho organizer

`readinessStatus` nên là enum ổn định, ví dụ:

```text
READY
REGISTRATION_PENDING
PAYMENT_PENDING
BOOTH_DRAFT
BOOTH_REVIEW_PENDING
```

Không trả chi tiết của company khác cho exhibitor. Organizer chỉ xem dữ liệu thuộc exhibition mình sở hữu.

Tái sử dụng query readiness của P1; không chạy N+1 query cho từng booth.

#### Acceptance criteria

- Exhibitor nhìn thấy deadline và trạng thái booth trong API hiện có.
- Organizer nhìn thấy tổng số blocker trước khi bấm publish.
- Deadline trong response khớp chính xác với policy dùng để chặn thao tác.
- Không làm lộ company/registration ngoài phạm vi authorization.

### P3.2. Nhắc hạn và escalation

#### Cách xử lý

Tạo scheduled reminder chạy theo business date:

- Nhắc exhibitor có booth chưa `PUBLISHED` tại T−7, T−3 và T−1.
- Nhắc organizer khi còn booth `PENDING` cần review.
- Escalate admin/organizer nếu exhibition sắp bắt đầu nhưng chưa thể publish.

Ưu tiên notification trong ứng dụng trước; email là kênh bổ sung và không được làm rollback transaction chính.

Chống gửi trùng:

- Khuyến nghị dùng `deduplicationKey` duy nhất theo
  `eventType + exhibitionId + recipientId + milestone`.
- Nếu cần thêm cột/index, phải duyệt migration trước.
- Nếu chưa được phép migration, lưu delivery log riêng hoặc dùng outbox sẵn có; không chống trùng bằng kiểm tra title tự do.

#### Acceptance criteria

- Cùng một milestone chỉ tạo một notification cho một recipient.
- Job retry không gửi trùng.
- Lỗi email không làm mất notification trong ứng dụng.
- Deep link dẫn đúng booth/exhibition và vẫn qua authorization ở API đích.

### P3.3. Audit và số liệu vận hành

#### Cách xử lý

Ghi structured log/metric cho:

- Publish bị chặn theo từng loại blocker.
- Số booth chưa sẵn sàng theo exhibition.
- Lifecycle transition thành công/thất bại.
- Reminder đã tạo, bỏ qua do trùng hoặc gửi lỗi.

Không log checkout URL, token, nội dung file hoặc dữ liệu cá nhân không cần thiết.

Nếu dự án chưa có metrics backend, bắt đầu bằng structured log; không thêm observability stack chỉ cho tính năng này.

#### Acceptance criteria

- Có thể xác định vì sao một exhibition không publish được từ log/dashboard.
- Có thể truy ngược transition bằng exhibition UUID và trạng thái trước/sau.
- Log không chứa secret hoặc dữ liệu nhạy cảm.

## 5. Thứ tự triển khai

### Slice 1 — Timeline policy

- Viết test biên ngày/múi giờ trước.
- Tạo policy dùng chung.
- Chuyển registration và booth review sang policy.
- Chạy suite exhibition/booth.

### Slice 2 — Lead time

- Chốt `minimumLeadDays`.
- Viết test create/update/approve.
- Áp dụng policy tại ba điểm.

### Slice 3 — Lifecycle

- Viết repository finder và service transition.
- Test idempotency/race.
- Thêm scheduler mỏng sau khi service đã xanh.

### Slice 4 — Readiness API

- Chốt enum/field response với frontend.
- Bổ sung aggregate query và mapper.
- Test authorization và query count.

### Slice 5 — Reminder/audit

- Chốt cơ chế deduplication và migration.
- Viết notification service idempotent.
- Thêm scheduler, structured log và test retry.

Không gộp P2 và P3 thành một PR lớn. Mỗi slice phải chạy được và có thể rollback độc lập.

## 6. Chiến lược kiểm thử

### Unit test

- Deadline trước/trong/sau T−3.
- Chuyển ngày tại `Asia/Ho_Chi_Minh`.
- Minimum lead time.
- State machine và scheduler idempotency.
- Notification deduplication.

### Repository/integration test

- Query blocker thật cho:
  - `PENDING`
  - `PENDING_PAYMENT`
  - `APPROVED` không có booth
  - booth template-only
  - booth non-template `PUBLISHED`
- Query lifecycle theo ngày và pagination.

### Concurrency smoke test trên MySQL

- Register cạnh tranh với publish.
- `startEdit` cạnh tranh với publish.
- Hai lifecycle worker xử lý cùng exhibition.
- Hai reminder worker xử lý cùng milestone.

### Commands

```powershell
.\mvnw.cmd -q "-Dtest=BoothReviewPolicyServiceUnitTest,BoothReviewServiceUnitTest,ExhibitorRegistrationServiceTest,ExhibitionServiceUnitTest" test
.\mvnw.cmd -q test
```

## 7. Boundaries

### Luôn thực hiện

- Dùng `Clock`/business timezone có thể kiểm thử.
- Giữ controller mỏng; rule nằm ở policy/service.
- Lấy pessimistic lock trước khi quyết định transition cuối.
- Viết test RED trước khi sửa hành vi.
- Giữ backward compatibility nếu chỉ bổ sung field response.

### Phải hỏi trước

- Đổi định nghĩa ngày T−3.
- Chọn `minimumLeadDays`.
- Thêm migration/index/deduplication key.
- Thêm dependency như Testcontainers hoặc metrics SDK.
- Gửi email/SMS thật.

### Không thực hiện

- Không set trạng thái bằng scheduler và controller theo hai logic khác nhau.
- Không dùng `LocalDate.now()` trực tiếp trong business service.
- Không bỏ readiness gate P1 để “cứu” exhibition sát ngày.
- Không dùng job chạy lặp mà thiếu idempotency.
- Không log secret, payment URL hoặc token.

## 8. Definition of done

- Product owner đã chốt timezone, deadline inclusivity và minimum lead time.
- P2 có test biên ngày, timezone, lifecycle và concurrency.
- P3 trả đúng deadline/readiness, reminder không trùng và có audit.
- Các suite liên quan và toàn bộ `mvn test` đều pass.
- Smoke test MySQL chứng minh lock/race đúng.
- OpenAPI/frontend contract được cập nhật nếu response có field mới.
- Không phát sinh endpoint, dependency hoặc migration ngoài phần đã được duyệt.
