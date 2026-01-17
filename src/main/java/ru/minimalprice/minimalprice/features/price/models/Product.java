package ru.minimalprice.minimalprice.features.price.models;

public class Product {
    private final int id;
    private final int categoryId;
    private final String name;
    private final double price;

    public Product(int id, int categoryId, String name, double price) {
        this.id = id;
        this.categoryId = categoryId;
        this.name = name;
        this.price = price;
    }

    public int getId() {
        return id;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }
}
