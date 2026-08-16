package com.kdb.it.domain.migration.request.service.adapter;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import org.apache.poi.ddf.EscherClientAnchorRecord;
import org.apache.poi.ddf.EscherClientDataRecord;
import org.apache.poi.ddf.EscherContainerRecord;
import org.apache.poi.ddf.EscherRecord;
import org.apache.poi.ddf.EscherRecordTypes;
import org.apache.poi.ddf.EscherTextboxRecord;
import org.apache.poi.hssf.record.CommonObjectDataSubRecord;
import org.apache.poi.hssf.record.ObjRecord;
import org.apache.poi.hssf.record.SubRecord;
import org.apache.poi.hssf.record.TextObjectRecord;
import org.apache.poi.hssf.usermodel.HSSFPatriarch;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * 시트에 놓인 양식 컨트롤 체크박스를 읽습니다.
 *
 * <p>1-1 시트의 `업무구분`·`사업유형`·`주 사용자` 같은 항목은 셀에 값을 적는 대신 <b>양식 컨트롤 체크박스</b>로 표시합니다. 셀은 비어 있으므로 일반 셀
 * 읽기로는 항목이 전부 공란으로 보입니다.
 *
 * <p>두 형식의 저장 방식이 완전히 달라 경로를 나눕니다.
 *
 * <ul>
 *   <li>`.xls`(BIFF8): Escher 도형 컨테이너의 `ObjRecord`. 체크 상태는 POI가 해석하지 않는 `FtCblsData`(sid {@code
 *       0x12}) 서브레코드의 첫 2바이트라 직렬화 바이트에서 직접 읽습니다.
 *   <li>`.xlsx`(OOXML): 시트 XML의 `control` 요소가 앵커를, 관계로 이어진 `ctrlProps` 파트가 `checked`를, VML 그림 파트가
 *       문구를 각각 들고 있습니다. `CTWorksheet.getControls()`는 이 요소들이 `mc:AlternateContent`로 감싸여 있어 <b>null을
 *       돌려주므로</b> 쓸 수 없고, 파트 XML을 직접 읽습니다.
 * </ul>
 *
 * <p>어느 단계에서 실패해도 예외를 던지지 않고 빈 목록으로 접습니다 — 체크박스를 못 읽는 것은 항목이 공란인 것과 같은 상태이고, 그 상태는 이미 선택항목 경고로 처리되고
 * 있습니다. 파일 하나 때문에 배치를 무너뜨리지 않습니다.
 */
@Component
public class FormCheckboxReader {

    private static final Logger log = LoggerFactory.getLogger(FormCheckboxReader.class);

    /** BIFF8 `FtCblsData` 서브레코드 id. 첫 2바이트가 체크 상태입니다. */
    private static final int CHECKBOX_DATA_SUBRECORD = 0x12;

    /** `FtCblsData`에서 체크 상태 값이 시작하는 위치(id 2바이트 + 길이 2바이트 다음). */
    private static final int CHECKBOX_DATA_VALUE_OFFSET = 4;

    /** OOXML 관계 참조 네임스페이스. `control` 요소의 `r:id`를 읽을 때 씁니다. */
    private static final String RELATIONSHIP_NS =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    /** VML 도형 id 접두어. `_x0000_s7184`의 뒤쪽 숫자가 시트 XML의 `shapeId`입니다. */
    private static final String VML_SHAPE_ID_PREFIX = "_x0000_s";

    /**
     * 시트의 체크박스를 모두 읽습니다.
     *
     * @param sheet 대상 시트
     * @return 체크박스 목록. 체크박스가 없거나 읽지 못하면 빈 목록
     */
    public List<FormCheckbox> read(Sheet sheet) {
        try {
            if (sheet instanceof HSSFSheet hssf) return readBiff8(hssf);
            if (sheet instanceof XSSFSheet xssf) return readOoxml(xssf);
        } catch (RuntimeException e) {
            log.debug("체크박스를 읽지 못했습니다: sheet={}", sheet.getSheetName(), e);
        }
        return List.of();
    }

