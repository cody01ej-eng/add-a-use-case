package team.projectpulse.security.authorizationmanagers;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import team.projectpulse.student.StudentSecurityService;

import java.util.function.Supplier;

/** Target-specific policy for UC-STU-deactivate-student, including inactive repeats. */
@Component
public class StudentLifecycleAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    private final StudentSecurityService students;

    public StudentLifecycleAuthorizationManager(StudentSecurityService students) {
        this.students = students;
    }

    @Override
    public @Nullable AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> authentication,
                                                   RequestAuthorizationContext context) {
        Integer studentId = PathVariables.readId(context, "studentId");
        return new AuthorizationDecision(studentId != null && this.students.canDeactivateStudent(studentId));
    }
}
