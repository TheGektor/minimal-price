package ru.minimalprice.minimalprice.features.price.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ProductDeleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String categoryName;
    private final String productName;

    public ProductDeleteEvent(String categoryName, String productName) {
        super(true); // async
        this.categoryName = categoryName;
        this.productName = productName;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getProductName() {
        return productName;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
