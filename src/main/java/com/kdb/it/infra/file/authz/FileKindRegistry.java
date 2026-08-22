package com.kdb.it.infra.file.authz;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * 이 시스템이 아는 첨부파일 종류({@code PK_COL_NM})의 목록입니다.
 *
 * <p>종류 목록을 따로 손으로 관리하지 않고 <b>판정기들이 선언한 종류의 합집합</b>으로 만듭니다. 새 종류를 쓰려면 읽기 또는 쓰기 판정기를 먼저 등록해야
 * 하므로, "권한 규칙 없는 종류가 조용히 생기는" 경로가 구조적으로 막힙니다.
 *
 * <p><b>왜 필요한가(SEC-14)</b>: {@link FileTargetWriteAuthorizerRegistry}는 전용 writer가 없는 종류를 그냥
 * 통과시킵니다(레거시 보존). 그 상태에서는 클라이언트가 임의의 새 종류 이름으로 첨부를 만들 수 있고, 그 종류에는 부모 자원 쓰기 권한 검사가 붙지 않습니다. 알려진
 * 종류만 받도록 해서 그 경로를 닫습니다.
 *
 * <p><b>쓰기 판정기가 없는 종류도 허용합니다.</b> 등록된 쓰기 판정기는 넷뿐이라 그것만으로 좁히면 요구사항정의서·타당성검토표처럼 정상적으로 쓰이는 종류의
 * 업로드가 막힙니다. 이 목록의 목적은 <b>종류를 아는지</b>를 묻는 것이지 부모 권한 검사를 대신하는 것이 아닙니다.
 *
 * <p>기존 행에는 영향이 없습니다 — 판정은 신규 업로드 경로에서만 하므로, 종류가 비어 있는 과거 행은 그대로 조회·삭제됩니다.
 */
@Component
public class FileKindRegistry {

    private final Set<String> knownKinds;

    public FileKindRegistry(
            List<FileReadAuthorizer> readAuthorizers,
            List<FileTargetWriteAuthorizer> writeAuthorizers) {
        Set<String> kinds = new TreeSet<>();
        readAuthorizers.forEach(authorizer -> kinds.addAll(authorizer.supportedPkColNms()));
        writeAuthorizers.forEach(authorizer -> kinds.addAll(authorizer.supportedPkColNms()));
        this.knownKinds = Set.copyOf(kinds);
    }

    /**
     * 알려진 종류인지 확인합니다.
     *
     * @param pkColNm 파일 종류
     * @return 판정기가 선언한 종류면 true
     */
    public boolean isKnown(String pkColNm) {
        return pkColNm != null && knownKinds.contains(pkColNm);
    }

    /**
     * 알려진 종류의 이름을 정렬해 돌려줍니다. 진단과 테스트가 씁니다.
     *
     * @return 종류 이름 집합
     */
    public Set<String> knownKinds() {
        return knownKinds;
    }
}
