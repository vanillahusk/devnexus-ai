package com.github.paicoding.forum.service.notify.repository.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.paicoding.forum.service.notify.repository.entity.DlqReplayAuditDO;
import com.github.paicoding.forum.service.notify.repository.mapper.DlqReplayAuditMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DlqReplayAuditDao extends ServiceImpl<DlqReplayAuditMapper, DlqReplayAuditDO> {
}
