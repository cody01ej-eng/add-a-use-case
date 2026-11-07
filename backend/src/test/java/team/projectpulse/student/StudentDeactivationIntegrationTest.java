package team.projectpulse.student;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.activity.ActivityRepository;
import team.projectpulse.evaluation.PeerEvaluationRepository;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StudentDeactivationIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired StudentRepository students;
    @Autowired UserRepository users;
    @Autowired ActivityRepository activities;
    @Autowired PeerEvaluationRepository evaluations;
    @Autowired SectionRepository sections;

    @ParameterizedTest @ValueSource(strings = {"b.wei@abc.edu", "b.gates@abc.edu"})
    @DisplayName("authorized-deactivation: owner and assigned instructor preserve membership and all stored work")
    void deactivatesAndPreservesHistory(String email) throws Exception {
        Student john = john();
        Integer teamId = john.getTeam().getTeamId();
        long activityCount = activities.count(), evaluationCount = evaluations.count();
        mvc.perform(patch(path(john.getId())).header(HttpHeaders.AUTHORIZATION, login(email)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.message").value("Student deactivated successfully"));
        assertThat(students.findById(john.getId()).orElseThrow().getTeam().getTeamId()).isEqualTo(teamId);
        assertThat(activities.count()).isEqualTo(activityCount);
        assertThat(evaluations.count()).isEqualTo(evaluationCount);
    }

    @Test @DisplayName("repeat3a: authorized inactive repeat succeeds even when all student accounts are inactive")
    void alreadyInactiveIsHarmless() throws Exception {
        students.findAll().forEach(student -> student.setEnabled(false));
        students.flush();
        mvc.perform(patch(path(john().getId())).header(HttpHeaders.AUTHORIZATION, login("b.gates@abc.edu")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Student is already deactivated."));
    }

    @ParameterizedTest @ValueSource(strings = {"j.smith@abc.edu", "t.cook@abc.edu"})
    @DisplayName("forbidden2a: a student and non-owning admin cannot deactivate an active or inactive target")
    void deniesUnauthorizedIncludingRepeat(String email) throws Exception {
        String token = login(email);
        Student target = students.findById(users.findByUsername("e.hudson@abc.edu").orElseThrow().getId()).orElseThrow();
        mvc.perform(patch(path(target.getId())).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        assertThat(target.isEnabled()).isTrue();
        target.setEnabled(false); students.flush();
        mvc.perform(patch(path(target.getId())).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        assertThat(target.isEnabled()).isFalse();
    }

    @Test @DisplayName("forbidden2a: instructor membership in another course section does not suffice")
    void deniesInstructorNotAssignedToTargetSection() throws Exception {
        String token = login("b.gates@abc.edu");
        Student john = john();
        john.getSection().getInstructors().removeIf(instructor -> instructor.getUsername().equals("b.gates@abc.edu"));
        sections.saveAndFlush(john.getSection());
        mvc.perform(patch(path(john.getId())).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        assertThat(john.isEnabled()).isTrue();
    }

    @Test @DisplayName("live-token: the exact token issued before deactivation loses access and disabled login fails")
    void rejectsExistingTokenAndNewLogin() throws Exception {
        Student john = john();
        String token = login("j.smith@abc.edu");
        mvc.perform(get("/api/v1/students/" + john.getId()).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
        mvc.perform(patch(path(john.getId())).header(HttpHeaders.AUTHORIZATION, login("b.gates@abc.edu"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/students/" + john.getId()).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/users/login").with(httpBasic("j.smith@abc.edu", "123456"))).andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("scoped-persistence: caller scope is enforced by actual lifecycle/report JPQL finders")
    void scopesRepositoryQueriesIndependentlyOfRoutes() {
        Student john = john();
        Integer stranger = users.findByUsername("t.cook@abc.edu").orElseThrow().getId();
        Integer instructor = users.findByUsername("b.gates@abc.edu").orElseThrow().getId();
        assertThat(students.findForLifecycle(john.getId(), stranger)).isEmpty();
        assertThat(students.findForLifecycle(john.getId(), instructor)).contains(john);
        assertThat(sections.findForEvaluationReport(john.getSection().getSectionId(), stranger)).isEmpty();
        assertThat(sections.findForEvaluationReport(john.getSection().getSectionId(), instructor)).contains(john.getSection());
    }

    @Test @DisplayName("profile-bypass: failed generic deactivation leaves names and account state unchanged")
    void rejectsGenericProfileDeactivation() throws Exception {
        Student john = john(); String oldName = john.getFirstName();
        mvc.perform(put("/api/v1/students/" + john.getId()).header(HttpHeaders.AUTHORIZATION, login("b.gates@abc.edu"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"j.smith@abc.edu\",\"firstName\":\"Changed\",\"lastName\":\"Smith\",\"email\":\"j.smith@abc.edu\",\"enabled\":false}"))
                .andExpect(status().isBadRequest());
        assertThat(john.isEnabled()).isTrue(); assertThat(john.getFirstName()).isEqualTo(oldName);
    }

    @Test @DisplayName("missing-auth/unresolved-target: route is closed for absent credentials and bad identifiers")
    void handlesMissingAuthenticationAndUnknownTargets() throws Exception {
        mvc.perform(patch(path(john().getId()))).andExpect(status().isUnauthorized());
        String token = login("b.gates@abc.edu");
        mvc.perform(patch(path(999999)).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/students/not-a-number/deactivation").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
    }

    private Student john() { return students.findById(users.findByUsername("j.smith@abc.edu").orElseThrow().getId()).orElseThrow(); }
    private String path(Integer id) { return "/api/v1/students/" + id + "/deactivation"; }
    private String login(String email) throws Exception {
        var response = mvc.perform(post("/api/v1/users/login").with(httpBasic(email, "123456")))
                .andExpect(status().isOk()).andReturn().getResponse();
        return "Bearer " + new JSONObject(response.getContentAsString()).getJSONObject("data").getString("token");
    }
}
