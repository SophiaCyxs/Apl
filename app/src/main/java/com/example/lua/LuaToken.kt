package com.example.lua

enum class TokenType {
  // Literals & Identifiers
  IDENT,
  NUMBER,
  STRING,

  // Lua 5.1 Keywords
  AND,
  BREAK,
  DO,
  ELSE,
  ELSEIF,
  END,
  FALSE,
  FOR,
  FUNCTION,
  IF,
  IN,
  LOCAL,
  NIL,
  NOT,
  OR,
  REPEAT,
  RETURN,
  THEN,
  TRUE,
  UNTIL,
  WHILE,

  // Operators & Punctuation
  PLUS,       // +
  MINUS,      // -
  STAR,       // *
  SLASH,      // /
  PERCENT,    // %
  CARET,      // ^
  HASH,       // #
  EQ,         // ==
  NEQ,        // ~=
  LE,         // <=
  GE,         // >=
  LT,         // <
  GT,         // >
  ASSIGN,     // =
  LPAREN,     // (
  RPAREN,     // )
  LBRACE,     // {
  RBRACE,     // }
  LBRACKET,   // [
  RBRACKET,   // ]
  SEMI,       // ;
  COLON,      // :
  COMMA,      // ,
  DOT,        // .
  CONCAT,     // ..
  VARARG,     // ...

  EOF,
}

data class LuaToken(
  val type: TokenType,
  val lexeme: String,
  val line: Int,
  val column: Int,
  val numberValue: Double? = null,
)

class LuaCompileException(
  override val message: String,
  val line: Int,
  val column: Int,
  val stage: String = "Compiler",
) : Exception("[$stage L$line:$column] $message")

class LuaRuntimeException(
  override val message: String,
  val line: Int = 0,
) : Exception(if (line > 0) "[Runtime L$line] $message" else "[Runtime] $message")
