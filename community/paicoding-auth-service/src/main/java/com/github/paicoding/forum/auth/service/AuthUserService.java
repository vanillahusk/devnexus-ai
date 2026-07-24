package com.github.paicoding.forum.auth.service;

import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.api.model.vo.user.UserInfoSaveReq;
import com.github.paicoding.forum.api.model.vo.user.UserPwdLoginReq;
import com.github.paicoding.forum.api.model.vo.user.dto.BaseUserInfoDTO;
import com.github.paicoding.forum.api.model.vo.user.dto.SimpleUserInfoDTO;
import com.github.paicoding.forum.api.model.vo.user.dto.UserStatisticInfoDTO;
import com.github.paicoding.forum.core.util.IpUtil;
import com.github.paicoding.forum.service.user.converter.UserConverter;
import com.github.paicoding.forum.service.user.repository.dao.UserAiDao;
import com.github.paicoding.forum.service.user.repository.dao.UserDao;
import com.github.paicoding.forum.service.user.repository.entity.IpInfo;
import com.github.paicoding.forum.service.user.repository.entity.UserAiDO;
import com.github.paicoding.forum.service.user.repository.entity.UserDO;
import com.github.paicoding.forum.service.user.repository.entity.UserInfoDO;
import com.github.paicoding.forum.service.user.service.UserAiService;
import com.github.paicoding.forum.service.user.service.UserService;
import com.github.paicoding.forum.service.user.service.help.UserPwdEncoder;
import com.github.paicoding.forum.service.user.service.help.UserSessionHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 认证服务只保留身份域需要的用户能力，避免加载主页统计、文章、评论和点赞 Bean。
 */
@Service
@RequiredArgsConstructor
public class AuthUserService implements UserService {

    private final UserDao userDao;
    private final UserAiDao userAiDao;
    private final UserSessionHelper userSessionHelper;
    private final UserPwdEncoder userPwdEncoder;
    private final UserAiService userAiService;

    @Override
    public UserDO getWxUser(String wxuuid) {
        return userDao.getByThirdAccountId(wxuuid);
    }

    @Override
    public List<SimpleUserInfoDTO> searchUser(String userName) {
        return userDao.getByUserNameLike(userName).stream()
                .map(UserConverter::toSimpleInfo)
                .toList();
    }

    @Override
    public void saveUserInfo(UserInfoSaveReq req) {
        userDao.updateUserInfo(UserConverter.toDO(req));
    }

    @Override
    public BaseUserInfoDTO getAndUpdateUserIpInfoBySessionId(String session, String clientIp) {
        Long userId = userSessionHelper.getUserIdBySession(session);
        if (userId == null) {
            return null;
        }
        UserInfoDO user = userDao.getByUserId(userId);
        if (user == null) {
            throw ExceptionUtil.of(StatusEnum.USER_NOT_EXISTS, "userId=" + userId);
        }
        IpInfo ip = user.getIp();
        if (clientIp != null && ip != null && !Objects.equals(ip.getLatestIp(), clientIp)) {
            ip.setLatestIp(clientIp);
            ip.setLatestRegion(IpUtil.getLocationByIp(clientIp).toRegionStr());
            if (ip.getFirstIp() == null) {
                ip.setFirstIp(clientIp);
                ip.setFirstRegion(ip.getLatestRegion());
            }
            userDao.updateById(user);
        }
        UserAiDO userAi = userAiDao.getByUserId(userId);
        return UserConverter.toDTO(user, userAi);
    }

    @Override
    public SimpleUserInfoDTO querySimpleUserInfo(Long userId) {
        UserInfoDO user = requireUser(userId);
        return UserConverter.toSimpleInfo(user);
    }

    @Override
    public BaseUserInfoDTO queryBasicUserInfo(Long userId) {
        return UserConverter.toDTO(requireUser(userId));
    }

    @Override
    public List<SimpleUserInfoDTO> batchQuerySimpleUserInfo(Collection<Long> userIds) {
        return userDao.getByUserIds(userIds).stream().map(UserConverter::toSimpleInfo).toList();
    }

    @Override
    public List<BaseUserInfoDTO> batchQueryBasicUserInfo(Collection<Long> userIds) {
        return userDao.getByUserIds(userIds).stream().map(UserConverter::toDTO).toList();
    }

    @Override
    public UserStatisticInfoDTO queryUserInfoWithStatistic(Long userId) {
        throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, "认证服务不提供用户主页统计");
    }

    @Override
    public Long getUserCount() {
        return userDao.getUserCount();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindUserInfo(UserPwdLoginReq req) {
        UserDO user = userDao.getUserByUserName(req.getUsername());
        if (user == null) {
            user = new UserDO();
            user.setId(req.getUserId());
        } else if (!Objects.equals(req.getUserId(), user.getId())) {
            throw ExceptionUtil.of(StatusEnum.USER_LOGIN_NAME_REPEAT, req.getUsername());
        }
        user.setUserName(req.getUsername());
        user.setPassword(userPwdEncoder.encPwd(req.getPassword()));
        userDao.saveUser(user);
        userAiService.initOrUpdateAiInfo(req);
    }

    private UserInfoDO requireUser(Long userId) {
        UserInfoDO user = userDao.getByUserId(userId);
        if (user == null) {
            throw ExceptionUtil.of(StatusEnum.USER_NOT_EXISTS, "userId=" + userId);
        }
        return user;
    }
}
