package com.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HealthResponseDto {

    private String status;
    private List<HealthDependencyDto> dependencies;
    private OffsetDateTime asOf;
}
