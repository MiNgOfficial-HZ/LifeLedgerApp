package com.lifebook.ledger.model;

/** 账本：主账本 / 小金库为内置，其余为用户自行创建 */
public class Book {
    public long id;
    public String key;
    public String name;
    public int sort;
    public boolean builtin;

    public Book() {
    }

    public Book(long id, String key, String name, int sort, boolean builtin) {
        this.id = id;
        this.key = key;
        this.name = name;
        this.sort = sort;
        this.builtin = builtin;
    }
}
