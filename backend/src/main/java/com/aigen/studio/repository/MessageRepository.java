package com.aigen.studio.repository;

import com.aigen.studio.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 消息数据访问层
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * 根据对话 ID 查找消息列表（按创建时间排序）
     */
    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    /**
     * 根据对话 ID 和角色查找消息列表
     */
    List<Message> findByConversationIdAndRoleOrderByCreatedAtAsc(Long conversationId, Message.MessageRole role);
}