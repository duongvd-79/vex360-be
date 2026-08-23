package com.example.vex360.shared.services;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.vex360.shared.dtos.PresignUploadRequest.FileItem;
import com.example.vex360.shared.dtos.PresignedUploadResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.utils.FileUploadUtils;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Cấp URL đã ký để trình duyệt tải tệp thẳng lên R2. Máy chủ không nhận bytes, chỉ quyết
 * định tệp được đặt ở đâu, mang tên gì và thuộc kiểu nào.
 */
@Service
@RequiredArgsConstructor
public class R2StorageService {

    private final S3Presigner r2Presigner;
    private final S3Client r2Client;

    @Value("${app.r2.bucket-name}")
    private String bucketName;

    @Value("${app.r2.public-url}")
    private String publicUrl;

    /** Vé sống đủ lâu để tải xong tệp lớn, đủ ngắn để lỡ rò rỉ cũng nhanh hết hạn. */
    private static final Duration URL_TTL = Duration.ofMinutes(10);

    /**
     * Phần mở rộng được phép, kèm kiểu nội dung do máy chủ quyết định. Ba định dạng mô hình
     * đều mang tiền tố "model/" để tầng sản phẩm phân loại được mà không phải đoán theo đuôi.
     */
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "obj", "model/obj",
            "mtl", "model/mtl",
            "glb", "model/gltf-binary",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg");

    public PresignedUploadResponse presignBatch(UUID companyId, List<FileItem> files) {
        // Cả lô chung một thư mục vì tệp .obj trỏ tới .mtl và texture bằng đường dẫn tương
        // đối — tách thư mục là mô hình mất vật liệu mà không có lỗi nào báo ra.
        String prefix = "products/%s/%s/".formatted(companyId, UUID.randomUUID());
        String baseUrl = publicUrl.replaceAll("/+$", "");

        List<PresignedUploadResponse.Item> items = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        for (FileItem file : files) {
            String fileName = validateFileName(file.getFileName());
            if (!seenNames.add(fileName)) {
                // Trùng tên trong cùng thư mục thì tệp sau ghi đè tệp trước, R2 không báo gì.
                throw new AppException(ErrorCode.FILE_NAME_DUPLICATED);
            }

            String contentType = contentTypeOf(fileName);
            String objectKey = prefix + fileName;

            items.add(PresignedUploadResponse.Item.builder()
                    .fileName(fileName)
                    .objectKey(objectKey)
                    .uploadUrl(presignPut(objectKey, contentType))
                    .contentType(contentType)
                    .publicUrl(baseUrl + "/" + objectKey)
                    .build());
        }

        return PresignedUploadResponse.builder()
                .prefix(prefix)
                .files(items)
                .build();
    }

    private String contentTypeOf(String fileName) {
        String extension = FileUploadUtils.getFileExtension(fileName);
        if (extension == null) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
        // Bảng tra viết bằng chữ thường; Locale.ROOT để "MODEL.OBJ" không đổi khác nhau
        // giữa các máy đặt ngôn ngữ khác nhau.
        String contentType = CONTENT_TYPES.get(extension.toLowerCase(Locale.ROOT));
        if (contentType == null) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
        return contentType;
    }

