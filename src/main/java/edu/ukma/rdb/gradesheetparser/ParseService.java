package edu.ukma.rdb.gradesheetparser;

import edu.ukma.rdb.gradesheetparser.exceptions.ParseStructuralError;
import edu.ukma.rdb.gradesheetparser.models.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
            add("Не відвідувала");
            add("Не допущена");
        }});
        put("екзамен", new HashSet<String>() {{
            add("Відмінно");
            add("Добре");
            add("Задовільно");
            add("Незадовільно");
            add("Не відвідував");
            add("Не допущений");
            add("Не відвідувла");
            add("Не допущена");
        }});
    }};

    private static final Map<String, String> NAT_GRADES_NORM_TO_CORRECT = NATIONAL_GRADES.values().stream()
            .flatMap(Set::stream)
            .distinct()
            .map(grade -> new AbstractMap.SimpleImmutableEntry<>(grade.replaceAll(" ", "").toLowerCase(), grade))
            .collect(Collectors.toMap(AbstractMap.SimpleImmutableEntry::getKey, AbstractMap.SimpleImmutableEntry::getValue));

    @Override
    public GradeSheet parse(MultipartFile input) throws IOException {

        try (InputStream fileStream = input.getInputStream()) {
            PDDocument document = PDDocument.load(fileStream);
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(document);

            text = text.replaceAll("(_+)|(\\s{2,})", " ");
            Pattern tablePattern = Pattern.compile("(?ui).*?п\\s*і\\s*д\\s*п\\s*и\\s*с\\s*в\\s*и\\s*к\\s*л\\s*а\\s*д\\s*а\\s*ч\\s*а(.*?)\\*.*?");
            Matcher tableMatch = tablePattern.matcher(text);
            String table = tableMatch.find() ? tableMatch.group(1) : "";

            GradeSheet sheet = identifySheet(text);
            sheet.setIsValid(true);
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
            setStudentData(table, sheet);
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
                .filter(studentData -> studentData.getNationalGrade().replaceAll("\\s+", "")
                        .matches("(?iu)недопущен((ий)|(а))")).count();
        Pattern p = Pattern.compile("(?iu)кількість студентів, недопущених до екзамену\\s*/тези\\s*/заліку\\s*(\\d+)");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            chadSheet.setBannedHasError(true);
            chadSheet.setIsValid(false);
            return;
        }
        int suggestedBanned = Integer.parseInt(m.group(1));
        chadSheet.setBanned(suggestedBanned);
        chadSheet.setBannedHasError(suggestedBanned != tableBanned);
        if (chadSheet.isBannedHasError()) chadSheet.setIsValid(false);
    }

    private void setMissing(String text, ChadStudentsSheet chadSheet) {
        long tableAbsent = chadSheet.getData().stream()
                .filter(studentData -> studentData.getNationalGrade().replaceAll("\\s+", "")
                        .matches("(?iu)невідвідув((ав)|(ла))")).count();
        Pattern p = Pattern.compile("(?iu)кількість студентів, які не з’явились на екзамен\\s*/тезу\\s*/залік\\s*(\\d+)");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            chadSheet.setMissingHasError(true);
            chadSheet.setIsValid(false);
            return;
        }
        int suggestedAbsent = Integer.parseInt(m.group(1));
        chadSheet.setMissing(suggestedAbsent);
        chadSheet.setMissingHasError(suggestedAbsent != tableAbsent);
        if (chadSheet.isMissingHasError()) chadSheet.setIsValid(false);
    }

    private void setPresent(String text, ChadStudentsSheet chadSheet) {
        long tablePresent = chadSheet.getData().stream()
                .filter(studentData -> studentData.getNationalGrade().replaceAll("\\s+", "")
                        .matches("(?iu)(зараховано)|(незараховано)|(відмінно)|(добре)|(задовільно)|(незадовільно)"))
                .count();

        /*
        *
        * !(studentData.getNationalGrade().equalsIgnoreCase("Не відвідував")
                        || studentData.getNationalGrade().equalsIgnoreCase("Не допущений"))
        * */

        Pattern p = Pattern.compile("(?iu)Кількість студентів на екзамені\\s*/тезі\\s*/заліку\\s*(\\d+)");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            chadSheet.setPresentHasError(true);
            chadSheet.setIsValid(false);
            return;
        }
        int suggestedPresent = Integer.parseInt(m.group(1));
        chadSheet.setPresent(suggestedPresent);
        if (suggestedPresent != tablePresent) {
            chadSheet.setPresentHasError(true);
            chadSheet.setIsValid(false);
        }
    }

    private void setExpiration(String text, Bigunets bigunetsSheet) {
        Pattern p = Pattern.compile("(?iu)дійсне до\\P{IsCyrillic}*?(\\d{2})\\P{IsCyrillic}*(\\p{IsCyrillic}+)\\s*(\\d{4})");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            bigunetsSheet.setExpiresError("Відсутня або неповна дата дійсності направлення.");
            bigunetsSheet.setIsValid(false);
            return;
        }
        bigunetsSheet.setExpires(new CustomDate(Integer.parseInt(m.group(1)), m.group(2).trim(), Integer.parseInt(m.group(3))));
    }

    private void setCause(String text, Bigunets bigunetsSheet) {
        Pattern p = Pattern.compile("(?iu)причина перенесення((\\p{IsCyrillic}|\\s)+)\\b\\s*форма");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            bigunetsSheet.setCauseError("Відсутня причина перенесення.");
            bigunetsSheet.setIsValid(false);
            return;
        }
        bigunetsSheet.setCause(m.group(1).trim());
    }

    private void setDean(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)декан факультету((\\s+\\p{IsCyrillic}+){3})");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setDeanError("Відсутній декан.");
            sheet.setIsValid(false);
            return;
        }
        sheet.setDean(m.group(1).trim());
    }

    private void setStudentData(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?u)(\\d+)\\s+((\\p{IsCyrillic}{2,}\\s*){2,})?\\s*(І \\d{3}/\\d{2}\\s*((бп)|(мп)))?\\s*(\\d+)?\\s*(\\d+)?\\s*(\\d+)?\\s*([\\p{IsCyrillic} ]+)?\\s+(\\w)?\\s*");
        Matcher m = p.matcher(text); // 5 Димченко Микита Олегович І 016/10 мп Не відвідував F
        while (m.find()) {
            StudentData std = new StudentData();
            std.setOrdinal(Integer.parseInt(m.group(1)));

            if (m.group(2) != null) {
                String[] fullName = m.group(2).trim().split("\\s+");
                std.setSurname(fullName[0]);
                std.setFirstName(fullName[1]);
                if (fullName.length == 3) std.setLastName(fullName[2]);
                if (Stream.of(fullName).anyMatch(x -> x.contains(".")))
                    std.setNameError("Можливо ім'я містить скорочення");
            } else sheet.setIsValid(false);

            if (m.group(4) != null)
                std.setBookNo(m.group(4).trim());
            else sheet.setIsValid(false);

            if (m.group(8) != null)
                std.setTermGrade(Integer.parseInt(m.group(8)));
            else sheet.setIsValid(false);

            if (m.group(9) != null)
                std.setExamGrade(Integer.parseInt(m.group(9)));
            else sheet.setIsValid(false);

            if (m.group(10) != null)
                std.setSum(Integer.parseInt(m.group(10)));
            else sheet.setIsValid(false);

            if ((std.getSum() == null || std.getTermGrade() == null || std.getExamGrade() == null) ||
                    (std.getSum() != std.getTermGrade() + std.getExamGrade())) {
                std.setSumHasError(true);
                sheet.setIsValid(false);
            }

            final String normNatGrade = m.group(11).replaceAll("\\s+", "").toLowerCase();
            if (m.group(11) != null && NAT_GRADES_NORM_TO_CORRECT.containsKey(normNatGrade)) {
                std.setNationalGrade(NAT_GRADES_NORM_TO_CORRECT.get(normNatGrade));
            } else sheet.setIsValid(false);

            if (sheet.getControlForm() == null || std.getNationalGrade() == null || !NATIONAL_GRADES.containsKey(sheet.getControlForm().toLowerCase())
                    || !NATIONAL_GRADES.get(sheet.getControlForm().toLowerCase()).contains(std.getNationalGrade())) {
                std.setNationalGradeHasError(true);
                sheet.setIsValid(false);
            }

            if (m.group(12) != null)
                std.setEctsGrade(m.group(12).charAt(0));
            else sheet.setIsValid(false);

            if (std.getEctsGrade() == null || std.getSum() == null || !ECTS_ASSERTS.containsKey(std.getEctsGrade())
                    || !ECTS_ASSERTS.get(std.getEctsGrade()).apply(std.getSum())) {
                std.setEctsGradeHasError(true);
                sheet.setIsValid(false);
            }

            sheet.addStudentData(std);
        }
    }

    private void setTeacherRank(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu),(.+?)прізвище");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setTeacherRankError("Відсутні ПІБ викладача.");
            sheet.setIsValid(false);
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
            sheet.setTeacherNameError("Відсутні або неповні ПІБ викладача.");
            sheet.setIsValid(false);
            return;
        }
        String[] teacherFullName = m.group(1).trim().split("\\s+");
        sheet.setTeacherSurname(teacherFullName[0]);
        sheet.setTeacherFirstname(teacherFullName[1]);
        sheet.setTeacherLastname(teacherFullName[2]);
        if (m.group(1).contains(".")) {
            sheet.setTeacherNameError("Можливо ім'я викладача містить скорочення.");
            sheet.setIsValid(false);
        }
    }

    private void setDate(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)дата\\P{IsCyrillic}*?(\\d{2})\\P{IsCyrillic}*(\\p{IsCyrillic}+)\\s*(\\d{4})");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setDateError("Відсутня або неповна дата.");
            sheet.setDate(new CustomDate());
            sheet.setIsValid(false);
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
            sheet.setIsValid(false);
            return;
        }
        sheet.setControlForm(m.group(2));
        if (!(m.group(2).equalsIgnoreCase("залік") || m.group(2).equalsIgnoreCase("екзамен"))) {
            sheet.setControlFormError("Прийнятні форми контролю - залік або екзамен.");
            sheet.setIsValid(false);
        }
    }

    private void setCreditPoints(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)залікові бали\\s*(\\d{1,2})");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setCreditPointsError("Відсутні залікові бали.");
            sheet.setIsValid(false);
            return;
        }
        sheet.setCreditPoints(Integer.parseInt(m.group(1)));
    }

    private void setTerm(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)семестр\\s*(\\dд?)");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setTermError("Відсутній семестр.");
            sheet.setIsValid(false);
            return;
        }
        sheet.setTerm(m.group(1));
        int termN = Integer.parseInt(m.group(1).charAt(0) + "");
        if (termN < 1 || termN > 8) {
            sheet.setTermError("Тримести можуть бути від 1 до 8.");
            sheet.setIsValid(false);
        }
    }

    private void setSubject(String text, GradeSheet sheet) {
//        Pattern p = Pattern.compile("(?iu)дисципліна\\s*((\\p{IsCyrillic}|\\w|\\s)+?)\\s*семестр");
        Pattern p = Pattern.compile("(?iu)дисципліна\\s*(.+?)\\s*семестр");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setSubjectError("Відсутня дисципліна.");
            sheet.setIsValid(false);
            return;
        }
        sheet.setSubject(m.group(1));
    }

    private void setGroup(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)група\\s*((\\p{IsCyrillic}|\\d)+)\\b");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setGroupError("Відсутня група.");
            sheet.setIsValid(false);
            return;
        }
        sheet.setGroup(m.group(1));
        if (!(m.group(1).matches("(?u)\\d+І?") || m.group(1).equalsIgnoreCase("бігунець"))) {
            sheet.setGroupError("Припустимі значення групи: 'бігунець' або '[число]І'.");
            sheet.setIsValid(false);
        }
    }

    private void setEduYear(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)Рік навчання\\s*(\\d)");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setEduYearError("Відсутній рік навчання.");
            sheet.setIsValid(false);
            return;
        }
        sheet.setEduYear(Integer.parseInt(m.group(1)));
        if (sheet.getOkr() != null) {
            if (sheet.getOkr().equalsIgnoreCase("бакалавр")) {
                if (sheet.getEduYear() < 1 || sheet.getEduYear() > 4) {
                    sheet.setEduYearError("для бакалаврів роки навчання від 1 до 4");
                    sheet.setIsValid(false);
                }
            } else if (sheet.getOkr().equalsIgnoreCase("магістр")) {
                if (sheet.getEduYear() < 1 || sheet.getEduYear() > 2) {
                    sheet.setEduYearError("для магістрів роки навчання 1 або 2");
                    sheet.setIsValid(false);
                }
            }
        }
    }

    private void setFaculty(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)факультет\\s+((\\p{IsCyrillic}|\\s)+)\\s+рік");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setFacultyError("Відсутній факультет.");
            sheet.setIsValid(false);
            return;
        }
        sheet.setFaculty(m.group(1).trim());
    }

    private void setOkr(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)\\bосвітній рівень\\s*(\\p{IsCyrillic}+?)\\b");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setOkrError("Відсутній освітній рівень.");
            sheet.setIsValid(false);
            return;
        }
        sheet.setOkr(m.group(1));
        if (!(sheet.getOkr().equalsIgnoreCase("бакалавр") || sheet.getOkr().equalsIgnoreCase("магістр"))) {
            sheet.setOkrError("припустимі ОКР: бакалавр, магістр");
            sheet.setIsValid(false);
        }
    }

    private void setSheetCode(String text, GradeSheet sheet) {
        Pattern p = Pattern.compile("(?iu)№\\s*(\\d+?)\\s*освітній"); // use \b?
        Matcher m = p.matcher(text);
        if (!m.find()) {
            sheet.setSheetCodeError("Відсутній номер відомості.");
            sheet.setIsValid(false);
            return;
        }
        sheet.setSheetCode(Integer.parseInt(m.group(1)));
    }

    @Override
    public ChadStudentsSheet validate(ChadSheetCore input) {
        ChadSheetCore basicChecked = validateGradeSheet(input);
        ChadStudentsSheet chadSheet = new ChadStudentsSheet();
        BeanUtils.copyProperties(basicChecked, chadSheet);
        setPresent(chadSheet, input);
        setMissing(chadSheet, input);
        setBanned(chadSheet, input);
        return chadSheet;
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

    @Override
    public Bigunets validate(Bigunets input) {
        Bigunets basicChecked = validateGradeSheet(input);
        if (input.getCause() == null || input.getCause().matches("\\s*")) {
            input.setCauseError("Причина перенесення не вказана.");
            input.setIsValid(false);
        }
        if (basicChecked.getExpires() == null) {
            input.setExpiresError("Дата 'дісне до' не вказана.");
            input.setIsValid(false);
        }
        return basicChecked;
    }

    private <T extends GradeSheet> T validateGradeSheet(T sheet) {
        if (sheet.getSheetCode() == null) {
            sheet.setSheetCodeError("Бракує коду відомості.");
            sheet.setIsValid(false);
        }

        if (sheet.getOkr() == null || !(sheet.getOkr().equalsIgnoreCase("бакалавр")
                || sheet.getOkr().equalsIgnoreCase("магістр"))) {
            sheet.setOkrError("Хибник освітній рівень, допустимі: бакалавр/магістр.");
            sheet.setIsValid(false);
        }

        if (sheet.getFaculty() == null || sheet.getFaculty().matches("\\s*")) {
            sheet.setFacultyError("Відсутній факультет.");
            sheet.setIsValid(false);
        }

        if (sheet.getEduYear() == null
                || (sheet.getOkr().equalsIgnoreCase("бакалавр") && (sheet.getEduYear() < 1 || sheet.getEduYear() > 4))
                || (sheet.getOkr().equalsIgnoreCase("магістр") && (sheet.getEduYear() < 1 || sheet.getEduYear() > 2))) {
            sheet.setEduYearError("Допустимі роки навчання бакалаврів - від 1 до 4, магістрів - 1 або 2.");
            sheet.setIsValid(false);
        }

        if (sheet.getGroup() == null || sheet.getGroup().matches("\\s*")) {
            sheet.setGroupError("Група не вказана.");
            sheet.setIsValid(false);
        } else if (!(sheet.getGroup().matches("(?u)\\d+І?") || sheet.getGroup().equalsIgnoreCase("бігунець"))) {
            sheet.setGroupError("Припустимі значення групи: 'бігунець' або '[число]І'.");
            sheet.setIsValid(false);
        }

        if (sheet.getSubject() == null || sheet.getSubject().matches("\\s*")) {
            sheet.setSubjectError("Предмет не вказаний.");
            sheet.setIsValid(false);
        }

        if (sheet.getTerm() == null || !sheet.getTerm().matches("(?u)\\dд?")) {
            sheet.setTermError("Семестр має бути вказаний у форматі '<цифра>[д]'");
            sheet.setIsValid(false);
        }

        if (sheet.getCreditPoints() == null) {
            sheet.setCreditPointsError("Кількість кредитів не вказана.");
            sheet.setIsValid(false);
        } else if (sheet.getCreditPoints() < 1 || sheet.getCreditPoints() > 62) {
            sheet.setCreditPointsError("Кількість кредитів вказана неправильно.");
            sheet.setIsValid(false);
        }

        if (sheet.getControlForm() == null || !(sheet.getControlForm().equalsIgnoreCase("залік")
                || sheet.getControlForm().equalsIgnoreCase("екзамен"))) {
            sheet.setControlFormError("Допустимі форми контролю - 'залік' або 'екзамен'.");
            sheet.setIsValid(false);
        }

        if (sheet.getDate() == null) {
            sheet.setDateError("Дата оцінювання має бути вказана.");
            sheet.setIsValid(false);
        } else {
            CustomDate passedCustom = sheet.getDate();
            if (passedCustom.getDay() == null || passedCustom.getMonth() == null || passedCustom.getYear() == null) {
                sheet.setDateError("Неповна дата.");
                sheet.setIsValid(false);
            } else if (!MONTHS_MAP.containsKey(passedCustom.getMonth())) {
                sheet.setDateError("Недопустимий місяць.");
                sheet.setIsValid(false);
            } else {
                try {
                    LocalDate passedDate = LocalDate.of(passedCustom.getYear(), MONTHS_MAP.get(sheet.getDate().getMonth()), passedCustom.getDay());
                    if (passedDate.isAfter(LocalDate.now())) {
                        sheet.setDateError("Майбутні дати не допускаються.");
                        sheet.setIsValid(false);
                    }
                } catch (DateTimeException wrongDate) {
                    sheet.setDateError("Химерна дата.");
                    sheet.setIsValid(false);
                }
            }

        }

        if (sheet.getTeacherSurname() == null || sheet.getTeacherSurname().matches("\\s*") || sheet.getTeacherSurname().contains(".")
                || sheet.getTeacherFirstname() == null || sheet.getTeacherFirstname().matches("\\s*") || sheet.getTeacherFirstname().contains(".")
                || sheet.getTeacherLastname() == null || sheet.getTeacherLastname().matches("\\s*") || sheet.getTeacherLastname().contains(".")) {
            sheet.setTeacherNameError("ПІБ викладача потенційно містять скорочення або частково неповні.");
            sheet.setIsValid(false);
        }

        if (sheet.getDean() == null || sheet.getDean().matches("\\s*")) {
            sheet.setDeanError("ПІБ декана відсутні.");
            sheet.setIsValid(false);
        }

        validateStudentData(sheet);
        return sheet;
    }

    private void validateStudentData(GradeSheet sheet) {
        sheet.getData().forEach(std -> {
            if (std.getSurname() == null || std.getSurname().matches("\\s*") || std.getSurname().contains(".")
                    || std.getFirstName() == null || std.getFirstName().matches("\\s*") || std.getFirstName().contains(".")
                    || (std.getLastName() != null && (std.getLastName().matches("\\s+") || std.getLastName().contains(".")))) {
                std.setNameError("Неправильно сформоване ім'я в номера " + std.getOrdinal());
                sheet.setIsValid(false);
            }

            if (std.getBookNo() == null) {
                std.setBookNoError("Відсутній код залікової книжки у номера " + std.getOrdinal());
                sheet.setIsValid(false);
            } else if (!std.getBookNo().matches("І \\d{3}/\\d{2}\\s*((бп)|(мп))")) {
                std.setBookNoError("Неправильно сформований код залікової книжки у номера " + std.getOrdinal());
                sheet.setIsValid(false);
            }

            if (std.getTermGrade() == null) {
                std.setTermGradeError("Нема оцінки за трим.");
                sheet.setIsValid(false);
            } else if (std.getTermGrade() < 0 || std.getTermGrade() > 100) {
                std.setTermGradeError("Оцінка може бути від 0 до 100.");
                sheet.setIsValid(false);
            }

            if (std.getExamGrade() == null) {
                std.setExamGradeError("Нема оцінки за залік/екзамен.");
                sheet.setIsValid(false);
            } else if (std.getExamGrade() < 0 || std.getExamGrade() > 40) {
                std.setExamGradeError("Оцінки за підсумкові роботи можуть бути від 0 до 40.");
                sheet.setIsValid(false);
            }

            if (std.getSum() == null || std.getExamGrade() == null || std.getEctsGrade() == null
                    || std.getSum() != std.getTermGrade() + std.getExamGrade()) {
                std.setSumHasError(true);
                sheet.setIsValid(false);
            }

            if (sheet.getControlForm() == null
                    || !NATIONAL_GRADES.get(sheet.getControlForm().toLowerCase()).contains(std.getNationalGrade())) {
                std.setNationalGradeHasError(true);
                sheet.setIsValid(false);
            }

            if (std.getExamGrade() == null || std.getSum() == null
                    || !ECTS_ASSERTS.get(std.getEctsGrade()).apply(std.getSum())) {
                std.setEctsGradeHasError(true);
                sheet.setIsValid(false);
            }
        });
    }

    private void setPresent(ChadStudentsSheet chadSheet, ChadSheetCore input) {
        if (input.getPresent() == null) {
            chadSheet.setPresentHasError(true);
            chadSheet.setIsValid(false);
            return;
        }
        chadSheet.setPresent(input.getPresent());
        long dataPresent = chadSheet.getData().stream()
                .filter(std -> std.getNationalGrade() != null && !(std.getNationalGrade().equalsIgnoreCase("Не відвідував")
                        || std.getNationalGrade().equalsIgnoreCase("Не допущений"))).count();

        if (chadSheet.getPresent() != dataPresent) {
            chadSheet.setMissingHasError(true);
            chadSheet.setIsValid(false);
        }
    }

    private void setMissing(ChadStudentsSheet chadSheet, ChadSheetCore input) {
        if (input.getMissing() == null) {
            chadSheet.setMissingHasError(true);
            chadSheet.setIsValid(false);
            return;
        }
        chadSheet.setMissing(input.getMissing());
        long dataMissing = chadSheet.getData().stream()
                .filter(std -> std.getNationalGrade() != null && std.getNationalGrade().equalsIgnoreCase("Не відвідував")).count();

        if (chadSheet.getMissing() != dataMissing) {
            chadSheet.setMissingHasError(true);
            chadSheet.setIsValid(false);
        }
    }

    private void setBanned(ChadStudentsSheet chadSheet, ChadSheetCore input) {
        if (input.getBanned() == null) {
            chadSheet.setBannedHasError(true);
            chadSheet.setIsValid(false);
            return;
        }
        chadSheet.setBanned(input.getBanned());
        long dataBanned = chadSheet.getData().stream()
                .filter(std -> std.getNationalGrade() != null && std.getNationalGrade().equalsIgnoreCase("Не допущений")).count();

        if (chadSheet.getBanned() != dataBanned) {
            chadSheet.setBannedHasError(true);
            chadSheet.setIsValid(false);
        }
    }
}
