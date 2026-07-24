package io.devnexus.dynamictp.demo.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.devnexus.dynamictp.demo.runner.StressSubmitter;
import io.devnexus.dynamictp.starter.core.DynamicThreadPoolCommandPublisher;
import io.devnexus.dynamictp.starter.model.ThreadPoolConfigVersionRecord;
import io.devnexus.dynamictp.starter.model.ThreadPoolRefreshCommand;
import io.devnexus.dynamictp.starter.model.ThreadPoolRollbackRequest;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class StressController {

    private final StressSubmitter stressSubmitter;
    private final DynamicThreadPoolCommandPublisher commandPublisher;

    public StressController(StressSubmitter stressSubmitter,
                            DynamicThreadPoolCommandPublisher commandPublisher) {
        this.stressSubmitter = stressSubmitter;
        this.commandPublisher = commandPublisher;
    }

    @PostMapping("/stress/start")
    public String startStress() {
        return stressSubmitter.start();
    }

    @PostMapping("/stress/stop")
    public String stopStress() {
        return stressSubmitter.stop();
    }

    @GetMapping("/stress/stats")
    public String stats() {
        return stressSubmitter.stats();
    }

    @PostMapping("/refresh")
    public String publish(@RequestBody ThreadPoolRefreshCommand command) throws JsonProcessingException {
        if (command.getVersion() == null) {
            command.setVersion(System.currentTimeMillis());
        }
        if (command.getRequestId() == null || command.getRequestId().trim().isEmpty()) {
            command.setRequestId("demo-" + command.getPoolName() + "-" + command.getVersion());
        }
        if (command.getSource() == null || command.getSource().trim().isEmpty()) {
            command.setSource("demo-http-api");
        }
        if (command.getTimestamp() == null) {
            command.setTimestamp(System.currentTimeMillis());
        }
        return commandPublisher.publish(command);
    }

    @PostMapping("/rollback")
    public String rollback(@RequestBody ThreadPoolRollbackRequest request) throws JsonProcessingException {
        if (request.getVersion() == null) {
            request.setVersion(System.currentTimeMillis());
        }
        if (request.getRequestId() == null || request.getRequestId().trim().isEmpty()) {
            request.setRequestId("rollback-" + request.getPoolName() + "-" + request.getVersion());
        }
        if (request.getSource() == null || request.getSource().trim().isEmpty()) {
            request.setSource("demo-rollback-api");
        }
        if (request.getTimestamp() == null) {
            request.setTimestamp(System.currentTimeMillis());
        }
        return commandPublisher.rollback(request);
    }

    @GetMapping("/config/history/{poolName}")
    public List<ThreadPoolConfigVersionRecord> history(@PathVariable("poolName") String poolName,
                                                       @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return commandPublisher.history(poolName, limit);
    }
}