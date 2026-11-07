package team.projectpulse.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface StudentRepository extends JpaRepository<Student, Integer>, JpaSpecificationExecutor<Student> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from Student s
            where s.id = :studentId and
              (s.section.course.courseAdmin.id = :actorId or
               exists (select i.id from Section sec join sec.instructors i
                       where sec = s.section and i.id = :actorId))
            """)
    Optional<Student> findForLifecycle(@Param("studentId") Integer studentId, @Param("actorId") Integer actorId);

    Optional<Student> findByIdAndTeamTeamId(Integer id, Integer teamId);

    // Find all students in a section
    List<Student> findBySectionSectionId(Integer sectionId);

    List<Student> findByTeamTeamId(Integer teamId);
}
