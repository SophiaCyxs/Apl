package com.example.lua

class LuaLexer(private val source: String) {
  private var pos = 0
  private var line = 1
  private var col = 1

  companion object {
    private val KEYWORDS =
      mapOf(
        "and" to TokenType.AND,
        "break" to TokenType.BREAK,
        "do" to TokenType.DO,
        "else" to TokenType.ELSE,
        "elseif" to TokenType.ELSEIF,
        "end" to TokenType.END,
        "false" to TokenType.FALSE,
        "for" to TokenType.FOR,
        "function" to TokenType.FUNCTION,
        "if" to TokenType.IF,
        "in" to TokenType.IN,
        "local" to TokenType.LOCAL,
        "nil" to TokenType.NIL,
        "not" to TokenType.NOT,
        "or" to TokenType.OR,
        "repeat" to TokenType.REPEAT,
        "return" to TokenType.RETURN,
        "then" to TokenType.THEN,
        "true" to TokenType.TRUE,
        "until" to TokenType.UNTIL,
        "while" to TokenType.WHILE,
      )
  }

  private fun isAtEnd(): Boolean = pos >= source.length

  private fun peek(offset: Int = 0): Char =
    if (pos + offset < source.length) source[pos + offset] else '\u0000'

  private fun advance(): Char {
    val ch = source[pos++]
    if (ch == '\n') {
      line++
      col = 1
    } else {
      col++
    }
    return ch
  }

  private fun match(expected: Char): Boolean {
    if (isAtEnd() || source[pos] != expected) return false
    advance()
    return true
  }

  fun tokenize(): List<LuaToken> {
    val tokens = mutableListOf<LuaToken>()

    // Skip optional Unix shebang on first line
    if (pos == 0 && peek() == '#' && peek(1) == '!') {
      while (!isAtEnd() && peek() != '\n') advance()
    }

    while (!isAtEnd()) {
      skipWhitespaceAndComments()
      if (isAtEnd()) break

      val startLine = line
      val startCol = col
      val ch = peek()

      when {
        ch.isLetter() || ch == '_' -> tokens.add(readIdentifierOrKeyword(startLine, startCol))
        ch.isDigit() || (ch == '.' && peek(1).isDigit()) ->
          tokens.add(readNumber(startLine, startCol))
        ch == '"' || ch == '\'' -> tokens.add(readShortString(startLine, startCol))
        ch == '[' && (peek(1) == '[' || peek(1) == '=') -> {
          val longLevel = countLongBracketLevel()
          if (longLevel >= 0) {
            tokens.add(readLongString(longLevel, startLine, startCol))
          } else {
            advance()
            tokens.add(LuaToken(TokenType.LBRACKET, "[", startLine, startCol))
          }
        }
        else -> tokens.add(readOperatorOrPunct(startLine, startCol))
      }
    }

    tokens.add(LuaToken(TokenType.EOF, "", line, col))
    return tokens
  }

  private fun skipWhitespaceAndComments() {
    while (!isAtEnd()) {
      val ch = peek()
      when {
        ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n' || ch == '\u000C' -> advance()
        ch == '-' && peek(1) == '-' -> {
          advance() // -
          advance() // -
          if (peek() == '[') {
            val level = countLongBracketLevel()
            if (level >= 0) {
              skipLongBracketBody(level)
              continue
            }
          }
          while (!isAtEnd() && peek() != '\n') advance()
        }
        else -> return
      }
    }
  }

  private fun countLongBracketLevel(): Int {
    if (peek() != '[') return -1
    var offset = 1
    var eqCount = 0
    while (peek(offset) == '=') {
      eqCount++
      offset++
    }
    return if (peek(offset) == '[') eqCount else -1
  }

