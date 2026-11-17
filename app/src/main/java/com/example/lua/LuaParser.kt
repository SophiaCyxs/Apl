package com.example.lua

class LuaParser(private val tokens: List<LuaToken>) {
  private var pos = 0

  private fun peek(offset: Int = 0): LuaToken =
    tokens.getOrElse(pos + offset) { tokens.last() }

  private fun check(type: TokenType): Boolean = peek().type == type

  private fun match(type: TokenType): Boolean {
    if (!check(type)) return false
    pos++
    return true
  }

  private fun expect(type: TokenType, message: String): LuaToken {
    val tok = peek()
    if (tok.type != type) {
      throw LuaCompileException(
        "$message (found '${tok.lexeme.ifEmpty { tok.type.name }}')",
        tok.line,
        tok.column,
        "Parser",
      )
    }
    pos++
    return tok
  }

  fun parseChunk(): Block {
    val block = parseBlock()
    if (!check(TokenType.EOF)) {
      val tok = peek()
      throw LuaCompileException(
        "Unexpected token '${tok.lexeme}'",
        tok.line,
        tok.column,
        "Parser",
      )
    }
    return block
  }

  private fun isBlockTerminator(type: TokenType): Boolean =
    type == TokenType.END ||
      type == TokenType.ELSE ||
      type == TokenType.ELSEIF ||
      type == TokenType.UNTIL ||
      type == TokenType.EOF

  private fun parseBlock(): Block {
    val stmts = mutableListOf<Stmt>()
    while (!isBlockTerminator(peek().type)) {
      if (match(TokenType.SEMI)) continue
      val stmt = parseStatement()
      stmts.add(stmt)
      while (match(TokenType.SEMI)) {
        // optional semicolons
      }
      if (stmt is Stmt.Return || stmt is Stmt.Break) {
        while (match(TokenType.SEMI)) {}
        break
      }
    }
    return Block(stmts)
  }

  private fun parseStatement(): Stmt {
    val tok = peek()
    return when (tok.type) {
      TokenType.DO -> {
        pos++
        val block = parseBlock()
        expect(TokenType.END, "Expected 'end' to close 'do' block")
        Stmt.DoBlock(block, tok.line)
      }
      TokenType.WHILE -> {
        pos++
        val cond = parseExpr()
        expect(TokenType.DO, "Expected 'do' after 'while' condition")
        val body = parseBlock()
        expect(TokenType.END, "Expected 'end' to close 'while' loop")
        Stmt.While(cond, body, tok.line)
      }
      TokenType.REPEAT -> {
        pos++
        val body = parseBlock()
        expect(TokenType.UNTIL, "Expected 'until' to close 'repeat' loop")
        val cond = parseExpr()
        Stmt.Repeat(body, cond, tok.line)
      }
      TokenType.IF -> parseIf(tok.line)
      TokenType.FOR -> parseFor(tok.line)
      TokenType.FUNCTION -> parseFunctionDecl(isLocal = false, line = tok.line)
      TokenType.LOCAL -> parseLocal(tok.line)
      TokenType.RETURN -> parseReturn(tok.line)
      TokenType.BREAK -> {
        pos++
        Stmt.Break(tok.line)
      }
      else -> parseExprOrAssign()
    }
  }

  private fun parseIf(line: Int): Stmt.If {
    expect(TokenType.IF, "Expected 'if'")
    val branches = mutableListOf<Pair<Expr, Block>>()
    val firstCond = parseExpr()
    expect(TokenType.THEN, "Expected 'then' after 'if' condition")
    branches.add(firstCond to parseBlock())

    while (match(TokenType.ELSEIF)) {
      val cond = parseExpr()
      expect(TokenType.THEN, "Expected 'then' after 'elseif' condition")
      branches.add(cond to parseBlock())
    }

    val elseBlock =
      if (match(TokenType.ELSE)) {
        parseBlock()
      } else {
        null
      }
    expect(TokenType.END, "Expected 'end' to close 'if' statement")
    return Stmt.If(branches, elseBlock, line)
  }

