package com.notification.infrastructure.slack;

import com.notification.domain.notification.application.dto.NotificationData;
import com.notification.domain.notification.application.port.out.NotificationSendPort;

// TODO: Slack Incoming Webhook 또는 Slack API를 통한 알림 발송 구현
// - Slack Bot Token, Webhook URL 등 설정 필요
// - 채널/DM 구분, 메시지 포맷팅 처리
public class SlackSender implements NotificationSendPort {

    @Override
    public void send(NotificationData data) {
        // TODO: Slack API 연동
    }
}
