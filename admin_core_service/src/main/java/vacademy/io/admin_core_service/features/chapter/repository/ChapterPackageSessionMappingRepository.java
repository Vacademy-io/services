package vacademy.io.admin_core_service.features.chapter.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vacademy.io.admin_core_service.features.chapter.entity.Chapter;
import vacademy.io.admin_core_service.features.chapter.entity.ChapterPackageSessionMapping;

import java.util.List;
import java.util.Optional;

public interface ChapterPackageSessionMappingRepository extends JpaRepository<ChapterPackageSessionMapping, String> {

    // Fetch all mappings by Chapter
    @Query("SELECT cpsm FROM ChapterPackageSessionMapping cpsm WHERE cpsm.chapter = :chapter AND cpsm.status != 'DELETED'")
    List<ChapterPackageSessionMapping> findByChapter(Chapter chapter);

    @Query("SELECT cpsm FROM ChapterPackageSessionMapping cpsm " +
            "WHERE cpsm.chapter.id IN :chapterIds " +
            "AND cpsm.packageSession.id IN :packageSessionIds " +
            "AND cpsm.status != 'DELETED'")
    List<ChapterPackageSessionMapping> findByChapterIdInAndPackageSessionIdIn(
            @Param("chapterIds") List<String> chapterIds,
            @Param("packageSessionIds") List<String> packageSessionIds);

}
