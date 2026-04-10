// src/main/java/com/example/payment/repository/ArtistAccountRepository.java
package com.example.settlement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.settlement.entity.ArtistAccount;

/**
 * [아티스트 정산 계좌 레포지토리]
 * 아티스트의 수익 잔액 정보를 조회 및 수정하기 위한 데이터 접근 계층.
 */
@Repository
public interface ArtistAccountRepository extends JpaRepository<ArtistAccount, Long> {

    /**
     * [낙관적 락(Optimistic Lock) 작동 원리]
     * 1. ArtistAccount 엔티티의 @Version 필드를 통해 데이터 수정 시점의 버전을 체크.
     * 2. 데이터를 읽은 시점의 버전과 DB에 저장된 버전이 다르면 수정 요청을 거부.
     * 3. 별도의 쿼리 작성 없이 save() 또는 Dirty Checking 발생 시 하이버네이트가 
     * 'WHERE version = ?' 조건을 추가하여 동시성 충돌을 방지.
     */
}