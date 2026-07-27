# Bàn giao FE: getList của ADMIN, ORGANIZER và EXHIBITOR

Ngày rà soát: 2026-07-27  
Nguồn xác minh: controller, service, repository của `vex360-be` và các API/page hiện tại trong `vex360-fe`.

Tài liệu ghi route theo dạng FE gọi là `/v1/...`. Trong source BE, các route có prefix đầy đủ `/api/v1/...`; FE cần giữ `/api` trong `baseURL` hoặc thêm prefix này khi cấu hình HTTP client.

## 1. Kết luận nhanh

Backend đã dùng `PageResponse` và mặc định `page=0`, `size=10` cho các getList trong phạm vi. Các capability Backend được phát hiện trong đợt rà soát đã được bổ sung; phần việc còn lại chủ yếu là chuyển FE sang server-side pagination/filter/search/sort.

### Trạng thái Backend

Không còn blocker Backend đã biết trong nhóm endpoint được tài liệu này liệt kê.

### Blocker phía Frontend hiện tại

| Mức độ | Khu vực FE | Hiện trạng cần sửa |
|---|---|---|
| Blocker | Enum dùng chung | FE vẫn dùng `CANCELED`; BE chỉ còn nhận/trả `CANCELLED`. Filter cũ có thể trả lỗi validation, còn response `CANCELLED` có thể bị hiển thị sai trạng thái. |
| Blocker | `src/api/storagePackages.js` | `fetchAdminStoragePackages()` vẫn parse response như array. BE hiện trả `data.content`, vì vậy màn hình có thể nhận danh sách rỗng. |
| Blocker | Exhibitor Products | FE chỉ tải trang đầu 10 phần tử rồi tìm kiếm/lọc cục bộ; không gửi `keyword`, `categoryId`, `sort`. Kết quả sẽ thiếu khi có hơn 10 sản phẩm. |
| Blocker | Exhibitor Media | FE chỉ tải trang đầu 10 phần tử rồi lọc loại file cục bộ; không gửi `filterType`, `page`, `size`. |
| Blocker | Exhibitor Design Requests | FE gọi `size=100`, không gửi `keyword`, sau đó phân trang cục bộ với `PAGE_SIZE=5`; không đúng contract mới `size=10`. |
| Cần chuyển đổi | Organizer Booths/Registrations | FE vẫn gọi `size=100` và tự phân trang. Cần chuyển sang metadata do server trả về và `size=10`. |
| Cần chuyển đổi | Admin Design/Exhibitions/Partnership | Một số API vẫn tải `size=100/1000` rồi xử lý cục bộ, chưa dùng server-side pagination đầy đủ. |

## 2. Response phân trang chuẩn

Các endpoint được đánh dấu `PageResponse` trả về:

```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "content": [],
    "page": 0,
    "size": 10,
    "totalElements": 0,
    "totalPages": 0,
    "first": true,
    "last": true
  }
}
```

FE phải đọc:

```js
const pageData = response.data?.data ?? {};
const items = Array.isArray(pageData.content) ? pageData.content : [];

return {
  items,
  page: pageData.page ?? 0,
  size: pageData.size ?? 10,
  totalElements: pageData.totalElements ?? 0,
  totalPages: pageData.totalPages ?? 0,
  first: pageData.first ?? true,
  last: pageData.last ?? true,
};
```

Quy ước chung:

- `page` của BE là 0-indexed. Nếu UI hiển thị từ 1 thì request phải gửi `uiPage - 1`.
- Mặc định dùng `size=10`.
- Khi đổi keyword/filter/sort, reset `page=0`.
- Không tải `size=100/1000` để tự lọc hoặc tự phân trang.
- Multi-sort dùng nhiều query param:

```text
?sort=status,asc&sort=createdAt,desc
```

- Với filter enum, lựa chọn `all` phải được FE thể hiện bằng cách bỏ query param.
- Không gửi alias `q`, `search`; dùng duy nhất `keyword`.
- Enum gửi lên API dùng chữ hoa đúng tên constant, ngoại trừ Storage Package và Media có parser riêng được mô tả bên dưới.

