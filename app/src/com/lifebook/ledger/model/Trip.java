package com.lifebook.ledger.model;

public class Trip {
    public long id;
    public String name;
    public String startDate;
    public String endDate;
    public String note;
    public boolean finished;
    public boolean addedToMain;
    public String addedBook = "main";
    public long addedRecordId;
    public long createdAt;
    public long updatedAt;

    public boolean isOngoing() {
        return !finished;
    }

    public String bookLabel() {
        return "vault".equals(addedBook) ? "小金库" : "主账本";
    }
}
