package com.lifebook.ledger.model;

/** 分类月度预算 */
public class Budget {
    public long id;
    public String book;
    public String type;
    public long cat1Id;
    public String cat1Name;
    public long amountCents;
    public long createdAt;

    public Budget() {
    }

    public Budget(long id, String book, String type, long cat1Id, String cat1Name, long amountCents) {
        this.id = id;
        this.book = book;
        this.type = type;
        this.cat1Id = cat1Id;
        this.cat1Name = cat1Name;
        this.amountCents = amountCents;
    }
}
