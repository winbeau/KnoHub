package com.knohub.backend.controller;

import com.knohub.backend.dto.ApiResponse;
import com.knohub.backend.service.ActiveUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MetricsController {

    private final ActiveUserService activeUserService;

    @GetMapping("/active-users")
    public ResponseEntity<ApiResponse<Integer>> getActiveUsers() {
        int count = activeUserService.getUniqueVisitorCount();
        return ResponseEntity.ok(ApiResponse.success(count));
    }
}