## 3. Contract ADMIN

### 3.1. Users

`GET /v1/users`

Trạng thái: Sẵn sàng.

| Nhóm | Giá trị |
|---|---|
| Pagination | `page=0`, `size=10` |
| Keyword | `keyword`; tìm `fullName`, `email`; không tìm `phoneNumber` |
| Filter | `role`, `status` |
| Role | `ADMIN`, `ORGANIZER`, `EXHIBITOR`, `DESIGNER`, `VISITOR` |
| User status | `ACTIVE`, `INACTIVE`, `PENDING`, `BLOCKED` |
| Sort FE được dùng | `fullName`, `email`, `role`, `status` |
| Default sort | `email,asc` |

Ví dụ:

```text
GET /v1/users?page=0&size=10&keyword=an&role=EXHIBITOR&status=ACTIVE&sort=fullName,asc
```

### 3.2. Admin Design Requests

`GET /v1/admin/design-requests`

Trạng thái: Backend sẵn sàng; FE cần bỏ whitelist sort cũ và đổi enum.

| Nhóm | Giá trị |
|---|---|
| Pagination | `page=0`, `size=10` |
| Keyword | `keyword`; tìm `booth.name`, tên triển lãm, `company.name`, `assignedDesigner.fullName` |
| Không tìm | UUID/code hiển thị |
| Filter | `status`; đã bỏ `designerId` và `mode` khỏi getList |
| Status | `PENDING`, `ASSIGNED`, `DRAFT_SUBMITTED`, `REVISION_REQUESTED`, `REVISION_QUEUED`, `APPROVED`, `CANCELLED` |
| Sort aliases | `boothName`, `customerCompany`, `assignedDesignerName` |
| Sort trực tiếp | `createdAt`, `status` |
| Default sort | `createdAt,desc` |

Mapping sort phía BE:

| FE gửi | Entity sort |
|---|---|
| `boothName` | `booth.name` |
| `customerCompany` | `company.name` |
| `assignedDesignerName` | `assignedDesigner.fullName` |

Ví dụ:

```text
GET /v1/admin/design-requests?page=0&size=10&keyword=expo&status=PENDING&sort=boothName,asc
```

FE cần sửa:

- `src/api/adminDesignRequests.js`: đổi `CANCELED` thành `CANCELLED`.
- Cho phép ba sort alias nêu trên; comment cho rằng các alias gây lỗi 500 đã không còn đúng.
- Sau khi chuyển `search` sang `keyword`, phải xóa param `search` khỏi request để chỉ gửi contract chuẩn.
- `src/pages/design-requests/AdminDesignRequestsPage.jsx`: đổi label/config `CANCELED` thành `CANCELLED`.
- Chuyển `size=100` sang server pagination `size=10`.

### 3.3. Admin Exhibitions

`GET /v1/admin/exhibitions`

Trạng thái: Backend sẵn sàng; FE cần chuyển sang server pagination.

| Nhóm | Giá trị |
|---|---|
| Pagination | `page=0`, `size=10` |
| Keyword | Tên triển lãm, `organizer.fullName`, `organizer.email`, tên company/tổ chức |
| Filter | `status`, `category`, `startDate`, `endDate` |
| Status | `PENDING`, `REJECTED`, `REGISTRATION`, `PUBLISHED`, `ACTIVE`, `COMPLETED`, `APPROVED` |
| Date format | `YYYY-MM-DD` |
| Default sort | `createdAt,desc` |

`APPROVED` là filter API tổng hợp, không phải trạng thái lưu trong database. Giá trị này lấy:

```text
REGISTRATION, PUBLISHED, ACTIVE, COMPLETED
```

Sort aliases:

| FE gửi | Entity field |
|---|---|
| `organizerName` | `organizer.fullName` |
| `exhibitionName` | `name` |
| `expectedBoothCount` | `estimatedBooths` |

Không tìm theo UUID hoặc mã `#EXR`. Giá trị `all/ALL` không hợp lệ; khi chọn “Tất cả”, FE phải bỏ query param `status`.

