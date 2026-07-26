# Backlog: đổi `ExhibitorRegistration.company` sang kiểu `Company`

## 0. Trạng thái refactor

**Trạng thái:** `READY FOR CODEBASE REFACTOR`.

**Giả định bắt buộc:** migration database đã hoàn thành ngoài codebase và không còn
là một phần của backlog này. Schema hiện tại được coi là baseline:

```text
exhibitor_registrations.company_id BINARY(16) NOT NULL
    → companies.id
```

`company_user_id` được coi là đã bị xóa. Không viết lại, chạy lại hoặc rollback
migration database trong quá trình refactor.

Các quyết định đã chốt để bắt đầu:

- Không đổi endpoint, request DTO hoặc chữ ký public của controller/service.
- Giữ `companyUserId` trong response và map từ `Company.ownerUser.id`; chưa thêm
  `companyId` trong refactor này.
- `companyName` lấy từ `Company.name`.
- Giữ `companyEmail` lấy từ `Company.ownerUser.email` để không âm thầm đổi API
  semantics; đổi sang `Company.email` là backlog riêng.
- Chỉ owner user được thao tác registration; chưa mở rộng sang company member.
- Thứ tự khóa của luồng đăng ký là `company → exhibition`.

Hệ quả triển khai:

- Backend hiện tại đang map cột legacy nên được coi là không tương thích với schema mới.
- Bắt đầu refactor từ entity/lookup trước, sau đó sửa repository, service và consumer.
- Không khởi động backend cũ để Hibernate tạo lại `company_user_id`.

## 1. Mục tiêu

Đổi quan hệ hiện tại:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "company_user_id", nullable = false)
User company;
```

thành:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "company_id", nullable = false)
Company company;
```

Sau thay đổi:

- Registration thuộc về pháp nhân `Company`, không thuộc trực tiếp owner `User`.
- Thay đổi owner user trong tương lai không làm mất ownership của registration.
- Booth, product, media, design request và registration dùng cùng một company identity.
- Khóa chống đăng ký trùng tiếp tục serialize theo company.
- Không làm hỏng payment/webhook, booth provisioning hoặc authorization hiện có.

## 2. Hiện trạng và phạm vi ảnh hưởng

### Persistence

- `ExhibitorRegistration.company` đang là `User`.
- Codebase vẫn map `company_user_id`, nhưng database được giả định đã chỉ còn
  `company_id → companies.id`.
- `Company.owner_user_id` vẫn là one-to-one và unique.
- Đây là code/schema mismatch có chủ đích trong lúc refactor; source phải chuyển
  sang `Company` trước khi chạy backend.

### Registration service

- `initializeRegistration()` khóa `User` trước khi kiểm tra đăng ký trùng.
- `getRegistrationDetails()` và `cancelRegistration()` so sánh trực tiếp
  `registration.company.id` với authenticated user ID.
- `getRegistrationsForExhibitor()` truyền user ID vào repository query.
- Response đang lấy `companyName`/`companyEmail` từ `User`.

### Repository

- `existsActiveRegistration()` hiện hiểu `companyId` là user ID.
- Search organizer dùng `r.company.fullName`.
- Search exhibitor dùng `r.company.id = :companyId`.
- Các `LEFT JOIN FETCH r.company` hiện fetch `User`, sau refactor sẽ fetch `Company`.

### Booth provisioning

`BoothProvisioningService` hiện:

1. Lấy `User` từ registration.
2. Query lại `Company` qua owner user.
3. Dùng user làm `Booth.createdBy`.
4. Dùng company làm `Booth.company`.

Sau refactor, bước 2 phải được loại bỏ; `createdBy` lấy từ
`registration.company.ownerUser`.

### API/frontend

- `ExhibitorRegistrationResponseDTO` có `companyUserId`, chưa có `companyId`.
- Frontend chủ yếu dùng `companyName` và `companyEmail`; `companyUserId` xuất hiện trong contract/JSDoc nhưng chưa thấy dùng cho quyết định UI.

