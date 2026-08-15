package com.nylg.zziiaicodemother.core.generation;

import lombok.Getter;
import org.reactivestreams.Subscription;
import reactor.core.Disposable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单次 AI 生成任务上下文，用于支持「手动停止生成」。
 * <p>
 * 一次用户点击发送对应一个 Task 实例，贯穿整条 SSE 流式链路。
 * 前端调用停止接口后，通过 {@link #cancel()} 标记取消并中断 Reactor 订阅。
 */
@Getter
public class AiGenerationTask {

    /** 当前生成所属应用 */
    private final Long appId;

    /** 发起生成的用户（与 Registry 的 key 组合使用） */
    private final Long userId;

    /** 是否已被用户手动停止 */
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * 聊天历史是否已写入数据库。
     * 停止时可能同时触发 doOnComplete / doOnCancel，用此标志避免重复保存。
     */
    private final AtomicBoolean historyPersisted = new AtomicBoolean(false);

    /** SSE 响应流的订阅对象，cancel() 时调用以停止向前端推送 */
    private volatile Subscription subscription;

    /** 备用的 Disposable，部分 Flux 场景下用于释放资源 */
    private volatile Disposable disposable;

    public AiGenerationTask(Long appId, Long userId) {
        this.appId = appId;
        this.userId = userId;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * 绑定 SSE 订阅，供 {@link AiGenerationTaskRegistry#cancel} 时中断推送。
     * 由 AppServiceImpl 在返回 Flux 前通过 doOnSubscribe 调用。
     */
    public void bindSubscription(Subscription subscription) {
        this.subscription = subscription;
    }

    public void bindDisposable(Disposable disposable) {
        this.disposable = disposable;
    }

    /**
     * 标记取消并中断当前 SSE 订阅。
     * 注意：这只能停止「向前端推流」，无法中断 LangChain4j 对 DeepSeek 的上游 HTTP 请求。
     */
    public void cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return;
        }
        Subscription currentSubscription = subscription;
        if (currentSubscription != null) {
            currentSubscription.cancel();
        }
        Disposable currentDisposable = disposable;
        if (currentDisposable != null && !currentDisposable.isDisposed()) {
            currentDisposable.dispose();
        }
    }

    /**
     * 尝试标记聊天历史已持久化。
     *
     * @return true 表示本次是第一个写入者；false 表示已经写过，应跳过
     */
    public boolean tryMarkHistoryPersisted() {
        return historyPersisted.compareAndSet(false, true);
    }
}
