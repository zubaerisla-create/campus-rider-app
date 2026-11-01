package campusrider.controller;

import campusrider.dto.PassengerProfileDto;
import campusrider.dto.RideDto;
import campusrider.dto.UpdatePassengerProfileRequest;
import campusrider.security.AuthUser;
import campusrider.service.AccountService;
import campusrider.service.RideService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Passenger-only endpoints (ROLE_PASSENGER, enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/passengers")
public class PassengerController {
    private final AccountService accounts;
    private final RideService rides;

    public PassengerController(AccountService accounts, RideService rides) {
        this.accounts = accounts;
        this.rides = rides;
    }

    @GetMapping("/profile")
    public PassengerProfileDto profile(@AuthenticationPrincipal AuthUser user) {
        return accounts.passengerProfile(user.id());
    }

    @PutMapping("/profile")
    public PassengerProfileDto updateProfile(@AuthenticationPrincipal AuthUser user,
                                             @Valid @RequestBody UpdatePassengerProfileRequest body) {
        return accounts.updatePassenger(user.id(), body);
    }

    @GetMapping("/rides")
    public List<RideDto> history(@AuthenticationPrincipal AuthUser user) {
        return rides.passengerHistory(user.id());
    }

    /** The passenger's open ride (pending, accepted or started), or 204. */
    @GetMapping("/rides/current")
    public ResponseEntity<RideDto> current(@AuthenticationPrincipal AuthUser user) {
        return rides.currentRideView(user.id()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