### Database baseline

- Migration, backfill, `NOT NULL`, index và foreign key được coi là đã hoàn thành.
- Backlog không thay đổi Flyway, Hibernate DDL policy hoặc schema.
- Nếu schema thực tế khác baseline, dừng refactor/deploy và xử lý như một database
  incident riêng; không mở rộng scope của các task code bên dưới.

## 3. Quyết định thiết kế đã chốt

### Ownership

- Registration được sở hữu bởi `Company`.
- Authenticated user chỉ là actor.
- Trong mô hình hiện tại, actor hợp lệ là `registration.company.ownerUser`.
- Nếu sau này có company member, authorization phải đi qua membership guard; không đổi
  semantics đó trong backlog này.

### Public service contract

Giữ controller/service public nhận authenticated `User` hoặc user ID để tránh đổi endpoint:

```java
initializeRegistration(UUID exhibitorUserId, ...)
getRegistrationDetails(UUID registrationUuid, UUID exhibitorUserId)
cancelRegistration(User exhibitor, UUID registrationUuid)
```

Service phải resolve user → company một lần, sau đó so sánh bằng company ID.

### Response contract

Giữ nguyên shape của `ExhibitorRegistrationResponseDTO` trong refactor này:

- `companyUserId` map từ `company.ownerUser.id`.
- `companyName` map từ `company.name`.
- `companyEmail` tiếp tục map từ `company.ownerUser.email`.
- Chưa thêm `companyId`; nếu frontend cần company identity thật, tạo API task riêng
  sau khi refactor hoàn tất.

Quyết định này giới hạn blast radius ở persistence và business logic, đồng thời sửa
đúng lỗi tên công ty mà không thay đổi hợp đồng API ngoài ý muốn.

### Lock order

Luồng khởi tạo registration phải giữ thứ tự:

```text
company → exhibition
```

Không khóa `User` thay cho `Company`.

