package edu.ukma.rdb.gradesheetparser.models;

import lombok.Data;

@Data
public class StudentData {
    private int ordinal;
    private String name;
    private String bookNo;
    private Integer termGrade;
    private Integer examGrade;
    private Integer sum;
    private boolean sumIsCorrect;
    private String nationalGrade;
    private boolean nationalGradeIsCorrect;
    private Character ectsGrade;
    private boolean ectsGradeIsCorrect;
}
