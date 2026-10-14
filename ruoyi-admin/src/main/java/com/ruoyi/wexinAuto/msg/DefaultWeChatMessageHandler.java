package com.ruoyi.wexinAuto.msg;

import com.ruoyi.wexinAuto.model.WeChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 默认处理器：把每条消息打日志并计数。
 * 要用你自己的业务逻辑，用 {@code @Primary} 标注一个实现了
 * {@link WeChatMessageHandler} 的 Bean 即可覆盖本类。
 */
@Component
@Slf4j
public class DefaultWeChatMessageHandler implements WeChatMessageHandler {

    private final AtomicLong processed = new AtomicLong(0);

    @Override
    public void handle(WeChatMessage m) {
        processed.incrementAndGet();
        log.info("收到微信消息 [{}|{}] {}: {} media={} t={}",
                m.getName(), m.getType(), m.getSender(), m.getContent(),
                m.getMediaPath(), m.getCreateTime());
    }

    public long count() {
        return processed.get();
    }
}


