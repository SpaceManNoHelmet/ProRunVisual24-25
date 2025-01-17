package api.controller;

import api.service.InstrumentationService;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.UUID;

@RestController
@RequestMapping("/api/instrument")
public class InstrumentController {
    private final InstrumentationService service;

    public InstrumentController(InstrumentationService service) {
        this.service = service;
    }

    /**
     * Now returns a String ID referencing a local folder
     * instead of a DB primary key.
     */
    @PostMapping
    public String instrumentProject(
            @RequestParam String projectName,
            @RequestParam(required = false) String inputDir
    ) {
        // If inputDir not provided, use some default
        if (inputDir == null || inputDir.isEmpty()) {
            inputDir = "/Users/yourname/Somewhere/defaultProjectDir";
        }

        // Generate a unique ID (could be a timestamp, but here we use UUID)
        String randomId = UUID.randomUUID().toString();

        // Instrument the code, storing results in local folder named after randomId
        // Notice we now pass `randomId` to the service
        service.instrumentProject(projectName, inputDir, randomId);

        // Return that ID so the frontend can pass it to subsequent endpoints
        return randomId;
    }
}