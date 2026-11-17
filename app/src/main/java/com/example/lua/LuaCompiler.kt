package com.example.lua

class LuaCompiler {
  private class LocalVar(val name: String, val slot: Int, val depth: Int)

  private class LoopContext {
    val breakJumpIndices = mutableListOf<Int>()
  }

  private class FuncScope(
    val name: String,
    val parent: FuncScope?,
    val paramCount: Int,
    val isVararg: Boolean,
  ) {
    val constants = mutableListOf<LuaConstant>()
    val constMap = mutableMapOf<LuaConstant, Int>()
    val upvalues = mutableListOf<UpvalDesc>()
    val code = mutableListOf<Instruction>()
    val protos = mutableListOf<FunctionProto>()
    val locals = mutableListOf<LocalVar>()
    val loopStack = mutableListOf<LoopContext>()
    var scopeDepth = 0
    var maxLocals = 0

    fun addConstant(constant: LuaConstant): Int =
      constMap.getOrPut(constant) {
        constants.add(constant)
        constants.size - 1
      }

    fun emit(op: OpCode, a: Int = 0, b: Int = 0, line: Int = 0): Int {
      code.add(Instruction(op, a, b, line))
      return code.size - 1
    }

    fun patchJump(instrIndex: Int, targetPc: Int = code.size) {
      val old = code[instrIndex]
      code[instrIndex] = old.copy(a = targetPc)
    }

    fun declareLocal(name: String): Int {
      val slot = locals.size
      locals.add(LocalVar(name, slot, scopeDepth))
      if (locals.size > maxLocals) maxLocals = locals.size
      return slot
    }

    fun resolveLocal(name: String): Int? {
      for (i in locals.indices.reversed()) {
        if (locals[i].name == name) return locals[i].slot
      }
      return null
    }

    fun resolveUpvalue(name: String): Int? {
      val p = parent ?: return null
      val localSlot = p.resolveLocal(name)
      if (localSlot != null) {
        return addUpvalue(name, inParentLocals = true, index = localSlot)
      }
      val parentUpval = p.resolveUpvalue(name)
      if (parentUpval != null) {
        return addUpvalue(name, inParentLocals = false, index = parentUpval)
      }
      return null
    }

    private fun addUpvalue(name: String, inParentLocals: Boolean, index: Int): Int {
      for (i in upvalues.indices) {
        val uv = upvalues[i]
        if (uv.inParentLocals == inParentLocals && uv.index == index) return i
      }
      upvalues.add(UpvalDesc(name, inParentLocals, index))
      return upvalues.size - 1
    }

    fun pushScope() {
      scopeDepth++
    }

    fun popScope() {
      while (locals.isNotEmpty() && locals.last().depth == scopeDepth) {
        locals.removeAt(locals.lastIndex)
      }
      scopeDepth--
    }

    fun toProto(): FunctionProto =
      FunctionProto(
        name = name,
        paramCount = paramCount,
        isVararg = isVararg,
        maxLocals = maxLocals,
        constants = constants,
        upvalues = upvalues,
        code = code,
        protos = protos,
      )
  }

  fun compile(source: String, chunkName: String = "main"): FunctionProto {
    val tokens = LuaLexer(source).tokenize()
    val block = LuaParser(tokens).parseChunk()
    val root = FuncScope(chunkName, parent = null, paramCount = 0, isVararg = true)
    compileBlock(root, block)
    root.emit(OpCode.RETURN, 0, 0, line = block.statements.lastOrNull()?.line ?: 1)
    return root.toProto()
  }

  private fun compileBlock(scope: FuncScope, block: Block) {
    scope.pushScope()
    for (stmt in block.statements) {
      compileStmt(scope, stmt)
    }
    scope.popScope()
  }

