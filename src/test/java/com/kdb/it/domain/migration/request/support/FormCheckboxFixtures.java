package com.kdb.it.domain.migration.request.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.ddf.EscherClientAnchorRecord;
import org.apache.poi.ddf.EscherClientDataRecord;
import org.apache.poi.ddf.EscherContainerRecord;
import org.apache.poi.ddf.EscherRecordTypes;
import org.apache.poi.ddf.EscherTextboxRecord;
import org.apache.poi.hssf.record.CommonObjectDataSubRecord;
import org.apache.poi.hssf.record.EndSubRecord;
import org.apache.poi.hssf.record.ObjRecord;
import org.apache.poi.hssf.record.SubRecord;
import org.apache.poi.hssf.record.TextObjectRecord;
import org.apache.poi.hssf.usermodel.HSSFPatriarch;
import org.apache.poi.hssf.usermodel.HSSFRichTextString;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.util.LittleEndianByteArrayInputStream;

/**
 * 양식 컨트롤 체크박스가 놓인 시트 픽스처입니다.
 *
 * <p>POI에는 체크박스 <b>생성</b> API가 없어 두 형식 모두 저수준으로 만듭니다. 실 제출본(`C:\it\sample`)은 실명·실제 예산이 든 업무 데이터라
 * 커밋할 수 없으므로, 그 파일들에서 관측한 저장 구조를 그대로 재현합니다.
 *
 * <ul>
 *   <li>`.xls`: Escher 도형 컨테이너와 `ObjRecord`를 만들어 시트의 {@code EscherAggregate}에 직접 등록합니다. 체크 상태는 POI가
 *       해석하지 않는 `FtCblsData`(sid {@code 0x12}) 서브레코드라 직렬화 바이트로 넣습니다.
 *   <li>`.xlsx`: OPC 패키지를 파트 단위로 조립합니다. 체크 상태는 `ctrlProps` 파트, 문구는 VML 그림 파트에 있고 시트 XML의 `control`이
 *       둘을 잇습니다.
 * </ul>
 */
public final class FormCheckboxFixtures {

    /** `FtCblsData` 서브레코드 id. 첫 2바이트가 체크 상태입니다. */
    private static final int CHECKBOX_DATA_SUBRECORD = 0x12;

    private static final String CONTENT_TYPES = "[Content_Types].xml";
    private static final String SHEET_PART = "xl/worksheets/sheet1.xml";
    private static final String XML_DECL =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    private static final String NS_MAIN =
            "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String NS_REL =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String NS_DRAWING =
            "http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing";

    private FormCheckboxFixtures() {}

    // ---------------------------------------------------------------- .xls

    /**
     * 체크박스가 놓인 `.xls` 시트를 만듭니다.
     *
     * <p>담는 도형은 다음과 같습니다 — 앵커는 모두 0-based입니다.
     *
     * <ol>
     *   <li>(17행, 2열) 체크된 체크박스 `여신`
     *   <li>(17행, 4열) 해제된 체크박스 `수신` — 문구 도형이 없어 빈 문구가 됩니다
     *   <li>(17행, 6열) 체크박스가 아닌 콤보 도형 — 걸러집니다
     *   <li>(17행, 8열) `FtCblsData`가 없는 체크박스 — 해제로 봅니다
     *   <li>앵커가 없는 도형 — 걸러집니다
     * </ol>
     *
     * @return 시트. 워크북은 호출자가 닫습니다
     */
    public static HSSFSheet biff8SheetWithCheckboxes(HSSFWorkbook workbook) {
        HSSFSheet sheet = workbook.createSheet("① (정보화사업) 1-1. 정보화사업 개요");
        HSSFPatriarch patriarch = sheet.createDrawingPatriarch();

        // 실 제출본은 도형들이 SPGR 컨테이너 아래 묶여 있어 재귀 탐색이 필요하다
        EscherContainerRecord group = new EscherContainerRecord();
        group.setRecordId(EscherRecordTypes.SPGR_CONTAINER.typeID);

        group.addChildRecord(shape(patriarch, 17, 2, checkbox(true), "  여신  "));
        group.addChildRecord(shape(patriarch, 17, 4, checkbox(false), null));
        group.addChildRecord(shape(patriarch, 17, 6, comboBox(), " 목록"));
        group.addChildRecord(shape(patriarch, 17, 8, checkboxWithoutState(), " 국제"));
        group.addChildRecord(shapeWithoutAnchor(patriarch, checkbox(true)));

        patriarch.getBoundAggregate().addEscherRecord(group);
        return sheet;
    }

