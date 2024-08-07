package pixel.aurora.core.tokenizer

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import pixel.aurora.core.Aurora
import pixel.aurora.core.parser.BaseParseException
import java.net.URI
import java.nio.CharBuffer

class DefaultTokenizer(
    private val buffer: CharBuffer,
    private val uri: URI = Aurora.BLANK_URI,
    var punctuations: String = DEFAULT_PUNCTUATIONS
) : Tokenizer {

    constructor(content: CharSequence, uri: URI = Aurora.BLANK_URI, punctuations: String = DEFAULT_PUNCTUATIONS) : this(
        CharBuffer.wrap(content),
        uri,
        punctuations
    )

    companion object {

        const val DEFAULT_PUNCTUATIONS = "+-*/<>,.;:?()[]{}!%|&=@"

    }

    private val objectMapper = jacksonObjectMapper()

    override fun getBuffer() = buffer
    override fun getUri() = uri

    fun lexBlockComment() {
        buffer.position(buffer.position() + 2)
        var last: Char = buffer.get()
        while (true) {
            val current = buffer.get()
            if (current == '/' && last == '*') break
            last = current
        }
    }

    override fun tokenize(): Sequence<Token> = sequence {
        var whitespace = ""
        while (true) {
            if (buffer.isEmpty()) break
            val character = buffer.get()
            if (character.isWhitespace() || character in "\n\r") {
                whitespace += character
                continue
            } else {
                whitespace = ""
                yield(WhitespaceToken(whitespace))
            }
            buffer.position(buffer.position() - 1)
            if (character == '/' && buffer.get(buffer.position() + 1) == '*') {
                lexBlockComment()
                continue
            } else if (character.lowercaseChar() == 'i' && buffer.get(buffer.position() + 1) == '"') {
                buffer.get()
                val string = lexString()
                yield(IdentifierToken(raw = string.getRaw(), parsed = string.getString(), isStringify = true))
            } else if (character.lowercaseChar() == 'r' && buffer.get(buffer.position() + 1) == '"') {
                buffer.get()
                val raw = lexString().getRaw()
                yield(StringToken(raw = objectMapper.writeValueAsString(raw), parsed = raw))
            } else if (character.isJavaIdentifierStart()) yield(lexIdentifier())
            else if (character == '"') yield(lexString())
            else if (isNumeric()) yield(lexNumeric())
            else if (character in punctuations) yield(PunctuationToken(buffer.get()))
            else throw makeError("Invalid syntax")
        }
    }

    fun lexNumeric(): Token {
        return if (buffer.startsWith("0x") || buffer.startsWith("0X")) lexHexNumber()
        else if (buffer.startsWith("0b") || buffer.startsWith("0B")) lexBinaryNumber()
        else if (buffer.startsWith("0o") || buffer.startsWith("0O")) lexOctalNumber()
        else lexDecimalNumber()
    }

    fun lexDecimalNumber(): Token {
        var raw = ""
        var character: Char
        while (true) {
            try {
                character = buffer.get()
            } catch (_: Throwable) {
                break
            }
            if (!(character.isDigit() || (character == '.' && '.' !in raw) || (character.lowercase() == "e" && 'e' !in raw.lowercase()) || (character in "+-" && (raw.last()
                    .lowercase() == "e")))
            ) {
                buffer.position(buffer.position() - 1)
                break
            }
            raw += character
        }
        if (raw.isEmpty()) throw makeError("Invalid or unexpected token")
        return NumericToken(raw, raw.toBigDecimal())
    }

    fun lexOctalNumber(): Token {
        buffer.position(buffer.position() + 2)
        var raw = "0o"
        var character: Char
        while (true) {
            try {
                character = buffer.get()
            } catch (_: Throwable) {
                break
            }
            if (!(character.isDigit() && character !in "89")) {
                buffer.position(buffer.position() - 1)
                break
            }
            raw += character
        }
        if (raw.length == 2) throw makeError("Invalid or unexpected token")
        return NumericToken(raw, raw.slice(2..<raw.length).toBigInteger(8).toBigDecimal())
    }

    fun lexBinaryNumber(): Token {
        buffer.position(buffer.position() + 2)
        var raw = "0b"
        var character: Char
        while (true) {
            try {
                character = buffer.get()
            } catch (_: Throwable) {
                break
            }
            if (character !in "01") {
                buffer.position(buffer.position() - 1)
                break
            }
            raw += character
        }
        if (raw.length == 2) throw makeError("Invalid or unexpected token")
        return NumericToken(raw, raw.slice(2..<raw.length).toBigInteger(2).toBigDecimal())
    }

    fun lexHexNumber(): Token {
        buffer.position(buffer.position() + 2)
        var raw = "0x"
        var character: Char
        while (true) {
            try {
                character = buffer.get()
            } catch (_: Throwable) {
                break
            }
            if (!(character.isDigit() || character in 'a'..'f' || character in 'A'..'F')) {
                buffer.position(buffer.position() - 1)
                break
            }
            raw += character
        }
        if (raw.length == 2) throw makeError("Invalid or unexpected token")
        return NumericToken(raw, raw.slice(2..<raw.length).toBigInteger(16).toBigDecimal())
    }

    fun isNumeric(): Boolean {
        val character = buffer.get(buffer.position())
        if (character.isDigit()) return true
        if (character == '.') {
            try {
                if (buffer.get(buffer.position() + 1).isDigit()) return true
            } catch (_: Throwable) {
            }
        }
        return false
    }

    fun lexString() = runCatching {
        var raw = buffer.get().toString()
        while (buffer.get(buffer.position()) != '"') {
            if (buffer.get(buffer.position()) == '\\') {
                raw += buffer.get()
            }
            raw += buffer.get()
        }
        raw += buffer.get()
        try {
            return@runCatching StringToken(raw, objectMapper.readValue<String>(raw))
        } catch (_: Throwable) {
            throw makeError("Invalid string literal: $raw")
        }
    }.let {
        if (it.isFailure && it.exceptionOrNull() !is BaseParseException) throw makeError("Invalid or unexpected token")
        else if (it.isFailure) throw it.exceptionOrNull()!!
        else it.getOrThrow()
    }

    fun lexIdentifier(): Token {
        var identifier = "${buffer.get()}"
        try {
            while (true) {
                val character = buffer.get()
                if (character.isJavaIdentifierPart()) identifier += character
                else {
                    buffer.position(buffer.position() - 1)
                    break
                }
            }
        } catch (_: Throwable) {
        }
        return when (identifier) {
            "null" -> NullToken()
            "true", "false" -> BooleanToken(identifier)
            else -> IdentifierToken(identifier, identifier)
        }
    }

}
