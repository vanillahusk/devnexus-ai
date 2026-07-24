package io.devnexus.dynamictp.starter.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingAlertNotifier.class);

    @Override
    public void send(String title, String content) {
        log.error("[dynamic-tp-alert] {} -> {}", title, content);
    }
}