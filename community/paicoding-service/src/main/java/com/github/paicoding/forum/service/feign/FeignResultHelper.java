package com.github.paicoding.forum.service.feign;

import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.Status;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import org.apache.commons.lang3.StringUtils;

public final class FeignResultHelper {

    private FeignResultHelper() {
    }

    public static <T> T unwrap(ResVo<T> response, String defaultErrorMessage) {
        if (response == null) {
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, defaultErrorMessage);
        }
        Status status = response.getStatus();
        if (status == null || status.getCode() != StatusEnum.SUCCESS.getCode()) {
            String msg = status == null ? defaultErrorMessage : status.getMsg();
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, StringUtils.defaultIfBlank(msg, defaultErrorMessage));
        }
        return response.getResult();
    }
}
