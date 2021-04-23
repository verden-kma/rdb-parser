package edu.ukma.rdb.gradesheetparser.models;

import lombok.Data;

@Data
public class StudentData {
    private int ordinal;
    private String surname;
    private boolean surnameChanged;
    private String firstName;
    private boolean firstNameChanged;
    private String lastName;
    private boolean lastNameChanged;
    private String nameError;
    private String bookNo;
    private boolean bookNoChanged;
    private String bookNoError;
    private Integer termGrade;
    private boolean termGradeChanged;
    private String termGradeError;
    private Integer examGrade;
    private boolean examGradeChanged;
    private String examGradeError;
    private Integer sum;
    private boolean sumChanged;
    private boolean sumError;
    private String nationalGrade;
    private boolean nationalGradeChanged;
    private boolean nationalGradeError;
    private Character ectsGrade;
    private boolean ectsGradeChanged;
    private boolean ectsGradeError;

    public void resetDefaults() {
        nameError = null;
        bookNoError = null;
        termGradeError = null;
        examGradeError = null;
        sumError = false;
        nationalGradeError = false;
        ectsGradeError = false;

        surnameChanged = false;
        firstNameChanged = false;
        lastNameChanged = false;
        bookNoChanged = false;
        termGradeChanged = false;
        examGradeChanged = false;
        sumChanged = false;
        nationalGradeChanged = false;
        ectsGradeChanged = false;
    }
}
