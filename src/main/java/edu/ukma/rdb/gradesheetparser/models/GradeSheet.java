package edu.ukma.rdb.gradesheetparser.models;

import lombok.Data;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class GradeSheet {
    protected String sheetType;
    @Nullable
    private String fileName;
    private Integer sheetCode;
    private String okr;
    private String faculty;
    private Integer eduYear;
    private String group;
    private String subject;
    private String term;
    private Integer creditPoints;
    private String controlForm;
    private LocalDate date;
    private String teacherName;
    private List<String> teacherRank;
    private List<StudentData> data;
    private String dean;
    private List<String> errors;

    public void addStudentData(StudentData std) {
        if (data == null) data = new ArrayList<>();
        data.add(std);
    }

    public void addError(String message) {
        if (errors == null) errors = new ArrayList<>();
        errors.add(message);
    }
}
