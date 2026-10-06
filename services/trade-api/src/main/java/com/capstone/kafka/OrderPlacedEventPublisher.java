package com.capstone.kafka;

import com.capstone.entity.Order;

public interface OrderPlacedEventPublisher {

    void publishOrderPlaced(Order order, String symbol);
}