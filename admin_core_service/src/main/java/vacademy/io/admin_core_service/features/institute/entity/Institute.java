package vacademy.io.admin_core_service.features.institute.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import vacademy.io.admin_core_service.features.institute.enums.HeldBy;
import vacademy.io.admin_core_service.features.institute.enums.InstituteType;
import vacademy.io.common.auth.enums.Gender;

import java.sql.Timestamp;

@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "institutes")
public class Institute {

    @Id
    @UuidGenerator
    @Column(name = "id")
    private String id;

    @Column(name = "name")
    private String instituteName;

    @Column(name = "address_line")
    private String address;

    @Column(name = "pin_code")
    private String pinCode;

    @Column(name = "mobile_number")
    private String mobileNumber;

    @Column(name = "logo_file_id")
    private String logoFileId;

    @Column(name = "language")
    private String Language;

    @Column(name = "institute_theme_code")
    private String instituteThemeCode;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "institute_type")
    private InstituteType instituteType;

    @Enumerated(EnumType.STRING)
    @Column(name = "held_by")
    private HeldBy heldBy;

    @Column(name = "founded_date")
    private Timestamp foundedData;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @Column(name = "country")
    private  String country;

    @Column(name = "state")
    private String state;

    @Column(name = "city")
    private String city;

    @Column(name = "email")
    private String email;




}