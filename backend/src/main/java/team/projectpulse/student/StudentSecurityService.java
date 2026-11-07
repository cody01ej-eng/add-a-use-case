package team.projectpulse.student;

import team.projectpulse.system.UserUtils;
import team.projectpulse.course.CourseSecurityService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

/**
 * Answers the questions a route asks about one named student: is that student the caller herself
 * ({@code isStudentSelf}), and is the caller an instructor of that student's course section
 * ({@code isCurrentUserInstructorOfStudentSection}).
 *
 * <p>Every check fails closed: an id that names no row, or a link not yet established, is denied rather than
 * throwing. These methods run inside an {@code AuthorizationManager}, upstream of the {@code DispatcherServlet},
 * so an exception thrown here escapes the filter chain as a 500 that also tells the caller which ids exist.
 */
@Service
@Transactional
public class StudentSecurityService {

    private final StudentRepository studentRepository;
    private final UserUtils userUtils;
    private final CourseSecurityService courseSecurityService;


    public StudentSecurityService(StudentRepository studentRepository, UserUtils userUtils, CourseSecurityService courseSecurityService) {
        this.studentRepository = studentRepository;
        this.userUtils = userUtils;
        this.courseSecurityService = courseSecurityService;
    }

    public boolean canDeactivateStudent(Integer studentId) {
        try {
            boolean admin = this.userUtils.hasRole("ROLE_admin");
            boolean instructor = this.userUtils.hasRole("ROLE_instructor");
            if (studentId == null || (!admin && !instructor)) return false;
            Student student = this.studentRepository.findById(studentId).orElse(null);
            if (student == null) return false;
            return (admin && this.courseSecurityService.isCourseOwner(student.getSection().getCourse().getCourseId()))
                    || (instructor && isCurrentUserInstructorOfStudentSection(studentId));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public boolean isStudentSelf(Integer studentId) {
        Integer studentIdFromJwt = this.userUtils.getUserId();
        return studentId.equals(studentIdFromJwt);
    }

    /**
     * Check if the user is the instructor of the section that the student is in
     *
     * @param studentId the id of the student we need to access, this is NOT the current user's Id
     * @return
     */
    public boolean isCurrentUserInstructorOfStudentSection(Integer studentId) {
        Integer instructorIdFromJwt = this.userUtils.getUserId();
        Student student = this.studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            return false;
        }
        return student.getSection().getInstructors().stream()
                .anyMatch(instructor -> instructor.getId().equals(instructorIdFromJwt));
    }

}