    /** 도형 컨테이너를 만들고 `ObjRecord`·문구 레코드를 시트의 도형-객체 매핑에 등록합니다. */
    private static EscherContainerRecord shape(
            HSSFPatriarch patriarch, int row, int column, ObjRecord obj, String caption) {
        EscherContainerRecord container = new EscherContainerRecord();
        container.setRecordId(EscherRecordTypes.SP_CONTAINER.typeID);

        EscherClientAnchorRecord anchor = new EscherClientAnchorRecord();
        anchor.setRow1((short) row);
        anchor.setCol1((short) column);
        container.addChildRecord(anchor);

        EscherClientDataRecord clientData = new EscherClientDataRecord();
        clientData.setRecordId(EscherRecordTypes.CLIENT_DATA.typeID);
        container.addChildRecord(clientData);
        patriarch.getBoundAggregate().associateShapeToObjRecord(clientData, obj);

        if (caption != null) {
            EscherTextboxRecord textbox = new EscherTextboxRecord();
            textbox.setRecordId(EscherRecordTypes.CLIENT_TEXTBOX.typeID);
            container.addChildRecord(textbox);
            TextObjectRecord text = new TextObjectRecord();
            text.setStr(new HSSFRichTextString(caption));
            patriarch.getBoundAggregate().associateShapeToObjRecord(textbox, text);
        }
        return container;
    }

    /** 앵커 없이 객체만 있는 도형. 좌표를 알 수 없어 걸러져야 합니다. */
    private static EscherContainerRecord shapeWithoutAnchor(
            HSSFPatriarch patriarch, ObjRecord obj) {
        EscherContainerRecord container = new EscherContainerRecord();
        container.setRecordId(EscherRecordTypes.SP_CONTAINER.typeID);
        EscherClientDataRecord clientData = new EscherClientDataRecord();
        clientData.setRecordId(EscherRecordTypes.CLIENT_DATA.typeID);
        container.addChildRecord(clientData);
        patriarch.getBoundAggregate().associateShapeToObjRecord(clientData, obj);
        return container;
    }

    private static ObjRecord checkbox(boolean checked) {
        ObjRecord obj = objectOf(CommonObjectDataSubRecord.OBJECT_TYPE_CHECKBOX);
        obj.addSubRecord(1, checkboxState(checked));
        return obj;
    }

    /** `FtCblsData`가 빠진 체크박스. 실측에는 없지만 상태를 못 읽을 때 해제로 접는지 확인합니다. */
    private static ObjRecord checkboxWithoutState() {
        return objectOf(CommonObjectDataSubRecord.OBJECT_TYPE_CHECKBOX);
    }

    private static ObjRecord comboBox() {
        return objectOf(CommonObjectDataSubRecord.OBJECT_TYPE_COMBO_BOX);
    }

    private static ObjRecord objectOf(int objectType) {
        CommonObjectDataSubRecord common = new CommonObjectDataSubRecord();
        common.setObjectType((short) objectType);
        common.setObjectId(1);
        ObjRecord obj = new ObjRecord();
        obj.addSubRecord(common);
        obj.addSubRecord(new EndSubRecord());
        return obj;
    }

