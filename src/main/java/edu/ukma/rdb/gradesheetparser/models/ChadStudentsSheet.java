package edu.ukma.rdb.gradesheetparser.models;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ChadStudentsSheet extends GradeSheet {
    public ChadStudentsSheet() {
        sheetType = "ЗАЛІКОВО-ЕКЗАМЕНАЦІЙНА ВІДОМІСТЬ";
    }

    private Integer present;
    private boolean presentIsCorrect;
    private Integer missing;
    private boolean missingIsCorrect;
    private Integer banned;
    private boolean bannedIsCorrect;
}

