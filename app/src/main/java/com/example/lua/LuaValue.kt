package com.example.lua

import kotlin.math.floor

class UpvalueCell(var value: LuaValue)

sealed class LuaValue {
  object Nil : LuaValue() {
    override fun toString(): String = "nil"
  }

  data class Bool(val value: Boolean) : LuaValue() {
    override fun toString(): String = if (value) "true" else "false"
  }

  data class Num(val value: Double) : LuaValue() {
    override fun toString(): String {
      if (value.isNaN()) return "nan"
      if (value.isInfinite()) return if (value > 0) "inf" else "-inf"
      val l = value.toLong()
      return if (value == l.toDouble() && value in -1e15..1e15) l.toString() else value.toString()
    }
  }

  data class Str(val value: String) : LuaValue() {
    override fun toString(): String = value
  }

  class Table : LuaValue() {
    val entries = LinkedHashMap<LuaKey, LuaValue>()
    var metatable: Table? = null
    var isReadonly: Boolean = false

    fun get(key: LuaValue): LuaValue {
      if (key is Nil) return Nil
      return entries[key.toKey()] ?: Nil
    }

    fun rawSet(key: LuaValue, value: LuaValue) {
      if (key is Nil) throw LuaRuntimeException("Table index is nil")
      if (key is Num && key.value.isNaN()) throw LuaRuntimeException("Table index is NaN")
      val k = key.toKey()
      if (value is Nil) {
        entries.remove(k)
      } else {
        entries[k] = value
      }
    }

    fun set(key: LuaValue, value: LuaValue) {
      if (isReadonly) throw LuaRuntimeException("Attempt to modify a readonly table")
      rawSet(key, value)
    }

    fun arrayLength(): Int {
      var i = 1
      while (get(Num(i.toDouble())) !is Nil) {
        i++
      }
      return i - 1
    }

    override fun toString(): String = "table: ${Integer.toHexString(System.identityHashCode(this))}"
  }

  class Closure(
    val proto: FunctionProto,
    val upvalues: Array<UpvalueCell>,
    var env: Table,
  ) : LuaValue() {
    override fun toString(): String = "function: ${proto.name}"
  }

  class NativeFunc(
    val name: String,
    val isCClosure: Boolean = true,
    val fn: suspend (LuaVm, List<LuaValue>) -> List<LuaValue>,
  ) : LuaValue() {
    override fun toString(): String = "function: $name"
  }

  class Userdata(
    val typeName: String,
    val data: Any,
    var metatable: Table? = null,
    val onGet: ((String) -> LuaValue)? = null,
    val onSet: ((String, LuaValue) -> Unit)? = null,
  ) : LuaValue() {
    override fun toString(): String = "$typeName: ${data}"
  }

  class Multi(val values: List<LuaValue>) : LuaValue() {
    override fun toString(): String = values.firstOrNull()?.toString() ?: "nil"
  }

  fun isTruthy(): Boolean = this !is Nil && !(this is Bool && !this.value)

  fun single(): LuaValue = if (this is Multi) values.firstOrNull() ?: Nil else this

  fun expand(): List<LuaValue> = if (this is Multi) values else listOf(this)

  fun luaTypeName(): String =
    when (this) {
      is Nil -> "nil"
      is Bool -> "boolean"
      is Num -> "number"
      is Str -> "string"
      is Table -> "table"
      is Closure,
      is NativeFunc -> "function"
      is Userdata -> "userdata"
      is Multi -> single().luaTypeName()
    }

  fun typeofName(): String =
    when (this) {
      is Userdata -> typeName
      is Multi -> single().typeofName()
      else -> luaTypeName()
    }

  fun asDoubleOrNull(): Double? =
    when (this) {
      is Num -> value
      is Str -> value.trim().toDoubleOrNull()
      else -> null
    }

  fun asInt(): Int = floor(asDoubleOrNull() ?: 0.0).toInt()

  fun toKey(): LuaKey =
    when (this) {
      is Nil -> LuaKey.NilKey
      is Bool -> LuaKey.BoolKey(value)
      is Num -> {
        val norm = if (value == -0.0) 0.0 else value
        LuaKey.NumKey(norm)
      }
      is Str -> LuaKey.StrKey(value)
      else -> LuaKey.RefKey(this)
    }
}

sealed interface LuaKey {
  object NilKey : LuaKey
  data class BoolKey(val v: Boolean) : LuaKey
  data class NumKey(val v: Double) : LuaKey
  data class StrKey(val v: String) : LuaKey
  class RefKey(val ref: Any) : LuaKey {
    override fun equals(other: Any?): Boolean = other is RefKey && other.ref === ref
    override fun hashCode(): Int = System.identityHashCode(ref)
  }

  fun toLuaValue(): LuaValue =
    when (this) {
      NilKey -> LuaValue.Nil
      is BoolKey -> LuaValue.Bool(v)
      is NumKey -> LuaValue.Num(v)
      is StrKey -> LuaValue.Str(v)
      is RefKey -> ref as? LuaValue ?: LuaValue.Nil
    }
}
