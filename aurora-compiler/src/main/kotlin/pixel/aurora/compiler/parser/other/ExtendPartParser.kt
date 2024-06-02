package pixel.aurora.compiler.parser.other

import pixel.aurora.compiler.parser.*
import pixel.aurora.compiler.parser.type.SimpleTypeParser
import pixel.aurora.compiler.parser.util.ListParser
import pixel.aurora.compiler.tokenizer.TokenType
import pixel.aurora.compiler.tree.SimpleType
import pixel.aurora.compiler.tree.other.ClassCall

class ExtendPartParser : Parser<Pair<ClassCall, List<SimpleType>>>() {

    override fun parse(): Pair<ClassCall, List<SimpleType>> {
        buffer.get().expect(":").expect(TokenType.PUNCTUATION)
        val extends = include(
            parser {
                val type = include(SimpleTypeParser())
                val arguments = include(ListParser(ArgumentParser() or NamedArgumentParser()))
                ClassCall(type, arguments)
            }.optional()
        ).getOrNull()
        val implements = include(
            ListParser(
                SimpleTypeParser(),
                if (extends == null) null else ",",
                null
            ).optional()
        ).getOrElse { emptyList() }
        return (extends ?: ClassCall(SimpleType.Any, emptyList())) to implements
    }

}