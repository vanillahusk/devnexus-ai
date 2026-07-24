package com.github.paicoding.forum.service.user.service.favor;

public interface RejectAwareRunnable extends Runnable {

    void onRejected(String poolName);
}
