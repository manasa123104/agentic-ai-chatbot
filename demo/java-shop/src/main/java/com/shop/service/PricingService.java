package com.shop.service;

import com.shop.model.Order;

public class PricingService {
    private static final double MEMBER_DISCOUNT = 0.10;
    private static final double BULK_DISCOUNT = 0.05;
    private static final double BULK_THRESHOLD = 1000;

    public double finalPrice(Order order) {
        if (order.getAmount() <= 0) {
            throw new IllegalArgumentException("Order amount must be positive");
        }
        double discount = 0;
        if (order.isMember()) {
            discount += MEMBER_DISCOUNT;
        }
        if (order.getAmount() >= BULK_THRESHOLD) {
            discount += BULK_DISCOUNT;
        }
        return Math.round(order.getAmount() * (1 - discount) * 100) / 100.0;
    }
}
