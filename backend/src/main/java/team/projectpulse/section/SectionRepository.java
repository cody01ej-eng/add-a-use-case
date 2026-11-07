package team.projectpulse.section;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SectionRepository extends JpaRepository<Section, Integer>, JpaSpecificationExecutor<Section> {
    @Query("""
            select s from Section s where s.sectionId = :sectionId and
              (s.course.courseAdmin.id = :actorId or
               exists (select i.id from Section sec join sec.instructors i
                       where sec = s and i.id = :actorId))
            """)
    Optional<Section> findForEvaluationReport(@Param("sectionId") Integer sectionId, @Param("actorId") Integer actorId);

    /**
     * Sections that are active, explicitly list the given ISO week key in activeWeeks.
     * Students are loaded for emailing; activeWeeks are NOT fetched (filtered in SQL).
     */
    @EntityGraph(attributePaths = {"students"}) // only fetch students
    @Query("""
            select s
            from Section s
            where s.isActive = true
              and :weekKey member of s.activeWeeks
            """)
    List<Section> findReminderEligibleSectionsForWeek(@Param("weekKey") String weekKey);

}
