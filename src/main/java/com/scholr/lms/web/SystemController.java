package com.scholr.lms.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal system endpoints for the Part 1 skeleton. */
@RestController
public class SystemController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/contexts")
    public List<String> contexts() {
        return List.of("identity", "catalog", "enrollment", "media", "learning", "assessment");
    }
}
