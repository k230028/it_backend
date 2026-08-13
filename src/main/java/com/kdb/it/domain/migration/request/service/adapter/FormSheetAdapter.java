package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.domain.migration.request.dto.FormSheetKind;

/** 편성요청서 시트를 도메인 생성 요청으로 바꿉니다. */
public interface FormSheetAdapter {

    /**
     * 이 어댑터를 발동시키는 시트입니다. 워크북에 이 시트가 있으면 실행됩니다.
     *
     * @return 발동 시트 종류
     */
    FormSheetKind trigger();

    /**
     * 시트를 읽어 생성 요청과 진단을 만듭니다.
     *
     * @param context 시트·연도·해석 인덱스·보정값 묶음
     * @return 생성 요청과 진단. 읽을 데이터가 없으면 {@link FormAdapterOutput#empty()}
     */
    FormAdapterOutput adapt(FormAdapterContext context);
}
