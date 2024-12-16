package api.controller;

import api.service.ProcessingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/process")
public class ProcessController {
    private final ProcessingService service;

    public ProcessController(ProcessingService service) {
        this.service = service;
    }

    @PostMapping
    public Long processTrace(@RequestParam Long traceDataId) {
        return service.processTrace(traceDataId);
    }
}