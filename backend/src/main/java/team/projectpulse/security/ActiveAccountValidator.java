package team.projectpulse.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import team.projectpulse.user.PeerEvaluationUser;
import team.projectpulse.user.UserRepository;

import java.math.BigDecimal;

/**
 * Checks current persisted account state for a JWT whose signature is verified by the decoder.
 * Compose with the decoder's standard validators to retain timestamp validation.
 */
@Component
public class ActiveAccountValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error("invalid_token", "Invalid token", null);

    private final UserRepository userRepository;

    public ActiveAccountValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        try {
            Integer userId = readUserId(token.getClaims().get("userId"));
            if (userId != null && this.userRepository.findById(userId)
                    .filter(PeerEvaluationUser::isEnabled).isPresent()) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (RuntimeException ex) {
            // Malformed claims and failed account lookups must never authenticate or disclose details.
        }
        return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }

    private Integer readUserId(Object claim) {
        int userId;
        if (claim instanceof Number number) {
            // intValue() would silently truncate fractions or wrap an overflowing id onto another account.
            userId = new BigDecimal(number.toString()).intValueExact();
        } else if (claim instanceof String value && value.matches("[0-9]+")) {
            userId = Integer.parseInt(value);
        } else {
            return null;
        }
        return userId > 0 ? userId : null;
    }
}
