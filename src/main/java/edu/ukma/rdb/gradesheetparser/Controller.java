package edu.ukma.rdb.gradesheetparser;

import edu.ukma.rdb.gradesheetparser.models.Bigunets;
import edu.ukma.rdb.gradesheetparser.models.ChadStudentsSheet;
import edu.ukma.rdb.gradesheetparser.models.GradeSheet;
import edu.ukma.rdb.gradesheetparser.models.cors.ChadSheetCore;
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
        return parser.validate(sheet);
    }

    @CrossOrigin
    @PostMapping("/check-bigunets")
    public Bigunets checkSessionFan(@RequestBody Bigunets bigunets) {
        return parser.validate(bigunets);
    }
}
