package edu.ukma.rdb.gradesheetparser;

import edu.ukma.rdb.gradesheetparser.models.Bigunets;
import edu.ukma.rdb.gradesheetparser.models.ChadSheetCore;
import edu.ukma.rdb.gradesheetparser.models.ChadStudentsSheet;
import edu.ukma.rdb.gradesheetparser.models.GradeSheet;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class Controller {
    private final IParser parser;

    @CrossOrigin
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GradeSheet receiveParseRequest(@RequestPart MultipartFile pdfInput) throws IOException {
        return parser.parse(pdfInput);
    }

    @CrossOrigin
    @PostMapping("/check-chad-student-sheet")
    public ChadStudentsSheet checkSessionEnjoyer(@RequestBody ChadSheetCore sheet) {
        sheet.resetErrors();
        sheet.setIsValid(true);
        return parser.validate(sheet);
    }

    @CrossOrigin
    @PostMapping("/check-bigunets")
    public Bigunets checkSessionFan(@RequestBody Bigunets bigunets) {
        bigunets.resetErrors();
        bigunets.setIsValid(true);
        return parser.validate(bigunets);
    }
}