  private fun compileStmt(scope: FuncScope, stmt: Stmt) {
    when (stmt) {
      is Stmt.DoBlock -> compileBlock(scope, stmt.block)

      is Stmt.ExprStmt -> {
        compileExpr(scope, stmt.expr, expectedResults = 0)
      }

      is Stmt.LocalAssign -> {
        val nameCount = stmt.names.size
        if (stmt.values.isEmpty()) {
          for (name in stmt.names) {
            val slot = scope.declareLocal(name)
            scope.emit(OpCode.LOADNIL, line = stmt.line)
            scope.emit(OpCode.SETLOCAL, slot, line = stmt.line)
          }
        } else {
          compileExprListForTargets(scope, stmt.values, nameCount, stmt.line)
          val slots = stmt.names.map { scope.declareLocal(it) }
          for (slot in slots.reversed()) {
            scope.emit(OpCode.SETLOCAL, slot, line = stmt.line)
          }
        }
      }

      is Stmt.Assign -> {
        // Evaluate LHS base targets first if indexed, then RHS, then assign right-to-left
        val targetCount = stmt.targets.size
        compileExprListForTargets(scope, stmt.values, targetCount, stmt.line)
        // Store RHS values into temporary local slots or evaluate right-to-left directly for simple names;
        // using hidden temp locals guarantees exact Lua evaluation order (a, b = b, a works!)
        scope.pushScope()
        val tempSlots = (0 until targetCount).map { idx -> scope.declareLocal("__tmp_$idx") }
        for (slot in tempSlots.reversed()) {
          scope.emit(OpCode.SETLOCAL, slot, line = stmt.line)
        }
        for (i in stmt.targets.indices) {
          val target = stmt.targets[i]
          val tempSlot = tempSlots[i]
          when (target) {
            is LValue.Name -> {
              scope.emit(OpCode.GETLOCAL, tempSlot, line = stmt.line)
              emitStoreVar(scope, target.name, stmt.line)
            }
            is LValue.Index -> {
              compileExpr(scope, target.target, 1)
              compileExpr(scope, target.key, 1)
              scope.emit(OpCode.GETLOCAL, tempSlot, line = stmt.line)
              scope.emit(OpCode.SETTABLE, line = stmt.line)
            }
          }
        }
        scope.popScope()
      }

      is Stmt.If -> {
        val endJumps = mutableListOf<Int>()
        for ((cond, block) in stmt.branches) {
          compileExpr(scope, cond, 1)
          val jumpFalse = scope.emit(OpCode.JMPF, 0, line = cond.line)
          compileBlock(scope, block)
          endJumps.add(scope.emit(OpCode.JMP, 0, line = cond.line))
          scope.patchJump(jumpFalse)
        }
        if (stmt.elseBlock != null) {
          compileBlock(scope, stmt.elseBlock)
        }
        for (j in endJumps) {
          scope.patchJump(j)
        }
      }

      is Stmt.While -> {
        val loopStart = scope.code.size
        scope.loopStack.add(LoopContext())
        compileExpr(scope, stmt.condition, 1)
        val exitJump = scope.emit(OpCode.JMPF, 0, line = stmt.line)
        compileBlock(scope, stmt.body)
        scope.emit(OpCode.JMP, loopStart, line = stmt.line)
        scope.patchJump(exitJump)
        val loopCtx = scope.loopStack.removeAt(scope.loopStack.lastIndex)
        loopCtx.breakJumpIndices.forEach { scope.patchJump(it) }
      }

      is Stmt.Repeat -> {
        val loopStart = scope.code.size
        scope.loopStack.add(LoopContext())
        scope.pushScope()
        for (s in stmt.body.statements) {
          compileStmt(scope, s)
        }
        compileExpr(scope, stmt.condition, 1)
        scope.popScope()
        val exitJump = scope.emit(OpCode.JMPF, loopStart, line = stmt.condition.line)
        // JMPF jumps back to loopStart if condition is falsy; otherwise falls through!
        val _unused = exitJump
        val loopCtx = scope.loopStack.removeAt(scope.loopStack.lastIndex)
        loopCtx.breakJumpIndices.forEach { scope.patchJump(it) }
      }

      is Stmt.NumericFor -> {
        scope.pushScope()
        val idxSlot = scope.declareLocal("(for index)")
        val limitSlot = scope.declareLocal("(for limit)")
        val stepSlot = scope.declareLocal("(for step)")
        val userSlot = scope.declareLocal(stmt.varName)

        compileExpr(scope, stmt.start, 1)
        scope.emit(OpCode.SETLOCAL, idxSlot, line = stmt.line)
        compileExpr(scope, stmt.limit, 1)
        scope.emit(OpCode.SETLOCAL, limitSlot, line = stmt.line)
        if (stmt.step != null) {
          compileExpr(scope, stmt.step, 1)
        } else {
          val oneIdx = scope.addConstant(LuaConstant.Num(1.0))
          scope.emit(OpCode.LOADK, oneIdx, line = stmt.line)
        }
        scope.emit(OpCode.SETLOCAL, stepSlot, line = stmt.line)

        val loopStart = scope.code.size
        scope.loopStack.add(LoopContext())

        // Check continuation: (step >= 0 and idx <= limit) or (step < 0 and idx >= limit)
        // Compute (step >= 0) -> if true check idx <= limit, else check idx >= limit
        val zeroIdx = scope.addConstant(LuaConstant.Num(0.0))
        scope.emit(OpCode.GETLOCAL, stepSlot, line = stmt.line)
        scope.emit(OpCode.LOADK, zeroIdx, line = stmt.line)
        scope.emit(OpCode.GE, line = stmt.line)
        val negStepJump = scope.emit(OpCode.JMPF, 0, line = stmt.line)

        // Positive step: idx <= limit
        scope.emit(OpCode.GETLOCAL, idxSlot, line = stmt.line)
        scope.emit(OpCode.GETLOCAL, limitSlot, line = stmt.line)
        scope.emit(OpCode.LE, line = stmt.line)
        val bodyJumpFromPos = scope.emit(OpCode.JMP, 0, line = stmt.line)

        // Negative step: idx >= limit
        scope.patchJump(negStepJump)
        scope.emit(OpCode.GETLOCAL, idxSlot, line = stmt.line)
        scope.emit(OpCode.GETLOCAL, limitSlot, line = stmt.line)
        scope.emit(OpCode.GE, line = stmt.line)

        scope.patchJump(bodyJumpFromPos)
        val exitJump = scope.emit(OpCode.JMPF, 0, line = stmt.line)

        // Fresh cell for loop variable so closures capture iteration value
        scope.emit(OpCode.NEWLOCALSCOPE, userSlot, line = stmt.line)
        scope.emit(OpCode.GETLOCAL, idxSlot, line = stmt.line)
        scope.emit(OpCode.SETLOCAL, userSlot, line = stmt.line)

        compileBlock(scope, stmt.body)

        // idx = idx + step
        scope.emit(OpCode.GETLOCAL, idxSlot, line = stmt.line)
        scope.emit(OpCode.GETLOCAL, stepSlot, line = stmt.line)
        scope.emit(OpCode.ADD, line = stmt.line)
        scope.emit(OpCode.SETLOCAL, idxSlot, line = stmt.line)
        scope.emit(OpCode.JMP, loopStart, line = stmt.line)

        scope.patchJump(exitJump)
        val loopCtx = scope.loopStack.removeAt(scope.loopStack.lastIndex)
        loopCtx.breakJumpIndices.forEach { scope.patchJump(it) }
        scope.popScope()
      }

      is Stmt.GenericFor -> {
        scope.pushScope()
        val fSlot = scope.declareLocal("(for generator)")
        val sSlot = scope.declareLocal("(for state)")
        val varSlot = scope.declareLocal("(for control)")

        compileExprListForTargets(scope, stmt.iterators, 3, stmt.line)
        scope.emit(OpCode.SETLOCAL, varSlot, line = stmt.line)
        scope.emit(OpCode.SETLOCAL, sSlot, line = stmt.line)
        scope.emit(OpCode.SETLOCAL, fSlot, line = stmt.line)

        val userSlots = stmt.names.map { scope.declareLocal(it) }
        val loopStart = scope.code.size
        scope.loopStack.add(LoopContext())

        // Call f(s, var) expecting userSlots.size results
        scope.emit(OpCode.GETLOCAL, fSlot, line = stmt.line)
        scope.emit(OpCode.GETLOCAL, sSlot, line = stmt.line)
        scope.emit(OpCode.GETLOCAL, varSlot, line = stmt.line)
        scope.emit(OpCode.CALL, 2, userSlots.size, line = stmt.line)

        for (slot in userSlots.reversed()) {
          scope.emit(OpCode.NEWLOCALSCOPE, slot, line = stmt.line)
          scope.emit(OpCode.SETLOCAL, slot, line = stmt.line)
        }

        // If first loop var == nil, exit loop
        scope.emit(OpCode.GETLOCAL, userSlots.first(), line = stmt.line)
        scope.emit(OpCode.LOADNIL, line = stmt.line)
        scope.emit(OpCode.NEQ, line = stmt.line)
        val exitJump = scope.emit(OpCode.JMPF, 0, line = stmt.line)

        // Update control variable var = var1
        scope.emit(OpCode.GETLOCAL, userSlots.first(), line = stmt.line)
        scope.emit(OpCode.SETLOCAL, varSlot, line = stmt.line)

        compileBlock(scope, stmt.body)
        scope.emit(OpCode.JMP, loopStart, line = stmt.line)

        scope.patchJump(exitJump)
        val loopCtx = scope.loopStack.removeAt(scope.loopStack.lastIndex)
        loopCtx.breakJumpIndices.forEach { scope.patchJump(it) }
        scope.popScope()
      }

      is Stmt.FunctionDecl -> {
        val funcName =
          buildString {
            append(stmt.nameParts.joinToString("."))
            if (stmt.methodName != null) append(":${stmt.methodName}")
          }
        val params =
          if (stmt.methodName != null) listOf("self") + stmt.params else stmt.params

        if (stmt.isLocal) {
          val slot = scope.declareLocal(stmt.nameParts.first())
          compileFunctionBody(scope, funcName, params, stmt.isVararg, stmt.body, stmt.line)
          scope.emit(OpCode.SETLOCAL, slot, line = stmt.line)
        } else if (stmt.nameParts.size == 1 && stmt.methodName == null) {
          compileFunctionBody(scope, funcName, params, stmt.isVararg, stmt.body, stmt.line)
          emitStoreVar(scope, stmt.nameParts.first(), stmt.line)
        } else {
          // Traverse table chain: a.b.c = fn or a.b:c = fn
          emitLoadVar(scope, stmt.nameParts.first(), stmt.line)
          val intermediateParts =
            if (stmt.methodName != null) stmt.nameParts.drop(1)
            else stmt.nameParts.drop(1).dropLast(1)
          for (part in intermediateParts) {
            val kIdx = scope.addConstant(LuaConstant.Str(part))
            scope.emit(OpCode.LOADK, kIdx, line = stmt.line)
            scope.emit(OpCode.GETTABLE, line = stmt.line)
          }
          val finalKey = stmt.methodName ?: stmt.nameParts.last()
          val kIdx = scope.addConstant(LuaConstant.Str(finalKey))
          scope.emit(OpCode.LOADK, kIdx, line = stmt.line)
          compileFunctionBody(scope, funcName, params, stmt.isVararg, stmt.body, stmt.line)
          scope.emit(OpCode.SETTABLE, line = stmt.line)
        }
      }

      is Stmt.Return -> {
        if (stmt.values.isEmpty()) {
          scope.emit(OpCode.RETURN, 0, 0, line = stmt.line)
        } else {
          stmt.values.forEachIndexed { idx, expr ->
            val isLast = idx == stmt.values.lastIndex
            compileExpr(scope, expr, if (isLast) -1 else 1)
          }
          scope.emit(OpCode.RETURN, stmt.values.size, -1, line = stmt.line)
        }
      }

      is Stmt.Break -> {
        val currentLoop =
          scope.loopStack.lastOrNull()
            ?: throw LuaCompileException("No loop to break out of", stmt.line, 1, "Compiler")
        val jmp = scope.emit(OpCode.JMP, 0, line = stmt.line)
        currentLoop.breakJumpIndices.add(jmp)
      }
    }
  }

