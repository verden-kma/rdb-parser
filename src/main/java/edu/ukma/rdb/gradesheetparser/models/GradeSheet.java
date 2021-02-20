package edu.ukma.rdb.gradesheetparser.models;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class GradeSheet {
    String sheetType;
    int sheetCode;
    String okr;
    String faculty;
    int eduYear;
    String group;
    String subject;
    String term;
    int creditPoints;
    String controlForm;
    LocalDate date;
    String teacherName;
    List<String> teacherRank;
    List<StudentData> data;
    String dean;

    public void addStudentData(StudentData std) {
        if (data == null) data = new ArrayList<>();
        data.add(std);
    }
}
