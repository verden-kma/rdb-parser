package edu.ukma.rdb.gradesheetparser;

import edu.ukma.rdb.gradesheetparser.models.GradeSheet;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class Controller {
    private final IParser parser;

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GradeSheet receiveParseRequest(@RequestPart MultipartFile pdfInput) throws IOException {
        return parser.parse(pdfInput);
    }
}
