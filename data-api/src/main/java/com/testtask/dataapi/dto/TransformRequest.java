package com.testtask.dataapi.dto;

import jakarta.validation.constraints.NotBlank;

public record TransformRequest(@NotBlank String text) {}
