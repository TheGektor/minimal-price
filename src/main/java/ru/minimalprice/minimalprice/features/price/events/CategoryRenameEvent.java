package ru.minimalprice.minimalprice.features.price.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class CategoryRenameEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final String oldName;
    private final String newName;

    public CategoryRenameEvent(String oldName, String newName) {
        super(true); // Async event
        this.oldName = oldName;
        this.newName = newName;
    }

    public String getOldName() {
        return oldName;
    }

    public String getNewName() {
        return newName;
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
