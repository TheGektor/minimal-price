package ru.minimalprice.minimalprice.features.price.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class CategoryDeleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String categoryName;

    public CategoryDeleteEvent(String categoryName) {
        super(true); // async
        this.categoryName = categoryName;
    }

    public String getCategoryName() {
        return categoryName;
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
