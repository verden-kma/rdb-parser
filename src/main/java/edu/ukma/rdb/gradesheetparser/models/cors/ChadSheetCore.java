package edu.ukma.rdb.gradesheetparser.models.cors;

import edu.ukma.rdb.gradesheetparser.models.GradeSheet;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ChadSheetCore extends GradeSheet {
    Integer present;
    Integer missing;
    Integer banned;
}
