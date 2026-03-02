package com.aigen.studio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 飞书机器人配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "feishu")
public class FeishuProperties {

    /**
     * 飞书应用 App ID
     */
    private String appId;

    /**
     * 飞书应用 App Secret
     */
    private String appSecret;

    /**
     * 是否启用飞书机器人
     */
    private boolean enabled = false;

    /**
     * 域名：feishu（国内）或 lark（国际）
     */
    private String domain = "feishu";
}
