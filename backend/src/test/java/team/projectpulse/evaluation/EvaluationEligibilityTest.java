package team.projectpulse.evaluation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.projectpulse.rubric.Criterion;
import team.projectpulse.section.Section;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.student.Student;
import team.projectpulse.student.StudentRepository;
import team.projectpulse.system.UserUtils;
import team.projectpulse.system.exception.ObjectNotFoundException;
import team.projectpulse.system.exception.PeerEvaluationIllegalArgumentException;
import team.projectpulse.team.Team;
import java.time.*;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationEligibilityTest {
    @Mock PeerEvaluationRepository evaluations;
    @Mock StudentRepository students;
    @Mock SectionRepository sections;
    @Mock UserUtils userUtils;
    EvaluationService service;
    Student evaluator;
    Student target;
    Section section;

    @BeforeEach void setUp() {
        service = new EvaluationService(evaluations, students, sections,
                Clock.fixed(Instant.parse("2023-08-08T12:00:00Z"), ZoneOffset.UTC), userUtils);
        section = new Section(); section.setSectionId(2); section.setActiveWeeks(List.of("2023-W31"));
        Team team = new Team(); team.setTeamId(1);
        evaluator = student(4, team); target = student(5, team);
    }

    @Test @DisplayName("create-inactive: persisted disabled target rejects a stale active payload before saving")
    void rejectsInactiveCreate() {
        Student stale = student(5, target.getTeam()); target.setEnabled(false);
        given(userUtils.getStudent()).willReturn(evaluator);
        given(students.findByIdAndTeamTeamId(5, 1)).willReturn(Optional.of(target));
        assertThatThrownBy(() -> service.addPeerEvaluation(evaluation(stale)))
                .isInstanceOf(PeerEvaluationIllegalArgumentException.class).hasMessageContaining("inactive");
        verify(evaluations, never()).save(any());
    }

    @Test @DisplayName("scoped-persistence: a foreign target is refused by a team-bound finder")
    void refusesForeignCreate() {
        given(userUtils.getStudent()).willReturn(evaluator);
        given(students.findByIdAndTeamTeamId(5, 1)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.addPeerEvaluation(evaluation(target))).isInstanceOf(ObjectNotFoundException.class);
        verify(evaluations, never()).save(any());
    }

    @Test @DisplayName("create-inactive regression: active persisted target still saves in an open window")
    void allowsActiveCreate() {
        PeerEvaluation input = evaluation(target);
        given(userUtils.getStudent()).willReturn(evaluator);
        given(students.findByIdAndTeamTeamId(5, 1)).willReturn(Optional.of(target));
        given(evaluations.save(input)).willReturn(input);
        assertThat(service.addPeerEvaluation(input).getEvaluatee()).isSameAs(target);
    }

    @Test @DisplayName("update-inactive/update-target-identity: replacement active payload cannot bypass stored disabled target")
    void refusesInactiveUpdateBeforeCommentsOrRatingsChange() {
        target.setEnabled(false);
        PeerEvaluation stored = evaluation(target);
        PeerEvaluation update = new PeerEvaluation("2023-W31", evaluator, evaluator,
                List.of(new Rating(stored.getRatings().get(0).getCriterion(), 2.0)), "changed", "changed private");
        given(userUtils.getUserId()).willReturn(4);
        given(evaluations.findByPeerEvaluationIdAndEvaluatorId(9, 4)).willReturn(Optional.of(stored));
        // Only used by the deliberate mutant: a bad write must not hide behind a null mock save.
        lenient().when(evaluations.save(any(PeerEvaluation.class))).thenAnswer(call -> call.getArgument(0));
        assertThatThrownBy(() -> service.updatePeerEvaluation(9, update))
                .isInstanceOf(PeerEvaluationIllegalArgumentException.class).hasMessageContaining("inactive");
        assertThat(stored.getPublicComment()).isEqualTo("public");
        assertThat(stored.getPrivateComment()).isEqualTo("private");
        assertThat(stored.getEvaluatee()).isSameAs(target);
        assertThat(stored.getRatings()).extracting(Rating::getActualScore).containsExactly(8.0);
        assertThat(stored.getTotalScore()).isEqualTo(8.0);
        verify(evaluations, never()).save(any());
    }

    @Test @DisplayName("scoped-persistence: service denies another evaluator's record independently of route guard")
    void refusesForeignUpdate() {
        given(userUtils.getUserId()).willReturn(4);
        assertThatThrownBy(() -> service.updatePeerEvaluation(9, evaluation(target))).isInstanceOf(ObjectNotFoundException.class);
        verify(evaluations, never()).save(any());
    }

    @Test @DisplayName("historical-report/retention: include inactive target with this week's results after team removal; retain inactive author's comments")
    void includesOnlyInactiveTargetsWithSelectedWeekHistory() {
        evaluator.setEnabled(false); target.setEnabled(false); target.setTeam(null);
        Student empty = student(6, null); empty.setEnabled(false);
        Student active = student(7, evaluator.getTeam());
        section.addStudent(target); section.addStudent(empty); section.addStudent(active);
        PeerEvaluation historical = evaluation(target);
        given(userUtils.getUserId()).willReturn(1);
        given(sections.findForEvaluationReport(2, 1)).willReturn(Optional.of(section));
        given(evaluations.existsByWeekAndEvaluateeId("2023-W31", 5)).willReturn(true);
        given(evaluations.findByWeekAndEvaluateeId("2023-W31", 5)).willReturn(List.of(historical));
        WeeklyPeerEvaluationReport report = service.generateWeeklyPeerEvaluationReportForSection(2, "2023-W31");
        assertThat(report.getPeerEvaluationAverages()).extracting(PeerEvaluationAverage::getStudentId).containsExactly(5, 7);
        PeerEvaluationAverage row = report.getPeerEvaluationAverages().get(0);
        assertThat(row.getAverageTotalScore()).isEqualTo(8.0);
        assertThat(historical.getRatings()).extracting(Rating::getActualScore).containsExactly(8.0);
        assertThat(row.isEnabled()).isFalse(); assertThat(row.getTeamName()).isNull();
        assertThat(row.getPublicComments()).containsExactly("public");
        assertThat(row.getPrivateComments()).containsExactly("private");
        assertThat(report.getPeerEvaluationAverages().get(1).isEnabled()).isTrue();
    }

    @Test @DisplayName("scoped-persistence: foreign course section report returns no student data")
    void deniesForeignSectionReport() {
        given(userUtils.getUserId()).willReturn(3);
        assertThatThrownBy(() -> service.generateWeeklyPeerEvaluationReportForSection(2, "2023-W31"))
                .isInstanceOf(ObjectNotFoundException.class);
        verifyNoInteractions(evaluations, students);
    }

    private Student student(int id, Team team) {
        Student result = new Student(); result.setId(id); result.setEnabled(true); result.setTeam(team); result.setSection(section); return result;
    }
    private PeerEvaluation evaluation(Student evaluatee) {
        Criterion criterion = new Criterion("Quality", "Quality of work", 10.0);
        criterion.setCriterionId(1);
        return new PeerEvaluation("2023-W31", evaluator, evaluatee, List.of(new Rating(criterion, 8.0)), "public", "private");
    }
}
