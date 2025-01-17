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

    /**
     * The client calls: POST /api/trace?instrumentId=<shortId>
     * We do NOT return a big absolute path anymore.
     * Instead, we just return the same short ID so the user can
     * pass it to /api/process later.
     */
    @PostMapping
    public String runTrace(@RequestParam String instrumentId) {
        // This will decode and produce the trace file "Trace.tr"
        // in local_storage/<instrumentId>/Trace.tr.
        service.runTrace(instrumentId);

        // Instead of returning an absolute path, we now just return the same short ID.
        return instrumentId;
    }
}