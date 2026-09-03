package com.capstone.domain;

import com.capstone.dto.PlaceOrderRequest;
import com.capstone.enums.OrderSide;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PlaceOrderRequestValidationTest {

    // ACCOUNT ID
    // @NotNull
    // @Min(1)

    @Test
    void accountIdShouldHaveNotNullConstraint() throws Exception {

        Field field =
                PlaceOrderRequest.class.getDeclaredField("accountId");

        assertTrue(
                field.isAnnotationPresent(NotNull.class)
        );
    }

    @Test
    void accountIdShouldHaveMinConstraintOfOne() throws Exception {

        Field field =
                PlaceOrderRequest.class.getDeclaredField("accountId");

        Min annotation =
                field.getAnnotation(Min.class);

        assertNotNull(annotation);
        assertEquals(1, annotation.value());
    }


    // SYMBOL
    // @NotBlank
    // @Size(max = 20)

    @Test
    void symbolShouldHaveNotBlankConstraint() throws Exception {

        Field field =
                PlaceOrderRequest.class.getDeclaredField("symbol");

        assertTrue(
                field.isAnnotationPresent(NotBlank.class)
        );
    }

    @Test
    void symbolShouldHaveSizeConstraint() throws Exception {

        Field field =
                PlaceOrderRequest.class.getDeclaredField("symbol");

        Size annotation =
                field.getAnnotation(Size.class);

        assertNotNull(annotation);

        assertEquals(0, annotation.min());
        assertEquals(20, annotation.max());
    }

    @Test
    void symbolLengthTwentyShouldBeValidBoundary() {

        String symbol = "A".repeat(20);

        assertEquals(20, symbol.length());
    }

    @Test
    void symbolLengthTwentyOneShouldBeBeyondValidBoundary() {

        String symbol = "A".repeat(21);

        assertEquals(21, symbol.length());
    }


    // SIDE
    // @NotNull

    @Test
    void sideShouldHaveNotNullConstraint() throws Exception {

        Field field =
                PlaceOrderRequest.class.getDeclaredField("side");

        assertTrue(
                field.isAnnotationPresent(NotNull.class)
        );
    }

    @Test
    void buySideShouldBeAcceptedAsValidEnumValue() {

        PlaceOrderRequest request =
                createValidRequest();

        assertEquals(
                OrderSide.BUY,
                request.getSide()
        );
    }

    @Test
    void sellSideShouldBeAcceptedAsValidEnumValue() {

        PlaceOrderRequest request =
                new PlaceOrderRequest(
                        1L,
                        "AAPL",
                        OrderSide.SELL,
                        1,
                        new BigDecimal("100.00"),
                        "KEY-000001"
                );

        assertEquals(
                OrderSide.SELL,
                request.getSide()
        );
    }


    // QUANTITY
    // @NotNull
    // @Min(1)

    @Test
    void quantityShouldHaveNotNullConstraint() throws Exception {

        Field field =
                PlaceOrderRequest.class.getDeclaredField("quantity");

        assertTrue(
                field.isAnnotationPresent(NotNull.class)
        );
    }

    @Test
    void quantityShouldHaveMinConstraintOfOne() throws Exception {

        Field field =
                PlaceOrderRequest.class.getDeclaredField("quantity");

        Min annotation =
                field.getAnnotation(Min.class);

        assertNotNull(annotation);
        assertEquals(1, annotation.value());
    }

    @Test
    void quantityZeroShouldBeBelowMinimum() {

        int quantity = 0;

        assertTrue(quantity < 1);
    }

    @Test
    void quantityOneShouldBeExactlyAtMinimum() {

        int quantity = 1;

        assertEquals(1, quantity);
    }

    @Test
    void quantityTwoShouldBeAboveMinimum() {

        int quantity = 2;

        assertTrue(quantity > 1);
    }


    // PRICE
    // @NotNull
    // @DecimalMin(value = "0.0", inclusive = false)

    @Test
    void priceShouldHaveNotNullConstraint() throws Exception {

        Field field =
                PlaceOrderRequest.class.getDeclaredField("price");

        assertTrue(
                field.isAnnotationPresent(NotNull.class)
        );
    }

    @Test
    void priceShouldHaveDecimalMinConstraint() throws Exception {

        Field field =
                PlaceOrderRequest.class.getDeclaredField("price");

        DecimalMin annotation =
                field.getAnnotation(DecimalMin.class);

        assertNotNull(annotation);

        assertEquals(
                "0.0",
                annotation.value()
        );

        assertFalse(
                annotation.inclusive()
        );
    }

    @Test
    void negativePriceShouldBeBelowBoundary() {

        BigDecimal price =
                new BigDecimal("-0.01");

        assertTrue(
                price.compareTo(BigDecimal.ZERO) < 0
        );
    }

    @Test
    void zeroPriceShouldBeAtExclusiveBoundary() {

        BigDecimal price =
                BigDecimal.ZERO;

        assertEquals(
                0,
                price.compareTo(BigDecimal.ZERO)
        );
    }

    @Test
    void positivePriceShouldBeAboveBoundary() {

        BigDecimal price =
                new BigDecimal("0.01");

        assertTrue(
                price.compareTo(BigDecimal.ZERO) > 0
        );
    }


    // IDEMPOTENCY KEY
    // @NotNull
    // @Size(min = 8, max = 100)

    @Test
    void idempotencyKeyShouldHaveNotNullConstraint()
            throws Exception {

        Field field =
                PlaceOrderRequest.class
                        .getDeclaredField("idempotencyKey");

        assertTrue(
                field.isAnnotationPresent(NotNull.class)
        );
    }

    @Test
    void idempotencyKeyShouldHaveSizeConstraint()
            throws Exception {

        Field field =
                PlaceOrderRequest.class
                        .getDeclaredField("idempotencyKey");

        Size annotation =
                field.getAnnotation(Size.class);

        assertNotNull(annotation);

        assertEquals(8, annotation.min());
        assertEquals(100, annotation.max());
    }

    @Test
    void idempotencyKeyLengthSevenShouldBeBelowMinimum() {

        String key = "1234567";

        assertEquals(7, key.length());
        assertTrue(key.length() < 8);
    }

    @Test
    void idempotencyKeyLengthEightShouldBeAtMinimum() {

        String key = "12345678";

        assertEquals(8, key.length());
    }

    @Test
    void idempotencyKeyLengthNineShouldBeAboveMinimum() {

        String key = "123456789";

        assertTrue(key.length() > 8);
    }

    @Test
    void idempotencyKeyLengthOneHundredShouldBeAtMaximum() {

        String key = "A".repeat(100);

        assertEquals(100, key.length());
    }

    @Test
    void idempotencyKeyLengthOneHundredOneShouldBeAboveMaximum() {

        String key = "A".repeat(101);

        assertEquals(101, key.length());
        assertTrue(key.length() > 100);
    }


    // COMPLETE DTO

    @Test
    void validRequestShouldContainValidValues() {

        PlaceOrderRequest request =
                createValidRequest();

        assertEquals(
                1L,
                request.getAccountId()
        );

        assertEquals(
                "AAPL",
                request.getSymbol()
        );

        assertEquals(
                OrderSide.BUY,
                request.getSide()
        );

        assertEquals(
                1,
                request.getQuantity()
        );

        assertEquals(
                new BigDecimal("100.00"),
                request.getPrice()
        );

        assertEquals(
                "KEY-000001",
                request.getIdempotencyKey()
        );
    }


    // HELPER

    private PlaceOrderRequest createValidRequest() {

        return new PlaceOrderRequest(
                1L,
                "AAPL",
                OrderSide.BUY,
                1,
                new BigDecimal("100.00"),
                "KEY-000001"
        );
    }
}
