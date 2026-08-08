package com.kdb.it.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.notification.dto.NotificationDto;
import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto;
import com.kdb.it.domain.bizplan.dto.BizplanDto;
import com.kdb.it.domain.contract.dto.ContractDto;
import com.kdb.it.domain.deliberation.dto.DeliberationDto;
import com.kdb.it.domain.estimate.dto.EstimateDto;
import com.kdb.it.domain.payment.dto.PaymentDto;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiResponseOpenApiContractTest {

    @Test
    void authAndBizplanResponsesExposeRequiredAndNullableContracts() {
        assertContract(
                AuthDto.LoginResponse.class,
                fields("eno", "empNm", "athIds", "bbrC", "temC"),
                fields());

        assertContract(
                BizplanDto.ListItem.class,
                fields(
                        "abusMngNo",
                        "abusNm",
                        "svnDpmC",
                        "svnDpmNm",
                        "bseYy",
                        "totRqmAmt",
                        "stsTc",
                        "lstChgDtm"),
                fields(
                        "abusNm",
                        "svnDpmC",
                        "svnDpmNm",
                        "bseYy",
                        "totRqmAmt",
                        "stsTc",
                        "lstChgDtm"));
        assertContract(
                BizplanDto.Schedule.class,
                fields("sno", "dsdCone", "sttDt", "endDt"),
                fields("dsdCone", "sttDt", "endDt"));
        assertContract(
                BizplanDto.Item.class,
                fields(
                        "sno",
                        "gclNm",
                        "ioeC",
                        "qty",
                        "amt",
                        "fcAmt",
                        "curC",
                        "xcr",
                        "xcrBseDt",
                        "cttSno"),
                fields(
                        "gclNm",
                        "ioeC",
                        "qty",
                        "amt",
                        "fcAmt",
                        "curC",
                        "xcr",
                        "xcrBseDt",
                        "cttSno"));
        assertContract(
                BizplanDto.Contract.class,
                fields("sno", "cttNm", "nowCttManrC", "cttTrmMmNbr"),
                fields("cttNm", "nowCttManrC", "cttTrmMmNbr"));
        assertContract(
                BizplanDto.Detail.class,
                fields(
                        "abusMngNo",
                        "abusNm",
                        "bgNo",
                        "totRqmAmt",
                        "itPtlEdrtTc",
                        "redtConeInf",
                        "stsTc",
                        "schedules",
                        "items",
                        "contracts"),
                fields("abusNm", "bgNo", "totRqmAmt", "itPtlEdrtTc", "redtConeInf"));
    }

    @Test
    void executionDocumentResponsesExposeRequiredAndNullableContracts() {
        assertContract(
                ContractDto.ListItem.class,
                fields(
                        "docMngNo",
                        "docVrsSno",
                        "ioeC",
                        "cncdRfrNo",
                        "stsTc",
                        "cttNm",
                        "cttAmt",
                        "reqUsid",
                        "reqDtm"),
                fields("cttNm", "cttAmt", "reqUsid", "reqDtm"));
        assertContract(
                ContractDto.Detail.class,
                fields(
                        "docMngNo",
                        "docVrsSno",
                        "ioeC",
                        "cncdRfrNo",
                        "tgtNm",
                        "stsTc",
                        "reqCone",
                        "cttNm",
                        "cttOppNm",
                        "cttAmt",
                        "cttDt",
                        "itPtlCttManrC",
                        "cttManrRsn",
                        "reqUsid",
                        "reqDtm"),
                fields(
                        "tgtNm",
                        "reqCone",
                        "cttNm",
                        "cttOppNm",
                        "cttAmt",
                        "cttDt",
                        "itPtlCttManrC",
                        "cttManrRsn",
                        "reqUsid",
                        "reqDtm"));

        assertContract(
                DeliberationDto.ListItem.class,
                fields(
                        "docMngNo",
                        "docVrsSno",
                        "ioeC",
                        "cncdRfrNo",
                        "stsTc",
                        "taskDbrRltTc",
                        "reqUsid",
                        "reqDtm"),
                fields("taskDbrRltTc", "reqUsid", "reqDtm"));
        assertContract(
                DeliberationDto.Detail.class,
                fields(
                        "docMngNo",
                        "docVrsSno",
                        "ioeC",
                        "cncdRfrNo",
                        "tgtNm",
                        "stsTc",
                        "reqCone",
                        "taskDbrTc",
                        "taskDbrRltTc",
                        "taskDbrDt",
                        "taskDbrTod",
                        "taskDbrOmtYn",
                        "taskDbrOmtRsn",
                        "opnnCone",
                        "apvTrdnRsnCone",
                        "reqUsid",
                        "reqDtm"),
                fields(
                        "tgtNm",
                        "reqCone",
                        "taskDbrTc",
                        "taskDbrRltTc",
                        "taskDbrDt",
                        "taskDbrTod",
                        "taskDbrOmtYn",
                        "taskDbrOmtRsn",
                        "opnnCone",
                        "apvTrdnRsnCone",
                        "reqUsid",
                        "reqDtm"));

        assertContract(
                EstimateDto.ListItem.class,
                fields(
                        "rqmBgReqDocNo",
                        "docVrsSno",
                        "ioeC",
                        "cncdRfrNo",
                        "abusNm",
                        "totalBudget",
                        "sttDtm",
                        "endDtm",
                        "svnDpmC",
                        "svnDpmNm",
                        "stsTc",
                        "reqUsid",
                        "reqDtm"),
                fields(
                        "rqmBgReqDocNo",
                        "docVrsSno",
                        "abusNm",
                        "totalBudget",
                        "sttDtm",
                        "endDtm",
                        "svnDpmC",
                        "svnDpmNm",
                        "stsTc",
                        "reqUsid",
                        "reqDtm"));
        assertContract(
                EstimateDto.Line.class,
                fields("svnTemC", "ioeC", "rqmBgAmt", "opnnCone"),
                fields("rqmBgAmt", "opnnCone"));
        assertContract(
                EstimateDto.Detail.class,
                fields(
                        "rqmBgReqDocNo",
                        "docVrsSno",
                        "ioeC",
                        "cncdRfrNo",
                        "abusNm",
                        "stsTc",
                        "reqCone",
                        "reqUsid",
                        "reqDtm",
                        "lines"),
                fields("abusNm", "reqCone", "reqUsid", "reqDtm"));

        assertContract(
                PaymentDto.ListItem.class,
                fields(
                        "docMngNo",
                        "docVrsSno",
                        "ioeC",
                        "cncdRfrNo",
                        "stsTc",
                        "cttNm",
                        "cttAmt",
                        "reqUsid",
                        "reqDtm"),
                fields("cttNm", "cttAmt", "reqUsid", "reqDtm"));
        assertContract(
                PaymentDto.Line.class,
                fields("dfrTod", "dfrAmt", "dfrDt", "dfrMplDt", "opnnCone"),
                fields("dfrAmt", "dfrDt", "dfrMplDt", "opnnCone"));
        assertContract(
                PaymentDto.Detail.class,
                fields(
                        "docMngNo",
                        "docVrsSno",
                        "ioeC",
                        "cncdRfrNo",
                        "tgtNm",
                        "stsTc",
                        "reqCone",
                        "cttNm",
                        "cttAmt",
                        "reqUsid",
                        "reqDtm",
                        "lines"),
                fields("tgtNm", "reqCone", "cttNm", "cttAmt", "reqUsid", "reqDtm"));
    }

    @Test
    void notificationAndTiptapResponsesExposeRequiredNullableAndEnumContracts() {
        assertContract(
                NotificationDto.Item.class,
                fields(
                        "infmMsgNo",
                        "itPtlInfmSvcTc",
                        "ttl",
                        "infmMsgCone",
                        "infmRcdUrl",
                        "inqYn",
                        "inqDtm",
                        "fstEnrDtm"),
                fields("ttl", "infmMsgCone", "infmRcdUrl", "inqDtm"));
        assertContract(NotificationDto.UnreadCount.class, fields("count"), fields());
        assertContract(NotificationDto.MarkAllReadResponse.class, fields("updated"), fields());
        assertEnum(
                NotificationDto.Item.class, "itPtlInfmSvcTc", "01", "02", "03", "04", "05", "06");
        assertEnum(NotificationDto.Item.class, "inqYn", "Y", "N");

        assertContract(TiptapVariableDto.MetadataResponse.class, fields("categories"), fields());
        assertContract(
                TiptapVariableDto.CategoryMetadata.class,
                fields("code", "label", "years", "projects", "items"),
                fields("projects"));
        assertContract(TiptapVariableDto.ProjectRef.class, fields("code", "name"), fields());
        assertContract(TiptapVariableDto.ItemRef.class, fields("key", "label"), fields());
        assertContract(TiptapVariableDto.ResolveResponse.class, fields("results"), fields());
        assertContract(TiptapVariableDto.ResolvedValue.class, fields("value", "status"), fields());
        assertEnum(
                TiptapVariableDto.ResolvedValue.class,
                "status",
                "OK",
                "MISSING",
                "FORBIDDEN",
                "INVALID");
    }

    private static void assertContract(Class<?> type, Set<String> required, Set<String> nullable) {
        Schema<?> schema = resolve(type);
        assertThat(schema.getProperties().keySet()).containsExactlyInAnyOrderElementsOf(required);
        assertThat(schema.getRequired()).containsExactlyInAnyOrderElementsOf(required);
        required.forEach(
                name ->
                        assertThat(Boolean.TRUE.equals(property(schema, name).getNullable()))
                                .as("%s.%s nullable", type.getSimpleName(), name)
                                .isEqualTo(nullable.contains(name)));
    }

    private static void assertEnum(Class<?> type, String property, String... values) {
        assertThat(
                        property(resolve(type), property).getEnum().stream()
                                .map(String::valueOf)
                                .toList())
                .containsExactly(values);
    }

    private static Schema<?> resolve(Class<?> type) {
        ResolvedSchema resolved =
                ModelConverters.getInstance()
                        .resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(false));
        return resolved.schema;
    }

    private static Schema<?> property(Schema<?> schema, String name) {
        return (Schema<?>) schema.getProperties().get(name);
    }

    private static Set<String> fields(String... names) {
        return Set.of(names);
    }
}
