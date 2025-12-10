package com.cuzzy.floweventbuslib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object EventBus {

    // 存储每种事件类型对应的 SharedFlow
     val stickyFlows = ConcurrentHashMap<Class<*>, MutableSharedFlow<Any>>()

    /**
     * 发布事件
     * @param event 事件对象
     * @param sticky 是否为粘性事件
     */
    fun post(event: Any, sticky: Boolean = false) {
        val clazz = event::class.java
        val flow = stickyFlows.getOrPut(clazz) {
            MutableSharedFlow(extraBufferCapacity = 64, replay = if (sticky) 1 else 0)
        }
        flow.tryEmit(event)
    }

    /**
     * 订阅事件
     * @param scope 协程作用域，可自定义
     * @param once 是否只接收一次事件
     */
    inline fun <reified T> subscribe(
        scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
        once: Boolean = false,
        crossinline onEvent: (T) -> Unit
    ) {
        val clazz = T::class.java
        val flow: SharedFlow<Any> = stickyFlows.getOrPut(clazz) {
            MutableSharedFlow(extraBufferCapacity = 64, replay = 0)
        }

        scope.launch {
            if (once) {
                // 只接收一次事件
                val event = flow.first { it is T } as T
                onEvent(event)
            } else {
                // 持续接收事件
                flow.collect { event ->
                    if (event is T) {
                        onEvent(event)
                    }
                }
            }
        }
    }

    /**
     * 清除某个事件类型的最后一次粘性事件
     */
    inline fun <reified T> clearSticky() {
        stickyFlows[T::class.java]?.resetReplayCache()
    }
}