  private fun parseFor(line: Int): Stmt {
    expect(TokenType.FOR, "Expected 'for'")
    val firstName = expect(TokenType.IDENT, "Expected loop variable name")
    if (match(TokenType.ASSIGN)) {
      val start = parseExpr()
      expect(TokenType.COMMA, "Expected ',' in numeric 'for' loop")
      val limit = parseExpr()
      val step = if (match(TokenType.COMMA)) parseExpr() else null
      expect(TokenType.DO, "Expected 'do' in numeric 'for' loop")
      val body = parseBlock()
      expect(TokenType.END, "Expected 'end' to close 'for' loop")
      return Stmt.NumericFor(firstName.lexeme, start, limit, step, body, line)
    }

    val names = mutableListOf(firstName.lexeme)
    while (match(TokenType.COMMA)) {
      names.add(expect(TokenType.IDENT, "Expected variable name in generic 'for'").lexeme)
    }
    expect(TokenType.IN, "Expected 'in' or '=' in 'for' loop")
    val iters = parseExprList()
    expect(TokenType.DO, "Expected 'do' in generic 'for' loop")
    val body = parseBlock()
    expect(TokenType.END, "Expected 'end' to close 'for' loop")
    return Stmt.GenericFor(names, iters, body, line)
  }

  private fun parseFunctionDecl(isLocal: Boolean, line: Int): Stmt.FunctionDecl {
    expect(TokenType.FUNCTION, "Expected 'function'")
    val nameParts = mutableListOf<String>()
    var methodName: String? = null
    nameParts.add(expect(TokenType.IDENT, "Expected function name").lexeme)
    if (!isLocal) {
      while (match(TokenType.DOT)) {
        nameParts.add(expect(TokenType.IDENT, "Expected field name after '.'").lexeme)
      }
      if (match(TokenType.COLON)) {
        methodName = expect(TokenType.IDENT, "Expected method name after ':'").lexeme
      }
    }
    val (params, isVararg) = parseParamList()
    val body = parseBlock()
    expect(TokenType.END, "Expected 'end' to close function body")
    return Stmt.FunctionDecl(nameParts, methodName, params, isVararg, body, isLocal, line)
  }

  private fun parseLocal(line: Int): Stmt {
    expect(TokenType.LOCAL, "Expected 'local'")
    if (check(TokenType.FUNCTION)) {
      return parseFunctionDecl(isLocal = true, line = line)
    }
    val names = mutableListOf<String>()
    names.add(expect(TokenType.IDENT, "Expected local variable name").lexeme)
    while (match(TokenType.COMMA)) {
      names.add(expect(TokenType.IDENT, "Expected local variable name").lexeme)
    }
    val values = if (match(TokenType.ASSIGN)) parseExprList() else emptyList()
    return Stmt.LocalAssign(names, values, line)
  }

  private fun parseReturn(line: Int): Stmt.Return {
    expect(TokenType.RETURN, "Expected 'return'")
    val values =
      if (isBlockTerminator(peek().type) || check(TokenType.SEMI)) {
        emptyList()
      } else {
        parseExprList()
      }
    return Stmt.Return(values, line)
  }

  private fun parseExprOrAssign(): Stmt {
    val first = parseSuffixedExpr()
    if (check(TokenType.COMMA) || check(TokenType.ASSIGN)) {
      val targets = mutableListOf(toLValue(first))
      while (match(TokenType.COMMA)) {
        targets.add(toLValue(parseSuffixedExpr()))
      }
      expect(TokenType.ASSIGN, "Expected '=' in assignment")
      val values = parseExprList()
      return Stmt.Assign(targets, values, first.line)
    }
    if (first is Expr.Call || first is Expr.MethodCall) {
      return Stmt.ExprStmt(first, first.line)
    }
    val tok = peek()
    throw LuaCompileException(
      "Expected assignment or function call statement",
      first.line,
      tok.column,
      "Parser",
    )
  }

  private fun toLValue(expr: Expr): LValue =
    when (expr) {
      is Expr.Name -> LValue.Name(expr.name, expr.line)
      is Expr.Index -> LValue.Index(expr.target, expr.key, expr.line)
      else -> throw LuaCompileException("Invalid assignment target", expr.line, 1, "Parser")
    }

