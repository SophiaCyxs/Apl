package com.example.lua

enum class OpCode {
  LOADK,       // push constants[a]
  LOADNIL,     // push nil
  LOADBOOL,    // push (a != 0)
  GETVARARG,   // push varargs (a = count, or -1 for multi)
  GETGLOBAL,   // push env[constants[a]]
  SETGLOBAL,   // env[constants[a]] = pop()
  GETLOCAL,    // push locals[a]
  SETLOCAL,    // locals[a] = pop()
  NEWLOCALSCOPE, // replace local slot `a` with fresh UpvalueCell (for loop capture)
  GETUPVAL,    // push upvalues[a]
  SETUPVAL,    // upvalues[a] = pop()
  NEWTABLE,    // push new empty table
  SETLIST,     // table at stack[-a-1]: append top `a` items (expands trailing Multi)
  SETTABLE,    // val = pop(), key = pop(), table = pop() -> table[key] = val
  GETTABLE,    // key = pop(), table = pop() -> push table[key]
  SELF,        // peek receiver, push receiver[constants[a]], push receiver (prepares method call)
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
  NOT,
  UNM,
  LEN,
  JMP,         // pc = a
  JMPF,        // if falsy(pop()) pc = a
  JMPF_KEEP,   // if falsy(peek()) pc = a else pop() (short-circuit 'and')
  JMPT_KEEP,   // if truthy(peek()) pc = a else pop() (short-circuit 'or')
  CALL,        // a = fixedArgCount, b = retCount (-1 = Multi, 0 = discard, >0 = exact)
  RETURN,      // a = valueCount (-1 = last is Multi)
  CLOSURE,     // push closure from protos[a]
  POP,         // discard top of stack
  DUP,         // duplicate top of stack
  DUP2,        // duplicate top two values (table, key)
}

data class Instruction(
  val op: OpCode,
  val a: Int = 0,
  val b: Int = 0,
  val line: Int = 0,
)

data class UpvalDesc(
  val name: String,
  val inParentLocals: Boolean,
  val index: Int,
)

sealed interface LuaConstant {
  data class Num(val value: Double) : LuaConstant {
    override fun toString(): String =
      if (value % 1.0 == 0.0 && value in -1e15..1e15) value.toLong().toString() else value.toString()
  }

  data class Str(val value: String) : LuaConstant {
    override fun toString(): String = "\"${value.replace("\n", "\\n").take(40)}\""
  }
}

data class FunctionProto(
  val name: String,
  val paramCount: Int,
  val isVararg: Boolean,
  val maxLocals: Int,
  val constants: List<LuaConstant>,
  val upvalues: List<UpvalDesc>,
  val code: List<Instruction>,
  val protos: List<FunctionProto>,
) {
  fun disassemble(indent: String = ""): String {
    val sb = StringBuilder()
    sb.appendLine("${indent}function <$name> (params=$paramCount, locals=$maxLocals, upvals=${upvalues.size}, instrs=${code.size})")
    if (constants.isNotEmpty()) {
      sb.appendLine("${indent}  constants:")
      constants.forEachIndexed { idx, c -> sb.appendLine("${indent}    [$idx] $c") }
    }
    code.forEachIndexed { pc, instr ->
      val comment =
        when (instr.op) {
          OpCode.LOADK,
          OpCode.GETGLOBAL,
          OpCode.SETGLOBAL,
          OpCode.SELF -> " ; ${constants.getOrNull(instr.a)}"
          else -> ""
        }
      sb.appendLine(
        "$indent  ${pc.toString().padStart(4, '0')}  [L${instr.line.toString().padStart(3)}]  ${instr.op.name.padEnd(13)} ${instr.a} ${instr.b}$comment"
      )
    }
    protos.forEachIndexed { idx, child ->
      sb.appendLine("${indent}  -- child proto #$idx --")
      sb.append(child.disassemble("$indent  "))
    }
    return sb.toString()
  }
}
