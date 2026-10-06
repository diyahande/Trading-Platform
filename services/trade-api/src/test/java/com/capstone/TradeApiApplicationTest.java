package com.capstone;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.*;

class TradeApiApplicationTest {

    @Test
    void main_invokesSpringApplicationRun() {
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            TradeApiApplication.main(new String[]{"--test"});
            springApplication.verify(() -> SpringApplication.run(TradeApiApplication.class, new String[]{"--test"}));
        }
    }
}