FE hiện tại `src/api/exhibitionRegistrationRequests.js` đang tải `page=0,size=1000`. Cần chuyển function này sang nhận đầy đủ `{page,size,keyword,status,category,startDate,endDate,sort}` và parse metadata.

### 3.4. Admin Partnership Requests

`GET /v1/admin/partnership-requests`

Trạng thái: Backend sẵn sàng; FE chưa dùng server filtering/pagination đầy đủ.

| Nhóm | Giá trị |
|---|---|
| Pagination | `page=0`, `size=10` |
| Keyword | `organizationName`, `requesterName`, `requesterEmail` |
| Không tìm | UUID/code `#REQ` |
| Filter | `status`, `requestedRole` |
| Status | `AWAITING_VERIFICATION`, `PENDING`, `APPROVED`, `REJECTED`, `SUPERSEDED` |
| Requested role | `EXHIBITOR`, `ORGANIZER` |
| Default sort | `createdAt,desc` |

Sort aliases:

| FE gửi | Entity sort |
|---|---|
| `companyName` | `organizationName` |
| `contactPerson` | `requesterName` |
| `email` | `requesterEmail` |
| `submittedAt` | `createdAt` |

FE cần sửa `src/api/partnershipRequests.js`: `fetchPartnershipRequests()` phải nhận params và bỏ `page=0,size=1000` cố định.

### 3.5. Admin Package Templates

`GET /v1/admin/package-templates`

Trạng thái: Sẵn sàng, không thay đổi logic chính.

| Nhóm | Giá trị |
|---|---|
| Pagination | `page=0`, `size=10` |
| Keyword | `name`, `description` |
| Status | `ACTIVE`, `INACTIVE` |
| Sort | `name`, `price`, `createdAt` |
| Default sort | `name,asc` |

FE cần cho API function nhận page/filter/sort thay vì luôn gọi trang đầu cố định.

### 3.6. Admin Storage Packages

`GET /v1/admin/storage-packages`

Trạng thái: Backend sẵn sàng; FE đang parse sai response.

| Nhóm | Giá trị |
|---|---|
| Pagination | `page=0`, `size=10` |
| Keyword | Tên gói |
| Status parser | `active`/`true`, `inactive`/`false`, `all`; không phân biệt hoa thường |
| Sort | `name`, `priceVnd`, `createdAt`, `isActive` |
| Default sort | `priceVnd,asc` |

FE bắt buộc sửa `src/api/storagePackages.js`:

```js
export async function fetchAdminStoragePackages(params = {}) {
  const { data: body } = await http.get('/v1/admin/storage-packages', {
    params: { page: 0, size: 10, ...params },
  });
  const pageData = body?.data ?? {};
  return {
    packages: pageData.content ?? [],
    page: pageData.page ?? 0,
    size: pageData.size ?? 10,
    totalElements: pageData.totalElements ?? 0,
    totalPages: pageData.totalPages ?? 0,
    first: pageData.first ?? true,
    last: pageData.last ?? true,
  };
}
```

### 3.7. Admin Storage Package Orders

`GET /v1/admin/storage-packages/orders`

Trạng thái: Backend sẵn sàng; FE cần chuyển parser từ `List` sang `PageResponse`.

| Nhóm | Giá trị |
|---|---|
| Pagination | `page=0`, `size=10` |
| Keyword | `company.name`, `orderCode` |
| Status | `PENDING`, `PAID`, `CANCELLED` |
| Sort alias | `companyName → company.name` |
| Sort trực tiếp | `orderCode`, `amountVnd`, `status`, `createdAt` |
| Default sort | `createdAt,desc` |

Keyword được trim; null/blank tương đương không tìm kiếm. Search, filter và sort đều chạy tại database trước pagination.

Ví dụ:

```text
GET /v1/admin/storage-packages/orders?page=0&size=10&keyword=12345&status=PAID&sort=companyName,asc
```

### 3.8. Booth Templates

`GET /v1/booths/templates`

Trạng thái: Sẵn sàng, không thay đổi logic chính.