Thêm finder:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    SELECT company
    FROM Company company
    WHERE company.ownerUser.id = :ownerUserId
    """)
Optional<Company> findByOwnerUserIdForUpdate(UUID ownerUserId);
```

Hai request đồng thời từ cùng company phải serialize trên cùng company row trước
khi chạy `existsActiveRegistration()`.

## 4. Database baseline đã hoàn thành

Backlog lấy các điều kiện sau làm đầu vào, không phải task cần triển khai:

- `exhibitor_registrations.company_id` tồn tại và là `NOT NULL`.
- `company_id` có index và foreign key tới `companies(id)`.
- Không còn `company_user_id`.
- Dữ liệu registration đã được backfill và không có orphan.
- Backup/rollback database thuộc quy trình vận hành đã hoàn tất.

Trong refactor chỉ được sửa Java code, test, seed và contract documentation cần
thiết. Không thêm migration SQL, không dual-write và không tạo compatibility field
cho cột legacy.

## 5. Backlog triển khai

### ER-COMPANY-001 — Quyết định API (`COMPLETED`)

- [x] Giữ `companyUserId` và map từ `Company.ownerUser.id`.
- [x] Giữ `companyEmail` từ `Company.ownerUser.email`.
- [x] Không thêm `companyId` trong response ở refactor này.
- [x] Không đổi endpoint, request DTO hoặc chữ ký public.

Acceptance:

- Các quyết định API ở trên là đầu vào cố định cho implementation.
- Không cần thay đổi frontend contract.

Verify:

- Review contract `ExhibitorRegistrationResponseDTO`.
- Review `vex360-fe/src/api/registrations.js` và organizer registration mapper.

### ER-COMPANY-002 — Database migration (`COMPLETED/OUT OF SCOPE`)

- [x] Tạo và backfill `company_id`.
- [x] Đặt `company_id NOT NULL`.
- [x] Thêm index và foreign key tới `companies(id)`.
- [x] Xóa `company_user_id`.
- [x] Hoàn tất backup/rollback procedure ở tầng vận hành.

Acceptance:

- Codebase được phép giả định schema target đã sẵn sàng.
- Không có migration work trong các task refactor.

Verify:

- Không chạy lại migration trong backlog này.

### ER-COMPANY-003 — Company lookup và locking

- [ ] Thêm `CompanyRepository.findByOwnerUserIdForUpdate()`.
- [ ] Thêm method tương ứng trong `CompanyService`.
- [ ] Từ chối actor không có company bằng `COMPANY_NOT_FOUND`.
- [ ] Giữ lock order `company → exhibition`.

Acceptance:

- Hai request đăng ký đồng thời của cùng company không cùng vượt duplicate check.
- Không còn dùng `UserService.getUserEntityByIdForUpdate()` để serialize registration.

Verify:

- Unit test finder được gọi trước exhibition lock.
- MySQL concurrency smoke test chỉ tạo một active registration.

Files:

- `CompanyRepository.java`
- `CompanyService.java`
- Test company/registration liên quan.

### ER-COMPANY-004 — Đổi entity sang `Company`

- [ ] Đổi type/import trong `ExhibitorRegistration`.
- [ ] Map sang `company_id`.
- [ ] Không đổi `reviewedBy`; field này vẫn là `User`.
- [ ] Cập nhật builder fixture tối thiểu để code compile.

Acceptance:

- Entity association là `ManyToOne<Company>`.
- Schema validation nhận đúng FK `company_id`.
- Không còn entity field trỏ tới `company_user_id`.

Verify:

- Persistence test load registration rồi đọc được company và owner user.
- Test lazy association trong transaction.

Files:

- `ExhibitorRegistration.java`
- Entity/persistence test.

### ER-COMPANY-005 — Sửa repository query

- [ ] `existsActiveRegistration()` nhận company UUID thật.
- [ ] Organizer search dùng `r.company.name`, không dùng `fullName`.
- [ ] Exhibitor search lọc bằng resolved `company.id`.
- [ ] Fetch `company.ownerUser` chỉ khi response cần `companyUserId`/fallback email.
- [ ] Kiểm tra lại count query khớp main query.

Target semantics:

```text
r.company.id = :companyId
LOWER(r.company.name) LIKE ...
```

Acceptance:

- Search theo tên pháp nhân company.
- User khác không đọc được registration chỉ vì biết UUID.
- Không phát sinh N+1 khi map owner user.

Verify:

- Repository integration test với hai company, hai owner.
- Test keyword company name và exhibition name.
- Test pagination total count.

Files:

- `ExhibitorRegistrationRepository.java`
- Repository integration test.

### ER-COMPANY-006 — Sửa registration service và authorization

- [ ] `initializeRegistration()` resolve và lock `Company`.
- [ ] Duplicate check dùng `company.id`.
- [ ] `getRegistrationsForExhibitor()` resolve company trước khi query.
- [ ] `getRegistrationDetails()` so sánh resolved company ID.
- [ ] `cancelRegistration()` so sánh resolved company ID.
- [ ] Đổi tên biến `companyUserId` thành `exhibitorUserId` ở nội bộ để tránh nhầm type.
- [ ] Không đổi endpoint/request path.

Authorization đề xuất:

```java
Company actorCompany = companyService.getCompanyEntityForCurrentUser(exhibitor);
if (!registration.getCompany().getId().equals(actorCompany.getId())) {
    throw new AppException(ErrorCode.UNAUTHORIZED);
}
```

Acceptance:

- Ownership được xác định bằng company ID.
- Owner hợp lệ giữ nguyên mọi chức năng registration/payment/cancel.
- User thuộc company khác luôn bị `UNAUTHORIZED`.
- P1 registration/publish locking vẫn pass.

Verify:

- Mở rộng `ExhibitorRegistrationServiceTest`.
- Test company A không đọc/hủy registration company B.
- Test registration re-register sau `REJECTED/CANCELED`.

Files:

- `ExhibitorRegistrationService.java`
- `ExhibitorRegistrationServiceImpl.java`
- `ExhibitorRegistrationServiceTest.java`

### ER-COMPANY-007 — Sửa response DTO và mapper

- [ ] Không thêm `companyId`; giữ nguyên response shape.
- [ ] Map `companyUserId` từ `company.ownerUser.id`.
- [ ] Map `companyName` từ `company.name`.
- [ ] Map `companyEmail` từ `company.ownerUser.email`.
- [ ] Chỉ cập nhật OpenAPI/JSDoc nếu mô tả hiện tại sai semantics.

Acceptance:

- Organizer thấy đúng tên pháp nhân, không phải `User.fullName`.
- Existing frontend không vỡ vì response shape được giữ nguyên.
- Không expose thêm owner data ngoài contract.

Verify:

- Unit test mapping company/owner có tên khác nhau.
- Frontend normalization test hoặc smoke test registration list/detail.

Files:

- `ExhibitorRegistrationResponseDTO.java`
- `ExhibitorRegistrationServiceImpl.java` hoặc mapper được tách riêng.
- `vex360-fe/src/api/registrations.js`
- `vex360-fe/src/api/organizerExhibitions.js` nếu contract thay đổi.

### ER-COMPANY-008 — Đơn giản hóa booth provisioning

- [ ] Bỏ dependency `CompanyService` nếu không còn consumer khác trong service này.
- [ ] Dùng trực tiếp `registration.getCompany()`.
- [ ] Dùng `registration.getCompany().getOwnerUser()` cho `Booth.createdBy`.
- [ ] Giữ idempotency theo `exhibitorRegistrationId`.

Target:

```java
Company company = registration.getCompany();
Booth booth = Booth.builder()
        .createdBy(company.getOwnerUser())
        .company(company)
        .exhibitorRegistration(registration)
        .build();
```

Acceptance:

- Event free-package và PayOS webhook vẫn provision đúng một booth.
- Booth company trùng registration company.
- Không còn query vòng User → Company khi provision.

Verify:

- `BoothProvisioningServiceUnitTest`.
- `PayOSWebhookServiceTest`.
- Test event lặp không tạo booth thứ hai.

Files:

- `BoothProvisioningService.java`
- `BoothProvisioningServiceUnitTest.java`
- Webhook/event test liên quan.

### ER-COMPANY-009 — Cập nhật seed và test fixtures

- [ ] Đổi helper seed registration nhận `Company`.
- [ ] Vẫn truyền `User reviewedBy` riêng.
- [ ] Cập nhật mọi `ExhibitorRegistration.builder().company(...)` trong test.
- [ ] Tạo test fixture factory nhỏ nếu giúp tránh lặp; không refactor unrelated tests.

Các suite bị ảnh hưởng trực tiếp:

- `ExhibitorRegistrationServiceTest`
- `PayOSWebhookServiceTest`
- `BoothProvisioningServiceUnitTest`
- `BoothReviewPolicyServiceUnitTest`
- `BoothReviewServiceUnitTest`
- `BoothBenefitGuardServiceUnitTest`
- `DesignDraftBenefitGuardServiceUnitTest`
- `ExhibitorBoothTemplateServiceUnitTest`
- `DataSeeder`

Acceptance:

- Không còn builder truyền `User` vào `registration.company`.
- Seed tạo registration, booth và company nhất quán.

Verify:

```powershell
rg -n "ExhibitorRegistration\.builder|\.company\(" src/main/java src/test/java
```

Review thủ công chỉ các match thuộc `ExhibitorRegistration`; các entity khác vẫn dùng
`Company` như hiện tại.

### ER-COMPANY-010 — Codebase cleanup và final verification

- [ ] Xóa mọi production-code reference tới `company_user_id`.
- [ ] Xóa import, mock và dependency trở nên thừa do refactor.
- [ ] Xác nhận không có logic nào còn hiểu `registration.company.id` là user ID.
- [ ] Giữ `companyUserId` ở DTO vì đây là API field, không phải legacy DB column.
- [ ] Chạy targeted suites, full test và concurrency smoke test.

Acceptance:

- Source code chỉ map `company_id`.
- Không còn string `company_user_id` trong production Java code.
- Không còn test fixture truyền `User` vào `ExhibitorRegistration.company`.
- Toàn bộ verification pass.

Verify:

```powershell
rg -n "company_user_id" src/main/java
rg -n "getCompany\(\)\.getId\(\)" src/main/java/com/example/vex360/features
.\mvnw.cmd -q test
```

## 6. Thứ tự thực hiện

```text
001 API decisions (completed)
002 DB migration (completed/out of scope)
    ↓
003 Company lookup/locking
    ↓
004 Entity
    ↓
005 Repository
    ↓
006 Service/auth ──→ 007 DTO/frontend
    ↓
008 Booth provisioning
    ↓
009 Seed/tests
    ↓
010 Codebase cleanup/final verification
```

Active implementation bắt đầu từ ER-COMPANY-003. ER-COMPANY-003 và 004 nên được
land cùng một compile-safe increment vì service lookup và entity type phụ thuộc nhau.

## 7. Test strategy

### Unit

- Company lookup/lock.
- Registration ownership theo company.
- DTO company vs owner fields.
- Booth provisioning trực tiếp từ company.

### Repository integration

- Relation `company_id` load đúng.
- Duplicate query theo company ID.
- Search organizer theo company name.
- Search exhibitor không trả company khác.

### Payment/event regression

- Free package approval.
- Paid PayOS callback.
- Callback lặp.
- Registration canceled trước callback.
- Booth provisioning idempotent.

### Concurrency trên MySQL

- Hai register request cùng company/cùng exhibition.
- Register cạnh tranh với publish.
- Owner user khác không thể thao tác registration không thuộc company mình.

### Commands

```powershell
.\mvnw.cmd -q "-Dtest=ExhibitorRegistrationServiceTest,PayOSWebhookServiceTest,BoothProvisioningServiceUnitTest" test
.\mvnw.cmd -q test
```

## 8. Boundaries

### Luôn thực hiện

- Authorization bằng company ID.
- Lock company trước exhibition trong initialize flow.
- Giữ payment snapshot và registration snapshot không đổi.
- Giữ response API tương thích theo quyết định ER-COMPANY-001.
- Chạy full regression trước khi hoàn tất refactor.

### Phải hỏi trước

- Drop `companyUserId` khỏi response.
- Đổi `companyEmail` sang `Company.email`.
- Cho phép company member ngoài owner thao tác registration.
- Thay đổi schema hoặc chạy lại migration database.

### Không thực hiện

- Không tạo lại `company_user_id` hoặc dual-write legacy column.
- Không thêm migration SQL trong refactor này.
- Không đổi `reviewedBy` sang `Company`.
- Không truyền company ID từ client để quyết định ownership; phải resolve từ actor.
- Không khởi động backend cũ trên schema target.

## 9. Definition of done

- `ExhibitorRegistration.company` là `Company`.
- Source code chỉ dùng baseline `company_id → companies.id`.
- Duplicate registration được kiểm tra và khóa theo company.
- Registration list/detail/cancel được authorize theo company.
- Organizer search hiển thị và tìm đúng tên company.
- Free/paid approval vẫn provision đúng booth.
- API/frontend contract giữ nguyên shape và có semantics đúng cho company/owner.
- Targeted suites và toàn bộ `mvn test` pass.
- MySQL concurrency smoke test pass.
- Không còn production-code reference tới `company_user_id`.
