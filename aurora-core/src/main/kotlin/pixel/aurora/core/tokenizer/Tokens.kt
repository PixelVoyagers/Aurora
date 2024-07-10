package pixel.aurora.core.tokenizer

import pixel.aurora.core.parser.Parser
import pixel.aurora.core.parser.buffer
import pixel.aurora.core.parser.include
import pixel.aurora.core.parser.operator.optional
import pixel.aurora.core.parser.rule

context(Parser<*>)
inline fun <reified T> Token.expect() = this.also {
    if (!T::class.isInstance(this))
        throw makeError("Expected token: ${T::class}, bot got ${this::class}")
} as T

context(Parser<*>)
fun <T : Token> T.expect(raw: String) = this.also {
    if (!this.isToken<Token>(raw)) throw makeError("Invalid token '${this.getRaw()}', did you mean '$raw'?")
}

context(Parser<*>)
fun <T : Token> T.expectIdentifier(identifier: String) = expect<IdentifierToken>().expect(identifier)

context(Parser<*>)
fun <T : Token> T.expectPunctuation(character: Char) = expect<PunctuationToken>().expect(character.toString())

context(Parser<*>)
inline fun <reified T> token() = buffer.get().expect<T>()

context(Parser<*>)
fun numeric() = buffer.get().expect<NumericToken>()

context(Parser<*>)
fun identifier(name: String? = null) = buffer.get().expect<IdentifierToken>().let {
    if (name != null) it.expect(name)
    else it
}

context(Parser<*>)
fun punctuation(punctuations: String) = punctuations.map { buffer.get().expectPunctuation(it) }

context(Parser<*>)
fun boolean() = buffer.get().expect<BooleanToken>()

context(Parser<*>)
fun whitespace(optional: Boolean = true) = include(
    rule {
        buffer.get().expect<WhitespaceToken>()
    }.optional()
).getOrNull().let {
    if (!optional) it!!
    else it
}