| Nhóm | Giá trị |
|---|---|
| Pagination | `page=0`, `size=10` |
| Keyword | `name`, `description` |
| Status | Toàn bộ `BoothStatus`; UI thường dùng `DRAFT`, `PUBLISHED`, `ARCHIVED` |
| Sort | `name`, `status`, `createdAt` |
| Default sort | `name,asc` |

### 3.9. Public Exhibitions liên quan

`GET /v1/public/exhibitions`

Trạng thái: Backend sẵn sàng.

- Không gửi `status`: lấy cả `PUBLISHED`, `ACTIVE`, `COMPLETED`.
- Gửi `PUBLISHED`, `ACTIVE` hoặc `COMPLETED`: lọc chính xác trạng thái đó.
- Gửi một `ExhibitionStatus` hợp lệ khác như `REGISTRATION`: trả trang rỗng.
- Gửi giá trị không thuộc enum: trả lỗi validation.
- Các param khác: `keyword`, `category`, `startDate`, `endDate`, `page`, `size`, `sort`.

## 4. Contract ORGANIZER

### 4.1. Gian hàng tham dự

`GET /v1/organizer/exhibitions/{exhibitionUuid}/booths`

Trạng thái: Backend sẵn sàng; FE cần chuyển sang server pagination.

| Nhóm | Giá trị |
|---|---|
| Pagination | `page=0`, `size=10` |
| Keyword | `booth.name`, `company.name`, `company.ownerUser.fullName` |
| Status UI | `DRAFT`, `PENDING`, `PUBLISHED`, `ARCHIVED` |
| Status BE bổ sung | `DESIGN_REQUEST_PENDING`, `DESIGNING` |
| Default ordering | `PENDING` trước, sau đó `updatedAt,desc` |

Không cần gửi `sort` nếu UI muốn giữ thứ tự ưu tiên mặc định.

FE cần sửa:

- `src/api/organizerExhibitions.js`: `fetchOrganizerExhibitionBooths()` đổi default `size=100` thành `10`.
- Trả và sử dụng đủ `page`, `size`, `totalElements`, `totalPages`, `first`, `last`.
- `src/pages/organizer/OrganizerExhibitionDetailPage.jsx`: bỏ phân trang cục bộ `BOOTHS_PAGE_SIZE=8`.

### 4.2. Đơn đăng ký tham dự triển lãm

Route chuẩn:

```text
GET /v1/organizer/exhibitions/{exhibitionUuid}/registrations
```

Route tương thích cũ:

```text
GET /v1/organizer/exhibitions/registrations?exhibitionUuid={uuid}
```

Trạng thái: Backend sẵn sàng; FE cần đổi route/size/enum.

| Nhóm | Giá trị |
|---|---|
| Pagination | `page=0`, `size=10` |
| Keyword | `company.fullName`, `company.email` |
| Status | `PENDING`, `PENDING_PAYMENT`, `APPROVED`, `REJECTED`, `CANCELLED` |
| Sort | `company.fullName`, `submittedAt`, `status` |
| Default sort | `submittedAt,desc` |

FE cần sửa:

- Ưu tiên route chuẩn có `exhibitionUuid` trong path.
- `fetchOrganizerExhibitorRegistrations()` đổi default `size=100` thành `10`.
- `normalizeOrganizerExhibitorRegistrationStatus()` phải chấp nhận `CANCELLED`.
- `OrganizerExhibitionDetailPage.jsx` đổi option/config `CANCELED` thành `CANCELLED`.
- Dùng metadata của BE thay cho phân trang cục bộ.

Ví dụ multi-sort:

```text
GET /v1/organizer/exhibitions/{uuid}/registrations?page=0&size=10&status=PENDING&sort=company.fullName,asc&sort=submittedAt,desc
```

### 4.3. Triển lãm của Organizer

`GET /v1/organizer/exhibitions`

Trạng thái: Backend và FE cơ bản đã phù hợp.

| Nhóm | Giá trị |
|---|---|
| Pagination | `page=0`, `size=10` |
| Filter UI | `status`, `startDate`, `endDate` |
| Params tương thích thêm | `keyword`, `category` |
| Status | `PENDING`, `REJECTED`, `REGISTRATION`, `PUBLISHED`, `ACTIVE`, `COMPLETED` |
| Date format | `YYYY-MM-DD` |
| Default sort | `createdAt,desc` |

