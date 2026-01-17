package ru.minimalprice.minimalprice.features.price.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class CategoryCreateEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final String categoryName;

    public CategoryCreateEvent(String categoryName) {
        super(true); // Async event
        this.categoryName = categoryName;
    }

    public String getCategoryName() {
        return categoryName;
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
