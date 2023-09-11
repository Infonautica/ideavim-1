/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */

package com.maddyhome.idea.vim.regexp

import com.maddyhome.idea.vim.api.BufferPosition
import com.maddyhome.idea.vim.api.VimCaret
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.common.Offset
import com.maddyhome.idea.vim.common.TextRange
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import kotlin.test.fail

internal object VimRegexTestUtils {

  const val START: String = "<start>"
  const val END: String = "<end>"
  const val CARET: String = "<caret>"
  const val VISUAL_START = "<vstart>"
  const val VISUAL_END = "<vend>"

  fun mockEditorFromText(text: CharSequence) : VimEditor {
    val textWithoutRangeTags = getTextWithoutRangeTags(text)

    val carets = mutableListOf<VimCaret>()
    val textWithCarets = getTextWithoutVisualTags(textWithoutRangeTags)
    val textWithVisuals = getTextWithoutCaretTags(textWithoutRangeTags)
    val visualStart = textWithVisuals.indexOf(VISUAL_START)
    val visualEnd = if (visualStart >= 0) textWithVisuals.indexOf(VISUAL_END) - VISUAL_START.length
                    else -1

    var currentIndex = textWithCarets.indexOf(CARET)
    var offset = 0

    while (currentIndex != -1) {
      carets.add(mockCaret(currentIndex - offset, Pair(visualStart, visualEnd)))
      currentIndex = textWithCarets.indexOf(CARET, currentIndex + CARET.length)
      offset += CARET.length
    }

    return mockEditor(getTextWithoutCaretTags(textWithCarets), carets)
  }

  private fun mockEditor(text: CharSequence, carets: List<VimCaret> = emptyList()) : VimEditor {
    val lines = text.split("\n").map { it + "\n" }

    val editorMock = Mockito.mock<VimEditor>()
    mockEditorText(editorMock, text)
    mockEditorOffsetToBufferPosition(editorMock, lines)

    if (carets.isEmpty()) {
      // if no carets are provided, place on at the start of the text
      val caret = mockCaret(0, Pair(-1, -1))
      whenever(editorMock.carets()).thenReturn(listOf(caret))
      whenever(editorMock.currentCaret()).thenReturn(caret)
    } else {
      whenever(editorMock.carets()).thenReturn(carets)
      whenever(editorMock.currentCaret()).thenReturn(carets.first())
    }

    return editorMock
  }

  private fun mockCaret(caretOffset: Int, visualOffset: Pair<Int, Int>): VimCaret {
    val caretMock = Mockito.mock<VimCaret>()
    whenever(caretMock.offset).thenReturn(Offset(caretOffset))
    whenever(caretMock.selectionStart).thenReturn(visualOffset.first)
    whenever(caretMock.selectionEnd).thenReturn(visualOffset.second)
    return caretMock
  }

  private fun getTextWithoutCaretTags(text: CharSequence): CharSequence {
    return text.replace(CARET.toRegex(), "")
  }

  private fun getTextWithoutVisualTags(text: CharSequence): CharSequence {
    return text.replace("$VISUAL_START|$VISUAL_END".toRegex(), "")
  }

  private fun getTextWithoutEditorTags(text: CharSequence): CharSequence {
    return getTextWithoutVisualTags(
      getTextWithoutCaretTags(
        text
      )
    )
  }

  private fun mockEditorText(editor: VimEditor, text: CharSequence) {
    whenever(editor.text()).thenReturn(text)
  }

  fun getMatchRanges(text: CharSequence): List<TextRange> {
    val textWithoutEditorTags = getTextWithoutEditorTags(text)
    val matchRanges = mutableListOf<TextRange>()
    var offset = 0
    var oldOffset = 0

    var startIndex = textWithoutEditorTags.indexOf(START)
    while (startIndex != -1) {
      val endIndex = textWithoutEditorTags.indexOf(END, startIndex + START.length)
      if (endIndex != -1) {
        offset += START.length
        matchRanges.add(TextRange(startIndex - oldOffset, endIndex - offset))
        startIndex = textWithoutEditorTags.indexOf(START, endIndex + END.length)
        offset += END.length
        oldOffset = offset
      } else {
        fail("Please provide the same number of START and END tags!")
      }
    }
    return matchRanges
  }

  private fun getTextWithoutRangeTags(text: CharSequence): CharSequence {
    val newText = StringBuilder(text)
    var index = newText.indexOf(START)
    while (index != -1) {
      newText.delete(index, index + START.length)
      index = newText.indexOf(START, index)
    }

    index = newText.indexOf(END)
    while (index != -1) {
      newText.delete(index, index + END.length)
      index = newText.indexOf(END, index)
    }

    return newText
  }

  private fun mockEditorOffsetToBufferPosition(editor: VimEditor, lines: List<String>) {
    whenever(editor.offsetToBufferPosition(Mockito.anyInt())).thenAnswer { invocation ->
      val offset = invocation.arguments[0] as Int
      var lineCounter = 0
      var currentOffset = 0

      while (lineCounter < lines.size && currentOffset + lines[lineCounter].length <= offset) {
        currentOffset += lines[lineCounter].length
        lineCounter++
      }

      if (lineCounter < lines.size) {
        val column = offset - currentOffset
        BufferPosition(lineCounter, column)
      } else {
        BufferPosition(-1, -1)
      }
    }
  }
}