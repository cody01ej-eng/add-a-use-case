package team.projectpulse.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.*;
import team.projectpulse.student.Student;
import team.projectpulse.user.UserRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ActiveAccountDecoderTest {
    @Mock UserRepository users;
    @InjectMocks SecurityConfiguration configuration;
    Student student;

    @BeforeEach void setUp() {
        student = new Student(); student.setId(4); student.setEnabled(true);
    }

    @Test @DisplayName("live-token: the actual configured decoder rejects the same signed token after deactivation")
    void rejectsPreviouslyIssuedTokenAfterAccountStateChanges() {
        given(users.findById(4)).willReturn(Optional.of(student));
        JwtDecoder decoder = configuration.jwtDecoder(new ActiveAccountValidator(users));
        String token = signedToken(configuration, Instant.now().plusSeconds(300));
        assertThat(decoder.decode(token).getClaimAsString("userId")).isEqualTo("4");
        student.setEnabled(false);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test @DisplayName("auth-regression: adding account validation does not remove token expiry validation")
    void stillRejectsExpiredSignedTokens() {
        given(users.findById(4)).willReturn(Optional.of(student));
        JwtDecoder decoder = configuration.jwtDecoder(new ActiveAccountValidator(users));
        String token = signedToken(configuration, Instant.now().minusSeconds(300));
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test @DisplayName("auth-regression: a token signed with a different key remains invalid")
    void stillRejectsWrongSignature() {
        JwtDecoder decoder = configuration.jwtDecoder(new ActiveAccountValidator(users));
        String token = signedToken(configuration, Instant.now().plusSeconds(300));
        String[] parts = token.split("\\.");
        String replacement = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        String tampered = parts[0] + "." + parts[1] + "." + replacement;
        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
    }

    private String signedToken(SecurityConfiguration config, Instant expiry) {
        JwtClaimsSet claims = JwtClaimsSet.builder().subject("j.smith@abc.edu").issuer("self")
                .issuedAt(expiry.minusSeconds(600)).expiresAt(expiry).claim("userId", 4)
                .claim("authorities", "ROLE_student").build();
        return config.jwtEncoder().encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
