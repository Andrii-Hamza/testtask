package com.testtask.dataapi.controller;

import com.testtask.dataapi.dto.TransformRequest;
import com.testtask.dataapi.dto.TransformResponse;
import com.testtask.dataapi.service.TransformService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TransformController {

    private final TransformService transformService;

    @PostMapping("/transform")
    public TransformResponse transform(@Valid @RequestBody TransformRequest request) {
        return new TransformResponse(transformService.transform(request.text()));
    }
}
