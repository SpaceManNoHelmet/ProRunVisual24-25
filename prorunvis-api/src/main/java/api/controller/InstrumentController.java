package api.controller;

import api.service.InstrumentationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/instrument")
public class InstrumentController {
    private final InstrumentationService service;

    public InstrumentController(InstrumentationService service) {
        this.service = service;
    }

    @PostMapping
    public Long instrumentProject(
            @RequestParam String projectName,
            @RequestParam(required = false) String inputDir) {

        // If inputDir is not provided, default to your given directory
        if (inputDir == null || inputDir.isEmpty()) {
            inputDir = "/Users/milanadhokari/Downloads/ProRunVis-examples-master/DualPivotQuicksort/java_copy/util";
        }

        return service.instrumentProject(projectName, inputDir);
    }
}