package io.devnexus.dynamictp.starter.alert;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DingTalkAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(DingTalkAlertNotifier.class);

    private final String webhook;

    public DingTalkAlertNotifier(String webhook) {
        this.webhook = webhook;
    }

    @Override
    public void send(String title, String content) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(webhook);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            String escapedContent = content.replace("\\", "\\\\").replace("\"", "\\\"");
            String escapedTitle = title.replace("\\", "\\\\").replace("\"", "\\\"");
            String payload = "{\"msgtype\":\"markdown\",\"markdown\":{\"title\":\""
                    + escapedTitle + "\",\"text\":\"### " + escapedTitle + "\\n> " + escapedContent + "\"}}";

            OutputStream outputStream = connection.getOutputStream();
            try {
                outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            } finally {
                outputStream.close();
            }

            int status = connection.getResponseCode();
            if (status >= 300) {
                log.warn("DingTalk alert failed, http status={}", status);
            }
        } catch (Exception exception) {
            log.warn("Failed to call DingTalk webhook", exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}