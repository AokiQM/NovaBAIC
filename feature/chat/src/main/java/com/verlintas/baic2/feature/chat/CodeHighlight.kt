/*
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of BetterAIChat2.
 *
 * BetterAIChat2 is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * BetterAIChat2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * BetterAIChat2. If not, see <https://www.gnu.org/licenses/>.
 */

package com.verlintas.baic2.feature.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.verlintas.baic2.core.model.CodeToken
import com.verlintas.baic2.core.model.CodeTokenizer

/** Maps [CodeTokenizer] spans onto the current theme's colours. */
@Composable
fun highlightedCode(code: String, language: String): AnnotatedString {
    if (code.length > MAX_HIGHLIGHT_CHARS) return AnnotatedString(code)
    val tokens = remember(code, language) { CodeTokenizer.tokenize(code, language) }
    if (tokens.isEmpty()) return AnnotatedString(code)

    val keyword = MaterialTheme.colorScheme.primary
    val string = MaterialTheme.colorScheme.tertiary
    val number = MaterialTheme.colorScheme.secondary
    val function = MaterialTheme.colorScheme.secondary
    val variable = MaterialTheme.colorScheme.tertiary
    val comment = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    return buildAnnotatedString {
        var cursor = 0
        tokens.forEach { token ->
            if (token.start > cursor) append(code.substring(cursor, token.start))
            val color: Color = when (token.kind) {
                CodeToken.Kind.KEYWORD -> keyword
                CodeToken.Kind.STRING -> string
                CodeToken.Kind.NUMBER -> number
                CodeToken.Kind.FUNCTION -> function
                CodeToken.Kind.VARIABLE -> variable
                CodeToken.Kind.COMMENT -> comment
            }
            withStyle(SpanStyle(color = color)) {
                append(code.substring(token.start, token.end))
            }
            cursor = token.end
        }
        if (cursor < code.length) append(code.substring(cursor))
    }
}

private const val MAX_HIGHLIGHT_CHARS = 24_000
