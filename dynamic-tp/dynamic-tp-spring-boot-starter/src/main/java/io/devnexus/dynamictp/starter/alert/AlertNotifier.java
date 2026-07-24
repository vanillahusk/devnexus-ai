package io.devnexus.dynamictp.starter.alert;

public interface AlertNotifier {

    void send(String title, String content);
}