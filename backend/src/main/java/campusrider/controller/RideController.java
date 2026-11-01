package campusrider.controller;

import campusrider.dto.RickshawOptionDto;
import campusrider.dto.RideDto;
import campusrider.dto.RideRequestBody;
import campusrider.security.AuthUser;
import campusrider.service.AccountService;
import campusrider.service.RideService;
import campusrider.tracking.TrackingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Ride lifecycle. Passenger actions: request, available, cancel. Driver actions:
 * accept, reject, start, complete. Role checks are in SecurityConfig; ownership
 * checks (whose ride, which rickshaw) are in RideService.
 */
@RestController
@RequestMapping("/api/rides")
public class RideController {
    private final RideService rides;
    private final AccountService accounts;
    private final TrackingService tracking;

    public RideController(RideService rides, AccountService accounts, TrackingService tracking) {
        this.rides = rides;
        this.accounts = accounts;
        this.tracking = tracking;
    }

    /** Rickshaws for a trip, route-compatibility evaluated and merge-sorted (sort=best|nearest|seats). */
    @GetMapping("/available")
    public List<RickshawOptionDto> available(@RequestParam String pickup, @RequestParam String destination,
                                             @RequestParam(defaultValue = "1") int seats,
                                             @RequestParam(defaultValue = "best") String sort) {
        return rides.availableRickshaws(pickup, destination, seats, sort);
    }

    @PostMapping("/request")
    public ResponseEntity<RideDto> request(@AuthenticationPrincipal AuthUser user,
                                           @Valid @RequestBody RideRequestBody body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rides.requestRide(user.id(), body));
    }

    @GetMapping("/{id}")
    public RideDto get(@AuthenticationPrincipal AuthUser user, @PathVariable int id) {
        if (user.role() == AuthUser.Role.DRIVER) {
            return rides.viewRide(id, null, accounts.rickshawIdOf(user.id()));
        }
        return rides.viewRide(id, user.id(), null);
    }

    @PostMapping("/{id}/cancel")
    public RideDto cancel(@AuthenticationPrincipal AuthUser user, @PathVariable int id) {
        RideDto dto = rides.cancelRide(user.id(), id);
        tracking.broadcastPositions();
        return dto;
    }

    @PostMapping("/{id}/accept")
    public RideDto accept(@AuthenticationPrincipal AuthUser user, @PathVariable int id) {
        RideDto dto = rides.acceptRide(accounts.rickshawIdOf(user.id()), id);
        tracking.broadcastPositions();
        return dto;
    }

    @PostMapping("/{id}/reject")
    public RideDto reject(@AuthenticationPrincipal AuthUser user, @PathVariable int id) {
        return rides.rejectRide(accounts.rickshawIdOf(user.id()), id);
    }

    @PostMapping("/{id}/start")
    public RideDto start(@AuthenticationPrincipal AuthUser user, @PathVariable int id) {
        return rides.startRide(accounts.rickshawIdOf(user.id()), id);
    }

    @PostMapping("/{id}/complete")
    public RideDto complete(@AuthenticationPrincipal AuthUser user, @PathVariable int id) {
        RideDto dto = rides.completeRide(accounts.rickshawIdOf(user.id()), id);
        tracking.broadcastPositions();
        return dto;
    }
}
