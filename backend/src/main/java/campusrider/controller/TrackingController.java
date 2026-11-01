package campusrider.controller;

import campusrider.dto.RickshawPositionDto;
import campusrider.tracking.TrackingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Current simulated rickshaw positions (the same data the WebSocket pushes). */
@RestController
public class TrackingController {
    private final TrackingService tracking;

    public TrackingController(TrackingService tracking) {
        this.tracking = tracking;
    }

    @GetMapping("/api/tracking/rickshaws")
    public List<RickshawPositionDto> rickshaws() {
        return tracking.positions();
    }
}
