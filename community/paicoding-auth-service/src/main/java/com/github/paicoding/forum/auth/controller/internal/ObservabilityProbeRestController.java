package com.github.paicoding.forum.auth.controller.internal;

import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.service.user.config.AuthServiceProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Deterministic slow/error endpoint used only by the local observability
 * evidence job. It is absent unless explicitly enabled at process startup and
 * remains protected by the auth service internal token.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "internal/auth/observability")
@ConditionalOnProperty(name = "observability.probe.enabled", havingValue = "true")
public class ObservabilityProbeRestController {

    private static final long MAX_DELAY_MS = 3_000L;

    private final AuthInternalAccessValidator validator;
    private final AuthServiceProperties properties;

    @GetMapping(path = "probe")
    public ResVo<String> probe(@RequestParam(defaultValue = "0") long delayMs,
                               @RequestParam(defaultValue = "false") boolean fail,
                               HttpServletRequest request) {
        validator.validate(request.getHeader(properties.getTokenHeader()));
        if (delayMs < 0 || delayMs > MAX_DELAY_MS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "delayMs must be between 0 and " + MAX_DELAY_MS);
        }
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "observability probe interrupted", ex);
            }
        }
        if (fail) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "intentional observability probe failure");
        }
        return ResVo.ok("delayMs=" + delayMs);
    }
}
