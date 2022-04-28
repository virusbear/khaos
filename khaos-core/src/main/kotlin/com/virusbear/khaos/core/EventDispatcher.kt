package com.virusbear.khaos.core

object EventDispatcher {
    private val handlers: MutableSet<Pair<(Event) -> Boolean, EventHandler>> = LinkedHashSet()

    fun dispatch(event: Event) {
        handlers.forEach { (selector, handler) ->
             if(selector(event)) {
                 handler.handle(event)
             }
        }
    }

    fun subscribe(handler: EventHandler, selector: (Event) -> Boolean) {
        handlers += selector to handler
    }

    inline fun <reified T: Event> subscribe(handler: EventHandler) {
        subscribe(handler) { it is T }
    }
}