`all` phải được thể hiện bằng cách bỏ `status`.

## 5. Contract EXHIBITOR

### 5.1. Triển lãm đã đăng ký

`GET /v1/exhibitor/registrations`

Trạng thái: Backend sẵn sàng; FE cần chuyển pagination và enum.

| Nhóm | Giá trị |
|---|---|
| Pagination | `page=0`, `size=10` |
| Keyword | Tên triển lãm |
| Status | `PENDING`, `PENDING_PAYMENT`, `APPROVED`, `REJECTED`, `CANCELLED` |
| Sort | `submittedAt,asc` hoặc `submittedAt,desc` |
| Default sort | `submittedAt,desc` |

FE cần sửa `src/api/registrations.js`:

- Thêm `CANCELLED: "cancelled"` và xóa mapping `CANCELED`.
- Function nhận `{page=0,size=10,keyword,status,sort}`.
- Không cố định `size=100`.
- Màn hình dùng metadata thay vì giả định đã tải toàn bộ dữ liệu.

### 5.2. Sản phẩm

`GET /v1/products`

Trạng thái: Backend sẵn sàng; FE hiện dùng chưa đúng.

| Nhóm | Giá trị |
|---|---|
| Pagination | `page=0`, `size=10` |
| Keyword | `product.name` |
| Category filter | `categoryId` là UUID, không gửi tên category |
| Status | `ACTIVE`, `INACTIVE` |
| Sort | `name`, `categoryName`, `price`, `sku` |
| Default sort | `createdAt,desc` |

Mapping sort:

| FE gửi | Entity sort |
|---|---|
| `categoryName` | `category.name` |

Multi-sort được hỗ trợ:

```text
GET /v1/products?page=0&size=10&keyword=chair&categoryId={uuid}&status=ACTIVE&sort=categoryName,asc&sort=name,asc
```

FE cần sửa:

- `src/api/products.js`: `fetchExhibitorProducts()` nhận và gửi `keyword`, `categoryId`, `status`, `sort`, `page`, `size`.
- `ExhibitorProductsPage.jsx`: không lọc `products` cục bộ sau khi chỉ tải trang đầu.
- Query key phải chứa toàn bộ params.
- Cần có điều khiển phân trang hoặc một cơ chế server-pagination tương đương.

### 5.3. Yêu cầu thiết kế

`GET /v1/exhibitor/design-requests`

Trạng thái: Backend sẵn sàng; FE cần chuyển search/pagination và enum.

| Nhóm | Giá trị |
|---|---|
| Pagination | `page=0`, `size=10` |
| Keyword | `booth.name`, tên triển lãm, `assignedDesigner.fullName` |
| Không tìm | UUID/code `#XXXXXXXX` |
| Status | `PENDING`, `ASSIGNED`, `DRAFT_SUBMITTED`, `REVISION_REQUESTED`, `REVISION_QUEUED`, `APPROVED`, `CANCELLED` |
| Sort | `createdAt`, `status` |
| Default sort | `createdAt,desc` |

FE cần sửa:

- `src/api/designRequests.js`: đổi mapping `CANCELED` thành `CANCELLED`.
- Nếu FE dùng lowercase nội bộ thì dùng `cancelled`, không dùng `canceled`.
- `fetchDesignRequests()` nên nhận object `{page,size,keyword,status,sort}`.
- Truyền `keyword` từ `ExhibitorBoothDesignListPage.jsx`; hiện `appliedSearch` không được gửi lên BE.
- Bỏ `size=100`, bỏ local `PAGE_SIZE=5`, dùng `size=10` và metadata BE.

### 5.4. Media Assets

`GET /v1/exhibitor/media-assets`

Trạng thái: Backend sẵn sàng; FE chưa sử dụng filter/pagination server.

| Nhóm | Giá trị |
|---|---|
| Pagination | `page=0`, `size=10` |
| Type filter | `filterType=all`, `image`, `video`; không phân biệt hoa thường |
| Không filter | `filterType` null/blank/`all` |
| Invalid filter | Trả validation error |
| Sort | `createdAt` |
| Default sort | `createdAt,desc` |

