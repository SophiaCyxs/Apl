package com.example.lua

data class Block(val statements: List<Stmt>)

sealed interface Stmt {
  val line: Int

  data class Assign(
    val targets: List<LValue>,
    val values: List<Expr>,
    override val line: Int,
  ) : Stmt

  data class LocalAssign(
    val names: List<String>,
    val values: List<Expr>,
    override val line: Int,
  ) : Stmt

  data class ExprStmt(val expr: Expr, override val line: Int) : Stmt

  data class DoBlock(val block: Block, override val line: Int) : Stmt

  data class While(val condition: Expr, val body: Block, override val line: Int) : Stmt

  data class Repeat(val body: Block, val condition: Expr, override val line: Int) : Stmt

  data class If(
    val branches: List<Pair<Expr, Block>>,
    val elseBlock: Block?,
    override val line: Int,
  ) : Stmt

  data class NumericFor(
    val varName: String,
    val start: Expr,
    val limit: Expr,
    val step: Expr?,
    val body: Block,
    override val line: Int,
  ) : Stmt

  data class GenericFor(
    val names: List<String>,
    val iterators: List<Expr>,
    val body: Block,
    override val line: Int,
  ) : Stmt

  data class FunctionDecl(
    val nameParts: List<String>,
    val methodName: String?,
    val params: List<String>,
    val isVararg: Boolean,
    val body: Block,
    val isLocal: Boolean,
    override val line: Int,
  ) : Stmt

  data class Return(val values: List<Expr>, override val line: Int) : Stmt

  data class Break(override val line: Int) : Stmt
}

sealed interface LValue {
  val line: Int

  data class Name(val name: String, override val line: Int) : LValue

  data class Index(val target: Expr, val key: Expr, override val line: Int) : LValue
}

sealed interface Expr {
  val line: Int

  data class NilLit(override val line: Int) : Expr

  data class BoolLit(val value: Boolean, override val line: Int) : Expr

  data class NumberLit(val value: Double, override val line: Int) : Expr

  data class StringLit(val value: String, override val line: Int) : Expr

  data class Vararg(override val line: Int) : Expr

  data class Name(val name: String, override val line: Int) : Expr

  data class Index(val target: Expr, val key: Expr, override val line: Int) : Expr

  data class Binary(val op: BinOp, val left: Expr, val right: Expr, override val line: Int) : Expr

  data class Unary(val op: UnOp, val operand: Expr, override val line: Int) : Expr

  data class FunctionExpr(
    val params: List<String>,
    val isVararg: Boolean,
    val body: Block,
    override val line: Int,
  ) : Expr

  data class TableConstructor(val fields: List<TableField>, override val line: Int) : Expr

  data class Call(val callee: Expr, val args: List<Expr>, override val line: Int) : Expr

  data class MethodCall(
    val receiver: Expr,
    val method: String,
    val args: List<Expr>,
    override val line: Int,
  ) : Expr
}

sealed interface TableField {
  data class Positional(val value: Expr) : TableField

  data class Named(val name: String, val value: Expr) : TableField

  data class Keyed(val key: Expr, val value: Expr) : TableField
}

enum class BinOp {
  ADD,
  SUB,
  MUL,
  DIV,
  MOD,
  POW,
  CONCAT,
  EQ,
  NEQ,
  LT,
  LE,
  GT,
  GE,
  AND,
  OR,
}

enum class UnOp {
  NEG,
  NOT,
  LEN,
}
