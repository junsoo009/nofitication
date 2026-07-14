package com.notification.infrastructure.sms;

import com.notification.domain.notification.application.dto.NotificationData;
import com.notification.domain.notification.application.port.out.NotificationSendPort;

// TODO: SMS 발송 구현 (AWS SNS, NHN Cloud, CoolSMS 등)
// - 발신번호, API Key 등 설정 필요
// - 국내/해외 발송 분기 처리
public class SmsSender implements NotificationSendPort {

    @Override
    public void send(NotificationData data) {
        // TODO: SMS API 연동
    }
}
