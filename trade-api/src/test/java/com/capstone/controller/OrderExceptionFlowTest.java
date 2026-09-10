package com.capstone.controller;

import com.capstone.enums.AccountStatus;
import com.capstone.exception.AccountNotActiveException;
import com.capstone.exception.AccountNotFoundException;
import com.capstone.exception.DomainException;
import com.capstone.exception.DuplicateOrderException;
import com.capstone.exception.InsufficientFundsException;
import com.capstone.exception.InstrumentNotFoundException;
import com.capstone.service.AccountService;
import com.capstone.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderExceptionFlowTest {

    private MockMvc mockMvc;
    private OrderService orderService;
    private AccountService accountService;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        accountService = mock(AccountService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        OrderController controller = new OrderController(orderService, accountService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        when(accountService.verifyAuthorizedAccount(anyLong(), anyString())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @ParameterizedTest
    @MethodSource("domainFailureCases")
    void placeOrder_domainFailures_mapToCodeAndStatus(DomainException ex, int expectedStatus, String expectedCode) throws Exception {
        when(orderService.placeOrder(any(), anyString())).thenThrow(ex);

        mockMvc.perform(post("/api/v1/orders")
                        .requestAttr("accountKey", "ACC-101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.errorCode").value(expectedCode))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @ParameterizedTest
    @MethodSource("invalidInputCases")
    void placeOrder_invalidInput_mapsToVal422(String invalidRequestBody) throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .requestAttr("accountKey", "ACC-101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestBody))
                                .andExpect(status().is(422))
                .andExpect(jsonPath("$.errorCode").value("VAL-422"))
                .andExpect(jsonPath("$.message").value("Invalid input"));
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> domainFailureCases() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(new AccountNotFoundException(101L), 404, "ACC-404"),
                org.junit.jupiter.params.provider.Arguments.of(new AccountNotActiveException(101L, AccountStatus.SUSPENDED), 403, "ACC-403"),
                org.junit.jupiter.params.provider.Arguments.of(new InstrumentNotFoundException("INFY"), 404, "INS-404"),
                org.junit.jupiter.params.provider.Arguments.of(new InsufficientFundsException(101L, new BigDecimal("1000"), new BigDecimal("500")), 400, "ORD-400"),
                org.junit.jupiter.params.provider.Arguments.of(new DuplicateOrderException("idem-12345678"), 409, "ORD-409")
        );
    }

    private static Stream<String> invalidInputCases() {
        return Stream.of(
                "{\"accountId\":0,\"symbol\":\"INFY\",\"side\":\"BUY\",\"quantity\":1,\"price\":100.00,\"idempotencyKey\":\"idem-12345678\"}",
                "{\"accountId\":101,\"symbol\":\"\",\"side\":\"BUY\",\"quantity\":1,\"price\":100.00,\"idempotencyKey\":\"idem-12345678\"}"
        );
    }

    private static String validRequestJson() {
        return "{\"accountId\":101,\"symbol\":\"INFY\",\"side\":\"BUY\",\"quantity\":1,\"price\":100.00,\"idempotencyKey\":\"idem-12345678\"}";
    }
}



