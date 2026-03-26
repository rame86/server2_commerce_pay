package com.example.settlement.dto;

import java.util.List;
import lombok.*;


//-----------------------------------------------------------------------------------------------------------
// [아티스트 후원 내역 조회]
@Getter 
@Setter 
@Builder
@NoArgsConstructor 
@AllArgsConstructor
public class ArtistDonationResponse {
    private Long thisMonthAmount;
    private Long totalAmount;
    private Integer donorCount;
    private Long maxSingleDonation;
    private List<DailyTrendDTO> dailyTrend;
    private List<DonorDTO> topDonors;
    private List<DonationMessageDTO> messages;

    @Getter 
    @Setter 
    @Builder 
    @AllArgsConstructor 
    @NoArgsConstructor
    public static class DailyTrendDTO {
        private String date;
        private Long amount;
    }

    @Getter 
    @Setter 
    @Builder 
    @AllArgsConstructor 
    @NoArgsConstructor
    public static class DonorDTO {
        private String name;
        private Long total;
    }

    @Getter 
    @Setter 
    @Builder 
    @AllArgsConstructor 
    @NoArgsConstructor
    public static class DonationMessageDTO {
        private String userName;
        private String createdAt;
        private Long amount;
        private String content;
    }
}