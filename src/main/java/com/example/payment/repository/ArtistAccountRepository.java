// src/main/java/com/example/payment/repository/ArtistAccountRepository.java
package com.example.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.payment.domain.ArtistAccount;

@Repository
public interface ArtistAccountRepository extends JpaRepository<ArtistAccount, Long> {
    // ArtistAccount 엔티티에 @Version이 적용되어 있으므로 
    // 기본 findById 사용 시 낙관적 락(Optimistic Lock)이 자동 적용됩니다.
}