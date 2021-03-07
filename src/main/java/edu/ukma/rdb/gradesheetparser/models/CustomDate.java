package edu.ukma.rdb.gradesheetparser.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class CustomDate {
    private int day;
    private String month;
    private int year;
}