  private fun skipLongBracketBody(level: Int) {
    advance() // [
    repeat(level) { advance() } // =
    advance() // [
    if (peek() == '\n') advance()
    while (!isAtEnd()) {
      if (peek() == ']') {
        var offset = 1
        var matched = 0
        while (matched < level && peek(offset) == '=') {
          matched++
          offset++
        }
        if (matched == level && peek(offset) == ']') {
          repeat(offset + 1) { advance() }
          return
        }
      }
      advance()
    }
    throw LuaCompileException("Unterminated long comment '--[['", line, col, "Lexer")
  }

  private fun readLongString(level: Int, startLine: Int, startCol: Int): LuaToken {
    advance() // [
    repeat(level) { advance() }
    advance() // [
    if (peek() == '\r' && peek(1) == '\n') {
      advance()
      advance()
    } else if (peek() == '\n') {
      advance()
    }
    val sb = StringBuilder()
    while (!isAtEnd()) {
      if (peek() == ']') {
        var offset = 1
        var matched = 0
        while (matched < level && peek(offset) == '=') {
          matched++
          offset++
        }
        if (matched == level && peek(offset) == ']') {
          repeat(offset + 1) { advance() }
          return LuaToken(TokenType.STRING, sb.toString(), startLine, startCol)
        }
      }
      sb.append(advance())
    }
    throw LuaCompileException("Unterminated long string '[['", startLine, startCol, "Lexer")
  }

  private fun readShortString(startLine: Int, startCol: Int): LuaToken {
    val quote = advance()
    val sb = StringBuilder()
    while (!isAtEnd()) {
      val c = advance()
      if (c == quote) {
        return LuaToken(TokenType.STRING, sb.toString(), startLine, startCol)
      }
      if (c == '\n' || c == '\r') {
        throw LuaCompileException("Unfinished string literal", startLine, startCol, "Lexer")
      }
      if (c == '\\') {
        if (isAtEnd()) throw LuaCompileException("Unfinished escape sequence", line, col, "Lexer")
        when (val esc = advance()) {
          'a' -> sb.append('\u0007')
          'b' -> sb.append('\b')
          'f' -> sb.append('\u000C')
          'n' -> sb.append('\n')
          'r' -> sb.append('\r')
          't' -> sb.append('\t')
          'v' -> sb.append('\u000B')
          '\\' -> sb.append('\\')
          '"' -> sb.append('"')
          '\'' -> sb.append('\'')
          '\n' -> sb.append('\n')
          '\r' -> {
            if (peek() == '\n') advance()
            sb.append('\n')
          }
          'x' -> {
            val h1 = peek()
            val h2 = peek(1)
            if (!isHexDigit(h1) || !isHexDigit(h2)) {
              throw LuaCompileException("Invalid hexadecimal escape '\\x'", line, col, "Lexer")
            }
            advance()
            advance()
            sb.append("$h1$h2".toInt(16).toChar())
          }
          in '0'..'9' -> {
            val digits = StringBuilder().append(esc)
            repeat(2) {
              if (peek() in '0'..'9') digits.append(advance())
            }
            val code = digits.toString().toInt()
            if (code > 255) {
              throw LuaCompileException("Decimal escape too large '\\$digits'", line, col, "Lexer")
            }
            sb.append(code.toChar())
          }
          else -> throw LuaCompileException("Invalid escape sequence '\\$esc'", line, col, "Lexer")
        }
      } else {
        sb.append(c)
      }
    }
    throw LuaCompileException("Unfinished string literal", startLine, startCol, "Lexer")
  }