  private fun parseParamList(): Pair<List<String>, Boolean> {
    expect(TokenType.LPAREN, "Expected '(' for parameter list")
    val params = mutableListOf<String>()
    var isVararg = false
    if (!check(TokenType.RPAREN)) {
      do {
        if (match(TokenType.VARARG)) {
          isVararg = true
          break
        }
        params.add(expect(TokenType.IDENT, "Expected parameter name").lexeme)
      } while (match(TokenType.COMMA))
    }
    expect(TokenType.RPAREN, "Expected ')' after parameter list")
    return params to isVararg
  }

  private fun parseExprList(): List<Expr> {
    val list = mutableListOf(parseExpr())
    while (match(TokenType.COMMA)) {
      list.add(parseExpr())
    }
    return list
  }

  private fun parseExpr(): Expr = parseSubExpr(0)

  private fun binaryPrecedence(type: TokenType): Pair<Int, Int>? =
    when (type) {
      TokenType.OR -> 1 to 1
      TokenType.AND -> 2 to 2
      TokenType.LT,
      TokenType.LE,
      TokenType.GT,
      TokenType.GE,
      TokenType.EQ,
      TokenType.NEQ -> 3 to 3
      TokenType.CONCAT -> 5 to 4 // right associative
      TokenType.PLUS,
      TokenType.MINUS -> 6 to 6
      TokenType.STAR,
      TokenType.SLASH,
      TokenType.PERCENT -> 7 to 7
      TokenType.CARET -> 10 to 9 // right associative
      else -> null
    }

  private fun toBinOp(type: TokenType): BinOp =
    when (type) {
      TokenType.OR -> BinOp.OR
      TokenType.AND -> BinOp.AND
      TokenType.LT -> BinOp.LT
      TokenType.LE -> BinOp.LE
      TokenType.GT -> BinOp.GT
      TokenType.GE -> BinOp.GE
      TokenType.EQ -> BinOp.EQ
      TokenType.NEQ -> BinOp.NEQ
      TokenType.CONCAT -> BinOp.CONCAT
      TokenType.PLUS -> BinOp.ADD
      TokenType.MINUS -> BinOp.SUB
      TokenType.STAR -> BinOp.MUL
      TokenType.SLASH -> BinOp.DIV
      TokenType.PERCENT -> BinOp.MOD
      TokenType.CARET -> BinOp.POW
      else -> error("Not a binary op: $type")
    }

  private fun parseSubExpr(minPrec: Int): Expr {
    val tok = peek()
    var left =
      when (tok.type) {
        TokenType.MINUS -> {
          pos++
          Expr.Unary(UnOp.NEG, parseSubExpr(8), tok.line)
        }
        TokenType.NOT -> {
          pos++
          Expr.Unary(UnOp.NOT, parseSubExpr(8), tok.line)
        }
        TokenType.HASH -> {
          pos++
          Expr.Unary(UnOp.LEN, parseSubExpr(8), tok.line)
        }
        else -> parseSimpleExpr()
      }

    while (true) {
      val opTok = peek()
      val prec = binaryPrecedence(opTok.type) ?: break
      if (prec.first < minPrec) break
      pos++
      val right = parseSubExpr(prec.second)
      left = Expr.Binary(toBinOp(opTok.type), left, right, opTok.line)
    }
    return left
  }

  private fun parseSimpleExpr(): Expr {
    val tok = peek()
    return when (tok.type) {
      TokenType.NIL -> {
        pos++
        Expr.NilLit(tok.line)
      }
      TokenType.TRUE -> {
        pos++
        Expr.BoolLit(true, tok.line)
      }
      TokenType.FALSE -> {
        pos++
        Expr.BoolLit(false, tok.line)
      }
      TokenType.NUMBER -> {
        pos++
        Expr.NumberLit(tok.numberValue ?: 0.0, tok.line)
      }
      TokenType.STRING -> {
        pos++
        Expr.StringLit(tok.lexeme, tok.line)
      }
      TokenType.VARARG -> {
        pos++
        Expr.Vararg(tok.line)
      }
      TokenType.LBRACE -> parseTableConstructor()
      TokenType.FUNCTION -> {
        pos++
        val (params, isVararg) = parseParamList()
        val body = parseBlock()
        expect(TokenType.END, "Expected 'end' after anonymous function")
        Expr.FunctionExpr(params, isVararg, body, tok.line)
      }
      else -> parseSuffixedExpr()
    }
  }

