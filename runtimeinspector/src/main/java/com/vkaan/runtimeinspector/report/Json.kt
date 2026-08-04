package com.vkaan.runtimeinspector.report


internal object Json {

    private const val HEX = "0123456789abcdef"

    /** Emits a flat object. Int/Long/Boolean are written bare, null as `null`, the rest quoted. */
    fun obj(vararg fields: Pair<String, Any?>): String = buildString {
        append('{')
        fields.forEachIndexed { index, (name, value) ->
            if (index > 0) append(',')
            append('"').append(escape(name)).append("\":")
            appendValue(value)
        }
        append('}')
    }

    private fun StringBuilder.appendValue(value: Any?) {
        when (value) {
            null -> append("null")
            is Int, is Long, is Boolean -> append(value.toString())
            else -> append('"').append(escape(value.toString())).append('"')
        }
    }

    /**
     * Escapes per RFC 8259. Newlines matter most here: findings are written one per line, so an
     * unescaped `\n` in a rule message would split one record into two unparseable halves.
     */
    fun escape(value: String): String = buildString(value.length) {
        for (c in value) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c == '\b' -> append("\\b")
                c == '' -> append("\\f")
                // Remaining control characters have no short escape. Built by hand rather than
                // with String.format, which would read the default locale.
                c < ' ' -> append("\\u00").append(HEX[c.code shr 4]).append(HEX[c.code and 0xF])
                else -> append(c)
            }
        }
    }
}
