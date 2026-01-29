package com.aigen.studio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRequirementRequest {

    @NotBlank(message = "Code is required")
    @Size(max = 50, message = "Code must be less than 50 characters")
    private String code;

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be less than 200 characters")
    private String title;

    @Size(max = 5000, message = "Description must be less than 5000 characters")
    private String description;
}