package edu.ukma.rdb.gradesheetparser.models;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ChadStudentsSheet extends GradeSheet {
    public ChadStudentsSheet() {
        sheetType = "general";
    }

    int presentStudents;
    boolean presentStudentsIsCorrect;
    int missingStudents;
    boolean missingStudentsIsCorrect;
    int bannedStudents;
    boolean bannedStudentsIsCorrect;
}
