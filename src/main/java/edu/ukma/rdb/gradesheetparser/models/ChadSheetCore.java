package edu.ukma.rdb.gradesheetparser.models;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ChadSheetCore extends GradeSheet {
    Integer present;
    Integer missing;
    Integer banned;
}
