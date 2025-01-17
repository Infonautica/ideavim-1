/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */

package com.maddyhome.idea.vim.key

import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.diagnostic.trace
import com.maddyhome.idea.vim.diagnostic.vimLogger
import javax.swing.KeyStroke

/**
 * This thing is used for keeping keys from ToKeys mappings and macros.
 * Previously, the mapping was directly sent to the [KeyHandler]. However, in this case it was impossible to
 *   pass the key to modal entry (for getChar function).
 * Original vim uses a typeahead buffer for that, but it's not implemented for IdeaVim and this mappingStack
 *   solves an issus of passing a keystroke to modal entry.
 *   However, some more advanced solution may be necessary in the future.
 */
public class KeyStack {
  private val stack = ArrayDeque<Frame>()

  public fun hasStroke(): Boolean {
    return stack.isNotEmpty() && stack.first().hasStroke()
  }

  public fun feedSomeStroke(): KeyStroke? {
    stack.forEach {
      if (it.hasStroke()) {
        return it.feed()
      }
    }
    return null
  }

  public fun feedStroke(): KeyStroke {
    val frame = stack.first()
    val key = frame.feed()
    return key
  }

  public fun addKeys(keyStrokes: List<KeyStroke>) {
    LOG.trace { "Got new keys to key stack: $keyStrokes" }
    stack.addFirst(Frame(keyStrokes))
  }

  public fun removeFirst() {
    if (stack.isNotEmpty()) {
      stack.removeFirst()
    }
  }

  public fun resetFirst() {
    if (stack.isNotEmpty()) {
      stack.first().resetPointer()
    }
  }

  public fun dump(): String {
    return buildString {
      this.appendLine("KeyStack:")
      this.appendLine("Stack size: ${stack.size}")
      stack.forEachIndexed { index, frame ->
        this.appendLine("Frame $index:")
        this.appendLine("Keys: ${frame.keys}")
        this.appendLine("Pointer: ${frame.pointer}")
        this.appendLine()
      }
    }
  }

  private companion object {
    private val LOG = vimLogger<KeyStack>()
  }
}

private class Frame(
  val keys: List<KeyStroke>,
  var pointer: Int = 0,
) {
  fun hasStroke(): Boolean {
    return pointer < keys.size
  }

  fun feed(): KeyStroke {
    val key = keys[pointer]
    pointer += 1
    return key
  }

  fun resetPointer() {
    pointer = 0
  }

  override fun toString(): String {
    return "" + pointer + " | " + injector.parser.toKeyNotation(keys)
  }
}
