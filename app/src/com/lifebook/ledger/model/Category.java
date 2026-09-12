package com.lifebook.ledger.model;

public class Category {
    public long id;
    public String type;
    public long parentId;
    public String name;
    public int sort;

    public Category() {
    }

    public Category(long id, String type, long parentId, String name, int sort) {
        this.id = id;
        this.type = type;
        this.parentId = parentId;
        this.name = name;
        this.sort = sort;
    }
}
