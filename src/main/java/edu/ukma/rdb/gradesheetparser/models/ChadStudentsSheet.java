package edu.ukma.rdb.gradesheetparser.models;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ChadStudentsSheet extends GradeSheet {
    public ChadStudentsSheet() {
        sheetType = "general";
    }

    private Integer presentStudents;
    private boolean presentStudentsIsCorrect;
    private Integer missingStudents;
    private boolean missingStudentsIsCorrect;
    private Integer bannedStudents;
    private boolean bannedStudentsIsCorrect;
}