  private fun compileExprListForTargets(
    scope: FuncScope,
    values: List<Expr>,
    targetCount: Int,
    line: Int,
  ) {
    if (values.isEmpty()) {
      repeat(targetCount) { scope.emit(OpCode.LOADNIL, line = line) }
      return
    }
    for (i in 0 until values.size - 1) {
      if (i < targetCount) {
        compileExpr(scope, values[i], 1)
      } else {
        compileExpr(scope, values[i], 0)
      }
    }
    val remaining = targetCount - (values.size - 1)
    if (remaining <= 0) {
      compileExpr(scope, values.last(), 0)
    } else {
      compileExpr(scope, values.last(), remaining)
    }
  }

  private fun compileFunctionBody(
    parentScope: FuncScope,
    name: String,
    params: List<String>,
    isVararg: Boolean,
    body: Block,
    line: Int,
  ) {
    val child = FuncScope(name, parent = parentScope, paramCount = params.size, isVararg = isVararg)
    child.pushScope()
    for (p in params) {
      child.declareLocal(p)
    }
    compileBlock(child, body)
    child.popScope()
    child.emit(OpCode.RETURN, 0, 0, line = line)
    parentScope.protos.add(child.toProto())
    val protoIndex = parentScope.protos.size - 1
    parentScope.emit(OpCode.CLOSURE, protoIndex, line = line)
  }

