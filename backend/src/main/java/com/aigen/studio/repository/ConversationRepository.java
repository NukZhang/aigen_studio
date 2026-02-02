package com.aigen.studio.repository;

import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 对话数据访问层
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * 根据状态查找对话列表
     */
    List<Conversation> findByStatus(Conversation.ConversationStatus status);

    /**
     * 根据阶段查找对话列表
     */
    List<Conversation> findByStage(ConversationStage stage);

    /**
     * 根据创建人查找对话列表
     */
    List<Conversation> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    /**
     * 根据作业 ID 查找对话
     */
    Optional<Conversation> findByJobId(Long jobId);

    /**
     * 查找活跃状态的对话列表
     */
    List<Conversation> findByStatusAndCreatedByOrderByCreatedAtDesc(Conversation.ConversationStatus status, String createdBy);
}