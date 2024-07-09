package pixel.aurora.core.parser

import pixel.aurora.core.tokenizer.TokenBuffer
import java.net.URI

abstract class Parser<T : Any>(name: String? = null) {

    private val name: String = name ?: this::class.java.simpleName

    fun getName() = name

    data class State(
        var uri: URI,
        var buffer: TokenBuffer,
        var parent: Parser<*>? = null,
        var isMarked: Boolean = false
    )

    private var state: State? = null
    open fun setState(state: State?) = this.also { this.state = state }
    open fun getStateOrNull(): State? = state
    open fun getState() = getStateOrNull()!!
    open fun mark(isMarked: Boolean) {
        setState(getStateOrNull()?.copy(isMarked = isMarked))
        getStateOrNull()?.parent?.mark(isMarked)
    }

    open fun mark() = getStateOrNull()?.isMarked == true

    abstract fun parse(): T

    open fun makeError(message: String? = null, cause: Throwable? = null) = getState().let {
        ParserException(message, it.buffer, this, it, cause)
    }

}


class ParserSequence<T : Any>(name: String? = null, private val block: Parser<T>.() -> T) : Parser<T>(name) {

    override fun parse(): T = block()

}
