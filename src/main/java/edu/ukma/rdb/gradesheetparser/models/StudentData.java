package edu.ukma.rdb.gradesheetparser.models;

import lombok.Data;

@Data
public class StudentData {
    private int ordinal;
    private String name;
    private String nameError;
    private String bookNo;
    private String bookNoError;
    private Integer termGrade;
    private String termGradeError;
    private Integer examGrade;
    private String examGradeError;
    private Integer sum;
    private boolean sumIsCorrect;
    private String nationalGrade;
    private boolean nationalGradeIsCorrect;
    private Character ectsGrade;
    private boolean ectsGradeIsCorrect;
}
