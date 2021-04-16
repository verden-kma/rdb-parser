package edu.ukma.rdb.gradesheetparser.models;

import lombok.Data;

@Data
public class StudentData {
    private int ordinal;
    private String surname;
    private String firstName;
    private String lastName;
    private String nameError;
    private String bookNo;
    private String bookNoError;
    private Integer termGrade;
    private String termGradeError;
    private Integer examGrade;
    private String examGradeError;
    private Integer sum;
    private boolean sumHasError;
    private String nationalGrade;
    private boolean nationalGradeHasError;
    private Character ectsGrade;
    private boolean ectsGradeHasError; // ectsGradeIsCorrect
}