  private fun readNumber(startLine: Int, startCol: Int): LuaToken {
    val sb = StringBuilder()
    if (peek() == '0' && (peek(1) == 'x' || peek(1) == 'X')) {
      sb.append(advance()) // 0
      sb.append(advance()) // x
      if (!isHexDigit(peek())) {
        throw LuaCompileException("Malformed hexadecimal number", startLine, startCol, "Lexer")
      }
      while (isHexDigit(peek()) || peek() == '_') {
        val c = advance()
        if (c != '_') sb.append(c)
      }
      val hexDigits = sb.substring(2)
      val value =
        hexDigits.toLongOrNull(16)?.toDouble()
          ?: throw LuaCompileException("Hex literal overflow: $sb", startLine, startCol, "Lexer")
      return LuaToken(TokenType.NUMBER, sb.toString(), startLine, startCol, value)
    }

    while (peek().isDigit()) sb.append(advance())
    if (peek() == '.' && peek(1) != '.') {
      sb.append(advance())
      while (peek().isDigit()) sb.append(advance())
    }
    if (peek() == 'e' || peek() == 'E') {
      sb.append(advance())
      if (peek() == '+' || peek() == '-') sb.append(advance())
      if (!peek().isDigit()) {
        throw LuaCompileException("Malformed exponent in number literal", startLine, startCol, "Lexer")
      }
      while (peek().isDigit()) sb.append(advance())
    }
    val raw = sb.toString()
    val num =
      raw.toDoubleOrNull()
        ?: throw LuaCompileException("Malformed number literal '$raw'", startLine, startCol, "Lexer")
    return LuaToken(TokenType.NUMBER, raw, startLine, startCol, num)
  }

  private fun readIdentifierOrKeyword(startLine: Int, startCol: Int): LuaToken {
    val sb = StringBuilder()
    while (!isAtEnd() && (peek().isLetterOrDigit() || peek() == '_')) {
      sb.append(advance())
    }
    val word = sb.toString()
    if (word == "goto") {
      throw LuaCompileException(
        "Lua 5.2+ 'goto' is not supported. BloxLua strictly targets Roblox Lua 5.1 & Executor Lua.",
        startLine,
        startCol,
        "DialectGate",
      )
    }
    val type = KEYWORDS[word] ?: TokenType.IDENT
    return LuaToken(type, word, startLine, startCol)
  }