    // ---------------------------------------------------------------- .xls

    private List<FormCheckbox> readBiff8(HSSFSheet sheet) {
        HSSFPatriarch patriarch = sheet.getDrawingPatriarch();
        if (patriarch == null) return List.of();

        Map<EscherRecord, org.apache.poi.hssf.record.Record> objByShape =
                patriarch.getBoundAggregate().getShapeToObjMapping();
        List<FormCheckbox> boxes = new ArrayList<>();
        for (EscherRecord record : patriarch.getBoundAggregate().getEscherRecords()) {
            collectBiff8(record, objByShape, boxes);
        }
        return List.copyOf(boxes);
    }

    private void collectBiff8(
            EscherRecord record,
            Map<EscherRecord, org.apache.poi.hssf.record.Record> objByShape,
            List<FormCheckbox> boxes) {
        if (record instanceof EscherContainerRecord container
                && container.getRecordId() == EscherRecordTypes.SP_CONTAINER.typeID) {
            addBiff8Checkbox(container, objByShape, boxes);
        }
        for (EscherRecord child : record.getChildRecords()) {
            collectBiff8(child, objByShape, boxes);
        }
    }

    private void addBiff8Checkbox(
            EscherContainerRecord container,
            Map<EscherRecord, org.apache.poi.hssf.record.Record> objByShape,
            List<FormCheckbox> boxes) {
        EscherClientAnchorRecord anchor = null;
        ObjRecord obj = null;
        TextObjectRecord caption = null;
        for (EscherRecord child : container.getChildRecords()) {
            if (child instanceof EscherClientAnchorRecord clientAnchor) {
                anchor = clientAnchor;
            } else if (child instanceof EscherClientDataRecord
                    && objByShape.get(child) instanceof ObjRecord objRecord) {
                obj = objRecord;
            } else if (child instanceof EscherTextboxRecord
                    && objByShape.get(child) instanceof TextObjectRecord textRecord) {
                caption = textRecord;
            }
        }
        if (anchor == null || obj == null || !isCheckbox(obj)) return;
        boxes.add(
                new FormCheckbox(
                        anchor.getRow1(), anchor.getCol1(), captionOf(caption), checkedOf(obj)));
    }

    private static boolean isCheckbox(ObjRecord obj) {
        for (SubRecord sub : obj.getSubRecords()) {
            if (sub instanceof CommonObjectDataSubRecord common) {
                return common.getObjectType() == CommonObjectDataSubRecord.OBJECT_TYPE_CHECKBOX;
            }
        }
        return false;
    }

    /**
     * `FtCblsData` 서브레코드에서 체크 상태를 읽습니다.
     *
     * <p>POI는 이 서브레코드를 해석하지 않고 미지의 레코드로 들고 있어 직렬화 바이트를 직접 봅니다. 실측 바이트는 체크 시 {@code 12 00 08 00 01
     * 00 …}, 해제 시 {@code 12 00 08 00 00 00 …}입니다.
     */
    private static boolean checkedOf(ObjRecord obj) {
        for (SubRecord sub : obj.getSubRecords()) {
            byte[] raw = sub.serialize();
            if (raw.length > CHECKBOX_DATA_VALUE_OFFSET
                    && (raw[0] & 0xFF) == CHECKBOX_DATA_SUBRECORD
                    && raw[1] == 0) {
                return raw[CHECKBOX_DATA_VALUE_OFFSET] != 0;
            }
        }
        return false;
    }

    private static String captionOf(TextObjectRecord caption) {
        if (caption == null || caption.getStr() == null) return "";
        return caption.getStr().getString().trim();
    }

    // --------------------------------------------------------------- .xlsx

