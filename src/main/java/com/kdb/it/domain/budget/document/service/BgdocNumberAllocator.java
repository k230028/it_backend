package com.kdb.it.domain.budget.document.service;

import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** BGDOC 문서의 공통 문서관리번호를 발급합니다. */
@Component
@RequiredArgsConstructor
public class BgdocNumberAllocator {

    private final GuideDocRepository guideDocRepository;
    private final Clock clock;

    /**
     * BGDOC 공유 시퀀스와 현재 연도로 문서관리번호를 발급합니다.
     *
     * @param prefix 기능별 문서관리번호 접두사
     * @return {@code {prefix}{yyyy}-{seq:04d}} 형식의 문서관리번호
     */
    public String next(String prefix) {
        long sequence = guideDocRepository.getNextSequenceValue();
        return "%s%d-%04d".formatted(prefix, LocalDate.now(clock).getYear(), sequence);
    }
}
