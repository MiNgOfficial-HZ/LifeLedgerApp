package com.lifebook.ledger.model;

public class TripRecord {
    public long id;
    public long tripId;
    public long amountCents;
    public String category;
    public String note;
    public String date;
    public long createdAt;

    public String categoryName() {
        return category == null || category.isEmpty() ? "其他" : category;
    }
}