    private List<FormCheckbox> readOoxml(XSSFSheet sheet) {
        Document sheetXml = parse(sheet.getPackagePart());
        if (sheetXml == null) return List.of();
        NodeList controls = sheetXml.getElementsByTagNameNS("*", "control");
        if (controls.getLength() == 0) return List.of();

        Map<String, String> captionByShapeId = vmlCaptions(sheet);
        List<FormCheckbox> boxes = new ArrayList<>();
        for (int i = 0; i < controls.getLength(); i++) {
            if (!(controls.item(i) instanceof Element control)) continue;
            Boolean checked =
                    ooxmlCheckedState(sheet, control.getAttributeNS(RELATIONSHIP_NS, "id"));
            if (checked == null) continue;
            Element from = firstElement(control, "from");
            if (from == null) continue;
            boxes.add(
                    new FormCheckbox(
                            intOf(from, "row"),
                            intOf(from, "col"),
                            captionByShapeId.getOrDefault(control.getAttribute("shapeId"), ""),
                            checked));
        }
        return List.copyOf(boxes);
    }

    /**
     * `ctrlProps` 파트를 열어 체크 상태를 읽습니다.
     *
     * @return 체크 여부. 관계가 없거나 체크박스가 아닌 컨트롤(라디오·콤보 등)이면 null
     */
    private Boolean ooxmlCheckedState(XSSFSheet sheet, String relationId) {
        if (relationId == null || relationId.isEmpty()) return null;
        POIXMLDocumentPart part = sheet.getRelationById(relationId);
        if (part == null) return null;
        Document doc = parse(part.getPackagePart());
        if (doc == null) return null;
        Element root = doc.getDocumentElement();
        if (root == null || !"CheckBox".equals(root.getAttribute("objectType"))) return null;
        return "Checked".equals(root.getAttribute("checked"));
    }

    /** VML 그림 파트에서 도형 id별 문구를 모읍니다. 문구는 셀이 아니라 도형 안의 텍스트박스에 있습니다. */
    private Map<String, String> vmlCaptions(XSSFSheet sheet) {
        Map<String, String> captions = new LinkedHashMap<>();
        for (POIXMLDocumentPart part : sheet.getRelations()) {
            PackagePart packagePart = part.getPackagePart();
            if (packagePart == null
                    || !packagePart.getPartName().getName().toLowerCase().endsWith(".vml")) {
                continue;
            }
            Document doc = parse(packagePart);
            if (doc == null) continue;
            NodeList shapes = doc.getElementsByTagNameNS("*", "shape");
            for (int i = 0; i < shapes.getLength(); i++) {
                if (!(shapes.item(i) instanceof Element shape)) continue;
                String id = shape.getAttribute("id");
                if (!id.startsWith(VML_SHAPE_ID_PREFIX)) continue;
                Element textbox = firstElement(shape, "textbox");
                if (textbox == null) continue;
                captions.put(
                        id.substring(VML_SHAPE_ID_PREFIX.length()),
                        textbox.getTextContent().replaceAll("\\s+", " ").trim());
            }
        }
        return captions;
    }

    // --------------------------------------------------------------- 공통

    /** 업로드 파일의 XML이므로 POI의 하드닝된 빌더로 읽습니다(외부 엔티티·DTD 차단). */
    private Document parse(PackagePart part) {
        if (part == null) return null;
        try (InputStream in = part.getInputStream()) {
            DocumentBuilder builder = XMLHelper.newDocumentBuilder();
            return builder.parse(in);
        } catch (IOException | RuntimeException | org.xml.sax.SAXException e) {
            log.debug("파트 XML을 읽지 못했습니다: {}", part.getPartName(), e);
            return null;
        }
    }

    private static Element firstElement(Element parent, String localName) {
        NodeList found = parent.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < found.getLength(); i++) {
            if (found.item(i) instanceof Element element) return element;
        }
        return null;
    }

    private static int intOf(Element parent, String localName) {
        Element child = firstElement(parent, localName);
        if (child == null) return -1;
        try {
            return Integer.parseInt(child.getTextContent().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
