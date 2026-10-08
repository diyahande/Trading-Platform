package com.capstone.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HealthDependencyDto {

    private String name;
    private String status;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer quotaRemaining;
}
