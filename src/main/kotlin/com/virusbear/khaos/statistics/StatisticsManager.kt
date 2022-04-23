package com.virusbear.khaos.statistics

import kotlin.reflect.KClass

object StatisticsManager {
    private val statistics = LinkedHashMap<KClass<Statistics>, MutableSet<Statistics>>()

    inline fun <reified T: Statistics> register(instance: T) {
        register(T::class as KClass<Statistics>, instance)
    }

    fun register(klass: KClass<Statistics>, instance: Statistics) {
        statistics.computeIfAbsent(klass) {
            LinkedHashSet()
        }.add(instance)
    }

    inline fun <reified T: Statistics> get(): List<T> =
        get(T::class as KClass<Statistics>) as List<T>

    fun get(klass: KClass<Statistics>): List<Statistics> =
        statistics[klass]?.toList() ?: emptyList()

    inline fun <reified T: Statistics> remove(instance: T) {
        remove(T::class as KClass<Statistics>, instance)
    }

    fun remove(klass: KClass<Statistics>, instance: Statistics) {
        statistics[klass]?.remove(instance)
    }
}