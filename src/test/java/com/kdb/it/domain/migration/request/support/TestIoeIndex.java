package com.kdb.it.domain.migration.request.support;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.domain.migration.request.service.IoeHierarchyIndex;
import java.util.List;
import org.mockito.Mockito;

/**
 * 로컬 Oracle 실측 비목 코드로 만든 테스트용 계층 인덱스입니다.
 *
 * <p>어댑터 테스트 여러 개가 같은 코드 목록을 쓰므로 한곳에 모읍니다. 값은 `TPRMPP_CCODEM`에서 `CO_C_ID_NM='IOE_C'`로 조회한
 * `CDVA_ID`·`CDVA_NM`·`CO_CDVA_SPS` 23건 그대로입니다.
 */
public final class TestIoeIndex {

    private TestIoeIndex() {}

    /**
     * 실측 23건을 담은 인덱스를 만듭니다.
     *
     * @return 계층 인덱스 스냅샷
     */
    public static IoeHierarchyIndex.Snapshot snapshot() {
        CodeRepository repository = Mockito.mock(CodeRepository.class);
        Mockito.when(repository.findByCIdAndDelYn(CommonCodeGroups.IOE, "N")).thenReturn(codes());
        return new IoeHierarchyIndex(repository).snapshot();
    }

    /**
     * 실측 비목 코드 목록을 돌려줍니다.
     *
     * @return 비목 공통코드 23건
     */
    public static List<Ccodem> codes() {
        return List.of(
                code("001", "국내전산임차료", "일반관리비 - 전산임차료 - 국내전산임차료"),
                code("002", "국외전산임차료", "일반관리비 - 전산임차료 - 국외전산임차료"),
                code("003", "국내출장", "일반관리비 - 전산여비 - 국내출장"),
                code("004", "국외출장", "일반관리비 - 전산여비 - 국외출장"),
                code("005", "국외점포전산여비", "일반관리비 - 전산여비 - 국외점포전산여비"),
                code("006", "원고강사심사료", "일반관리비 - 전산용역비 - 원고강사심사료"),
                code("007", "국외전산용역비", "일반관리비 - 전산용역비 - 국외전산용역비"),
                code("008", "외주용역(외주운영/관제 등)", "일반관리비 - 전산용역비 - 외주용역 - 외주운영/관제 등"),
                code("009", "외주용역(자문/심사)", "일반관리비 - 전산용역비 - 외주용역 - 자문/심사"),
                code("010", "회선사용료", "일반관리비 - 전산제비 - 회선사용료"),
                code("011", "유지보수료", "일반관리비 - 전산제비 - 유지보수료"),
                code("012", "전산소모품비", "일반관리비 - 전산제비 - 전산소모품비"),
                code("013", "국외회선사용료", "일반관리비 - 전산제비 - 국외회선사용료"),
                code("014", "국외유지보수료", "일반관리비 - 전산제비 - 국외유지보수료"),
                code("015", "국외전산기타제비", "일반관리비 - 전산제비 - 국외전산기타제비"),
                code("016", "전산회의비", "일반관리비 - 전산제비 - 전산회의비"),
                code("017", "국외전산제비", "일반관리비 - 전산제비 - 국외전산제비"),
                code("101", "국내기계장치", "자본예산 - 기계장치 - 국내"),
                code("102", "국외기계장치", "자본예산 - 기계장치 - 국외"),
                code("103", "개발비(일반)", "자본예산 - 개발비 - 일반"),
                code("104", "개발비(감리/컨설팅)", "자본예산 - 개발비 - 감리/컨설팅"),
                code("105", "국외기타무형자산", "자본예산 - 기타무형자산 - 국외"),
                code("106", "국내기타무형자산(일반)", "자본예산 - 기타무형자산 - 국내 - 일반"),
                code("107", "국내기타무형자산(SW라이선스)", "자본예산 - 기타무형자산 - 국내 - SW라이선스"));
    }

    private static Ccodem code(String cdva, String name, String hierarchy) {
        return Ccodem.builder()
                .cId(CommonCodeGroups.IOE)
                .cdva(cdva)
                .cdvaNm(name)
                .cdvaDtl(hierarchy)
                .build();
    }
}
