package com.kdb.it.domain.migration.request.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 편성요청서 테스트 픽스처.
 *
 * <p>실 제출본(`C:\it\sample`)은 실명 담당자·실제 예산액이 든 업무 데이터라 저장소에 커밋하지 않습니다. 대신 그 파일들에서 관측한 레이아웃을 POI로
 * 재현합니다. 두 파일이 1-1 시트에서 각각 38행·41행이었고 1-2의 두 번째 헤더 위치도 22행·18행으로 달랐으므로, 픽스처도 그 차이를 그대로 담아 앵커 스캔이 고정
 * 좌표에 기대지 않는지 검증합니다.
 */
public final class RequestFormFixtures {

    private RequestFormFixtures() {}

    /** 시트 ①만 채운 .xlsx (스마트워크 인프라 샘플 레이아웃, 1-2 두 번째 헤더가 22행). */
    public static byte[] capitalOnlyXlsx() {
        try (Workbook wb = new XSSFWorkbook()) {
            writeCapitalOverview(wb, "① (정보화사업) 1-1. 정보화사업 개요", 0);
            writeCapitalResource(wb, "① (정보화사업) 1-2. 소요자원 상세내용", 22);
            return toBytes(wb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 4시트를 모두 담은 .xls (자금운용실 샘플 레이아웃, 1-2 두 번째 헤더가 18행). */
    public static byte[] fullFormXls() {
        try (Workbook wb = new HSSFWorkbook()) {
            writeCapitalOverview(wb, "① (정보화사업) 1-1. 정보화사업 개요", 3);
            writeCapitalResource(wb, "① (정보화사업) 1-2. 소요자원 상세내용", 18);
            writeRecurring(wb, "② (경상사업) 2. 경상적인 사업", true);
            writeGeneralExpense(wb, "③ (일반관리비) 전산 일반관리비 편성요청서", false);
            return toBytes(wb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 영문 양식 .xls (런던지점 레이아웃). 시트명만 국문이고 내용은 영문입니다. */
    public static byte[] englishFormXls() {
        try (Workbook wb = new HSSFWorkbook()) {
            writeRecurring(wb, "② (경상사업) 2. 경상적인 사업", false);
            writeGeneralExpense(wb, "③ (일반관리비) 전산 일반관리비 편성요청서", true);
            return toBytes(wb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 1-1 개요 시트를 씁니다.
     *
     * @param extraScopeRows `사업 범위` 칸에 끼워 넣을 추가 행 수. 실 제출본이 이 칸의 행 수로 시트 전체 길이가 달라졌습니다
     */
    private static void writeCapitalOverview(Workbook wb, String name, int extraScopeRows) {
        Sheet s = wb.createSheet(name);
        put(s, 0, 0, "1-1. 정보화사업 개요");
        put(s, 1, 6, "(확인자)");
        put(s, 1, 7, "허인선 팀장");
        put(s, 1, 8, "(작성자)");
        put(s, 1, 9, "김준영 차장");
        put(s, 2, 0, "사업명");
        put(s, 2, 2, "국채전문유통시장 접속인프라 도입");
        put(s, 3, 0, "사업 개요");
        put(s, 3, 2, "(개요)");
        put(s, 3, 3, "접속방식 변경");
        put(s, 4, 2, "(현황)");
        put(s, 4, 3, "Exture3.0 운영 중");
        put(s, 5, 2, "(필요성)");
        put(s, 5, 3, "접속체계 전환 대응");
        put(s, 6, 2, "(기대효과)");
        put(s, 6, 3, "PD 자격 유지");
        put(s, 7, 2, "(미추진시 문제점)");
        put(s, 7, 3, "PD 업무 수행 불가");

        int row = 8;
        put(s, row, 0, "사업 범위 (전산 요구사항)");
        put(s, row, 2, "전용망 거래 기능");
        for (int i = 1; i <= extraScopeRows; i++) {
            put(s, row + i, 2, "추가 요구사항 " + i);
        }
        row += extraScopeRows + 1;

        put(s, row, 0, "진행 상황");
        put(s, row, 2, "추진경과");
        put(s, row, 3, "내부승인 완료");
        put(s, row, 6, "향후계획");
        put(s, row, 7, "계약 체결 예정");
        row++;

        put(s, row++, 2, "업무구분");
        put(s, row++, 2, "사업유형");
        put(s, row++, 2, "디지털 기술 유형");
        put(s, row++, 2, "주 사용자");
        put(s, row, 2, "중복 여부");
        put(s, row, 6, "법규상 완료시기");
        row++;
        put(s, row, 2, "주관부문/본부");
        put(s, row, 3, "글로벌사업부문");
        put(s, row, 5, "팀장");
        put(s, row, 6, "윤소정");
        put(s, row, 8, "IT팀장");
        put(s, row, 9, "공현순");
        row++;
        put(s, row, 2, "주관부서/팀");
        put(s, row, 3, "자금운용실/원화유가증권팀");
        put(s, row, 5, "실무자(정/부)");
        put(s, row, 6, "허진성/장준호");
        put(s, row, 8, "IT실무자(정/부)");
        put(s, row, 9, "최현식/이종현");
        row++;
        put(s, row, 2, "최종보고");
        put(s, row, 8, "전결권자");
        put(s, row, 9, "수석부행장");
        row++;
        put(s, row++, 2, "추진가능성");
        put(s, row, 2, "시작일자 (YY/MM)");
        put(s, row, 3, "25/06");
        put(s, row, 4, "종료일자 (YY/MM)");
        put(s, row, 5, "26/02");
        put(s, row, 7, "총 사업금액(전체기간)");
        put(s, row, 9, "2,000백만원");
        row++;

        // 예산 소요(상세) 요약표. 실 제출본은 이 표의 단위가 파일마다 달라(백만원/원)
        // 적재하지 않고 1-2 품목 합계와 대사만 한다.
        put(s, row, 0, "예산 소요(상세) (백만원, 부가세포함)");
        put(s, row, 2, "1분기");
        put(s, row, 3, "2분기");
        put(s, row, 4, "3분기");
        put(s, row, 5, "4분기");
        put(s, row, 6, "'26년도 합계");
        put(s, row, 7, "'26년도 이후");
        put(s, row, 8, "전체 합계");
        row++;
        put(s, row, 0, "자본예산");
        put(s, row, 1, "기타무형자산(SW)");
        row++;
        put(s, row, 0, "일반관리비");
        put(s, row, 1, "전산제비");
        row++;
        put(s, row, 0, "총 계");
        // 1-2 품목 합계(957,000,000 + 120,000,000 + 188,624,700 = 1,265,624,700)와 같은 원 단위로 적는다
        putNumber(s, row, 6, 1_265_624_700d);
        putNumber(s, row, 8, 1_265_624_700d);
    }

    /**
     * 1-2 소요자원 시트를 씁니다.
     *
     * @param secondHeaderRow 일반관리비 블록 헤더의 0-based 행 번호. 실 제출본이 22행·18행으로 달랐습니다
     */
    private static void writeCapitalResource(Workbook wb, String name, int secondHeaderRow) {
        Sheet s = wb.createSheet(name);
        put(s, 0, 0, "1-2. 정보화사업 소요자원 상세내용");
        put(s, 9, 1, "구분");
        put(s, 9, 3, "항목");
        put(s, 9, 4, "수량");
        put(s, 9, 5, "단가");
        put(s, 9, 6, "통화");
        put(s, 9, 7, "소요예산 (부가세포함)");
        put(s, 9, 8, "산정근거");
        put(s, 9, 9, "도입시기(월)");
        put(s, 9, 10, "정보보호여부");
        put(s, 9, 11, "인프라 통합관리 여부");

        writeResourceRow(
                s,
                10,
                "자본예산",
                "기타무형자산(SW)",
                "솔루션 패키지",
                1,
                957_000_000,
                "KRW",
                957_000_000,
                "계약금액 참조",
                "~26.2월",
                "Y",
                "Y");
        writeResourceRow(
                s,
                11,
                "자본예산",
                "기계장치(HW)",
                "운영 서버",
                2,
                60_000_000,
                "KRW",
                120_000_000,
                "업체견적",
                "2분기 중",
                "O",
                "X");

        put(s, secondHeaderRow, 1, "구분");
        put(s, secondHeaderRow, 3, "항목");
        put(s, secondHeaderRow, 4, "수량");
        put(s, secondHeaderRow, 5, "단가");
        put(s, secondHeaderRow, 6, "통화");
        put(s, secondHeaderRow, 7, "연간 소요예산 (부가세포함)");
        put(s, secondHeaderRow, 8, "산정근거");
        put(s, secondHeaderRow, 9, "대금지급주기 (월/분기/년)");
        put(s, secondHeaderRow, 10, "정보보호여부");
        put(s, secondHeaderRow, 11, "인프라 통합관리 여부");

        writeResourceRow(
                s,
                secondHeaderRow + 1,
                "일반관리비",
                "전산임차료",
                "전용망 회선 이용료",
                1,
                188_624_700,
                "KRW",
                188_624_700,
                "품의문 참조",
                "년",
                "Y",
                "Y");
    }

    private static void writeResourceRow(
            Sheet s,
            int row,
            String division,
            String group,
            String item,
            int qty,
            long unitPrice,
            String currency,
            long amount,
            String basis,
            String timing,
            String infoSec,
            String infra) {
        put(s, row, 1, division);
        put(s, row, 2, group);
        put(s, row, 3, item);
        putNumber(s, row, 4, qty);
        putNumber(s, row, 5, unitPrice);
        put(s, row, 6, currency);
        putNumber(s, row, 7, amount);
        put(s, row, 8, basis);
        put(s, row, 9, timing);
        put(s, row, 10, infoSec);
        put(s, row, 11, infra);
    }

    /** 시트 ②를 씁니다. `withName=false`면 런던 샘플처럼 사업명이 공란입니다. */
    private static void writeRecurring(Workbook wb, String name, boolean withName) {
        Sheet s = wb.createSheet(name);
        put(s, 0, 0, withName ? "2. 경상적인 사업" : "2. Recurring business");
        // 상단 머리말 — 확인자가 주관팀장, 작성자가 담당자다 (런던 실측: 라벨과 이름이 다른 칸)
        put(s, 1, 6, "(확인자)");
        put(s, 1, 7, "신원석 부부장");
        put(s, 1, 8, "(작성자)");
        put(s, 1, 9, "Luke Buckingham-Brown 과장");
        put(s, 2, 0, withName ? "사업명" : "Business Name");
        if (withName) put(s, 2, 2, "2026년 IT기계장치 구입");
        put(s, 3, 0, withName ? "사업 개요" : "Business Overview");
        put(s, 3, 2, "(개요)");
        put(s, 3, 3, "PC, 모니터 구입");
        put(s, 4, 2, "(현황)");
        put(s, 4, 3, "내용연수 경과");
        put(s, 5, 2, "(추진내용)");
        put(s, 5, 3, "고장기기 교체");
        put(s, 6, 2, "(미추진시 문제점)");
        put(s, 6, 3, "업무효율 저하");

        put(s, 7, 0, "구분");
        put(s, 7, 2, withName ? "항목" : "Item");
        put(s, 7, 4, withName ? "수량" : "Qty");
        put(s, 7, 5, withName ? "단가" : "Unit Cost");
        put(s, 7, 6, withName ? "통화" : "Currency");
        put(s, 7, 7, withName ? "소요예산 (부가세포함)" : "Budget (Tax Included)");
        put(s, 7, 8, withName ? "도입시기" : "Timing");
        put(s, 7, 9, withName ? "비고(적용 환율 등)" : "Remarks");

        put(s, 8, 0, "소요 자원");
        put(s, 8, 1, "기계장치(HW)");
        put(s, 8, 2, "데스크탑(고사양)");
        putNumber(s, 8, 4, 12);
        putNumber(s, 8, 5, 1945.57);
        put(s, 8, 6, "GBP");
        putNumber(s, 8, 7, 23346.84);
        put(s, 8, 8, "26년 연중");

        put(s, 9, 1, "기타무형자산(SW)");
        put(s, 9, 2, "MS오피스");
        putNumber(s, 9, 4, 99);
        putNumber(s, 9, 5, 537.12);
        put(s, 9, 6, "GBP");
        putNumber(s, 9, 7, 53174.88);

        put(s, 10, 0, "계");
        s.addMergedRegion(new CellRangeAddress(10, 10, 0, 1));
    }

    /** 시트 ③을 씁니다. `english=true`면 런던 샘플처럼 라벨·비목명이 영문입니다. */
    private static void writeGeneralExpense(Workbook wb, String name, boolean english) {
        Sheet s = wb.createSheet(name);
        // 상단 머리말 — 런던 실측은 라벨과 이름이 같은 칸에 붙어 있다
        put(s, 1, 8, "(확인자)");
        put(s, 1, 9, "박은지 팀장");
        put(s, 1, 10, "(작성자) 최민호 대리");
        put(
                s,
                0,
                0,
                english
                        ? "3. Request for allocation of general IT management expenses"
                        : "3. 전산 일반관리비 편성 요청서");
        put(s, 3, 0, english ? "Expense" : "비 목 명");
        put(s, 3, 2, english ? "Name of the Contract / Item" : "계약명 / 건명");
        put(s, 3, 3, english ? "Currency" : "통화 구분");
        put(s, 3, 4, english ? "Budget" : "소요예산");
        put(s, 3, 6, english ? "Contract" : "계약");
        put(s, 3, 7, english ? "Contract Type" : "계약구분");
        put(s, 3, 9, english ? "InfoSec. Related" : "정보보호 관련여부");
        put(s, 3, 10, english ? "Remarks (Reasons for increase / decrease)" : "비고(증감사유, 적용환율 등)");
        put(s, 4, 4, english ? "Monthly" : "월간");
        put(s, 4, 5, english ? "Annual" : "연간");
        put(s, 4, 6, english ? "Counterparty" : "상대처");
        put(s, 4, 7, english ? "Cont." : "계속");
        put(s, 4, 8, english ? "New" : "신규");

        if (english) {
            put(s, 5, 0, "IT Expenses");
            put(s, 5, 1, "Foreign branch line usage fees");
            put(s, 5, 2, "Main Internet");
            put(s, 5, 3, "GBP");
            putNumber(s, 5, 4, 431.44);
            putNumber(s, 5, 5, 5177.28);
            put(s, 5, 6, "Daisy Communications");
            put(s, 5, 7, "○");
            put(s, 5, 9, "Ⅹ");
            put(s, 5, 10, "물가상승율 반영");
            // A·B열이 병합된 다음 행 — forward-fill 대상
            put(s, 6, 2, "AML Screening");
            put(s, 6, 3, "GBP");
            putNumber(s, 6, 5, 16643);
            put(s, 6, 6, "Dow Jones");
            put(s, 6, 7, "○");
            put(s, 6, 9, "Ⅹ");
            // 세부비목 칸에 중분류를 그대로 적은 행 (런던 실측). (중분류, 세부) 쌍이 빗나가 중분류로 좁히는 경로를 탄다
            put(s, 7, 1, "Machinery");
            put(s, 7, 2, "Tape backup software");
            put(s, 7, 3, "GBP");
            putNumber(s, 7, 5, 1200);
            put(s, 7, 6, "Veeam");
            put(s, 7, 7, "○");
            put(s, 7, 9, "Ⅹ");
        } else {
            put(s, 5, 0, "전산 제비");
            put(s, 5, 1, "회선사용료");
            put(s, 5, 2, "블룸버그 회선사용료");
            put(s, 5, 3, "KRW");
            putNumber(s, 5, 4, 73_887_778d);
            putNumber(s, 5, 5, 841_854_085d);
            put(s, 5, 6, "Bloomberg");
            put(s, 5, 7, "√");
            put(s, 5, 10, "비용인상 및 환율 반영");
            put(s, 6, 1, "국외전산유지보수료");
            put(s, 6, 2, "KINS 서버 유지보수");
            put(s, 6, 3, "KRW");
            putNumber(s, 6, 5, 23_346_800d);
            put(s, 6, 6, "Support Warehouse");
            put(s, 6, 8, "√");
        }
    }

    private static void put(Sheet sheet, int rowIndex, int colIndex, String value) {
        cell(sheet, rowIndex, colIndex).setCellValue(value);
    }

    private static void putNumber(Sheet sheet, int rowIndex, int colIndex, double value) {
        cell(sheet, rowIndex, colIndex).setCellValue(value);
    }

    private static Cell cell(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        Cell cell = row.getCell(colIndex);
        return cell == null ? row.createCell(colIndex) : cell;
    }

    private static byte[] toBytes(Workbook workbook) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