  private fun readOperatorOrPunct(startLine: Int, startCol: Int): LuaToken {
    val c = advance()
    return when (c) {
      '+' -> {
        if (match('=')) {
          throw LuaCompileException(
            "Compound assignment '+=' is not valid in Lua 5.1. Use 'x = x + y'.",
            startLine,
            startCol,
            "DialectGate",
          )
        }
        LuaToken(TokenType.PLUS, "+", startLine, startCol)
      }
      '-' -> {
        if (match('=')) {
          throw LuaCompileException(
            "Compound assignment '-=' is not valid in Lua 5.1. Use 'x = x - y'.",
            startLine,
            startCol,
            "DialectGate",
          )
        }
        LuaToken(TokenType.MINUS, "-", startLine, startCol)
      }
      '*' -> {
        if (match('=')) {
          throw LuaCompileException(
            "Compound assignment '*=' is not valid in Lua 5.1. Use 'x = x * y'.",
            startLine,
            startCol,
            "DialectGate",
          )
        }
        LuaToken(TokenType.STAR, "*", startLine, startCol)
      }
      '/' -> {
        if (match('/')) {
          throw LuaCompileException(
            "Lua 5.3+ floor division '//' is not supported in Roblox Lua 5.1. Use 'math.floor(a / b)'.",
            startLine,
            startCol,
            "DialectGate",
          )
        }
        if (match('=')) {
          throw LuaCompileException(
            "Compound assignment '/=' is not valid in Lua 5.1. Use 'x = x / y'.",
            startLine,
            startCol,
            "DialectGate",
          )
        }
        LuaToken(TokenType.SLASH, "/", startLine, startCol)
      }
      '%' -> LuaToken(TokenType.PERCENT, "%", startLine, startCol)
      '^' -> LuaToken(TokenType.CARET, "^", startLine, startCol)
      '#' -> LuaToken(TokenType.HASH, "#", startLine, startCol)
      ';' -> LuaToken(TokenType.SEMI, ";", startLine, startCol)
      ',' -> LuaToken(TokenType.COMMA, ",", startLine, startCol)
      '(' -> LuaToken(TokenType.LPAREN, "(", startLine, startCol)
      ')' -> LuaToken(TokenType.RPAREN, ")", startLine, startCol)
      '{' -> LuaToken(TokenType.LBRACE, "{", startLine, startCol)
      '}' -> LuaToken(TokenType.RBRACE, "}", startLine, startCol)
      '[' -> LuaToken(TokenType.LBRACKET, "[", startLine, startCol)
      ']' -> LuaToken(TokenType.RBRACKET, "]", startLine, startCol)
      ':' -> {
        if (match(':')) {
          throw LuaCompileException(
            "Lua 5.2+ label syntax '::label::' is not supported in Roblox Lua 5.1.",
            startLine,
            startCol,
            "DialectGate",
          )
        }
        LuaToken(TokenType.COLON, ":", startLine, startCol)
      }
      '.' -> {
        if (match('.')) {
          if (match('.')) {
            LuaToken(TokenType.VARARG, "...", startLine, startCol)
          } else {
            LuaToken(TokenType.CONCAT, "..", startLine, startCol)
          }
        } else {
          LuaToken(TokenType.DOT, ".", startLine, startCol)
        }
      }
      '=' -> {
        if (match('=')) {
          if (match('=')) {
            throw LuaCompileException(
              "Operator '===' is not valid in Lua 5.1. Use '=='.",
              startLine,
              startCol,
              "DialectGate",
            )
          }
          LuaToken(TokenType.EQ, "==", startLine, startCol)
        } else {
          LuaToken(TokenType.ASSIGN, "=", startLine, startCol)
        }
      }
      '~' -> {
        if (match('=')) {
          LuaToken(TokenType.NEQ, "~=", startLine, startCol)
        } else {
          throw LuaCompileException(
            "Lua 5.3+ bitwise '~' is not supported in Roblox Lua 5.1. Use 'bit32.bxor' / 'bit32.bnot' or '~=' for inequality.",
            startLine,
            startCol,
            "DialectGate",
          )
        }
      }
      '<' -> {
        if (match('=')) {
          LuaToken(TokenType.LE, "<=", startLine, startCol)
        } else if (match('<')) {
          throw LuaCompileException(
            "Lua 5.3+ shift '<<' is not supported in Roblox Lua 5.1. Use 'bit32.lshift(a, b)'.",
            startLine,
            startCol,
            "DialectGate",
          )
        } else {
          LuaToken(TokenType.LT, "<", startLine, startCol)
        }
      }
      '>' -> {
        if (match('=')) {
          LuaToken(TokenType.GE, ">=", startLine, startCol)
        } else if (match('>')) {
          throw LuaCompileException(
            "Lua 5.3+ shift '>>' is not supported in Roblox Lua 5.1. Use 'bit32.rshift(a, b)'.",
            startLine,
            startCol,
            "DialectGate",
          )
        } else {
          LuaToken(TokenType.GT, ">", startLine, startCol)
        }
      }
      '&' -> throw LuaCompileException(
        "Operator '&' / '&&' is not valid in Roblox Lua 5.1. Use 'and' or 'bit32.band(a, b)'.",
        startLine,
        startCol,
        "DialectGate",
      )
      '|' -> throw LuaCompileException(
        "Operator '|' / '||' is not valid in Roblox Lua 5.1. Use 'or' or 'bit32.bor(a, b)'.",
        startLine,
        startCol,
        "DialectGate",
      )
      '!' -> throw LuaCompileException(
        "Operator '!' / '!=' is not valid in Roblox Lua 5.1. Use 'not' or '~='.",
        startLine,
        startCol,
        "DialectGate",
      )
      else -> throw LuaCompileException(
        "Unexpected character '$c' in Roblox Lua 5.1 source.",
        startLine,
        startCol,
        "Lexer",
      )
    }
  }

  private fun isHexDigit(c: Char): Boolean =
    c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
}
