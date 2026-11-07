package team.projectpulse.student;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.system.UserUtils;
import team.projectpulse.system.exception.ObjectNotFoundException;
import team.projectpulse.team.Team;
import team.projectpulse.user.userinvitation.UserInvitationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentLifecycleServiceTest {
    @Mock StudentRepository studentRepository;
    @Mock SectionRepository sectionRepository;
    @Mock UserInvitationService userInvitationService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserUtils userUtils;
    @InjectMocks StudentService service;

    @Test
    @DisplayName("authorized-deactivation/repeat: disable only the account and preserve its team identity")
    void deactivatesOnlyEnabledAndRepeatsWithoutSaving() {
        Student student = new Student("john", "John", "Smith", "john@example.edu", "hash", true, "student");
        Team team = new Team(); student.setTeam(team);
        given(userUtils.getUserId()).willReturn(1);
        given(studentRepository.findForLifecycle(4, 1)).willReturn(Optional.of(student));
        StudentDeactivationResult first = service.deactivateStudent(4);
        assertThat(first.changed()).isTrue();
        assertThat(first.student()).isSameAs(student);
        assertThat(student.isEnabled()).isFalse();
        assertThat(student.getTeam()).isSameAs(team);
        assertThat(student.getEmail()).isEqualTo("john@example.edu");
        assertThat(service.deactivateStudent(4).changed()).isFalse();
        verify(studentRepository, times(1)).save(student);
    }

    @Test
    @DisplayName("no-team: an enrolled student can be deactivated without acquiring membership")
    void supportsStudentWithoutTeam() {
        Student student = new Student(); student.setEnabled(true);
        given(userUtils.getUserId()).willReturn(1);
        given(studentRepository.findForLifecycle(4, 1)).willReturn(Optional.of(student));
        assertThat(service.deactivateStudent(4).student().isEnabled()).isFalse();
        assertThat(student.getTeam()).isNull();
    }

    @Test
    @DisplayName("scoped-persistence: service alone refuses a target outside the actor's scope")
    void refusesUnscopedTargetWithoutMutation() {
        given(userUtils.getUserId()).willReturn(3);
        given(studentRepository.findForLifecycle(4, 3)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.deactivateStudent(4)).isInstanceOf(ObjectNotFoundException.class);
        verify(studentRepository, never()).save(any());
        verify(studentRepository, never()).findById(any());
    }

    @Test
    @DisplayName("profile-bypass: reject before changing name, email, account state or saving")
    void refusesGenericDeactivationBeforeAnyProfileWrite() {
        Student original = new Student("john", "John", "Smith", "john@example.edu", "hash", true, "student");
        given(studentRepository.findById(4)).willReturn(Optional.of(original));
        Student update = new Student("other", "Changed", "Other", "other@example.edu", "", false, "student");
        assertThatThrownBy(() -> service.updateStudent(4, update))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Use the dedicated deactivation action.");
        assertThat(original.isEnabled()).isTrue();
        assertThat(original.getFirstName()).isEqualTo("John");
        assertThat(original.getEmail()).isEqualTo("john@example.edu");
        verify(studentRepository, never()).save(any());
    }
}
