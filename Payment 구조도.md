# Payment  구조도
<src/main/ >
    ├ </java/com/example/ >
    │   ├── [PaymentApplication.java]
    │   │
    │   ├── <common/ >                        # 공통 유틸, 예외 처리, 상수 등
    │   │    ├── <exception/ >                # Custom Exception 클래스
    │   │    └── <util/ >                     # 공통 유틸리티 (날짜, 문자열 등)
    │   │
    │   ├── <config/ >                        # 설정 관련
    │   │    ├── [FrontendUrlProperties.java]
    │   │    ├── [KakaoPayConfig.java]
    │   │    └── [RabbitMQConfig.java]
    │   │
    │   └── <payment/ >                  # 도메인
    │       │ 
    │       ├── <controller/ >           # Controller HTTP 요청/응답 처리
    │       │    ├── [PaymentAdminController.java]
    │       │    ├── [PaymentController.java]
    │       │    └── [WalletController.java]
    │       │
    │       ├── <domain/ >                 # domain 데이터 모델
    │       │    ├── [ArtistAccount.java]
    │       │    ├── [Charge.java]
    │       │    ├── [Ledger.java]
    │       │    ├── [TransactionHistory.java]
    │       │    └── [Wallet.java]
    │       │
    │       ├── <dto/ >
    │       │    │
    │       │    ├── <event/ >           # 메시지 브로커 전송/수신용
    │       │    │    └── PaymentEventDTO.java
    │       │    │
    │       │    ├── <request/ >         # Controller 요청용
    │       │    │    └── ChargeRequestDTO.java
    │       │    │
    │       │    └── <response/ >        # Controller 응답용
    │       │         ├── [ChargeReadyResponseDTO.java]
    │       │         ├── [PaymentHistoryResponseDTO.java]
    │       │         ├── [PaymentResponseDTO.java]
    │       │         └── [WalletResponseDTO.java]
    │       │
    │       │
    │       ├── <messaging/ >            # RabbitMQ 메시징 관련
    │       │    │
    │       │    ├── <listener/ >        # 소비자
    │       │    │    └── [PaymentEventListener.java]
    │       │    │
    │       │    └── <producer/ >        # 발행자
    │       │         └── [PaymentEventProducer.java]
    │       │
    │       ├── <repository/ >           # Repository 인터페이스
    │       │    ├── [ChargeRepository.java]
    │       │    ├── [TransactionHistoryRepository.java]
    │       │    └── [WalletRepository.java]
    │       │
    │       └── <service/ >              # Service 비즈니스 로직 처리 및 트랜잭션 관리
    │            │
    │            ├── <provider/ >        # Provider 외부 API 연결
    │            │    ├── [KakaoPayProvider.java]
    │            │    └── [paymentProvider.java]
    │            │
    │            ├── [PaymentService.java]
    │            ├── [PaymentServiceImpl.java]
    │            ├── [WalletService.java]
    │            └── [WalletServiceImpl.java]
    │
    └── <resources/ >
        │
        ├── <mappers/ >                  # MyBatis SQL XML 파일 위치 (쿼리문 작성)
        │    └── [PaymentMapper.xml]
        │
        ├── <static/ >                   # 정적 리소스 (이미지, JS, CSS)
        │
        ├── [application.yml]            # 환경 설정
        ├── [application-dev.yml]        # 개발 환경 설정(하드코딩)
        └── [application-prod.yml]       # 운영 환경 배포용 설정(.env 참조)

<github/workflows/ >
└── [ci-cd.yml]                  # git action 배포 설정 정의용 파일
[.env]                           # 설정 파일
[Dockerfile]                     # 컨테이너 빌드용 파일
[docker-compose.yml]             # 로컬 실행 및 서비스 정의용 파일