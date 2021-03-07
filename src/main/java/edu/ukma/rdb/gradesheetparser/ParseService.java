package edu.ukma.rdb.gradesheetparser;

import edu.ukma.rdb.gradesheetparser.exceptions.ParseStructuralError;
import edu.ukma.rdb.gradesheetparser.models.*;
import edu.ukma.rdb.gradesheetparser.models.cors.ChadSheetCore;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
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
            add("Незадовільно");
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
            sheet.setFileName(input.getOriginalFilename());

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
                setPresent(text, chadSheet);
                setMissing(text, chadSheet);
                setBanned(text, chadSheet);
            } else {
                Bigunets bigunetsSheet = (Bigunets) sheet;
                setCause(text, bigunetsSheet);
                setExpiration(text, bigunetsSheet);
            }

            return sheet;
        }
    }

    private void setBanned(String text, ChadStudentsSheet chadSheet) {
        long tableBanned = chadSheet.getData().stream()
                .filter(studentData -> studentData.getNationalGrade().equalsIgnoreCase("Не допущений")).count();
        Pattern p = Pattern.compile("(?iu)кількість студентів, недопущених до екзамену\\s*/тези\\s*/заліку\\s*(\\d+)");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            chadSheet.setBannedIsCorrect(false);
            return;
        }
        int suggestedBanned = Integer.parseInt(m.group(1));
        chadSheet.setBanned(suggestedBanned);
        chadSheet.setBannedIsCorrect(suggestedBanned == tableBanned);
    }

    private void setMissing(String text, ChadStudentsSheet chadSheet) {
        long tableAbsent = chadSheet.getData().stream()
                .filter(studentData -> studentData.getNationalGrade().equalsIgnoreCase("Не відвідував")).count();
        Pattern p = Pattern.compile("(?iu)кількість студентів, які не з’явились на екзамен\\s*/тезу\\s*/залік\\s*(\\d+)");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            chadSheet.setMissingIsCorrect(false);
            return;
        }
        int suggestedAbsent = Integer.parseInt(m.group(1));
        chadSheet.setMissing(suggestedAbsent);
        chadSheet.setMissingIsCorrect(suggestedAbsent == tableAbsent);
    }

    private void setPresent(String text, ChadStudentsSheet chadSheet) {
        long tablePresent = chadSheet.getData().stream()
                .filter(studentData -> !(studentData.getNationalGrade().equalsIgnoreCase("Не відвідував")
                        || studentData.getNationalGrade().equalsIgnoreCase("Не допущений"))).count();
        Pattern p = Pattern.compile("(?iu)Кількість студентів на екзамені\\s*/тезі\\s*/заліку\\s*(\\d+)");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            chadSheet.setPresentIsCorrect(false);
            return;
        }
        int suggestedPresent = Integer.parseInt(m.group(1));
        chadSheet.setPresent(suggestedPresent);
        chadSheet.setPresentIsCorrect(suggestedPresent == tablePresent);
    }

    private void setExpiration(String text, Bigunets bigunetsSheet) {
        Pattern p = Pattern.compile("(?iu)дійсне до\\P{IsCyrillic}*?(\\d{2})\\P{IsCyrillic}*(\\p{IsCyrillic}+)\\s*(\\d{4})");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            bigunetsSheet.setExpiresError("Відсутня або неповна дата дійсності направлення.");
            return;
        }
        bigunetsSheet.setExpires(new CustomDate(Integer.parseInt(m.group(1)), m.group(2).trim(), Integer.parseInt(m.group(3))));
    }

    private void setCause(String text, Bigunets bigunetsSheet) {
        Pattern p = Pattern.compile("(?iu)причина перенесення((\\p{IsCyrillic}|\\s)+)\\b\\s*форма");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            bigunetsSheet.setExpiresError("Відсутня причина перенесення.");
            return;
        }
        bigunetsSheet.setCause(m.group(1).trim());
    }

    private void setDean(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)декан факультету((\\s+\\p{IsCyrillic}+){3})");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setDeanError("Відсутній декан.");
            return;
        }
        sheet.setDean(m.group(1).trim());
    }

    private void setData(String text, GradeSheet sheet) {
        Stream.of(text.split(LINE_SEPARATOR))
                .forEach(datum -> {
                    Pattern p = Pattern.compile("(\\d+)\\s+((\\p{IsCyrillic}{2,}\\s){2,})(.+?)(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\p{IsCyrillic}+)\\s+(\\w)");
                    Matcher m = p.matcher(datum);
                    if (!m.find()) {
                        return;
                    }
                    StudentData std = new StudentData();
                    std.setOrdinal(Integer.parseInt(m.group(1)));
                    std.setName(m.group(2).trim());
                    std.setBookNo(m.group(4).trim());
                    std.setTermGrade(Integer.parseInt(m.group(5)));
                    std.setExamGrade(Integer.parseInt(m.group(6)));
                    std.setSum(Integer.parseInt(m.group(7)));
                    if ((std.getSum() != null && std.getTermGrade() != null && std.getExamGrade() != null) &&
                            (std.getSum() == std.getTermGrade() + std.getExamGrade()))
                        std.setSumIsCorrect(true);
                    std.setNationalGrade(m.group(8));
                    if (sheet.getControlForm() != null &&
                            NATIONAL_GRADES.get(sheet.getControlForm().toLowerCase()).contains(std.getNationalGrade()))
                        std.setNationalGradeIsCorrect(true);
                    std.setEctsGrade(m.group(9).charAt(0));
                    if (ECTS_ASSERTS.get(std.getEctsGrade()) != null &&
                            ECTS_ASSERTS.get(std.getEctsGrade()).apply(std.getSum()))
                        std.setEctsGradeIsCorrect(true);
                    sheet.addStudentData(std);
                });
    }

    private void setTeacherRank(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu),(.+?)прізвище");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setTeacherNameError("Відсутні ПІБ викладача.");
            return;
        }
        List<String> ranks = new ArrayList<>(m.groupCount());
        for (String token : m.group(1).split(","))
            ranks.add(token.trim());
        sheet.setTeacherRank(ranks);
    }

    private void setTeacherName(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)р\\.?\\s*((\\b\\p{IsCyrillic}+\\s*){3}),");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setTeacherNameError("Відсутні ПІБ викладача.");
            return;
        }
        sheet.setTeacherName(m.group(1));
    }

    private void setDate(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)дата\\P{IsCyrillic}*?(\\d{2})\\P{IsCyrillic}*(\\p{IsCyrillic}+)\\s*(\\d{4})");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setDateError("Відсутня або неповна дата.");
            return;
        }
