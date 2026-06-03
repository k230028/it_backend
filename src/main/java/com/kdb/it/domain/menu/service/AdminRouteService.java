package com.kdb.it.domain.menu.service;

import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.entity.Cmenud;
import com.kdb.it.domain.menu.repository.CmenudRepository;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 라우트 카탈로그(Cmenud) 관리. dead link 방지를 위해 저장 시 경로 형식·중복 검증. */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminRouteService {

    private final CmenudRepository cmenudRepository;
    private final CmenumRepository cmenumRepository;

    @Transactional(readOnly = true)
    public List<Cmenud> listUsable() { return cmenudRepository.findAllUsable(); }

    @Transactional(readOnly = true)
    public List<Cmenud> listAll() { return cmenudRepository.findAllActive(); }

    public void create(MenuDto.Route req) {
        validatePath(req.getSrePth());
        cmenudRepository.findBySrePthAndDelYn(req.getSrePth(), "N").ifPresent(x -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "중복된 화면경로: " + req.getSrePth());
        });
        cmenudRepository.save(Cmenud.builder()
                .srePth(req.getSrePth()).sreMnuNm(req.getSreMnuNm()).sreTc(req.getSreTc())
                .useYn(req.getUseYn() == null ? "Y" : req.getUseYn()).rmk(req.getRmk()).delYn("N")
                .build());
    }

    public void update(MenuDto.Route req) {
        Cmenud c = cmenudRepository.findBySrePthAndDelYn(req.getSrePth(), "N")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 경로: " + req.getSrePth()));
        // Cmenud has no @Setter; rebuild + save (PK srePth unchanged → JPA merge)
        cmenudRepository.save(Cmenud.builder()
                .srePth(c.getSrePth()).sreMnuNm(req.getSreMnuNm()).sreTc(req.getSreTc())
                .useYn(req.getUseYn()).rmk(req.getRmk()).delYn("N").build());
    }

    public void delete(String srePth) {
        Cmenud c = cmenudRepository.findBySrePthAndDelYn(srePth, "N")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 경로: " + srePth));
        if (cmenumRepository.findAllActive().stream().anyMatch(m -> srePth.equals(m.getSrePth()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "메뉴에서 참조 중인 경로는 삭제할 수 없습니다.");
        }
        c.delete();
    }

    private void validatePath(String srePth) {
        if (srePth == null || !srePth.startsWith("/") || srePth.contains(" ")
                || srePth.startsWith("http")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "화면경로는 '/'로 시작하고 공백/외부 URL을 포함할 수 없습니다: " + srePth);
        }
    }
}
