package com.example.vex360.shared.config;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
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
import com.example.vex360.features.designrequest.enums.DesignRequestCancellationStatus;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
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
 * {@code APP_SEED_ENABLED=true}) để không bao giờ vô tình chạy trên
 * production.
 *
 * <p>
 * Ảnh được upload thật lên Cloudinary bằng cách truyền URL công khai cho SDK
 * —
 * Cloudinary tự tải về và host lại, trả về {@code secure_url} +
 * {@code public_id} thật.
 * Nếu upload lỗi (thiếu credential, mất mạng), seeder tự fallback sang URL
 * gốc
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
        private static final String OTHER_ORGANIZER_EMAIL = "organizer2@vex360.local";
        private static final UUID OTHER_ORGANIZER_EXHIBITION_UUID = UUID
                        .fromString("4fa85f64-5717-4562-b3fc-2c963f66afa6");
        private static final UUID OTHER_ORGANIZER_REGISTRATION_1_UUID = UUID
                        .fromString("4fa85f64-5717-4562-b3fc-2c963f66afc1");
        private static final UUID OTHER_ORGANIZER_REGISTRATION_2_UUID = UUID
                        .fromString("4fa85f64-5717-4562-b3fc-2c963f66afc2");
        private static final UUID OTHER_ORGANIZER_BOOTH_1_UUID = UUID
                        .fromString("4fa85f64-5717-4562-b3fc-2c963f66afd1");
        private static final UUID OTHER_ORGANIZER_BOOTH_2_UUID = UUID
                        .fromString("4fa85f64-5717-4562-b3fc-2c963f66afd2");
        private static final String LEGACY_GUEST_LEAD_EMAIL = "khachle@example.com";
        private static final String DEFAULT_PASSWORD = "123456";
        /**
         * Ảnh panorama 360 <b>equirectangular thật</b> (4096x2048), bối cảnh trong
         * nhà
         * hợp với nền tảng triển lãm ảo: sảnh đón khách triển lãm, khu trưng bày bảo
         * tàng,
         * hội trường, sảnh lớn.
         *
         * <p>
         * Nguồn: Wikimedia Commons (giấy phép tự do). Dùng bản thu nhỏ 4096px thay vì
         * bản gốc (~12000px) để vừa giới hạn upload của Cloudinary và đúng độ phân
         * giải
         * cần thiết cho viewer web.
         *
         * <p>
         * Bắt buộc phải là ảnh equirectangular thì khi bọc lên hình cầu trong viewer
         * mới không bị méo và không lộ đường nối dọc.
         */
        private static final String WIKI_THUMB = "https://upload.wikimedia.org/wikipedia/commons/thumb/";

        private static final String WIKI_FILE = "https://upload.wikimedia.org/wikipedia/commons/";

        /**
         * Ảnh minh hoạ <b>đúng chủ đề VEX360</b> (nội thất, thiết bị công nghệ, hội
         * chợ
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
                        +
                        "2/27/MESAP_2012._-_dvorana_%28ulaz%29.JPG/1280px-MESAP_2012._-_dvorana_%28ulaz%29.JPG";
        /** Dãy gian hàng hội chợ - dùng làm ảnh hướng dẫn bố trí. */
        private static final String IMG_EXPO_BOOTHS = WIKI_THUMB
                        + "4/49/MESAP_2011._-_%C5%A1tandovi_%28sjeverni_niz%29.jpg/"
                        + "1280px-MESAP_2011._-_%C5%A1tandovi_%28sjeverni_niz%29.jpg";
        private static final String IMG_FLOOR_PLAN = WIKI_THUMB
                        + "9/97/Bungalow_drawing_--_Floor_Plan_MET_DP804276.jpg/"
                        + "1280px-Bungalow_drawing_--_Floor_Plan_MET_DP804276.jpg";
        /** Nội thất cửa hàng - dùng làm banner/thumbnail gian hàng nội thất. */
        private static final String IMG_SHOWROOM = WIKI_FILE +
                        "0/09/Interior_of_Severin_%26_Andreas_Jensen.jpg";

        /**
         * Ảnh key visual cho 18 exhibition fixture hàng loạt (bulk demo), mỗi ảnh
         * đúng chủ đề của triển lãm tương ứng. Nguồn: Wikimedia Commons, giấy phép
         * tự do.
         */
        private static final String IMG_SMART_HOME = WIKI_THUMB
                        +
                        "f/f4/Vacuum_cleaner_in_front_of_charger.jpg/1280px-Vacuum_cleaner_in_front_of_charger.jpg";
        private static final String IMG_FASHION = WIKI_THUMB
                        +
                        "9/9f/Model_walking_for_Lavinia_Ilies_show.jpg/1280px-Model_walking_for_Lavinia_Ilies_show.jpg";
        private static final String IMG_FOOD_BEVERAGE = WIKI_THUMB
                        + "0/0c/Buffets_hors_d%27%C5%93uvre%2C_restaurant_Les_Grands_Buffets.jpg/"
                        + "1280px-Buffets_hors_d%27%C5%93uvre%2C_restaurant_Les_Grands_Buffets.jpg";
        private static final String IMG_JEWELRY = WIKI_THUMB
                        +
                        "c/ca/Anillos_I_Santa_Barbara_Joyeria_.jpg/1280px-Anillos_I_Santa_Barbara_Joyeria_.jpg";
        private static final String IMG_OFFICE_FURNITURE = WIKI_THUMB
                        +
                        "d/d4/Offices_in_Verisure_headquarters_at_Versoix_in_Switzerland_-_2020-06-12.jpg/"
                        +
                        "1280px-Offices_in_Verisure_headquarters_at_Versoix_in_Switzerland_-_2020-06-12.jpg";
        private static final String IMG_MEDICAL = WIKI_THUMB
                        + "3/33/PET_CT_scanner.JPG/1280px-PET_CT_scanner.JPG";
        private static final String IMG_EDUCATION = WIKI_THUMB
                        +
                        "b/b1/Students_are_learning_in_the_classroom.jpg/1280px-Students_are_learning_in_the_classroom.jpg";
        private static final String IMG_TRAVEL = WIKI_THUMB
                        + "6/60/Halong_Bay_panorama.jpg/1280px-Halong_Bay_panorama.jpg";
        private static final String IMG_MACHINERY = WIKI_THUMB
                        + "2/22/Factory_Automation_Robotics_Palettizing_Bread.jpg/"
                        + "1280px-Factory_Automation_Robotics_Palettizing_Bread.jpg";
        private static final String IMG_FISHERY = WIKI_THUMB
                        +
                        "a/a9/Vietnamese_Fish_Market_%28Unsplash%29.jpg/1280px-Vietnamese_Fish_Market_%28Unsplash%29.jpg";
        private static final String IMG_AGRICULTURE = WIKI_THUMB
                        +
                        "b/b9/Tomatoes_in_Greenhouse_%2828244946782%29.jpg/1280px-Tomatoes_in_Greenhouse_%2828244946782%29.jpg";
        private static final String IMG_TOYS = WIKI_THUMB
                        + "8/82/Nijntje_Amsterdam%2C_Toyshop_Beethovenstraat.jpg/"
                        + "1280px-Nijntje_Amsterdam%2C_Toyshop_Beethovenstraat.jpg";
        private static final String IMG_BOOKS = WIKI_THUMB
                        +
                        "6/6f/Charleroi_-_librairie_Fafouille_-_01.jpg/1280px-Charleroi_-_librairie_Fafouille_-_01.jpg";
        private static final String IMG_SPORTS_OUTDOOR = WIKI_THUMB
                        + "5/58/1998-07-21_Campingplatz_im_Fundy-National-Park_%282%29.jpg/"
                        + "1280px-1998-07-21_Campingplatz_im_Fundy-National-Park_%282%29.jpg";
        private static final String IMG_ELECTRONICS = WIKI_THUMB
                        + "a/ac/Samsung_Galaxy_Store_1.jpg/1280px-Samsung_Galaxy_Store_1.jpg";
        private static final String IMG_COSMETICS = WIKI_THUMB
                        + "0/03/Korean_cosmetic_products.jpg/1280px-Korean_cosmetic_products.jpg";
        private static final String IMG_PETS = WIKI_THUMB
                        +
                        "e/e0/Petteri_Sulonen_-_Cats_and_Dogs_%28by%29.jpg/1280px-Petteri_Sulonen_-_Cats_and_Dogs_%28by%29.jpg";
        private static final String IMG_WINE = WIKI_THUMB
                        + "7/7e/Wine_Barrels.jpg/1280px-Wine_Barrels.jpg";
        private static final String IMG_GREEN_TECH = WIKI_THUMB
                        + "a/af/Rooftop_Solar_Panels.jpg/1280px-Rooftop_Solar_Panels.jpg";
        private static final String IMG_GARDEN_FURNITURE = WIKI_THUMB
                        +
                        "7/73/Garden_patio_and_furniture_Capel_Manor_College_Gardens_Enfield_London_England_01.jpg/"
                        +
                        "1280px-Garden_patio_and_furniture_Capel_Manor_College_Gardens_Enfield_London_England_01.jpg";
        private static final String IMG_CONSTRUCTION_MATERIALS = WIKI_THUMB
                        +
                        "5/5e/Brick_and_pallet_stack_at_Hatfield_Broad_Oak%2C_Essex%2C_England.jpg/"
                        +
                        "1280px-Brick_and_pallet_stack_at_Hatfield_Broad_Oak%2C_Essex%2C_England.jpg";
        private static final String IMG_TRAVEL_BOOTH_THUMB = WIKI_THUMB
                        +
                        "a/af/Departure_Lounge_Pune_Airport_India.jpg/1280px-Departure_Lounge_Pune_Airport_India.jpg";
        private static final String[] TRAVEL_BOOTH_THUMBS = {
                        "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1280&q=85",
                        "https://images.unsplash.com/photo-1469474968028-56623f02e42e?auto=format&fit=crop&w=1280&q=85",
                        "https://images.unsplash.com/photo-1499856871958-5b9627545d1a?auto=format&fit=crop&w=1280&q=85",
                        "https://images.unsplash.com/photo-1476514525535-07fb3b4ae5f1?auto=format&fit=crop&w=1280&q=85",
                        "https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=1280&q=85",
                        "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1280&q=85",
                        "https://images.unsplash.com/photo-1530789253388-582c481c54b0?auto=format&fit=crop&w=1280&q=85",
                        "https://images.unsplash.com/photo-1501785888041-af3ef285b470?auto=format&fit=crop&w=1280&q=85",
                        "https://images.unsplash.com/photo-1488646953014-85cb44e25828?auto=format&fit=crop&w=1280&q=85",
                        "https://images.unsplash.com/photo-1548013146-72479768bada?auto=format&fit=crop&w=1280&q=85",
        };
        private static final String[] TRAVEL_BOOTH_PANORAMAS = {
                        "https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=4096&h=2048&q=90",
                        "https://images.unsplash.com/photo-1439853949127-fa647821eba0?auto=format&fit=crop&w=4096&h=2048&q=90",
                        "https://images.unsplash.com/photo-1510414842594-a61c69b5ae57?auto=format&fit=crop&w=4096&h=2048&q=90",
                        "https://images.unsplash.com/photo-1516483638261-f4dbaf036963?auto=format&fit=crop&w=4096&h=2048&q=90",
                        "https://images.unsplash.com/photo-1533669955142-6a73332af4db?auto=format&fit=crop&w=4096&h=2048&q=90",
                        "https://images.unsplash.com/photo-1504214208698-ea1916a2195a?auto=format&fit=crop&w=4096&h=2048&q=90",
                        "https://images.unsplash.com/photo-1500534623283-312aade485b7?auto=format&fit=crop&w=4096&h=2048&q=90",
                        "classpath:seed/panorama/travel-published-01.png",
                        "classpath:seed/panorama/travel-published-02.png",
                        "classpath:seed/panorama/travel-published-03.png",
        };
        private static final String IMG_WIND_TURBINE = WIKI_THUMB
                        + "4/4a/Wind_turbines.jpg/1280px-Wind_turbines.jpg";
        private static final String IMG_EV_CHARGING = WIKI_THUMB
                        +
                        "2/24/Electric_car_charging_station.jpg/1280px-Electric_car_charging_station.jpg";
        private static final String IMG_SOLAR_BUILDING = WIKI_THUMB
                        +
                        "1/17/Wall-mounted_solar_panels_on_building.jpg/1280px-Wall-mounted_solar_panels_on_building.jpg";

        /**
         * Bộ ảnh thumbnail + panorama KHÁC NHAU cho từng gian hàng theo chủ đề
         * (xoay vòng qua nhiều ảnh thật thay vì dùng chung 1 ảnh). Panorama ưu
         * tiên ảnh 360 equirectangular thật đúng chủ đề; nếu Wikimedia Commons
         * không có ảnh equirectangular đúng chủ đề thì mới dùng ảnh rộng gần
         * đúng nội dung nhất (chấp nhận méo nhẹ khi hiển thị 360, vì seeder ghi
         * thẳng DB, không qua validate tỉ lệ ảnh của endpoint upload).
         */
        private static final String[] GREEN_TECH_BOOTH_THUMBS = {
                        IMG_GREEN_TECH,
                        IMG_WIND_TURBINE,
                        IMG_EV_CHARGING,
                        IMG_SOLAR_BUILDING,
                        WIKI_THUMB +
                                        "d/dd/Rooftop_Solar_at_Barnstable_Airport_%2811353968514%29.jpg/"
                                        + "1280px-Rooftop_Solar_at_Barnstable_Airport_%2811353968514%29.jpg",
                        WIKI_THUMB +
                                        "6/6d/Altach-Veranstaltungszentrum_KOM-photovoltaic_system-01ASD.jpg/"
                                        + "1280px-Altach-Veranstaltungszentrum_KOM-photovoltaic_system-01ASD.jpg",
                        WIKI_THUMB + "4/46/Landscape%2C_Fraisse-sur-Agout.jpg/"
                                        + "1280px-Landscape%2C_Fraisse-sur-Agout.jpg",
                        WIKI_THUMB +
                                        "4/43/Wind_turbines_%28Adair_Wind_Farm%2C_Iowa%2C_USA%29_1_%2848915520428%29.jpg/"
                                        +
                                        "1280px-Wind_turbines_%28Adair_Wind_Farm%2C_Iowa%2C_USA%29_1_%2848915520428%29.jpg",
                        WIKI_THUMB +
                                        "9/9e/Wind_farm_landscape_on_Windrow_Hill_-_geograph.org.uk_-_6926461.jpg/"
                                        +
                                        "1280px-Wind_farm_landscape_on_Windrow_Hill_-_geograph.org.uk_-_6926461.jpg",
                        WIKI_THUMB + "5/54/Car2Go_Charging_Station_Stuttgart_2013_01.jpg/"
                                        + "1280px-Car2Go_Charging_Station_Stuttgart_2013_01.jpg",
                        WIKI_THUMB +
                                        "b/b5/Electric_vehicle_charging_stations_in_Stuttgart_1X7A6287.jpg/"
                                        + "1280px-Electric_vehicle_charging_stations_in_Stuttgart_1X7A6287.jpg",
                        WIKI_THUMB +
                                        "5/59/Aire_de_Saverne_Eckartswiller%2C_station_de_recharge_%C3%A9lectrique.jpg/"
                                        +
                                        "1280px-Aire_de_Saverne_Eckartswiller%2C_station_de_recharge_%C3%A9lectrique.jpg",
                        WIKI_THUMB +
                                        "9/91/Charge_Point_charging_station_handles%2C_Windsor%2C_Ontario%2C_2025-07-30.jpg/"
                                        +
                                        "1280px-Charge_Point_charging_station_handles%2C_Windsor%2C_Ontario%2C_2025-07-30.jpg",
                        WIKI_THUMB + "5/5d/Hanging_gardens_of_One_Central_Park%2C_Sydney.jpg/"
                                        + "1280px-Hanging_gardens_of_One_Central_Park%2C_Sydney.jpg",
                        WIKI_THUMB + "2/25/Sustainable_architecture_in_city_Bangladesh.jpg/"
                                        + "1280px-Sustainable_architecture_in_city_Bangladesh.jpg",
                        WIKI_THUMB + "3/3c/Bratsk_Hydroelectric_Power_Station_in_october.jpg/"
                                        + "1280px-Bratsk_Hydroelectric_Power_Station_in_october.jpg",
                        WIKI_THUMB + "6/6e/Irganai_Dam_and_Power_Station.jpg/"
                                        + "1280px-Irganai_Dam_and_Power_Station.jpg",
                        WIKI_THUMB + "8/80/Materials_recovery_facility.jpg/"
                                        + "1280px-Materials_recovery_facility.jpg",
                        WIKI_THUMB + "e/e0/Materials_Recovery_Facility_April_2015_13.jpg/"
                                        + "1280px-Materials_Recovery_Facility_April_2015_13.jpg",
                        WIKI_THUMB + "6/62/72%2C000_panel_solar_field_at_Nellis_AFB.jpg/"
                                        + "1280px-72%2C000_panel_solar_field_at_Nellis_AFB.jpg",
                        WIKI_THUMB + "f/f7/Solar_Sharing_Power_Plant_in_Kamisu%2C_Ibaraki_01.jpg/"
                                        + "1280px-Solar_Sharing_Power_Plant_in_Kamisu%2C_Ibaraki_01.jpg",
                        WIKI_THUMB + "e/ec/Solar_Sharing_Power_Plant_in_Inashiki%2C_Ibaraki_07.jpg/"
                                        + "1280px-Solar_Sharing_Power_Plant_in_Inashiki%2C_Ibaraki_07.jpg",
                        WIKI_THUMB + "8/8b/Nelson_River_Bipoles_1_and_2_Terminus_at_Rosser.jpg/"
                                        + "1280px-Nelson_River_Bipoles_1_and_2_Terminus_at_Rosser.jpg",
                        WIKI_THUMB +
                                        "a/a8/Biogas_Photovoltaik_Wind.jpg/1280px-Biogas_Photovoltaik_Wind.jpg",
        };
        /**
         * Panorama equirectangular thật (2:1), bên trong/quanh tuabin gió và trạm
         * năng lượng mặt trời.
         */
        private static final String[] GREEN_TECH_BOOTH_PANORAMAS = {
                        WIKI_THUMB +
                                        "7/75/%D0%9A%D0%BE%D0%BD%D0%B0%D0%B5%D0%B2%2C_%D1%81%D1%84%D0%B5%D1%80%D0%BE%D0%BF%D0%B0%D0%BD%D0%BE%D1%80%D0%B0%D0%BC%D0%B0_%D1%83_%D0%A1%D0%AD%D0%A1.jpg/"
                                        +
                                        "3840px-%D0%9A%D0%BE%D0%BD%D0%B0%D0%B5%D0%B2%2C_%D1%81%D1%84%D0%B5%D1%80%D0%BE%D0%BF%D0%B0%D0%BD%D0%BE%D1%80%D0%B0%D0%BC%D0%B0_%D1%83_%D0%A1%D0%AD%D0%A1.jpg",
                        WIKI_THUMB +
                                        "7/78/%D0%A1%D0%AD%D0%A1_%D0%9D%D1%83%D1%80%D0%B3%D0%B8%D1%81%D0%B0%2C_%D1%81%D1%84%D0%B5%D1%80%D0%BE%D0%BF%D0%B0%D0%BD%D0%BE%D1%80%D0%B0%D0%BC%D0%B0.jpg/"
                                        +
                                        "3840px-%D0%A1%D0%AD%D0%A1_%D0%9D%D1%83%D1%80%D0%B3%D0%B8%D1%81%D0%B0%2C_%D1%81%D1%84%D0%B5%D1%80%D0%BE%D0%BF%D0%B0%D0%BD%D0%BE%D1%80%D0%B0%D0%BC%D0%B0.jpg",
                        WIKI_THUMB +
                                        "3/3e/The_0_floor_of_the_wind_turbine.jpg/3840px-The_0_floor_of_the_wind_turbine.jpg",
                        WIKI_THUMB +
                                        "8/81/The_1st_floor_of_the_wind_turbine.jpg/3840px-The_1st_floor_of_the_wind_turbine.jpg",
                        WIKI_THUMB +
                                        "1/1b/Wind_turbine_in_full_01.jpg/3840px-Wind_turbine_in_full_01.jpg",
                        WIKI_THUMB +
                                        "1/1a/The_2nd_floor_of_the_wind_turbine.jpg/3840px-The_2nd_floor_of_the_wind_turbine.jpg",
                        WIKI_THUMB + "0/0e/Inside_the_nacelle.jpg/3840px-Inside_the_nacelle.jpg",
                        WIKI_THUMB +
                                        "a/aa/Ladder_in_wind_turbine.jpg/3840px-Ladder_in_wind_turbine.jpg",
        };
        private static final String[] FURNITURE_BOOTH_THUMBS = {
                        IMG_SOFA,
                        IMG_TABLE,
                        IMG_CABINET,
                        IMG_SHOWROOM,
                        WIKI_THUMB +
                                        "9/9e/Living_room_Germany_2006.jpg/1280px-Living_room_Germany_2006.jpg",
                        WIKI_THUMB +
                                        "d/dd/The_guest-of-honor_bedroom_of_the_Schloss_Drachenburg_%282022%29.jpg/"
                                        +
                                        "1280px-The_guest-of-honor_bedroom_of_the_Schloss_Drachenburg_%282022%29.jpg",
                        WIKI_THUMB +
                                        "2/2f/IKEA_store%2C_IKEA_kitchen%2C_Interior_design%2C_Rostov-on-Don%2C_Russia.jpg/"
                                        +
                                        "1280px-IKEA_store%2C_IKEA_kitchen%2C_Interior_design%2C_Rostov-on-Don%2C_Russia.jpg",
                        WIKI_THUMB + "b/b8/IKEA_furnitures_in_Forum_Istanbul_store.jpg/"
                                        + "1280px-IKEA_furnitures_in_Forum_Istanbul_store.jpg",
                        WIKI_THUMB + "2/26/PAX_wardrobe_at_IKEA_Xihongmen_%2820150423113022%29.jpg/"
                                        + "1280px-PAX_wardrobe_at_IKEA_Xihongmen_%2820150423113022%29.jpg",
                        WIKI_THUMB +
                                        "6/61/IKEA_garden_dining_set.jpg/1280px-IKEA_garden_dining_set.jpg",
                        WIKI_THUMB + "5/5d/Yellow_upholstered_armchair_in_IKEA_Torp_Uddevalla.jpg/"
                                        + "1280px-Yellow_upholstered_armchair_in_IKEA_Torp_Uddevalla.jpg",
                        WIKI_THUMB +
                                        "2/2b/Interior%2C_Duncan_House%2C_Polymath_Park%2C_Acme%2C_PA.jpg/"
                                        + "1280px-Interior%2C_Duncan_House%2C_Polymath_Park%2C_Acme%2C_PA.jpg",
                        WIKI_THUMB + "e/e6/Amazon_Echo_Plus_02.jpg/1280px-Amazon_Echo_Plus_02.jpg",
                        WIKI_THUMB +
                                        "3/32/Amazon_Echo_Dot_Not_Listening_Mode_%28Microphone_Disabled%29_%2845718825084%29.jpg/"
                                        +
                                        "1280px-Amazon_Echo_Dot_Not_Listening_Mode_%28Microphone_Disabled%29_%2845718825084%29.jpg",
                        WIKI_THUMB + "8/8b/I3_Engineering_Atom_Hydrogen_controller.jpg/"
                                        + "1280px-I3_Engineering_Atom_Hydrogen_controller.jpg",
                        WIKI_THUMB +
                                        "c/c4/Period_room_during_mourning%2C_early_1800s_-_Concord_Museum_-_Concord%2C_MA_-_DSC05789.JPG/"
                                        +
                                        "1280px-Period_room_during_mourning%2C_early_1800s_-_Concord_Museum_-_Concord%2C_MA_-_DSC05789.JPG",
                        WIKI_THUMB + "9/9d/Tapestry_Room_from_Croome_Court_MET_DP341253.jpg/"
                                        + "1280px-Tapestry_Room_from_Croome_Court_MET_DP341253.jpg",
                        WIKI_THUMB + "d/d9/Powers_Warehouse_Factory_west_-_Portland_Oregon.jpg/"
                                        + "1280px-Powers_Warehouse_Factory_west_-_Portland_Oregon.jpg",
                        WIKI_THUMB + "8/87/Dining_room_from_Lansdowne_House_MET_DT238002.jpg/"
                                        + "1280px-Dining_room_from_Lansdowne_House_MET_DT238002.jpg",
        };
        /** Panorama equirectangular thật (2:1), phòng/gian trưng bày nội thất. */
        private static final String[] FURNITURE_BOOTH_PANORAMAS = {
                        WIKI_THUMB + "8/8a/Tallinna_Ehituskool_007-mooblirestauraatorid_pano.jpg/"
                                        + "3840px-Tallinna_Ehituskool_007-mooblirestauraatorid_pano.jpg",
                        WIKI_THUMB + "c/c8/Kehtna_Kutsehariduskeskus_010-opilaskodu-tuba_pano.jpg/"
                                        + "3840px-Kehtna_Kutsehariduskeskus_010-opilaskodu-tuba_pano.jpg",
                        WIKI_THUMB + "3/38/Tartu_loodusmaja_%C3%B5petajate_tuba.jpg/"
                                        + "3840px-Tartu_loodusmaja_%C3%B5petajate_tuba.jpg",
                        WIKI_THUMB +
                                        "9/95/Eesti_%C3%9Cli%C3%B5pilaste_Seltsi_maja%2C_soome_tuba.jpg/"
                                        + "3840px-Eesti_%C3%9Cli%C3%B5pilaste_Seltsi_maja%2C_soome_tuba.jpg",
                        WIKI_THUMB +
                                        "b/b4/Eesti_%C3%9Cli%C3%B5pilaste_Seltsi_maja%2C_daamide_tuba.jpg/"
                                        + "3840px-Eesti_%C3%9Cli%C3%B5pilaste_Seltsi_maja%2C_daamide_tuba.jpg",
                        WIKI_THUMB + "3/35/Stenbocki_maja_360_--_roheline_tuba.jpg/"
                                        + "3840px-Stenbocki_maja_360_--_roheline_tuba.jpg",
                        WIKI_THUMB + "6/60/Mustpeade_maja_360-vaade_--_vennaste-tuba.jpg/"
                                        + "3840px-Mustpeade_maja_360-vaade_--_vennaste-tuba.jpg",
                        WIKI_THUMB +
                                        "b/be/Biblioteca_P%C3%BAblica_de_%C3%89vora_-_Sala_de_exposi%C3%A7%C3%B5es_%28360_panorama%29.jpg/"
                                        +
                                        "3840px-Biblioteca_P%C3%BAblica_de_%C3%89vora_-_Sala_de_exposi%C3%A7%C3%B5es_%28360_panorama%29.jpg",
        };

        /**
         * Tên nhà tài trợ hư cấu (không trùng thương hiệu thật) cho fixture triển
         * lãm.
         */
        private static final String[] SPONSOR_NAMES = {
                        "Ngân hàng TMCP Kim Cương Việt",
                        "Tập đoàn Viễn thông Sao Bắc",
                        "Công ty CP Bảo hiểm An Tâm",
                        "Tập đoàn Bất động sản Phú Gia",
                        "Ngân hàng TMCP Thịnh Vượng",
                        "Công ty CP Hàng không Việt Bay",
                        "Tập đoàn Năng lượng Sông Hồng",
                        "Công ty CP Thương mại Đông Nam",
                        "Ngân hàng TMCP Đại Tín",
                        "Tập đoàn Công nghệ Việt Số",
                        "Công ty CP Truyền thông Ngôi Sao",
                        "Tập đoàn Xây dựng Hoàng Long",
        };

        /** Video đúng chủ đề (Wikimedia Commons, giấy phép tự do). */
        private static final String VIDEO_EXPO = WIKI_FILE +
                        "b/b5/Craft_x_Tech_exhibition_at_V%26A_2024.webm";
        private static final String VIDEO_FURNITURE = WIKI_FILE +
                        "7/72/Furniture_Making.webm";
        private static final String VIDEO_SENSOR = WIKI_FILE +
                        "b/b1/Gas_sensor_with_Arduino.webm";

        /** Màu nền logo (clay terracotta của VEX360). */
        private static final String LOGO_BG = "A8572F";

