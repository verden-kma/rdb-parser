package edu.ukma.rdb.gradesheetparser;

import edu.ukma.rdb.gradesheetparser.exceptions.ParseStructuralError;
import edu.ukma.rdb.gradesheetparser.models.Bigunets;
import edu.ukma.rdb.gradesheetparser.models.ChadStudentsSheet;
import edu.ukma.rdb.gradesheetparser.models.GradeSheet;
import edu.ukma.rdb.gradesheetparser.models.StudentData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

    private static final Map<Character, Function<Integer, Boolean>> ECTS_ASSERTS =
            new HashMap<Character, Function<Integer, Boolean>>() {{
                put('A', (Integer grade) -> grade > 90);
                put('B', (Integer grade) -> grade > 80 && grade < 91);
                put('C', (Integer grade) -> grade > 70 && grade < 81);
                put('D', (Integer grade) -> grade > 65 && grade < 71);
                put('E', (Integer grade) -> grade >= 60 && grade <= 65);
                put('F', (Integer grade) -> grade < 60);
            }};

    private static final Map<String, Set<String>> NATIONAL_GRADES = new HashMap<String, Set<String>>() {{
        put("залік", new HashSet<String>() {{
            add("Зараховано");
            add("Не зараховано");
            add("Не відвідував");
            add("Не допущений");
        }});
        put("екзамен", new HashSet<String>() {{
            add("Відмінно");
            add("Добре");
            add("Задовільно");
            add("Не відвідував");
            add("Не допущений");
        }});
    }};

    private final static String LINE_SEPARATOR = System.getProperty("line.separator");

    @Override
    public GradeSheet parse(MultipartFile input) throws IOException {
        try (InputStream fileStream = input.getInputStream()) {
            PDDocument document = PDDocument.load(fileStream);
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(document);

            String startHook = "Підпис " + LINE_SEPARATOR + "викладача";
            int tableStart = text.indexOf(startHook);
            int tableEnd = text.indexOf('*');
            String table = text.substring(tableStart + startHook.length(), tableEnd).trim();
            text = text.replaceAll("(_+)|(\\s{2,})", " ");

            GradeSheet sheet = identifySheet(text);
//            System.out.println(text);

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
            setData(table, sheet);
            setDean(text, sheet);

            if (sheet instanceof ChadStudentsSheet) {
                ChadStudentsSheet chadSheet = (ChadStudentsSheet) sheet;

            } else {
                Bigunets bigunetsSheet = (Bigunets) sheet;
                setCause(text, bigunetsSheet);
                setExpiration(text, bigunetsSheet);
            }

            return sheet;
        }
    }

    private void setExpiration(String text, Bigunets bigunetsSheet) {
        Pattern p = Pattern.compile("(?iu)дійсне до\\P{IsCyrillic}*?(\\d{2})\\P{IsCyrillic}*(\\p{IsCyrillic}+)\\s*(\\d{4})");
        Matcher m = p.matcher(text);
        if (!m.find()) throw new ParseStructuralError("Відсутня або неповна дата направлення.");
        LocalDate date = LocalDate.of(Integer.parseInt(m.group(3)), MONTHS_MAP.get(m.group(2)), Integer.parseInt(m.group(1)));
        bigunetsSheet.setDate(date);
    }

    private void setCause(String text, Bigunets bigunetsSheet) {
        Pattern p = Pattern.compile("(?iu)причина перенесення((\\p{IsCyrillic}|\\s)+)\\b\\s*форма");
        Matcher m = p.matcher(text);
        if (!m.find()) throw new ParseStructuralError("Відсутня причина перенесення.");
        bigunetsSheet.setCause(m.group(1).trim());
    }

    private void setDean(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)декан факультету((\\s+\\p{IsCyrillic}+){3})");
        Matcher m = p.matcher(text);
        if (!m.find()) throw new ParseStructuralError("Відсутній декан.");
        sheet.setDean(m.group(1).trim());
    }

    private void setData(String text, GradeSheet sheet) {
        Stream.of(text.split(LINE_SEPARATOR))
                .forEach(datum -> {
                    Pattern p = Pattern.compile("(\\d+)\\s+((\\p{IsCyrillic}{2,}\\s){2,})(.+?)(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\p{IsCyrillic}+)\\s+(\\w)");
                    Matcher m = p.matcher(datum);
                    if (!m.find()) throw new ParseStructuralError("Failed to parse student data.");
                    StudentData std = new StudentData();
                    std.setOrdinal(Integer.parseInt(m.group(1)));
                    std.setName(m.group(2).trim());
                    std.setBookNo(m.group(4).trim());
                    std.setTermGrade(Integer.parseInt(m.group(5)));
                    std.setExamGrade(Integer.parseInt(m.group(6)));
                    std.setSum(Integer.parseInt(m.group(7)));
                    if (std.getSum() == std.getTermGrade() + std.getExamGrade())
                        std.setSumIsCorrect(true);
                    std.setNationalGrade(m.group(8));
                    if (NATIONAL_GRADES.get(sheet.getControlForm().toLowerCase()).contains(std.getNationalGrade()))
                        std.setNationalGradeIsCorrect(true);
                    std.setEctsGrade(m.group(9).charAt(0));
                    if (ECTS_ASSERTS.get(std.getEctsGrade()).apply(std.getSum()))
                        std.setEctsGradeIsCorrect(true);
                    sheet.addStudentData(std);
                });
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
        sheet.setControlForm(m.group(2));
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
