package com.maddyhome.idea.vim.api

import com.maddyhome.idea.vim.command.CommandState
import com.maddyhome.idea.vim.common.VimMachine
import com.maddyhome.idea.vim.diagnostic.VimLogger
import com.maddyhome.idea.vim.group.TabService
import com.maddyhome.idea.vim.group.VimWindowGroup
import com.maddyhome.idea.vim.helper.VimCommandLineHelper
import com.maddyhome.idea.vim.history.VimHistory
import com.maddyhome.idea.vim.macro.VimMacro
import com.maddyhome.idea.vim.mark.VimMarkGroup
import com.maddyhome.idea.vim.options.OptionService
import com.maddyhome.idea.vim.put.VimPut
import com.maddyhome.idea.vim.register.VimRegisterGroup
import com.maddyhome.idea.vim.undo.VimUndoRedo
import com.maddyhome.idea.vim.vimscript.services.VariableService
import com.maddyhome.idea.vim.yank.VimYankGroup

interface VimInjector {
  val parser: VimStringParser
  val messages: VimMessages
  val registerGroup: VimRegisterGroup
  val registerGroupIfCreated: VimRegisterGroup?
  val processGroup: VimProcessGroup
  val application: VimApplication
  val executionContextManager: ExecutionContextManager
  val digraphGroup: VimDigraphGroup
  val vimMachine: VimMachine
  val enabler: VimEnabler

  // TODO We should somehow state that [OptionServiceImpl] can be used from any implementation
  val optionService: OptionService
  val nativeActionManager: NativeActionManager
  val keyGroup: VimKeyGroup
  val markGroup: VimMarkGroup
  val visualMotionGroup: VimVisualMotionGroup
  fun commandStateFor(editor: VimEditor): CommandState
  val engineEditorHelper: EngineEditorHelper
  val editorGroup: VimEditorGroup
  val commandGroup: VimCommandGroup
  val changeGroup: VimChangeGroup
  val actionExecutor: VimActionExecutor
  val exEntryPanel: ExEntryPanel
  val exOutputPanel: VimExOutputPanelService
  val clipboardManager: VimClipboardManager
  val historyGroup: VimHistory
  val extensionRegistrator: VimExtensionRegistrator
  val tabService: TabService
  val regexpService: VimRegexpService

  val searchHelper: VimSearchHelper
  val motion: VimMotionGroup
  val lookupManager: VimLookupManager
  val templateManager: VimTemplateManager
  val searchGroup: VimSearchGroup
  val statisticsService: VimStatistics
  val put: VimPut
  val window: VimWindowGroup
  val yank: VimYankGroup
  val file: VimFile
  val macro: VimMacro
  val undo: VimUndoRedo
  val commandLineHelper: VimCommandLineHelper

  val vimscriptExecutor: VimscriptExecutor
  val vimscriptParser: VimscriptParser
  val variableService: VariableService
  val functionService: VimscriptFunctionService
  val vimrcFileState: VimrcFileState

  /**
   * Please use vimLogger() function
   */
  fun <T : Any> getLogger(clazz: Class<T>): VimLogger
}

lateinit var injector: VimInjector