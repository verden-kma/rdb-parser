package edu.ukma.rdb.gradesheetparser.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.annotation.Nullable;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class CustomDate {
    @Nullable
    private Integer day;
    @Nullable
    private String month;
    @Nullable
    private Integer year;
}
