package com.shop.model;

public class Order {
    private final String customer;
    private final double amount;
    private final boolean member;

    public Order(String customer, double amount, boolean member) {
        this.customer = customer;
        this.amount = amount;
        this.member = member;
    }

    public String getCustomer() {
        return customer;
    }

    public double getAmount() {
        return amount;
    }

    public boolean isMember() {
        return member;
    }
}
