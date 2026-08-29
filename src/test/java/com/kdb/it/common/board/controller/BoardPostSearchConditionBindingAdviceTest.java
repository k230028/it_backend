package com.kdb.it.common.board.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.board.dto.BoardPostDto;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.WebDataBinder;

class BoardPostSearchConditionBindingAdviceTest {

    @Test
    @DisplayName("게시물 검색조건 바인더는 서버 전용 플래그를 막고 정상 필드는 그대로 바인딩한다")
    void binder_blocksServerOnlyField_andBindsNormalFields() throws Exception {
        Class<?> adviceType =
                Class.forName(
                        "com.kdb.it.common.board.controller.BoardPostSearchConditionBindingAdvice");
        Object advice = adviceType.getDeclaredConstructor().newInstance();
        Method initBinder =
                adviceType.getDeclaredMethod("initSearchConditionBinder", WebDataBinder.class);

        BoardPostDto.SearchCondition condition = new BoardPostDto.SearchCondition();
        WebDataBinder binder = new WebDataBinder(condition);
        initBinder.invoke(advice, binder);
        binder.bind(
                new MutablePropertyValues()
                        .add("ignorePublicationPeriod", "true")
                        .add("keyword", "공지")
                        .add("page", "3")
                        .add("size", "50"));

        assertThat(condition.isIgnorePublicationPeriod()).isFalse();
        assertThat(condition.getKeyword()).isEqualTo("공지");
        assertThat(condition.getPage()).isEqualTo(3);
        assertThat(condition.getSize()).isEqualTo(50);
    }
}