    /**
     * `FtCblsData` 서브레코드를 직렬화 바이트로 만듭니다.
     *
     * <p>POI는 sid {@code 0x12}를 해석하지 않아 미지의 레코드로 들고 있으므로 바이트를 직접 넣습니다. 실측 바이트는 체크 시 {@code 12 00 08
     * 00 01 00 …}, 해제 시 {@code 12 00 08 00 00 00 …}입니다.
     */
    private static SubRecord checkboxState(boolean checked) {
        byte[] raw = new byte[12];
        raw[0] = (byte) CHECKBOX_DATA_SUBRECORD;
        raw[2] = 8; // 데이터 길이
        raw[4] = (byte) (checked ? 1 : 0);
        return SubRecord.createSubRecord(
                new LittleEndianByteArrayInputStream(raw),
                CommonObjectDataSubRecord.OBJECT_TYPE_CHECKBOX);
    }

    // --------------------------------------------------------------- .xlsx

    /**
     * 체크박스가 놓인 `.xlsx` 패키지를 조립합니다.
     *
     * <p>시트1의 `control` 구성 — 실 제출본에서 관측한 갈래를 모두 담습니다.
     *
     * <ol>
     *   <li>(17행, 2열) 체크됨 `여신`
     *   <li>(17행, 4열) 해제됨 `수신` — Excel은 해제 상태에서 `checked` 속성을 아예 쓰지 않습니다
     *   <li>드롭다운 컨트롤 — `objectType`이 CheckBox가 아니라 걸러집니다
     *   <li>관계가 끊긴 컨트롤·관계 id가 빈 컨트롤 — 걸러집니다
     *   <li>`ctrlProps` XML이 깨진 컨트롤 — 걸러집니다
     *   <li>앵커(`from`)가 없는 컨트롤 — 걸러집니다
     *   <li>(-1행, 4열) 행 번호가 숫자가 아닌 컨트롤 — 좌표를 -1로 둡니다
     *   <li>(17행, 10열) VML에 문구가 없는 컨트롤 — 빈 문구가 됩니다
     * </ol>
     *
     * <p>시트2는 컨트롤이 없는 시트입니다.
     *
     * @return .xlsx 바이트
     */
    public static byte[] checkboxXlsx() {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put(CONTENT_TYPES, contentTypes());
        parts.put("_rels/.rels", rootRels());
        parts.put("xl/workbook.xml", workbook());
        parts.put("xl/_rels/workbook.xml.rels", workbookRels());
        parts.put(SHEET_PART, sheetWithControls());
        parts.put("xl/worksheets/_rels/sheet1.xml.rels", sheetRels());
        parts.put("xl/worksheets/sheet2.xml", sheetWithoutControls());
        parts.put("xl/ctrlProps/ctrlProp1.xml", ctrlProp("CheckBox", " checked=\"Checked\""));
        parts.put("xl/ctrlProps/ctrlProp2.xml", ctrlProp("CheckBox", ""));
        parts.put("xl/ctrlProps/ctrlProp3.xml", ctrlProp("Drop", ""));
        // 닫히지 않은 태그 — 파트 XML 파싱 실패를 빈 결과로 접는지 확인한다
        parts.put(
                "xl/ctrlProps/ctrlProp4.xml", XML_DECL + "<formControlPr objectType=\"CheckBox\"");
        parts.put("xl/ctrlProps/ctrlProp5.xml", ctrlProp("CheckBox", " checked=\"Checked\""));
        parts.put("xl/ctrlProps/ctrlProp6.xml", ctrlProp("CheckBox", " checked=\"Checked\""));
        parts.put("xl/ctrlProps/ctrlProp7.xml", ctrlProp("CheckBox", " checked=\"Checked\""));
        parts.put("xl/drawings/vmlDrawing1.vml", vmlDrawing());
        return zip(parts);
    }

