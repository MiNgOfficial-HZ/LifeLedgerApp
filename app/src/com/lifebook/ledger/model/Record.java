package com.lifebook.ledger.model;

public class Record {
    public long id;
    public String type;
    public long amountCents;
    public long cat1Id;
    public String cat1Name;
    public long cat2Id;
    public String cat2Name;
    public String date;
    public String note;
    public String book = "main";
    public boolean deleted;
    public long createdAt;
    public long updatedAt;

    public boolean isIncome() {
        return "income".equals(type);
    }

    public String title() {
        if (cat2Name != null && !cat2Name.isEmpty()) {
            return (cat1Name == null || cat1Name.isEmpty() ? "未分类" : cat1Name) + " · " + cat2Name;
        }
        return cat1Name == null || cat1Name.isEmpty() ? "未分类" : cat1Name;
    }
}
