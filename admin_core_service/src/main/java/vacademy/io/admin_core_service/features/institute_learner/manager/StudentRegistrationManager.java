package vacademy.io.admin_core_service.features.institute_learner.manager;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vacademy.io.admin_core_service.features.institute_learner.constants.StudentConstants;
import vacademy.io.admin_core_service.features.institute_learner.dto.InstituteStudentDTO;
import vacademy.io.admin_core_service.features.institute_learner.dto.InstituteStudentDetails;
import vacademy.io.admin_core_service.features.institute_learner.dto.StudentExtraDetails;
import vacademy.io.admin_core_service.features.institute_learner.entity.Student;
import vacademy.io.admin_core_service.features.institute_learner.repository.InstituteStudentRepository;
import vacademy.io.admin_core_service.features.institute_learner.repository.StudentSessionRepository;
import vacademy.io.common.auth.dto.UserDTO;
import vacademy.io.common.auth.model.CustomUserDetails;
import vacademy.io.common.core.internal_api_wrapper.InternalClientUtils;
import vacademy.io.common.core.utils.RandomGenerator;
import vacademy.io.common.exceptions.VacademyException;

import java.util.*;

@Component
public class StudentRegistrationManager {

    @Autowired
    InternalClientUtils internalClientUtils;

    @Autowired
    InstituteStudentRepository instituteStudentRepository;

    @Autowired
    StudentSessionRepository studentSessionRepository;

    @Value("${auth.server.baseurl}")
    private String authServerBaseUrl;
    @Value("${spring.application.name}")
    private String applicationName;


    public ResponseEntity<String> addStudentToInstitute(CustomUserDetails user, InstituteStudentDTO instituteStudentDTO) {
        Student student = checkAndCreateStudent(instituteStudentDTO);
        linkStudentToInstitute(student, instituteStudentDTO.getInstituteStudentDetails());
        return ResponseEntity.ok("Student added successfully");
    }


    private UserDTO createUserFromAuthService(InstituteStudentDTO instituteStudentDTO) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ResponseEntity<String> response = internalClientUtils.makeHmacRequest(applicationName, HttpMethod.POST.name(), authServerBaseUrl, StudentConstants.addUserRoute + "?instituteId=" + instituteStudentDTO.getInstituteStudentDetails().getInstituteId(), instituteStudentDTO.getUserDetails());
            return objectMapper.readValue(response.getBody(), UserDTO.class);

        } catch (Exception e) {
            throw new VacademyException(e.getMessage());
        }
    }

    private Student checkAndCreateStudent(InstituteStudentDTO instituteStudentDTO) {
        instituteStudentDTO.getUserDetails().setRoles(getStudentRoles());
        setRandomPasswordIfNull(instituteStudentDTO.getUserDetails());
        UserDTO createdUser = createUserFromAuthService(instituteStudentDTO);
        return createStudentFromRequest(createdUser, instituteStudentDTO.getStudentExtraDetails());
    }

    private void setRandomPasswordIfNull(UserDTO userDTO) {
        if (!StringUtils.hasText(userDTO.getPassword())) {
            userDTO.setPassword(RandomGenerator.generatePassword(6));
        }
    }

    private Student createStudentFromRequest(UserDTO userDTO, StudentExtraDetails studentExtraDetails) {
        Student student = new Student();
        Optional<Student> existingStudent = getExistingStudentByUserNameAndUserId(userDTO.getUsername(), userDTO.getId());
        if (existingStudent.isPresent()) {
            student = existingStudent.get();
        }
        student.setUserId(userDTO.getId());
        student.setUsername(userDTO.getUsername());
        student.setFullName(userDTO.getFullName());
        student.setEmail(userDTO.getEmail());
        student.setMobileNumber(userDTO.getMobileNumber());
        student.setAddressLine(userDTO.getAddressLine());
        student.setFaceFileId(userDTO.getProfilePicFileId());
        student.setCity(userDTO.getCity());
        student.setPinCode(userDTO.getPinCode());
        student.setGender(userDTO.getGender());
        student.setDateOfBirth(userDTO.getDateOfBirth());
        student.setFatherName(studentExtraDetails.getFathersName());
        student.setMotherName(studentExtraDetails.getMothersName());
        student.setParentsMobileNumber(studentExtraDetails.getParentsMobileNumber());
        student.setParentsEmail(studentExtraDetails.getParentsEmail());
        student.setLinkedInstituteName(studentExtraDetails.getLinkedInstituteName());
        return instituteStudentRepository.save(student);
    }

    private void linkStudentToInstitute(Student student, InstituteStudentDetails instituteStudentDetails) {

        try {
            UUID studentSessionId = UUID.randomUUID();

            studentSessionRepository.addStudentToInstitute(studentSessionId.toString(), student.getUserId(), instituteStudentDetails.getEnrollmentDate() == null ? new Date() : instituteStudentDetails.getEnrollmentDate(), instituteStudentDetails.getEnrollmentStatus(), instituteStudentDetails.getEnrollmentId(), instituteStudentDetails.getGroupId(), instituteStudentDetails.getInstituteId(), makeExpiryDate(instituteStudentDetails.getEnrollmentDate(), instituteStudentDetails.getAccessDays()), instituteStudentDetails.getPackageSessionId());
        } catch (Exception e) {
            throw new VacademyException(e.getMessage());
        }
    }

    private List<String> getStudentRoles() {
        List<String> roles = new ArrayList<>();
        roles.add(StudentConstants.studentRole);
        return roles;
    }

    private Date makeExpiryDate(Date enrollmentDate, String accessDays) {
        try {
            if (enrollmentDate == null || accessDays == null) {
                return null;
            }
            Date expiryDate = new Date();
            expiryDate.setTime(enrollmentDate.getTime() + Long.parseLong(accessDays) * 24 * 60 * 60 * 1000);
            return expiryDate;
        } catch (Exception e) {
        }
        return null;
    }

    public ResponseEntity<String> addStudentToInstituteWithoutUserEntry(CustomUserDetails user, InstituteStudentDTO instituteStudentDTO) {
        Student student = createStudentFromRequest(instituteStudentDTO.getUserDetails(), instituteStudentDTO.getStudentExtraDetails());
        linkStudentToInstitute(student, instituteStudentDTO.getInstituteStudentDetails());
        return ResponseEntity.ok("Student added successfully");
    }

    private Optional<Student> getExistingStudentByUserNameAndUserId(String username, String userId) {
        return instituteStudentRepository.findByUsernameAndUserId(username, userId);
    }
}
