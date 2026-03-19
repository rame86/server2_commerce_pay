package com.example.payment.service.settlement;

import java.util.List;

import com.example.payment.domain.ArtistAccount;
import com.example.payment.dto.event.PaymentEventDTO;
import com.example.payment.dto.response.UserDetailPaymentResponseDTO;
import com.example.payment.dto.response.UserPaymentSummaryDTO;

public interface SettlementService {
    public void processSettlement(PaymentEventDTO dto);
    public ArtistAccount getArtistAccount(Long artistId);
    public List<UserPaymentSummaryDTO> getUserPaymentSummary(List<Long> memberId);
    public UserDetailPaymentResponseDTO getUserPaymentDetail(Long memberId)
    ;
}
