package com.testtask.authapi.dto;

import jakarta.validation.constraints.NotBlank;

public record ProcessRequest(@NotBlank String text) {}
