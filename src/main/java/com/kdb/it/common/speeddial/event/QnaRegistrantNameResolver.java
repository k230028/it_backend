package com.kdb.it.common.speeddial.event;

import com.kdb.it.common.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 문의 등록자 이름을 알림 리스너와 분리된 트랜잭션에서 조회합니다. */
@Component
@RequiredArgsConstructor
public class QnaRegistrantNameResolver {

    private final UserRepository userRepository;

    /** 사용자명이 없으면 알림에 식별 정보가 남도록 사번을 반환합니다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public String resolve(String authorEno) {
        if (!StringUtils.hasText(authorEno)) {
            return authorEno;
        }
        return userRepository
                .findNameViewByEno(authorEno)
                .map(UserRepository.UserNameView::getUsrNm)
                .filter(StringUtils::hasText)
                .orElse(authorEno);
    }
}
