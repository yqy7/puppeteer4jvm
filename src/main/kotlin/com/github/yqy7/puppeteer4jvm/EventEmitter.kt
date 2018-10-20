package com.github.yqy7.puppeteer4jvm

import org.reactivestreams.Publisher
import reactor.core.publisher.ReplayProcessor
import reactor.core.scheduler.Schedulers
import java.util.function.Consumer

/**
 *  @author qiyun.yqy
 *  @date 2018/10/20
 */

data class Event(val name: String, val data: Any?)

open class EventEmitter {
    private val eventPublisher = ReplayProcessor.create<Event>()

    fun subscribeFrom(publisher: Publisher<Event>) {
        publisher.subscribe(eventPublisher)
    }

    fun on(eventName: String, consumer: Consumer<Event>) {
        eventPublisher.filter { event -> event.name == eventName }
                .subscribeOn(Schedulers.elastic())
                .subscribe(consumer)
    }

    fun emit(event: String, data: Any?) {
        eventPublisher.onNext(Event(event, data))
    }
}