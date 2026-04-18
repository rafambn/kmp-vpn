package com.rafambn.wgkotlin.session.io

class ManualPeriodicTicker(
    private val values: ArrayDeque<Boolean> = ArrayDeque(),
) : () -> Boolean {
    override fun invoke(): Boolean {
        return values.removeFirstOrNull() ?: false
    }

    fun enqueue(value: Boolean) {
        values.addLast(value)
    }
}
