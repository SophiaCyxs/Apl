package com.example.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.SyntaxColors

object LuaSyntaxHighlighter {
  private val KEYWORDS =
    setOf(
      "and",
      "break",
      "do",
      "else",
      "elseif",
      "end",
      "false",
      "for",
      "function",
      "if",
      "in",
      "local",
      "nil",
      "not",
      "or",
      "repeat",
      "return",
      "then",
      "true",
      "until",
      "while",
    )

  private val ROBLOX_TYPES =
    setOf(
      "Instance",
      "UDim",
      "UDim2",
      "Vector2",
      "Vector3",
      "CFrame",
      "Color3",
      "TweenInfo",
      "Enum",
      "game",
      "workspace",
      "script",
      "task",
      "bit32",
    )

  private val EXECUTOR_APIS =
    setOf(
      "getgenv",
      "getrenv",
      "getfenv",
      "setfenv",
      "gethui",
      "getrawmetatable",
      "setrawmetatable",
      "setreadonly",
      "isreadonly",
      "hookfunction",
      "replaceclosure",
      "hookmetamethod",
      "newcclosure",
      "islclosure",
      "iscclosure",
      "checkcaller",
      "identifyexecutor",
      "getexecutorname",
      "loadstring",
      "Drawing",
      "cleardrawcache",
      "crypt",
      "readfile",
      "writefile",
      "appendfile",
      "isfile",
      "isfolder",
      "makefolder",
      "listfiles",
      "delfile",
      "setclipboard",
      "cloneref",
      "compareinstances",
      "getinstances",
      "getnilinstances",
      "fireclickdetector",
      "request",
      "http_request",
    )

  private val BUILTINS =
    setOf(
      "print",
      "warn",
      "error",
      "assert",
      "pcall",
      "xpcall",
      "pairs",
      "ipairs",
      "next",
      "unpack",
      "select",
      "tonumber",
      "tostring",
      "type",
      "typeof",
      "setmetatable",
      "getmetatable",
      "rawget",
      "rawset",
      "rawequal",
      "math",
      "string",
      "table",
      "os",
      "wait",
      "spawn",
      "delay",
      "tick",
    )

  fun highlight(source: String, colors: SyntaxColors, enabled: Boolean): AnnotatedString {
    if (!enabled) return AnnotatedString(source)
    return buildAnnotatedString {
      append(source)
      var i = 0
      val n = source.length
      while (i < n) {
        val c = source[i]
        when {
          // Comments
          c == '-' && i + 1 < n && source[i + 1] == '-' -> {
            val start = i
            if (i + 3 < n && source[i + 2] == '[' && source[i + 3] == '[') {
              val endIdx = source.indexOf("]]", i + 4)
              i = if (endIdx >= 0) endIdx + 2 else n
            } else {
              while (i < n && source[i] != '\n') i++
            }
            addStyle(SpanStyle(color = colors.comment), start, i)
          }
          // Short Strings
          c == '"' || c == '\'' -> {
            val quote = c
            val start = i++
            while (i < n) {
              val cur = source[i++]
              if (cur == '\\' && i < n) {
                i++
              } else if (cur == quote || cur == '\n') {
                break
              }
            }
            addStyle(SpanStyle(color = colors.stringLiteral), start, i)
          }
          // Long Strings [[...]]
          c == '[' && i + 1 < n && source[i + 1] == '[' -> {
            val start = i
            val endIdx = source.indexOf("]]", i + 2)
            i = if (endIdx >= 0) endIdx + 2 else n
            addStyle(SpanStyle(color = colors.stringLiteral), start, i)
          }
          // Numbers
          c.isDigit() -> {
            val start = i
            while (i < n && (source[i].isLetterOrDigit() || source[i] == '.')) i++
            addStyle(SpanStyle(color = colors.numberLiteral), start, i)
          }
          // Identifiers & Keywords
          c.isLetter() || c == '_' -> {
            val start = i
            while (i < n && (source[i].isLetterOrDigit() || source[i] == '_')) i++
            val word = source.substring(start, i)
            val style =
              when (word) {
                in KEYWORDS -> SpanStyle(color = colors.keyword, fontWeight = FontWeight.SemiBold)
                in ROBLOX_TYPES -> SpanStyle(color = colors.robloxType, fontWeight = FontWeight.Medium)
                in EXECUTOR_APIS -> SpanStyle(color = colors.executorApi, fontWeight = FontWeight.Medium)
                in BUILTINS -> SpanStyle(color = colors.builtin)
                else -> null
              }
            if (style != null) addStyle(style, start, i)
          }
          // Operators
          c in "+-*/%^#=<>~:" -> {
            val start = i++
            addStyle(SpanStyle(color = colors.operator), start, i)
          }
          else -> i++
        }
      }
    }
  }
}
