package edu.ukma.rdb.gradesheetparser;

import edu.ukma.rdb.gradesheetparser.models.GradeSheet;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface IParser {
    GradeSheet parse(MultipartFile input) throws IOException;
}