private static final String[] PANORAMA_IMAGES = {
// Sảnh đón khách của một triển lãm (BITM, Kolkata)
WIKI_THUMB +
"0/01/Exhibition_Light_Matters_-_Reception_Area_-_360x180_Degree_Equirectangular_View_-_BITM_"
+
"-_Kolkata_2016-01-02_8729-8739_Compress.JPG/3840px-Exhibition_Light_Matters_-_Reception_Area_"
+
"-_360x180_Degree_Equirectangular_View_-_BITM_-_Kolkata_2016-01-02_8729-8739_Compress.JPG",
// Khu trưng bày trong bảo tàng
WIKI_THUMB +
"0/08/Cmglee_Wikimania2016_Esino_Lario_museum_interior_photosphere.jpg/"
+ "3840px-Cmglee_Wikimania2016_Esino_Lario_museum_interior_photosphere.jpg",
// Hội trường lớn
WIKI_THUMB +
"4/43/Cmglee_Wikimania2016_Esino_Lario_hall_interior_photosphere.jpg/"
+ "3840px-Cmglee_Wikimania2016_Esino_Lario_hall_interior_photosphere.jpg",
// Sảnh rộng (dự phòng khi thêm panorama mới)
WIKI_THUMB + "a/a3/Pomona_College_gymnasium_lobby_photosphere.jpg/"
+ "3840px-Pomona_College_gymnasium_lobby_photosphere.jpg",
// Đảo Fraueninsel, hồ Chiemsee - điểm đến du lịch, dùng cho gian hàng Du
// lịch
WIKI_THUMB +
"e/ee/Fraueninsel_Chiemsee_Panorama.jpg/3840px-Fraueninsel_Chiemsee_Panorama.jpg",
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

        /** Cache upload theo URL nguồn, tránh upload trùng trong 1 lần chạy seed. */
        private final Map<String, Uploaded> uploadCache = new HashMap<>();

        /** Kết quả upload lên Cloudinary. */
        private record Uploaded(String url, String publicId, long bytes) {
        }

@Override
@Transactional
public void run(ApplicationArguments args) {
long removedGuestLeads = boothLeadRepository
.deleteByEmailIgnoreCaseAndVisitorIsNull(LEGACY_GUEST_LEAD_EMAIL);
if (removedGuestLeads > 0) {
log.info("[SEED] Đã xóa {} booth lead khách vãng lai cũ.",
removedGuestLeads);
}

if (userRepository.existsByEmail(MARKER_EMAIL)) {
ensureOtherOrganizerActiveExhibitionFixtures();
ensureBulkDemoOrganizersFixtures();
ensurePublishedBoothsHaveDisplayableProductHotspot();
log.info("[SEED] Dữ liệu demo đã tồn tại; fixture đã được kiểm tra/cập nhật.");
User existingOrganizer =
userRepository.findByEmail(MARKER_EMAIL).orElseThrow();
User existingAdmin =
userRepository.findByEmail("admin@vex360.local").orElseThrow();
Company existingOrganizerCompany = companyRepository
.findByOwnerUserId(existingOrganizer.getId())
.orElseThrow();
seedOrganizerFinance(existingOrganizer, existingAdmin,
existingOrganizerCompany);
log.info("[SEED] Core test data already exists; organizer finance data is ready.");
return;
}

log.info("[SEED] Bắt đầu seed dữ liệu test (ảnh sẽ được upload lên Cloudinary)...");

// ---------- 1. USERS ----------
User admin = userRepository.findByEmail("admin@vex360.local").orElseGet(
() -> createUser("admin@vex360.local", "Quản trị hệ thống", Role.ADMIN,
"0900000000",
11));
User organizer = createUser(MARKER_EMAIL, "Nguyễn Văn An", Role.ORGANIZER,
"0901111111", 12);
User exhibitor1 = createUser("exhibitor@vex360.local", "Trần Quang Huy",
Role.EXHIBITOR, "0902222222",
13);
User exhibitor2 = createUser("exhibitor2@vex360.local", "Lê Thái Dương",
Role.EXHIBITOR, "0903333333",
14);
User designer = createUser("designer@vex360.local", "Phạm Thiên An",
Role.DESIGNER, "0904444444", 15);
User visitor = createUser("visitor@vex360.local", "Hoàng An Vy",
Role.VISITOR, "0905555555",
16);

// Phủ UserStatus (INACTIVE/PENDING/BLOCKED) và AuthProvider (GOOGLE)
User visitorInactive = createUser("visitor.inactive@vex360.local", "Ngô Thị Bảo Trân", Role.VISITOR,
"0906111111", 21,
UserStatus.INACTIVE, AuthProvider.LOCAL);
createUser("visitor.pending@vex360.local", "Đinh Văn Khôi", Role.VISITOR,
"0906222222", 22,
UserStatus.PENDING, AuthProvider.LOCAL);
createUser("visitor.blocked@vex360.local", "Bùi Thanh Sơn", Role.VISITOR,
"0906333333", 23,
UserStatus.BLOCKED, AuthProvider.LOCAL);
User visitorGoogle = createUser("visitor.google@vex360.local", "Lý Gia Bảo",
Role.VISITOR,
"0906444444", 24,
UserStatus.ACTIVE, AuthProvider.GOOGLE);
// 2 chủ sở hữu cho company INCOMPLETE_PROFILE và ARCHIVED
User exhibitor3 = createUser("exhibitor3@vex360.local", "Vũ Ngọc Anh",
Role.EXHIBITOR,
"0906555555", 25);
User exhibitor4 = createUser("exhibitor4@vex360.local", "Tạ Kim Thư",
Role.EXHIBITOR,
"0906666666", 26);
log.info("[SEED] Đã tạo 12 user - phủ đủ Role, UserStatus, AuthProvider (mật khẩu chung: {})",
DEFAULT_PASSWORD);

// ---------- 2. COMPANIES (phủ đủ 3 CompanyStatus) ----------
Company orgCompany = createCompany(organizer, "Công ty Tổ chức Sự kiện ABC",
"Sự kiện & Triển lãm", "Đơn vị tổ chức triển lãm ảo hàng đầu.", "logo-abc");
Company company1 = createCompany(exhibitor1, "Công ty Nội thất Mộc Việt",
"Nội thất", "Chuyên nội thất gỗ tự nhiên cao cấp.", "logo-mocviet");
Company company2 = createCompany(exhibitor2, "Công ty Công nghệ TechVina",
"Công nghệ", "Giải pháp thiết bị thông minh cho doanh nghiệp.",
"logo-techvina");
createCompany(exhibitor3, "Công ty TNHH Thương Mại Phú Cường",
"Chưa xác định", null, "logo-incomplete", CompanyStatus.INCOMPLETE_PROFILE);
createCompany(exhibitor4, "Công ty CP Xuất Nhập Khẩu Đại Thành",
"Bán lẻ", "Doanh nghiệp đã ngừng tham gia nền tảng.", "logo-archived",
CompanyStatus.ARCHIVED);
log.info("[SEED] Đã tạo 5 company (ACTIVE / INCOMPLETE_PROFILE / ARCHIVED)");

// ---------- 3. PACKAGE TEMPLATES ----------
PackageTemplate basicTemplate =
packageTemplateRepository.save(PackageTemplate.builder()
.createdBy(admin).name("Gói Cơ Bản")
.description("Gian hàng tiêu chuẩn cho doanh nghiệp mới tham gia.")
.price(new BigDecimal("5000000")).currency("VND")
.maxProductsPerBooth(20).maxEmbeddedVideosPerBooth(2)
.maxPanoramasPerBooth(3).maxHotspotsPerBooth(15)
.listingPriority(BoothListingPriority.NORMAL)
.status(PackageTemplateStatus.ACTIVE).build());

PackageTemplate premiumTemplate =
packageTemplateRepository.save(PackageTemplate.builder()
.createdBy(admin).name("Gói Cao Cấp")
.description("Gian hàng nổi bật, ưu tiên hiển thị đầu danh sách.")
.price(new BigDecimal("15000000")).currency("VND")
.maxProductsPerBooth(100).maxEmbeddedVideosPerBooth(10)
.maxPanoramasPerBooth(10).maxHotspotsPerBooth(60)
.listingPriority(BoothListingPriority.FEATURED)
.status(PackageTemplateStatus.ACTIVE).build());

// Phủ BoothListingPriority.PRIORITY và PackageTemplateStatus.INACTIVE
packageTemplateRepository.save(PackageTemplate.builder()
.createdBy(admin).name("Gói Ưu Tiên (ngừng bán)")
.description("Gói cũ đã ngừng kinh doanh, giữ lại để tra cứu lịch sử.")
.price(new BigDecimal("9000000")).currency("VND")
.maxProductsPerBooth(50).maxEmbeddedVideosPerBooth(5)
.maxPanoramasPerBooth(6).maxHotspotsPerBooth(30)
.listingPriority(BoothListingPriority.PRIORITY)
.status(PackageTemplateStatus.INACTIVE).build());
log.info("[SEED] Đã tạo 3 package template (phủ đủ BoothListingPriority + PackageTemplateStatus)");

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

log.info("[SEED] Đã tạo 5 exhibition");

// ---------- 5. EXHIBITION ASSETS ----------
Uploaded keyVisual = upload(IMG_SHOWROOM, "seed/exhibition");
Uploaded sponsorLogo = upload(letterLogo("Ngân hàng TMCP Kim Cương Việt"),
"seed/exhibition");
exhibitionAssetRepository.save(ExhibitionAsset.builder().exhibition(exhibition)
.assetUrl(keyVisual.url()).publicId(keyVisual.publicId())
.type(ExhibitionAssetType.KEY_VISUAL).build());
exhibitionAssetRepository.save(ExhibitionAsset.builder().exhibition(exhibition)
.assetUrl(sponsorLogo.url()).publicId(sponsorLogo.publicId())
.name("Ngân hàng TMCP Kim Cương Việt")
.type(ExhibitionAssetType.SPONSOR_LOGO).build());

Uploaded greenTechVisual = upload(IMG_GREEN_TECH, "seed/exhibition");
exhibitionAssetRepository.save(ExhibitionAsset.builder().exhibition(exhActive)
.assetUrl(greenTechVisual.url()).publicId(greenTechVisual.publicId())
.type(ExhibitionAssetType.KEY_VISUAL).build());

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
ExhibitionPackage basicPackage = savePackage(basicTemplate, exhibition,
"5000000");
ExhibitionPackage premiumPackage = savePackage(premiumTemplate, exhibition,
"13500000");
ExhibitionPackage designTestPackage = savePackage(basicTemplate,
exhRegistration, "5000000");
ExhibitionPackage regPackage = savePackage(basicTemplate, exhRegistration,
"4500000");
savePackage(premiumTemplate, exhRegistration, "12000000");
savePackage(basicTemplate, exhActive, "6000000");
log.info("[SEED] Đã tạo 6 exhibition package");

// ---------- 7. EXHIBITOR REGISTRATIONS (phủ đủ 5 trạng thái) ----------
ExhibitorRegistration reg1 =
exhibitorRegistrationRepository.save(buildRegistration(
premiumPackage, company1, ExhibitorRegistrationStatus.APPROVED,
admin, "Chúng tôi muốn giới thiệu bộ sưu tập nội thất gỗ mới.", null,
"Gian hàng Nội thất Mộc Việt",
"Gian hàng giới thiệu bộ sưu tập nội thất gỗ cao cấp Mộc Việt."));
ExhibitorRegistration reg2 =
exhibitorRegistrationRepository.save(buildRegistration(
basicPackage, company2, ExhibitorRegistrationStatus.PENDING_PAYMENT,
null, "TechVina mong muốn tiếp cận khách hàng doanh nghiệp.", null,
"Gian hàng TechVina IoT",
"Gian hàng giới thiệu giải pháp nhà thông minh và thiết bị IoT TechVina."));
ExhibitorRegistration designTestRegistration =
exhibitorRegistrationRepository.save(buildRegistration(
designTestPackage, company2, ExhibitorRegistrationStatus.APPROVED,
admin, "TechVina sử dụng dịch vụ thiết kế gian hàng 360.", null,
"Gian hàng TechVina", "Trưng bày thiết bị công nghệ thông minh."));
ExhibitorRegistration payosWebhookTestRegistration =
exhibitorRegistrationRepository
.save(buildRegistration(
regPackage, company2,
ExhibitorRegistrationStatus.PENDING_PAYMENT,
null, "Dữ liệu kiểm thử tích hợp webhook PayOS.", null,
"PAYHK Webhook Integration Test",
"Gian hàng chuyên dùng cho kiểm thử webhook PayOS."));
ExhibitorRegistration reg3 =
exhibitorRegistrationRepository.save(buildRegistration(
regPackage, company2, ExhibitorRegistrationStatus.PENDING,
null, "TechVina muốn trưng bày giải pháp vật liệu thông minh.", null,
"Gian hàng Vật liệu TechVina",
"Trưng bày giải pháp vật liệu thông minh cho công trình hiện đại."));
ExhibitorRegistration reg4 =
exhibitorRegistrationRepository.save(buildRegistration(
regPackage, company1, ExhibitorRegistrationStatus.REJECTED,
organizer, "Mộc Việt đăng ký gian hàng nội thất gỗ.",
"Ngành hàng không phù hợp với chủ đề vật liệu xây dựng của triển lãm.",
"Gian hàng Gỗ Mộc Việt", "Bộ sưu tập sản phẩm gỗ tự nhiên cao cấp."));
log.info("[SEED] Đã tạo 6 exhibitor registration "
+ "(APPROVED/PENDING_PAYMENT/PENDING/REJECTED/CANCELED)");

// ---------- 8. PAYMENTS ----------
paymentRepository.save(Payment.builder()
.exhibitorRegistration(reg1).paymentType(PaymentType.EXHIBITION_REGISTRATION)
.orderCode(orderCode()).amount(new BigDecimal("13500000"))
.systemFee(new BigDecimal("1350000")).organizerPayout(new
BigDecimal("12150000"))
.currency("VND").paymentProvider("PAYOS").paymentReference("SEED-PAY-001")
.status(PaymentStatus.PAID).paidAt(Instant.now().minus(1,
ChronoUnit.DAYS)).build());
paymentRepository.save(Payment.builder()
.exhibitorRegistration(reg2).paymentType(PaymentType.EXHIBITION_REGISTRATION)
.orderCode(orderCode()).amount(new BigDecimal("5000000"))
.systemFee(new BigDecimal("500000")).organizerPayout(new
BigDecimal("4500000"))
.currency("VND").paymentProvider("PAYOS")
.status(PaymentStatus.PENDING).build());
paymentRepository.save(Payment.builder()
.exhibitorRegistration(payosWebhookTestRegistration)
.paymentType(PaymentType.EXHIBITION_REGISTRATION)
.orderCode(orderCode()).amount(new BigDecimal("4500000"))
.systemFee(new BigDecimal("450000")).organizerPayout(new
BigDecimal("4050000"))
.currency("VND").paymentProvider("PAYOS")
.status(PaymentStatus.PENDING).build());
paymentRepository.save(Payment.builder()
.exhibitorRegistration(designTestRegistration)
.paymentType(PaymentType.EXHIBITION_REGISTRATION)
.orderCode(orderCode()).amount(new BigDecimal("5000000"))
.systemFee(new BigDecimal("500000")).organizerPayout(new
BigDecimal("4500000"))
.currency("VND").paymentProvider("PAYOS").paymentReference("SEED-DESIGN-TEST")
.status(PaymentStatus.PAID).paidAt(Instant.now().minus(2,
ChronoUnit.DAYS)).build());
paymentRepository.save(Payment.builder()
.exhibitorRegistration(reg4).paymentType(PaymentType.EXHIBITION_REGISTRATION)
.orderCode(orderCode()).amount(new BigDecimal("4500000"))
.systemFee(new BigDecimal("450000")).organizerPayout(new
BigDecimal("4050000"))
.currency("VND").paymentProvider("PAYOS").paymentReference("SEED-PAY-FAILED")
.status(PaymentStatus.FAILED).build());
log.info("[SEED] Đã tạo 5 payment (PAID/PENDING/FAILED)");

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

// Phủ nốt BoothStatus: DESIGNING / PENDING / ARCHIVED (booth không gắn đơn
// đăng
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

ensureOtherOrganizerActiveExhibitionFixtures();
ensureBulkDemoOrganizersFixtures();

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
Product sofa = saveProduct(company1, catSofa, "Sofa gỗ óc chó Luxury",
"MV-SOFA-001",
"Sofa 3 chỗ khung gỗ óc chó, đệm da bò thật.", new BigDecimal("28500000"),
IMG_SOFA);
Product table = saveProduct(company1, catTable, "Bàn ăn gỗ sồi 6 chỗ",
"MV-TABLE-002",
"Bàn ăn mặt gỗ sồi nguyên tấm, chân sắt sơn tĩnh điện.", new
BigDecimal("15200000"),
IMG_TABLE);
// Sản phẩm này có thêm ProductContent kiểu VIDEO -> phủ
// ProductContentType.VIDEO
Product sensor = saveProduct(company2, catDevice, "Cảm biến môi trường TechVina S1", "TV-SEN-001",
"Đo nhiệt độ, độ ẩm, CO2 theo thời gian thực.", new BigDecimal("3200000"),
IMG_SENSOR, ProductStatus.ACTIVE, VIDEO_SENSOR);
// Phủ ProductStatus.INACTIVE
saveProduct(company1, catDiscontinued, "Tủ gỗ Xoan Đào (ngừng bán)",
"MV-CAB-003",
"Mẫu tủ đã ngừng sản xuất, giữ lại để tra cứu.", new BigDecimal("9800000"),
IMG_CABINET, ProductStatus.INACTIVE, null);
log.info("[SEED] Đã tạo 4 product + content (phủ đủ ProductStatus + ProductContentType)");

// ---------- 14. MEDIA ASSETS ----------
MediaAsset banner = saveMediaAsset(company1, "Banner khuyến mãi Mộc Việt",
IMG_SHOWROOM);
MediaAsset poster = saveMediaAsset(company2, "Poster giới thiệu TechVina",
IMG_SENSOR);
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
StoragePackage pkg1GB =
storagePackageRepository.save(StoragePackage.builder()
.name("Gói 1 GB").description("Thêm 1 GB dung lượng lưu trữ.")
.quotaBytes(1_073_741_824L).priceVnd(10_000L).isActive(true).build());
StoragePackage pkg5GB =
storagePackageRepository.save(StoragePackage.builder()
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
StoragePackageOrder cancelledOrder =
storagePackageOrderRepository.save(StoragePackageOrder.builder()
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
.requesterName("Vũ Văn Anh").requesterEmail("guest.partner@example.com")
.requesterPhoneNumber("0906666666").organizationName("Công ty TNHH Ánh Dương")
.requestedRole(Role.EXHIBITOR)
.accountAction(PartnershipAccountAction.CREATE_NEW_ACCOUNT)
.message("Chúng tôi muốn mở gian hàng tại triển lãm sắp tới.")
.acceptedPolicy(Boolean.TRUE).status(PartnershipRequestStatus.PENDING)
.activeRequesterEmail("guest.partner@example.com").build());
partnershipRequestRepository.save(PartnershipRequest.builder()
.requesterName("Đỗ Nhật Minh").requesterEmail("guest.partner2@example.com")
.requesterPhoneNumber("0907777777").organizationName("Công ty CP Sao Mai")
.requestedRole(Role.ORGANIZER)
.accountAction(PartnershipAccountAction.CREATE_NEW_ACCOUNT)
.message("Chúng tôi muốn tổ chức triển lãm ngành xây dựng.")
.acceptedPolicy(Boolean.TRUE).status(PartnershipRequestStatus.AWAITING_VERIFICATION)
.activeRequesterEmail("guest.partner2@example.com").build());
// Phủ nốt PartnershipRequestStatus (APPROVED/REJECTED/SUPERSEDED)
// và PartnershipAccountAction.UPGRADE_EXISTING_USER.
// activeRequesterEmail để null vì cột này unique - chỉ đơn đang "hoạt động"
// mới
// giữ giá trị.
partnershipRequestRepository.save(PartnershipRequest.builder()
.submittedByUser(visitor)
.requesterName(visitor.getFullName()).requesterEmail(visitor.getEmail())
.requesterPhoneNumber("0905555555").organizationName("Hộ kinh doanh Hoàng Gia")
.requestedRole(Role.EXHIBITOR)
.accountAction(PartnershipAccountAction.UPGRADE_EXISTING_USER)
.message("Tôi đang là visitor, muốn nâng cấp thành exhibitor.")
.acceptedPolicy(Boolean.TRUE).status(PartnershipRequestStatus.APPROVED)
.approvedUser(visitor).reviewedAt(Instant.now().minus(5,
ChronoUnit.DAYS)).build());
partnershipRequestRepository.save(PartnershipRequest.builder()
.requesterName("Trịnh Trần Phương Tuấn").requesterEmail("guest.partner3@example.com")
.requesterPhoneNumber("0907888888").organizationName("Công ty TNHH Chưa Đủ Điều Kiện")
.requestedRole(Role.EXHIBITOR)
.accountAction(PartnershipAccountAction.CREATE_NEW_ACCOUNT)
.message("Chúng tôi muốn tham gia nền tảng.")
.acceptedPolicy(Boolean.TRUE).status(PartnershipRequestStatus.REJECTED)
.reviewNote("Thông tin doanh nghiệp chưa đầy đủ, vui lòng bổ sung giấy phép.")
.reviewedAt(Instant.now().minus(4, ChronoUnit.DAYS)).build());
partnershipRequestRepository.save(PartnershipRequest.builder()
.requesterName("Trịnh Trần Phương Tuấn").requesterEmail("guest.partner3@example.com")
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
DesignRequest assignedDesignRequest =
designRequestRepository.save(DesignRequest.builder()
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

// ---------- 25. ANALYTICS EVENTS (rải qua nhiều ngày cho dashboard
// organizer +
// gian hàng) ----------
// Mỗi ngày: một số lượt BOOTH_VIEW (giờ khác nhau trong ngày) + BOOTH_LEAVE
// kèm
// thời
// lượng, cộng ENTER/LEAVE_EXHIBITION. Tất cả gắn với triển lãm chính và
// booth1
// (Mộc
// Việt) để cả dashboard organizer lẫn trang "Thống kê gian hàng" của
// exhibitor1
// đều
// có dữ liệu thật ngay khi seed xong (không cần tự click qua viewer 360 để
// tạo
// dữ liệu).
int[] daysAgo = { 18, 15, 12, 9, 6, 4, 2, 1 };
int[] viewsPerDay = { 4, 6, 5, 8, 7, 9, 8, 11 };
int[] visitsPerDay = { 2, 3, 2, 4, 3, 4, 3, 5 };
// Giờ trong ngày để rải lượt xem gian hàng -> biểu đồ "lượt xem theo giờ" có
// phân bố thật
// thay vì dồn hết vào 1 giờ cố định.
int[] hourSlots = { 9, 10, 11, 13, 14, 15, 17, 19, 20, 21 };
// // Thời lượng ở booth (giây), cố tình phủ đủ 5 khoảng của histogram thời
// gian
// ở
// // booth:
// // <30s, 30-60s, 1-3 phút, 3-5 phút, >5 phút.
int[] boothDurationsSeconds = { 15, 45, 90, 150, 240, 340, 20, 100 };
int totalAnalyticsEvents = 0;
ZoneId seedZone = ZoneId.systemDefault();
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
Instant tenAm =
day.atZone(seedZone).withHour(10).withMinute(0).withSecond(0).withNano(0)
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

// Bảng xếp hạng "hotspot/sản phẩm được click nhiều nhất" ở trang thống kê
// gian
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

ensurePublishedBoothsHaveDisplayableProductHotspot();

log.info("[SEED] HOÀN TẤT. Đăng nhập bằng bất kỳ email @vex360.local với mật khẩu: {}",
DEFAULT_PASSWORD);

log.info("[SEED] HOÀN TẤT (đã tắt phần seed analytics event giả lập).");
}

private void ensureOtherOrganizerActiveExhibitionFixtures() {
User admin = userRepository.findByEmail("admin@vex360.local").orElse(null);
User exhibitor1 =
userRepository.findByEmail("exhibitor@vex360.local").orElse(null);
User exhibitor2 =
userRepository.findByEmail("exhibitor2@vex360.local").orElse(null);
if (admin == null || exhibitor1 == null || exhibitor2 == null) {
log.warn("[SEED][OTHER-ORGANIZER] Thiếu admin hoặc exhibitor nền; không thể tạo fixture.");
return;
}

Company exhibitorCompany1 =
companyRepository.findByOwnerUserId(exhibitor1.getId()).orElse(null);
Company exhibitorCompany2 =
companyRepository.findByOwnerUserId(exhibitor2.getId()).orElse(null);
if (exhibitorCompany1 == null || exhibitorCompany2 == null) {
log.warn("[SEED][OTHER-ORGANIZER] Thiếu company exhibitor nền; không thể tạo fixture.");
return;
}

PackageTemplate template = packageTemplateRepository
.findByStatus(PackageTemplateStatus.ACTIVE, Sort.by("createdAt").ascending())
.stream()
.filter(candidate -> "Gói Cơ Bản".equals(candidate.getName()))
.findFirst()
.orElse(null);
if (template == null) {
log.warn("[SEED][OTHER-ORGANIZER] Không có Gói Cơ Bản ACTIVE; không thể tạo fixture.");
return;
}

User organizer = userRepository.findByEmail(OTHER_ORGANIZER_EMAIL)
.orElseGet(() -> createUser(
OTHER_ORGANIZER_EMAIL,
"Trần Minh Khôi",
Role.ORGANIZER,
"0908888888",
28));
organizer.setFullName("Trần Minh Khôi");
organizer.setPhoneNumber("0908888888");
organizer.setRole(Role.ORGANIZER);
organizer.setProvider(AuthProvider.LOCAL);
organizer.setStatus(UserStatus.ACTIVE);
organizer.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
userRepository.save(organizer);

Company organizerCompany =
companyRepository.findByOwnerUserId(organizer.getId())
.orElseGet(() -> createCompany(
organizer,
"Công ty Tổ chức Triển lãm Sao Việt",
"Sự kiện & Triển lãm",
"Đơn vị tổ chức triển lãm trải nghiệm và phong cách sống.",
"logo-sao-viet"));
organizerCompany.setOwnerUser(organizer);
organizerCompany.setName("Công ty Tổ chức Triển lãm Sao Việt");
organizerCompany.setIndustry("Sự kiện & Triển lãm");
organizerCompany.setDescription("Đơn vị tổ chức triển lãm trải nghiệm và phong cách sống.");
organizerCompany.setWebsite("https://sao-viet.example.com");
organizerCompany.setEmail(OTHER_ORGANIZER_EMAIL);
organizerCompany.setPhone("0908888888");
organizerCompany.setAddress("Số 10 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh");
organizerCompany.setStatus(CompanyStatus.ACTIVE);
companyRepository.save(organizerCompany);

LocalDate today = LocalDate.now();
Exhibition exhibition =
exhibitionRepository.findByUuid(OTHER_ORGANIZER_EXHIBITION_UUID)
.orElseGet(() -> saveExhibition(
OTHER_ORGANIZER_EXHIBITION_UUID,
organizer,
"Triển lãm Ngoại thất & Sân vườn Sao Việt 2026",
"Ngoại thất - Sân vườn",
"Triển lãm đồ ngoại thất và giải pháp sân vườn thông minh, đang diễn ra"
+ " của Organizer thứ hai, dùng để kiểm thử luồng tham quan chéo.",
today.minusDays(5),
today.plusDays(15),
20,
ExhibitionStatus.ACTIVE,
admin,
null));
exhibition.setOrganizer(organizer);
exhibition.setName("Triển lãm Ngoại thất & Sân vườn Sao Việt 2026");
exhibition.setCategory("Ngoại thất - Sân vườn");
exhibition.setDescription(
"Triển lãm đồ ngoại thất và giải pháp sân vườn thông minh, đang diễn ra của Organizer"
+ " thứ hai, dùng để kiểm thử luồng tham quan chéo.");
exhibition.setStartDate(today.minusDays(5));
exhibition.setEndDate(today.plusDays(15));
exhibition.setEstimatedBooths(20);
exhibition.setStatus(ExhibitionStatus.ACTIVE);
exhibition.setReviewedBy(admin);
exhibition.setReviewedAt(Instant.now().minus(2, ChronoUnit.DAYS));
exhibition.setRejectedReason(null);
exhibition.setRejectionCount(0);
exhibition = exhibitionRepository.save(exhibition);
ensureExhibitionKeyVisual("Triển lãm Ngoại thất & Sân vườn Sao Việt 2026",
IMG_GARDEN_FURNITURE, false);

ExhibitionPackage exhibitionPackage = ensureExhregPackage(template,
exhibition, "5000000");
ExhibitorRegistration registration1 = ensureApprovedRegistration(
OTHER_ORGANIZER_REGISTRATION_1_UUID,
exhibitionPackage,
exhibitorCompany1,
organizer,
"Gian hàng Mộc Việt tại Sao Việt",
"Không gian nội thất gỗ tự nhiên trong triển lãm Sao Việt.");
ExhibitorRegistration registration2 = ensureApprovedRegistration(
OTHER_ORGANIZER_REGISTRATION_2_UUID,
exhibitionPackage,
exhibitorCompany2,
organizer,
"Gian hàng TechVina tại Sao Việt",
"Không gian giải pháp nhà thông minh trong triển lãm Sao Việt.");

Booth booth1 = ensurePublishedBooth(
OTHER_ORGANIZER_BOOTH_1_UUID,
exhibitor1,
exhibitorCompany1,
registration1,
"Mộc Việt Living",
"Bộ sưu tập nội thất gỗ dành cho không gian sống hiện đại.",
"classic");
Booth booth2 = ensurePublishedBooth(
OTHER_ORGANIZER_BOOTH_2_UUID,
exhibitor2,
exhibitorCompany2,
registration2,
"TechVina Smart Living",
"Giải pháp thiết bị thông minh và tự động hóa cho gia đình.",
"modern");

ensureBoothTourContent(
booth1,
"Không gian Mộc Việt Living",
"Mộc Việt Living giới thiệu nội thất gỗ tự nhiên cho không gian sống hiện đại.",
0);
ensureBoothTourContent(
booth2,
"Không gian TechVina Smart Living",
"TechVina Smart Living giới thiệu giải pháp nhà thông minh và tự động hóa.",
1);

log.info("[SEED][OTHER-ORGANIZER] READY | email={} | password={} | exhibitionUuid={} "
+ "| boothUuids=[{}, {}]",
OTHER_ORGANIZER_EMAIL,
DEFAULT_PASSWORD,
OTHER_ORGANIZER_EXHIBITION_UUID,
OTHER_ORGANIZER_BOOTH_1_UUID,
OTHER_ORGANIZER_BOOTH_2_UUID);
}

        /**
         * Fixture hàng loạt: 10 organizer (7 ACTIVE / 2 INACTIVE / 1 BLOCKED),
         * 30 exhibitor ACTIVE, và 18 exhibition dàn trải đủ 6 trạng thái theo yêu
         * cầu demo (3 ACTIVE, 3 PUBLISHED, 3 REGISTRATION, 2 COMPLETED, 5 PENDING,
         * 2 REJECTED). Tách riêng phần tạo user (chỉ chạy một lần) và phần tạo/vá
         * exhibition (luôn chạy để bảo đảm đủ ảnh key visual kể cả khi user đã
         * tồn tại từ lần seed trước).
         */
        private void ensureBulkDemoOrganizersFixtures() {
                ensureBulkDemoOrganizersAndExhibitors();
                ensureBulkDemoCompanies();
                ensureBulkDemoExhibitions();
                ensureBulkDemoPackageTiers();
                ensureBulkDemoExhibitionPackages();
                ensureExhibitionKeyVisual("Triển lãm Nội thất & Công nghệ VEX360 2026",
                                IMG_SHOWROOM, true);
                ensureExhibitionKeyVisual("Triển lãm Công nghệ Xanh 2026", IMG_GREEN_TECH,
                                false);
                ensureExhibitionKeyVisual("Triển lãm Vật liệu Xây dựng 2026",
                                IMG_CONSTRUCTION_MATERIALS, false);
                ensureBulkDemoTravelBooths();
                ensureTravelDesignerFixtures();
                ensureBulkDemoGreenTechBooths();
                ensureBulkDemoFurnitureTechBooths();
                ensureBulkDemoSponsors();
        }

private void ensureBulkDemoOrganizersAndExhibitors() {
if (userRepository.existsByEmail("organizer3@vex360.local")) {
return;
}

// ---------- 10 organizer: 7 ACTIVE + 2 INACTIVE + 1 BLOCKED ----------
String[][] activeOrganizers = {
{ "organizer3@vex360.local", "Đặng Quốc Bảo", "0920000001" },
{ "organizer4@vex360.local", "Phan Thị Mai Anh", "0920000002" },
{ "organizer5@vex360.local", "Vũ Đình Trọng", "0920000003" },
{ "organizer6@vex360.local", "Ngô Thanh Tùng", "0920000004" },
{ "organizer7@vex360.local", "Hồ Bảo Ngọc", "0920000005" },
{ "organizer8@vex360.local", "Trịnh Xuân Sơn", "0920000006" },
{ "organizer9@vex360.local", "Lâm Thảo Vy", "0920000007" },
};
int avatarIdx = 30;
for (int i = 0; i < activeOrganizers.length; i++) {
createUser(activeOrganizers[i][0], activeOrganizers[i][1], Role.ORGANIZER,
activeOrganizers[i][2], avatarIdx++);
}
createUser("organizer10@vex360.local", "Đỗ Minh Hiếu", Role.ORGANIZER,
"0920000008", avatarIdx++,
UserStatus.INACTIVE, AuthProvider.LOCAL);
createUser("organizer11@vex360.local", "Châu Ngọc Diễm", Role.ORGANIZER,
"0920000009", avatarIdx++,
UserStatus.INACTIVE, AuthProvider.LOCAL);
createUser("organizer12@vex360.local", "Mai Anh Dũng", Role.ORGANIZER,
"0920000010", avatarIdx++,
UserStatus.BLOCKED, AuthProvider.LOCAL);
log.info("[SEED][BULK] Đã tạo 10 organizer (7 ACTIVE / 2 INACTIVE / 1 BLOCKED)");

// ---------- 30 exhibitor ACTIVE ----------
String[] exhibitorNames = {
"Nguyễn Thị Hồng Nhung", "Trần Văn Bình", "Lê Hoàng Phúc", "Phạm Thị Thu Hà",
"Hoàng Văn Đức", "Vũ Thị Ngọc Ánh", "Đặng Minh Quân", "Bùi Thị Kim Chi",
"Đỗ Văn Nam", "Ngô Thị Bích Trâm", "Dương Anh Khoa", "Lý Thị Mỹ Duyên",
"Trịnh Văn Hùng", "Phan Thị Lan Anh", "Đinh Công Thành", "Tô Thị Cẩm Tú",
"Mai Văn Sơn", "Chu Thị Hải Yến", "Lâm Đức Anh", "Vương Thị Diễm My",
"Huỳnh Văn Phong", "Nguyễn Thị Thanh Thảo", "Trần Đình Khang", "Lê Thị Ngọc Hân",
"Phạm Văn Trường", "Hồ Thị Mai Trinh", "Cao Văn Tuấn", "Đào Thị Yến Nhi",
"Nguyễn Văn Long", "Trần Thị Kim Ngân",
};
for (int i = 0; i < exhibitorNames.length; i++) {
createUser("exhibitor" + (i + 5) + "@vex360.local", exhibitorNames[i],
Role.EXHIBITOR,
String.format("09300000%02d", i + 1), avatarIdx++);
}
log.info("[SEED][BULK] Đã tạo 30 exhibitor ACTIVE");
}

private void ensureBulkDemoExhibitions() {
User admin = userRepository.findByEmail("admin@vex360.local").orElse(null);
String[] activeOrganizerEmails = {
"organizer3@vex360.local", "organizer4@vex360.local",
"organizer5@vex360.local",
"organizer6@vex360.local", "organizer7@vex360.local",
"organizer8@vex360.local",
"organizer9@vex360.local",
};
User[] org = new User[activeOrganizerEmails.length];
for (int i = 0; i < activeOrganizerEmails.length; i++) {
org[i] = userRepository.findByEmail(activeOrganizerEmails[i]).orElse(null);
}
if (admin == null || org[0] == null) {
log.warn("[SEED][BULK] Thiếu admin/organizer nền; không thể tạo/vá fixture exhibition hàng loạt.");
return;
}

// ---------- 18 exhibition, chia cho 7 organizer ACTIVE ----------
LocalDate today = LocalDate.now();

// 3 ACTIVE - đang diễn ra
createExhibitionIfAbsent(org[0], "Triển lãm Đồ gia dụng thông minh 2026", "Đồ gia dụng",
"Triển lãm các thiết bị gia dụng thông minh cho căn hộ hiện đại.",
today.minusDays(10), today.plusDays(9), 45, ExhibitionStatus.ACTIVE, admin,
null,
IMG_SMART_HOME);
createExhibitionIfAbsent(org[3], "Triển lãm Thời trang & Làm đẹp 2026", "Thời trang - Làm đẹp",
"Triển lãm các thương hiệu thời trang và làm đẹp trong nước.",
today.minusDays(6), today.plusDays(14), 60, ExhibitionStatus.ACTIVE, admin,
null,
IMG_FASHION);
createExhibitionIfAbsent(org[4], "Triển lãm Ẩm thực & Đồ uống 2026", "Ẩm thực",
"Triển lãm ẩm thực và đồ uống quy tụ các thương hiệu F&B hàng đầu.",
today.minusDays(14), today.plusDays(7), 70, ExhibitionStatus.ACTIVE, admin,
null,
IMG_FOOD_BEVERAGE);

// 3 PUBLISHED (1 ngày 17/08/2026, 1 cuối tháng 8, 1 cuối tháng 9)
createExhibitionIfAbsent(org[0], "Triển lãm Trang sức & Phụ kiện 2026",
"Trang sức",
"Triển lãm trang sứMonth.AUGUST và phụ kiện thời trang cao cấp.",
LocalDate.of(2026, Month.AUGUST, 17), LocalDate.of(2026, Month.AUGUST, 17), 25,
ExhibitionStatus.PUBLISHED,
admin, null, IMG_JEWELRY);
createExhibitionIfAbsent(org[1], "Triển lãm Nội thất Văn phòng 2026", "Nội thất văn phòng",
"Triển lãm giải pháp nội thất văn phòng cho doanh nghiệp.",
LocalDate.of(2026, Month.AUGUST, 28), LocalDate.of(2026, Month.AUGUST, 30), 35,
ExhibitionStatus.PUBLISHED,
admin, null, IMG_OFFICE_FURNITURE);
createExhibitionIfAbsent(org[2], "Triển lãm Thiết bị Y tế 2026", "Y tế",
"Triển lãm thiết bị và công nghệ y tế hiện đại.",
LocalDate.of(2026, Month.SEPTEMBER, 27), LocalDate.of(2026, Month.SEPTEMBER, 29), 30,
ExhibitionStatus.PUBLISHED,
admin, null, IMG_MEDICAL);

// 3 REGISTRATION - diễn ra trong tháng 9
createExhibitionIfAbsent(org[1], "Triển lãm Giáo dục & Đào tạo 2026", "Giáo dục",
"Triển lãm các chương trình giáo dục và đào tạo kỹ năng.",
LocalDate.of(2026, Month.SEPTEMBER, 3), LocalDate.of(2026, Month.SEPTEMBER, 7), 40,
ExhibitionStatus.REGISTRATION,
admin, null, IMG_EDUCATION);
createExhibitionIfAbsent(org[2], "Triển lãm Du lịch & Lữ hành 2026", "Du lịch",
"Triển lãm các tour du lịch và dịch vụ lữ hành trong và ngoài nước.",
LocalDate.of(2026, Month.SEPTEMBER, 10), LocalDate.of(2026, Month.SEPTEMBER, 14), 50,
ExhibitionStatus.REGISTRATION,
admin,
null, IMG_TRAVEL);
createExhibitionIfAbsent(org[4], "Triển lãm Cơ khí & Tự động hóa 2026", "Cơ khí",
"Triển lãm máy móc cơ khí và giải pháp tự động hóa sản xuất.",
LocalDate.of(2026, Month.SEPTEMBER, 18), LocalDate.of(2026, Month.SEPTEMBER, 22), 55,
ExhibitionStatus.REGISTRATION,
admin,
null, IMG_MACHINERY);

// 2 COMPLETED
createExhibitionIfAbsent(org[2], "Triển lãm Thủy sản Việt Nam 2025", "Thủy sản",
"Triển lãm ngành thủy sản đã diễn ra thành công.",
LocalDate.of(2025, Month.NOVEMBER, 10), LocalDate.of(2025, Month.NOVEMBER, 15), 40,
ExhibitionStatus.COMPLETED,
admin,
null, IMG_FISHERY);
createExhibitionIfAbsent(org[5], "Triển lãm Nông nghiệp Công nghệ cao 2026",
"Nông nghiệp",
"Triển lãm giải pháp nông nghiệp công nghệ cao đã kết thúc.",
LocalDate.of(2026, Month.MAY, 5), LocalDate.of(2026, Month.MAY, 10), 35,
ExhibitionStatus.COMPLETED,
admin, null, IMG_AGRICULTURE);

// 5 PENDING - chờ duyệt, diễn ra từ tháng 9 đến tháng 10
createExhibitionIfAbsent(org[0], "Triển lãm Đồ chơi & Mẹ bé 2026", "Mẹ và bé",
"Hồ sơ vừa gửi, đang chờ quản trị viên xét duyệt.",
LocalDate.of(2026, Month.SEPTEMBER, 5), LocalDate.of(2026, Month.SEPTEMBER, 9), 30,
ExhibitionStatus.PENDING, null,
null, IMG_TOYS);
createExhibitionIfAbsent(org[1], "Triển lãm Sách & Văn phòng phẩm 2026",
"Sách - Văn phòng phẩm",
"Hồ sơ vừa gửi, đang chờ quản trị viên xét duyệt.",
LocalDate.of(2026, Month.SEPTEMBER, 15), LocalDate.of(2026, Month.SEPTEMBER, 18), 25,
ExhibitionStatus.PENDING,
null, null, IMG_BOOKS);
createExhibitionIfAbsent(org[4], "Triển lãm Thể thao & Dã ngoại 2026", "Thể thao",
"Hồ sơ vừa gửi, đang chờ quản trị viên xét duyệt.",
LocalDate.of(2026, Month.SEPTEMBER, 25), LocalDate.of(2026, Month.SEPTEMBER, 28), 35,
ExhibitionStatus.PENDING,
null, null, IMG_SPORTS_OUTDOOR);
createExhibitionIfAbsent(org[5], "Triển lãm Điện tử tiêu dùng 2026", "Điện tử",
"Hồ sơ vừa gửi, đang chờ quản trị viên xét duyệt.",
LocalDate.of(2026, Month.OCTOBER, 2), LocalDate.of(2026, Month.OCTOBER, 6), 45,
ExhibitionStatus.PENDING,
null, null, IMG_ELECTRONICS);
createExhibitionIfAbsent(org[6], "Triển lãm Mỹ phẩm & Chăm sóc sức khỏe 2026", "Mỹ phẩm",
"Hồ sơ vừa gửi, đang chờ quản trị viên xét duyệt.",
LocalDate.of(2026, Month.OCTOBER, 12), LocalDate.of(2026, Month.OCTOBER, 16), 30,
ExhibitionStatus.PENDING,
null, null, IMG_COSMETICS);

// 2 REJECTED
createExhibitionIfAbsent(org[3], "Triển lãm Vật nuôi & Thú cưng 2026", "Thú cưng",
"Hồ sơ đã bị từ chối, tổ chức cần bổ sung giấy tờ.",
LocalDate.of(2026, Month.SEPTEMBER, 1), LocalDate.of(2026, Month.SEPTEMBER, 4), 20,
ExhibitionStatus.REJECTED,
admin,
"Danh mục sản phẩm chưa phù hợp với quy định nền tảng.", IMG_PETS);
createExhibitionIfAbsent(org[6], "Triển lãm Rượu & Đồ uống có cồn 2026", "Đồ uống có cồn",
"Hồ sơ đã bị từ chối, tổ chức cần bổ sung giấy tờ.",
LocalDate.of(2026, Month.SEPTEMBER, 8), LocalDate.of(2026, Month.SEPTEMBER, 11), 20,
ExhibitionStatus.REJECTED,
admin,
"Ngành hàng thuộc danh mục hạn chế, cần giấy phép kinh doanh đặc biệt.",
IMG_WINE);

log.info("[SEED][BULK] Đã tạo 18 exhibition "
+ "(3 ACTIVE/3 PUBLISHED/3 REGISTRATION/2 COMPLETED/5 PENDING/2 REJECTED)");
}

/**
* Company riêng cho 10 organizer + 30 exhibitor hàng loạt, tên/logo giống
// thực
* tế.
*/
private void ensureBulkDemoCompanies() {
String[][] organizerCompanies = {
{ "organizer3@vex360.local", "Công ty CP Tổ chức Sự kiện Bảo Minh",
"Sự kiện & Triển lãm",
"Đơn vị tổ chức sự kiện và triển lãm chuyên nghiệp tại khu vực phía Bắc.",
"bao-minh-events" },
{ "organizer4@vex360.local", "Công ty TNHH Truyền thông & Triển lãm Hoa Mai",
"Sự kiện & Triển lãm",
"Chuyên tổ chức triển lãm thương mại và hội chợ tiêu dùng.",
"hoa-mai-media" },
{ "organizer5@vex360.local", "Công ty CP Triển lãm Quốc tế Đông Dương",
"Sự kiện & Triển lãm",
"Đơn vị tổ chức triển lãm quốc tế kết nối doanh nghiệp Việt Nam và khu vực.",
"dong-duong-expo" },
{ "organizer6@vex360.local", "Công ty TNHH Sự kiện Tùng Lâm", "Sự kiện & Triển lãm",
"Tổ chức hội chợ, triển lãm chuyên ngành cho doanh nghiệp vừa và nhỏ.",
"tung-lam-events" },
{ "organizer7@vex360.local", "Công ty CP Truyền thông Ngọc Bích", "Sự kiện & Triển lãm",
"Đơn vị truyền thông và tổ chức sự kiện triển lãm trực tuyến.",
"ngoc-bich-media" },
{ "organizer8@vex360.local", "Công ty TNHH Tổ chức Triển lãm Sơn Hà",
"Sự kiện & Triển lãm",
"Chuyên tổ chức triển lãm ngành xây dựng và công nghiệp.",
"son-ha-expo" },
{ "organizer9@vex360.local", "Công ty CP Sự kiện & Triển lãm Việt Xanh",
"Sự kiện & Triển lãm",
"Đơn vị tổ chức triển lãm hướng đến phát triển bền vững.",
"viet-xanh-events" },
{ "organizer10@vex360.local", "Công ty TNHH Sự kiện Minh Hiếu", "Sự kiện & Triển lãm",
"Đơn vị tổ chức sự kiện quy mô vừa, hiện tạm ngừng hoạt động.",
"minh-hieu-events" },
{ "organizer11@vex360.local", "Công ty CP Triển lãm Ngọc Diễm", "Sự kiện & Triển lãm",
"Đơn vị tổ chức triển lãm thời trang và làm đẹp, hiện tạm ngừng hoạt động.",
"ngoc-diem-expo" },
{ "organizer12@vex360.local", "Công ty TNHH Tổ chức Sự kiện Anh Dũng",
"Sự kiện & Triển lãm",
"Đơn vị tổ chức sự kiện đang bị tạm khoá do vi phạm chính sách nền tảng.",
"anh-dung-events" },
};
for (String[] row : organizerCompanies) {
ensureCompanyForOwner(row[0], row[1], row[2], row[3], row[4]);
}

String[][] exhibitorCompanies = {
{ "exhibitor5@vex360.local", "Công ty TNHH Nội thất Hồng Nhung", "Nội thất",
"Chuyên sản xuất và phân phối nội thất gỗ tự nhiên cao cấp.",
"hong-nhung-furniture" },
{ "exhibitor6@vex360.local", "Công ty CP Cơ khí Bình Minh", "Cơ khí",
"Sản xuất máy móc và thiết bị cơ khí công nghiệp.",
"binh-minh-mechanical" },
{ "exhibitor7@vex360.local", "Công ty TNHH Công nghệ Phúc An", "Công nghệ",
"Phát triển giải pháp công nghệ và thiết bị thông minh.",
"phuc-an-tech" },
{ "exhibitor8@vex360.local", "Công ty CP Mỹ phẩm Thu Hà", "Mỹ phẩm",
"Sản xuất và phân phối mỹ phẩm thiên nhiên.", "thu-ha-cosmetics" },
{ "exhibitor9@vex360.local", "Công ty TNHH Thực phẩm Đức Phát", "Ẩm thực",
"Chế biến và phân phối thực phẩm sạch, đồ uống đóng chai.",
"duc-phat-foods" },
{ "exhibitor10@vex360.local", "Công ty CP Trang sức Ngọc Ánh", "Trang sức",
"Thiết kế và chế tác trang sức vàng bạc đá quý.", "ngoc-anh-jewelry" },
{ "exhibitor11@vex360.local", "Công ty TNHH Điện tử Minh Quân", "Điện tử",
"Phân phối thiết bị điện tử và linh kiện công nghệ.",
"minh-quan-electronics" },
{ "exhibitor12@vex360.local", "Công ty CP Thời trang Kim Chi", "Thời trang",
"Thiết kế và sản xuất thời trang nữ cao cấp.", "kim-chi-fashion" },
{ "exhibitor13@vex360.local", "Công ty TNHH Thủy sản Nam Phương", "Thủy sản",
"Chế biến và xuất khẩu thủy hải sản.", "nam-phuong-seafood" },
{ "exhibitor14@vex360.local", "Công ty CP Sách & Văn phòng phẩm Bích Trâm",
"Sách - Văn phòng phẩm",
"Phân phối sách và văn phòng phẩm cho trường học, doanh nghiệp.",
"bich-tram-books" },
{ "exhibitor15@vex360.local", "Công ty TNHH Thiết bị Y tế Anh Khoa", "Y tế",
"Nhập khẩu và phân phối thiết bị y tế hiện đại.", "anh-khoa-medical" },
{ "exhibitor16@vex360.local", "Công ty CP Du lịch Mỹ Duyên", "Du lịch",
"Tổ chức tour du lịch trong nước và quốc tế.", "my-duyen-travel" },
{ "exhibitor17@vex360.local", "Công ty TNHH Nông sản Hùng Vương", "Nông nghiệp",
"Sản xuất và phân phối nông sản sạch theo công nghệ cao.",
"hung-vuong-agri" },
{ "exhibitor18@vex360.local", "Công ty CP Đồ chơi Lan Anh", "Mẹ và bé",
"Sản xuất đồ chơi giáo dục và sản phẩm mẹ bé.", "lan-anh-toys" },
{ "exhibitor19@vex360.local", "Công ty TNHH Thể thao Công Thành", "Thể thao",
"Phân phối dụng cụ thể thao và thiết bị dã ngoại.",
"cong-thanh-sports" },
{ "exhibitor20@vex360.local", "Công ty CP Giáo dục Cẩm Tú", "Giáo dục",
"Cung cấp giải pháp đào tạo và tài liệu giáo dục.",
"cam-tu-education" },
{ "exhibitor21@vex360.local", "Công ty TNHH Vật liệu Xây dựng Sơn Hà", "Xây dựng",
"Sản xuất và phân phối vật liệu xây dựng.", "son-ha-materials" },
{ "exhibitor22@vex360.local", "Công ty CP Rượu vang Hải Yến", "Đồ uống có cồn",
"Nhập khẩu và phân phối rượu vang cao cấp.", "hai-yen-wine" },
{ "exhibitor23@vex360.local", "Công ty TNHH Thú cưng Đức Anh", "Thú cưng",
"Cung cấp sản phẩm và dịch vụ chăm sóc thú cưng.", "duc-anh-petcare" },
{ "exhibitor24@vex360.local", "Công ty CP Nội thất Văn phòng Diễm My",
"Nội thất văn phòng",
"Thiết kế và cung cấp nội thất văn phòng hiện đại.", "diem-my-office" },
{ "exhibitor25@vex360.local", "Công ty TNHH Cơ khí Tự động hóa Phong Phú",
"Cơ khí",
"Giải pháp tự động hóa dây chuyền sản xuất.", "phong-phu-automation" },
{ "exhibitor26@vex360.local", "Công ty CP Ẩm thực Thanh Thảo", "Ẩm thực",
"Chuỗi nhà hàng và dịch vụ ẩm thực Việt Nam.", "thanh-thao-cuisine" },
{ "exhibitor27@vex360.local", "Công ty TNHH Đồ gia dụng Đình Khang", "Đồ gia dụng",
"Phân phối thiết bị gia dụng thông minh.", "dinh-khang-homeware" },
{ "exhibitor28@vex360.local", "Công ty CP Trang sức Ngọc Hân", "Trang sức",
"Thiết kế trang sức bạc và phụ kiện thời trang.", "ngoc-han-jewelry" },
{ "exhibitor29@vex360.local", "Công ty TNHH Xây dựng Trường Thịnh", "Xây dựng",
"Thi công và cung cấp giải pháp xây dựng dân dụng.",
"truong-thinh-construction" },
{ "exhibitor30@vex360.local", "Công ty CP Mỹ phẩm Mai Trinh", "Mỹ phẩm",
"Sản xuất mỹ phẩm chăm sóc da chiết xuất thiên nhiên.",
"mai-trinh-cosmetics" },
{ "exhibitor31@vex360.local", "Công ty TNHH Điện tử Tuấn Phát", "Điện tử",
"Phân phối thiết bị điện tử tiêu dùng.", "tuan-phat-electronics" },
{ "exhibitor32@vex360.local", "Công ty CP Thời trang Yến Nhi", "Thời trang",
"Thiết kế thời trang trẻ em và gia đình.", "yen-nhi-fashion" },
{ "exhibitor33@vex360.local", "Công ty TNHH Nông nghiệp Công nghệ cao Long Phát",
"Nông nghiệp",
"Ứng dụng công nghệ cao trong canh tác nông nghiệp.",
"long-phat-agritech" },
{ "exhibitor34@vex360.local", "Công ty CP Du lịch Kim Ngân", "Du lịch",
"Tổ chức tour du lịch nghỉ dưỡng và team building.",
"kim-ngan-travel" },
};
for (String[] row : exhibitorCompanies) {
ensureCompanyForOwner(row[0], row[1], row[2], row[3], row[4]);
}
log.info("[SEED][BULK] Đã tạo company cho 10 organizer + 30 exhibitor hàng loạt");
}

        private void ensureCompanyForOwner(String ownerEmail, String name, String industry, String description,
                        String logoSeed) {
                User owner = userRepository.findByEmail(ownerEmail).orElse(null);
                if (owner == null) {
                        return;
                }
                if (companyRepository.findByOwnerUserId(owner.getId()).isPresent()) {
                        return;
                }
                createCompany(owner, name, industry, description, logoSeed);
        }

/**
* Gói dịch vụ 3 tier (Khởi Nghiệp/Chuyên Nghiệp/Doanh Nghiệp), mỗi tier 2
* gói (theo triển lãm / trọn năm), thông số lần lượt 100/200/300, giá tối
* thiểu 0đ/10.000đ/20.000đ. Thêm 1 gói INACTIVE (lưu trữ, ngừng kinh doanh).
*/
private void ensureBulkDemoPackageTiers() {
User admin = userRepository.findByEmail("admin@vex360.local").orElse(null);
if (admin == null) {
return;
}
ensurePackageTemplate(admin, "Gói Khởi Nghiệp",
"Gói khởi điểm cho gian hàng tham gia theo từng triển lãm, tối đa 100 sản phẩm.",
"0", 100, 10, 5, 50, BoothListingPriority.NORMAL,
PackageTemplateStatus.ACTIVE);
ensurePackageTemplate(admin, "Gói Khởi Nghiệp",
"Gói khởi điểm dùng trọn năm cho nhiều triển lãm, tối đa 100 sản phẩm.",
"0", 100, 10, 5, 50, BoothListingPriority.NORMAL,
PackageTemplateStatus.ACTIVE);
ensurePackageTemplate(admin, "Gói Chuyên Nghiệp",
"Gói tiêu chuẩn cho gian hàng tham gia theo từng triển lãm, tối đa 200 sản phẩm.",
"10000", 200, 20, 10, 100, BoothListingPriority.PRIORITY,
PackageTemplateStatus.ACTIVE);
ensurePackageTemplate(admin, "Gói Chuyên Nghiệp",
"Gói tiêu chuẩn dùng trọn năm cho nhiều triển lãm, tối đa 200 sản phẩm.",
"10000", 200, 20, 10, 100, BoothListingPriority.PRIORITY,
PackageTemplateStatus.ACTIVE);
ensurePackageTemplate(admin, "Gói Doanh Nghiệp ",
"Gói cao cấp cho gian hàng tham gia theo từng triển lãm, tối đa 300 sản phẩm.",
"20000", 300, 30, 15, 150, BoothListingPriority.FEATURED,
PackageTemplateStatus.ACTIVE);
ensurePackageTemplate(admin, "Gói Doanh Nghiệp ",
"Gói cao cấp dùng trọn năm cho nhiều triển lãm, tối đa 300 sản phẩm.",
"20000", 300, 30, 15, 150, BoothListingPriority.FEATURED,
PackageTemplateStatus.ACTIVE);
ensurePackageTemplate(admin, "Gói Doanh Nghiệp Plus (Ngừng Kinh Doanh)",
"Gói cao cấp nhất, đã ngừng kinh doanh, giữ lại để tra cứu lịch sử.",
"30000", 350, 35, 18, 175, BoothListingPriority.PRIORITY,
PackageTemplateStatus.INACTIVE);
log.info("[SEED][BULK] Đã tạo 7 package template 3-tier (6 ACTIVE + 1 INACTIVE)");
}

        private void ensurePackageTemplate(User admin, String name, String description, String price,
                        int maxProducts, int maxVideos, int maxPanoramas, int maxHotspots,
                        BoothListingPriority listingPriority, PackageTemplateStatus status) {
                if (packageTemplateRepository.existsByNameIgnoreCase(name)) {
                        return;
                }
                packageTemplateRepository.save(PackageTemplate.builder()
                                .createdBy(admin).name(name).description(description)
                                .price(new BigDecimal(price)).currency("VND")
                                .maxProductsPerBooth(maxProducts).maxEmbeddedVideosPerBooth(maxVideos)
                                .maxPanoramasPerBooth(maxPanoramas).maxHotspotsPerBooth(maxHotspots)
                                .listingPriority(listingPriority).status(status).build());
        }

/**
* Gán 3 exhibition package (1 mỗi tier, giá = giá tối thiểu tier + 10.000đ)
* cho toàn bộ 23 triển lãm (5 gốc + 18 hàng loạt).
*/
private void ensureBulkDemoExhibitionPackages() {
PackageTemplate tier1 = findPackageTemplateByName("Gói Khởi Nghiệp");
PackageTemplate tier2 = findPackageTemplateByName("Gói Chuyên Nghiệp");
PackageTemplate tier3 = findPackageTemplateByName("Gói Doanh Nghiệp");
if (tier1 == null || tier2 == null || tier3 == null) {
log.warn("[SEED][BULK] Thiếu package template 3-tier; bỏ qua gán exhibition package.");
return;
}

String[] exhibitionNames = {
"Triển lãm Nội thất & Công nghệ VEX360 2026",
"Triển lãm Thủ công Mỹ nghệ Việt 2026",
"Triển lãm Xe cũ Toàn quốc 2026",
"Triển lãm Vật liệu Xây dựng 2026",
"Triển lãm Công nghệ Xanh 2026",
"Triển lãm Đồ gia dụng thông minh 2026",
"Triển lãm Thời trang & Làm đẹp 2026",
"Triển lãm Ẩm thực & Đồ uống 2026",
"Triển lãm Trang sức & Phụ kiện 2026",
"Triển lãm Nội thất Văn phòng 2026",
"Triển lãm Thiết bị Y tế 2026",
"Triển lãm Giáo dục & Đào tạo 2026",
"Triển lãm Du lịch & Lữ hành 2026",
"Triển lãm Cơ khí & Tự động hóa 2026",
"Triển lãm Thủy sản Việt Nam 2025",
"Triển lãm Nông nghiệp Công nghệ cao 2026",
"Triển lãm Đồ chơi & Mẹ bé 2026",
"Triển lãm Sách & Văn phòng phẩm 2026",
"Triển lãm Thể thao & Dã ngoại 2026",
"Triển lãm Điện tử tiêu dùng 2026",
"Triển lãm Mỹ phẩm & Chăm sóc sức khỏe 2026",
"Triển lãm Vật nuôi & Thú cưng 2026",
"Triển lãm Rượu & Đồ uống có cồn 2026",
};
int matched = 0;
for (String name : exhibitionNames) {
Exhibition exhibition = findExhibitionByName(name);
if (exhibition == null) {
continue;
}
ensureExhibitionPackage(exhibition, tier1, "10000");
ensureExhibitionPackage(exhibition, tier2, "20000");
ensureExhibitionPackage(exhibition, tier3, "30000");
matched++;
}
log.info("[SEED][BULK] Đã bảo đảm 3 exhibition package (3 tier) cho {} triển lãm", matched);
}

        private void ensureExhibitionPackage(Exhibition exhibition, PackageTemplate template, String finalPrice) {
                boolean exists = exhibitionPackageRepository
                                .findByExhibitionIdAndTemplateId(exhibition.getId(), template.getId())
                                .isPresent();
                if (!exists) {
                        savePackage(template, exhibition, finalPrice);
                }
        }

        /**
         * Bảo đảm exhibition có đúng ảnh KEY_VISUAL. Nếu {@code forceReplace} là
         * true thì luôn ghi đè ảnh cũ (dùng khi đổi ảnh cho đúng nội dung); nếu
         * false thì chỉ thêm ảnh khi exhibition chưa có KEY_VISUAL nào.
         */
        private void ensureExhibitionKeyVisual(String exhibitionName, String imageUrl, boolean forceReplace) {
                Exhibition exhibition = findExhibitionByName(exhibitionName);
                if (exhibition == null) {
                        return;
                }
                ExhibitionAsset existing = findExhibitionAssets(exhibition.getId(),
                                ExhibitionAssetType.KEY_VISUAL)
                                .stream().findFirst().orElse(null);
                if (existing != null && !forceReplace) {
                        return;
                }
                Uploaded uploaded = upload(imageUrl, "seed/exhibition");
                if (existing != null) {
                        existing.setAssetUrl(uploaded.url());
                        existing.setPublicId(uploaded.publicId());
                        exhibitionAssetRepository.save(existing);
                } else {
                        exhibitionAssetRepository.save(ExhibitionAsset.builder().exhibition(exhibition)
                                        .assetUrl(uploaded.url()).publicId(uploaded.publicId())
                                        .type(ExhibitionAssetType.KEY_VISUAL).build());
                }
        }

        private void createExhibitionIfAbsent(User organizer, String name, String category, String description,
                        LocalDate startDate, LocalDate endDate, int estimatedBooths,
                        ExhibitionStatus status, User reviewedBy, String rejectedReason, String keyVisualImageUrl) {
                Exhibition exhibition = findExhibitionByName(name);
                if (exhibition == null) {
                        exhibition = saveExhibition(organizer, name, category, description,
                                        startDate, endDate,
                                        estimatedBooths, status, reviewedBy, rejectedReason);
                }
                boolean hasKeyVisual = !findExhibitionAssets(exhibition.getId(),
                                ExhibitionAssetType.KEY_VISUAL)
                                .isEmpty();
                if (!hasKeyVisual) {
                        Uploaded keyVisual = upload(keyVisualImageUrl, "seed/exhibition");
                        exhibitionAssetRepository.save(ExhibitionAsset.builder().exhibition(exhibition)
                                        .assetUrl(keyVisual.url()).publicId(keyVisual.publicId())
                                        .type(ExhibitionAssetType.KEY_VISUAL).build());
                }
        }

private Exhibition findExhibitionByName(String name) {
return entityManager.createQuery("SELECT e FROM Exhibition e WHERE e.name = :name", Exhibition.class)
.setParameter("name", name)
.getResultStream()
.findFirst()
.orElse(null);
}

private PackageTemplate findPackageTemplateByName(String name) {
return entityManager.createQuery(
"SELECT p FROM PackageTemplate p WHERE LOWER(TRIM(p.name)) = LOWER(TRIM(:name))",
PackageTemplate.class)
.setParameter("name", name)
.getResultStream()
.findFirst()
.orElse(null);
}

private List<ExhibitionAsset> findExhibitionAssets(Integer exhibitionId,
ExhibitionAssetType type) {
return entityManager.createQuery(
"SELECT a FROM ExhibitionAsset a WHERE a.exhibition.id = :exhibitionId AND a.type = :type",
ExhibitionAsset.class)
.setParameter("exhibitionId", exhibitionId)
.setParameter("type", type)
.getResultList();
}

        private ExhibitorRegistration ensureApprovedRegistration(
                        UUID registrationUuid,
                        ExhibitionPackage exhibitionPackage,
                        Company company,
                        User reviewedBy,
                        String boothName,
                        String boothDescription) {
                return ensureApprovedRegistration(registrationUuid, exhibitionPackage,
                                company, reviewedBy,
                                "Tham gia triển lãm đang diễn ra của Organizer thứ hai.", boothName,
                                boothDescription);
        }

        private ExhibitorRegistration ensureApprovedRegistration(
                        UUID registrationUuid,
                        ExhibitionPackage exhibitionPackage,
                        Company company,
                        User reviewedBy,
                        String participationReason,
                        String boothName,
                        String boothDescription) {
                ExhibitorRegistration registration = exhibitorRegistrationRepository
                                .findByUuid(registrationUuid)
                                .orElseGet(() -> {
                                        ExhibitorRegistration created = buildRegistration(
                                                        exhibitionPackage,
                                                        company,
                                                        ExhibitorRegistrationStatus.APPROVED,
                                                        reviewedBy,
                                                        participationReason,
                                                        null,
                                                        boothName,
                                                        boothDescription);
                                        created.setUuid(registrationUuid);
                                        return created;
                                });
                registration.setExhibitionPackage(exhibitionPackage);
                registration.setCompany(company);
                registration.setStatus(ExhibitorRegistrationStatus.APPROVED);
                registration.setReviewedBy(reviewedBy);
                registration.setRejectedReason(null);
                registration.setParticipationReason(participationReason);
                registration.setBoothName(boothName);
                registration.setBoothDescription(boothDescription);
                registration.setPackageNameSnapshot(exhibitionPackage.getPackageNameSnapshot());
                registration.setPriceSnapshot(exhibitionPackage.getPriceSnapshot());
                registration.setFinalPriceSnapshot(exhibitionPackage.getFinalPrice());
                registration.setCurrencySnapshot(exhibitionPackage.getCurrencySnapshot());
                registration.setMaxProductsPerBoothSnapshot(exhibitionPackage.getMaxProductsPerBoothSnapshot());
                registration.setMaxEmbeddedVideosPerBoothSnapshot(
                                exhibitionPackage.getMaxEmbeddedVideosPerBoothSnapshot());
                registration.setMaxPanoramasPerBoothSnapshot(exhibitionPackage.getMaxPanoramasPerBoothSnapshot());
                registration.setMaxHotspotsPerBoothSnapshot(exhibitionPackage.getMaxHotspotsPerBoothSnapshot());
                registration.setListingPrioritySnapshot(exhibitionPackage.getListingPrioritySnapshot());
                return exhibitorRegistrationRepository.save(registration);
        }

        private Booth ensurePublishedBooth(
                        UUID boothUuid,
                        User createdBy,
                        Company company,
                        ExhibitorRegistration registration,
                        String name,
                        String description,
                        String displayTemplateKey) {
                Booth booth = boothRepository.findById(boothUuid).orElse(null);
                if (booth == null) {
                        booth = insertBoothFixtureShell(boothUuid, createdBy);
                }
                booth.setName(name);
                booth.setDescription(description);
                booth.setStatus(BoothStatus.PUBLISHED);
                booth.setIsTemplate(false);
                booth.setCreatedBy(createdBy);
                booth.setCompany(company);
                booth.setExhibitorRegistration(registration);
                booth.setDisplayTemplateKey(displayTemplateKey);
                return boothRepository.save(booth);
        }

        private void ensureBoothTourContent(
                        Booth booth,
                        String panoramaName,
                        String hotspotInfo,
                        int imageIndex) {
                Panorama panorama = panoramaRepository.findByBoothIdOrderByOrderIndexAsc(booth.getId())
                                .stream()
                                .findFirst()
                                .orElseGet(() -> savePanorama(booth, panoramaName, imageIndex, 0, true));
                if (hotspotRepository.countBySourcePanoramaBoothId(booth.getId()) == 0) {
                        hotspotRepository.save(Hotspot.builder()
                                        .type(HotspotType.INFO)
                                        .name("Giới thiệu gian hàng")
                                        .sourcePanorama(panorama)
                                        .infoText(hotspotInfo)
                                        .infoContentType(HotspotInfoContentType.TEXT)
                                        .xPosition(0.0)
                                        .yPosition(20.0)
                                        .zPosition(-350.0)
                                        .iconStyle("classic")
                                        .scale(1.0)
                                        .zIndex(1)
                                        .build());
                }
        }

/**
* Sinh idempotent N gian hàng PUBLISHED cho 1 triển lãm, mỗi gian hàng gắn
* với 1 company exhibitor hàng loạt (exhibitor5..34). Ảnh thumbnail và
* panorama được xoay vòng qua {@code thumbnailImageUrls}/
* {@code panoramaImageUrls} theo vị trí gian hàng để mỗi gian hàng có ảnh
* khác gian hàng liền kề (kể cả khi gian hàng đã tồn tại từ lần seed
* trước - ảnh sẽ được cập nhật lại cho đúng vị trí xoay vòng).
*/
private void ensureBulkBoothsForExhibition(
String exhibitionName,
int[] companyIndices,
String seedKeyPrefix,
String boothNamePrefix,
String boothDescriptionTemplate,
String participationReason,
String[] thumbnailImageUrls,
String[] panoramaImageUrls,
String panoramaHotspotInfoTemplate) {
Exhibition exhibition = findExhibitionByName(exhibitionName);
User admin = userRepository.findByEmail("admin@vex360.local").orElse(null);
if (exhibition == null || admin == null) {
return;
}
ExhibitionPackage exhibitionPackage = exhibitionPackageRepository
.findByExhibitionId(exhibition.getId())
.stream()
.filter(candidate -> candidate.getStatus() == ExhibitionPackageStatus.ACTIVE)
.findFirst()
.orElse(null);
if (exhibitionPackage == null) {
log.warn("[SEED][BULK] Chưa có exhibition package cho '{}'; bỏ qua tạo gian hàng.",
exhibitionName);
return;
}

int created = 0;
int position = 0;
for (int companyIndex : companyIndices) {
String email = "exhibitor" + companyIndex + "@vex360.local";
User owner = userRepository.findByEmail(email).orElse(null);
Company company = owner == null ? null
: companyRepository.findByOwnerUserId(owner.getId()).orElse(null);
if (owner == null || company == null) {
continue;
}

UUID registrationUuid = UUID
.nameUUIDFromBytes((seedKeyPrefix + "-registration-" + email)
.getBytes(StandardCharsets.UTF_8));
UUID boothUuid = UUID.nameUUIDFromBytes(
(seedKeyPrefix + "-booth-" + email).getBytes(StandardCharsets.UTF_8));
String boothName = boothNamePrefix + " " + company.getName();
String boothDescription = String.format(boothDescriptionTemplate,
company.getName());

ExhibitorRegistration registration =
ensureApprovedRegistration(registrationUuid,
exhibitionPackage, company, admin, participationReason,
boothName, boothDescription);
Booth booth = ensurePublishedBooth(boothUuid, owner, company, registration,
boothName, boothDescription, "modern");

String thumbnailImageUrl = thumbnailImageUrls[position %
thumbnailImageUrls.length];
Uploaded boothThumb = cachedUpload(thumbnailImageUrl, "seed/booth");
booth.setThumbnailUrl(boothThumb.url());
booth.setThumbnailPublicId(boothThumb.publicId());
boothRepository.save(booth);

String panoramaImageUrl = panoramaImageUrls[position %
panoramaImageUrls.length];
ensureBoothTourContentWithImage(booth, "Không gian " + boothName,
String.format(panoramaHotspotInfoTemplate, company.getName()),
panoramaImageUrl);
position++;
created++;
}
log.info("[SEED][BULK] Đã bảo đảm {} gian hàng PUBLISHED cho '{}' (ảnh thumbnail + panorama xoay vòng)",
created, exhibitionName);
}

        /**
         * Cache upload trong 1 lần seed để không upload trùng cùng 1 URL nhiều lần
         * lên Cloudinary.
         */
        private Uploaded cachedUpload(String remoteUrl, String folder) {
                return uploadCache.computeIfAbsent(remoteUrl, url -> upload(url, folder));
        }

        /**
         * Giống {@link #ensureBoothTourContent}, nhưng luôn cập nhật panorama theo
         * đúng {@code imageUrl} truyền vào (kể cả khi panorama đã tồn tại), để có
         * thể "vá" ảnh khác nhau cho các gian hàng đã seed từ trước.
         */
        private void ensureBoothTourContentWithImage(Booth booth, String panoramaName, String hotspotInfo,
                        String imageUrl) {
                Uploaded img = cachedUpload(imageUrl, "seed/panorama");
                Panorama panorama = panoramaRepository.findByBoothIdOrderByOrderIndexAsc(booth.getId())
                                .stream()
                                .findFirst()
                                .orElse(null);
                if (panorama == null) {
                        panorama = Panorama.builder()
                                        .booth(booth).name(panoramaName)
                                        .imageUrl(img.url()).imageKey(img.publicId())
                                        .orderIndex(0).isDefault(true).isTemplateDerived(false)
                                        .build();
                } else {
                        panorama.setName(panoramaName);
                        panorama.setImageUrl(img.url());
                        panorama.setImageKey(img.publicId());
                }
                panorama = panoramaRepository.save(panorama);
                if (hotspotRepository.countBySourcePanoramaBoothId(booth.getId()) == 0) {
                        hotspotRepository.save(Hotspot.builder()
                                        .type(HotspotType.INFO)
                                        .name("Giới thiệu gian hàng")
                                        .sourcePanorama(panorama)
                                        .infoText(hotspotInfo)
                                        .infoContentType(HotspotInfoContentType.TEXT)
                                        .xPosition(0.0)
                                        .yPosition(20.0)
                                        .zPosition(-350.0)
                                        .iconStyle("classic")
                                        .scale(1.0)
                                        .zIndex(1)
                                        .build());
                }
        }

        private static int[] intRange(int startInclusive, int endInclusive) {
                int[] values = new int[endInclusive - startInclusive + 1];
                for (int i = 0; i < values.length; i++) {
                        values[i] = startInclusive + i;
                }
                return values;
        }

        private void ensureBulkDemoTravelBooths() {
                removeExcessTravelBooths();
                ensureBulkBoothsForExhibition(
                                "Triển lãm Du lịch & Lữ hành 2026",
                                intRange(5, 14),
                                "travel",
                                "Gian hàng Du lịch",
                                "Gian hàng giới thiệu tour du lịch và dịch vụ lữ hành của %s.",
                                "Đăng ký gian hàng trưng bày dịch vụ du lịch tại triển lãm.",
                                TRAVEL_BOOTH_THUMBS,
                                TRAVEL_BOOTH_PANORAMAS,
                                "Khám phá các gói tour và dịch vụ lữ hành của %s.");
        }

        private void removeExcessTravelBooths() {
                for (int companyIndex = 15; companyIndex <= 28; companyIndex++) {
                        String email = "exhibitor" + companyIndex + "@vex360.local";
                        UUID boothId = UUID.nameUUIDFromBytes(
                                        ("travel-booth-" + email).getBytes(StandardCharsets.UTF_8));
                        UUID registrationId = UUID.nameUUIDFromBytes(
                                        ("travel-registration-" + email).getBytes(StandardCharsets.UTF_8));
                        boothRepository.findById(boothId).ifPresent(boothRepository::delete);
                        boothRepository.flush();
                        exhibitorRegistrationRepository.findByUuid(registrationId)
                                        .ifPresent(exhibitorRegistrationRepository::delete);
                }
        }

private void ensureTravelDesignerFixtures() {
String[][] designerData = {
{ "designer2@vex360.local", "Nguyễn Minh Anh", "0940000001" },
{ "designer3@vex360.local", "Trần Gia Huy", "0940000002" },
{ "designer4@vex360.local", "Lê Hoàng Yến", "0940000003" },
{ "designer5@vex360.local", "Phạm Đức Long", "0940000004" },
{ "designer6@vex360.local", "Vũ Khánh Linh", "0940000005" },
{ "designer7@vex360.local", "Đặng Quốc Việt", "0940000006" },
};
User[] designers = new User[designerData.length];
for (int i = 0; i < designerData.length; i++) {
int designerIndex = i;
int avatarIndex = 70 + i;
designers[i] = userRepository.findByEmail(designerData[i][0])
.orElseGet(() -> createUser(designerData[designerIndex][0],
designerData[designerIndex][1], Role.DESIGNER,
designerData[designerIndex][2], avatarIndex));
}

int[] companyIndices = { 5, 6, 7, 8, 9, 10 };
DesignRequestStatus[] statuses = {
DesignRequestStatus.PENDING,
DesignRequestStatus.PENDING,
DesignRequestStatus.PENDING,
DesignRequestStatus.ASSIGNED,
DesignRequestStatus.DRAFT_SUBMITTED,
DesignRequestStatus.REVISION_REQUESTED,
};
for (int i = 0; i < companyIndices.length; i++) {
String ownerEmail = "exhibitor" + companyIndices[i] + "@vex360.local";
UUID boothId = UUID.nameUUIDFromBytes(
("travel-booth-" + ownerEmail).getBytes(StandardCharsets.UTF_8));
Booth booth = boothRepository.findById(boothId).orElse(null);
if (booth == null) {
continue;
}

String note = i < 3
? "[SEED][TRAVEL-DESIGN] Chờ phân công - " + ownerEmail
: "[SEED][TRAVEL-DESIGN] Đã phân công - " + ownerEmail;
DesignRequest request = findTravelDesignRequest(boothId, note);
boolean isNew = request == null;
if (isNew) {
request = DesignRequest.builder().build();
}
request.setBooth(booth);
request.setCompany(booth.getCompany());
request.setRequestedBy(booth.getCreatedBy());
request.setAssignedDesigner(i < 3 ? null : designers[i - 3]);
request.setStatus(statuses[i]);
request.setMode(DesignRequestMode.REDESIGN);
request.setNote(note);
request.setReviewCount(i == 5 ? 1 : 0);
request.setQuotaCharged(true);
request.setCancellationStatus(DesignRequestCancellationStatus.NONE);
request.setAssignedAt(i < 3 ? null : Instant.now().minus(i - 2L,
ChronoUnit.DAYS));
booth.setStatus(i < 3 ? BoothStatus.DESIGN_REQUEST_PENDING :
BoothStatus.DESIGNING);
boothRepository.save(booth);
request = designRequestRepository.save(request);
if (isNew && i >= 3) {
designRequestBaselineService.createWorkingBaseline(request);
designRequestRepository.save(request);
}
}
log.info("[SEED][TRAVEL-DESIGN] Đã bảo đảm 6 designer, 3 yêu cầu chờ phân công "
+ "và 3 yêu cầu đã phân công cho gian hàng Du lịch.");
}

private DesignRequest findTravelDesignRequest(UUID boothId, String note) {
return entityManager.createQuery(
"SELECT dr FROM DesignRequest dr WHERE dr.booth.id = :boothId AND dr.note = :note",
DesignRequest.class)
.setParameter("boothId", boothId)
.setParameter("note", note)
.getResultStream()
.findFirst()
.orElse(null);
}

private void ensureBulkDemoGreenTechBooths() {
ensureBulkBoothsForExhibition(
"Triển lãm Công nghệ Xanh 2026",
intRange(5, 34),
"greentech",
"Gian hàng Công nghệ Xanh",
"Gian hàng giới thiệu giải pháp công nghệ xanh và năng lượng tái tạo của %s.",
"Đăng ký gian hàng trưng bày giải pháp công nghệ xanh tại triển lãm.",
GREEN_TECH_BOOTH_THUMBS,
GREEN_TECH_BOOTH_PANORAMAS,
"Khám phá giải pháp công nghệ xanh và năng lượng tái tạo của %s.");
}

        private void ensureBulkDemoFurnitureTechBooths() {
                ensureBulkBoothsForExhibition(
                                "Triển lãm Nội thất & Công nghệ VEX360 2026",
                                intRange(5, 31),
                                "furniture-tech",
                                "Gian hàng Nội thất",
                                "Gian hàng trưng bày sản phẩm nội thất và công nghệ nhà thông minh của %s.",
                                "Đăng ký gian hàng trưng bày sản phẩm nội thất và công nghệ tại triển lãm.",
                                FURNITURE_BOOTH_THUMBS,
                                FURNITURE_BOOTH_PANORAMAS,
                                "Khám phá bộ sưu tập nội thất và giải pháp công nghệ của %s.");
        }

        /**
         * Bảo đảm mọi triển lãm đang REGISTRATION/PUBLISHED/ACTIVE/COMPLETED có
         * 1-3 nhà tài trợ (SPONSOR_LOGO) và ít nhất 2 exhibition package. Triển
         * lãm PENDING/REJECTED không tính vì chưa được duyệt chính thức.
         */
        private void ensureBulkDemoSponsors() {
                String[] exhibitionNames = {
                                "Triển lãm Nội thất & Công nghệ VEX360 2026",
                                "Triển lãm Công nghệ Xanh 2026",
                                "Triển lãm Vật liệu Xây dựng 2026",
                                "Triển lãm Ngoại thất & Sân vườn Sao Việt 2026",
                                "Triển lãm Đồ gia dụng thông minh 2026",
                                "Triển lãm Thời trang & Làm đẹp 2026",
                                "Triển lãm Ẩm thực & Đồ uống 2026",
                                "Triển lãm Trang sức & Phụ kiện 2026",
                                "Triển lãm Nội thất Văn phòng 2026",
                                "Triển lãm Thiết bị Y tế 2026",
                                "Triển lãm Giáo dục & Đào tạo 2026",
                                "Triển lãm Du lịch & Lữ hành 2026",
                                "Triển lãm Cơ khí & Tự động hóa 2026",
                                "Triển lãm Thủy sản Việt Nam 2025",
                                "Triển lãm Nông nghiệp Công nghệ cao 2026",
                };
                int sponsorCursor = 0;
                int touchedExhibitions = 0;
                for (int i = 0; i < exhibitionNames.length; i++) {
                        Exhibition exhibition = findExhibitionByName(exhibitionNames[i]);
                        if (exhibition == null) {
                                continue;
                        }
                        touchedExhibitions++;
                        int targetSponsorCount = (i % 3) + 1;
                        List<ExhibitionAsset> existingSponsors = findExhibitionAssets(
                                        exhibition.getId(), ExhibitionAssetType.SPONSOR_LOGO);
                        for (int toAdd = targetSponsorCount - existingSponsors.size(); toAdd > 0; toAdd--) {
                                String sponsorName = SPONSOR_NAMES[sponsorCursor % SPONSOR_NAMES.length];
                                sponsorCursor++;
                                Uploaded logo = upload(letterLogo(sponsorName), "seed/exhibition");
                                exhibitionAssetRepository.save(ExhibitionAsset.builder().exhibition(exhibition)
                                                .assetUrl(logo.url()).publicId(logo.publicId())
                                                .name(sponsorName)
                                                .type(ExhibitionAssetType.SPONSOR_LOGO).build());
                        }
                }
                log.info("[SEED][BULK] Đã bảo đảm 1-3 nhà tài trợ cho {} triển lãm "
                                + "REGISTRATION/PUBLISHED/ACTIVE/COMPLETED", touchedExhibitions);

                ensureExtraPackageForOtherOrganizerExhibition();
        }

/**
* "Triển lãm Ngoại thất & Sân vườn Sao Việt 2026" (organizer thứ hai) chỉ
* có sẵn 1 exhibition package; thêm 1 gói tier Khởi Nghiệp để đạt tối
* thiểu 2 gói dịch vụ.
*/
private void ensureExtraPackageForOtherOrganizerExhibition() {
Exhibition exhibition = findExhibitionByName("Triển lãm Ngoại thất & Sân vườn Sao Việt 2026");
PackageTemplate tier1 = findPackageTemplateByName("Gói Khởi Nghiệp - Theo Triển Lãm");
if (exhibition == null || tier1 == null) {
return;
}
ensureExhibitionPackage(exhibition, tier1, "10000");
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
                }
        }

        // ================= Helpers =================

        private User createUser(String email, String fullName, Role role, String phone, int avatarIndex) {
                return createUser(email, fullName, role, phone, avatarIndex,
                                UserStatus.ACTIVE, AuthProvider.LOCAL);
        }

        private User createUser(String email, String fullName, Role role, String phone, int avatarIndex,
                        UserStatus status, AuthProvider provider) {
                Uploaded avatar = upload("https://i.pravatar.cc/400?img=" + avatarIndex,
                                "seed/avatar");
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

        private Company createCompany(User owner, String name, String industry,
                        String description, String logoSeed) {
                return createCompany(owner, name, industry, description, logoSeed,
                                CompanyStatus.ACTIVE);
        }

private Company createCompany(User owner, String name, String industry,
String description, String logoSeed,
CompanyStatus status) {
// Công ty chưa hoàn thiện hồ sơ thì để trống các trường tuỳ chọn cho đúng
// thực
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

        private ExhibitionPackage ensureExhregPackage(
                        PackageTemplate template,
                        Exhibition exhibition,
                        String finalPrice) {
                ExhibitionPackage exhibitionPackage = exhibitionPackageRepository
                                .findByExhibitionIdAndTemplateId(exhibition.getId(), template.getId())
                                .orElseGet(() -> ExhibitionPackage.builder()
                                                .exhibition(exhibition)
                                                .build());
                if (exhibitionPackage.getPackageNameSnapshot() == null) {
                        exhibitionPackage.snapshotTemplateTerms(template);
                }
                exhibitionPackage.setFinalPrice(new BigDecimal(finalPrice));
                exhibitionPackage.setStatus(ExhibitionPackageStatus.ACTIVE);
                return exhibitionPackageRepository.save(exhibitionPackage);
        }

        private Booth insertBoothFixtureShell(UUID id, User createdBy) {
                entityManager.flush();
                entityManager.createNativeQuery("""
                                INSERT INTO booths (
                                    id, name, status, is_template, created_by_id,
                                    display_template_key, created_at, updated_at
                                ) VALUES (
                                    :id, 'Booth shell', 'DRAFT', false, :createdById,
                                    'classic', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                                )
                                """)
                                .setParameter("id", id)
                                .setParameter("createdById", createdBy.getId())
                                .executeUpdate();
                return boothRepository.findById(id)
                                .orElseThrow(() -> new IllegalStateException("Không thể tạo booth fixture " +
                                                id));
        }

        private Exhibition saveExhibition(User organizer, String name, String category, String description,
                        LocalDate startDate, LocalDate endDate, int estimatedBooths,
                        ExhibitionStatus status, User reviewedBy, String rejectedReason) {
                return saveExhibition(null, organizer, name, category, description,
                                startDate, endDate,
                                estimatedBooths, status, reviewedBy, rejectedReason);
        }

        private Exhibition saveExhibition(UUID uuid, User organizer, String name,
                        String category, String description,
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
                return savePackage(template, exhibition, finalPrice,
                                ExhibitionPackageStatus.ACTIVE);
        }

        private ExhibitionPackage savePackage(PackageTemplate template, Exhibition exhibition, String finalPrice,
                        ExhibitionPackageStatus status) {
                ExhibitionPackage exhibitionPackage = ExhibitionPackage.builder()
                                .exhibition(exhibition)
                                .finalPrice(new BigDecimal(finalPrice))
                                .status(status).build();
                exhibitionPackage.snapshotTemplateTerms(template);
                return exhibitionPackageRepository.save(exhibitionPackage);
        }

        private ExhibitorRegistration buildRegistration(ExhibitionPackage pkg,
                        Company company,
                        ExhibitorRegistrationStatus status, User reviewedBy,
                        String reason, String rejectedReason) {
                return buildRegistration(pkg, company, status, reviewedBy, reason,
                                rejectedReason,
                                "Gian hàng " + company.getName(),
                                "Mô tả gian hàng " + company.getName() + " tại triển lãm.");
        }

        private ExhibitorRegistration buildRegistration(ExhibitionPackage pkg,
                        Company company,
                        ExhibitorRegistrationStatus status, User reviewedBy,
                        String reason, String rejectedReason, String boothName, String boothDescription) {
                return ExhibitorRegistration.builder()
                                .exhibitionPackage(pkg).company(company).status(status)
                                .reviewedBy(reviewedBy).participationReason(reason)
                                .rejectedReason(rejectedReason)
                                .boothName(boothName != null ? boothName : "Gian hàng " + company.getName())
                                .boothDescription(boothDescription != null ? boothDescription
                                                : "Mô tả gian hàng " + company.getName())
                                .packageNameSnapshot(pkg.getPackageNameSnapshot())
                                .priceSnapshot(pkg.getPriceSnapshot())
                                .finalPriceSnapshot(pkg.getFinalPrice())
                                .currencySnapshot(pkg.getCurrencySnapshot())
                                .maxProductsPerBoothSnapshot(pkg.getMaxProductsPerBoothSnapshot())
                                .maxEmbeddedVideosPerBoothSnapshot(pkg.getMaxEmbeddedVideosPerBoothSnapshot())
                                .maxPanoramasPerBoothSnapshot(pkg.getMaxPanoramasPerBoothSnapshot())
                                .maxHotspotsPerBoothSnapshot(pkg.getMaxHotspotsPerBoothSnapshot())
                                .listingPrioritySnapshot(pkg.getListingPrioritySnapshot())
                                .build();
        }

/**
* Backfill idempotent nội dung tối thiểu để mọi booth demo đang PUBLISHED
* đều có panorama và ít nhất một hotspot PRODUCT trỏ tới sản phẩm ACTIVE
* của chính company sở hữu booth.
*/
private void ensurePublishedBoothsHaveDisplayableProductHotspot() {
List<Booth> publishedBooths = entityManager.createQuery(
"SELECT b FROM Booth b WHERE b.status = :status "
+ "AND b.isTemplate = false AND b.company IS NOT NULL",
Booth.class)
.setParameter("status", BoothStatus.PUBLISHED)
.getResultList();

int createdPanoramas = 0;
int createdProductHotspots = 0;
int imageIndex = 0;
for (Booth booth : publishedBooths) {
Panorama panorama =
panoramaRepository.findByBoothIdOrderByOrderIndexAsc(booth.getId())
.stream()
.findFirst()
.orElse(null);
if (panorama == null) {
panorama = savePanorama(booth, "Sảnh chính", imageIndex++, 0, true);
createdPanoramas++;
}

Long activeProductHotspotCount = entityManager.createQuery(
"SELECT COUNT(h) FROM Hotspot h "
+ "WHERE h.sourcePanorama.booth = :booth "
+ "AND h.type = :type AND h.product.status = :productStatus "
+ "AND h.product.company = :company",
Long.class)
.setParameter("booth", booth)
.setParameter("type", HotspotType.PRODUCT)
.setParameter("productStatus", ProductStatus.ACTIVE)
.setParameter("company", booth.getCompany())
.getSingleResult();
if (activeProductHotspotCount > 0) {
continue;
}

List<Product> products = entityManager.createQuery(
"SELECT p FROM Product p WHERE p.company = :company "
+ "AND p.status = :status ORDER BY p.createdAt ASC",
Product.class)
.setParameter("company", booth.getCompany())
.setParameter("status", ProductStatus.ACTIVE)
.setMaxResults(1)
.getResultList();
if (products.isEmpty()) {
log.warn("[SEED] Booth {} chưa có sản phẩm ACTIVE nên chưa thể tạo hotspot PRODUCT.",
booth.getId());
continue;
}

Product product = products.getFirst();
hotspotRepository.save(Hotspot.builder()
.type(HotspotType.PRODUCT)
.name(product.getName())
.sourcePanorama(panorama)
.product(product)
.xPosition(180.0)
.yPosition(-25.0)
.zPosition(-320.0)
.iconStyle("tag")
.scale(1.0)
.zIndex(10)
.build());
createdProductHotspots++;
}

log.info("[SEED] Đã bảo đảm {} booth PUBLISHED có nội dung hiển thị "
+ "(thêm {} panorama, {} hotspot PRODUCT).",
publishedBooths.size(), createdPanoramas, createdProductHotspots);
}

        private Panorama savePanorama(Booth booth, String name, int imageIndex, int orderIndex, boolean isDefault) {
                Uploaded img = upload(PANORAMA_IMAGES[imageIndex % PANORAMA_IMAGES.length],
                                "seed/panorama");
                return panoramaRepository.save(Panorama.builder()
                                .booth(booth).name(name)
                                .imageUrl(img.url()).imageKey(img.publicId())
                                .orderIndex(orderIndex).isDefault(isDefault).isTemplateDerived(false)
                                .build());
        }

        private ProductCategory saveCategory(Company company, String name, String description) {
                return saveCategory(company, name, description,
                                ProductCategoryStatus.ACTIVE);
        }

        private ProductCategory saveCategory(Company company, String name, String description,
                        ProductCategoryStatus status) {
                return productCategoryRepository.save(ProductCategory.builder()
                                .company(company).name(name).description(description)
                                .status(status).build());
        }

        private Product saveProduct(Company company, ProductCategory category, String name, String sku,
                        String description, BigDecimal price, String imageUrl) {
                return saveProduct(company, category, name, sku, description, price,
                                imageUrl,
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
        private void seedAnalyticsEvent(AnalyticsEventType type, User user,
                        Exhibition exhibition,
                        Booth booth, Product product, Integer durationSeconds, Instant when) {
                seedAnalyticsEvent(type, user, exhibition, booth, product, durationSeconds,
                                when, null);
        }

private void seedAnalyticsEvent(AnalyticsEventType type, User user,
Exhibition exhibition,
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
        private int seedBoothClicks(User user, Exhibition exhibition, Booth booth,
                        AnalyticsEventType type,
                        String clickableName, Product product, int count) {
                String metadata = clickMetadata(clickableName);
                ZoneId zone = ZoneId.systemDefault();
                for (int c = 0; c < count; c++) {
                        Instant when = Instant.now().minus(1 + (c % 6), ChronoUnit.DAYS)
                                        .atZone(zone)
                                        .withHour(9 + (c % 10)).withMinute(c % 60).withSecond(0).withNano(0)
                                        .toInstant();
                        seedAnalyticsEvent(type, user, exhibition, booth, product, null, when,
                                        metadata);
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
                        Map<?, ?> params = ObjectUtils.asMap("folder", folder, "resource_type",
                                        "auto");
                        Object source = remoteUrl.startsWith("classpath:")
                                        ? new ClassPathResource(remoteUrl.substring("classpath:".length()))
                                                        .getInputStream().readAllBytes()
                                        : remoteUrl;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> result = cloudinary.uploader().upload(source, params);

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
                        return new Uploaded(remoteUrl, "seed/placeholder-" +
                                        Math.abs(remoteUrl.hashCode()), 0L);
                }
        }
}
