package com.example.lua

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.math.pow

class LuaVm(
  val globalEnv: LuaValue.Table = LuaValue.Table(),
  var maxOpsPerExecution: Int = 500_000,
) {
  var totalOpsExecuted: Long = 0L
    private set

  var hookResolver: (LuaValue) -> LuaValue = { it }

  private var opsSinceYield = 0

  fun resetOpCounters() {
    totalOpsExecuted = 0L
    opsSinceYield = 0
  }

  suspend fun execute(proto: FunctionProto, env: LuaValue.Table = globalEnv): List<LuaValue> {
    val upvals = Array(proto.upvalues.size) { UpvalueCell(LuaValue.Nil) }
    val rootClosure = LuaValue.Closure(proto, upvals, env)
    return callFunction(rootClosure, emptyList())
  }

  suspend fun callFunction(fn: LuaValue, args: List<LuaValue>): List<LuaValue> {
    val resolved = hookResolver(fn)
    return when (resolved) {
      is LuaValue.Closure -> runClosure(resolved, args)
      is LuaValue.NativeFunc -> resolved.fn(this, args)
      is LuaValue.Table -> {
        val callMeta = resolved.metatable?.get(LuaValue.Str("__call"))
        if (callMeta != null && callMeta !is LuaValue.Nil) {
          callFunction(callMeta, listOf(resolved) + args)
        } else {
          throw LuaRuntimeException("Attempt to call a table value")
        }
      }
      is LuaValue.Userdata -> {
        val callMeta = resolved.metatable?.get(LuaValue.Str("__call"))
        if (callMeta != null && callMeta !is LuaValue.Nil) {
          callFunction(callMeta, listOf(resolved) + args)
        } else {
          throw LuaRuntimeException("Attempt to call a ${resolved.typeName} value")
        }
      }
      else -> throw LuaRuntimeException("Attempt to call a ${resolved.luaTypeName()} value")
    }
  }

  private suspend fun runClosure(closure: LuaValue.Closure, args: List<LuaValue>): List<LuaValue> {
    val proto = closure.proto
    val locals = Array(proto.maxLocals) { UpvalueCell(LuaValue.Nil) }
    for (i in 0 until proto.paramCount) {
      locals[i].value = args.getOrElse(i) { LuaValue.Nil }
    }
    val varargs =
      if (proto.isVararg && args.size > proto.paramCount) {
        args.subList(proto.paramCount, args.size)
      } else {
        emptyList()
      }

    val stack = ArrayList<LuaValue>(32)
    val code = proto.code
    var pc = 0

    while (pc < code.size) {
      totalOpsExecuted++
      opsSinceYield++
      if (opsSinceYield >= 4096) {
        opsSinceYield = 0
        currentCoroutineContext().ensureActive()
        yield()
      }
      if (totalOpsExecuted > maxOpsPerExecution * 40L) {
        throw LuaRuntimeException(
          "Instruction guard triggered ($totalOpsExecuted ops). Increase limit in Settings or optimize loop.",
          code[pc].line,
        )
      }

      val instr = code[pc++]
      try {
        when (instr.op) {
          OpCode.LOADK -> stack.add(constToLua(proto.constants[instr.a]))
          OpCode.LOADNIL -> stack.add(LuaValue.Nil)
          OpCode.LOADBOOL -> stack.add(LuaValue.Bool(instr.a != 0))
          OpCode.GETVARARG -> {
            if (instr.a < 0) {
              stack.add(LuaValue.Multi(varargs))
            } else {
              for (i in 0 until instr.a) {
                stack.add(varargs.getOrElse(i) { LuaValue.Nil })
              }
            }
          }
          OpCode.GETGLOBAL -> {
            val name = (proto.constants[instr.a] as LuaConstant.Str).value
            stack.add(getTableValue(closure.env, LuaValue.Str(name)))
          }
          OpCode.SETGLOBAL -> {
            val name = (proto.constants[instr.a] as LuaConstant.Str).value
            val v = stack.removeAt(stack.lastIndex).single()
            setTableValue(closure.env, LuaValue.Str(name), v)
          }
          OpCode.GETLOCAL -> stack.add(locals[instr.a].value)
          OpCode.SETLOCAL -> {
            val v = stack.removeAt(stack.lastIndex).single()
            locals[instr.a].value = v
          }
          OpCode.NEWLOCALSCOPE -> {
            locals[instr.a] = UpvalueCell(locals[instr.a].value)
          }
          OpCode.GETUPVAL -> stack.add(closure.upvalues[instr.a].value)
          OpCode.SETUPVAL -> {
            val v = stack.removeAt(stack.lastIndex).single()
            closure.upvalues[instr.a].value = v
          }
          OpCode.NEWTABLE -> stack.add(LuaValue.Table())
          OpCode.SETLIST -> {
            val rawValues = MutableList<LuaValue>(instr.a) { LuaValue.Nil }
            for (i in instr.a - 1 downTo 0) {
              rawValues[i] = stack.removeAt(stack.lastIndex)
            }
            val table = stack.last() as LuaValue.Table
            var arrIdx = table.arrayLength() + 1
            rawValues.forEachIndexed { idx, v ->
              if (idx == rawValues.lastIndex && v is LuaValue.Multi) {
                for (item in v.values) {
                  table.rawSet(LuaValue.Num((arrIdx++).toDouble()), item)
                }
              } else {
                table.rawSet(LuaValue.Num((arrIdx++).toDouble()), v.single())
              }
            }
          }
          OpCode.SETTABLE -> {
            val value = stack.removeAt(stack.lastIndex).single()
            val key = stack.removeAt(stack.lastIndex).single()
            val target = stack.removeAt(stack.lastIndex).single()
            setTableValue(target, key, value)
          }
          OpCode.GETTABLE -> {
            val key = stack.removeAt(stack.lastIndex).single()
            val target = stack.removeAt(stack.lastIndex).single()
            stack.add(getTableValue(target, key))
          }
          OpCode.SELF -> {
            val receiver = stack.last().single()
            val methodKey = LuaValue.Str((proto.constants[instr.a] as LuaConstant.Str).value)
            val method = getTableValue(receiver, methodKey)
            stack[stack.lastIndex] = method
            stack.add(receiver)
          }
          OpCode.ADD -> binaryMath(stack, "+") { a, b -> a + b }
          OpCode.SUB -> binaryMath(stack, "-") { a, b -> a - b }
          OpCode.MUL -> binaryMath(stack, "*") { a, b -> a * b }
          OpCode.DIV -> binaryMath(stack, "/") { a, b -> a / b }
          OpCode.MOD -> binaryMath(stack, "%") { a, b ->
            val r = a % b
            if (r != 0.0 && (r < 0) != (b < 0)) r + b else r
          }
          OpCode.POW -> binaryMath(stack, "^") { a, b -> a.pow(b) }
          OpCode.CONCAT -> {
            val b = stack.removeAt(stack.lastIndex).single()
            val a = stack.removeAt(stack.lastIndex).single()
            stack.add(LuaValue.Str(concatString(a) + concatString(b)))
          }
          OpCode.EQ -> {
            val b = stack.removeAt(stack.lastIndex).single()
            val a = stack.removeAt(stack.lastIndex).single()
            stack.add(LuaValue.Bool(luaEquals(a, b)))
          }
          OpCode.NEQ -> {
            val b = stack.removeAt(stack.lastIndex).single()
            val a = stack.removeAt(stack.lastIndex).single()
            stack.add(LuaValue.Bool(!luaEquals(a, b)))
          }
          OpCode.LT -> compareOp(stack) { a, b -> a < b }
          OpCode.LE -> compareOp(stack) { a, b -> a <= b }
          OpCode.GT -> compareOp(stack) { a, b -> a > b }
          OpCode.GE -> compareOp(stack) { a, b -> a >= b }
          OpCode.NOT -> {
            val v = stack.removeAt(stack.lastIndex).single()
            stack.add(LuaValue.Bool(!v.isTruthy()))
          }
          OpCode.UNM -> {
            val v = stack.removeAt(stack.lastIndex).single()
            val n = v.asDoubleOrNull()
              ?: throw LuaRuntimeException("Attempt to perform arithmetic on a ${v.typeofName()} value")
            stack.add(LuaValue.Num(-n))
          }
          OpCode.LEN -> {
            val v = stack.removeAt(stack.lastIndex).single()
            val len =
              when (v) {
                is LuaValue.Str -> v.value.length.toDouble()
                is LuaValue.Table -> v.arrayLength().toDouble()
                else -> throw LuaRuntimeException("Attempt to get length of a ${v.typeofName()} value")
              }
            stack.add(LuaValue.Num(len))
          }
          OpCode.JMP -> pc = instr.a
          OpCode.JMPF -> {
            val cond = stack.removeAt(stack.lastIndex).single()
            if (!cond.isTruthy()) pc = instr.a
          }
          OpCode.JMPF_KEEP -> {
            val cond = stack.last().single()
            if (!cond.isTruthy()) {
              stack[stack.lastIndex] = cond
              pc = instr.a
            } else {
              stack.removeAt(stack.lastIndex)
            }
          }
          OpCode.JMPT_KEEP -> {
            val cond = stack.last().single()
            if (cond.isTruthy()) {
              stack[stack.lastIndex] = cond
              pc = instr.a
            } else {
              stack.removeAt(stack.lastIndex)
            }
          }
          OpCode.CALL -> {
            val rawArgs = MutableList<LuaValue>(instr.a) { LuaValue.Nil }
            for (i in instr.a - 1 downTo 0) {
              rawArgs[i] = stack.removeAt(stack.lastIndex)
            }
            val fn = stack.removeAt(stack.lastIndex).single()
            val expandedArgs = ArrayList<LuaValue>(rawArgs.size)
            rawArgs.forEachIndexed { idx, v ->
              if (idx == rawArgs.lastIndex && v is LuaValue.Multi) {
                expandedArgs.addAll(v.values)
              } else {
                expandedArgs.add(v.single())
              }
            }
            val results = callFunction(fn, expandedArgs)
            when {
              instr.b < 0 -> stack.add(LuaValue.Multi(results))
              instr.b == 0 -> Unit
              else -> {
                for (i in 0 until instr.b) {
                  stack.add(results.getOrElse(i) { LuaValue.Nil })
                }
              }
            }
          }
          OpCode.RETURN -> {
            if (instr.a == 0) return emptyList()
            val raw = MutableList<LuaValue>(instr.a) { LuaValue.Nil }
            for (i in instr.a - 1 downTo 0) {
              raw[i] = stack.removeAt(stack.lastIndex)
            }
            val out = ArrayList<LuaValue>(raw.size)
            raw.forEachIndexed { idx, v ->
              if (idx == raw.lastIndex && v is LuaValue.Multi) {
                out.addAll(v.values)
              } else {
                out.add(v.single())
              }
            }
            return out
          }
          OpCode.CLOSURE -> {
            val childProto = proto.protos[instr.a]
            val childUpvals =
              Array(childProto.upvalues.size) { idx ->
                val desc = childProto.upvalues[idx]
                if (desc.inParentLocals) locals[desc.index] else closure.upvalues[desc.index]
              }
            stack.add(LuaValue.Closure(childProto, childUpvals, closure.env))
          }
          OpCode.POP -> stack.removeAt(stack.lastIndex)
          OpCode.DUP -> stack.add(stack.last())
          OpCode.DUP2 -> {
            val top = stack[stack.lastIndex]
            val second = stack[stack.lastIndex - 1]
            stack.add(second)
            stack.add(top)
          }
        }
      } catch (e: LuaRuntimeException) {
        if (e.line == 0) {
          throw LuaRuntimeException(e.message, instr.line)
        }
        throw e
      }
    }
    return emptyList()
  }

  private fun constToLua(c: LuaConstant): LuaValue =
    when (c) {
      is LuaConstant.Num -> LuaValue.Num(c.value)
      is LuaConstant.Str -> LuaValue.Str(c.value)
    }

  private suspend fun getTableValue(target: LuaValue, key: LuaValue): LuaValue {
    return when (target) {
      is LuaValue.Table -> {
        val direct = target.get(key)
        if (direct !is LuaValue.Nil) return direct
        val indexMeta = target.metatable?.get(LuaValue.Str("__index"))
        when (indexMeta) {
          null, is LuaValue.Nil -> LuaValue.Nil
          is LuaValue.Table -> getTableValue(indexMeta, key)
          else -> callFunction(indexMeta, listOf(target, key)).firstOrNull() ?: LuaValue.Nil
        }
      }
      is LuaValue.Userdata -> {
        if (key is LuaValue.Str) {
          val prop = target.onGet?.invoke(key.value)
          if (prop != null && prop !is LuaValue.Nil) return prop
        }
        val indexMeta = target.metatable?.get(LuaValue.Str("__index"))
        when (indexMeta) {
          null, is LuaValue.Nil -> LuaValue.Nil
          is LuaValue.Table -> getTableValue(indexMeta, key)
          else -> callFunction(indexMeta, listOf(target, key)).firstOrNull() ?: LuaValue.Nil
        }
      }
      is LuaValue.Str -> {
        val stringTable = globalEnv.get(LuaValue.Str("string"))
        if (stringTable is LuaValue.Table) stringTable.get(key) else LuaValue.Nil
      }
      else -> throw LuaRuntimeException("Attempt to index a ${target.typeofName()} value with '$key'")
    }
  }

  private suspend fun setTableValue(target: LuaValue, key: LuaValue, value: LuaValue) {
    when (target) {
      is LuaValue.Table -> {
        if (target.isReadonly) {
          throw LuaRuntimeException("Attempt to modify a readonly table")
        }
        val existing = target.get(key)
        val newIndex = target.metatable?.get(LuaValue.Str("__newindex"))
        if (existing is LuaValue.Nil && newIndex != null && newIndex !is LuaValue.Nil) {
          if (newIndex is LuaValue.Table) {
            setTableValue(newIndex, key, value)
          } else {
            callFunction(newIndex, listOf(target, key, value))
          }
        } else {
          target.rawSet(key, value)
        }
      }
      is LuaValue.Userdata -> {
        val keyStr = (key as? LuaValue.Str)?.value
        if (keyStr != null && target.onSet != null) {
          target.onSet.invoke(keyStr, value)
          return
        }
        val newIndex = target.metatable?.get(LuaValue.Str("__newindex"))
        if (newIndex != null && newIndex !is LuaValue.Nil) {
          callFunction(newIndex, listOf(target, key, value))
        } else {
          throw LuaRuntimeException("Cannot set property '$key' on ${target.typeName}")
        }
      }
      else -> throw LuaRuntimeException("Attempt to index a ${target.typeofName()} value")
    }
  }

  private suspend fun binaryMath(
    stack: ArrayList<LuaValue>,
    opName: String,
    op: (Double, Double) -> Double,
  ) {
    val b = stack.removeAt(stack.lastIndex).single()
    val a = stack.removeAt(stack.lastIndex).single()
    val na = a.asDoubleOrNull()
    val nb = b.asDoubleOrNull()
    if (na != null && nb != null) {
      stack.add(LuaValue.Num(op(na, nb)))
      return
    }
    val metaName =
      when (opName) {
        "+" -> "__add"
        "-" -> "__sub"
        "*" -> "__mul"
        "/" -> "__div"
        "%" -> "__mod"
        "^" -> "__pow"
        else -> "__add"
      }
    val meta = getMetamethod(a, b, metaName)
    if (meta != null) {
      val res = callFunction(meta, listOf(a, b)).firstOrNull() ?: LuaValue.Nil
      stack.add(res)
      return
    }
    throw LuaRuntimeException(
      "Attempt to perform arithmetic '$opName' on ${a.typeofName()} and ${b.typeofName()}"
    )
  }

  private suspend fun compareOp(stack: ArrayList<LuaValue>, cmp: (Double, Double) -> Boolean) {
    val b = stack.removeAt(stack.lastIndex).single()
    val a = stack.removeAt(stack.lastIndex).single()
    val result =
      when {
        a is LuaValue.Num && b is LuaValue.Num -> cmp(a.value, b.value)
        a is LuaValue.Str && b is LuaValue.Str -> {
          val c = a.value.compareTo(b.value)
          cmp(c.toDouble(), 0.0)
        }
        else -> throw LuaRuntimeException(
          "Attempt to compare ${a.typeofName()} with ${b.typeofName()}"
        )
      }
    stack.add(LuaValue.Bool(result))
  }

  private suspend fun luaEquals(a: LuaValue, b: LuaValue): Boolean {
    if (a === b) return true
    return when {
      a is LuaValue.Nil && b is LuaValue.Nil -> true
      a is LuaValue.Bool && b is LuaValue.Bool -> a.value == b.value
      a is LuaValue.Num && b is LuaValue.Num -> a.value == b.value
      a is LuaValue.Str && b is LuaValue.Str -> a.value == b.value
      a is LuaValue.Userdata && b is LuaValue.Userdata -> a.data == b.data
      a is LuaValue.Table && b is LuaValue.Table -> {
        val eqMeta = a.metatable?.get(LuaValue.Str("__eq")) ?: b.metatable?.get(LuaValue.Str("__eq"))
        if (eqMeta != null && eqMeta !is LuaValue.Nil) {
          callFunction(eqMeta, listOf(a, b)).firstOrNull()?.isTruthy() == true
        } else {
          false
        }
      }
      else -> false
    }
  }

  private suspend fun concatString(v: LuaValue): String =
    when (v) {
      is LuaValue.Str -> v.value
      is LuaValue.Num -> v.toString()
      is LuaValue.Table -> {
        val ts = v.metatable?.get(LuaValue.Str("__tostring"))
        if (ts != null && ts !is LuaValue.Nil) {
          callFunction(ts, listOf(v)).firstOrNull()?.toString() ?: ""
        } else {
          throw LuaRuntimeException("Attempt to concatenate a table value")
        }
      }
      else -> throw LuaRuntimeException("Attempt to concatenate a ${v.typeofName()} value")
    }

  private fun getMetamethod(a: LuaValue, b: LuaValue, name: String): LuaValue? {
    val ma = (a as? LuaValue.Table)?.metatable?.get(LuaValue.Str(name))
      ?: (a as? LuaValue.Userdata)?.metatable?.get(LuaValue.Str(name))
    if (ma != null && ma !is LuaValue.Nil) return ma
    val mb = (b as? LuaValue.Table)?.metatable?.get(LuaValue.Str(name))
      ?: (b as? LuaValue.Userdata)?.metatable?.get(LuaValue.Str(name))
    if (mb != null && mb !is LuaValue.Nil) return mb
    return null
  }
}