    /**
     * Từ chối tên tệp nguy hiểm thay vì gọt sửa nó. Khoá object ghép thẳng từ tên gốc nên
     * một dấu gạch chéo là đủ để ghi đè dữ liệu của công ty khác; còn thay ký tự lặng lẽ
     * thì tệp .obj vẫn trỏ tới tên cũ và mô hình hỏng liên kết mà không ai hiểu vì sao.
     */
    private String validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new AppException(ErrorCode.FILE_NAME_NOT_ALLOWED);
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new AppException(ErrorCode.FILE_NAME_NOT_ALLOWED);
        }
        if (fileName.chars().anyMatch(c -> c < 32)) {
            throw new AppException(ErrorCode.FILE_NAME_NOT_ALLOWED);
        }
        return fileName;
    }

    private String presignPut(String objectKey, String contentType) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                // Đưa content-type vào chữ ký để client không đổi được kiểu tệp sau lưng.
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(URL_TTL)
                .putObjectRequest(objectRequest)
                .build();

        return r2Presigner.presignPutObject(presignRequest).url().toString();
    }

    /**
     * Xoá cả thư mục chứa tệp này. Một mô hình gồm .obj, .mtl và các texture nằm chung một
     * thư mục nhưng chỉ tệp cửa vào được ghi vào cơ sở dữ liệu, nên xoá theo thư mục là cách
     * duy nhất dọn sạch mà không phải lưu danh sách tệp phụ ở đâu cả.
     */
    public void deleteFolderOf(String objectKey) {
        String prefix = folderPrefixOf(objectKey);
        if (prefix == null) {
            return;
        }
        deleteAllUnder(prefix);
    }

    /**
     * Dọn một thư mục tải lên chưa gắn vào sản phẩm nào, dùng khi người dùng huỷ biểu mẫu.
     * Prefix do client gửi lên nên phải chứng minh hai điều trước khi xoá bất cứ thứ gì.
     */
    public void deleteUploadFolder(UUID companyId, String prefix) {
        String companyRoot = "products/%s/".formatted(companyId);
        // Một: thư mục phải thuộc đúng công ty đang đăng nhập.
        if (prefix == null || !prefix.startsWith(companyRoot) || !prefix.endsWith("/")) {
            throw new AppException(ErrorCode.STORAGE_OBJECT_FORBIDDEN);
        }
        // Hai: phải trỏ vào một thư mục con cụ thể. Nhận đúng companyRoot thì một lần gọi
        // nhầm sẽ xoá sạch mọi tệp sản phẩm của cả doanh nghiệp.
        if (prefix.length() <= companyRoot.length()) {
            throw new AppException(ErrorCode.STORAGE_OBJECT_FORBIDDEN);
        }
        deleteAllUnder(prefix);
    }

    private void deleteAllUnder(String prefix) {
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();

        // Xoá theo từng trang: mỗi trang tối đa 1000 khoá, vừa đúng giới hạn của một lệnh
        // DeleteObjects, nên thư mục lớn đến mấy cũng không vượt hạn.
        for (ListObjectsV2Response page : r2Client.listObjectsV2Paginator(listRequest)) {
            List<ObjectIdentifier> keys = page.contents().stream()
                    .map(object -> ObjectIdentifier.builder().key(object.key()).build())
                    .toList();
            if (keys.isEmpty()) {
                continue;
            }
            r2Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder().objects(keys).build())
                    .build());
        }
    }

    private String folderPrefixOf(String objectKey) {
        if (objectKey == null) {
            return null;
        }
        int lastSlash = objectKey.lastIndexOf('/');
        return lastSlash <= 0 ? null : objectKey.substring(0, lastSlash + 1);
    }

    /**
     * Xác minh tệp cửa vào thật sự nằm trên R2 và trả về tổng dung lượng cả thư mục chứa nó.
     *
     * <p>Không đọc con số nào do client gửi lên: URL đã ký không ràng buộc kích thước, nên
     * người tải hoàn toàn có thể khai một đằng đẩy lên một nẻo. Danh sách lấy thẳng từ R2
     * cũng khép luôn kẽ hở ngược lại — client không giấu được những tệp phụ đã nhét thêm vào
     * cùng thư mục, vì tổng tính trên mọi thứ thật sự có ở đó.
     */
    public long verifyAndMeasureFolder(UUID companyId, String objectKey) {
        String prefix = folderPrefixOf(objectKey);
        if (prefix == null) {
            throw new AppException(ErrorCode.STORAGE_OBJECT_NOT_FOUND);
        }
        // Chặn việc gắn tệp của doanh nghiệp khác vào sản phẩm của mình: khoá do client gửi
        // lên nên phải chứng minh nó nằm trong đúng thư mục của công ty đang đăng nhập.
        if (!objectKey.startsWith("products/%s/".formatted(companyId))) {
            throw new AppException(ErrorCode.STORAGE_OBJECT_FORBIDDEN);
        }

        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();

        long totalBytes = 0L;
        boolean entryFound = false;
        for (ListObjectsV2Response page : r2Client.listObjectsV2Paginator(listRequest)) {
            for (S3Object object : page.contents()) {
                totalBytes += object.size();
                if (object.key().equals(objectKey)) {
                    entryFound = true;
                }
            }
        }

        if (!entryFound) {
            throw new AppException(ErrorCode.STORAGE_OBJECT_NOT_FOUND);
        }
        return totalBytes;
    }
}
