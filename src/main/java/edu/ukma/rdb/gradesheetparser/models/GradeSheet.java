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
    private String sheetCodeError;
    private String okr;
    private String okrError;
    private String faculty;
    private String facultyError;
    private Integer eduYear;
    private String eduYearError;
    private String group;
    private String groupError;
    private String subject;
    private String subjectError;
    private String term;
    private String termError;
    private Integer creditPoints;
    private String creditPointsError;
    private String controlForm;
    private String controlFormError;
    private CustomDate date;
    private String dateError;
    private String teacherSurname;
    private String teacherFirstname;
    private String teacherLastname;
    private String teacherNameError;
    private List<String> teacherRank;
    private String teacherRankError;
    private List<StudentData> data;
    private String dean;
    private String deanError;
    private Boolean isValid;

    public void setIsValid(boolean v) {
        isValid = v;
    }

    public void addStudentData(StudentData std) {
        if (data == null) data = new ArrayList<>();
        data.add(std);
    }
}
