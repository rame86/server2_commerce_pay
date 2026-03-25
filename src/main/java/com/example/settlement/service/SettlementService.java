package com.example.settlement.service;

import com.example.payment.domain.ArtistAccount;
import com.example.payment.dto.event.PaymentEventDTO;

public interface SettlementService {
    public void processSettlement(PaymentEventDTO dto);

    public ArtistAccount getArtistAccount(Long artistId);

}
