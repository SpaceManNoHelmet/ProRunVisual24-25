package api.controller;

import api.service.VisualizationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/visualize")
public class VisualizationController {
    private final VisualizationService service;

    public VisualizationController(VisualizationService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public String getTraceJson(@PathVariable Long id) {
        return service.getTraceJson(id);
    }
}