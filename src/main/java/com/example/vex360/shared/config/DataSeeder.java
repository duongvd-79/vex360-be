package com.example.vex360.shared.config;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.core.annotation.Order;
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
import com.example.vex360.features.exhibition.entities.ExhibitionReviewRequest;
import com.example.vex360.features.exhibition.enums.ExhibitionReviewStatus;
import com.example.vex360.features.exhibition.repositories.ExhibitionReviewRequestRepository;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.entities.StoragePackage;
import com.example.vex360.features.company.entities.StoragePackageOrder;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.company.repositories.StoragePackageOrderRepository;
import com.example.vex360.features.company.repositories.StoragePackageRepository;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequestMessage;
import com.example.vex360.features.designrequest.enums.DesignDraftFileAction;
import com.example.vex360.features.designrequest.enums.DesignRequestCancellationStatus;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.repositories.DesignDraftPanoramaRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestMessageRepository;
import com.example.vex360.features.designrequest.entities.DesignRequestMediaAsset;
import com.example.vex360.features.designrequest.entities.DesignRequestProduct;
import com.example.vex360.features.designrequest.repositories.DesignRequestMediaAssetRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestProductRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.designrequest.services.DesignRequestBaselineService;
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
import com.example.vex360.shared.enums.LeadStatus;
import com.example.vex360.features.lead.repositories.BoothLeadRepository;
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
import com.example.vex360.features.wallet.dtos.UpdatePayoutProfileRequestDTO;
import com.example.vex360.features.wallet.repositories.CompanyPayoutProfileRepository;
import com.example.vex360.features.wallet.repositories.OrganizerWalletRepository;
import com.example.vex360.features.wallet.services.CompanyPayoutProfileService;
import com.example.vex360.features.wallet.services.PaymentRevenueRecognitionService;
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
@Order(1)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

        /** Nếu user này đã tồn tại thì coi như đã seed rồi -> bỏ qua. */
        private static final String MARKER_EMAIL = "organizer@vex360.local";
        private static final String LEGACY_GUEST_LEAD_EMAIL = "khachle@example.com";
        private static final String DEFAULT_PASSWORD = "123456";
        private static final String EXHREG_TEST_EMAIL = "exhreg.tester@vex360.local";
        private static final UUID EXHREG_EXHIBITION_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        private static final UUID EXHREG_CANCEL_EXHIBITION_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66afa7");
        private static final UUID EXHREG_REGISTRATION_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        private static final UUID EXHBTH_BOOTH_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        private static final UUID EXHBTH_REGISTRATION_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66afc0");
        private static final UUID EXHBTH_PANORAMA_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        private static final UUID EXHBTH_TARGET_PANORAMA_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66afa7");
        private static final UUID EXHBTH_HOTSPOT_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        private static final UUID EXHBTH_MEDIA_ASSET_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        private static final UUID EXHBTH_TEMPLATE_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66afb0");
        private static final UUID EXHBTH_TEMPLATE_PANORAMA_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66afb1");
        private static final UUID EXHBTH_TEMPLATE_TARGET_PANORAMA_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66afb2");
        private static final UUID EXHBTH_TEMPLATE_HOTSPOT_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66afb3");
        private static final UUID EXHBTH_PRODUCT_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        private static final UUID EXHBTH_CATEGORY_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        private static final UUID EXHLED_LEAD_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        private static final UUID EXHDSG_ELIGIBLE_BOOTH_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ad00");
        private static final UUID EXHDSG_CANCEL_BOOTH_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ad01");
        private static final UUID EXHDSG_ASSIGNED_BOOTH_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ad02");
        private static final UUID EXHDSG_APPROVE_BOOTH_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ad03");
        private static final UUID EXHDSG_REVIEW_BOOTH_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ad04");
        private static final UUID EXHDSG_REJECT_BOOTH_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ad05");
        private static final UUID EXHDSG_FINAL_REJECT_BOOTH_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ad06");
        private static final UUID EXHDSG_CANCEL_REQUEST_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ac01");
        private static final UUID EXHDSG_ASSIGNED_REQUEST_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ac02");
        private static final UUID EXHDSG_APPROVE_REQUEST_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ac03");
        private static final UUID EXHDSG_REVIEW_REQUEST_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ac04");
        private static final UUID EXHDSG_REJECT_REQUEST_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ac05");
        private static final UUID EXHDSG_FINAL_REJECT_REQUEST_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ac06");
        private static final UUID EXHDSG_ELIGIBLE_REGISTRATION_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ae00");
        private static final UUID EXHDSG_CANCEL_REGISTRATION_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ae01");
        private static final UUID EXHDSG_ASSIGNED_REGISTRATION_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ae02");
        private static final UUID EXHDSG_APPROVE_REGISTRATION_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ae03");
        private static final UUID EXHDSG_REVIEW_REGISTRATION_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ae04");
        private static final UUID EXHDSG_REJECT_REGISTRATION_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ae05");
        private static final UUID EXHDSG_FINAL_REJECT_REGISTRATION_UUID = UUID
                        .fromString("3fa85f64-5717-4562-b3fc-2c963f66ae06");
        private static final int SHRPKG_ORDER_PACKAGE_ID = 1;
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
        private final ExhibitionReviewRequestRepository exhibitionReviewRequestRepository;
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
        private final DesignDraftRepository designDraftRepository;
        private final DesignDraftPanoramaRepository designDraftPanoramaRepository;
        private final DesignRequestMessageRepository designRequestMessageRepository;
        private final DesignRequestProductRepository designRequestProductRepository;
        private final DesignRequestMediaAssetRepository designRequestMediaAssetRepository;
        private final DesignRequestBaselineService designRequestBaselineService;
        private final ChatRoomRepository chatRoomRepository;
        private final ChatMessageRepository chatMessageRepository;
        private final BoothLeadRepository boothLeadRepository;
        private final CompanyPayoutProfileRepository companyPayoutProfileRepository;
        private final OrganizerWalletRepository organizerWalletRepository;
        private final CompanyPayoutProfileService companyPayoutProfileService;
        private final PaymentRevenueRecognitionService paymentRevenueRecognitionService;

        private final PasswordEncoder passwordEncoder;
        private final Cloudinary cloudinary;
        private final EntityManager entityManager;

        /** Kết quả upload lên Cloudinary. */
        private record Uploaded(String url, String publicId, long bytes) {
        }

        @Override
        @Transactional
        public void run(ApplicationArguments args) {
                long removedGuestLeads = boothLeadRepository
                                .deleteByEmailIgnoreCaseAndVisitorIsNull(LEGACY_GUEST_LEAD_EMAIL);
                if (removedGuestLeads > 0) {
                        log.info("[SEED] Đã xóa {} booth lead khách vãng lai cũ.", removedGuestLeads);
                }

                if (userRepository.existsByEmail(MARKER_EMAIL)) {
                        ensureExhibitorRegistrationApiFixtures();
                        ensureExhibitorBoothApiFixtures();
                        ensureExhibitorDesignRequestApiFixtures();
                        ensureStoragePackageApiFixtures();
                        ensureExhibitorLeadApiFixtures();
                        ensureExhibitorReportApiFixtures();
                        log.info("[SEED] Dữ liệu demo đã tồn tại; fixture EXHREG đã được kiểm tra/cập nhật.");
                        User existingOrganizer = userRepository.findByEmail(MARKER_EMAIL).orElseThrow();
                        User existingAdmin = userRepository.findByEmail("admin@vex360.local").orElseThrow();
                        Company existingOrganizerCompany = companyRepository
                                        .findByOwnerUserId(existingOrganizer.getId())
                                        .orElseThrow();
                        seedOrganizerFinance(existingOrganizer, existingAdmin, existingOrganizerCompany);
                        log.info("[SEED] Core test data already exists; organizer finance data is ready.");
                        return;
                }

                log.info("[SEED] Bắt đầu seed dữ liệu test (ảnh sẽ được upload lên Cloudinary)...");

                // ---------- 1. USERS ----------
                User admin = userRepository.findByEmail("admin@vex360.local").orElseGet(
                                () -> createUser("admin@vex360.local", "Quản trị hệ thống", Role.ADMIN, "0900000000",
                                                11));
                User organizer = createUser(MARKER_EMAIL, "Nguyễn Văn An", Role.ORGANIZER, "0901111111", 12);
                User exhibitor1 = createUser("exhibitor@vex360.local", "Trần Quang Huy", Role.EXHIBITOR, "0902222222",
                                13);
                User exhregTester = createUser(EXHREG_TEST_EMAIL, "Nguyễn Kiểm Thử", Role.EXHIBITOR, "0907777777",
                                27);
                User exhibitor2 = createUser("exhibitor2@vex360.local", "Lê Thái Dương", Role.EXHIBITOR, "0903333333",
                                14);
                User designer = createUser("designer@vex360.local", "Phạm Thiên An", Role.DESIGNER, "0904444444", 15);
                User visitor = createUser("visitor@vex360.local", "Hoàng An Vy", Role.VISITOR, "0905555555",
                                16);

                // Phủ UserStatus (INACTIVE/PENDING/BLOCKED) và AuthProvider (GOOGLE)
                User visitorInactive = createUser("visitor.inactive@vex360.local", "Ngô Ngừng Hoạt Động", Role.VISITOR,
                                "0906111111", 21,
                                UserStatus.INACTIVE, AuthProvider.LOCAL);
                createUser("visitor.pending@vex360.local", "Đinh Chờ Kích Hoạt", Role.VISITOR, "0906222222", 22,
                                UserStatus.PENDING, AuthProvider.LOCAL);
                createUser("visitor.blocked@vex360.local", "Bùi Bị Khoá", Role.VISITOR, "0906333333", 23,
                                UserStatus.BLOCKED, AuthProvider.LOCAL);
                User visitorGoogle = createUser("visitor.google@vex360.local", "Lý Đăng Nhập Google", Role.VISITOR,
                                "0906444444", 24,
                                UserStatus.ACTIVE, AuthProvider.GOOGLE);
                // 2 chủ sở hữu cho company INCOMPLETE_PROFILE và ARCHIVED
                User exhibitor3 = createUser("exhibitor3@vex360.local", "Vũ Hồ Sơ Thiếu", Role.EXHIBITOR,
                                "0906555555", 25);
                User exhibitor4 = createUser("exhibitor4@vex360.local", "Tạ Đã Lưu Trữ", Role.EXHIBITOR,
                                "0906666666", 26);
                log.info("[SEED] Đã tạo 13 user - phủ đủ Role, UserStatus, AuthProvider (mật khẩu chung: {})",
                                DEFAULT_PASSWORD);

                // ---------- 2. COMPANIES (phủ đủ 3 CompanyStatus) ----------
                Company orgCompany = createCompany(organizer, "Công ty Tổ chức Sự kiện ABC",
                                "Sự kiện & Triển lãm", "Đơn vị tổ chức triển lãm ảo hàng đầu.", "logo-abc");
                Company company1 = createCompany(exhibitor1, "Công ty Nội thất Mộc Việt",
                                "Nội thất", "Chuyên nội thất gỗ tự nhiên cao cấp.", "logo-mocviet");
                Company exhregTestCompany = createCompany(exhregTester, "Công ty Kiểm thử EXHREG",
                                "Kiểm thử phần mềm", "Dữ liệu chuyên dùng cho test API đăng ký triển lãm.",
                                "logo-exhreg-test");
                Company company2 = createCompany(exhibitor2, "Công ty Công nghệ TechVina",
                                "Công nghệ", "Giải pháp thiết bị thông minh cho doanh nghiệp.", "logo-techvina");
                createCompany(exhibitor3, "Công ty TNHH Hồ Sơ Chưa Đủ",
                                "Chưa xác định", null, "logo-incomplete", CompanyStatus.INCOMPLETE_PROFILE);
                createCompany(exhibitor4, "Công ty CP Ngừng Hoạt Động",
                                "Bán lẻ", "Doanh nghiệp đã ngừng tham gia nền tảng.", "logo-archived",
                                CompanyStatus.ARCHIVED);
                log.info("[SEED] Đã tạo 6 company (ACTIVE / INCOMPLETE_PROFILE / ARCHIVED)");

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

                // Fixture riêng cho EXHREG_1..7. Đặt trước các exhibition package khác để
                // package đăng ký có ID=1 trên database sạch như test sheet đang dùng.
                ensureExhibitorRegistrationApiFixtures(
                                organizer, admin, exhregTester, exhregTestCompany, basicTemplate);
                ensureExhibitorBoothApiFixtures(
                                admin, exhregTester, exhregTestCompany, basicTemplate);
                ensureExhibitorDesignRequestApiFixtures();
                ensureStoragePackageApiFixtures();
                ensureExhibitorLeadApiFixtures();
                ensureExhibitorReportApiFixtures();

                // ---------- 4. EXHIBITIONS (phủ đủ 6 trạng thái) ----------
                // Ngày trải quanh "hôm nay" để triển lãm đang diễn ra và bao trùm các event
                // seed
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
                ExhibitionPackage designTestPackage = savePackage(basicTemplate, exhRegistration, "5000000");
                ExhibitionPackage regPackage = savePackage(basicTemplate, exhRegistration, "4500000");
                savePackage(premiumTemplate, exhRegistration, "12000000");
                savePackage(basicTemplate, exhActive, "6000000");
                ExhibitionPackage completedPackage = savePackage(basicTemplate, exhCompleted, "4000000");
                // Gói active để tạo booth mock; vẫn giữ thêm một gói inactive để phủ enum.
                ExhibitionPackage completedPremiumPackage = savePackage(premiumTemplate, exhCompleted, "11000000");
                savePackage(premiumTemplate, exhCompleted, "11000000", ExhibitionPackageStatus.INACTIVE);
                log.info("[SEED] Đã tạo 9 exhibition package (phủ đủ ExhibitionPackageStatus)");

                // ---------- 7. EXHIBITOR REGISTRATIONS (phủ đủ 5 trạng thái) ----------
                ExhibitorRegistration reg1 = exhibitorRegistrationRepository.save(buildRegistration(
                                premiumPackage, company1, premiumTemplate, ExhibitorRegistrationStatus.APPROVED,
                                admin, "Chúng tôi muốn giới thiệu bộ sưu tập nội thất gỗ mới.", null,
                                "Gian hàng Nội thất Mộc Việt",
                                "Gian hàng giới thiệu bộ sưu tập nội thất gỗ cao cấp Mộc Việt."));
                ExhibitorRegistration reg2 = exhibitorRegistrationRepository.save(buildRegistration(
                                basicPackage, company2, basicTemplate, ExhibitorRegistrationStatus.PENDING_PAYMENT,
                                null, "TechVina mong muốn tiếp cận khách hàng doanh nghiệp.", null,
                                "Gian hàng TechVina IoT",
                                "Gian hàng giới thiệu giải pháp nhà thông minh và thiết bị IoT TechVina."));
                ExhibitorRegistration designTestRegistration = exhibitorRegistrationRepository.save(buildRegistration(
                                designTestPackage, company2, basicTemplate, ExhibitorRegistrationStatus.APPROVED,
                                admin, "TechVina sử dụng dịch vụ thiết kế gian hàng 360.", null,
                                "Gian hàng TechVina", "Trưng bày thiết bị công nghệ thông minh."));
                ExhibitorRegistration payosWebhookTestRegistration = exhibitorRegistrationRepository
                                .save(buildRegistration(
                                                regPackage, company2, basicTemplate,
                                                ExhibitorRegistrationStatus.PENDING_PAYMENT,
                                                null, "Dữ liệu kiểm thử tích hợp webhook PayOS.", null,
                                                "PAYHK Webhook Integration Test",
                                                "Gian hàng chuyên dùng cho kiểm thử webhook PayOS."));
                ExhibitorRegistration reg4 = exhibitorRegistrationRepository.save(buildRegistration(
                                regPackage, company1, basicTemplate, ExhibitorRegistrationStatus.REJECTED,
                                organizer, "Mộc Việt đăng ký gian hàng nội thất gỗ.",
                                "Ngành hàng không phù hợp với chủ đề vật liệu xây dựng của triển lãm.",
                                "Gian hàng Gỗ Mộc Việt", "Bộ sưu tập sản phẩm gỗ tự nhiên cao cấp."));
                ExhibitorRegistration reg5 = exhibitorRegistrationRepository.save(buildRegistration(
                                completedPackage, company2, basicTemplate, ExhibitorRegistrationStatus.CANCELED,
                                null, "Đăng ký rồi tự huỷ do thay đổi kế hoạch kinh doanh.", null,
                                "Gian hàng TechVina Cũ", "Gian hàng thử nghiệm của TechVina."));
                ExhibitorRegistration completedReg1 = exhibitorRegistrationRepository.save(buildRegistration(
                                completedPackage, company1, basicTemplate, ExhibitorRegistrationStatus.APPROVED,
                                admin, "Trưng bày bộ sưu tập nội thất gỗ mùa thu.", null,
                                "Mộc Việt - Bộ sưu tập Mùa Thu", "Nội thất gỗ tự nhiên cho không gian sống mùa thu."));
                ExhibitorRegistration completedReg2 = exhibitorRegistrationRepository.save(buildRegistration(
                                completedPremiumPackage, company2, premiumTemplate,
                                ExhibitorRegistrationStatus.APPROVED,
                                admin, "Giới thiệu giải pháp nhà thông minh cho không gian sống.", null,
                                "TechVina Home - Nhà thông minh",
                                "Thiết bị thông minh cho không gian nội thất hiện đại."));
                log.info("[SEED] Đã tạo 9 exhibitor registration "
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
                                .exhibitorRegistration(payosWebhookTestRegistration)
                                .paymentType(PaymentType.EXHIBITION_REGISTRATION)
                                .orderCode(orderCode()).amount(new BigDecimal("4500000"))
                                .systemFee(new BigDecimal("450000")).organizerPayout(new BigDecimal("4050000"))
                                .currency("VND").paymentProvider("PAYOS")
                                .status(PaymentStatus.PENDING).build());
                paymentRepository.save(Payment.builder()
                                .exhibitorRegistration(designTestRegistration)
                                .paymentType(PaymentType.EXHIBITION_REGISTRATION)
                                .orderCode(orderCode()).amount(new BigDecimal("5000000"))
                                .systemFee(new BigDecimal("500000")).organizerPayout(new BigDecimal("4500000"))
                                .currency("VND").paymentProvider("PAYOS").paymentReference("SEED-DESIGN-TEST")
                                .status(PaymentStatus.PAID).paidAt(Instant.now().minus(2, ChronoUnit.DAYS)).build());
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
                Instant completedPaymentTime = exhCompleted.getStartDate().plusDays(2)
                                .atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant();
                paymentRepository.save(Payment.builder()
                                .exhibitorRegistration(completedReg1).paymentType(PaymentType.EXHIBITION_REGISTRATION)
                                .orderCode(orderCode()).amount(new BigDecimal("4000000"))
                                .systemFee(new BigDecimal("400000")).organizerPayout(new BigDecimal("3600000"))
                                .currency("VND").paymentProvider("PAYOS").paymentReference("SEED-AUTUMN-001")
                                .status(PaymentStatus.PAID).paidAt(completedPaymentTime).build());
                paymentRepository.save(Payment.builder()
                                .exhibitorRegistration(completedReg2).paymentType(PaymentType.EXHIBITION_REGISTRATION)
                                .orderCode(orderCode()).amount(new BigDecimal("11000000"))
                                .systemFee(new BigDecimal("1100000")).organizerPayout(new BigDecimal("9900000"))
                                .currency("VND").paymentProvider("PAYOS").paymentReference("SEED-AUTUMN-002")
                                .status(PaymentStatus.PAID).paidAt(completedPaymentTime.plus(2, ChronoUnit.DAYS))
                                .build());
                log.info("[SEED] Đã tạo 8 payment (PAID/PENDING/FAILED/EXPIRED)");

                // ---------- 8A. ORGANIZER WALLET + PAYOUT PROFILE ----------
                seedOrganizerFinance(organizer, admin, orgCompany);
                log.info("[SEED] Seeded organizer wallet ledger and VERIFIED payout profile");

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
                                .status(BoothStatus.DESIGNING).isTemplate(false)
                                .createdBy(exhibitor2).company(company2).exhibitorRegistration(designTestRegistration)
                                .thumbnailUrl(boothThumb2.url()).thumbnailPublicId(boothThumb2.publicId())
                                .displayTemplateKey("modern").build());

                // Booth thuộc "Triển lãm Nội thất Mùa Thu 2025" để trang chi tiết triển
                // lãm có leaderboard và dữ liệu analytics độc lập với triển lãm chính.
                Booth completedBooth1 = boothRepository.save(Booth.builder()
                                .name("Mộc Việt - Bộ sưu tập Mùa Thu")
                                .description("Nội thất gỗ tự nhiên cho không gian sống mùa thu.")
                                .status(BoothStatus.PUBLISHED).isTemplate(false)
                                .createdBy(exhibitor1).company(company1).exhibitorRegistration(completedReg1)
                                .thumbnailUrl(boothThumb1.url()).thumbnailPublicId(boothThumb1.publicId())
                                .displayTemplateKey("classic").build());
                Booth completedBooth2 = boothRepository.save(Booth.builder()
                                .name("TechVina Home - Nhà thông minh")
                                .description("Thiết bị thông minh cho không gian nội thất hiện đại.")
                                .status(BoothStatus.PUBLISHED).isTemplate(false)
                                .createdBy(exhibitor2).company(company2).exhibitorRegistration(completedReg2)
                                .thumbnailUrl(boothThumb2.url()).thumbnailPublicId(boothThumb2.publicId())
                                .displayTemplateKey("modern").build());

                // Phủ nốt BoothStatus: DESIGNING / PENDING / ARCHIVED (booth không gắn đơn đăng
                // ký)
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

                // ---------- 9.1. BOOTH LEADS ----------
                Instant leadSeedTime = Instant.now().minus(3, ChronoUnit.DAYS);
                boothLeadRepository.save(BoothLead.builder()
                                .booth(booth1).visitor(visitor)
                                .fullName(visitor.getFullName()).email(visitor.getEmail())
                                .phoneNumber(visitor.getPhoneNumber()).companyName("An Vy Studio")
                                .message("Tôi muốn nhận báo giá bộ sofa và lịch tư vấn trực tuyến.")
                                .status(LeadStatus.NEW).consentAt(leadSeedTime).build());
                boothLeadRepository.save(BoothLead.builder()
                                .booth(booth1).visitor(visitorGoogle)
                                .fullName(visitorGoogle.getFullName()).email(visitorGoogle.getEmail())
                                .phoneNumber(visitorGoogle.getPhoneNumber()).companyName("Lý Gia Decor")
                                .message("Quan tâm chính sách hợp tác đại lý và mức chiết khấu.")
                                .status(LeadStatus.QUALIFIED)
                                .exhibitorNote("Nhu cầu rõ ràng, hẹn gửi catalogue.")
                                .consentAt(leadSeedTime.minus(1, ChronoUnit.DAYS)).build());
                boothLeadRepository.save(BoothLead.builder()
                                .booth(booth1).visitor(visitorInactive)
                                .fullName(visitorInactive.getFullName()).email(visitorInactive.getEmail())
                                .phoneNumber(visitorInactive.getPhoneNumber()).companyName("Nội Thất An Nhiên")
                                .message("Đăng ký nhận thông tin sản phẩm mới.")
                                .status(LeadStatus.LOST)
                                .exhibitorNote("Không liên hệ được sau ba lần.")
                                .consentAt(leadSeedTime.minus(2, ChronoUnit.DAYS)).build());
                log.info("[SEED] Đã tạo 3 booth lead mẫu cho gian hàng Mộc Việt");

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
                // Sản phẩm này có thêm ProductContent kiểu VIDEO -> phủ
                // ProductContentType.VIDEO
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
                // activeRequesterEmail để null vì cột này unique - chỉ đơn đang "hoạt động" mới
                // giữ giá trị.
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

                // ---------- 20. DESIGN REQUESTS (phủ đủ 6 trạng thái + 2 mode) ----------
                // booth2 chưa có panorama nào -> INITIAL_DESIGN; booth1 đã có nội dung/đã
                // publish
                // -> các yêu cầu sau đó là REDESIGN.
                DesignRequest assignedDesignRequest = designRequestRepository.save(DesignRequest.builder()
                                .booth(booth2).company(company2).requestedBy(exhibitor2)
                                .assignedDesigner(designer).status(DesignRequestStatus.ASSIGNED)
                                .mode(DesignRequestMode.INITIAL_DESIGN)
                                .note("Cần thiết kế gian hàng tông xanh công nghệ, tối giản.")
                                .reviewCount(0).assignedAt(Instant.now().minus(1, ChronoUnit.DAYS)).build());
                designRequestBaselineService.createWorkingBaseline(assignedDesignRequest);
                assignedDesignRequest = designRequestRepository.save(assignedDesignRequest);
                designRequestRepository.save(DesignRequest.builder()
                                .booth(booth1).company(company1).requestedBy(exhibitor1)
                                .status(DesignRequestStatus.PENDING)
                                .mode(DesignRequestMode.REDESIGN)
                                .note("Chưa có designer nhận, đang chờ phân công.")
                                .reviewCount(0).build());
                designRequestRepository.save(DesignRequest.builder()
                                .booth(booth1).company(company1).requestedBy(exhibitor1)
                                .assignedDesigner(designer).status(DesignRequestStatus.DRAFT_SUBMITTED)
                                .mode(DesignRequestMode.REDESIGN)
                                .note("Designer đã nộp bản thiết kế đầu tiên.")
                                .reviewCount(0).assignedAt(Instant.now().minus(3, ChronoUnit.DAYS)).build());
                designRequestRepository.save(DesignRequest.builder()
                                .booth(booth1).company(company1).requestedBy(exhibitor1)
                                .assignedDesigner(designer).status(DesignRequestStatus.REVISION_REQUESTED)
                                .mode(DesignRequestMode.REDESIGN)
                                .note("Yêu cầu chỉnh lại bố cục khu trưng bày.")
                                .reviewCount(1).assignedAt(Instant.now().minus(5, ChronoUnit.DAYS)).build());
                designRequestRepository.save(DesignRequest.builder()
                                .booth(booth1).company(company1).requestedBy(exhibitor1)
                                .assignedDesigner(designer).status(DesignRequestStatus.APPROVED)
                                .mode(DesignRequestMode.REDESIGN)
                                .note("Thiết kế đã được duyệt và áp dụng.")
                                .reviewCount(2).assignedAt(Instant.now().minus(10, ChronoUnit.DAYS))
                                .approvedAt(Instant.now().minus(7, ChronoUnit.DAYS)).build());
                designRequestRepository.save(DesignRequest.builder()
                                .booth(booth2).company(company2).requestedBy(exhibitor2)
                                .status(DesignRequestStatus.CANCELED)
                                .mode(DesignRequestMode.INITIAL_DESIGN)
                                .note("Exhibitor tự huỷ do đổi kế hoạch.")
                                .reviewCount(0).canceledAt(Instant.now().minus(2, ChronoUnit.DAYS)).build());
                designRequestProductRepository.save(DesignRequestProduct.builder()
                                .designRequest(assignedDesignRequest).product(sensor)
                                .requiredFromBaseline(false).build());
                designRequestMediaAssetRepository.save(DesignRequestMediaAsset.builder()
                                .designRequest(assignedDesignRequest).mediaAsset(poster)
                                .requiredFromBaseline(false).build());
                designRequestMessageRepository.save(DesignRequestMessage.builder()
                                .designRequest(assignedDesignRequest).sender(exhibitor2)
                                .message("Vui lòng ưu tiên sản phẩm cảm biến và poster giới thiệu trong thiết kế.")
                                .build());
                log.info("[SEED] Đã tạo 6 design request (phủ đủ DesignRequestStatus + DesignRequestMode)");

                // ---------- 21. CHAT ROOM + MESSAGES ----------
                ChatRoom room = chatRoomRepository.save(ChatRoom.builder()
                                .exhibition(exhActive).exhibitorUser(exhibitor1).visitorUser(visitor)
                                .lastMessageAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                                .lastMessagePreview("Bên mình có hỗ trợ giao hàng toàn quốc ạ.").build());
                chatMessageRepository.save(ChatMessage.builder().room(room).sender(visitor)
                                .senderRole(Role.VISITOR.name()).content("Chào shop, sofa này còn hàng không ạ?")
                                .build());
                chatMessageRepository.save(ChatMessage.builder().room(room).sender(exhibitor1)
                                .senderRole(Role.EXHIBITOR.name()).content("Chào bạn, sản phẩm còn hàng nhé!").build());
                chatMessageRepository.save(ChatMessage.builder().room(room).sender(exhibitor1)
                                .senderRole(Role.EXHIBITOR.name())
                                .content("Bên mình có hỗ trợ giao hàng toàn quốc ạ.").build());
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

                // ---------- 23. ANALYTICS: TRIỂN LÃM NỘI THẤT MÙA THU 2025 ----------
                // Rải event đúng trong thời gian triển lãm đã kết thúc. Nhờ đó tab Analytics
                // của /organizer/dashboard/exhibitions/{id} có đầy đủ KPI, chart và ranking.
                User[] autumnVisitors = { visitor, exhibitor1, exhibitor2 };
                int[] autumnDays = { 2, 5, 9, 14, 19, 24 };
                int[] autumnViews = { 6, 9, 7, 12, 10, 14 };
                int autumnAnalyticsEvents = 0;
                ZoneId seedZone = ZoneId.systemDefault();
                for (int dayIndex = 0; dayIndex < autumnDays.length; dayIndex++) {
                        Instant dayStart = exhCompleted.getStartDate().plusDays(autumnDays[dayIndex])
                                        .atStartOfDay(seedZone).toInstant();

                        // Mỗi ngày có ba khách vào/ra triển lãm để KPI khách duy nhất và thời
                        // lượng truy cập trung bình có dữ liệu.
                        for (int visitorIndex = 0; visitorIndex < autumnVisitors.length; visitorIndex++) {
                                Instant entryTime = dayStart.plus(9 + visitorIndex * 2L, ChronoUnit.HOURS);
                                int exhibitionDuration = 420 + dayIndex * 25 + visitorIndex * 35;
                                seedAnalyticsEvent(AnalyticsEventType.ENTER_EXHIBITION, autumnVisitors[visitorIndex],
                                                exhCompleted, null, null, null, entryTime);
                                seedAnalyticsEvent(AnalyticsEventType.LEAVE_EXHIBITION, autumnVisitors[visitorIndex],
                                                exhCompleted, null, null, exhibitionDuration,
                                                entryTime.plus(exhibitionDuration, ChronoUnit.SECONDS));
                                autumnAnalyticsEvents += 2;
                        }

                        // Booth 1 được xem nhiều hơn booth 2 để leaderboard có thứ hạng rõ ràng.
                        for (int viewIndex = 0; viewIndex < autumnViews[dayIndex]; viewIndex++) {
                                Booth booth = viewIndex % 3 == 0 ? completedBooth2 : completedBooth1;
                                User eventUser = autumnVisitors[viewIndex % autumnVisitors.length];
                                Instant viewTime = dayStart.plus(10 + (viewIndex % 8), ChronoUnit.HOURS)
                                                .plus(viewIndex * 4L, ChronoUnit.MINUTES);
                                int boothDuration = 75 + ((dayIndex + viewIndex) % 5) * 55;
                                seedAnalyticsEvent(AnalyticsEventType.BOOTH_VIEW, eventUser, exhCompleted, booth,
                                                null, null, viewTime);
                                seedAnalyticsEvent(AnalyticsEventType.BOOTH_LEAVE, eventUser, exhCompleted, booth,
                                                null, boothDuration, viewTime.plus(boothDuration, ChronoUnit.SECONDS));
                                autumnAnalyticsEvents += 2;
                        }

                        // Chat được gắn booth để cột "Phiên chat" của leaderboard có dữ liệu.
                        for (int chatIndex = 0; chatIndex < 1 + (dayIndex % 2); chatIndex++) {
                                Booth booth = chatIndex == 0 ? completedBooth1 : completedBooth2;
                                seedAnalyticsEvent(AnalyticsEventType.CHAT_INITIATED,
                                                autumnVisitors[(dayIndex + chatIndex) % autumnVisitors.length],
                                                exhCompleted, booth, null, null,
                                                dayStart.plus(16 + chatIndex, ChronoUnit.HOURS));
                                autumnAnalyticsEvents++;
                        }
                }
                log.info("[SEED] Đã tạo {} analytics event cho Triển lãm Nội thất Mùa Thu 2025",
                                autumnAnalyticsEvents);

                // ---------- 25. ANALYTICS EVENTS (rải qua nhiều ngày cho dashboard organizer +
                // gian hàng) ----------
                // Mỗi ngày: một số lượt BOOTH_VIEW (giờ khác nhau trong ngày) + BOOTH_LEAVE kèm
                // thời
                // lượng, cộng ENTER/LEAVE_EXHIBITION. Tất cả gắn với triển lãm chính và booth1
                // (Mộc
                // Việt) để cả dashboard organizer lẫn trang "Thống kê gian hàng" của exhibitor1
                // đều
                // có dữ liệu thật ngay khi seed xong (không cần tự click qua viewer 360 để tạo
                // dữ liệu).
                int[] daysAgo = { 18, 15, 12, 9, 6, 4, 2, 1 };
                int[] viewsPerDay = { 4, 6, 5, 8, 7, 9, 8, 11 };
                int[] visitsPerDay = { 2, 3, 2, 4, 3, 4, 3, 5 };
                // Giờ trong ngày để rải lượt xem gian hàng -> biểu đồ "lượt xem theo giờ" có
                // phân bố thật
                // thay vì dồn hết vào 1 giờ cố định.
                int[] hourSlots = { 9, 10, 11, 13, 14, 15, 17, 19, 20, 21 };
                // // Thời lượng ở booth (giây), cố tình phủ đủ 5 khoảng của histogram thời gian
                // ở
                // // booth:
                // // <30s, 30-60s, 1-3 phút, 3-5 phút, >5 phút.
                int[] boothDurationsSeconds = { 15, 45, 90, 150, 240, 340, 20, 100 };
                int totalAnalyticsEvents = 0;
                for (int i = 0; i < daysAgo.length; i++) {
                        Instant day = Instant.now().minus(daysAgo[i], ChronoUnit.DAYS);
                        for (int v = 0; v < viewsPerDay[i]; v++) {
                                int hour = hourSlots[(i + v) % hourSlots.length];
                                Instant viewTime = day.atZone(seedZone)
                                                .withHour(hour).withMinute(v).withSecond(0).withNano(0)
                                                .toInstant();
                                seedAnalyticsEvent(AnalyticsEventType.BOOTH_VIEW, visitor, exhibition,
                                                booth1, null,
                                                null, viewTime);
                                // BOOTH_LEAVE kèm thời lượng -> phủ histogram "thời gian ở trong booth"
                                int duration = boothDurationsSeconds[(i + v) % boothDurationsSeconds.length];
                                seedAnalyticsEvent(AnalyticsEventType.BOOTH_LEAVE, visitor, exhibition,
                                                booth1, null,
                                                duration, viewTime.plus(duration, ChronoUnit.SECONDS));
                                totalAnalyticsEvents += 2;
                        }
                        Instant tenAm = day.atZone(seedZone).withHour(10).withMinute(0).withSecond(0).withNano(0)
                                        .toInstant();
                        for (int v = 0; v < visitsPerDay[i]; v++) {
                                seedAnalyticsEvent(AnalyticsEventType.ENTER_EXHIBITION, visitor, exhibition,
                                                null, null,
                                                null, tenAm.plus(v * 3L, ChronoUnit.MINUTES));
                                // LEAVE kèm thời lượng (giây) để tính "thời lượng visit trung bình"
                                seedAnalyticsEvent(AnalyticsEventType.LEAVE_EXHIBITION, visitor, exhibition,
                                                null, null,
                                                480 + v * 40, tenAm.plus(v * 3L + 6, ChronoUnit.MINUTES));
                                totalAnalyticsEvents += 2;
                        }
                }

                // Bảng xếp hạng "hotspot/sản phẩm được click nhiều nhất" ở trang thống kê gian
                // hàng
                // -- tên khớp với sản phẩm/hotspot đã seed ở trên để số liệu có ý nghĩa.
                totalAnalyticsEvents += seedBoothClicks(visitor, exhibition, booth1,
                                AnalyticsEventType.PRODUCT_CLICK, sofa.getName(), sofa, 12);
                totalAnalyticsEvents += seedBoothClicks(visitor, exhibition, booth1,
                                AnalyticsEventType.HOTSPOT_CLICK, "Giới thiệu gian hàng", null, 9);
                totalAnalyticsEvents += seedBoothClicks(visitor, exhibition, booth1,
                                AnalyticsEventType.PRODUCT_CLICK, table.getName(), table, 6);
                totalAnalyticsEvents += seedBoothClicks(visitor, exhibition, booth1,
                                AnalyticsEventType.HOTSPOT_CLICK, "Banner khuyến mãi", null, 4);

                // Phủ nốt CHAT_INITIATED
                seedAnalyticsEvent(AnalyticsEventType.CHAT_INITIATED, visitor, exhibition,
                                booth1, null, 0,
                                Instant.now().minus(2, ChronoUnit.DAYS));
                totalAnalyticsEvents += 1;
                log.info("[SEED] Đã tạo {} analytics event (rải qua {} ngày, phủ đủ AnalyticsEventType)",
                                totalAnalyticsEvents, daysAgo.length);

                log.info("[SEED] HOÀN TẤT. Đăng nhập bằng bất kỳ email @vex360.local với mật khẩu: {}",
                                DEFAULT_PASSWORD);

                log.info("[SEED] HOÀN TẤT (đã tắt phần seed analytics event giả lập).");
        }

        private void seedOrganizerFinance(User organizer, User admin, Company company) {
                if (organizerWalletRepository.findByCompanyId(company.getId()).isEmpty()) {
                        paymentRepository.findAll().stream()
                                        .filter(payment -> payment.getStatus() == PaymentStatus.PAID)
                                        .filter(payment -> payment.getPaymentReference() != null
                                                        && payment.getPaymentReference().startsWith("SEED-"))
                                        .forEach(paymentRevenueRecognitionService::recognizeRevenueForPayment);
                }

                if (companyPayoutProfileRepository.findByCompanyId(company.getId()).isEmpty()) {
                        companyPayoutProfileService.updateProfileForOrganizer(organizer,
                                        UpdatePayoutProfileRequestDTO.builder()
                                                        .bankCode("VCB")
                                                        .bankNameSnapshot("Vietcombank")
                                                        .accountNumber("0123456789")
                                                        .accountHolderName("NGUYEN VAN AN")
                                                        .build());
                        companyPayoutProfileService.verifyProfileForAdmin(company.getId(), admin);
                }
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
                // Công ty chưa hoàn thiện hồ sơ thì để trống các trường tuỳ chọn cho đúng thực
                // tế
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

        private void ensureExhibitorRegistrationApiFixtures() {
                User organizer = userRepository.findByEmail(MARKER_EMAIL).orElse(null);
                User admin = userRepository.findByEmail("admin@vex360.local").orElse(null);
                if (organizer == null || admin == null) {
                        log.warn("[SEED][EXHREG] Thiếu organizer/admin nền; không thể bổ sung fixture API.");
                        return;
                }

                User tester = userRepository.findByEmail(EXHREG_TEST_EMAIL)
                                .orElseGet(() -> createUser(
                                                EXHREG_TEST_EMAIL,
                                                "Nguyễn Kiểm Thử",
                                                Role.EXHIBITOR,
                                                "0907777777",
                                                27));
                tester.setFullName("Nguyễn Kiểm Thử");
                tester.setPhoneNumber("0907777777");
                tester.setRole(Role.EXHIBITOR);
                tester.setProvider(AuthProvider.LOCAL);
                tester.setStatus(UserStatus.ACTIVE);
                tester.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
                User persistedTester = userRepository.save(tester);

                Company testCompany = companyRepository.findByOwnerUserId(persistedTester.getId())
                                .orElseGet(() -> createCompany(
                                                persistedTester,
                                                "Công ty Kiểm thử EXHREG",
                                                "Kiểm thử phần mềm",
                                                "Dữ liệu chuyên dùng cho test API đăng ký triển lãm.",
                                                "logo-exhreg-test"));
                testCompany.setName("Công ty Kiểm thử EXHREG");
                testCompany.setIndustry("Kiểm thử phần mềm");
                testCompany.setDescription("Dữ liệu chuyên dùng cho test API đăng ký triển lãm.");
                testCompany.setEmail(EXHREG_TEST_EMAIL);
                testCompany.setPhone("0907777777");
                testCompany.setAddress("Phòng kiểm thử VEX360, Hà Nội");
                testCompany.setStatus(CompanyStatus.ACTIVE);
                testCompany = companyRepository.save(testCompany);

                PackageTemplate template = packageTemplateRepository
                                .findByStatus(PackageTemplateStatus.ACTIVE, Sort.by("createdAt").ascending())
                                .stream()
                                .filter(candidate -> "Gói Cơ Bản".equals(candidate.getName()))
                                .findFirst()
                                .orElseGet(() -> packageTemplateRepository
                                                .findByStatus(PackageTemplateStatus.ACTIVE,
                                                                Sort.by("createdAt").ascending())
                                                .stream()
                                                .findFirst()
                                                .orElse(null));
                if (template == null) {
                        log.warn("[SEED][EXHREG] Không có package template ACTIVE; không thể bổ sung fixture API.");
                        return;
                }

                ensureExhibitorRegistrationApiFixtures(organizer, admin, persistedTester, testCompany, template);
        }

        private void ensureExhibitorRegistrationApiFixtures(
                        User organizer,
                        User admin,
                        User tester,
                        Company testCompany,
                        PackageTemplate template) {
                LocalDate today = LocalDate.now();
                Exhibition discoveryExhibition = ensureExhregExhibition(
                                EXHREG_EXHIBITION_UUID,
                                organizer,
                                admin,
                                "Triển lãm Kiểm thử Đăng ký Exhibitor",
                                "Kiểm thử API",
                                "Fixture cố định cho EXHREG_1, EXHREG_2 và EXHREG_4.",
                                today.plusDays(45),
                                today.plusDays(60));
                Exhibition cancellationExhibition = ensureExhregExhibition(
                                EXHREG_CANCEL_EXHIBITION_UUID,
                                organizer,
                                admin,
                                "Triển lãm Kiểm thử Hủy đăng ký Exhibitor",
                                "Kiểm thử API",
                                "Fixture cố định cho EXHREG_3, EXHREG_5, EXHREG_6 và EXHREG_7.",
                                today.plusDays(50),
                                today.plusDays(65));

                ExhibitionPackage registrationPackage = ensureExhregPackage(
                                template, discoveryExhibition, "2500000");
                ExhibitionPackage cancellationPackage = ensureExhregPackage(
                                template, cancellationExhibition, "2500000");

                // Đưa các đăng ký chưa hoàn tất do lần test POST trước về trạng thái không
                // còn active để EXHREG_4 có thể chạy lại sau khi restart ứng dụng.
                exhibitorRegistrationRepository
                                .findByCompanyIdAndExhibitionPackageExhibitionId(
                                                testCompany.getId(), discoveryExhibition.getId())
                                .stream()
                                .filter(registration -> registration.getStatus() == ExhibitorRegistrationStatus.PENDING
                                                || registration.getStatus() == ExhibitorRegistrationStatus.PENDING_PAYMENT)
                                .forEach(registration -> {
                                        registration.setStatus(ExhibitorRegistrationStatus.CANCELED);
                                        exhibitorRegistrationRepository.save(registration);
                                });

                ExhibitorRegistration cancellableRegistration = exhibitorRegistrationRepository
                                .findByUuid(EXHREG_REGISTRATION_UUID)
                                .orElseGet(() -> {
                                        ExhibitorRegistration registration = buildRegistration(
                                                        cancellationPackage,
                                                        testCompany,
                                                        template,
                                                        ExhibitorRegistrationStatus.PENDING,
                                                        null,
                                                        "Kiểm thử xem chi tiết, trạng thái thanh toán và hủy đăng ký.",
                                                        null);
                                        registration.setUuid(EXHREG_REGISTRATION_UUID);
                                        return registration;
                                });
                cancellableRegistration.setExhibitionPackage(cancellationPackage);
                cancellableRegistration.setCompany(testCompany);
                cancellableRegistration.setStatus(ExhibitorRegistrationStatus.PENDING);
                cancellableRegistration.setReviewedBy(null);
                cancellableRegistration.setRejectedReason(null);
                cancellableRegistration.setParticipationReason(
                                "Kiểm thử xem chi tiết, trạng thái thanh toán và hủy đăng ký.");
                cancellableRegistration.setBoothName("Gian hàng kiểm thử EXHREG");
                cancellableRegistration.setBoothDescription(
                                "Gian hàng fixture dành riêng cho kiểm thử API đăng ký triển lãm.");
                cancellableRegistration = exhibitorRegistrationRepository.save(cancellableRegistration);

                var fixturePayments = paymentRepository
                                .findByExhibitorRegistrationIdForUpdate(cancellableRegistration.getId());
                fixturePayments.stream()
                                .filter(payment -> payment.getStatus() != PaymentStatus.PAID)
                                .forEach(payment -> {
                                        payment.setStatus(PaymentStatus.FAILED);
                                        paymentRepository.save(payment);
                                });
                if (fixturePayments.isEmpty()) {
                        paymentRepository.save(Payment.builder()
                                        .exhibitorRegistration(cancellableRegistration)
                                        .paymentType(PaymentType.EXHIBITION_REGISTRATION)
                                        .orderCode(orderCode())
                                        .amount(new BigDecimal("2500000"))
                                        .systemFee(new BigDecimal("250000"))
                                        .organizerPayout(new BigDecimal("2250000"))
                                        .currency("VND")
                                        .paymentProvider("PAYOS")
                                        .paymentReference("SEED-EXHREG-CANCEL")
                                        .checkoutUrl("https://pay.payos.vn/web/seed-exhreg-cancel")
                                        .status(PaymentStatus.FAILED)
                                        .build());
                }

                log.info("[SEED][EXHREG] READY | email={} | password={} | exhibitionUuid={} "
                                + "| exhibitionPackageId={} | registrationUuid={}",
                                EXHREG_TEST_EMAIL,
                                DEFAULT_PASSWORD,
                                EXHREG_EXHIBITION_UUID,
                                registrationPackage.getId(),
                                EXHREG_REGISTRATION_UUID);
        }

        private Exhibition ensureExhregExhibition(
                        UUID uuid,
                        User organizer,
                        User admin,
                        String name,
                        String category,
                        String description,
                        LocalDate startDate,
                        LocalDate endDate) {
                Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                                .orElseGet(() -> saveExhibition(
                                                uuid,
                                                organizer,
                                                name,
                                                category,
                                                description,
                                                startDate,
                                                endDate,
                                                20,
                                                ExhibitionStatus.REGISTRATION,
                                                admin,
                                                null));
                exhibition.setOrganizer(organizer);
                exhibition.setName(name);
                exhibition.setCategory(category);
                exhibition.setDescription(description);
                exhibition.setStartDate(startDate);
                exhibition.setEndDate(endDate);
                exhibition.setEstimatedBooths(20);
                exhibition.setStatus(ExhibitionStatus.REGISTRATION);
                exhibition.setReviewedBy(admin);
                exhibition.setRejectedReason(null);
                exhibition.setRejectionCount(0);
                return exhibitionRepository.save(exhibition);
        }

        private ExhibitionPackage ensureExhregPackage(
                        PackageTemplate template,
                        Exhibition exhibition,
                        String finalPrice) {
                ExhibitionPackage exhibitionPackage = exhibitionPackageRepository
                                .findByExhibitionIdAndTemplateId(exhibition.getId(), template.getId())
                                .orElseGet(() -> ExhibitionPackage.builder()
                                                .template(template)
                                                .exhibition(exhibition)
                                                .build());
                exhibitionPackage.setFinalPrice(new BigDecimal(finalPrice));
                exhibitionPackage.setStatus(ExhibitionPackageStatus.ACTIVE);
                return exhibitionPackageRepository.save(exhibitionPackage);
        }

        private void ensureExhibitorBoothApiFixtures() {
                User admin = userRepository.findByEmail("admin@vex360.local").orElse(null);
                User tester = userRepository.findByEmail(EXHREG_TEST_EMAIL).orElse(null);
                if (admin == null || tester == null) {
                        log.warn("[SEED][EXHBTH] Thiếu admin hoặc tài khoản Exhibitor fixture.");
                        return;
                }

                Company testCompany = companyRepository.findByOwnerUserId(tester.getId()).orElse(null);
                Exhibition exhibition = exhibitionRepository.findByUuid(EXHREG_EXHIBITION_UUID).orElse(null);
                if (testCompany == null || exhibition == null) {
                        log.warn("[SEED][EXHBTH] Thiếu company hoặc triển lãm EXHREG nền.");
                        return;
                }

                ExhibitionPackage exhibitionPackage = exhibitionPackageRepository
                                .findByExhibitionId(exhibition.getId())
                                .stream()
                                .filter(candidate -> candidate.getStatus() == ExhibitionPackageStatus.ACTIVE)
                                .findFirst()
                                .orElse(null);
                if (exhibitionPackage == null || exhibitionPackage.getTemplate() == null) {
                        log.warn("[SEED][EXHBTH] Không có exhibition package ACTIVE để cấp booth fixture.");
                        return;
                }

                ensureExhibitorBoothApiFixtures(
                                admin,
                                tester,
                                testCompany,
                                exhibitionPackage.getTemplate());
        }

        private void ensureExhibitorBoothApiFixtures(
                        User admin,
                        User tester,
                        Company testCompany,
                        PackageTemplate template) {
                Exhibition exhibition = exhibitionRepository.findByUuid(EXHREG_EXHIBITION_UUID)
                                .orElseThrow();
                ExhibitionPackage exhibitionPackage = ensureExhregPackage(template, exhibition, "2500000");

                testCompany.setStatus(CompanyStatus.ACTIVE);
                testCompany.setStorageQuotaBytes(Math.max(
                                testCompany.getStorageQuotaBytes() == null ? 0L : testCompany.getStorageQuotaBytes(),
                                2_147_483_648L));
                Company persistedTestCompany = companyRepository.save(testCompany);

                ExhibitorRegistration registration = exhibitorRegistrationRepository
                                .findByUuid(EXHBTH_REGISTRATION_UUID)
                                .orElseGet(() -> {
                                        ExhibitorRegistration created = buildRegistration(
                                                        exhibitionPackage,
                                                        persistedTestCompany,
                                                        template,
                                                        ExhibitorRegistrationStatus.APPROVED,
                                                        admin,
                                                        "Fixture cho bộ test quản lý booth EXHBTH.",
                                                        null);
                                        created.setUuid(EXHBTH_REGISTRATION_UUID);
                                        return created;
                                });
                registration.setExhibitionPackage(exhibitionPackage);
                registration.setCompany(persistedTestCompany);
                registration.setStatus(ExhibitorRegistrationStatus.APPROVED);
                registration.setReviewedBy(admin);
                registration.setRejectedReason(null);
                registration.setBoothName("Gian hàng kiểm thử EXHBTH");
                registration.setBoothDescription("Fixture cố định cho API quản lý booth và nội dung.");
                registration = exhibitorRegistrationRepository.save(registration);

                Booth exhibitorBooth = boothRepository.findById(EXHBTH_BOOTH_UUID).orElse(null);
                if (exhibitorBooth == null) {
                        exhibitorBooth = insertBoothFixtureShell(EXHBTH_BOOTH_UUID, tester);
                }
                exhibitorBooth.setName("Gian hàng kiểm thử EXHBTH");
                exhibitorBooth.setDescription("Fixture có thể khôi phục sau khi chạy test tạo, sửa hoặc xóa.");
                exhibitorBooth.setStatus(BoothStatus.PUBLISHED);
                exhibitorBooth.setIsTemplate(false);
                exhibitorBooth.setCreatedBy(tester);
                exhibitorBooth.setCompany(persistedTestCompany);
                exhibitorBooth.setExhibitorRegistration(registration);
                exhibitorBooth.setThumbnailUrl(IMG_SHOWROOM);
                exhibitorBooth.setThumbnailPublicId(null);
                exhibitorBooth.setBackgroundMusicUrl("https://example.com/seed/exhbth-background.mp3");
                exhibitorBooth.setBackgroundMusicPublicId("seed/exhbth/background-music");
                exhibitorBooth.setBackgroundMusicFileName("exhbth-background.mp3");
                exhibitorBooth.setBackgroundMusicFileSize(1_024L);
                exhibitorBooth.setDisplayTemplateKey("classic");
                exhibitorBooth.setLateEditAllowedUntil(null);
                exhibitorBooth = boothRepository.save(exhibitorBooth);

                boothReviewRequestRepository.deleteAll(
                                boothReviewRequestRepository.findByBoothIdInAndStatus(
                                                List.of(exhibitorBooth.getId()),
                                                BoothReviewStatus.PENDING));
                resetFixtureContent(
                                exhibitorBooth,
                                EXHBTH_PANORAMA_UUID,
                                EXHBTH_TARGET_PANORAMA_UUID,
                                EXHBTH_HOTSPOT_UUID,
                                "Không gian chính EXHBTH");

                ProductCategory category = productCategoryRepository.findById(EXHBTH_CATEGORY_UUID).orElse(null);
                if (category == null) {
                        category = insertProductCategoryFixtureShell(EXHBTH_CATEGORY_UUID, persistedTestCompany);
                }
                category.setCompany(persistedTestCompany);
                category.setName("Danh mục kiểm thử EXHBTH");
                category.setDescription("Danh mục cố định cho EXHBTH_38 đến EXHBTH_45.");
                category.setStatus(ProductCategoryStatus.ACTIVE);
                category = productCategoryRepository.save(category);

                Product product = productRepository.findById(EXHBTH_PRODUCT_UUID).orElse(null);
                if (product == null) {
                        product = insertProductFixtureShell(
                                        EXHBTH_PRODUCT_UUID, persistedTestCompany, category);
                }
                product.setCompany(persistedTestCompany);
                product.setCategory(category);
                product.setName("Sản phẩm kiểm thử EXHBTH");
                product.setSku("EXHBTH-FIXTURE-001");
                product.setDescription("Sản phẩm cố định cho API xem, cập nhật và xóa.");
                product.setPrice(new BigDecimal("100000"));
                product.setCurrency("VND");
                product.setThumbnailUrl(IMG_SOFA);
                product.setThumbnailPublicId("seed/exhbth/product");
                product.setThumbnailFileSize(1_024L);
                product.setStatus(ProductStatus.ACTIVE);
                productRepository.save(product);

                MediaAsset mediaAsset = mediaAssetRepository.findById(EXHBTH_MEDIA_ASSET_UUID).orElse(null);
                if (mediaAsset == null) {
                        mediaAsset = insertMediaAssetFixtureShell(
                                        EXHBTH_MEDIA_ASSET_UUID, persistedTestCompany);
                }
                mediaAsset.setCompany(persistedTestCompany);
                mediaAsset.setName("Media kiểm thử EXHBTH");
                mediaAsset.setType(MediaAssetType.IMAGE);
                mediaAsset.setUrl(IMG_EXPO_HALL);
                mediaAsset.setPublicId("seed/exhbth/media-asset");
                mediaAsset.setMimeType("image/jpeg");
                mediaAsset.setFileSize(2_048L);
                mediaAssetRepository.save(mediaAsset);

                Booth boothTemplate = boothRepository.findById(EXHBTH_TEMPLATE_UUID).orElse(null);
                if (boothTemplate == null) {
                        boothTemplate = insertBoothFixtureShell(EXHBTH_TEMPLATE_UUID, admin);
                }
                boothTemplate.setName("Booth template kiểm thử EXHBTH");
                boothTemplate.setDescription("Template cố định dùng cho API Admin và thao tác apply của Exhibitor.");
                boothTemplate.setStatus(BoothStatus.PUBLISHED);
                boothTemplate.setIsTemplate(true);
                boothTemplate.setCreatedBy(admin);
                boothTemplate.setCompany(null);
                boothTemplate.setExhibitorRegistration(null);
                boothTemplate.setThumbnailUrl(IMG_EXPO_BOOTHS);
                boothTemplate.setThumbnailPublicId(null);
                boothTemplate.setBackgroundMusicUrl(null);
                boothTemplate.setBackgroundMusicPublicId(null);
                boothTemplate.setBackgroundMusicFileName(null);
                boothTemplate.setBackgroundMusicFileSize(null);
                boothTemplate.setDisplayTemplateKey("classic");
                boothTemplate = boothRepository.save(boothTemplate);
                resetFixtureContent(
                                boothTemplate,
                                EXHBTH_TEMPLATE_PANORAMA_UUID,
                                EXHBTH_TEMPLATE_TARGET_PANORAMA_UUID,
                                EXHBTH_TEMPLATE_HOTSPOT_UUID,
                                "Không gian template EXHBTH");

                log.info("[SEED][EXHBTH] READY | exhibitorEmail={} | adminEmail={} | password={} "
                                + "| boothUuid={} | panoramaUuid={} | hotspotUuid={} | mediaAssetUuid={} "
                                + "| templateUuid={} | templatePanoramaUuid={} | templateHotspotUuid={} "
                                + "| productUuid={} | categoryUuid={}",
                                EXHREG_TEST_EMAIL,
                                "admin@vex360.local",
                                DEFAULT_PASSWORD,
                                EXHBTH_BOOTH_UUID,
                                EXHBTH_PANORAMA_UUID,
                                EXHBTH_HOTSPOT_UUID,
                                EXHBTH_MEDIA_ASSET_UUID,
                                EXHBTH_TEMPLATE_UUID,
                                EXHBTH_TEMPLATE_PANORAMA_UUID,
                                EXHBTH_TEMPLATE_HOTSPOT_UUID,
                                EXHBTH_PRODUCT_UUID,
                                EXHBTH_CATEGORY_UUID);
        }

        /**
         * Rebuilds an isolated, idempotent data set for EXHDSG_1..13.
         *
         * <p>
         * Mutating cases deliberately use different requests so approve, reject,
         * cancel and cancellation-request can be executed in any order without
         * invalidating the remaining happy cases.
         * </p>
         */
        private void ensureExhibitorDesignRequestApiFixtures() {
                User admin = userRepository.findByEmail("admin@vex360.local").orElse(null);
                User tester = userRepository.findByEmail(EXHREG_TEST_EMAIL).orElse(null);
                User designer = userRepository.findByEmail("designer@vex360.local").orElse(null);
                if (admin == null || tester == null || designer == null) {
                        log.warn("[SEED][EXHDSG] Missing admin, Exhibitor fixture, or Designer fixture.");
                        return;
                }

                Company company = companyRepository.findByOwnerUserId(tester.getId()).orElse(null);
                Exhibition exhibition = exhibitionRepository.findByUuid(EXHREG_EXHIBITION_UUID).orElse(null);
                if (company == null || exhibition == null) {
                        log.warn("[SEED][EXHDSG] Missing EXHREG company or exhibition foundation.");
                        return;
                }

                ExhibitionPackage exhibitionPackage = exhibitionPackageRepository
                                .findByExhibitionId(exhibition.getId())
                                .stream()
                                .filter(candidate -> candidate.getStatus() == ExhibitionPackageStatus.ACTIVE)
                                .findFirst()
                                .orElse(null);
                if (exhibitionPackage == null || exhibitionPackage.getTemplate() == null) {
                        log.warn("[SEED][EXHDSG] Missing ACTIVE exhibition package.");
                        return;
                }

                company.setStatus(CompanyStatus.ACTIVE);
                company.setEmail(EXHREG_TEST_EMAIL);
                company.setPhone("0907777777");
                company = companyRepository.save(company);

                List<UUID> boothIds = List.of(
                                EXHDSG_ELIGIBLE_BOOTH_UUID,
                                EXHDSG_CANCEL_BOOTH_UUID,
                                EXHDSG_ASSIGNED_BOOTH_UUID,
                                EXHDSG_APPROVE_BOOTH_UUID,
                                EXHDSG_REVIEW_BOOTH_UUID,
                                EXHDSG_REJECT_BOOTH_UUID,
                                EXHDSG_FINAL_REJECT_BOOTH_UUID);

                // Also remove a random request created by EXHDSG_2 in a previous run.
                List<UUID> existingRequestIds = entityManager.createQuery("""
                                SELECT request.id
                                FROM DesignRequest request
                                WHERE request.booth.id IN :boothIds
                                """, UUID.class)
                                .setParameter("boothIds", boothIds)
                                .getResultList();
                if (!existingRequestIds.isEmpty()) {
                        designRequestRepository.deleteAll(designRequestRepository.findAllById(existingRequestIds));
                        designRequestRepository.flush();
                        // Hibernate keeps deleted UUID entities in the persistence context. Clear
                        // them before recreating the same deterministic IDs below.
                        entityManager.clear();
                }

                PackageTemplate template = exhibitionPackage.getTemplate();
                Booth eligibleBooth = ensureExhdsgBooth(
                                EXHDSG_ELIGIBLE_BOOTH_UUID,
                                EXHDSG_ELIGIBLE_REGISTRATION_UUID,
                                "EXHDSG - Booth eligible to create request",
                                BoothStatus.DRAFT,
                                tester, admin, company, exhibitionPackage, template);
                Booth cancelBooth = ensureExhdsgBooth(
                                EXHDSG_CANCEL_BOOTH_UUID,
                                EXHDSG_CANCEL_REGISTRATION_UUID,
                                "EXHDSG - Booth with pending request",
                                BoothStatus.DESIGN_REQUEST_PENDING,
                                tester, admin, company, exhibitionPackage, template);
                Booth assignedBooth = ensureExhdsgBooth(
                                EXHDSG_ASSIGNED_BOOTH_UUID,
                                EXHDSG_ASSIGNED_REGISTRATION_UUID,
                                "EXHDSG - Booth with assigned request",
                                BoothStatus.DESIGNING,
                                tester, admin, company, exhibitionPackage, template);
                Booth approveBooth = ensureExhdsgBooth(
                                EXHDSG_APPROVE_BOOTH_UUID,
                                EXHDSG_APPROVE_REGISTRATION_UUID,
                                "EXHDSG - Booth awaiting approval",
                                BoothStatus.DESIGNING,
                                tester, admin, company, exhibitionPackage, template);
                Booth reviewBooth = ensureExhdsgBooth(
                                EXHDSG_REVIEW_BOOTH_UUID,
                                EXHDSG_REVIEW_REGISTRATION_UUID,
                                "EXHDSG - Booth review workspace",
                                BoothStatus.DESIGNING,
                                tester, admin, company, exhibitionPackage, template);
                Booth rejectBooth = ensureExhdsgBooth(
                                EXHDSG_REJECT_BOOTH_UUID,
                                EXHDSG_REJECT_REGISTRATION_UUID,
                                "EXHDSG - Booth awaiting revision decision",
                                BoothStatus.DESIGNING,
                                tester, admin, company, exhibitionPackage, template);
                Booth finalRejectBooth = ensureExhdsgBooth(
                                EXHDSG_FINAL_REJECT_BOOTH_UUID,
                                EXHDSG_FINAL_REJECT_REGISTRATION_UUID,
                                "EXHDSG - Booth awaiting final rejection",
                                BoothStatus.DESIGNING,
                                tester, admin, company, exhibitionPackage, template);

                seedExhdsgRequest(
                                EXHDSG_CANCEL_REQUEST_UUID, cancelBooth, company, tester, null,
                                DesignRequestStatus.PENDING);
                seedExhdsgRequest(
                                EXHDSG_ASSIGNED_REQUEST_UUID, assignedBooth, company, tester, designer,
                                DesignRequestStatus.ASSIGNED);
                DesignRequest approveRequest = seedExhdsgRequest(
                                EXHDSG_APPROVE_REQUEST_UUID, approveBooth, company, tester, designer,
                                DesignRequestStatus.DRAFT_SUBMITTED);
                DesignRequest reviewRequest = seedExhdsgRequest(
                                EXHDSG_REVIEW_REQUEST_UUID, reviewBooth, company, tester, designer,
                                DesignRequestStatus.DRAFT_SUBMITTED);
                DesignRequest rejectRequest = seedExhdsgRequest(
                                EXHDSG_REJECT_REQUEST_UUID, rejectBooth, company, tester, designer,
                                DesignRequestStatus.DRAFT_SUBMITTED);
                DesignRequest finalRejectRequest = seedExhdsgRequest(
                                EXHDSG_FINAL_REJECT_REQUEST_UUID, finalRejectBooth, company, tester, designer,
                                DesignRequestStatus.DRAFT_SUBMITTED);

                seedExhdsgDraft(approveRequest, 1, "Draft ready for EXHDSG_5 approval.");
                seedExhdsgDraft(reviewRequest, 1, "Historical draft used by EXHDSG_8.");
                seedExhdsgDraft(reviewRequest, 2, "Latest draft used by EXHDSG_12 workspace.");
                seedExhdsgDraft(rejectRequest, 1, "Draft ready for EXHDSG_11 rejection.");
                seedExhdsgDraft(finalRejectRequest, 1, "Draft ready for EXHDSG_13 final rejection.");

                designRequestMessageRepository.save(DesignRequestMessage.builder()
                                .designRequest(reviewRequest)
                                .sender(designer)
                                .message("Please review version 2; the main panorama has been finalized.")
                                .build());
                designRequestMessageRepository.save(DesignRequestMessage.builder()
                                .designRequest(reviewRequest)
                                .sender(tester)
                                .message("Received. I will review the latest draft.")
                                .build());

                log.info("[SEED][EXHDSG] READY | exhibitorEmail={} | password={} | eligibleBoothUuid={} "
                                + "| cancelRequestUuid={} | assignedRequestUuid={} | approveRequestUuid={} "
                                + "| reviewRequestUuid={} | rejectRequestUuid={} | finalRejectRequestUuid={}",
                                EXHREG_TEST_EMAIL,
                                DEFAULT_PASSWORD,
                                eligibleBooth.getId(),
                                EXHDSG_CANCEL_REQUEST_UUID,
                                EXHDSG_ASSIGNED_REQUEST_UUID,
                                EXHDSG_APPROVE_REQUEST_UUID,
                                EXHDSG_REVIEW_REQUEST_UUID,
                                EXHDSG_REJECT_REQUEST_UUID,
                                EXHDSG_FINAL_REJECT_REQUEST_UUID);
        }

        private Booth ensureExhdsgBooth(
                        UUID boothId,
                        UUID registrationUuid,
                        String boothName,
                        BoothStatus boothStatus,
                        User tester,
                        User admin,
                        Company company,
                        ExhibitionPackage exhibitionPackage,
                        PackageTemplate template) {
                ExhibitorRegistration registration = exhibitorRegistrationRepository
                                .findByUuid(registrationUuid)
                                .orElseGet(() -> {
                                        ExhibitorRegistration created = buildRegistration(
                                                        exhibitionPackage,
                                                        company,
                                                        template,
                                                        ExhibitorRegistrationStatus.APPROVED,
                                                        admin,
                                                        "Dedicated fixture for EXHDSG API tests.",
                                                        null);
                                        created.setUuid(registrationUuid);
                                        return created;
                                });
                registration.setExhibitionPackage(exhibitionPackage);
                registration.setCompany(company);
                registration.setStatus(ExhibitorRegistrationStatus.APPROVED);
                registration.setReviewedBy(admin);
                registration.setRejectedReason(null);
                registration.setParticipationReason("Dedicated fixture for EXHDSG API tests.");
                registration.setBoothName(boothName);
                registration.setBoothDescription(
                                "Isolated booth data for Exhibitor design-request collaboration tests.");
                registration.setPackageNameSnapshot(template.getName());
                registration.setPriceSnapshot(template.getPrice());
                registration.setFinalPriceSnapshot(exhibitionPackage.getFinalPrice());
                registration.setCurrencySnapshot(template.getCurrency());
                registration.setMaxProductsPerBoothSnapshot(template.getMaxProductsPerBooth());
                registration.setMaxEmbeddedVideosPerBoothSnapshot(template.getMaxEmbeddedVideosPerBooth());
                registration.setMaxPanoramasPerBoothSnapshot(template.getMaxPanoramasPerBooth());
                registration.setMaxHotspotsPerBoothSnapshot(template.getMaxHotspotsPerBooth());
                registration.setStorageLimitMbSnapshot(template.getStorageLimitMb());
                registration.setListingPrioritySnapshot(template.getListingPriority());
                registration = exhibitorRegistrationRepository.save(registration);

                Booth booth = boothRepository.findById(boothId).orElse(null);
                if (booth == null) {
                        booth = insertBoothFixtureShell(boothId, tester);
                }
                booth.setName(boothName);
                booth.setDescription("Dedicated and resettable EXHDSG test fixture.");
                booth.setStatus(boothStatus);
                booth.setIsTemplate(false);
                booth.setCreatedBy(tester);
                booth.setCompany(company);
                booth.setExhibitorRegistration(registration);
                booth.setThumbnailUrl(null);
                booth.setThumbnailPublicId(null);
                booth.setBackgroundMusicUrl(null);
                booth.setBackgroundMusicPublicId(null);
                booth.setBackgroundMusicFileName(null);
                booth.setBackgroundMusicFileSize(null);
                booth.setDisplayTemplateKey("classic");
                booth.setLateEditAllowedUntil(null);
                booth = boothRepository.save(booth);
                clearExhdsgBoothContent(booth);
                return booth;
        }

        private void clearExhdsgBoothContent(Booth booth) {
                List<Panorama> panoramas = panoramaRepository.findByBoothIdOrderByOrderIndexAsc(booth.getId());
                if (panoramas.isEmpty()) {
                        return;
                }
                List<UUID> panoramaIds = panoramas.stream().map(Panorama::getId).toList();
                hotspotRepository.clearTargetsForPanoramas(panoramaIds);
                hotspotRepository.flush();
                for (Panorama panorama : panoramas) {
                        hotspotRepository.deleteAll(
                                        hotspotRepository.findBySourcePanoramaIdOrderByNameAsc(panorama.getId()));
                }
                hotspotRepository.flush();
                panoramaRepository.deleteAll(panoramas);
                panoramaRepository.flush();
        }

        private DesignRequest seedExhdsgRequest(
                        UUID requestId,
                        Booth booth,
                        Company company,
                        User tester,
                        User designer,
                        DesignRequestStatus status) {
                DesignRequest request = designRequestRepository.findById(requestId).orElse(null);
                if (request == null) {
                        request = insertDesignRequestFixtureShell(requestId, booth, company, tester);
                }
                request.setBooth(booth);
                request.setCompany(company);
                request.setRequestedBy(tester);
                request.setAssignedDesigner(designer);
                request.setStatus(status);
                request.setMode(DesignRequestMode.INITIAL_DESIGN);
                request.setNote("Dedicated fixture for EXHDSG API tests.");
                request.setContactEmail(company.getEmail());
                request.setContactPhone(company.getPhone());
                request.setReviewCount(0);
                request.setQuotaCharged(true);
                request.setCancellationStatus(DesignRequestCancellationStatus.NONE);
                request.setCancellationReason(null);
                request.setCancellationRequestedAt(null);
                request.setCancellationResolvedAt(null);
                request.setCancellationResolutionNote(null);
                request.setAssignedAt(designer == null ? null : Instant.now().minus(2, ChronoUnit.DAYS));
                request.setApprovedAt(null);
                request.setCanceledAt(null);
                return designRequestRepository.save(request);
        }

        private DesignRequest insertDesignRequestFixtureShell(
                        UUID id,
                        Booth booth,
                        Company company,
                        User tester) {
                entityManager.flush();
                entityManager.createNativeQuery("""
                                INSERT INTO design_requests (
                                    id, booth_id, company_id, requested_by_user_id,
                                    status, mode, note, contact_email, contact_phone,
                                    review_count, quota_charged, cancellation_status,
                                    created_at, updated_at
                                ) VALUES (
                                    :id, :boothId, :companyId, :requestedById,
                                    'PENDING', 'INITIAL_DESIGN', 'EXHDSG fixture', :contactEmail, :contactPhone,
                                    0, true, 'NONE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                                )
                                """)
                                .setParameter("id", id)
                                .setParameter("boothId", booth.getId())
                                .setParameter("companyId", company.getId())
                                .setParameter("requestedById", tester.getId())
                                .setParameter("contactEmail", company.getEmail())
                                .setParameter("contactPhone", company.getPhone())
                                .executeUpdate();
                return designRequestRepository.findById(id)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Cannot create EXHDSG design request fixture " + id));
        }

        private void seedExhdsgDraft(DesignRequest request, int versionNumber, String note) {
                DesignDraft draft = designDraftRepository.save(DesignDraft.builder()
                                .designRequest(request)
                                .versionNumber(versionNumber)
                                .note(note)
                                .boothName(request.getBooth().getName() + " - draft v" + versionNumber)
                                .boothDescription("Submitted booth design fixture for Exhibitor review.")
                                .displayTemplateKey("classic")
                                .thumbnailAction(DesignDraftFileAction.KEEP)
                                .backgroundMusicAction(DesignDraftFileAction.KEEP)
                                .submittedAt(Instant.now().minus(3L - versionNumber, ChronoUnit.HOURS))
                                .build());
                DesignDraftPanorama panorama = designDraftPanoramaRepository.save(DesignDraftPanorama.builder()
                                .draft(draft)
                                .clientKey("exhdsg-" + request.getId() + "-v" + versionNumber + "-p1")
                                .name("EXHDSG panorama v" + versionNumber)
                                .imageUrl(PANORAMA_IMAGES[(versionNumber - 1) % PANORAMA_IMAGES.length])
                                .imageKey("seed/exhdsg/" + request.getId() + "/v" + versionNumber + "/panorama-1")
                                .orderIndex(0)
                                .isDefault(true)
                                .build());
                draft.getPanoramas().add(panorama);
        }

        /**
         * Ensures SHRPKG_2 can keep the packageId=1 body from the supplied test
         * sheet on both a clean and an existing local database.
         */
        private void ensureStoragePackageApiFixtures() {
                User tester = userRepository.findByEmail(EXHREG_TEST_EMAIL).orElse(null);
                Company company = tester == null
                                ? null
                                : companyRepository.findByOwnerUserId(tester.getId()).orElse(null);
                if (tester == null || company == null) {
                        log.warn("[SEED][SHRPKG] Missing Exhibitor fixture account or company.");
                        return;
                }

                tester.setRole(Role.EXHIBITOR);
                tester.setStatus(UserStatus.ACTIVE);
                userRepository.save(tester);
                company.setStatus(CompanyStatus.ACTIVE);
                companyRepository.save(company);

                StoragePackage storagePackage = storagePackageRepository
                                .findById(SHRPKG_ORDER_PACKAGE_ID)
                                .orElse(null);
                if (storagePackage == null) {
                        entityManager.flush();
                        entityManager.createNativeQuery("""
                                        INSERT INTO storage_packages (
                                            id, name, description, quota_bytes, price_vnd,
                                            is_active, created_at, updated_at
                                        ) VALUES (
                                            :id, :name, :description, :quotaBytes, :priceVnd,
                                            true, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                                        )
                                        """)
                                        .setParameter("id", SHRPKG_ORDER_PACKAGE_ID)
                                        .setParameter("name", "SHRPKG - 1 GB storage test package")
                                        .setParameter("description", "Deterministic ACTIVE package for SHRPKG_2.")
                                        .setParameter("quotaBytes", 1_073_741_824L)
                                        .setParameter("priceVnd", 50_000L)
                                        .executeUpdate();
                        storagePackage = storagePackageRepository.findById(SHRPKG_ORDER_PACKAGE_ID)
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "Cannot create SHRPKG storage package fixture"));
                }

                storagePackage.setName("SHRPKG - Gói lưu trữ 1 GB");
                storagePackage.setDescription("Gói ACTIVE cố định dùng để kiểm thử tạo đơn nâng cấp lưu trữ.");
                storagePackage.setQuotaBytes(1_073_741_824L);
                storagePackage.setPriceVnd(50_000L);
                storagePackage.setIsActive(true);
                storagePackageRepository.save(storagePackage);

                log.info("[SEED][SHRPKG] READY | exhibitorEmail={} | password={} "
                                + "| packageId={} | packageActive=true | quotaBytes={} | priceVnd={}",
                                EXHREG_TEST_EMAIL,
                                DEFAULT_PASSWORD,
                                SHRPKG_ORDER_PACKAGE_ID,
                                storagePackage.getQuotaBytes(),
                                storagePackage.getPriceVnd());
        }

        private void ensureExhibitorLeadApiFixtures() {
                User visitor = userRepository.findByEmail("visitor@vex360.local").orElse(null);
                Booth booth = boothRepository.findById(EXHBTH_BOOTH_UUID).orElse(null);
                if (visitor == null || booth == null || booth.getCompany() == null) {
                        log.warn("[SEED][EXHLED] Thiếu visitor hoặc booth EXHBTH nền; không thể bổ sung fixture API.");
                        return;
                }

                entityManager.flush();
                entityManager.createNativeQuery("""
                                DELETE FROM booth_leads
                                WHERE booth_id = :boothId
                                  AND id <> :leadId
                                """)
                                .setParameter("boothId", booth.getId())
                                .setParameter("leadId", EXHLED_LEAD_UUID)
                                .executeUpdate();

                BoothLead conflictingLead = boothLeadRepository
                                .findByBoothIdAndVisitorId(booth.getId(), visitor.getId())
                                .filter(existing -> !EXHLED_LEAD_UUID.equals(existing.getId()))
                                .orElse(null);
                if (conflictingLead != null) {
                        boothLeadRepository.delete(conflictingLead);
                        boothLeadRepository.flush();
                }

                BoothLead lead = boothLeadRepository.findById(EXHLED_LEAD_UUID).orElse(null);
                if (lead == null) {
                        lead = insertBoothLeadFixtureShell(EXHLED_LEAD_UUID, booth, visitor);
                }
                lead.setBooth(booth);
                lead.setVisitor(visitor);
                lead.setFullName("Hoàng An Vy");
                lead.setEmail("visitor@vex360.local");
                lead.setPhoneNumber("0905555555");
                lead.setCompanyName("An Vy Studio");
                lead.setMessage("Tôi quan tâm đến sản phẩm và muốn nhận báo giá chi tiết.");
                lead.setStatus(LeadStatus.CONTACTED);
                lead.setExhibitorNote("Fixture EXHLED: đã liên hệ lần đầu.");
                lead.setConsentAt(Instant.now().minus(2, ChronoUnit.DAYS));
                boothLeadRepository.save(lead);
                boothLeadRepository.flush();
                entityManager.createNativeQuery("""
                                UPDATE booth_leads
                                SET created_at = :createdAt,
                                    updated_at = CURRENT_TIMESTAMP(6)
                                WHERE id = :leadId
                                """)
                                .setParameter("createdAt", Instant.now().minus(2, ChronoUnit.DAYS))
                                .setParameter("leadId", EXHLED_LEAD_UUID)
                                .executeUpdate();

                log.info("[SEED][EXHLED] READY | exhibitorEmail={} | password={} | leadUuid={} "
                                + "| boothUuid={} | visitorEmail={} | initialStatus={}",
                                EXHREG_TEST_EMAIL,
                                DEFAULT_PASSWORD,
                                EXHLED_LEAD_UUID,
                                EXHBTH_BOOTH_UUID,
                                "visitor@vex360.local",
                                LeadStatus.CONTACTED);
        }

        private BoothLead insertBoothLeadFixtureShell(UUID id, Booth booth, User visitor) {
                entityManager.flush();
                entityManager.createNativeQuery("""
                                INSERT INTO booth_leads (
                                    id, booth_id, visitor_user_id, full_name, email,
                                    phone_number, company_name, message, status,
                                    exhibitor_note, consent_at, created_at, updated_at
                                ) VALUES (
                                    :id, :boothId, :visitorId, 'EXHLED fixture', 'visitor@vex360.local',
                                    '0905555555', 'An Vy Studio', 'EXHLED fixture', 'CONTACTED',
                                    'EXHLED fixture', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                                )
                                """)
                                .setParameter("id", id)
                                .setParameter("boothId", booth.getId())
                                .setParameter("visitorId", visitor.getId())
                                .executeUpdate();
                return boothLeadRepository.findById(id)
                                .orElseThrow(() -> new IllegalStateException("Không thể tạo booth lead fixture " + id));
        }

        private void ensureExhibitorReportApiFixtures() {
                User visitor = userRepository.findByEmail("visitor@vex360.local").orElse(null);
                Booth booth = boothRepository.findById(EXHBTH_BOOTH_UUID).orElse(null);
                Exhibition exhibition = booth == null
                                || booth.getExhibitorRegistration() == null
                                || booth.getExhibitorRegistration().getExhibitionPackage() == null
                                                ? null
                                                : booth.getExhibitorRegistration().getExhibitionPackage()
                                                                .getExhibition();
                if (visitor == null || booth == null || exhibition == null) {
                        log.warn("[SEED][EXHRPT] Thiếu visitor, booth hoặc exhibition nền; không thể bổ sung fixture API.");
                        return;
                }

                entityManager.flush();
                entityManager.createNativeQuery("DELETE FROM analytics_events WHERE booth_id = :boothId")
                                .setParameter("boothId", booth.getId())
                                .executeUpdate();

                int[] daysAgo = { 6, 3, 1 };
                int[] viewsPerDay = { 2, 3, 4 };
                int[] productClicksPerDay = { 1, 2, 2 };
                int[] hotspotClicksPerDay = { 1, 1, 2 };
                int[] chatsPerDay = { 1, 1, 2 };
                int[][] durationsPerDay = {
                                { 25, 75 },
                                { 120, 240, 360 },
                                { 45, 180, 420, 90 }
                };
                ZoneId zone = ZoneId.systemDefault();
                int eventCount = 0;

                for (int dayIndex = 0; dayIndex < daysAgo.length; dayIndex++) {
                        Instant dayStart = LocalDate.now().minusDays(daysAgo[dayIndex])
                                        .atStartOfDay(zone).toInstant();

                        for (int viewIndex = 0; viewIndex < viewsPerDay[dayIndex]; viewIndex++) {
                                Instant viewTime = dayStart.plus(9 + viewIndex, ChronoUnit.HOURS)
                                                .plus(viewIndex * 5L, ChronoUnit.MINUTES);
                                seedAnalyticsEvent(AnalyticsEventType.BOOTH_VIEW, visitor, exhibition, booth,
                                                null, null, viewTime, exhrptMetadata(null));
                                int duration = durationsPerDay[dayIndex][viewIndex];
                                seedAnalyticsEvent(AnalyticsEventType.BOOTH_LEAVE, visitor, exhibition, booth,
                                                null, duration, viewTime.plus(duration, ChronoUnit.SECONDS),
                                                exhrptMetadata(null));
                                eventCount += 2;
                        }

                        for (int clickIndex = 0; clickIndex < productClicksPerDay[dayIndex]; clickIndex++) {
                                seedAnalyticsEvent(AnalyticsEventType.PRODUCT_CLICK, visitor, exhibition, booth,
                                                null, null,
                                                dayStart.plus(14, ChronoUnit.HOURS)
                                                                .plus(clickIndex, ChronoUnit.MINUTES),
                                                exhrptMetadata("Sản phẩm kiểm thử EXHBTH"));
                                eventCount++;
                        }
                        for (int clickIndex = 0; clickIndex < hotspotClicksPerDay[dayIndex]; clickIndex++) {
                                seedAnalyticsEvent(AnalyticsEventType.HOTSPOT_CLICK, visitor, exhibition, booth,
                                                null, null,
                                                dayStart.plus(15, ChronoUnit.HOURS)
                                                                .plus(clickIndex, ChronoUnit.MINUTES),
                                                exhrptMetadata("Đi tới khu vực 2"));
                                eventCount++;
                        }
                        for (int chatIndex = 0; chatIndex < chatsPerDay[dayIndex]; chatIndex++) {
                                seedAnalyticsEvent(AnalyticsEventType.CHAT_INITIATED, visitor, exhibition, booth,
                                                null, null,
                                                dayStart.plus(16, ChronoUnit.HOURS)
                                                                .plus(chatIndex, ChronoUnit.MINUTES),
                                                exhrptMetadata(null));
                                eventCount++;
                        }
                }

                log.info("[SEED][EXHRPT] READY | exhibitorEmail={} | password={} | boothUuid={} "
                                + "| views=9 | interactions=9 | productClicks=5 | chats=4 | leads=1 | events={}",
                                EXHREG_TEST_EMAIL,
                                DEFAULT_PASSWORD,
                                EXHBTH_BOOTH_UUID,
                                eventCount);
        }

        private String exhrptMetadata(String clickableName) {
                if (clickableName == null) {
                        return "{\"fixture\":\"EXHRPT\"}";
                }
                return "{\"fixture\":\"EXHRPT\",\"name\":\"" + clickableName + "\"}";
        }

        private void resetFixtureContent(
                        Booth booth,
                        UUID sourcePanoramaId,
                        UUID targetPanoramaId,
                        UUID hotspotId,
                        String namePrefix) {
                List<Panorama> existingPanoramas = panoramaRepository
                                .findByBoothIdOrderByOrderIndexAsc(booth.getId());
                for (Panorama panorama : existingPanoramas) {
                        List<Hotspot> removableHotspots = hotspotRepository
                                        .findBySourcePanoramaIdOrderByNameAsc(panorama.getId())
                                        .stream()
                                        .filter(hotspot -> !hotspotId.equals(hotspot.getId()))
                                        .toList();
                        hotspotRepository.deleteAll(removableHotspots);
                }
                hotspotRepository.flush();

                List<UUID> extraPanoramaIds = existingPanoramas.stream()
                                .map(Panorama::getId)
                                .filter(id -> !sourcePanoramaId.equals(id) && !targetPanoramaId.equals(id))
                                .toList();
                if (!extraPanoramaIds.isEmpty()) {
                        hotspotRepository.deleteAll(hotspotRepository.findAllByTargetPanoramaIdIn(extraPanoramaIds));
                        hotspotRepository.flush();
                        panoramaRepository.deleteAll(existingPanoramas.stream()
                                        .filter(panorama -> extraPanoramaIds.contains(panorama.getId()))
                                        .toList());
                        panoramaRepository.flush();
                }

                Panorama sourcePanorama = ensureFixturePanorama(
                                sourcePanoramaId, booth, namePrefix, 0, true);
                Panorama targetPanorama = ensureFixturePanorama(
                                targetPanoramaId, booth, namePrefix + " - khu vực 2", 1, false);

                Hotspot hotspot = hotspotRepository.findById(hotspotId).orElse(null);
                if (hotspot == null) {
                        hotspot = insertHotspotFixtureShell(hotspotId, sourcePanorama, targetPanorama);
                }
                hotspot.setType(HotspotType.NAV);
                hotspot.setName("Đi tới khu vực 2");
                hotspot.setSourcePanorama(sourcePanorama);
                hotspot.setTargetPanorama(targetPanorama);
                hotspot.setProduct(null);
                hotspot.setMediaAsset(null);
                hotspot.setInfoText(null);
                hotspot.setXPosition(1.0);
                hotspot.setYPosition(0.0);
                hotspot.setZPosition(-1.0);
                hotspot.setIconStyle("arrow");
                hotspot.setScale(1.0);
                hotspot.setZIndex(1);
                hotspot.setMediaClickAction(null);
                hotspot.setInfoContentType(null);
                hotspotRepository.save(hotspot);
        }

        private Panorama ensureFixturePanorama(
                        UUID id,
                        Booth booth,
                        String name,
                        int orderIndex,
                        boolean isDefault) {
                Panorama panorama = panoramaRepository.findById(id).orElse(null);
                if (panorama == null) {
                        panorama = insertPanoramaFixtureShell(id, booth);
                }
                panorama.setBooth(booth);
                panorama.setName(name);
                panorama.setImageUrl(PANORAMA_IMAGES[orderIndex % PANORAMA_IMAGES.length]);
                panorama.setImageKey(Boolean.TRUE.equals(booth.getIsTemplate())
                                ? "seed/exhbth/template-panorama/" + id
                                : null);
                panorama.setFileSize(0L);
                panorama.setOrderIndex(orderIndex);
                panorama.setIsDefault(isDefault);
                panorama.setIsTemplateDerived(booth.getIsTemplate());
                return panoramaRepository.save(panorama);
        }

        private Booth insertBoothFixtureShell(UUID id, User createdBy) {
                entityManager.flush();
                entityManager.createNativeQuery("""
                                INSERT INTO booths (
                                    id, name, status, is_template, created_by_id,
                                    display_template_key, created_at, updated_at
                                ) VALUES (
                                    :id, 'EXHBTH fixture', 'DRAFT', false, :createdById,
                                    'classic', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                                )
                                """)
                                .setParameter("id", id)
                                .setParameter("createdById", createdBy.getId())
                                .executeUpdate();
                return boothRepository.findById(id)
                                .orElseThrow(() -> new IllegalStateException("Không thể tạo booth fixture " + id));
        }

        private Panorama insertPanoramaFixtureShell(UUID id, Booth booth) {
                entityManager.flush();
                entityManager.createNativeQuery("""
                                INSERT INTO panoramas (
                                    id, booth_id, name, image_url, order_index,
                                    is_default, is_template_derived, created_at, updated_at
                                ) VALUES (
                                    :id, :boothId, 'EXHBTH panorama', :imageUrl, 0,
                                    false, false, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                                )
                                """)
                                .setParameter("id", id)
                                .setParameter("boothId", booth.getId())
                                .setParameter("imageUrl", PANORAMA_IMAGES[0])
                                .executeUpdate();
                return panoramaRepository.findById(id)
                                .orElseThrow(() -> new IllegalStateException("Không thể tạo panorama fixture " + id));
        }

        private Hotspot insertHotspotFixtureShell(
                        UUID id,
                        Panorama sourcePanorama,
                        Panorama targetPanorama) {
                entityManager.flush();
                entityManager.createNativeQuery("""
                                INSERT INTO hotspots (
                                    id, type, name, source_panorama_id, target_panorama_id,
                                    x_position, y_position, z_position
                                ) VALUES (
                                    :id, 'NAV', 'EXHBTH hotspot', :sourcePanoramaId, :targetPanoramaId,
                                    1.0, 0.0, -1.0
                                )
                                """)
                                .setParameter("id", id)
                                .setParameter("sourcePanoramaId", sourcePanorama.getId())
                                .setParameter("targetPanoramaId", targetPanorama.getId())
                                .executeUpdate();
                return hotspotRepository.findById(id)
                                .orElseThrow(() -> new IllegalStateException("Không thể tạo hotspot fixture " + id));
        }

        private ProductCategory insertProductCategoryFixtureShell(UUID id, Company company) {
                entityManager.flush();
                entityManager.createNativeQuery("""
                                INSERT INTO product_categories (
                                    id, company_id, name, status, created_at, updated_at
                                ) VALUES (
                                    :id, :companyId, 'EXHBTH category', 'ACTIVE',
                                    CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                                )
                                """)
                                .setParameter("id", id)
                                .setParameter("companyId", company.getId())
                                .executeUpdate();
                return productCategoryRepository.findById(id)
                                .orElseThrow(() -> new IllegalStateException("Không thể tạo category fixture " + id));
        }

        private Product insertProductFixtureShell(UUID id, Company company, ProductCategory category) {
                entityManager.flush();
                entityManager.createNativeQuery("""
                                INSERT INTO products (
                                    id, company_id, category_id, name, sku, description,
                                    price, currency, thumbnail_url, thumbnail_public_id,
                                    thumbnail_file_size, status, created_at, updated_at
                                ) VALUES (
                                    :id, :companyId, :categoryId, 'EXHBTH product', 'EXHBTH-FIXTURE-001',
                                    'EXHBTH fixture', 100000, 'VND', :thumbnailUrl,
                                    'seed/exhbth/product', 1024, 'ACTIVE',
                                    CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                                )
                                """)
                                .setParameter("id", id)
                                .setParameter("companyId", company.getId())
                                .setParameter("categoryId", category.getId())
                                .setParameter("thumbnailUrl", IMG_SOFA)
                                .executeUpdate();
                return productRepository.findById(id)
                                .orElseThrow(() -> new IllegalStateException("Không thể tạo product fixture " + id));
        }

        private MediaAsset insertMediaAssetFixtureShell(UUID id, Company company) {
                entityManager.flush();
                entityManager.createNativeQuery("""
                                INSERT INTO media_assets (
                                    id, company_id, name, type, url, public_id,
                                    mime_type, file_size, created_at
                                ) VALUES (
                                    :id, :companyId, 'EXHBTH media', 'IMAGE', :url,
                                    'seed/exhbth/media-asset', 'image/jpeg', 2048,
                                    CURRENT_TIMESTAMP(6)
                                )
                                """)
                                .setParameter("id", id)
                                .setParameter("companyId", company.getId())
                                .setParameter("url", IMG_EXPO_HALL)
                                .executeUpdate();
                return mediaAssetRepository.findById(id)
                                .orElseThrow(() -> new IllegalStateException("Không thể tạo media fixture " + id));
        }

        private Exhibition saveExhibition(User organizer, String name, String category, String description,
                        LocalDate startDate, LocalDate endDate, int estimatedBooths,
                        ExhibitionStatus status, User reviewedBy, String rejectedReason) {
                return saveExhibition(null, organizer, name, category, description, startDate, endDate,
                                estimatedBooths, status, reviewedBy, rejectedReason);
        }

        private Exhibition saveExhibition(UUID uuid, User organizer, String name, String category, String description,
                        LocalDate startDate, LocalDate endDate, int estimatedBooths,
                        ExhibitionStatus status, User reviewedBy, String rejectedReason) {
                Exhibition.ExhibitionBuilder exhibitionBuilder = Exhibition.builder()
                                .organizer(organizer).name(name).category(category).description(description)
                                .startDate(startDate).endDate(endDate).estimatedBooths(estimatedBooths)
                                .status(status)
                                .reviewedBy(reviewedBy)
                                .reviewedAt(reviewedBy != null ? Instant.now().minus(2, ChronoUnit.DAYS) : null)
                                .rejectedReason(rejectedReason)
                                .rejectionCount(rejectedReason != null ? 1 : 0);
                if (uuid != null) {
                        exhibitionBuilder.uuid(uuid);
                }
                Exhibition exhibition = exhibitionRepository.save(exhibitionBuilder.build());

                ExhibitionReviewStatus reviewStatus = ExhibitionReviewStatus.PENDING;
                if (status == ExhibitionStatus.REJECTED) {
                        reviewStatus = ExhibitionReviewStatus.REJECTED;
                } else if (status != ExhibitionStatus.PENDING) {
                        reviewStatus = ExhibitionReviewStatus.APPROVED;
                }

                String snapshotJson = String.format(
                                "{\"name\":\"%s\",\"category\":\"%s\",\"description\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"estimatedBooths\":%d}",
                                name, category, description != null ? description : "", startDate, endDate,
                                estimatedBooths);

                exhibitionReviewRequestRepository.save(ExhibitionReviewRequest.builder()
                                .exhibition(exhibition)
                                .versionNumber(1)
                                .status(reviewStatus)
                                .submittedBy(organizer)
                                .submittedAt(Instant.now().minus(5, ChronoUnit.DAYS))
                                .reviewedBy(reviewedBy)
                                .reviewedAt(reviewedBy != null ? Instant.now().minus(2, ChronoUnit.DAYS) : null)
                                .rejectedReason(rejectedReason)
                                .contentSnapshotJson(snapshotJson)
                                .legacyIncomplete(true)
                                .build());

                return exhibition;
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

        private ExhibitorRegistration buildRegistration(ExhibitionPackage pkg, Company company,
                        PackageTemplate template, ExhibitorRegistrationStatus status, User reviewedBy,
                        String reason, String rejectedReason) {
                return buildRegistration(pkg, company, template, status, reviewedBy, reason, rejectedReason,
                                "Gian hàng " + company.getName(),
                                "Mô tả gian hàng " + company.getName() + " tại triển lãm.");
        }

        private ExhibitorRegistration buildRegistration(ExhibitionPackage pkg, Company company,
                        PackageTemplate template, ExhibitorRegistrationStatus status, User reviewedBy,
                        String reason, String rejectedReason, String boothName, String boothDescription) {
                return ExhibitorRegistration.builder()
                                .exhibitionPackage(pkg).company(company).status(status)
                                .reviewedBy(reviewedBy).participationReason(reason)
                                .rejectedReason(rejectedReason)
                                .boothName("Gian hàng " + company.getName())
                                .boothDescription(reason != null ? reason : "Gian hàng dữ liệu mẫu VEX360.")
                                .boothName(boothName != null ? boothName : "Gian hàng " + company.getName())
                                .boothDescription(boothDescription != null ? boothDescription
                                                : "Mô tả gian hàng " + company.getName())
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
         * Hibernate luôn set = thời điểm hiện tại, bỏ qua giá trị ta gán. Vì vậy sau
         * khi
         * persist ta chạy 1 câu UPDATE native để ghi đè về ngày mong muốn — nhờ đó dữ
         * liệu trải qua nhiều ngày cho biểu đồ.
         */
        private void seedAnalyticsEvent(AnalyticsEventType type, User user, Exhibition exhibition,
                        Booth booth, Product product, Integer durationSeconds, Instant when) {
                seedAnalyticsEvent(type, user, exhibition, booth, product, durationSeconds, when, null);
        }

        private void seedAnalyticsEvent(AnalyticsEventType type, User user, Exhibition exhibition,
                        Booth booth, Product product, Integer durationSeconds, Instant when,
                        String metadataJson) {
                AnalyticsEvent event = AnalyticsEvent.builder()
                                .user(user).exhibition(exhibition).booth(booth).product(product)
                                .eventType(type).durationSeconds(durationSeconds).metadataJson(metadataJson).build();
                entityManager.persist(event);
                entityManager.flush(); // đảm bảo có id + đã INSERT
                entityManager.createNativeQuery("UPDATE analytics_events SET event_time = :t WHERE id = :id")
                                .setParameter("t", when)
                                .setParameter("id", event.getId())
                                .executeUpdate();
        }

        /**
         * Seed nhiều lượt click (PRODUCT_CLICK/HOTSPOT_CLICK) cùng tên clickable, rải
         * qua vài ngày
         * gần đây, để bảng xếp hạng "hotspot/sản phẩm được click nhiều nhất" ở trang
         * thống kê gian
         * hàng (exhibitor) có dữ liệu thật thay vì trống.
         *
         * @return số event đã tạo (bằng {@code count})
         */
        private int seedBoothClicks(User user, Exhibition exhibition, Booth booth, AnalyticsEventType type,
                        String clickableName, Product product, int count) {
                String metadata = clickMetadata(clickableName);
                ZoneId zone = ZoneId.systemDefault();
                for (int c = 0; c < count; c++) {
                        Instant when = Instant.now().minus(1 + (c % 6), ChronoUnit.DAYS)
                                        .atZone(zone)
                                        .withHour(9 + (c % 10)).withMinute(c % 60).withSecond(0).withNano(0)
                                        .toInstant();
                        seedAnalyticsEvent(type, user, exhibition, booth, product, null, when, metadata);
                }
                return count;
        }

        /**
         * Metadata JSON tối thiểu cho 1 lượt click — khớp format FE gửi lên
         * ({@code {"name": "..."}}).
         */
        private String clickMetadata(String name) {
                return "{\"name\":\"" + name + "\"}";
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
                        Map<String, Object> result = cloudinary.uploader().upload(remoteUrl, params);

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