  private fun compileExpr(scope: FuncScope, expr: Expr, expectedResults: Int) {
    when (expr) {
      is Expr.NilLit -> emitResultAdjusted(scope, expectedResults, expr.line) {
        scope.emit(OpCode.LOADNIL, line = expr.line)
      }
      is Expr.BoolLit -> emitResultAdjusted(scope, expectedResults, expr.line) {
        scope.emit(OpCode.LOADBOOL, if (expr.value) 1 else 0, line = expr.line)
      }
      is Expr.NumberLit -> emitResultAdjusted(scope, expectedResults, expr.line) {
        val kIdx = scope.addConstant(LuaConstant.Num(expr.value))
        scope.emit(OpCode.LOADK, kIdx, line = expr.line)
      }
      is Expr.StringLit -> emitResultAdjusted(scope, expectedResults, expr.line) {
        val kIdx = scope.addConstant(LuaConstant.Str(expr.value))
        scope.emit(OpCode.LOADK, kIdx, line = expr.line)
      }
      is Expr.Vararg -> {
        if (!scope.isVararg) {
          throw LuaCompileException("Cannot use '...' outside a vararg function", expr.line, 1, "Compiler")
        }
        if (expectedResults != 0) {
          scope.emit(OpCode.GETVARARG, expectedResults, line = expr.line)
        }
      }
      is Expr.Name -> emitResultAdjusted(scope, expectedResults, expr.line) {
        emitLoadVar(scope, expr.name, expr.line)
      }
      is Expr.Index -> emitResultAdjusted(scope, expectedResults, expr.line) {
        compileExpr(scope, expr.target, 1)
        compileExpr(scope, expr.key, 1)
        scope.emit(OpCode.GETTABLE, line = expr.line)
      }
      is Expr.FunctionExpr -> emitResultAdjusted(scope, expectedResults, expr.line) {
        compileFunctionBody(
          scope,
          "anon@${expr.line}",
          expr.params,
          expr.isVararg,
          expr.body,
          expr.line,
        )
      }
      is Expr.Unary -> emitResultAdjusted(scope, expectedResults, expr.line) {
        compileExpr(scope, expr.operand, 1)
        val op =
          when (expr.op) {
            UnOp.NEG -> OpCode.UNM
            UnOp.NOT -> OpCode.NOT
            UnOp.LEN -> OpCode.LEN
          }
        scope.emit(op, line = expr.line)
      }
      is Expr.Binary -> emitResultAdjusted(scope, expectedResults, expr.line) {
        when (expr.op) {
          BinOp.AND -> {
            compileExpr(scope, expr.left, 1)
            val skip = scope.emit(OpCode.JMPF_KEEP, 0, line = expr.line)
            compileExpr(scope, expr.right, 1)
            scope.patchJump(skip)
          }
          BinOp.OR -> {
            compileExpr(scope, expr.left, 1)
            val skip = scope.emit(OpCode.JMPT_KEEP, 0, line = expr.line)
            compileExpr(scope, expr.right, 1)
            scope.patchJump(skip)
          }
          else -> {
            compileExpr(scope, expr.left, 1)
            compileExpr(scope, expr.right, 1)
            val op =
              when (expr.op) {
                BinOp.ADD -> OpCode.ADD
                BinOp.SUB -> OpCode.SUB
                BinOp.MUL -> OpCode.MUL
                BinOp.DIV -> OpCode.DIV
                BinOp.MOD -> OpCode.MOD
                BinOp.POW -> OpCode.POW
                BinOp.CONCAT -> OpCode.CONCAT
                BinOp.EQ -> OpCode.EQ
                BinOp.NEQ -> OpCode.NEQ
                BinOp.LT -> OpCode.LT
                BinOp.LE -> OpCode.LE
                BinOp.GT -> OpCode.GT
                BinOp.GE -> OpCode.GE
                BinOp.AND,
                BinOp.OR -> error("Handled above")
              }
            scope.emit(op, line = expr.line)
          }
        }
      }
      is Expr.TableConstructor -> emitResultAdjusted(scope, expectedResults, expr.line) {
        scope.emit(OpCode.NEWTABLE, line = expr.line)
        val positionalBatch = mutableListOf<Expr>()
        fun flushPositionals(isEndOfTable: Boolean) {
          if (positionalBatch.isEmpty()) return
          positionalBatch.forEachIndexed { idx, v ->
            val expand = isEndOfTable && idx == positionalBatch.lastIndex
            compileExpr(scope, v, if (expand) -1 else 1)
          }
          scope.emit(OpCode.SETLIST, positionalBatch.size, line = expr.line)
          positionalBatch.clear()
        }

        expr.fields.forEachIndexed { idx, field ->
          val isLast = idx == expr.fields.lastIndex
          when (field) {
            is TableField.Positional -> {
              positionalBatch.add(field.value)
              if (isLast) flushPositionals(isEndOfTable = true)
            }
            is TableField.Named -> {
              flushPositionals(isEndOfTable = false)
              scope.emit(OpCode.DUP, line = expr.line)
              val kIdx = scope.addConstant(LuaConstant.Str(field.name))
              scope.emit(OpCode.LOADK, kIdx, line = expr.line)
              compileExpr(scope, field.value, 1)
              scope.emit(OpCode.SETTABLE, line = expr.line)
            }
            is TableField.Keyed -> {
              flushPositionals(isEndOfTable = false)
              scope.emit(OpCode.DUP, line = expr.line)
              compileExpr(scope, field.key, 1)
              compileExpr(scope, field.value, 1)
              scope.emit(OpCode.SETTABLE, line = expr.line)
            }
          }
        }
      }
      is Expr.Call -> {
        compileExpr(scope, expr.callee, 1)
        expr.args.forEachIndexed { idx, arg ->
          val isLast = idx == expr.args.lastIndex
          compileExpr(scope, arg, if (isLast) -1 else 1)
        }
        scope.emit(OpCode.CALL, expr.args.size, expectedResults, line = expr.line)
      }
      is Expr.MethodCall -> {
        compileExpr(scope, expr.receiver, 1)
        val kIdx = scope.addConstant(LuaConstant.Str(expr.method))
        scope.emit(OpCode.SELF, kIdx, line = expr.line)
        expr.args.forEachIndexed { idx, arg ->
          val isLast = idx == expr.args.lastIndex
          compileExpr(scope, arg, if (isLast) -1 else 1)
        }
        scope.emit(OpCode.CALL, expr.args.size + 1, expectedResults, line = expr.line)
      }
    }
  }

