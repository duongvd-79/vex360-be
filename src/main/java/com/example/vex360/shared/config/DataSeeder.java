package com.example.vex360.shared.config;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.vex360.features.analytics.entities.AnalyticsEvent;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.BoothReviewRequest;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothReviewStatus;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.enums.HotspotInfoContentType;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.BoothReviewRequestRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.MediaAssetRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.chat.entities.ChatMessage;
import com.example.vex360.features.chat.entities.ChatRoom;
import com.example.vex360.features.chat.repositories.ChatMessageRepository;
import com.example.vex360.features.chat.repositories.ChatRoomRepository;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.entities.StoragePackage;
import com.example.vex360.features.company.entities.StoragePackageOrder;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.company.repositories.StoragePackageOrderRepository;
import com.example.vex360.features.company.repositories.StoragePackageRepository;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionAsset;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.repositories.ExhibitionAssetRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionPackageRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.lead.entities.BoothLead;
import com.example.vex360.features.notification.entities.Notification;
import com.example.vex360.features.packagetemplate.entities.PackageTemplate;
import com.example.vex360.features.packagetemplate.repositories.PackageTemplateRepository;
import com.example.vex360.features.partnership.entities.PartnershipRequest;
import com.example.vex360.features.partnership.repositories.PartnershipRequestRepository;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.entities.ProductCategory;
import com.example.vex360.features.product.entities.ProductContent;
import com.example.vex360.features.product.enums.ProductCategoryStatus;
import com.example.vex360.features.product.enums.ProductContentType;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.repositories.ProductCategoryRepository;
import com.example.vex360.features.product.repositories.ProductRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.repositories.UserRepository;
import com.example.vex360.features.analytics.enums.AnalyticsEventType;
import com.example.vex360.shared.enums.AuthProvider;
import com.example.vex360.shared.enums.BoothListingPriority;
import com.example.vex360.shared.enums.CompanyStatus;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.enums.ExhibitionAssetType;
import com.example.vex360.shared.enums.ExhibitionPackageStatus;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.PackageTemplateStatus;
import com.example.vex360.shared.enums.PartnershipAccountAction;
import com.example.vex360.shared.enums.PartnershipRequestStatus;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.enums.PaymentType;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.StoragePackageOrderStatus;
import com.example.vex360.shared.enums.UserStatus;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeder dữ liệu test.
 *
 * <p>
 * Chỉ chạy khi bật cờ {@code app.seed.enabled=true} (hoặc biến môi trường
 * {@code APP_SEED_ENABLED=true}) để không bao giờ vô tình chạy trên production.
 *
 * <p>
 * Ảnh được upload thật lên Cloudinary bằng cách truyền URL công khai cho SDK —
 * Cloudinary tự tải về và host lại, trả về {@code secure_url} +
 * {@code public_id} thật.
 * Nếu upload lỗi (thiếu credential, mất mạng), seeder tự fallback sang URL gốc
 * để
 * không làm hỏng toàn bộ quá trình seed.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

        /** Nếu user này đã tồn tại thì coi như đã seed rồi -> bỏ qua. */
        private static final String MARKER_EMAIL = "organizer@vex360.local";
        private static final String DEFAULT_PASSWORD = "123456";

        /**
         * Ảnh panorama 360 <b>equirectangular thật</b> (4096x2048), bối cảnh trong nhà
         * hợp với nền tảng triển lãm ảo: sảnh đón khách triển lãm, khu trưng bày bảo
         * tàng,
         * hội trường, sảnh lớn.
         *
         * <p>
         * Nguồn: Wikimedia Commons (giấy phép tự do). Dùng bản thu nhỏ 4096px thay vì
         * bản gốc (~12000px) để vừa giới hạn upload của Cloudinary và đúng độ phân giải
         * cần thiết cho viewer web.
         *
         * <p>
         * Bắt buộc phải là ảnh equirectangular thì khi bọc lên hình cầu trong viewer
         * mới không bị méo và không lộ đường nối dọc.
         */
        private static final String WIKI_THUMB = "https://upload.wikimedia.org/wikipedia/commons/thumb/";

        private static final String WIKI_FILE = "https://upload.wikimedia.org/wikipedia/commons/";

        /**
         * Ảnh minh hoạ <b>đúng chủ đề VEX360</b> (nội thất, thiết bị công nghệ, hội chợ
         * triển lãm). Nguồn: Wikimedia Commons, giấy phép tự do. Dùng bản thu nhỏ
         * 1280px cho nhẹ.
         */
        private static final String IMG_SOFA = WIKI_THUMB
                        + "4/47/Couch-furniture-living-room-sofa_%2824300293356%29.jpg/"
                        + "1280px-Couch-furniture-living-room-sofa_%2824300293356%29.jpg";
        private static final String IMG_TABLE = WIKI_THUMB
                        + "2/2b/Dining_table_brown.jpg/1280px-Dining_table_brown.jpg";
        private static final String IMG_SENSOR = WIKI_THUMB
                        + "0/02/Humidity_and_temperature_transmitter.jpg/"
                        + "1280px-Humidity_and_temperature_transmitter.jpg";
        private static final String IMG_CABINET = WIKI_THUMB
                        + "c/c1/Armoire_%C3%A0_sept_colonnes-Mus%C3%A9e_de_la_Folie_Marco.jpg/"
                        + "1280px-Armoire_%C3%A0_sept_colonnes-Mus%C3%A9e_de_la_Folie_Marco.jpg";
        /** Sảnh hội chợ triển lãm - dùng làm key visual. */
        private static final String IMG_EXPO_HALL = WIKI_THUMB
                        + "2/27/MESAP_2012._-_dvorana_%28ulaz%29.JPG/1280px-MESAP_2012._-_dvorana_%28ulaz%29.JPG";
        /** Dãy gian hàng hội chợ - dùng làm ảnh hướng dẫn bố trí. */
        private static final String IMG_EXPO_BOOTHS = WIKI_THUMB
                        + "4/49/MESAP_2011._-_%C5%A1tandovi_%28sjeverni_niz%29.jpg/"
                        + "1280px-MESAP_2011._-_%C5%A1tandovi_%28sjeverni_niz%29.jpg";
        private static final String IMG_FLOOR_PLAN = WIKI_THUMB
                        + "9/97/Bungalow_drawing_--_Floor_Plan_MET_DP804276.jpg/"
                        + "1280px-Bungalow_drawing_--_Floor_Plan_MET_DP804276.jpg";
        /** Nội thất cửa hàng - dùng làm banner/thumbnail gian hàng nội thất. */
        private static final String IMG_SHOWROOM = WIKI_FILE + "0/09/Interior_of_Severin_%26_Andreas_Jensen.jpg";

        /** Video đúng chủ đề (Wikimedia Commons, giấy phép tự do). */
        private static final String VIDEO_EXPO = WIKI_FILE + "b/b5/Craft_x_Tech_exhibition_at_V%26A_2024.webm";
        private static final String VIDEO_FURNITURE = WIKI_FILE + "7/72/Furniture_Making.webm";
        private static final String VIDEO_SENSOR = WIKI_FILE + "b/b1/Gas_sensor_with_Arduino.webm";

        /** Màu nền logo (clay terracotta của VEX360). */
        private static final String LOGO_BG = "A8572F";

        private static final String[] PANORAMA_IMAGES = {
                        // Sảnh đón khách của một triển lãm (BITM, Kolkata)
                        WIKI_THUMB + "0/01/Exhibition_Light_Matters_-_Reception_Area_-_360x180_Degree_Equirectangular_View_-_BITM_"
                                        + "-_Kolkata_2016-01-02_8729-8739_Compress.JPG/3840px-Exhibition_Light_Matters_-_Reception_Area_"
                                        + "-_360x180_Degree_Equirectangular_View_-_BITM_-_Kolkata_2016-01-02_8729-8739_Compress.JPG",
                        // Khu trưng bày trong bảo tàng
                        WIKI_THUMB + "0/08/Cmglee_Wikimania2016_Esino_Lario_museum_interior_photosphere.jpg/"
                                        + "3840px-Cmglee_Wikimania2016_Esino_Lario_museum_interior_photosphere.jpg",
                        // Hội trường lớn
                        WIKI_THUMB + "4/43/Cmglee_Wikimania2016_Esino_Lario_hall_interior_photosphere.jpg/"
                                        + "3840px-Cmglee_Wikimania2016_Esino_Lario_hall_interior_photosphere.jpg",
                        // Sảnh rộng (dự phòng khi thêm panorama mới)
                        WIKI_THUMB + "a/a3/Pomona_College_gymnasium_lobby_photosphere.jpg/"
                                        + "3840px-Pomona_College_gymnasium_lobby_photosphere.jpg",
        };

        private final UserRepository userRepository;
        private final CompanyRepository companyRepository;
        private final PackageTemplateRepository packageTemplateRepository;
        private final ExhibitionRepository exhibitionRepository;
        private final ExhibitionAssetRepository exhibitionAssetRepository;
        private final ExhibitionPackageRepository exhibitionPackageRepository;
        private final ExhibitorRegistrationRepository exhibitorRegistrationRepository;
        private final PaymentRepository paymentRepository;
        private final BoothRepository boothRepository;
        private final PanoramaRepository panoramaRepository;
        private final HotspotRepository hotspotRepository;
        private final MediaAssetRepository mediaAssetRepository;
        private final ProductCategoryRepository productCategoryRepository;
        private final ProductRepository productRepository;
        private final StoragePackageRepository storagePackageRepository;
        private final StoragePackageOrderRepository storagePackageOrderRepository;
        private final PartnershipRequestRepository partnershipRequestRepository;
        private final BoothReviewRequestRepository boothReviewRequestRepository;
        private final DesignRequestRepository designRequestRepository;
        private final ChatRoomRepository chatRoomRepository;
        private final ChatMessageRepository chatMessageRepository;

        private final PasswordEncoder passwordEncoder;
        private final Cloudinary cloudinary;
        private final EntityManager entityManager;

        /** Kết quả upload lên Cloudinary. */
        private record Uploaded(String url, String publicId, long bytes) {
        }

        @Override
        @Transactional
        public void run(ApplicationArguments args) {
                if (userRepository.existsByEmail(MARKER_EMAIL)) {
                        log.info("[SEED] Dữ liệu test đã tồn tại -> bỏ qua.");
                        return;
                }

                log.info("[SEED] Bắt đầu seed dữ liệu test (ảnh sẽ được upload lên Cloudinary)...");

                // ---------- 1. USERS ----------
                User admin = userRepository.findByEmail("admin@vex360.local").orElseGet(
                                () -> createUser("admin@vex360.local", "Quản trị hệ thống", Role.ADMIN, "0900000000",
                                                11));
                User organizer = createUser(MARKER_EMAIL, "Nguyễn Tổ Chức", Role.ORGANIZER, "0901111111", 12);
                User exhibitor1 = createUser("exhibitor@vex360.local", "Trần Quang Huy", Role.EXHIBITOR, "0902222222",
                                13);
                User exhibitor2 = createUser("exhibitor2@vex360.local", "Lê Thái Dương", Role.EXHIBITOR, "0903333333",
                                14);
                User designer = createUser("designer@vex360.local", "Phạm Thiên An", Role.DESIGNER, "0904444444", 15);
                User visitor = createUser("visitor@vex360.local", "Hoàng Khách Tham Quan", Role.VISITOR, "0905555555",
                                16);

                // Phủ UserStatus (INACTIVE/PENDING/BLOCKED) và AuthProvider (GOOGLE)
                createUser("visitor.inactive@vex360.local", "Ngô Ngừng Hoạt Động", Role.VISITOR, "0906111111", 21,
                                UserStatus.INACTIVE, AuthProvider.LOCAL);
                createUser("visitor.pending@vex360.local", "Đinh Chờ Kích Hoạt", Role.VISITOR, "0906222222", 22,
                                UserStatus.PENDING, AuthProvider.LOCAL);
                createUser("visitor.blocked@vex360.local", "Bùi Bị Khoá", Role.VISITOR, "0906333333", 23,
                                UserStatus.BLOCKED, AuthProvider.LOCAL);
                createUser("visitor.google@vex360.local", "Lý Đăng Nhập Google", Role.VISITOR, "0906444444", 24,
                                UserStatus.ACTIVE, AuthProvider.GOOGLE);
                // 2 chủ sở hữu cho company INCOMPLETE_PROFILE và ARCHIVED
                User exhibitor3 = createUser("exhibitor3@vex360.local", "Vũ Hồ Sơ Thiếu", Role.EXHIBITOR,
                                "0906555555", 25);
                User exhibitor4 = createUser("exhibitor4@vex360.local", "Tạ Đã Lưu Trữ", Role.EXHIBITOR,
                                "0906666666", 26);
                log.info("[SEED] Đã tạo 12 user - phủ đủ Role, UserStatus, AuthProvider (mật khẩu chung: {})",
                                DEFAULT_PASSWORD);

                // ---------- 2. COMPANIES (phủ đủ 3 CompanyStatus) ----------
                Company orgCompany = createCompany(organizer, "Công ty Tổ chức Sự kiện ABC",
                                "Sự kiện & Triển lãm", "Đơn vị tổ chức triển lãm ảo hàng đầu.", "logo-abc");
                Company company1 = createCompany(exhibitor1, "Công ty Nội thất Mộc Việt",
                                "Nội thất", "Chuyên nội thất gỗ tự nhiên cao cấp.", "logo-mocviet");
                Company company2 = createCompany(exhibitor2, "Công ty Công nghệ TechVina",
                                "Công nghệ", "Giải pháp thiết bị thông minh cho doanh nghiệp.", "logo-techvina");
                createCompany(exhibitor3, "Công ty TNHH Hồ Sơ Chưa Đủ",
                                "Chưa xác định", null, "logo-incomplete", CompanyStatus.INCOMPLETE_PROFILE);
                createCompany(exhibitor4, "Công ty CP Ngừng Hoạt Động",
                                "Bán lẻ", "Doanh nghiệp đã ngừng tham gia nền tảng.", "logo-archived",
                                CompanyStatus.ARCHIVED);
                log.info("[SEED] Đã tạo 5 company (ACTIVE / INCOMPLETE_PROFILE / ARCHIVED)");

                // ---------- 3. PACKAGE TEMPLATES ----------
                PackageTemplate basicTemplate = packageTemplateRepository.save(PackageTemplate.builder()
                                .createdBy(admin).name("Gói Cơ Bản")
                                .description("Gian hàng tiêu chuẩn cho doanh nghiệp mới tham gia.")
                                .price(new BigDecimal("5000000")).currency("VND")
                                .maxProductsPerBooth(20).maxEmbeddedVideosPerBooth(2)
                                .maxPanoramasPerBooth(3).maxHotspotsPerBooth(15)
                                .storageLimitMb(500L).listingPriority(BoothListingPriority.NORMAL)
                                .status(PackageTemplateStatus.ACTIVE).build());

                PackageTemplate premiumTemplate = packageTemplateRepository.save(PackageTemplate.builder()
                                .createdBy(admin).name("Gói Cao Cấp")
                                .description("Gian hàng nổi bật, ưu tiên hiển thị đầu danh sách.")
                                .price(new BigDecimal("15000000")).currency("VND")
                                .maxProductsPerBooth(100).maxEmbeddedVideosPerBooth(10)
                                .maxPanoramasPerBooth(10).maxHotspotsPerBooth(60)
                                .storageLimitMb(5000L).listingPriority(BoothListingPriority.FEATURED)
                                .status(PackageTemplateStatus.ACTIVE).build());

                // Phủ BoothListingPriority.PRIORITY và PackageTemplateStatus.INACTIVE
                packageTemplateRepository.save(PackageTemplate.builder()
                                .createdBy(admin).name("Gói Ưu Tiên (ngừng bán)")
                                .description("Gói cũ đã ngừng kinh doanh, giữ lại để tra cứu lịch sử.")
                                .price(new BigDecimal("9000000")).currency("VND")
                                .maxProductsPerBooth(50).maxEmbeddedVideosPerBooth(5)
                                .maxPanoramasPerBooth(6).maxHotspotsPerBooth(30)
                                .storageLimitMb(2000L).listingPriority(BoothListingPriority.PRIORITY)
                                .status(PackageTemplateStatus.INACTIVE).build());
                log.info("[SEED] Đã tạo 3 package template (phủ đủ BoothListingPriority + PackageTemplateStatus)");

                // ---------- 4. EXHIBITIONS (phủ đủ 6 trạng thái) ----------
                // Ngày trải quanh "hôm nay" để triển lãm đang diễn ra và bao trùm các event seed
                Exhibition exhibition = saveExhibition(organizer,
                                "Triển lãm Nội thất & Công nghệ VEX360 2026", "Nội thất - Công nghệ",
                                "Triển lãm ảo quy tụ các thương hiệu nội thất và công nghệ hàng đầu Việt Nam.",
                                LocalDate.now().minusDays(20), LocalDate.now().plusDays(10), 50,
                                ExhibitionStatus.ACTIVE, admin, null);

                saveExhibition(organizer,
                                "Triển lãm Thủ công Mỹ nghệ Việt 2026", "Thủ công mỹ nghệ",
                                "Hồ sơ vừa gửi, đang chờ quản trị viên xét duyệt.",
                                LocalDate.now().plusDays(60), LocalDate.now().plusDays(75), 30,
                                ExhibitionStatus.PENDING, null, null);

                saveExhibition(organizer,
                                "Triển lãm Xe cũ Toàn quốc 2026", "Ô tô - Xe máy",
                                "Hồ sơ đã bị từ chối, tổ chức cần bổ sung giấy tờ.",
                                LocalDate.now().plusDays(90), LocalDate.now().plusDays(100), 20,
                                ExhibitionStatus.REJECTED, admin,
                                "Nội dung triển lãm chưa phù hợp định hướng nền tảng. Vui lòng bổ sung giấy phép kinh doanh.");

                Exhibition exhRegistration = saveExhibition(organizer,
                                "Triển lãm Vật liệu Xây dựng 2026", "Xây dựng",
                                "Đang mở cổng đăng ký gian hàng cho doanh nghiệp.",
                                LocalDate.now().plusDays(45), LocalDate.now().plusDays(60), 80,
                                ExhibitionStatus.REGISTRATION, admin, null);

                Exhibition exhActive = saveExhibition(organizer,
                                "Triển lãm Công nghệ Xanh 2026", "Công nghệ - Môi trường",
                                "Triển lãm đang diễn ra, khách tham quan có thể vào xem gian hàng.",
                                LocalDate.now().minusDays(3), LocalDate.now().plusDays(10), 40,
                                ExhibitionStatus.ACTIVE, admin, null);

                Exhibition exhCompleted = saveExhibition(organizer,
                                "Triển lãm Nội thất Mùa Thu 2025", "Nội thất",
                                "Triển lãm đã kết thúc, dữ liệu được lưu để tra cứu.",
                                LocalDate.now().minusDays(60), LocalDate.now().minusDays(30), 35,
                                ExhibitionStatus.COMPLETED, admin, null);
                log.info("[SEED] Đã tạo 6 exhibition (PENDING/REJECTED/REGISTRATION/PUBLISHED/ACTIVE/COMPLETED)");

                // ---------- 5. EXHIBITION ASSETS ----------
                Uploaded keyVisual = upload(IMG_EXPO_HALL, "seed/exhibition");
                Uploaded sponsorLogo = upload(letterLogo("Nha Tai Tro Kim Cuong"), "seed/exhibition");
                exhibitionAssetRepository.save(ExhibitionAsset.builder().exhibition(exhibition)
                                .assetUrl(keyVisual.url()).publicId(keyVisual.publicId())
                                .type(ExhibitionAssetType.KEY_VISUAL).build());
                exhibitionAssetRepository.save(ExhibitionAsset.builder().exhibition(exhibition)
                                .assetUrl(sponsorLogo.url()).publicId(sponsorLogo.publicId())
                                .type(ExhibitionAssetType.SPONSOR_LOGO).build());

                // Phủ nốt 3 loại asset còn lại
                Uploaded trailer = upload(VIDEO_EXPO, "seed/exhibition");
                exhibitionAssetRepository.save(ExhibitionAsset.builder().exhibition(exhibition)
                                .assetUrl(trailer.url()).publicId(trailer.publicId())
                                .type(ExhibitionAssetType.TRAILER_VIDEO).build());
                Uploaded floorPlan = upload(IMG_FLOOR_PLAN, "seed/exhibition");
                exhibitionAssetRepository.save(ExhibitionAsset.builder().exhibition(exhibition)
                                .assetUrl(floorPlan.url()).publicId(floorPlan.publicId())
                                .type(ExhibitionAssetType.FLOOR_PLAN).build());
                Uploaded guideline = upload(IMG_EXPO_BOOTHS, "seed/exhibition");
                exhibitionAssetRepository.save(ExhibitionAsset.builder().exhibition(exhibition)
                                .assetUrl(guideline.url()).publicId(guideline.publicId())
                                .type(ExhibitionAssetType.GUIDELINE).build());
                log.info("[SEED] Đã tạo 5 exhibition asset (phủ đủ ExhibitionAssetType)");

                // ---------- 6. EXHIBITION PACKAGES ----------
                ExhibitionPackage basicPackage = savePackage(basicTemplate, exhibition, "5000000");
                ExhibitionPackage premiumPackage = savePackage(premiumTemplate, exhibition, "13500000");
                ExhibitionPackage regPackage = savePackage(basicTemplate, exhRegistration, "4500000");
                savePackage(premiumTemplate, exhRegistration, "12000000");
                savePackage(basicTemplate, exhActive, "6000000");
                ExhibitionPackage completedPackage = savePackage(basicTemplate, exhCompleted, "4000000");
                // Phủ ExhibitionPackageStatus.INACTIVE
                savePackage(premiumTemplate, exhCompleted, "11000000", ExhibitionPackageStatus.INACTIVE);
                log.info("[SEED] Đã tạo 7 exhibition package (phủ đủ ExhibitionPackageStatus)");

                // ---------- 7. EXHIBITOR REGISTRATIONS (phủ đủ 5 trạng thái) ----------
                ExhibitorRegistration reg1 = exhibitorRegistrationRepository.save(buildRegistration(
                                premiumPackage, exhibitor1, premiumTemplate, ExhibitorRegistrationStatus.APPROVED,
                                admin, "Chúng tôi muốn giới thiệu bộ sưu tập nội thất gỗ mới.", null));
                ExhibitorRegistration reg2 = exhibitorRegistrationRepository.save(buildRegistration(
                                basicPackage, exhibitor2, basicTemplate, ExhibitorRegistrationStatus.PENDING_PAYMENT,
                                null, "TechVina mong muốn tiếp cận khách hàng doanh nghiệp.", null));
                ExhibitorRegistration reg3 = exhibitorRegistrationRepository.save(buildRegistration(
                                regPackage, exhibitor2, basicTemplate, ExhibitorRegistrationStatus.PENDING,
                                null, "TechVina muốn trưng bày giải pháp vật liệu thông minh.", null));
                ExhibitorRegistration reg4 = exhibitorRegistrationRepository.save(buildRegistration(
                                regPackage, exhibitor1, basicTemplate, ExhibitorRegistrationStatus.REJECTED,
                                organizer, "Mộc Việt đăng ký gian hàng nội thất gỗ.",
                                "Ngành hàng không phù hợp với chủ đề vật liệu xây dựng của triển lãm."));
                ExhibitorRegistration reg5 = exhibitorRegistrationRepository.save(buildRegistration(
                                completedPackage, exhibitor2, basicTemplate, ExhibitorRegistrationStatus.CANCELED,
                                null, "Đăng ký rồi tự huỷ do thay đổi kế hoạch kinh doanh.", null));
                log.info("[SEED] Đã tạo 5 exhibitor registration "
                                + "(APPROVED/PENDING_PAYMENT/PENDING/REJECTED/CANCELED)");

                // ---------- 8. PAYMENTS ----------
                paymentRepository.save(Payment.builder()
                                .exhibitorRegistration(reg1).paymentType(PaymentType.EXHIBITION_REGISTRATION)
                                .orderCode(orderCode()).amount(new BigDecimal("13500000"))
                                .systemFee(new BigDecimal("1350000")).organizerPayout(new BigDecimal("12150000"))
                                .currency("VND").paymentProvider("PAYOS").paymentReference("SEED-PAY-001")
                                .status(PaymentStatus.PAID).paidAt(Instant.now().minus(1, ChronoUnit.DAYS)).build());
                paymentRepository.save(Payment.builder()
                                .exhibitorRegistration(reg2).paymentType(PaymentType.EXHIBITION_REGISTRATION)
                                .orderCode(orderCode()).amount(new BigDecimal("5000000"))
                                .systemFee(new BigDecimal("500000")).organizerPayout(new BigDecimal("4500000"))
                                .currency("VND").paymentProvider("PAYOS")
                                .status(PaymentStatus.PENDING).build());
                paymentRepository.save(Payment.builder()
                                .exhibitorRegistration(reg4).paymentType(PaymentType.EXHIBITION_REGISTRATION)
                                .orderCode(orderCode()).amount(new BigDecimal("4500000"))
                                .systemFee(new BigDecimal("450000")).organizerPayout(new BigDecimal("4050000"))
                                .currency("VND").paymentProvider("PAYOS").paymentReference("SEED-PAY-FAILED")
                                .status(PaymentStatus.FAILED).build());
                paymentRepository.save(Payment.builder()
                                .exhibitorRegistration(reg5).paymentType(PaymentType.EXHIBITION_REGISTRATION)
                                .orderCode(orderCode()).amount(new BigDecimal("4000000"))
                                .systemFee(new BigDecimal("400000")).organizerPayout(new BigDecimal("3600000"))
                                .currency("VND").paymentProvider("PAYOS")
                                .status(PaymentStatus.EXPIRED).build());
                log.info("[SEED] Đã tạo 4 payment (PAID/PENDING/FAILED/EXPIRED)");

                // ---------- 9. BOOTHS ----------
                Uploaded boothThumb1 = upload(IMG_SHOWROOM, "seed/booth");
                Booth booth1 = boothRepository.save(Booth.builder()
                                .name("Gian hàng Mộc Việt").description("Không gian trưng bày nội thất gỗ tự nhiên.")
                                .status(BoothStatus.PUBLISHED).isTemplate(false)
                                .createdBy(exhibitor1).company(company1).exhibitorRegistration(reg1)
                                .thumbnailUrl(boothThumb1.url()).thumbnailPublicId(boothThumb1.publicId())
                                .displayTemplateKey("classic").build());

                Uploaded boothThumb2 = upload(IMG_SENSOR, "seed/booth");
                Booth booth2 = boothRepository.save(Booth.builder()
                                .name("Gian hàng TechVina").description("Trưng bày thiết bị công nghệ thông minh.")
                                .status(BoothStatus.DRAFT).isTemplate(false)
                                .createdBy(exhibitor2).company(company2).exhibitorRegistration(reg2)
                                .thumbnailUrl(boothThumb2.url()).thumbnailPublicId(boothThumb2.publicId())
                                .displayTemplateKey("modern").build());

                // Phủ nốt BoothStatus: DESIGNING / PENDING / ARCHIVED (booth không gắn đơn đăng ký)
                Uploaded boothThumb3 = upload(IMG_EXPO_BOOTHS, "seed/booth");
                boothRepository.save(Booth.builder()
                                .name("Gian hàng đang thiết kế").description("Đang được designer dựng nội dung.")
                                .status(BoothStatus.DESIGNING).isTemplate(false)
                                .createdBy(exhibitor2).company(company2)
                                .thumbnailUrl(boothThumb3.url()).thumbnailPublicId(boothThumb3.publicId())
                                .displayTemplateKey("classic").build());
                Uploaded boothThumb4 = upload(IMG_EXPO_HALL, "seed/booth");
                boothRepository.save(Booth.builder()
                                .name("Gian hàng chờ duyệt").description("Đã gửi ban tổ chức, đang chờ xét duyệt.")
                                .status(BoothStatus.PENDING).isTemplate(false)
                                .createdBy(exhibitor1).company(company1)
                                .thumbnailUrl(boothThumb4.url()).thumbnailPublicId(boothThumb4.publicId())
                                .displayTemplateKey("classic").build());
                Uploaded boothThumb5 = upload(IMG_CABINET, "seed/booth");
                boothRepository.save(Booth.builder()
                                .name("Gian hàng đã lưu trữ").description("Gian hàng của kỳ triển lãm đã kết thúc.")
                                .status(BoothStatus.ARCHIVED).isTemplate(false)
                                .createdBy(exhibitor1).company(company1)
                                .thumbnailUrl(boothThumb5.url()).thumbnailPublicId(boothThumb5.publicId())
                                .displayTemplateKey("classic").build());
                log.info("[SEED] Đã tạo 5 booth (phủ đủ 5 BoothStatus)");

                // ---------- 10. PANORAMAS (ảnh equirectangular 360 thật) ----------
                Panorama pano1 = savePanorama(booth1, "Sảnh chính", 0, 0, true);
                Panorama pano2 = savePanorama(booth1, "Khu trưng bày", 1, 1, false);
                Panorama pano3 = savePanorama(booth2, "Sảnh TechVina", 2, 0, true);
                log.info("[SEED] Đã tạo 3 panorama");

                // ---------- 11. PRODUCT CATEGORIES ----------
                ProductCategory catSofa = saveCategory(company1, "Sofa & Ghế", "Các dòng sofa, ghế gỗ tự nhiên.");
                ProductCategory catTable = saveCategory(company1, "Bàn & Tủ", "Bàn ăn, bàn làm việc, tủ gỗ.");
                ProductCategory catDevice = saveCategory(company2, "Thiết bị thông minh",
                                "Thiết bị IoT cho văn phòng.");
                // Phủ ProductCategoryStatus.INACTIVE
                ProductCategory catDiscontinued = saveCategory(company1, "Hàng ngừng kinh doanh",
                                "Danh mục đã ẩn khỏi gian hàng.", ProductCategoryStatus.INACTIVE);
                log.info("[SEED] Đã tạo 4 product category (phủ đủ ProductCategoryStatus)");

                // ---------- 12 + 13. PRODUCTS + PRODUCT CONTENTS ----------
                Product sofa = saveProduct(company1, catSofa, "Sofa gỗ óc chó Luxury", "MV-SOFA-001",
                                "Sofa 3 chỗ khung gỗ óc chó, đệm da bò thật.", new BigDecimal("28500000"), IMG_SOFA);
                Product table = saveProduct(company1, catTable, "Bàn ăn gỗ sồi 6 chỗ", "MV-TABLE-002",
                                "Bàn ăn mặt gỗ sồi nguyên tấm, chân sắt sơn tĩnh điện.", new BigDecimal("15200000"),
                                IMG_TABLE);
                // Sản phẩm này có thêm ProductContent kiểu VIDEO -> phủ ProductContentType.VIDEO
                Product sensor = saveProduct(company2, catDevice, "Cảm biến môi trường TechVina S1", "TV-SEN-001",
                                "Đo nhiệt độ, độ ẩm, CO2 theo thời gian thực.", new BigDecimal("3200000"),
                                IMG_SENSOR, ProductStatus.ACTIVE, VIDEO_SENSOR);
                // Phủ ProductStatus.INACTIVE
                saveProduct(company1, catDiscontinued, "Tủ gỗ Xoan Đào (ngừng bán)", "MV-CAB-003",
                                "Mẫu tủ đã ngừng sản xuất, giữ lại để tra cứu.", new BigDecimal("9800000"),
                                IMG_CABINET, ProductStatus.INACTIVE, null);
                log.info("[SEED] Đã tạo 4 product + content (phủ đủ ProductStatus + ProductContentType)");

                // ---------- 14. MEDIA ASSETS ----------
                MediaAsset banner = saveMediaAsset(company1, "Banner khuyến mãi Mộc Việt", IMG_SHOWROOM);
                MediaAsset poster = saveMediaAsset(company2, "Poster giới thiệu TechVina", IMG_SENSOR);
                // Phủ MediaAssetType.VIDEO
                MediaAsset introVideo = saveVideoMediaAsset(company1, "Video giới thiệu xưởng Mộc Việt",
                                VIDEO_FURNITURE);
                log.info("[SEED] Đã tạo 3 media asset (phủ đủ MediaAssetType)");

                // ---------- 15. HOTSPOTS ----------
                // --- Gian hàng Mộc Việt: panorama "Sảnh chính" (pano1) ---
                hotspotRepository.save(Hotspot.builder().type(HotspotType.NAV).name("Sang khu trưng bày")
                                .sourcePanorama(pano1).targetPanorama(pano2)
                                .xPosition(120.0).yPosition(0.0).zPosition(-380.0)
                                .iconStyle("arrow").scale(1.0).zIndex(1).build());
                hotspotRepository.save(Hotspot.builder().type(HotspotType.PRODUCT).name("Sofa gỗ óc chó")
                                .sourcePanorama(pano1).product(sofa)
                                .xPosition(-210.0).yPosition(-30.0).zPosition(-320.0)
                                .iconStyle("tag").scale(1.0).zIndex(2).build());
                hotspotRepository.save(Hotspot.builder().type(HotspotType.INFO).name("Giới thiệu gian hàng")
                                .sourcePanorama(pano1)
                                .infoText("Mộc Việt - 15 năm kinh nghiệm trong ngành nội thất gỗ tự nhiên. "
                                                + "Chuyên sofa, bàn ăn, tủ gỗ óc chó và gỗ sồi nhập khẩu.")
                                .infoContentType(HotspotInfoContentType.TEXT)
                                .xPosition(40.0).yPosition(60.0).zPosition(-330.0)
                                .iconStyle("classic").scale(1.0).zIndex(3).build());

                // --- Gian hàng Mộc Việt: panorama "Khu trưng bày" (pano2) ---
                hotspotRepository.save(Hotspot.builder().type(HotspotType.MEDIA).name("Banner khuyến mãi")
                                .sourcePanorama(pano2).mediaAsset(banner)
                                .mediaClickAction(HotspotMediaClickAction.DEFAULT)
                                .xPosition(80.0).yPosition(40.0).zPosition(-400.0)
                                .iconStyle("image").scale(1.2).zIndex(1).build());

                // --- Gian hàng TechVina: panorama "Sảnh TechVina" (pano3) ---
                hotspotRepository.save(Hotspot.builder().type(HotspotType.INFO).name("Giới thiệu gian hàng")
                                .sourcePanorama(pano3)
                                .infoText("TechVina - giải pháp thiết bị thông minh cho doanh nghiệp. "
                                                + "Cảm biến môi trường, điều khiển tự động và giám sát từ xa.")
                                .infoContentType(HotspotInfoContentType.TEXT)
                                .xPosition(0.0).yPosition(20.0).zPosition(-350.0)
                                .iconStyle("classic").scale(1.0).zIndex(1).build());
                hotspotRepository.save(Hotspot.builder().type(HotspotType.PRODUCT).name("Cảm biến môi trường S1")
                                .sourcePanorama(pano3).product(sensor)
                                .xPosition(-190.0).yPosition(-20.0).zPosition(-330.0)
                                .iconStyle("tag").scale(1.0).zIndex(2).build());
                hotspotRepository.save(Hotspot.builder().type(HotspotType.MEDIA).name("Poster giới thiệu")
                                .sourcePanorama(pano3).mediaAsset(poster)
                                .mediaClickAction(HotspotMediaClickAction.DEFAULT)
                                .xPosition(200.0).yPosition(35.0).zPosition(-360.0)
                                .iconStyle("image").scale(1.2).zIndex(3).build());

                // --- Phủ nốt HotspotInfoContentType (NONE/IMAGE/VIDEO/PRODUCT) và
                // --- HotspotMediaClickAction.NONE ---
                hotspotRepository.save(Hotspot.builder().type(HotspotType.INFO).name("Điểm thông tin trống")
                                .sourcePanorama(pano2)
                                .infoContentType(HotspotInfoContentType.NONE)
                                .xPosition(-150.0).yPosition(10.0).zPosition(-360.0)
                                .iconStyle("classic").scale(1.0).zIndex(2).build());
                hotspotRepository.save(Hotspot.builder().type(HotspotType.INFO).name("Ảnh banner khuyến mãi")
                                .sourcePanorama(pano2).mediaAsset(banner)
                                .infoContentType(HotspotInfoContentType.IMAGE)
                                .xPosition(-60.0).yPosition(50.0).zPosition(-390.0)
                                .iconStyle("holo").scale(1.0).zIndex(3).build());
                hotspotRepository.save(Hotspot.builder().type(HotspotType.INFO).name("Video giới thiệu xưởng")
                                .sourcePanorama(pano2).mediaAsset(introVideo)
                                .infoContentType(HotspotInfoContentType.VIDEO)
                                .xPosition(220.0).yPosition(-10.0).zPosition(-340.0)
                                .iconStyle("pulse").scale(1.0).zIndex(4).build());
                hotspotRepository.save(Hotspot.builder().type(HotspotType.INFO).name("Chi tiết bàn ăn gỗ sồi")
                                .sourcePanorama(pano2).product(table)
                                .infoContentType(HotspotInfoContentType.PRODUCT)
                                .xPosition(-260.0).yPosition(-40.0).zPosition(-300.0)
                                .iconStyle("tag").scale(1.0).zIndex(5).build());
                hotspotRepository.save(Hotspot.builder().type(HotspotType.MEDIA).name("Poster chỉ hiển thị")
                                .sourcePanorama(pano3).mediaAsset(poster)
                                .mediaClickAction(HotspotMediaClickAction.NONE)
                                .xPosition(-40.0).yPosition(-50.0).zPosition(-370.0)
                                .iconStyle("image").scale(1.0).zIndex(4).build());
                log.info("[SEED] Đã tạo 12 hotspot (phủ đủ HotspotType, HotspotInfoContentType, "
                                + "HotspotMediaClickAction)");

                // ---------- 16. STORAGE PACKAGES ----------
                StoragePackage pkg1GB = storagePackageRepository.save(StoragePackage.builder()
                                .name("Gói 1 GB").description("Thêm 1 GB dung lượng lưu trữ.")
                                .quotaBytes(1_073_741_824L).priceVnd(50_000L).isActive(true).build());
                StoragePackage pkg5GB = storagePackageRepository.save(StoragePackage.builder()
                                .name("Gói 5 GB").description("Thêm 5 GB dung lượng lưu trữ.")
                                .quotaBytes(5_368_709_120L).priceVnd(200_000L).isActive(true).build());
                storagePackageRepository.save(StoragePackage.builder()
                                .name("Gói 20 GB (ngừng bán)").description("Gói cũ đã ngừng kinh doanh.")
                                .quotaBytes(21_474_836_480L).priceVnd(700_000L).isActive(false).build());
                log.info("[SEED] Đã tạo 3 storage package");

                // ---------- 17. STORAGE PACKAGE ORDERS ----------
                storagePackageOrderRepository.save(StoragePackageOrder.builder()
                                .company(company1).storagePackage(pkg5GB).orderCode(orderCode())
                                .amountVnd(pkg5GB.getPriceVnd()).status(StoragePackageOrderStatus.PAID)
                                .paidAt(Instant.now().minus(3, ChronoUnit.DAYS))
                                .checkoutUrl("https://pay.payos.vn/web/seed-paid").build());
                storagePackageOrderRepository.save(StoragePackageOrder.builder()
                                .company(company2).storagePackage(pkg1GB).orderCode(orderCode())
                                .amountVnd(pkg1GB.getPriceVnd()).status(StoragePackageOrderStatus.PENDING)
                                .checkoutUrl("https://pay.payos.vn/web/seed-pending").build());
                // Phủ StoragePackageOrderStatus.CANCELLED
                StoragePackageOrder cancelledOrder = storagePackageOrderRepository.save(StoragePackageOrder.builder()
                                .company(company2).storagePackage(pkg5GB).orderCode(orderCode())
                                .amountVnd(pkg5GB.getPriceVnd()).status(StoragePackageOrderStatus.CANCELLED)
                                .checkoutUrl("https://pay.payos.vn/web/seed-cancelled").build());
                log.info("[SEED] Đã tạo 3 storage package order (phủ đủ StoragePackageOrderStatus)");

                // Payment kiểu STORAGE_PACKAGE -> phủ nốt PaymentType
                paymentRepository.save(Payment.builder()
                                .storagePackageOrderId(cancelledOrder.getId())
                                .paymentType(PaymentType.STORAGE_PACKAGE)
                                .orderCode(orderCode()).amount(new BigDecimal(pkg5GB.getPriceVnd()))
                                .systemFee(BigDecimal.ZERO).organizerPayout(BigDecimal.ZERO)
                                .currency("VND").paymentProvider("PAYOS")
                                .status(PaymentStatus.PENDING).build());
                log.info("[SEED] Đã tạo payment STORAGE_PACKAGE (phủ đủ PaymentType)");

                // ---------- 18. PARTNERSHIP REQUESTS ----------
                partnershipRequestRepository.save(PartnershipRequest.builder()
                                .requesterName("Vũ Thị Hợp Tác").requesterEmail("guest.partner@example.com")
                                .requesterPhoneNumber("0906666666").organizationName("Công ty TNHH Ánh Dương")
                                .requestedRole(Role.EXHIBITOR)
                                .accountAction(PartnershipAccountAction.CREATE_NEW_ACCOUNT)
                                .message("Chúng tôi muốn mở gian hàng tại triển lãm sắp tới.")
                                .acceptedPolicy(Boolean.TRUE).status(PartnershipRequestStatus.PENDING)
                                .activeRequesterEmail("guest.partner@example.com").build());
                partnershipRequestRepository.save(PartnershipRequest.builder()
                                .requesterName("Đỗ Văn Chờ Duyệt").requesterEmail("guest.partner2@example.com")
                                .requesterPhoneNumber("0907777777").organizationName("Công ty CP Sao Mai")
                                .requestedRole(Role.ORGANIZER)
                                .accountAction(PartnershipAccountAction.CREATE_NEW_ACCOUNT)
                                .message("Chúng tôi muốn tổ chức triển lãm ngành xây dựng.")
                                .acceptedPolicy(Boolean.TRUE).status(PartnershipRequestStatus.AWAITING_VERIFICATION)
                                .activeRequesterEmail("guest.partner2@example.com").build());
                // Phủ nốt PartnershipRequestStatus (APPROVED/REJECTED/SUPERSEDED)
                // và PartnershipAccountAction.UPGRADE_EXISTING_USER.
                // activeRequesterEmail để null vì cột này unique - chỉ đơn đang "hoạt động" mới giữ giá trị.
                partnershipRequestRepository.save(PartnershipRequest.builder()
                                .submittedByUser(visitor)
                                .requesterName(visitor.getFullName()).requesterEmail(visitor.getEmail())
                                .requesterPhoneNumber("0905555555").organizationName("Hộ kinh doanh Hoàng Gia")
                                .requestedRole(Role.EXHIBITOR)
                                .accountAction(PartnershipAccountAction.UPGRADE_EXISTING_USER)
                                .message("Tôi đang là visitor, muốn nâng cấp thành exhibitor.")
                                .acceptedPolicy(Boolean.TRUE).status(PartnershipRequestStatus.APPROVED)
                                .approvedUser(visitor).reviewedAt(Instant.now().minus(5, ChronoUnit.DAYS)).build());
                partnershipRequestRepository.save(PartnershipRequest.builder()
                                .requesterName("Trịnh Bị Từ Chối").requesterEmail("guest.partner3@example.com")
                                .requesterPhoneNumber("0907888888").organizationName("Công ty TNHH Chưa Đủ Điều Kiện")
                                .requestedRole(Role.EXHIBITOR)
                                .accountAction(PartnershipAccountAction.CREATE_NEW_ACCOUNT)
                                .message("Chúng tôi muốn tham gia nền tảng.")
                                .acceptedPolicy(Boolean.TRUE).status(PartnershipRequestStatus.REJECTED)
                                .reviewNote("Thông tin doanh nghiệp chưa đầy đủ, vui lòng bổ sung giấy phép.")
                                .reviewedAt(Instant.now().minus(4, ChronoUnit.DAYS)).build());
                partnershipRequestRepository.save(PartnershipRequest.builder()
                                .requesterName("Trịnh Bị Từ Chối").requesterEmail("guest.partner3@example.com")
                                .requesterPhoneNumber("0907888888").organizationName("Công ty TNHH Chưa Đủ Điều Kiện")
                                .requestedRole(Role.EXHIBITOR)
                                .accountAction(PartnershipAccountAction.CREATE_NEW_ACCOUNT)
                                .message("Đơn cũ đã bị thay thế bởi đơn gửi lại sau đó.")
                                .acceptedPolicy(Boolean.TRUE).status(PartnershipRequestStatus.SUPERSEDED)
                                .reviewedAt(Instant.now().minus(6, ChronoUnit.DAYS)).build());
                log.info("[SEED] Đã tạo 5 partnership request "
                                + "(phủ đủ PartnershipRequestStatus + PartnershipAccountAction)");

                // ---------- 19. BOOTH REVIEW REQUEST ----------
                boothReviewRequestRepository.save(BoothReviewRequest.builder()
                                .booth(booth1).status(BoothReviewStatus.APPROVED).submittedBy(exhibitor1)
                                .reviewedBy(organizer).reviewedAt(Instant.now().minus(1, ChronoUnit.DAYS))
                                .versionNumber(1).build());
                boothReviewRequestRepository.save(BoothReviewRequest.builder()
                                .booth(booth2).status(BoothReviewStatus.PENDING).submittedBy(exhibitor2)
                                .versionNumber(1).build());
                // Phủ BoothReviewStatus.REJECTED
                boothReviewRequestRepository.save(BoothReviewRequest.builder()
                                .booth(booth2).status(BoothReviewStatus.REJECTED).submittedBy(exhibitor2)
                                .reviewedBy(organizer).reviewedAt(Instant.now().minus(2, ChronoUnit.DAYS))
                                .rejectedReason("Hình ảnh gian hàng chưa đạt chuẩn độ phân giải tối thiểu.")
                                .versionNumber(1).build());
                log.info("[SEED] Đã tạo 3 booth review request (phủ đủ BoothReviewStatus)");

                // ---------- 20. DESIGN REQUESTS (phủ đủ 6 trạng thái) ----------
                designRequestRepository.save(DesignRequest.builder()
                                .booth(booth2).company(company2).requestedBy(exhibitor2)
                                .assignedDesigner(designer).status(DesignRequestStatus.ASSIGNED)
                                .note("Cần thiết kế gian hàng tông xanh công nghệ, tối giản.")
                                .reviewCount(0).assignedAt(Instant.now().minus(1, ChronoUnit.DAYS)).build());
                designRequestRepository.save(DesignRequest.builder()
                                .booth(booth1).company(company1).requestedBy(exhibitor1)
                                .status(DesignRequestStatus.PENDING)
                                .note("Chưa có designer nhận, đang chờ phân công.")
                                .reviewCount(0).build());
                designRequestRepository.save(DesignRequest.builder()
                                .booth(booth1).company(company1).requestedBy(exhibitor1)
                                .assignedDesigner(designer).status(DesignRequestStatus.DRAFT_SUBMITTED)
                                .note("Designer đã nộp bản thiết kế đầu tiên.")
                                .reviewCount(0).assignedAt(Instant.now().minus(3, ChronoUnit.DAYS)).build());
                designRequestRepository.save(DesignRequest.builder()
                                .booth(booth1).company(company1).requestedBy(exhibitor1)
                                .assignedDesigner(designer).status(DesignRequestStatus.REVISION_REQUESTED)
                                .note("Yêu cầu chỉnh lại bố cục khu trưng bày.")
                                .reviewNote("Vui lòng tăng khoảng trống lối đi và đổi tông màu sáng hơn.")
                                .reviewCount(1).assignedAt(Instant.now().minus(5, ChronoUnit.DAYS)).build());
                designRequestRepository.save(DesignRequest.builder()
                                .booth(booth1).company(company1).requestedBy(exhibitor1)
                                .assignedDesigner(designer).status(DesignRequestStatus.APPROVED)
                                .note("Thiết kế đã được duyệt và áp dụng.")
                                .reviewCount(2).assignedAt(Instant.now().minus(10, ChronoUnit.DAYS))
                                .approvedAt(Instant.now().minus(7, ChronoUnit.DAYS)).build());
                designRequestRepository.save(DesignRequest.builder()
                                .booth(booth2).company(company2).requestedBy(exhibitor2)
                                .status(DesignRequestStatus.CANCELED)
                                .note("Exhibitor tự huỷ do đổi kế hoạch.")
                                .reviewCount(0).canceledAt(Instant.now().minus(2, ChronoUnit.DAYS)).build());
                log.info("[SEED] Đã tạo 6 design request (phủ đủ DesignRequestStatus)");

                // ---------- 21. CHAT ROOM + MESSAGES ----------
                ChatRoom room = chatRoomRepository.save(ChatRoom.builder()
                                .exhibition(exhibition).exhibitorUser(exhibitor1).visitorUser(visitor)
                                .lastMessageAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                                .lastMessagePreview("Bên mình có hỗ trợ giao hàng toàn quốc ạ.").build());
                chatMessageRepository.save(ChatMessage.builder().room(room).sender(visitor)
                                .senderRole(Role.VISITOR.name()).content("Chào shop, sofa này còn hàng không ạ?")
                                .build());
                chatMessageRepository.save(ChatMessage.builder().room(room).sender(exhibitor1)
                                .senderRole(Role.EXHIBITOR.name()).content("Chào bạn, sản phẩm còn hàng nhé!").build());
                chatMessageRepository.save(ChatMessage.builder().room(room).sender(exhibitor1)
                                .senderRole(Role.EXHIBITOR.name()).content("Bên mình có hỗ trợ giao hàng toàn quốc ạ.")
                                .build());
                log.info("[SEED] Đã tạo 1 chat room + 3 message");

                // ---------- 22. NOTIFICATIONS (không có repository -> dùng EntityManager)
                // ----------
                entityManager.persist(Notification.builder().recipient(exhibitor1).recipientRole(Role.EXHIBITOR)
                                .title("Đăng ký gian hàng được duyệt")
                                .content("Đăng ký của bạn tại triển lãm VEX360 2026 đã được duyệt.")
                                .deepLink("/exhibitor/dashboard").isRead(false).build());
                entityManager.persist(Notification.builder().recipient(organizer).recipientRole(Role.ORGANIZER)
                                .title("Có gian hàng chờ duyệt")
                                .content("Gian hàng TechVina đang chờ bạn xét duyệt nội dung.")
                                .deepLink("/organizer/dashboard").isRead(false).build());
                log.info("[SEED] Đã tạo 2 notification");

                // ---------- 23. BOOTH LEADS ----------
                entityManager.persist(BoothLead.builder().booth(booth1).visitor(visitor)
                                .fullName("Hoàng Khách Tham Quan").email("visitor@vex360.local")
                                .phoneNumber("0905555555").companyName("Công ty TNHH Minh Anh")
                                .message("Tôi muốn nhận báo giá bộ sofa gỗ óc chó.").build());
                entityManager.persist(BoothLead.builder().booth(booth1)
                                .fullName("Khách vãng lai").email("khachle@example.com")
                                .phoneNumber("0908888888").message("Cho mình xin catalogue sản phẩm.").build());
                log.info("[SEED] Đã tạo 2 booth lead");

                // ---------- 24. ANALYTICS EVENTS (rải qua nhiều ngày cho dashboard organizer) ----------
                // Mỗi ngày: một số lượt BOOTH_VIEW + ENTER/LEAVE_EXHIBITION (kèm thời lượng).
                // Tất cả gắn với triển lãm chính để query tổng hợp theo exhibition_id nhận được.
                int[] daysAgo = { 18, 15, 12, 9, 6, 4, 2, 1 };
                int[] viewsPerDay = { 4, 6, 5, 8, 7, 9, 8, 11 };
                int[] visitsPerDay = { 2, 3, 2, 4, 3, 4, 3, 5 };
                int totalAnalyticsEvents = 0;
                for (int i = 0; i < daysAgo.length; i++) {
                        Instant day = Instant.now().minus(daysAgo[i], ChronoUnit.DAYS);
                        for (int v = 0; v < viewsPerDay[i]; v++) {
                                seedAnalyticsEvent(AnalyticsEventType.BOOTH_VIEW, visitor, exhibition, booth1, null,
                                                null, day.plus(v, ChronoUnit.MINUTES));
                                totalAnalyticsEvents++;
                        }
                        for (int v = 0; v < visitsPerDay[i]; v++) {
                                seedAnalyticsEvent(AnalyticsEventType.ENTER_EXHIBITION, visitor, exhibition, null, null,
                                                null, day.plus(v * 3L, ChronoUnit.MINUTES));
                                // LEAVE kèm thời lượng (giây) để tính "thời lượng visit trung bình"
                                seedAnalyticsEvent(AnalyticsEventType.LEAVE_EXHIBITION, visitor, exhibition, null, null,
                                                480 + v * 40, day.plus(v * 3L + 6, ChronoUnit.MINUTES));
                                totalAnalyticsEvents += 2;
                        }
                }
                // Vài event khác loại để phủ đủ enum (PRODUCT_CLICK, CHAT_INITIATED)
                seedAnalyticsEvent(AnalyticsEventType.PRODUCT_CLICK, visitor, exhibition, booth1, sofa, 38,
                                Instant.now().minus(2, ChronoUnit.DAYS));
                seedAnalyticsEvent(AnalyticsEventType.CHAT_INITIATED, visitor, exhibition, booth1, null, 0,
                                Instant.now().minus(2, ChronoUnit.DAYS));
                totalAnalyticsEvents += 2;
                log.info("[SEED] Đã tạo {} analytics event (rải qua {} ngày, phủ đủ AnalyticsEventType)",
                                totalAnalyticsEvents, daysAgo.length);

                log.info("[SEED] HOÀN TẤT. Đăng nhập bằng bất kỳ email @vex360.local với mật khẩu: {}",
                                DEFAULT_PASSWORD);
        }

        // ================= Helpers =================

        private User createUser(String email, String fullName, Role role, String phone, int avatarIndex) {
                return createUser(email, fullName, role, phone, avatarIndex, UserStatus.ACTIVE, AuthProvider.LOCAL);
        }

        private User createUser(String email, String fullName, Role role, String phone, int avatarIndex,
                        UserStatus status, AuthProvider provider) {
                Uploaded avatar = upload("https://i.pravatar.cc/400?img=" + avatarIndex, "seed/avatar");
                return userRepository.save(User.builder()
                                .email(email)
                                // Tài khoản Google không có mật khẩu cục bộ
                                .password(provider == AuthProvider.GOOGLE
                                                ? null
                                                : passwordEncoder.encode(DEFAULT_PASSWORD))
                                .fullName(fullName)
                                .phoneNumber(phone)
                                .role(role)
                                .provider(provider)
                                .avatarUrl(avatar.url())
                                .status(status)
                                .build());
        }

        private Company createCompany(User owner, String name, String industry, String description, String logoSeed) {
                return createCompany(owner, name, industry, description, logoSeed, CompanyStatus.ACTIVE);
        }

        private Company createCompany(User owner, String name, String industry, String description, String logoSeed,
                        CompanyStatus status) {
                // Công ty chưa hoàn thiện hồ sơ thì để trống các trường tuỳ chọn cho đúng thực tế
                boolean incomplete = status == CompanyStatus.INCOMPLETE_PROFILE;
                Uploaded logo = incomplete ? null : upload(letterLogo(name), "seed/company");
                return companyRepository.save(Company.builder()
                                .ownerUser(owner).name(name)
                                .industry(incomplete ? null : industry)
                                .description(incomplete ? null : description)
                                .logoUrl(logo != null ? logo.url() : null)
                                .website(incomplete ? null : "https://" + logoSeed + ".example.com")
                                .email(owner.getEmail()).phone(owner.getPhoneNumber())
                                .address(incomplete ? null : "Số 1 Đại Cồ Việt, Hai Bà Trưng, Hà Nội")
                                .status(status)
                                .storageUsedBytes(0L).storageQuotaBytes(524_288_000L)
                                .build());
        }

        private Exhibition saveExhibition(User organizer, String name, String category, String description,
                        LocalDate startDate, LocalDate endDate, int estimatedBooths,
                        ExhibitionStatus status, User reviewedBy, String rejectedReason) {
                return exhibitionRepository.save(Exhibition.builder()
                                .organizer(organizer).name(name).category(category).description(description)
                                .startDate(startDate).endDate(endDate).estimatedBooths(estimatedBooths)
                                .status(status)
                                .reviewedBy(reviewedBy)
                                .reviewedAt(reviewedBy != null ? Instant.now().minus(2, ChronoUnit.DAYS) : null)
                                .rejectedReason(rejectedReason)
                                .rejectionCount(rejectedReason != null ? 1 : 0)
                                .build());
        }

        private ExhibitionPackage savePackage(PackageTemplate template, Exhibition exhibition, String finalPrice) {
                return savePackage(template, exhibition, finalPrice, ExhibitionPackageStatus.ACTIVE);
        }

        private ExhibitionPackage savePackage(PackageTemplate template, Exhibition exhibition, String finalPrice,
                        ExhibitionPackageStatus status) {
                return exhibitionPackageRepository.save(ExhibitionPackage.builder()
                                .template(template).exhibition(exhibition)
                                .finalPrice(new BigDecimal(finalPrice))
                                .status(status).build());
        }

        private ExhibitorRegistration buildRegistration(ExhibitionPackage pkg, User exhibitor,
                        PackageTemplate template, ExhibitorRegistrationStatus status, User reviewedBy,
                        String reason, String rejectedReason) {
                return ExhibitorRegistration.builder()
                                .exhibitionPackage(pkg).company(exhibitor).status(status)
                                .reviewedBy(reviewedBy).participationReason(reason)
                                .rejectedReason(rejectedReason)
                                .packageNameSnapshot(template.getName())
                                .priceSnapshot(template.getPrice())
                                .finalPriceSnapshot(pkg.getFinalPrice())
                                .currencySnapshot(template.getCurrency())
                                .maxProductsPerBoothSnapshot(template.getMaxProductsPerBooth())
                                .maxEmbeddedVideosPerBoothSnapshot(template.getMaxEmbeddedVideosPerBooth())
                                .maxPanoramasPerBoothSnapshot(template.getMaxPanoramasPerBooth())
                                .maxHotspotsPerBoothSnapshot(template.getMaxHotspotsPerBooth())
                                .storageLimitMbSnapshot(template.getStorageLimitMb())
                                .listingPrioritySnapshot(template.getListingPriority())
                                .build();
        }

        private Panorama savePanorama(Booth booth, String name, int imageIndex, int orderIndex, boolean isDefault) {
                Uploaded img = upload(PANORAMA_IMAGES[imageIndex % PANORAMA_IMAGES.length], "seed/panorama");
                return panoramaRepository.save(Panorama.builder()
                                .booth(booth).name(name)
                                .imageUrl(img.url()).imageKey(img.publicId())
                                .orderIndex(orderIndex).isDefault(isDefault).isTemplateDerived(false)
                                .build());
        }

        private ProductCategory saveCategory(Company company, String name, String description) {
                return saveCategory(company, name, description, ProductCategoryStatus.ACTIVE);
        }

        private ProductCategory saveCategory(Company company, String name, String description,
                        ProductCategoryStatus status) {
                return productCategoryRepository.save(ProductCategory.builder()
                                .company(company).name(name).description(description)
                                .status(status).build());
        }

        private Product saveProduct(Company company, ProductCategory category, String name, String sku,
                        String description, BigDecimal price, String imageUrl) {
                return saveProduct(company, category, name, sku, description, price, imageUrl,
                                ProductStatus.ACTIVE, null);
            }

        /**
         * @param imageUrl URL ảnh thật của sản phẩm (dùng cho cả thumbnail và
         *                 ProductContent kiểu IMAGE)
         * @param videoUrl URL video sản phẩm, null nếu không có
         *                 (phủ {@link ProductContentType#VIDEO})
         */
        private Product saveProduct(Company company, ProductCategory category, String name, String sku,
                        String description, BigDecimal price, String imageUrl, ProductStatus status,
                        String videoUrl) {
                Uploaded thumb = upload(imageUrl, "seed/product");
                Product product = Product.builder()
                                .company(company).category(category).name(name).sku(sku)
                                .description(description).price(price).currency("VND")
                                .thumbnailUrl(thumb.url()).thumbnailPublicId(thumb.publicId())
                                .thumbnailFileSize(thumb.bytes()).status(status)
                                .build();

                // Ảnh chi tiết dùng lại chính ảnh đã upload -> khỏi tốn thêm 1 lượt upload
                product.getContents().add(ProductContent.builder()
                                .product(product)
                                .contentUrl(thumb.url()).publicId(thumb.publicId())
                                .type(ProductContentType.IMAGE).orderIndex(1)
                                .mimeType("image/jpeg").fileSize(thumb.bytes())
                                .build());

                if (videoUrl != null) {
                        Uploaded video = upload(videoUrl, "seed/product");
                        product.getContents().add(ProductContent.builder()
                                        .product(product)
                                        .contentUrl(video.url()).publicId(video.publicId())
                                        .type(ProductContentType.VIDEO).orderIndex(2)
                                        .mimeType("video/webm").fileSize(video.bytes())
                                        .build());
                }
                return productRepository.save(product);
        }

        private MediaAsset saveMediaAsset(Company company, String name, String imageUrl) {
                Uploaded img = upload(imageUrl, "seed/media");
                return mediaAssetRepository.save(MediaAsset.builder()
                                .company(company).name(name).type(MediaAssetType.IMAGE)
                                .url(img.url()).publicId(img.publicId())
                                .mimeType("image/jpeg").fileSize(img.bytes())
                                .build());
        }

        /** Media asset kiểu VIDEO — phủ {@link MediaAssetType#VIDEO}. */
        private MediaAsset saveVideoMediaAsset(Company company, String name, String videoUrl) {
                Uploaded video = upload(videoUrl, "seed/media");
                return mediaAssetRepository.save(MediaAsset.builder()
                                .company(company).name(name).type(MediaAssetType.VIDEO)
                                .url(video.url()).publicId(video.publicId())
                                .mimeType("video/webm").fileSize(video.bytes())
                                .build());
        }

        /**
         * Tạo 1 analytics event với event_time chỉ định (backdate).
         *
         * <p>
         * Cột {@code event_time} dùng {@code @CreationTimestamp} nên khi persist,
         * Hibernate luôn set = thời điểm hiện tại, bỏ qua giá trị ta gán. Vì vậy sau khi
         * persist ta chạy 1 câu UPDATE native để ghi đè về ngày mong muốn — nhờ đó dữ
         * liệu trải qua nhiều ngày cho biểu đồ.
         */
        private void seedAnalyticsEvent(AnalyticsEventType type, User user, Exhibition exhibition,
                        Booth booth, Product product, Integer durationSeconds, Instant when) {
                AnalyticsEvent event = AnalyticsEvent.builder()
                                .user(user).booth(booth).product(product)
                                .eventType(type).durationSeconds(durationSeconds).build();
                entityManager.persist(event);
                entityManager.flush(); // đảm bảo có id + đã INSERT
                entityManager.createNativeQuery("UPDATE analytics_events SET event_time = :t WHERE id = :id")
                                .setParameter("t", when)
                                .setParameter("id", event.getId())
                                .executeUpdate();
        }

        /**
         * Sinh logo dạng chữ cái đầu từ tên công ty.
         *
         * <p>
         * Cố ý KHÔNG dùng ảnh logo doanh nghiệp có thật, vì các công ty trong dữ liệu
         * test đều là hư cấu — gán logo thật vào sẽ thành mạo danh.
         */
        private String letterLogo(String companyName) {
                return "https://ui-avatars.com/api/?name="
                                + URLEncoder.encode(companyName, StandardCharsets.UTF_8)
                                + "&size=400&background=" + LOGO_BG + "&color=fff&bold=true&format=png";
        }

        private long orderCode() {
                return System.currentTimeMillis() / 1000 * 1_000_000L
                                + ThreadLocalRandom.current().nextLong(1_000_000L);
        }

        /**
         * Upload ảnh lên Cloudinary từ một URL công khai.
         * Cloudinary tự tải ảnh về và host lại nên không cần file local.
         */
        private Uploaded upload(String remoteUrl, String folder) {
                try {
                        Map<?, ?> params = ObjectUtils.asMap("folder", folder, "resource_type", "auto");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> result = (Map<String, Object>) cloudinary.uploader().upload(remoteUrl,
                                        params);

                        String url = (String) result.get("secure_url");
                        String publicId = (String) result.get("public_id");
                        Object bytes = result.get("bytes");
                        long size = bytes instanceof Number number ? number.longValue() : 0L;

                        log.debug("[SEED] Uploaded {} -> {}", remoteUrl, publicId);
                        return new Uploaded(url, publicId, size);
                } catch (Exception e) {
                        // Không để lỗi upload làm hỏng toàn bộ seed (vd chưa cấu hình Cloudinary)
                        log.warn("[SEED] Upload Cloudinary thất bại cho {} ({}). Dùng tạm URL gốc.",
                                        remoteUrl, e.getMessage());
                        return new Uploaded(remoteUrl, "seed/placeholder-" + Math.abs(remoteUrl.hashCode()), 0L);
                }
        }
}
