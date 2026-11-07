package team.projectpulse.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.student.Student;
import team.projectpulse.user.PeerEvaluationUser;
import team.projectpulse.user.UserRepository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ActiveAccountValidatorTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    ActiveAccountValidator validator;

    @ParameterizedTest
    @MethodSource("activeAccounts")
    void acceptsAnActivePersistedAccount(PeerEvaluationUser account) {
        account.setId(7);
        account.setEnabled(true);
        given(this.userRepository.findById(7)).willReturn(Optional.of(account));

        assertThat(this.validator.validate(jwt(7)).hasErrors()).isFalse();
    }

    static Stream<PeerEvaluationUser> activeAccounts() {
        return Stream.of(new Student(), new Instructor());
    }

    @ParameterizedTest
    @MethodSource("activeAccounts")
    void rejectsAnInactivePersistedAccountDespiteTokenEnabledClaim(PeerEvaluationUser account) {
        account.setId(7);
        account.setEnabled(false);
        given(this.userRepository.findById(7)).willReturn(Optional.of(account));
        Jwt token = Jwt.withTokenValue("signed-token").header("alg", "RS256")
                .subject("other-user@example.edu").claim("userId", 7).claim("enabled", true).build();

        assertGenericRejection(this.validator.validate(token));
    }

    @Test
    void resolvesByUserIdRatherThanSubjectOrTokenStatus() {
        Student active = new Student();
        active.setId(7);
        active.setEnabled(true);
        given(this.userRepository.findById(7)).willReturn(Optional.of(active));
        Jwt token = Jwt.withTokenValue("signed-token").header("alg", "RS256")
                .subject("somebody-else@example.edu").claim("userId", 7).claim("enabled", false).build();

        assertThat(this.validator.validate(token).hasErrors()).isFalse();
    }

    @Test
    void rejectsADeletedAccount() {
        given(this.userRepository.findById(7)).willReturn(Optional.empty());

        assertGenericRejection(this.validator.validate(jwt(7)));
    }

    @Test
    void failsClosedWhenTheDatabaseLookupFails() {
        given(this.userRepository.findById(7)).willThrow(
                new DataAccessResourceFailureException("private database/account diagnostic"));

        assertGenericRejection(this.validator.validate(jwt(7)));
    }

    @Test
    void rereadsPersistedStateOnEveryValidationOfTheSameToken() {
        Student active = new Student();
        active.setId(7);
        active.setEnabled(true);
        Student inactive = new Student();
        inactive.setId(7);
        inactive.setEnabled(false);
        given(this.userRepository.findById(7)).willReturn(
                Optional.of(active), Optional.of(inactive), Optional.of(active), Optional.empty());
        Jwt token = jwt(7);

        assertThat(this.validator.validate(token).hasErrors()).isFalse();
        assertGenericRejection(this.validator.validate(token));
        assertThat(this.validator.validate(token).hasErrors()).isFalse();
        assertGenericRejection(this.validator.validate(token));
    }

    @ParameterizedTest
    @MethodSource("validUserIds")
    void acceptsExactIntegralIdsWithoutNarrowingToADifferentAccount(Object claim, int expectedId) {
        Student active = new Student();
        active.setId(expectedId);
        active.setEnabled(true);
        given(this.userRepository.findById(expectedId)).willReturn(Optional.of(active));

        assertThat(this.validator.validate(jwt(claim)).hasErrors()).isFalse();
    }

    static Stream<Arguments> validUserIds() {
        return Stream.of(
                Arguments.of(1, 1), Arguments.of(7L, 7), Arguments.of("7", 7),
                Arguments.of(new BigInteger("7"), 7), Arguments.of(new BigDecimal("7.000"), 7),
                Arguments.of(7.0d, 7), Arguments.of(7.0f, 7),
                Arguments.of(Integer.MAX_VALUE, 2147483647), Arguments.of("2147483647", 2147483647));
    }

    @Test
    void rejectsAMissingUserIdWithoutLookingUpAnAccount() {
        Jwt token = Jwt.withTokenValue("signed-token").header("alg", "RS256").subject("user").build();

        assertGenericRejection(this.validator.validate(token));
        verifyNoInteractions(this.userRepository);
    }

    @ParameterizedTest
    @MethodSource("invalidUserIds")
    void rejectsMalformedOrOutOfRangeIdsWithoutLookingUpAnotherAccount(Object claim) {
        assertGenericRejection(this.validator.validate(jwt(claim)));
        verifyNoInteractions(this.userRepository);
    }

    static Stream<Object> invalidUserIds() {
        return Stream.of(
                "", " ", "user-7", "7.5", "7.0", "1e1", " 7", "7 ", "+7", "-7",
                0, -1, "0", "2147483648", 2147483648L, 4294967303L, Long.MAX_VALUE,
                new BigInteger("18446744073709551623"), new BigDecimal("7.0000000000000000001"),
                new BigDecimal("2147483647.0000000001"), 7.5d, 7.5f,
                Double.NaN, Double.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                true, List.of(7), Map.of("id", 7));
    }

    private static Jwt jwt(Object userId) {
        return Jwt.withTokenValue("signed-token").header("alg", "RS256")
                .subject("user@example.edu").claim("userId", userId).build();
    }

    private static void assertGenericRejection(OAuth2TokenValidatorResult result) {
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getErrorCode()).isEqualTo("invalid_token");
            assertThat(error.getDescription()).isEqualTo("Invalid token");
            assertThat(error.getUri()).isNull();
        });
    }
}
