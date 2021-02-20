package edu.ukma.rdb.gradesheetparser.models;

import lombok.Data;

@Data
public class StudentData {
    int ordinal;
    String name;
    String bookNo;
    int termGrade;
    int examGrade;
    int sum;
    boolean sumIsCorrect;
    String nationalGrade;
    boolean nationalGradeIsCorrect;
    Character ectsGrade;
    boolean ectsGradeIsCorrect;
}
