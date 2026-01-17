package ru.minimalprice.minimalprice.features.price.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ProductUpdateEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final String categoryName;
    private final String productName;
    private final double price;

    public ProductUpdateEvent(String categoryName, String productName, double price) {
        super(true); // Async event
        this.categoryName = categoryName;
        this.productName = productName;
        this.price = price;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getProductName() {
        return productName;
    }

    public double getPrice() {
        return price;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
