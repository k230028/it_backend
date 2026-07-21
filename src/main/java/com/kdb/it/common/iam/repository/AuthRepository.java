package com.kdb.it.common.iam.repository;

import com.kdb.it.common.iam.entity.CauthI;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 자격등급(TPRMPP_CAUTHI) JPA 리포지토리
 *
 * <p>기본 CRUD 기능만 사용합니다. (findById, findAll 등)
 */
public interface AuthRepository extends JpaRepository<CauthI, String> {}
