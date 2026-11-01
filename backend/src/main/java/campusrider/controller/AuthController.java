package campusrider.controller;

import campusrider.dto.DriverRegisterRequest;
import campusrider.dto.LoginRequest;
import campusrider.dto.PassengerRegisterRequest;
import campusrider.dto.SessionUserDto;
import campusrider.exception.ApiException;
import campusrider.security.AuthUser;
import campusrider.security.LoginAttemptLimiter;
import campusrider.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * Registration, login and logout. A successful login stores an {@link AuthUser}
 * in a fresh HTTP session (the session id is rotated to prevent fixation) and
 * issues a new CSRF token.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    private final LoginAttemptLimiter limiter;
    private final SecurityContextRepository contextRepository;
    private final CookieCsrfTokenRepository csrfRepository;

    public AuthController(AccountService accounts, LoginAttemptLimiter limiter,
                          SecurityContextRepository contextRepository, CookieCsrfTokenRepository csrfRepository) {
        this.accounts = accounts;
        this.limiter = limiter;
        this.contextRepository = contextRepository;
        this.csrfRepository = csrfRepository;
    }

    /** Lets the browser obtain the XSRF-TOKEN cookie before its first POST. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    /** The signed-in user, or 204 when nobody is signed in. */
    @GetMapping("/me")
    public ResponseEntity<SessionUserDto> me(@AuthenticationPrincipal AuthUser user) {
        if (user == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(accounts.sessionUser(user));
    }

    @PostMapping("/passenger/register")
    public ResponseEntity<SessionUserDto> registerPassenger(@Valid @RequestBody PassengerRegisterRequest body,
                                                            HttpServletRequest req, HttpServletResponse res) {
        AuthUser user = accounts.registerPassenger(body);
        signIn(user, req, res);
        return ResponseEntity.status(HttpStatus.CREATED).body(accounts.sessionUser(user));
    }

    @PostMapping("/driver/register")
    public ResponseEntity<SessionUserDto> registerDriver(@Valid @RequestBody DriverRegisterRequest body,
                                                         HttpServletRequest req, HttpServletResponse res) {
        AuthUser user = accounts.registerDriver(body);
        signIn(user, req, res);
        return ResponseEntity.status(HttpStatus.CREATED).body(accounts.sessionUser(user));
    }

    @PostMapping("/login")
    public SessionUserDto login(@Valid @RequestBody LoginRequest body, HttpServletRequest req,
                                HttpServletResponse res) {
        AuthUser.Role role = AuthUser.Role.valueOf(body.role().trim().toUpperCase(Locale.ROOT));
        String key = role + ":" + body.username().trim().toLowerCase(Locale.ROOT);
        long locked = limiter.secondsLocked(key);
        if (locked > 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Try again in " + locked + " seconds.");
        }
        AuthUser user = accounts.authenticate(role, body.username(), body.password()).orElse(null);
        if (user == null) {
            limiter.recordFailure(key);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect username or password.");
        }
        limiter.recordSuccess(key);
        signIn(user, req, res);
        return accounts.sessionUser(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req, HttpServletResponse res) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        rotateCsrf(req, res);
        return ResponseEntity.noContent().build();
    }

    private void signIn(AuthUser user, HttpServletRequest req, HttpServletResponse res) {
        if (req.getSession(false) != null) {
            req.changeSessionId(); // session fixation protection
        }
        UsernamePasswordAuthenticationToken auth = UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority(user.role().authority())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, req, res);
        rotateCsrf(req, res);
    }

    private void rotateCsrf(HttpServletRequest req, HttpServletResponse res) {
        csrfRepository.saveToken(csrfRepository.generateToken(req), req, res);
    }
}
