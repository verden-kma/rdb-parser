package edu.ukma.rdb.gradesheetparser.models;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Bigunets extends GradeSheet {
    String cause;
    boolean causeChanged;
    String causeError;
    CustomDate expires;
    boolean expiresChanged;
    String expiresError;

    public Bigunets() {
        sheetType = "ЗАЛІКОВО-ЕКЗАМЕНАЦІЙНИЙ ЛИСТОК";
    }

    @Override
    public void resetDefaults() {
        super.resetDefaults();
        causeError = null;
        expiresError = null;

        causeChanged = false;
        expiresChanged = false;
    }
}
