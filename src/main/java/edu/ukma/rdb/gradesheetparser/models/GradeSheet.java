package edu.ukma.rdb.gradesheetparser.models;

import lombok.Data;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

@Data
public class GradeSheet {
    protected String sheetType;
    @Nullable
    private String fileName;
    private Integer sheetCode;
    boolean sheetCodeChanged;
    private String sheetCodeError;
    private String okr;
    boolean okrChanged;
    private String okrError;
    private String faculty;
    boolean facultyChanged;
    private String facultyError;
    private Integer eduYear;
    boolean eduYearChanged;
    private String eduYearError;
    private String group;
    boolean groupChanged;
    private String groupError;
    private String subject;
    boolean subjectChanged;
    private String subjectError;
    private String term;
    boolean termChanged;
    private String termError;
    private Integer creditPoints;
    boolean creditPointsChanged;
    private String creditPointsError;
    private String controlForm;
    boolean controlFormChanged;
    private String controlFormError;
    private CustomDate date;
    boolean dateChanged;
    private String dateError;
    private String teacherSurname;
    boolean teacherSurnameChanged;
    private String teacherFirstname;
    boolean teacherFirstnameChanged;
    private String teacherLastname;
    boolean teacherLastnameChanged;
    private String teacherNameError;
    private List<String> teacherRank;
    boolean teacherRankChanged;
    private String teacherRankError;
    boolean deanChanged;
    private String dean;
    private List<StudentData> studentsData;
    private String deanError;
    private Boolean isValid;

    public void setIsValid(boolean v) {
        isValid = v;
    }

    public void addStudentData(StudentData std) {
        if (studentsData == null) studentsData = new ArrayList<>();
        studentsData.add(std);
    }

    public void resetDefaults() {
        sheetCodeError = null;
        okrError = null;
        facultyError = null;
        eduYearError = null;
        groupError = null;
        subjectError = null;
        termError = null;
        creditPointsError = null;
        controlFormError = null;
        dateError = null;
        teacherNameError = null;
        teacherRank = null;
        teacherRankError = null;
        deanError = null;
        studentsData.forEach(StudentData::resetDefaults);

        sheetCodeChanged = false;
        okrChanged = false;
        facultyChanged = false;
        eduYearChanged = false;
        groupChanged = false;
        subjectChanged = false;
        termChanged = false;
        creditPointsChanged = false;
        controlFormChanged = false;
        dateChanged = false;
        teacherSurnameChanged = false;
        teacherFirstnameChanged = false;
        teacherLastnameChanged = false;
        teacherRankChanged = false;
        deanChanged = false;
    }
}