FE cần sửa:

- `src/api/booths.js`: `fetchMediaAssets({page=0,size=10,filterType,sort='createdAt,desc'})`; chỉ gửi `filterType` khi là `image` hoặc `video`.
- `ExhibitorMediaAssetsPage.jsx`: đưa loại file vào query key; khi chọn “Tất cả” thì bỏ `filterType`.
- Bỏ lọc image/video cục bộ dựa trên phần mở rộng file.
- Thêm điều khiển pagination dựa trên metadata BE.

## 6. Đổi enum `CANCELED` thành `CANCELLED`

BE chỉ còn sử dụng:

```text
ExhibitorRegistrationStatus.CANCELLED
DesignRequestStatus.CANCELLED
```

FE cần tìm và thay toàn bộ `CANCELED` liên quan. Các file hiện phát hiện có giá trị cũ:

```text
src/api/adminDesignRequests.js
src/api/designRequests.js
src/api/organizerExhibitions.js
src/api/registrations.js
src/pages/design-requests/AdminDesignRequestsPage.jsx
src/pages/exhibitor management/ExhibitorRegistrationDetailPage.jsx
src/pages/organizer/OrganizerExhibitionDetailPage.jsx
src/pages/designer management/DesignerBoothsPage.jsx
src/pages/designer management/DesignerRequestsPage.jsx
src/pages/designer management/DesignerWorkspacePage.jsx
```

Kiểm tra lại bằng:

```text
rg "CANCELED" src
```

Kết quả mong đợi: không còn `CANCELED`.

## 7. Checklist triển khai FE

- [ ] Dùng `page=0`, `size=10` cho toàn bộ màn hình trong phạm vi.
- [ ] Query key chứa `page`, `size`, `keyword`, filter và sort.
- [ ] Reset `page=0` khi filter/search/sort thay đổi.
- [ ] Parse `data.content` và toàn bộ metadata `PageResponse`.
- [ ] Không dùng `size=100/1000` để tự phân trang.
- [ ] Không lọc/tìm kiếm cục bộ trên một trang dữ liệu.
- [ ] Không gửi `all` cho các enum filter; bỏ query param.
- [ ] Chỉ dùng `keyword`, không gửi `q/search`.
- [ ] Đổi toàn bộ `CANCELED` thành `CANCELLED`.
- [ ] Product gửi `categoryId`, không gửi tên category.
- [ ] Media gửi `filterType=image|video`; khi chọn “Tất cả” thì bỏ param.
- [ ] Organizer Registration ưu tiên route có exhibition UUID trong path.
- [ ] Public Exhibition chỉ gửi `PUBLISHED`, `ACTIVE`, `COMPLETED`; chọn “Tất cả” thì bỏ `status`.
- [ ] Storage Package Orders parse `PageResponse` và dùng server pagination `size=10`.
- [ ] Admin Exhibition sử dụng các sort alias `organizerName`, `exhibitionName`, `expectedBoothCount`.

## 8. Acceptance criteria FE

Mỗi màn hình getList chỉ được coi là hoàn tất khi:

1. Request trang đầu gửi `page=0&size=10`.
2. Chuyển trang gọi lại API thay vì `slice()` dữ liệu cũ.
3. Search/filter/sort tác động trên toàn bộ dữ liệu vì được thực hiện ở BE trước pagination.
4. Số bản ghi và số trang lấy từ `totalElements`/`totalPages`.
5. Trang rỗng, trang cuối và quay lại trang đầu hoạt động đúng.
6. Không có request nào gửi `CANCELED`, `q`, `search` hoặc enum `all`.
7. Multi-sort được serialize thành nhiều param `sort`.

## 9. Trạng thái kiểm thử Backend

Toàn bộ test suite gần nhất:

```text
Tests run: 537, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Lưu ý: test suite hiện chủ yếu là unit test dùng mock repository. Query tìm theo company cần được smoke-test thêm trên môi trường MySQL tích hợp trước khi release.
