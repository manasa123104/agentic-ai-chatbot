package com.shop;

import com.shop.model.Order;
import com.shop.service.PricingService;

public class App {
    public static void main(String[] args) {
        PricingService pricing = new PricingService();
        Order order = new Order("Asha", 1200, true);
        System.out.println(order.getCustomer() + " pays " + pricing.finalPrice(order));
    }
}
