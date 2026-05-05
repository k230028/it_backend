package com.kdb.it.domain.budget.plan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.domain.budget.plan.dto.PlanDto;
import com.kdb.it.domain.budget.plan.entity.Bplanm;
import com.kdb.it.domain.budget.plan.entity.Bproja;
import com.kdb.it.domain.budget.plan.repository.BplanmRepository;
import com.kdb.it.domain.budget.plan.repository.BprojaRepository;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 정보기술부문 계획 서비스
 *
 * <p>
 * 정보기술부문계획(TAAABB_BPLANM)과 정보화사업 관계(TAAABB_BPROJA)의
 * 등록, 조회, 삭제 비즈니스 로직을 담당합니다.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class PlanService {

        private final BplanmRepository bplanmRepository;
        private final BprojaRepository bprojaRepository;
        private final ProjectService projectService;
        private final CostService costService;
        private final ObjectMapper objectMapper;

        /**
         * 전체 계획 목록을 조회합니다.
         *
         * <p>
         * 삭제되지 않은(DEL_YN='N') 계획을 등록일시 내림차순으로 반환합니다.
         * </p>
         *
         * @return 계획 목록 응답 DTO 리스트
         */
        @Transactional(readOnly = true)
        public List<PlanDto.ListResponse> getPlans() {
                return bplanmRepository.findAllByDelYnOrderByFstEnrDtmDesc("N")
                                .stream()
                                .map(PlanDto.ListResponse::fromEntity)
                                .collect(Collectors.toList());
        }

        /**
         * 계획관리번호로 단건 상세 조회합니다.
         *
         * <p>
         * 계획 정보와 연결된 프로젝트관리번호 목록, JSON 스냅샷을 함께 반환합니다.
         * </p>
         *
         * @param plnMngNo 계획관리번호 (예: PLN-2026-0001)
         * @return 계획 상세 응답 DTO
         * @throws ResponseStatusException 계획을 찾을 수 없는 경우 404
         */
        @Transactional(readOnly = true)
        public PlanDto.DetailResponse getPlan(String plnMngNo) {
                // 계획 조회
                Bplanm plan = bplanmRepository.findByPlnMngNoAndDelYn(plnMngNo, "N")
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "존재하지 않는 계획입니다: " + plnMngNo));

                // 연결된 프로젝트관리번호 목록 조회
                List<String> prjMngNos = bprojaRepository.findAllByBzMngNoAndDelYn(plnMngNo, "N")
                                .stream()
                                .map(Bproja::getPrjMngNo)
                                .collect(Collectors.toList());

                return PlanDto.DetailResponse.fromEntity(plan, prjMngNos);
        }

        /**
         * 정보기술부문 계획을 등록합니다.
         *
         * <p>
         * [처리 순서]
         * 1. 대상 프로젝트 목록을 ProjectService에서 조회
         * 2. 예산 합계(TTL_BG, CPT_BG, MNGC) 계산
         * 3. 부문(SVN_HDQ)별, 사업유형(PRJ_TP)별 그룹핑 후 JSON 스냅샷 생성
         * 4. 계획관리번호 채번: PLN-{plnYy}-{seq:04d}
         * 5. TAAABB_BPLANM 저장
         * 6. 각 프로젝트에 대해 TAAABB_BPROJA 저장
         * </p>
         *
         * @param request 계획 생성 요청 DTO
         * @return 생성된 계획관리번호
         * @throws ResponseStatusException 프로젝트 목록이 비어있는 경우 400
         */
        @Transactional
        public String createPlan(PlanDto.CreateRequest request) {
                // 대상사업 유효성 검사 (프로젝트 또는 전산업무비 중 1개 이상 선택 필수)
                List<String> prjMngNos = request.getPrjMngNos() != null ? request.getPrjMngNos() : List.of();
                List<String> itMngcNos = request.getItMngcNos() != null ? request.getItMngcNos() : List.of();

                if (prjMngNos.isEmpty() && itMngcNos.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "대상사업을 1개 이상 선택해야 합니다.");
                }

                // 1. 대상 정보화사업 목록 조회
                List<ProjectDto.Response> projects = List.of();
                if (!prjMngNos.isEmpty()) {
                        ProjectDto.BulkGetRequest bulkRequest = new ProjectDto.BulkGetRequest();
                        bulkRequest.setPrjMngNos(prjMngNos);
                        projects = projectService.getProjectsByIds(bulkRequest);
                }

                // 2. 대상 전산업무비 목록 조회
                List<CostDto.Response> costs = List.of();
                if (!itMngcNos.isEmpty()) {
                        CostDto.BulkGetRequest costBulkRequest = new CostDto.BulkGetRequest();
                        costBulkRequest.setItMngcNos(itMngcNos);
                        costs = costService.getCostsByIds(costBulkRequest);
                }

                // 3. 예산 합계 계산 (정보화사업 + 전산업무비)
                BigDecimal ttlBg = projects.stream()
                                .map(p -> p.getPrjBg() != null ? p.getPrjBg() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                ttlBg = costs.stream()
                                .map(c -> c.getItMngcBg() != null ? c.getItMngcBg() : BigDecimal.ZERO)
                                .reduce(ttlBg, BigDecimal::add);

                BigDecimal cptBg = projects.stream()
                                .map(p -> p.getAssetBg() != null ? p.getAssetBg() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                cptBg = costs.stream()
                                .map(c -> c.getAssetBg() != null ? c.getAssetBg() : BigDecimal.ZERO)
                                .reduce(cptBg, BigDecimal::add);

                BigDecimal mngc = projects.stream()
                                .map(p -> p.getCostBg() != null ? p.getCostBg() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                mngc = costs.stream()
                                .map(c -> c.getCostBg() != null ? c.getCostBg() : BigDecimal.ZERO)
                                .reduce(mngc, BigDecimal::add);

                // 4. JSON 스냅샷 생성
                String snapshotJson = buildSnapshot(request, projects, costs, ttlBg, cptBg, mngc);

                // 5. 계획관리번호 채번
                Long seq = bplanmRepository.getNextSequenceValue();
                String plnMngNo = String.format("PLN-%s-%04d", request.getPlnYy(), seq);

                // 6. TAAABB_BPLANM 저장
                Bplanm plan = Bplanm.builder()
                                .plnMngNo(plnMngNo)
                                .plnTp(request.getPlnTp())
                                .plnYy(request.getPlnYy())
                                .ttlBg(ttlBg)
                                .cptBg(cptBg)
                                .mngc(mngc)
                                .plnDtlCone(snapshotJson)
                                .build();
                bplanmRepository.save(plan);

                // 7. TAAABB_BPROJA 저장 (프로젝트/전산업무비-계획 관계)
                for (String prjMngNo : prjMngNos) {
                        Bproja relation = Bproja.builder()
                                        .prjMngNo(prjMngNo)
                                        .bzMngNo(plnMngNo)
                                        .build();
                        bprojaRepository.save(relation);
                }
                for (String itMngcNo : itMngcNos) {
                        Bproja relation = Bproja.builder()
                                        .prjMngNo(itMngcNo)
                                        .bzMngNo(plnMngNo)
                                        .build();
                        bprojaRepository.save(relation);
                }

                return plnMngNo;
        }

        /**
         * 계획을 논리 삭제합니다.
         *
         * <p>
         * 계획 엔티티의 DEL_YN을 'Y'로 변경하며,
         * 연결된 정보화사업 관계(BPROJA) 레코드도 함께 논리 삭제합니다.
         * </p>
         *
         * @param plnMngNo 계획관리번호
         * @throws ResponseStatusException 계획을 찾을 수 없는 경우 404
         */
        @Transactional
        public void deletePlan(String plnMngNo) {
                // 계획 존재 여부 확인
                Bplanm plan = bplanmRepository.findByPlnMngNoAndDelYn(plnMngNo, "N")
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "존재하지 않는 계획입니다: " + plnMngNo));

                // 계획 논리 삭제
                plan.delete();
                bplanmRepository.save(plan);

                // 연결된 정보화사업 관계 논리 삭제
                List<Bproja> relations = bprojaRepository.findAllByBzMngNoAndDelYn(plnMngNo, "N");
                for (Bproja relation : relations) {
                        relation.delete();
                        bprojaRepository.save(relation);
                }
        }

        /**
         * 계획 저장 시 PLN_DTL_CONE에 보관할 JSON 스냅샷을 생성합니다.
         *
         * <p>
         * 스냅샷 구조:
         * - 기본 정보(plnYy, plnTp, 예산 합계)
         * - 전체 프로젝트 목록(projects)
         * - 부문(SVN_HDQ)별 그룹 목록(byDepartment)
         * - 사업유형(PRJ_TP)별 그룹 목록(byProjectType)
         * </p>
         *
         * @param request  계획 생성 요청
         * @param projects 대상 정보화사업 목록
         * @param costs    대상 전산업무비 목록
         * @param ttlBg    총예산 합계
         * @param cptBg    자본예산 합계
         * @param mngc     일반관리비 합계
         * @return JSON 직렬화 문자열
         */
        private String buildSnapshot(PlanDto.CreateRequest request,
                        List<ProjectDto.Response> projects,
                        List<CostDto.Response> costs,
                        BigDecimal ttlBg, BigDecimal cptBg, BigDecimal mngc) {
                // 정보화사업 스냅샷 변환
                List<PlanDto.ProjectSnapshot> projectSnapshots = projects.stream()
                                .map(p -> PlanDto.ProjectSnapshot.builder()
                                                .prjMngNo(p.getPrjMngNo())
                                                .prjNm(p.getPrjNm())
                                                .prjTp(p.getPrjTp())
                                                .svnHdq(p.getSvnHdq())
                                                .svnDpm(p.getSvnDpm())
                                                .svnDpmNm(p.getSvnDpmNm())
                                                .prjBg(p.getPrjBg())
                                                .assetBg(p.getAssetBg())
                                                .costBg(p.getCostBg())
                                                .build())
                                .collect(Collectors.toList());

                // 전산업무비 스냅샷 변환 (정보화사업과 동일한 형식으로 매핑)
                List<PlanDto.ProjectSnapshot> costSnapshots = costs.stream()
                                .map(c -> PlanDto.ProjectSnapshot.builder()
                                                .prjMngNo(c.getItMngcNo())
                                                .prjNm(c.getCttNm())
                                                .prjTp(c.getItMngcTp())
                                                .svnHdq("미분류")
                                                .svnDpm(c.getBiceDpm())
                                                .svnDpmNm(c.getBiceDpmNm() != null ? c.getBiceDpmNm() : "")
                                                .prjBg(c.getItMngcBg())
                                                .assetBg(c.getAssetBg())
                                                .costBg(c.getCostBg())
                                                .build())
                                .collect(Collectors.toList());

                // 통합 스냅샷 목록
                projectSnapshots.addAll(costSnapshots);

                // 부문(SVN_HDQ)별 그룹핑
                Map<String, List<PlanDto.ProjectSnapshot>> byDeptMap = projectSnapshots.stream()
                                .collect(Collectors.groupingBy(
                                                p -> p.getSvnHdq() != null ? p.getSvnHdq() : "미분류"));

                List<Map<String, Object>> byDepartment = byDeptMap.entrySet().stream()
                                .map(entry -> {
                                        Map<String, Object> group = new HashMap<>();
                                        group.put("svnHdq", entry.getKey());
                                        group.put("projects", entry.getValue());
                                        return group;
                                })
                                .collect(Collectors.toList());

                // 사업유형(PRJ_TP)별 그룹핑
                Map<String, List<PlanDto.ProjectSnapshot>> byTypeMap = projectSnapshots.stream()
                                .collect(Collectors.groupingBy(
                                                p -> p.getPrjTp() != null ? p.getPrjTp() : "미분류"));

                List<Map<String, Object>> byProjectType = byTypeMap.entrySet().stream()
                                .map(entry -> {
                                        Map<String, Object> group = new HashMap<>();
                                        group.put("prjTp", entry.getKey());
                                        group.put("projects", entry.getValue());
                                        return group;
                                })
                                .collect(Collectors.toList());

                // 스냅샷 DTO 생성
                PlanDto.SnapshotDto snapshot = PlanDto.SnapshotDto.builder()
                                .plnYy(request.getPlnYy())
                                .plnTp(request.getPlnTp())
                                .ttlBg(ttlBg)
                                .cptBg(cptBg)
                                .mngc(mngc)
                                .projects(projectSnapshots)
                                .byDepartment(byDepartment)
                                .byProjectType(byProjectType)
                                .build();

                try {
                        return objectMapper.writeValueAsString(snapshot);
                } catch (JsonProcessingException e) {
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                        "계획 스냅샷 직렬화에 실패했습니다.");
                }
        }
}
