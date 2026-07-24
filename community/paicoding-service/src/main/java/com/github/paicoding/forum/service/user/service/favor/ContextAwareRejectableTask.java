package com.github.paicoding.forum.service.user.service.favor;

import com.github.paicoding.forum.api.model.context.ReqInfoContext;
import org.slf4j.MDC;

import java.util.Map;

public class ContextAwareRejectableTask implements RejectAwareRunnable {

    private final Runnable delegate;
    private final RejectAwareRunnable rejectAwareDelegate;
    private final ReqInfoContext.ReqInfo reqInfoSnapshot;
    private final Map<String, String> mdcSnapshot;

    public ContextAwareRejectableTask(Runnable delegate) {
        this.delegate = delegate;
        this.rejectAwareDelegate = delegate instanceof RejectAwareRunnable ? (RejectAwareRunnable) delegate : null;
        this.reqInfoSnapshot = ReqInfoContext.getReqInfo();
        this.mdcSnapshot = MDC.getCopyOfContextMap();
    }

    @Override
    public void run() {
        ReqInfoContext.ReqInfo oldReqInfo = ReqInfoContext.getReqInfo();
        Map<String, String> oldMdc = MDC.getCopyOfContextMap();
        try {
            if (reqInfoSnapshot != null) {
                ReqInfoContext.addReqInfo(reqInfoSnapshot);
            } else {
                ReqInfoContext.clear();
            }
            if (mdcSnapshot != null) {
                MDC.setContextMap(mdcSnapshot);
            } else {
                MDC.clear();
            }
            delegate.run();
        } finally {
            if (oldReqInfo != null) {
                ReqInfoContext.addReqInfo(oldReqInfo);
            } else {
                ReqInfoContext.clear();
            }
            if (oldMdc != null) {
                MDC.setContextMap(oldMdc);
            } else {
                MDC.clear();
            }
        }
    }

    @Override
    public void onRejected(String poolName) {
        if (rejectAwareDelegate != null) {
            rejectAwareDelegate.onRejected(poolName);
        }
    }
}
