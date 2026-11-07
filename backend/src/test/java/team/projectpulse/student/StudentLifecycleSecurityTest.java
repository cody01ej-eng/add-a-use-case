package team.projectpulse.student;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.projectpulse.course.Course;
import team.projectpulse.course.CourseSecurityService;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.section.Section;
import team.projectpulse.system.UserUtils;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StudentLifecycleSecurityTest {
    @Mock StudentRepository studentRepository;
    @Mock UserUtils userUtils;
    @Mock CourseSecurityService courseSecurityService;
    @InjectMocks StudentSecurityService service;

    @Test @DisplayName("forbidden: a student cannot use the lifecycle action")
    void deniesStudent() { assertThat(service.canDeactivateStudent(4)).isFalse(); }

    @Test @DisplayName("authorized-deactivation: course owner does not need the target as default section")
    void allowsOwningCourseAdmin() {
        Student student = target();
        given(userUtils.hasRole(org.mockito.ArgumentMatchers.anyString())).willAnswer(call -> "ROLE_admin".equals(call.getArgument(0)));
        given(studentRepository.findById(4)).willReturn(Optional.of(student));
        given(courseSecurityService.isCourseOwner(8)).willReturn(true);
        assertThat(service.canDeactivateStudent(4)).isTrue();
    }

    @Test @DisplayName("authorized-deactivation: assigned instructor uses target's actual course section")
    void allowsAssignedInstructor() {
        Student student = target(); Instructor assigned = new Instructor(); assigned.setId(2);
        student.getSection().addInstructor(assigned);
        given(userUtils.hasRole(org.mockito.ArgumentMatchers.anyString())).willAnswer(call -> "ROLE_instructor".equals(call.getArgument(0)));
        given(userUtils.getUserId()).willReturn(2);
        given(studentRepository.findById(4)).willReturn(Optional.of(student));
        assertThat(service.canDeactivateStudent(4)).isTrue();
    }

    @Test @DisplayName("forbidden: an unrelated admin's global role is insufficient")
    void deniesUnrelatedAdmin() {
        given(userUtils.hasRole(org.mockito.ArgumentMatchers.anyString())).willAnswer(call -> "ROLE_admin".equals(call.getArgument(0)));
        given(studentRepository.findById(4)).willReturn(Optional.of(target()));
        assertThat(service.canDeactivateStudent(4)).isFalse();
    }

    @Test @DisplayName("unresolved-target: lookup failures deny without exception disclosure")
    void failsClosedOnLookupFailure() {
        given(userUtils.hasRole(org.mockito.ArgumentMatchers.anyString())).willAnswer(call -> "ROLE_instructor".equals(call.getArgument(0)));
        given(studentRepository.findById(4)).willThrow(new IllegalStateException("database diagnostic"));
        assertThat(service.canDeactivateStudent(4)).isFalse();
    }

    private Student target() {
        Course course = new Course(); course.setCourseId(8);
        Section section = new Section(); section.setCourse(course);
        Student student = new Student(); student.setSection(section); return student;
    }
}
