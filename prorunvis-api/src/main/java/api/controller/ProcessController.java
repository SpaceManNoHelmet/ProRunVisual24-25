package api.controller;

import api.service.ProcessingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/process")
public class ProcessController {

    private final ProcessingService processingService;

    public ProcessController(ProcessingService processingService) {
        this.processingService = processingService;
    }

    /**
     * POST /api/process?traceId=<shortId>
     *
     * We'll read local_storage/<shortId>/Trace.tr,
     * produce processedTrace.json, store it in the same folder,
     * and return the shortId (or path) for the next step.
     */
    @PostMapping
    public String processTrace(@RequestParam String traceId) {
        // run the processing
        processingService.processTrace(traceId);

        // return the same short ID for the front end
        // so the front end can do a GET /api/visualize/<traceId> if desired
        return traceId;
    }
}