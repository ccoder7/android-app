package one.mixin.android.util.mention

import one.mixin.android.util.mention.syntax.node.Node
import one.mixin.android.util.mention.syntax.parser.ParseSpec
import one.mixin.android.util.mention.syntax.parser.Parser
import one.mixin.android.util.mention.syntax.parser.Rule
import java.util.regex.Matcher
import java.util.regex.Pattern

class MentionConversationRule :
    Rule<MentionRenderContext, Node<MentionRenderContext>>(Pattern.compile("^@[\\d]{4,}")) {

    override fun parse(
        matcher: Matcher,
        parser: Parser<MentionRenderContext, in Node<MentionRenderContext>>
    ): ParseSpec<MentionRenderContext, Node<MentionRenderContext>> {
        return ParseSpec.createTerminal(MentionConversationNode(matcher.group()))
    }
}
