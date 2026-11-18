package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.SyntaxColors
import com.example.ui.theme.buildMovingGradientBrush

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
      "ColorSequence",
      "ColorSequenceKeypoint",
      "NumberRange",
      "NumberSequence",
      "Rect",
      "TweenInfo",
      "Enum",
      "game",
      "workspace",
      "script",
      "task",
      "bit32",
      "coroutine",
      "debug",
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
      "syn",
      "protect_gui",
      "HttpGet",
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
      "require",
      "math",
      "string",
      "table",
      "os",
      "wait",
      "spawn",
      "delay",
      "tick",
    )

  enum class TokenKind {
    COMMENT,
    STRING,
    NUMBER,
    KEYWORD,
    ROBLOX_TYPE,
    EXECUTOR_API,
    BUILTIN,
    OPERATOR,
  }

  data class HighlightSpan(
    val start: Int,
    val end: Int,
    val kind: TokenKind,
  )

  fun tokenizeSpans(source: String): List<HighlightSpan> {
    if (source.isEmpty()) return emptyList()
    val spans = ArrayList<HighlightSpan>(source.length / 6 + 8)
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
          spans.add(HighlightSpan(start, i, TokenKind.COMMENT))
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
          spans.add(HighlightSpan(start, i, TokenKind.STRING))
        }
        // Long Strings [[...]]
        c == '[' && i + 1 < n && source[i + 1] == '[' -> {
          val start = i
          val endIdx = source.indexOf("]]", i + 2)
          i = if (endIdx >= 0) endIdx + 2 else n
          spans.add(HighlightSpan(start, i, TokenKind.STRING))
        }
        // Numbers
        c.isDigit() -> {
          val start = i
          while (i < n && (source[i].isLetterOrDigit() || source[i] == '.')) i++
          spans.add(HighlightSpan(start, i, TokenKind.NUMBER))
        }
        // Identifiers & Keywords
        c.isLetter() || c == '_' -> {
          val start = i
          while (i < n && (source[i].isLetterOrDigit() || source[i] == '_')) i++
          val word = source.substring(start, i)
          val kind =
            when (word) {
              in KEYWORDS -> TokenKind.KEYWORD
              in ROBLOX_TYPES -> TokenKind.ROBLOX_TYPE
              in EXECUTOR_APIS -> TokenKind.EXECUTOR_API
              in BUILTINS -> TokenKind.BUILTIN
              else -> null
            }
          if (kind != null) spans.add(HighlightSpan(start, i, kind))
        }
        // Operators
        c in "+-*/%^#=<>~:" -> {
          val start = i++
          spans.add(HighlightSpan(start, i, TokenKind.OPERATOR))
        }
        else -> i++
      }
    }
    return spans
  }

  fun renderSpans(
    source: String,
    spans: List<HighlightSpan>,
    colors: SyntaxColors,
    enabled: Boolean,
    gradientEnabled: Boolean = false,
    gradientColors: List<Color> = emptyList(),
    gradientPhase: Float = 0f,
  ): AnnotatedString {
    if (!enabled) return AnnotatedString(source)
    val useGrad = gradientEnabled && gradientColors.size >= 2
    val keywordStyle =
      if (useGrad) SpanStyle(brush = buildMovingGradientBrush(gradientColors, gradientPhase, 720f), fontWeight = FontWeight.Bold)
      else SpanStyle(color = colors.keyword, fontWeight = FontWeight.SemiBold)
    val typeStyle =
      if (useGrad) SpanStyle(brush = buildMovingGradientBrush(gradientColors.reversed(), gradientPhase + 0.18f, 640f), fontWeight = FontWeight.SemiBold)
      else SpanStyle(color = colors.robloxType, fontWeight = FontWeight.Medium)
    val execStyle =
      if (useGrad) SpanStyle(brush = buildMovingGradientBrush(gradientColors, gradientPhase + 0.36f, 520f), fontWeight = FontWeight.Medium)
      else SpanStyle(color = colors.executorApi, fontWeight = FontWeight.Medium)
    val builtinStyle =
      if (useGrad) SpanStyle(brush = buildMovingGradientBrush(gradientColors.reversed(), gradientPhase + 0.18f, 640f), alpha = 0.85f)
      else SpanStyle(color = colors.builtin)
    val stringStyle =
      if (useGrad) SpanStyle(brush = buildMovingGradientBrush(gradientColors, gradientPhase + 0.55f, 800f), alpha = 0.9f)
      else SpanStyle(color = colors.stringLiteral)
    val commentStyle = SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)
    val numberStyle = SpanStyle(color = colors.numberLiteral)
    val operatorStyle = SpanStyle(color = colors.operator)

    return buildAnnotatedString {
      append(source)
      for (span in spans) {
        val st =
          when (span.kind) {
            TokenKind.COMMENT -> commentStyle
            TokenKind.STRING -> stringStyle
            TokenKind.NUMBER -> numberStyle
            TokenKind.KEYWORD -> keywordStyle
            TokenKind.ROBLOX_TYPE -> typeStyle
            TokenKind.EXECUTOR_API -> execStyle
            TokenKind.BUILTIN -> builtinStyle
            TokenKind.OPERATOR -> operatorStyle
          }
        addStyle(st, span.start, span.end)
      }
    }
  }

  fun highlight(
    source: String,
    colors: SyntaxColors,
    enabled: Boolean,
    gradientEnabled: Boolean = false,
    gradientColors: List<Color> = emptyList(),
    gradientPhase: Float = 0f,
  ): AnnotatedString {
    if (!enabled) return AnnotatedString(source)
    return renderSpans(
      source = source,
      spans = tokenizeSpans(source),
      colors = colors,
      enabled = true,
      gradientEnabled = gradientEnabled,
      gradientColors = gradientColors,
      gradientPhase = gradientPhase,
    )
  }
}