  private fun parsePrimaryExpr(): Expr {
    val tok = peek()
    return when (tok.type) {
      TokenType.LPAREN -> {
        pos++
        val inner = parseExpr()
        expect(TokenType.RPAREN, "Expected ')'")
        inner
      }
      TokenType.IDENT -> {
        pos++
        Expr.Name(tok.lexeme, tok.line)
      }
      else ->
        throw LuaCompileException(
          "Unexpected token '${tok.lexeme.ifEmpty { tok.type.name }}' in expression",
          tok.line,
          tok.column,
          "Parser",
        )
    }
  }

  private fun parseSuffixedExpr(): Expr {
    var expr = parsePrimaryExpr()
    while (true) {
      val tok = peek()
      expr =
        when (tok.type) {
          TokenType.DOT -> {
            pos++
            val key = expect(TokenType.IDENT, "Expected field name after '.'")
            Expr.Index(expr, Expr.StringLit(key.lexeme, key.line), tok.line)
          }
          TokenType.LBRACKET -> {
            pos++
            val key = parseExpr()
            expect(TokenType.RBRACKET, "Expected ']'")
            Expr.Index(expr, key, tok.line)
          }
          TokenType.COLON -> {
            pos++
            val method = expect(TokenType.IDENT, "Expected method name after ':'")
            val args = parseCallArgs()
            Expr.MethodCall(expr, method.lexeme, args, tok.line)
          }
          TokenType.LPAREN,
          TokenType.LBRACE,
          TokenType.STRING -> {
            val args = parseCallArgs()
            Expr.Call(expr, args, tok.line)
          }
          else -> return expr
        }
    }
  }

  private fun parseCallArgs(): List<Expr> {
    val tok = peek()
    return when (tok.type) {
      TokenType.LPAREN -> {
        pos++
        if (match(TokenType.RPAREN)) {
          emptyList()
        } else {
          val args = parseExprList()
          expect(TokenType.RPAREN, "Expected ')' after arguments")
          args
        }
      }
      TokenType.LBRACE -> listOf(parseTableConstructor())
      TokenType.STRING -> {
        pos++
        listOf(Expr.StringLit(tok.lexeme, tok.line))
      }
      else ->
        throw LuaCompileException(
          "Expected function arguments",
          tok.line,
          tok.column,
          "Parser",
        )
    }
  }

  private fun parseTableConstructor(): Expr.TableConstructor {
    val start = expect(TokenType.LBRACE, "Expected '{'")
    val fields = mutableListOf<TableField>()
    while (!check(TokenType.RBRACE)) {
      when {
        check(TokenType.LBRACKET) -> {
          pos++
          val key = parseExpr()
          expect(TokenType.RBRACKET, "Expected ']'")
          expect(TokenType.ASSIGN, "Expected '=' after table key")
          val value = parseExpr()
          fields.add(TableField.Keyed(key, value))
        }
        check(TokenType.IDENT) && peek(1).type == TokenType.ASSIGN -> {
          val key = expect(TokenType.IDENT, "Expected field name")
          expect(TokenType.ASSIGN, "Expected '='")
          val value = parseExpr()
          fields.add(TableField.Named(key.lexeme, value))
        }
        else -> {
          fields.add(TableField.Positional(parseExpr()))
        }
      }
      if (!match(TokenType.COMMA) && !match(TokenType.SEMI)) {
        break
      }
    }
    expect(TokenType.RBRACE, "Expected '}' to close table constructor")
    return Expr.TableConstructor(fields, start.line)
  }
}
