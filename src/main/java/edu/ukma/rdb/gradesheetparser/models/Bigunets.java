package edu.ukma.rdb.gradesheetparser.models;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Bigunets extends GradeSheet {
    String cause;
    String causeError;
    CustomDate expires;
    String expiresError;

    public Bigunets() {
        sheetType = "ЗАЛІКОВО-ЕКЗАМЕНАЦІЙНИЙ ЛИСТОК";
    }
}
