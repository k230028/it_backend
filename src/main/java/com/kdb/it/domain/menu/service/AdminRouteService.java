package com.kdb.it.domain.menu.service;

import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.entity.Cmenud;
import com.kdb.it.domain.menu.repository.CmenudRepository;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 라우트 카탈로그(Cmenud) 관리. dead link 방지를 위해 저장 시 경로 형식·중복 검증. */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminRouteService {

    private final CmenudRepository cmenudRepository;
    private final CmenumRepository cmenumRepository;

    /**
     * LNK 메뉴 생성 시 선택 가능한 사용 중 라우트 목록을 조회한다.
     *
     * @return useYn='Y'이고 삭제되지 않은 라우트 카탈로그
     */
    @Transactional(readOnly = true)
    public List<Cmenud> listUsable() {
        return cmenudRepository.findAllUsable();
    }

    /**
     * 관리자 화면에서 사용할 전체 활성 라우트 목록을 조회한다.
     *
     * @return 삭제되지 않은 라우트 카탈로그
     */
    @Transactional(readOnly = true)
    public List<Cmenud> listAll() {
        return cmenudRepository.findAllActive();
    }

    /**
     * 라우트 카탈로그를 생성한다.
     *
     * @param req 화면 경로, 메뉴명, 시스템 상위 메뉴 ID, 사용 여부
     * @throws ResponseStatusException 경로 형식이 잘못되었거나 중복 경로가 있는 경우
     */
    public void create(MenuDto.Route req) {
        validatePath(req.getSrePth());
        cmenudRepository
                .findBySrePthAndDelYn(req.getSrePth(), "N")
                .ifPresent(
                        x -> {
                            throw new ResponseStatusException(
                                    HttpStatus.CONFLICT, "중복된 화면경로: " + req.getSrePth());
                        });
        cmenudRepository.save(
                Cmenud.builder()
                        .srePth(req.getSrePth())
                        .sreMnuNm(req.getSreMnuNm())
                        .useYn(req.getUseYn() == null ? "Y" : req.getUseYn())
                        .rmk(req.getRmk())
                        .delYn("N")
                        .build());
    }

    /**
     * 라우트 카탈로그 표시 정보와 사용 여부를 수정한다.
     *
     * @param req 수정할 라우트 정보. `srePth`는 기본키로 변경하지 않는다.
     * @throws ResponseStatusException 대상 경로가 없는 경우
     */
    public void update(MenuDto.Route req) {
        Cmenud c =
                cmenudRepository
                        .findBySrePthAndDelYn(req.getSrePth(), "N")
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "없는 경로: " + req.getSrePth()));
        // Cmenud는 setter가 없으므로 기본키 srePth를 유지한 새 엔티티를 저장해 JPA merge로 갱신한다.
        cmenudRepository.save(
                Cmenud.builder()
                        .srePth(c.getSrePth())
                        .sreMnuNm(req.getSreMnuNm())
                        .useYn(req.getUseYn())
                        .rmk(req.getRmk())
                        .delYn("N")
                        .build());
    }

    /**
     * 라우트 카탈로그를 Soft Delete 처리한다.
     *
     * @param srePth 삭제할 화면 경로
     * @throws ResponseStatusException 경로가 없거나 메뉴에서 참조 중인 경우
     */
    public void delete(String srePth) {
        Cmenud c =
                cmenudRepository
                        .findBySrePthAndDelYn(srePth, "N")
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "없는 경로: " + srePth));
        if (cmenumRepository.findAllActive().stream().anyMatch(m -> srePth.equals(m.getSrePth()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "메뉴에서 참조 중인 경로는 삭제할 수 없습니다.");
        }
        c.delete();
    }

    private void validatePath(String srePth) {
        if (srePth == null
                || !srePth.startsWith("/")
                || srePth.contains(" ")
                || srePth.startsWith("http")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "화면경로는 '/'로 시작하고 공백/외부 URL을 포함할 수 없습니다: " + srePth);
        }
    }
}
