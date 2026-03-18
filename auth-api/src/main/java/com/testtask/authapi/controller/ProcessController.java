package com.testtask.authapi.controller;

import com.testtask.authapi.dto.ProcessRequest;
import com.testtask.authapi.dto.ProcessResponse;
import com.testtask.authapi.security.CustomUserDetails;
import com.testtask.authapi.service.ProcessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProcessController {

    private final ProcessService processService;

    @PostMapping("/process")
    public ProcessResponse process(@Valid @RequestBody ProcessRequest request,
                                   @AuthenticationPrincipal CustomUserDetails userDetails) {
        return processService.process(request, userDetails.user().getId());
    }
}