//        LocalDate date = LocalDate.of(Integer.parseInt(m.group(4)), MONTHS_MAP.get(m.group(3)), Integer.parseInt(m.group(2)));
        sheet.setDate(new CustomDate(Integer.parseInt(m.group(1)), m.group(2).trim(), Integer.parseInt(m.group(3))));
    }

    private void setControlForm(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)форма контролю(:)?\\s*(\\p{IsCyrillic}+)\\b");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setControlFormError("Відсутня форма контролю.");
            return;
        }
        sheet.setControlForm(m.group(2));
    }

    private void setCreditPoints(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)залікові бали\\s*(\\d)");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setCreditPointsError("Відсутні залікові бали.");
            return;
        }
        sheet.setCreditPoints(Integer.parseInt(m.group(1)));
    }

    private void setTerm(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)семестр\\s*(\\d\\p{IsCyrillic}?)");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setTermError("Відсутня семестр.");
            return;
        }
        sheet.setTerm(m.group(1));
    }

    private void setSubject(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)дисципліна\\s*((\\p{IsCyrillic}|\\s)+?)\\s*семестр");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setSubjectError("Відсутня дисципліна.");
            return;
        }
        sheet.setSubject(m.group(1));
    }

    private void setGroup(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)група\\s*((\\p{IsCyrillic}|\\d)+)\\b");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setGroupError("Відсутня група.");
            return;
        }
        sheet.setGroup(m.group(1));
    }

    private void setEduYear(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)Рік навчання\\s*(\\d)");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setEduYearError("Відсутній рік навчання.");
            return;
        }
        sheet.setEduYear(Integer.parseInt(m.group(1)));
    }

    private void setFaculty(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)факультет\\s+((\\p{IsCyrillic}|\\s)+)\\s+рік");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setFacultyError("Відсутній факультет.");
            return;
        }
        sheet.setFaculty(m.group(1).trim());
    }

    private void setOkr(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)\\bосвітній рівень\\s*(\\p{IsCyrillic}+?)\\b");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setOkrError("Відсутній освітній рівень.");
            return;
        }
        sheet.setOkr(m.group(1));
    }

    private void setSheetCode(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)№\\s*(\\d+?)\\s*освітній"); // use \b?
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setSheetCodeError("Відсутній номер відомості.");
            return;
        }
        sheet.setSheetCode(Integer.parseInt(m.group(1)));
    }

    @Override
    public ChadStudentsSheet validate(ChadSheetCore input) {
        ChadSheetCore basicChecked = validateGradeSheet(input);
        ChadStudentsSheet chadSheet = new ChadStudentsSheet();
        BeanUtils.copyProperties(basicChecked, chadSheet);
        setPresent(chadSheet);
        setMissing(chadSheet);
        setBanned(chadSheet);
        return chadSheet;
    }

    private void setBanned(ChadStudentsSheet chadSheet) {
        if (chadSheet.getBanned() == null) {
            chadSheet.setBannedIsCorrect(false);
            return;
        }
        long dataBanned = chadSheet.getData().stream()
                .filter(std -> std.getNationalGrade() != null && std.getNationalGrade().equalsIgnoreCase("Не допущений")).count();
        chadSheet.setBannedIsCorrect(chadSheet.getBanned() == dataBanned);
    }

    private void setMissing(ChadStudentsSheet chadSheet) {
        if (chadSheet.getMissing() == null) {
            chadSheet.setMissingIsCorrect(false);
            return;
        }
        long dataMissing = chadSheet.getData().stream()
                .filter(std -> std.getNationalGrade() != null && std.getNationalGrade().equalsIgnoreCase("Не відвідував")).count();
        chadSheet.setMissingIsCorrect(chadSheet.getMissing() == dataMissing);
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

    private void setPresent(ChadStudentsSheet chadSheet) {
        if (chadSheet.getPresent() == null) {
            chadSheet.setPresentIsCorrect(false);
            return;
        }
        long dataPresent = chadSheet.getData().stream()
                .filter(std -> std.getNationalGrade() != null && !(std.getNationalGrade().equalsIgnoreCase("Не відвідував")
                        || std.getNationalGrade().equalsIgnoreCase("Не допущений"))).count();
        chadSheet.setMissingIsCorrect(chadSheet.getMissing() == dataPresent);
    }

    @Override
    public Bigunets validate(Bigunets input) {
        Bigunets basicChecked = validateGradeSheet(input);
        if (input.getCause() == null || input.getCause().matches("\\s*"))
            input.setCauseError("Причина перенесення не вказана.");
        if (basicChecked.getExpires() == null) input.setExpiresError("Дата 'дісне до' не вказана.");
        return basicChecked;
    }

    private <T extends GradeSheet> T validateGradeSheet(T sheet) {
        if (sheet.getSheetCode() == null) sheet.setSheetCodeError("Бракує коду відомості.");
        if (sheet.getOkr() == null || !(sheet.getOkr().equalsIgnoreCase("бакалавр")
                || sheet.getOkr().equalsIgnoreCase("магістр")))
            sheet.setOkrError("Хибник освітній рівень, допустимі: бакалавр/магістр.");
        if (sheet.getFaculty() == null || sheet.getFaculty().matches("\\s*"))
            sheet.setFacultyError("Відсутній факультет.");
        if (sheet.getEduYear() == null || sheet.getEduYear() < 1 || sheet.getEduYear() > 6)
            sheet.setEduYearError("Допустимі роки навчання - від 1 до 6.");
        if (sheet.getGroup() == null || sheet.getGroup().matches("\\s*")) sheet.setGroupError("Група не вказана.");
        if (sheet.getSubject() == null || sheet.getSubject().matches("\\s*"))
            sheet.setSubjectError("Предмет не вказаний.");
        if (sheet.getTerm() == null || !sheet.getTerm().matches("(?u)\\dд?"))
            sheet.setTermError("Семестр має бути вказаний у форматі '<цифра>[д]'");
        if (sheet.getCreditPoints() == null) sheet.setCreditPointsError("Кількість кредитів не вказана.");
        if (sheet.getControlForm() == null || !(sheet.getControlForm().equalsIgnoreCase("залік")
                || sheet.getControlForm().equalsIgnoreCase("екзамен")))
            sheet.setControlFormError("Допустимі форми контролю - 'залік' або 'екзамен'.");
        if (sheet.getDate() == null)
            sheet.setDateError("Дата оцінювання має бути вказана, майбутні дати не допускаються.");
        if (sheet.getTerm() == null || sheet.getTeacherName().matches("\\s*"))
            sheet.setTeacherNameError("ПІБ викладача відсутні.");
        if (sheet.getDean() == null || sheet.getDean().matches("\\s*")) sheet.setDeanError("ПІБ декана відсутні.");

        validateStudentData(sheet);
        return sheet;
    }

    private void validateStudentData(GradeSheet sheet) {
        sheet.getData().forEach(std -> {
            if (std.getName() == null || std.getName().matches("\\s*"))
                std.setNameError("Відсутнє ім'я в номера " + std.getOrdinal());
            if (std.getBookNo() == null || std.getBookNo().matches("\\s*"))
                std.setBookNoError("Відсутній код залікової книжки у номера " + std.getOrdinal());

            if (std.getTermGrade() == null)
                std.setTermGradeError("Нема оцінки за трим.");

            if (std.getExamGrade() == null)
                std.setExamGradeError("Нема оцінки за залік/екзамен.");

            if (std.getSum() != null && std.getExamGrade() != null && std.getEctsGrade() != null
                    && std.getSum() == std.getTermGrade() + std.getExamGrade())
                std.setSumIsCorrect(true);

            if (sheet.getControlForm() != null && NATIONAL_GRADES.get(sheet.getControlForm().toLowerCase()).contains(std.getNationalGrade()))
                std.setNationalGradeIsCorrect(true);
            if (std.getExamGrade() != null && ECTS_ASSERTS.get(std.getEctsGrade()).apply(std.getSum()))
                std.setEctsGradeIsCorrect(true);
        });
    }
}
