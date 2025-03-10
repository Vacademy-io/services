package vacademy.io.common.auth.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vacademy.io.common.auth.dto.RoleCountProjection;
import vacademy.io.common.auth.entity.User;
import vacademy.io.common.auth.entity.UserRole;

import java.util.List;

@Repository
public interface UserRoleRepository extends CrudRepository<UserRole, String> {

    List<UserRole> findByUser(User user);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserRole ur WHERE ur.user.id = :userId AND ur.role.id IN :roleIds")
    void deleteUserRolesByUserIdAndRoleIds(@Param("userId") String userId, @Param("roleIds") List<String> roleIds);

    @Query("SELECT r.name AS roleName, COUNT(ur.user.id) AS userCount " +
            "FROM UserRole ur JOIN ur.role r " +
            "WHERE ur.instituteId = :instituteId and r.name != 'STUDENT' " +
            "GROUP BY r.name")
    List<RoleCountProjection> getUserRoleCountsByInstituteId(@Param("instituteId") String instituteId);

}