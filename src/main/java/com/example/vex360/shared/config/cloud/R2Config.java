package com.example.vex360.shared.config.cloud;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Cloudflare R2 nói được giao thức S3 nên dùng thẳng AWS SDK v2, chỉ cần chỉnh ba chỗ
 * cho khớp: vùng là "auto", endpoint trỏ về R2 thay vì amazonaws.com, và địa chỉ theo
 * kiểu path-style.
 */
@Configuration
public class R2Config {

    /**
     * R2 không chia vùng địa lý như AWS, nhưng thuật toán ký SigV4 vẫn bắt buộc có tên
     * vùng trong chữ ký. Cloudflare quy ước dùng đúng chuỗi "auto".
     */
    private static final Region R2_REGION = Region.of("auto");

    @Value("${app.r2.endpoint}")
    private String endpoint;

    @Value("${app.r2.access-key}")
    private String accessKey;

    @Value("${app.r2.secret-key}")
    private String secretKey;

    private StaticCredentialsProvider credentialsProvider() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    /**
     * Ký sẵn URL để trình duyệt tự PUT file thẳng lên R2 — backend không nhận bytes,
     * không tốn băng thông, và khoá bí mật không bao giờ rời khỏi máy chủ.
     */
    @Bean
    public S3Presigner r2Presigner() {
        return S3Presigner.builder()
                .region(R2_REGION)
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(S3Configuration.builder()
                        // Địa chỉ dạng <endpoint>/<bucket>/<key>. Mặc định SDK ghép tên bucket
                        // thành subdomain, khó đọc log và dễ vướng chứng chỉ TLS lồng nhau.
                        .pathStyleAccessEnabled(true)
                        // Trình duyệt gửi nguyên khối file, không dùng chunked encoding của AWS.
                        // Bật lên thì chữ ký đòi định dạng thân request mà fetch() không tạo ra.
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();
    }

    /**
     * Backend chỉ gọi trực tiếp R2 cho hai việc: HeadObject để đọc dung lượng thật của file
     * sau khi trình duyệt báo đã tải xong (không tin số do client gửi lên), và DeleteObject
     * để dọn file mồ côi khi người dùng huỷ biểu mẫu.
     */
    @Bean
    public S3Client r2Client() {
        return S3Client.builder()
                .region(R2_REGION)
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentialsProvider())
                .forcePathStyle(true)
                // Từ bản 2.30 SDK tự đính kèm checksum CRC32 vào mọi request ghi. R2 không
                // chấp nhận header đó và trả về lỗi chữ ký, nên chỉ tính khi nào bắt buộc.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .build();
    }
}
