package vacademy.io.admin_core_service.features.packages.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vacademy.io.common.institute.entity.LevelProjection;
import vacademy.io.common.institute.entity.PackageEntity;
import vacademy.io.common.institute.entity.session.PackageSession;
import vacademy.io.common.institute.entity.session.SessionProjection;

import java.util.List;

@Repository
public interface PackageRepository extends JpaRepository<PackageEntity, String> {

    // Get all distinct sessions of an institute_id
    @Query(value = "SELECT DISTINCT s.* FROM session s " +
            "JOIN package_session ps ON s.id = ps.session_id " +
            "JOIN package p ON ps.package_id = p.id " +
            "JOIN package_institute pi ON p.id = pi.package_id " +  // Ensure to join package_institute to filter by institute
            "WHERE pi.institute_id = :instituteId",
            nativeQuery = true)
    List<SessionProjection> findDistinctSessionsByInstituteId(@Param("instituteId") String instituteId);


    @Query(value = "SELECT DISTINCT l.* FROM level l " +
            "JOIN package_session ps ON l.id = ps.level_id " +
            "JOIN package p ON ps.package_id = p.id " +
            "JOIN package_institute pi ON p.id = pi.package_id " +  // Ensure to join package_institute to filter by institute
            "WHERE pi.institute_id = :instituteId AND l.status = 'ACTIVE' ",
            nativeQuery = true)
    List<LevelProjection> findDistinctLevelsByInstituteId(@Param("instituteId") String instituteId);

    // Get all distinct packages of an institute_id
    @Query(value = "SELECT DISTINCT p.* FROM package p " +
            "JOIN package_institute pi ON p.id = pi.package_id " +  // Ensure to join package_institute to filter by institute
            "WHERE pi.institute_id = :instituteId",
            nativeQuery = true)
    List<PackageEntity> findDistinctPackagesByInstituteId(@Param("instituteId") String instituteId);

    // Get all package sessions of an institute_id and of a session_id
    @Query(value = "SELECT ps.id, ps.level_id, ps.session_id, ps.start_time, ps.updated_at, ps.created_at, ps.status, ps.package_id " +
            "FROM package_session ps " +
            "JOIN package p ON ps.package_id = p.id " +
            "JOIN package_institute pi ON p.id = pi.package_id " +
            "WHERE pi.institute_id = :instituteId",
            nativeQuery = true)
    List<PackageSession> findPackageSessionsByInstituteId(
            @Param("instituteId") String instituteId);

}