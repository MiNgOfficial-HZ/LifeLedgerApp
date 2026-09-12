package com.lifebook.ledger.model;

/** 账本间转账：不产生收入/支出，只改变两本账的资金分布 */
public class Transfer {
    public long id;
    public String fromBook;
    public String toBook;
    public long amountCents;
    public String date;
    public String note;
    public long createdAt;
    public long updatedAt;

    public Transfer() {
    }

    public Transfer(long id, String fromBook, String toBook, long amountCents, String date, String note) {
        this.id = id;
        this.fromBook = fromBook;
        this.toBook = toBook;
        this.amountCents = amountCents;
        this.date = date;
        this.note = note;
    }

    public boolean deleted;
}
