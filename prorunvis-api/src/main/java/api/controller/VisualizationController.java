package api.controller;

import api.service.VisualizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Returns JSON from the processedTrace.json file in local_storage/<localId>.
 */
@RestController
@RequestMapping("/api/visualize")
public class VisualizationController {

    private final VisualizationService service;
    private final ObjectMapper objectMapper;

    public VisualizationController(VisualizationService service) {
        this.service = service;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * We pass a local ID referencing the folder with processedTrace.json.
     *
     * 1) We read the raw JSON string from VisualizationService.
     * 2) Parse it into an Object so Spring can produce real JSON with correct Content-Type.
     */
    @GetMapping(value = "/{localId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getTraceJson(@PathVariable String localId) {
        String rawJson = service.getTraceJson(localId);
        try {
            // Convert the raw JSON string to a generic Object
            Object jsonObj = objectMapper.readValue(rawJson, Object.class);
            // Return as JSON, 200 OK
            return ResponseEntity.ok(jsonObj);
        } catch (Exception e) {
            // If parse fails or file is invalid, return 500
            return ResponseEntity
                    .status(500)
                    .body("Failed to parse processedTrace.json: " + e.getMessage());
        }
    }
}