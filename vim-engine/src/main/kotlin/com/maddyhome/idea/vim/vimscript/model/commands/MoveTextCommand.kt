/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */

package com.maddyhome.idea.vim.vimscript.model.commands

import com.intellij.vim.annotations.ExCommand
import com.maddyhome.idea.vim.api.BufferPosition
import com.maddyhome.idea.vim.api.ExecutionContext
import com.maddyhome.idea.vim.api.ImmutableVimCaret
import com.maddyhome.idea.vim.api.SelectionInfo
import com.maddyhome.idea.vim.api.VimCaret
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.api.VimMarkService
import com.maddyhome.idea.vim.api.getText
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.command.OperatorArguments
import com.maddyhome.idea.vim.state.mode.SelectionType
import com.maddyhome.idea.vim.common.TextRange
import com.maddyhome.idea.vim.ex.ExException
import com.maddyhome.idea.vim.ex.InvalidRangeException
import com.maddyhome.idea.vim.ex.ranges.LineRange
import com.maddyhome.idea.vim.ex.ranges.Ranges
import com.maddyhome.idea.vim.helper.Msg
import com.maddyhome.idea.vim.mark.Mark
import com.maddyhome.idea.vim.mark.VimMark
import com.maddyhome.idea.vim.put.PutData
import com.maddyhome.idea.vim.vimscript.model.ExecutionResult
import kotlin.math.min

/**
 * see "h :move"
 */
@ExCommand(command = "m[ove]")
public data class MoveTextCommand(val ranges: Ranges, val argument: String) : Command.SingleExecution(ranges, argument) {
  override val argFlags: CommandHandlerFlags = flags(RangeFlag.RANGE_OPTIONAL, ArgumentFlag.ARGUMENT_REQUIRED, Access.WRITABLE)

  @Throws(ExException::class)
  override fun processCommand(editor: VimEditor, context: ExecutionContext, operatorArguments: OperatorArguments): ExecutionResult {
    val caretCount = editor.nativeCarets().size
    if (caretCount > 1) {
      throw ExException("Move command supported only for one caret at the moment")
    }
    val caret = editor.primaryCaret()
    val caretPosition = caret.getBufferPosition()

    val goToLineCommand = injector.vimscriptParser.parseCommand(argument) ?: throw ExException("E16: Invalid range")

    val range = getTextRange(editor, caret, false)

    /*
    FIXME: see VIM-2884. It's absolutely not the best way to resolve this bug
     */
    caret.moveToOffset(range.startOffset)

    val lineRange = getLineRange(editor, caret)
    val line = min(editor.fileSize().toInt(), normalizeLine(editor, caret, goToLineCommand, lineRange))
    val linesMoved = lineRange.endLine - lineRange.startLine + 1
    if (line < -1 || line + linesMoved >= editor.lineCount()) {
      caret.moveToBufferPosition(caretPosition)
      throw ExException("E16: Invalid range")
    }
    val shift = line + 1 - editor.offsetToBufferPosition(range.startOffset).line

    val localMarks = injector.markService.getAllLocalMarks(caret)
      .filter { range.contains(it.offset(editor)) }
      .filter { it.key != VimMarkService.SELECTION_START_MARK && it.key != VimMarkService.SELECTION_END_MARK }
      .toSet()
    val globalMarks = injector.markService.getGlobalMarks(editor)
      .filter { range.contains(it.offset(editor)) }
      .map { Pair(it, it.line) } // we save logical line because it will be cleared by Platform after text deletion
      .toSet()
    val lastSelectionInfo = caret.lastSelectionInfo
    val selectionStartOffset = lastSelectionInfo.start?.let { editor.bufferPositionToOffset(it) }
    val selectionEndOffset = lastSelectionInfo.end?.let { editor.bufferPositionToOffset(it) }

    val text = editor.getText(range)
    val textData = PutData.TextData(text, SelectionType.LINE_WISE, emptyList(), null)

    val dropNewLineInEnd = (line + linesMoved == editor.lineCount() - 1 && text.last() == '\n') ||
      (lineRange.endLine == editor.lineCount() - 1)

    editor.deleteString(range)
    val putData = if (line == -1) {
      caret.moveToOffset(0)
      PutData(textData, null, 1, insertTextBeforeCaret = true, rawIndent = true, caretAfterInsertedText = false)
    } else {
      PutData(textData, null, 1, insertTextBeforeCaret = false, rawIndent = true, caretAfterInsertedText = false, putToLine = line)
    }
    injector.put.putTextForCaret(editor, caret, context, putData)

    if (dropNewLineInEnd) {
      assert(editor.text().last() == '\n')
      editor.deleteString(TextRange(editor.text().length - 1, editor.text().length))
    }

    globalMarks.forEach { shiftGlobalMark(editor, it, shift) }
    localMarks.forEach { shiftLocalMark(caret, it, shift) }
    shiftSelectionInfo(caret, selectionStartOffset, selectionEndOffset, lastSelectionInfo, shift, range)

    val newCaretPosition = shiftBufferPosition(caretPosition, shift)
    caret.moveToBufferPosition(newCaretPosition)

    return ExecutionResult.Success
  }

  private fun shiftGlobalMark(editor: VimEditor, markAndLine: Pair<Mark, Int>, shift: Int) {
    val newOffset = editor.bufferPositionToOffset(BufferPosition(markAndLine.second + shift, markAndLine.first.col))
    injector.markService.setGlobalMark(editor, markAndLine.first.key, newOffset)
  }

  private fun shiftLocalMark(caret: VimCaret, mark: Mark, shift: Int) {
    val editor = caret.editor
    val path = editor.getPath() ?: return
    val mark = VimMark(mark.key, mark.line + shift, mark.col, path, editor.extractProtocol())
    injector.markService.setMark(caret, mark)
  }

  private fun shiftSelectionInfo(
    caret: ImmutableVimCaret,
    startOffset: Int?,
    endOffset: Int?,
    selectionInfo: SelectionInfo,
    shift: Int,
    range: TextRange,
  ) {
    var newStartPosition = selectionInfo.start
    if (startOffset != null && selectionInfo.start != null && range.contains(startOffset)) {
      newStartPosition = shiftBufferPosition(selectionInfo.start!!, shift)
    }

    var newEndPosition = selectionInfo.end
    if (endOffset != null && selectionInfo.end != null && range.contains(endOffset)) {
      newEndPosition = shiftBufferPosition(selectionInfo.end!!, shift)
    }

    if (newStartPosition != selectionInfo.start || newEndPosition != selectionInfo.end) {
      caret.lastSelectionInfo = SelectionInfo(newStartPosition, newEndPosition, selectionInfo.selectionType)
    }
  }

  private fun shiftBufferPosition(bufferPosition: BufferPosition, shift: Int): BufferPosition {
    return BufferPosition(bufferPosition.line + shift, bufferPosition.column, bufferPosition.leansForward)
  }

  @Throws
  private fun normalizeLine(
    editor: VimEditor,
    caret: VimCaret,
    command: Command,
    lineRange: LineRange,
  ): Int {
    var line = command.commandRanges.getFirstLine(editor, caret)
    val adj = lineRange.endLine - lineRange.startLine + 1
    if (line >= lineRange.endLine) {
      line -= adj
    } else if (line >= lineRange.startLine) throw InvalidRangeException(injector.messages.message(Msg.e_backrange))

    return line
  }
}
