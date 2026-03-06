
    const schema = {
  "asyncapi": "2.6.0",
  "info": {
    "title": "결제 서비스 메시징 API",
    "version": "1.0.0",
    "description": "파이널 프로젝트 MSA 프로젝트 내 결제 및 환불 요청을 처리하는 메시지 규격서"
  },
  "servers": {
    "dev": {
      "url": "localhost:5672",
      "protocol": "amqp",
      "description": "개발용 RabbitMQ 서버"
    }
  },
  "channels": {
    "pay.request": {
      "description": "결제 및 환불 요청이 들어오는 채널 (Routing Key)",
      "publish": {
        "summary": "결제/환불 요청 발행",
        "message": {
          "name": "PaymentRequest",
          "title": "결제 요청 메시지",
          "summary": "결제, 환불, 지갑 충전 정보를 포함하는 데이터 객체",
          "payload": {
            "type": "object",
            "properties": {
              "orderId": {
                "type": "string",
                "description": "예약/주문 ID (결제 원장의 reference_id)",
                "x-parser-schema-id": "<anonymous-schema-2>"
              },
              "memberId": {
                "type": "integer",
                "format": "int64",
                "description": "결제 요청자의 회원 번호",
                "x-parser-schema-id": "<anonymous-schema-3>"
              },
              "amount": {
                "type": "number",
                "description": "총 결제 금액 (total_price)",
                "x-parser-schema-id": "<anonymous-schema-4>"
              },
              "type": {
                "type": "string",
                "enum": [
                  "PAYMENT",
                  "REFUND"
                ],
                "description": "요청 타입 (결제 또는 환불)",
                "x-parser-schema-id": "<anonymous-schema-5>"
              },
              "eventTitle": {
                "type": "string",
                "description": "공연명 또는 상품명",
                "x-parser-schema-id": "<anonymous-schema-6>"
              },
              "replyRoutingKey": {
                "type": "string",
                "description": "응답을 받고 싶은 서비스의 라우팅 키",
                "x-parser-schema-id": "<anonymous-schema-7>"
              },
              "chargeAmount": {
                "type": "number",
                "description": "지갑 충전 시 요청 금액",
                "x-parser-schema-id": "<anonymous-schema-8>"
              },
              "payType": {
                "type": "string",
                "description": "결제 수단 (예: kakao_pay, naver_pay)",
                "x-parser-schema-id": "<anonymous-schema-9>"
              }
            },
            "required": [
              "orderId",
              "memberId",
              "amount",
              "type"
            ],
            "x-parser-schema-id": "<anonymous-schema-1>"
          }
        }
      }
    }
  },
  "components": {
    "messages": {
      "PaymentRequest": "$ref:$.channels.pay.request.publish.message"
    }
  },
  "x-parser-spec-parsed": true,
  "x-parser-api-version": 3,
  "x-parser-spec-stringified": true
};
    const config = {"show":{"sidebar":true},"sidebar":{"showOperations":"byDefault"}};
    const appRoot = document.getElementById('root');
    AsyncApiStandalone.render(
        { schema, config, }, appRoot
    );
  