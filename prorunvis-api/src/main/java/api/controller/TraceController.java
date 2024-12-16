package api.controller;

import api.service.TracingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trace")
public class TraceController {
    private final TracingService service;

    public TraceController(TracingService service) {
        this.service = service;
    }

    @PostMapping
    public Long runTrace(@RequestParam Long instrumentedCodeId) {
        return service.runTrace(instrumentedCodeId);
    }
}