package com.kdb.it.domain.contract.service;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.contract.dto.ContractDto;
import com.kdb.it.domain.contract.entity.Bcontm;
import com.kdb.it.domain.contract.repository.ContractRepository;
import com.kdb.it.infra.eai.config.GweProperties;
import com.kdb.it.infra.eai.dto.EaiRequest;
import com.kdb.it.infra.eai.dto.EaiResult;
import com.kdb.it.infra.eai.dto.GwePayload;
import com.kdb.it.infra.eai.service.EaiService;
import java.time.Year;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입찰계약 서비스. 상태 71→75→79. 대상구분 100=사업/200=전산업무비.
 * 쓰기 주체: 작성중=신청자/부서, 진행중 계약입력=작업자(IT계약팀). 상태 전이는 인접만 허용.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ContractService {

    static final String STS_DRAFT = "71";
    static final String STS_IN_PROGRESS = "75";
    static final String STS_DONE = "79";
    static final String TGT_PROJECT = "100";
    static final String TGT_COST = "200";

    private final ContractRepository contractRepository;
    private final ProjectRepository projectRepository;
    private final CostRepository costRepository;
    private final com.kdb.it.domain.budget.project.service.BprojaSyncService bprojaSyncService;
    private final EaiService eaiService;
    private final GweProperties gweProperties;

    /**
     * 신규 입찰계약 의뢰를 생성한다.
     *
     * @param req  신규 의뢰 요청 DTO
     * @param user 요청자 인증 정보
     * @return 채번된 문서관리번호
     * @throws IllegalArgumentException 대상 미존재 또는 알 수 없는 대상구분
     * @throws IllegalStateException    동일 대상에 진행 중인 입찰계약 존재
     */
    @Transactional
    public String create(ContractDto.CreateRequest req, CustomUserDetails user) {
        validateTarget(req.ioeC(), req.cncdRfrNo());
        if (contractRepository.existsByIoeCAndCncdRfrNoAndStsTcInAndDelYn(
                req.ioeC(), req.cncdRfrNo(), List.of(STS_DRAFT, STS_IN_PROGRESS), "N")) {
            throw new IllegalStateException("해당 대상에 진행 중인 입찰/계약이 이미 있습니다.");
        }
        String docNo = String.format("CTR-%d-%04d", Year.now().getValue(), contractRepository.nextDocSeq());
        contractRepository.save(Bcontm.builder()
                .docMngNo(docNo).docVrsSno(1).lstYn("Y")
                .ioeC(req.ioeC()).cncdRfrNo(req.cncdRfrNo())
                .stsTc(STS_DRAFT).reqCone(req.reqCone()).build());
        if (TGT_PROJECT.equals(req.ioeC())) {
            bprojaSyncService.upsert(req.cncdRfrNo(), docNo, STS_DRAFT);
        }
        return docNo;
    }

    /**
     * 대상 유효성을 검증한다. 대상구분에 따라 사업 또는 전산업무비 존재 여부를 확인한다.
     *
     * @param ioeC   예산성격구분코드 (100=사업, 200=전산업무비)
     * @param cncdRfrNo 관련참조번호
     * @throws IllegalArgumentException 알 수 없는 대상구분 또는 대상 미존재
     */
    private void validateTarget(String ioeC, String cncdRfrNo) {
        boolean ok;
        if (TGT_PROJECT.equals(ioeC)) {
            ok = projectRepository.existsByAbusMngNoAndLstYnAndDelYn(cncdRfrNo, "Y", "N");
        } else if (TGT_COST.equals(ioeC)) {
            ok = costRepository.existsByCostBgNoAndLstYnAndDelYn(cncdRfrNo, "Y", "N");
        } else {
            throw new IllegalArgumentException("알 수 없는 대상구분: " + ioeC);
        }
        if (!ok) throw new IllegalArgumentException("대상을 찾을 수 없습니다: " + ioeC + "/" + cncdRfrNo);
    }

    /**
     * 입찰계약 요청내용을 수정한다. 작성중(71) 상태에서만 수정 가능하다.
     *
     * @param docNo 문서관리번호
     * @param req   수정 요청 DTO
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 작성중 상태가 아닌 경우
     */
    @Transactional
    public void update(String docNo, ContractDto.UpdateRequest req, CustomUserDetails user) {
        Bcontm e = loadCurrent(docNo);
        OwnershipVerifier.verifyOwnerOrAdmin(e.getFstEnrUsid(), user);
        if (!STS_DRAFT.equals(e.getStsTc())) throw new IllegalStateException("작성중 상태에서만 수정할 수 있습니다.");
        e.updateRequest(req.reqCone());
    }

    /**
     * 입찰계약을 논리 삭제한다. 작성중(71) 상태에서만 삭제 가능하다.
     *
     * @param docNo 문서관리번호
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 작성중 상태가 아닌 경우
     */
    @Transactional
    public void delete(String docNo, CustomUserDetails user) {
        Bcontm e = loadCurrent(docNo);
        OwnershipVerifier.verifyOwnerOrAdmin(e.getFstEnrUsid(), user);
        if (!STS_DRAFT.equals(e.getStsTc())) throw new IllegalStateException("작성중 상태에서만 삭제할 수 있습니다.");
        e.delete();
        if (TGT_PROJECT.equals(e.getIoeC())) {
            bprojaSyncService.softDelete(e.getCncdRfrNo(), docNo);
        }
    }

    /**
     * 입찰계약 상태를 전이한다. 허용 전이: 71→75, 75→79.
     *
     * @param docNo 문서관리번호
     * @param req   상태 전이 요청 DTO
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 허용되지 않은 상태 전이
     */
    @Transactional
    public void changeStatus(String docNo, ContractDto.StatusRequest req, CustomUserDetails user) {
        Bcontm e = loadCurrent(docNo);
        OwnershipVerifier.verifyAdmin(user);
        String from = e.getStsTc(), to = req.stsTc();
        boolean ok = (STS_DRAFT.equals(from) && STS_IN_PROGRESS.equals(to))
                || (STS_IN_PROGRESS.equals(from) && STS_DONE.equals(to));
        if (!ok) throw new IllegalStateException("허용되지 않은 상태 전이입니다: " + from + " → " + to);
        e.changeStatus(to);
        if (TGT_PROJECT.equals(e.getIoeC())) {
            bprojaSyncService.upsert(e.getCncdRfrNo(), docNo, to);
        }
        sendStatusEai("입찰계약", docNo, from, to, user);
    }

    /**
     * 계약 정보를 입력한다. 진행중(75) 상태에서만 입력 가능하다.
     *
     * @param docNo 문서관리번호
     * @param req   계약 정보 입력 DTO
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 진행중 상태가 아닌 경우
     */
    @Transactional
    public void saveContract(String docNo, ContractDto.WorkRequest req, CustomUserDetails user) {
        Bcontm e = loadCurrent(docNo);
        OwnershipVerifier.verifyOwnerOrAdmin(e.getFstEnrUsid(), user);
        if (!STS_IN_PROGRESS.equals(e.getStsTc())) throw new IllegalStateException("진행중 상태에서만 계약 정보를 입력할 수 있습니다.");
        e.updateContract(req.itPtlCttManrC(), req.cttManrRsn(), req.cttNm(), req.cttAmt(), req.cttOppNm(), req.cttDt());
    }

    /**
     * 입찰계약 상세를 조회한다.
     *
     * @param docNo 문서관리번호
     * @return 입찰계약 상세 DTO
     * @throws IllegalArgumentException 문서 미존재
     */
    public ContractDto.Detail get(String docNo) {
        var row = contractRepository.findCurrentDetail(docNo)
                .orElseThrow(() -> new IllegalArgumentException("입찰계약 문서를 찾을 수 없습니다: " + docNo));
        return ContractDto.Detail.fromProjection(row);
    }

    /**
     * 입찰계약 목록을 검색한다. 관리자는 전체 조회, 일반 사용자는 소속 부서 조회.
     *
     * @param stsTc     상태구분코드 필터 (nullable)
     * @param ioeC   예산성격구분코드 필터 (nullable)
     * @param cncdRfrNo 관련참조번호 필터 (nullable)
     * @param user      요청자 인증 정보
     * @return 목록 항목 리스트
     */
    public List<ContractDto.ListItem> list(String stsTc, String ioeC, String cncdRfrNo, CustomUserDetails user) {
        String bbrC = user.isAdmin() ? null : user.getBbrC();
        return contractRepository.search(stsTc, ioeC, cncdRfrNo, bbrC);
    }

    /**
     * 문서관리번호로 최신 활성 엔티티를 조회한다.
     *
     * @param docNo 문서관리번호
     * @return 입찰계약 엔티티
     * @throws IllegalArgumentException 문서 미존재
     */
    Bcontm loadCurrent(String docNo) {
        return contractRepository.findByDocMngNoAndLstYnAndDelYn(docNo, "Y", "N")
                .orElseThrow(() -> new IllegalArgumentException("입찰계약 문서를 찾을 수 없습니다: " + docNo));
    }

    private void sendStatusEai(String domainName, String docNo, String from, String to, CustomUserDetails user) {
        try {
            EaiResult result = eaiService.sendEai(EaiRequest.gwe(gweProperties.ifId(), GwePayload.builder()
                    .msgGubun("1")
                    .recvIds(user.getEno())
                    .subject("[IT Portal] " + domainName + " 상태 변경")
                    .contents(domainName + " 문서 " + docNo + " 상태가 " + from + "에서 " + to + "로 변경되었습니다.")
                    .sendId("systemalert")
                    .sendName("IT Portal")
                    .build()));
            if (!result.success() && !result.skipped()) {
                log.warn("EAI 발송 실패 — 원 업무 처리는 유지합니다. domain={}, docNo={}, 사유={}",
                        domainName, docNo, result.errorMessage());
            }
        } catch (RuntimeException e) {
            log.warn("EAI 발송 실패 — 원 업무 처리는 유지합니다.", e);
        }
    }
}
