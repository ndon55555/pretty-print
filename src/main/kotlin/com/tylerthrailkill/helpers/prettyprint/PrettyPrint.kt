package com.tylerthrailkill.helpers.prettyprint

import mu.KotlinLogging
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

private val logger = KotlinLogging.logger {}
private const val DEFAULT_WRAP_WIDTH = 80
var defaultIndentSize = 2
var defaultAppendable: Appendable = System.out

/**
 * Pretty print function.
 *
 * Prints any object in a pretty format for easy debugging/reading
 *
 * @param [obj] the object to pretty print
 * @param [indent] optional param that specifies the number of spaces to use to indent. Defaults to 2.
 * @param [writeTo] optional param that specifies the [Appendable] to output the pretty print to. Defaults appending to `System.out`.
 */
fun pp(obj: Any?, indent: Int = defaultIndentSize, writeTo: Appendable = defaultAppendable) {
    defaultIndentSize = indent
    defaultAppendable = writeTo
    ppAny(obj, mutableSetOf(), mutableSetOf())
    writeLine()
}

/**
 * Inline helper method for printing withing method chains. Simply delegates to [pp]
 *
 * Example:
 *   val foo = op2(op1(bar).pp())
 *
 * @param[T] the object to pretty print
 * @param[indent] optional param that specifies the number of spaces to use to indent. Defaults to 2.
 * @param[writeTo] optional param that specifies the [Appendable] to output the pretty print to. Defaults appending to `System.out`
 */
fun <T> T.pp(indent: Int = defaultIndentSize, writeTo: Appendable = defaultAppendable): T =
    this.also { pp(it, indent, writeTo) }

/**
 * Pretty prints any object. `currentPad` is how much more to indent the contents of a collection.
 * `objectFieldPad` is how much to indent the fields of an object.
 */
private fun ppAny(
    obj: Any?,
    visited: MutableSet<Int>,
    revisited: MutableSet<Int>,
    currentPad: String = ""
) {
    val id = System.identityHashCode(obj)

    if (!isAtomic(obj) && visited.contains(id)) {
        write("cyclic reference detected for $id")
        revisited.add(id)
        return
    }

    if (!isAtomic(obj)) visited.add(id)

    when {
        obj is Iterable<*> -> ppIterable(obj, visited, revisited, currentPad)
        obj is Map<*, *> -> ppMap(obj, visited, revisited, currentPad)
        obj is String -> ppString(obj, currentPad)
        isAtomic(obj) -> ppAtomic(obj)
        obj is Any -> ppPlainObject(obj, visited, revisited, currentPad)
    }

    visited.remove(id)

    if (revisited.contains(id)) {
        write("[\$id=$id]")
        revisited.remove(id)
    }
}

/**
 * TODO
 */
private fun isAtomic(o: Any?): Boolean =
    o == null
            || o is Char || o is Number || o is Boolean || o is BigInteger || o is BigDecimal || o is UUID

/**
 * Pretty prints the contents of the Iterable receiver. The given function is applied to each element. The result
 * of an application to each element is on its own line, separated by a separator. `currentDepth` specifies the
 * indentation level of any closing bracket.
 */
private fun <T> Iterable<T>.ppContents(currentDepth: String, separator: String = "", f: (T, String) -> Unit) {
    val list = this.toMutableList()
    val increasedDepth = deepen(currentDepth)

    if (!list.isEmpty()) {
        f(list.removeAt(0), increasedDepth)
        list.forEach {
            writeLine(separator)
            f(it, increasedDepth)
        }
        writeLine()
    }

    write(currentDepth)
}

/**
 * TODO
 */
private fun ppAtomic(obj: Any?) {
    write(obj.toString())
}

/**
 * Pretty print a plain object.
 */
private fun ppPlainObject(obj: Any, visited: MutableSet<Int>, revisited: MutableSet<Int>, currentDepth: String) {
    val className = obj.javaClass.simpleName

    writeLine("$className(")
    obj.javaClass.declaredFields
        .filterNot { it.isSynthetic }
        .toList()
        .ppContents(currentDepth) { field, pad ->
            field.isAccessible = true
            write("$pad${field.name} = ")
            val fieldValue = field.get(obj)
            logger.debug { "field value is ${fieldValue.javaClass}" }
            ppAny(fieldValue, visited, revisited, pad)
        }
    write(')')
}

/**
 * Pretty print an Iterable.
 */
private fun ppIterable(obj: Iterable<*>, visited: MutableSet<Int>, revisited: MutableSet<Int>, currentDepth: String) {
    writeLine('[')
    obj.ppContents(currentDepth, ",") { element, pad ->
        write(pad)
        ppAny(element, visited, revisited, pad)
    }
    write(']')
}

/**
 * Pretty print a Map.
 */
private fun ppMap(obj: Map<*, *>, visited: MutableSet<Int>, revisited: MutableSet<Int>, currentDepth: String) {
    writeLine('{')
    obj.entries.ppContents(currentDepth, ",") { entry, pad ->
        write(pad)
        ppAny(entry.key, visited, revisited, pad)
        write(" -> ")
        ppAny(entry.value, visited, revisited, pad)
    }
    write('}')
}

/**
 * TODO
 */
private fun ppString(s: String, currentDepth: String) {
    if (s.length > DEFAULT_WRAP_WIDTH) {
        val tripleDoubleQuotes = "\"\"\""
        writeLine(tripleDoubleQuotes)
        s.split(' ').getLines().ppContents(currentDepth) { line, pad ->
            write(pad)
            write(line)
        }
        write(tripleDoubleQuotes)
    } else {
        write("\"$s\"")
    }
}

/**
 * TODO
 */
private tailrec fun List<String>.getLines(acc: MutableList<String> = mutableListOf()): List<String> = when {
    this.isEmpty() -> acc
    else -> {
        val firstLine = this.getFirstLine()
        acc.add(firstLine.joinToString(" "))
        this.subList(firstLine.size, this.size).getLines(acc)
    }
}

/**
 * TODO
 */
private tailrec fun List<String>.getFirstLine(
    wrapWidth: Int = DEFAULT_WRAP_WIDTH,
    acc: MutableList<String> = mutableListOf()
): List<String> = when {
    this.isEmpty() -> acc
    (1 + this.first().length) in wrapWidth..DEFAULT_WRAP_WIDTH -> acc
    (1 + this.first().length) > DEFAULT_WRAP_WIDTH && !acc.isEmpty() -> acc
    else -> {
        acc.add(this.first())
        val spacesLeft = wrapWidth - (1 + this.first().length)
        this.subList(1, this.size).getFirstLine(spacesLeft, acc)
    }
}

/**
 * Writes to the defaultAppendable with a new line and adds logging
 */
private fun writeLine(str: Any? = "") {
    logger.debug { "writing $str" }
    defaultAppendable.append(str.toString()).appendln()
}

/**
 * Writes to the defaultAppendable and adds logging
 */
private fun write(str: Any?) {
    logger.debug { "writing $str" }
    defaultAppendable.append(str.toString())
}

/**
 * TODO
 */
private fun deepen(currentDepth: String, size: Int = defaultIndentSize): String =
    " ".repeat(size) + currentDepth