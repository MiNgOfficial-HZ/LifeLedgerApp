package com.lifebook.ledger.model;

/** 周期记账：每月某天自动生成一笔记录 */
public class Recurring {
    public long id;
    public String type;
    public long amountCents;
    public long cat1Id;
    public String cat1Name;
    public long cat2Id;
    public String cat2Name;
    public String note;
    public String book;
    public int dayOfMonth;
    public boolean enabled;
    public String lastRun;
    public long createdAt;
    public long updatedAt;

    public Recurring() {
    }

    public String title() {
        if (cat2Name != null && !cat2Name.isEmpty()) {
            return (cat1Name == null || cat1Name.isEmpty() ? "未分类" : cat1Name) + " · " + cat2Name;
        }
        return cat1Name == null || cat1Name.isEmpty() ? "未分类" : cat1Name;
    }
}