    private static String contentTypes() {
        StringBuilder xml = new StringBuilder(XML_DECL);
        xml.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append(
                        "<Default Extension=\"rels\""
                                + " ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append(
                        "<Default Extension=\"vml\""
                                + " ContentType=\"application/vnd.openxmlformats-officedocument.vmlDrawing\"/>")
                .append(
                        "<Override PartName=\"/xl/workbook.xml\""
                                + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        for (String sheet : new String[] {"sheet1", "sheet2"}) {
            xml.append("<Override PartName=\"/xl/worksheets/")
                    .append(sheet)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument")
                    .append(".spreadsheetml.worksheet+xml\"/>");
        }
        for (int i = 1; i <= 7; i++) {
            xml.append("<Override PartName=\"/xl/ctrlProps/ctrlProp")
                    .append(i)
                    .append(
                            ".xml\" ContentType=\"application/vnd.ms-excel.controlproperties+xml\"/>");
        }
        return xml.append("</Types>").toString();
    }

    private static String rootRels() {
        return XML_DECL
                + "<Relationships"
                + " xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\""
                + NS_REL
                + "/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>";
    }

    private static String workbook() {
        return XML_DECL
                + "<workbook xmlns=\""
                + NS_MAIN
                + "\" xmlns:r=\""
                + NS_REL
                + "\"><sheets>"
                + "<sheet name=\"1-1\" sheetId=\"1\" r:id=\"rId1\"/>"
                + "<sheet name=\"1-2\" sheetId=\"2\" r:id=\"rId2\"/>"
                + "</sheets></workbook>";
    }

    private static String workbookRels() {
        return XML_DECL
                + "<Relationships"
                + " xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + relationship("rId1", "/worksheet", "worksheets/sheet1.xml")
                + relationship("rId2", "/worksheet", "worksheets/sheet2.xml")
                + "</Relationships>";
    }

    private static String sheetRels() {
        StringBuilder xml = new StringBuilder(XML_DECL);
        xml.append(
                "<Relationships"
                        + " xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        xml.append(relationship("rId1", "/vmlDrawing", "../drawings/vmlDrawing1.vml"));
        for (int i = 1; i <= 7; i++) {
            xml.append(
                    relationship(
                            "rId" + (i + 1), "/ctrlProp", "../ctrlProps/ctrlProp" + i + ".xml"));
        }
        return xml.append("</Relationships>").toString();
    }

    private static String relationship(String id, String typeSuffix, String target) {
        return "<Relationship Id=\""
                + id
                + "\" Type=\""
                + NS_REL
                + typeSuffix
                + "\" Target=\""
                + target
                + "\"/>";
    }

    private static String sheetWithoutControls() {
        return XML_DECL + "<worksheet xmlns=\"" + NS_MAIN + "\"><sheetData/></worksheet>";
    }

    private static String sheetWithControls() {
        StringBuilder xml = new StringBuilder(XML_DECL);
        xml.append("<worksheet xmlns=\"")
                .append(NS_MAIN)
                .append("\" xmlns:r=\"")
                .append(NS_REL)
                .append(
                        "\" xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\"")
                .append(" mc:Ignorable=\"x14\"")
                .append(
                        " xmlns:x14=\"http://schemas.microsoft.com/office/spreadsheetml/2009/9/main\">")
                .append("<sheetData/><controls>");
        // 실 제출본은 컨트롤이 mc:AlternateContent로 감싸여 있어 CTWorksheet.getControls()가 null이 된다
        xml.append(control("7184", "rId2", 17, "2"))
                .append(control("7185", "rId3", 17, "4"))
                .append(control("7186", "rId4", 17, "6"))
                .append(control("7187", "rId99", 17, "7"))
                .append(control("7188", "", 17, "8"))
                .append(control("7189", "rId5", 17, "9"))
                .append(controlWithoutAnchor("7190", "rId6"))
                .append(control("7191", "rId7", null, "4"))
                .append(control("9999", "rId8", 17, "10"));
        return xml.append("</controls></worksheet>").toString();
    }

    private static String control(String shapeId, String relationId, Integer row, String column) {
        return alternateContent(
                "<control shapeId=\""
                        + shapeId
                        + "\" r:id=\""
                        + relationId
                        + "\" name=\"Check Box "
                        + shapeId
                        + "\"><controlPr defaultSize=\"0\" autoFill=\"0\"><anchor"
                        + " moveWithCells=\"1\"><from xmlns:xdr=\""
                        + NS_DRAWING
                        + "\"><xdr:col>"
                        + column
                        + "</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>"
                        + (row == null ? "열여덟" : row)
                        + "</xdr:row><xdr:rowOff>0</xdr:rowOff></from><to xmlns:xdr=\""
                        + NS_DRAWING
                        + "\"><xdr:col>99</xdr:col><xdr:row>99</xdr:row></to>"
                        + "</anchor></controlPr></control>");
    }

    /** 앵커가 없는 컨트롤. 좌표를 알 수 없어 걸러져야 합니다. */
    private static String controlWithoutAnchor(String shapeId, String relationId) {
        return alternateContent(
                "<control shapeId=\""
                        + shapeId
                        + "\" r:id=\""
                        + relationId
                        + "\" name=\"Check Box "
                        + shapeId
                        + "\"><controlPr defaultSize=\"0\"/></control>");
    }

    private static String alternateContent(String inner) {
        return "<mc:AlternateContent><mc:Choice Requires=\"x14\">"
                + inner
                + "</mc:Choice></mc:AlternateContent>";
    }

    private static String ctrlProp(String objectType, String checkedAttribute) {
        return XML_DECL
                + "<formControlPr"
                + " xmlns=\"http://schemas.microsoft.com/office/spreadsheetml/2009/9/main\""
                + " objectType=\""
                + objectType
                + "\""
                + checkedAttribute
                + " lockText=\"1\" noThreeD=\"1\"/>";
    }

    /** 도형 id별 문구를 담은 VML 그림 파트입니다. 문구는 셀이 아니라 도형 안 텍스트박스에 있습니다. */
    private static String vmlDrawing() {
        return "<xml xmlns:v=\"urn:schemas-microsoft-com:vml\""
                + " xmlns:o=\"urn:schemas-microsoft-com:office:office\""
                + " xmlns:x=\"urn:schemas-microsoft-com:office:excel\">"
                + "<o:shapelayout v:ext=\"edit\"><o:idmap v:ext=\"edit\" data=\"7\"/></o:shapelayout>"
                + "<v:shapetype id=\"_x0000_t201\" coordsize=\"21600,21600\" o:spt=\"201\""
                + " path=\"m,l,21600r21600,l21600,xe\"><v:stroke joinstyle=\"miter\"/>"
                + "<v:path shadowok=\"t\" o:connecttype=\"rect\"/></v:shapetype>"
                + vmlShape("_x0000_s7184", "  여신\n  ")
                + vmlShape("_x0000_s7186", " 목록")
                // 문구 도형이 없는 컨트롤(9999)과, 접두어가 다른 도형은 문구 없이 남는다
                + "<v:shape id=\"_x0000_s7191\" type=\"#_x0000_t201\""
                + " style=\"position:absolute\"/>"
                + "<v:shape id=\"딴것\" type=\"#_x0000_t201\" style=\"position:absolute\">"
                + "<v:textbox><div><font>무시</font></div></v:textbox></v:shape>"
                + "</xml>";
    }

    private static String vmlShape(String id, String caption) {
        return "<v:shape id=\""
                + id
                + "\" type=\"#_x0000_t201\" style=\"position:absolute\">"
                + "<v:textbox style=\"mso-direction-alt:auto\"><div style=\"text-align:left\">"
                + "<font face=\"맑은 고딕\" size=\"180\" color=\"auto\">"
                + caption
                + "</font></div></v:textbox>"
                + "<x:ClientData ObjectType=\"Checkbox\"><x:Anchor>2,0,17,0,4,0,18,0</x:Anchor>"
                + "</x:ClientData></v:shape>";
    }

    private static byte[] zip(Map<String, String> parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> part : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(part.getKey()));
                zip.write(part.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
