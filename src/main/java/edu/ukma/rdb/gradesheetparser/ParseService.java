package edu.ukma.rdb.gradesheetparser;

import edu.ukma.rdb.gradesheetparser.exceptions.ParseStructuralError;
import edu.ukma.rdb.gradesheetparser.models.Bigunets;
import edu.ukma.rdb.gradesheetparser.models.ChadStudentsSheet;
import edu.ukma.rdb.gradesheetparser.models.GradeSheet;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ParseService implements IParser {
    private static final Map<String, Integer> MONTHS_MAP = new HashMap<String, Integer>() {{
        put("січня", 1);
        put("лютого", 2);
        put("березня", 3);
        put("квітня", 4);
        put("травня", 5);
        put("червня", 6);
        put("липня", 7);
        put("серпня", 8);
        put("вересня", 9);
        put("жовтня", 10);
        put("листопада", 11);
        put("грудня", 12);
    }};

    // https://www.baeldung.com/pdf-conversions-java
    @Override
    public GradeSheet parse(MultipartFile input) throws IOException {
        try (InputStream fileStream = input.getInputStream()) {
            PDDocument document = PDDocument.load(fileStream);
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(document)
                    .replaceAll("(_+)|(\\s{2,})", " ");
            GradeSheet sheet = identifySheet(text);

            setSheetCode(text, sheet);
            setOkr(text, sheet);
            setFaculty(text, sheet);
            setEduYear(text, sheet);
            setGroup(text, sheet);
            setSubject(text, sheet);
            setTerm(text, sheet);
            setCreditPoints(text, sheet);
            setControlForm(text, sheet);
            setDate(text, sheet);
            setTeacherName(text, sheet);
            setTeacherRank(text, sheet);
            setData(text, sheet);
            setDean(text, sheet);

            if (sheet instanceof ChadStudentsSheet) {

            } else {

            }

            return sheet;
        }
    }



    private void setDean(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)Декан факультету((\\s+\\p{IsCyrillic}+){3})");
        Matcher m = p.matcher(text);
        if (!m.find()) throw new ParseStructuralError("Відсутній декан.");
        sheet.setDean(m.group(1).trim());
    }

    private void setData(String text, GradeSheet sheet) {
    }

    private void setTeacherRank(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu),(.+?)прізвище");
        Matcher m = p.matcher(text);
        if (!m.find()) throw new ParseStructuralError("Відсутні ПІБ викладача.");
        List<String> ranks = new ArrayList<>(m.groupCount());
        for (String token : m.group(1).split(","))
            ranks.add(token.trim());
        sheet.setTeacherRank(ranks);
    }

    private void setTeacherName(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)р\\.?\\s*((\\b\\p{IsCyrillic}+\\s*){3}),");
        Matcher m = p.matcher(text);
        if (!m.find()) throw new ParseStructuralError("Відсутні ПІБ викладача.");
        sheet.setTeacherName(m.group(1));
    }

    private void setDate(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)дата\\P{IsCyrillic}*?(\\d{2})\\P{IsCyrillic}*(\\p{IsCyrillic}+)\\s*(\\d{4})");
        Matcher m = p.matcher(text);
        if (!m.find()) throw new ParseStructuralError("Відсутня або неповна дата.");
        LocalDate date = LocalDate.of(Integer.parseInt(m.group(3)), MONTHS_MAP.get(m.group(2)), Integer.parseInt(m.group(1)));
        sheet.setDate(date);
    }

    private void setControlForm(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)форма контролю(:)?\\s*(\\p{IsCyrillic}+)\\b");
        Matcher m = p.matcher(text);
        if (!m.find()) throw new ParseStructuralError("Відсутня форма контролю.");
        sheet.setControlForm(m.group(1));
    }

    private void setCreditPoints(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)залікові бали\\s*(\\d)");
        Matcher m = p.matcher(text);
        if (!m.find()) throw new ParseStructuralError("Відсутні залікові бали.");
        sheet.setCreditPoints(Integer.parseInt(m.group(1)));
    }

    private void setTerm(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)семестр\\s*(\\d\\p{IsCyrillic}?)");
        Matcher m = p.matcher(text);
        if (!m.find()) throw new ParseStructuralError("Відсутня семестр.");
        sheet.setTerm(m.group(1));
    }

    private void setSubject(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)дисципліна\\s*((\\p{IsCyrillic}|\\s)+?)\\s*семестр");
        Matcher m = p.matcher(text);
        if (!m.find()) throw new ParseStructuralError("Відсутня дисципліна.");
        sheet.setSubject(m.group(1));
    }

    private void setGroup(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)група\\s*((\\p{IsCyrillic}|\\d)+)\\b");
        Matcher m = p.matcher(text);
        if (!m.find()) throw new ParseStructuralError("Відсутня група.");
        sheet.setGroup(m.group(1));
    }

    private void setEduYear(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)Рік навчання\\s*(\\d)");
        Matcher m = p.matcher(text);
        if (!m.find()) throw new ParseStructuralError("Відсутній рік навчання.");
        sheet.setEduYear(Integer.parseInt(m.group(1)));
    }

    private void setFaculty(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)(факультет\\s+(\\p{IsCyrillic}|\\s)+)\\s+рік");
        Matcher m = p.matcher(text);
        if (!m.find()) throw new ParseStructuralError("Відсутній факультет.");
        sheet.setFaculty(m.group(1));
    }

    private void setOkr(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)\\bосвітній рівень\\s*(\\p{IsCyrillic}+?)\\b");
        Matcher m = p.matcher(text);
        if (!m.find()) throw new ParseStructuralError("Відсутній освітній рівень.");
        sheet.setOkr(m.group(1));
    }

    private void setSheetCode(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)№\\s*(\\d+?)\\s*освітній"); // use \b?
        Matcher m = p.matcher(text);
        if (!m.find()) throw new ParseStructuralError("Відсутній номер відомості.");
        sheet.setSheetCode(Integer.parseInt(m.group(1)));
    }

    private GradeSheet identifySheet(String str) {
        Pattern p = Pattern.compile("(?iu)заліково[- ]?екзаменаційна\\s*відомість");
        Matcher matcher = p.matcher(str);
        if (matcher.find())
            return new ChadStudentsSheet();
        p = Pattern.compile("(?iu)заліково[- ]?екзаменаційний\\s*листок");
        matcher = p.matcher(str);
        if (matcher.find())
            return new Bigunets();
        throw new ParseStructuralError("Не вдалося визначити тип заліково-екзаменаційного документу.");
    }

}