  private inline fun emitResultAdjusted(
    scope: FuncScope,
    expectedResults: Int,
    line: Int,
    emitSingle: () -> Unit,
  ) {
    if (expectedResults == 0) {
      emitSingle()
      scope.emit(OpCode.POP, line = line)
      return
    }
    emitSingle()
    if (expectedResults > 1) {
      repeat(expectedResults - 1) {
        scope.emit(OpCode.LOADNIL, line = line)
      }
    }
  }

  private fun emitLoadVar(scope: FuncScope, name: String, line: Int) {
    val localSlot = scope.resolveLocal(name)
    if (localSlot != null) {
      scope.emit(OpCode.GETLOCAL, localSlot, line = line)
      return
    }
    val upvalIdx = scope.resolveUpvalue(name)
    if (upvalIdx != null) {
      scope.emit(OpCode.GETUPVAL, upvalIdx, line = line)
      return
    }
    val kIdx = scope.addConstant(LuaConstant.Str(name))
    scope.emit(OpCode.GETGLOBAL, kIdx, line = line)
  }

  private fun emitStoreVar(scope: FuncScope, name: String, line: Int) {
    val localSlot = scope.resolveLocal(name)
    if (localSlot != null) {
      scope.emit(OpCode.SETLOCAL, localSlot, line = line)
      return
    }
    val upvalIdx = scope.resolveUpvalue(name)
    if (upvalIdx != null) {
      scope.emit(OpCode.SETUPVAL, upvalIdx, line = line)
      return
    }
    val kIdx = scope.addConstant(LuaConstant.Str(name))
    scope.emit(OpCode.SETGLOBAL, kIdx, line = line)
  }
}
