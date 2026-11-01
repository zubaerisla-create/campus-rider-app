package campusrider.controller;

import campusrider.dto.AutoDriveRequest;
import campusrider.dto.AvailabilityRequest;
import campusrider.dto.DriverDashboardDto;
import campusrider.dto.DriverProfileDto;
import campusrider.dto.MessageDto;
import campusrider.dto.OfferDto;
import campusrider.dto.RickshawStateRequest;
import campusrider.dto.RideDto;
import campusrider.dto.UpdateDriverProfileRequest;
import campusrider.security.AuthUser;
import campusrider.service.AccountService;
import campusrider.service.RideService;
import campusrider.tracking.TrackingService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Driver-only endpoints (ROLE_DRIVER). The rickshaw is always the signed-in driver's own. */
@RestController
@RequestMapping("/api/drivers")
public class DriverController {
    private final AccountService accounts;
    private final RideService rides;
    private final TrackingService tracking;

    public DriverController(AccountService accounts, RideService rides, TrackingService tracking) {
        this.accounts = accounts;
        this.rides = rides;
        this.tracking = tracking;
    }

    @GetMapping("/profile")
    public DriverProfileDto profile(@AuthenticationPrincipal AuthUser user) {
        return accounts.driverProfile(user.id());
    }

    @PutMapping("/profile")
    public DriverProfileDto updateProfile(@AuthenticationPrincipal AuthUser user,
                                          @Valid @RequestBody UpdateDriverProfileRequest body) {
        return accounts.updateDriver(user.id(), body);
    }

    @GetMapping("/dashboard")
    public DriverDashboardDto dashboard(@AuthenticationPrincipal AuthUser user) {
        return rides.driverDashboard(rickshaw(user));
    }

    @PutMapping("/availability")
    public DriverDashboardDto availability(@AuthenticationPrincipal AuthUser user,
                                           @Valid @RequestBody AvailabilityRequest body) {
        DriverDashboardDto dto = rides.setOnline(rickshaw(user), body.online());
        tracking.broadcastPositions();
        return dto;
    }

    /** Current location (when empty), own destination and seats offered (0-3). */
    @PutMapping("/rickshaw")
    public DriverDashboardDto updateRickshaw(@AuthenticationPrincipal AuthUser user,
                                             @Valid @RequestBody RickshawStateRequest body) {
        DriverDashboardDto dto = rides.updateRickshawState(rickshaw(user), body.currentLocationId().trim(),
                body.targetDestinationId(), body.availableSeats());
        tracking.broadcastPositions();
        return dto;
    }

    @GetMapping("/requests")
    public List<OfferDto> requests(@AuthenticationPrincipal AuthUser user) {
        return rides.driverRequests(rickshaw(user));
    }

    @GetMapping("/rides")
    public List<RideDto> history(@AuthenticationPrincipal AuthUser user) {
        return rides.driverHistory(rickshaw(user));
    }

    /** Simulated tracking: move to the next campus location on the route. */
    @PostMapping("/move")
    public MessageDto move(@AuthenticationPrincipal AuthUser user) {
        return new MessageDto(tracking.moveStep(rickshaw(user)));
    }

    /** Simulated tracking: automatic drive along the route. */
    @PutMapping("/auto-drive")
    public DriverDashboardDto autoDrive(@AuthenticationPrincipal AuthUser user,
                                        @Valid @RequestBody AutoDriveRequest body) {
        tracking.setAutoDrive(rickshaw(user), body.enabled());
        return rides.driverDashboard(rickshaw(user));
    }

    private String rickshaw(AuthUser user) {
        return accounts.rickshawIdOf(user.id());
    }
}
