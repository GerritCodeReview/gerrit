// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.client.diff;

import static java.lang.Double.POSITIVE_INFINITY;

import com.google.gerrit.client.DiffObject;
import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.diff.LineMapper.LineOnOtherInfo;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.projects.ConfigInfoCache;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DiffView;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ImageResourceRenderer;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import java.util.Collections;
import java.util.List;
import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineHandle;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.KeyMap;
import net.codemirror.lib.Pos;

public class SideBySide extends DiffScreen {
  interface Binder extends UiBinder<FlowPanel, SideBySide> {}

  private static final Binder uiBinder = GWT.create(Binder.class);
  private static final String LINE_NUMBER_CLASSNAME = "CodeMirror-linenumber";

  @UiField(provided = true)
  SideBySideTable diffTable;

  private CodeMirror cmA;
  private CodeMirror cmB;

  private ScrollSynchronizer scrollSynchronizer;

  private SideBySideChunkManager chunkManager;
  private SideBySideCommentManager commentManager;

  public SideBySide(
      DiffObject base, DiffObject revision, String path, DisplaySide startSide, int startLine) {
    super(base, revision, path, startSide, startLine, DiffView.SIDE_BY_SIDE);

    diffTable = new SideBySideTable(this, base, revision, path);
    add(uiBinder.createAndBindUi(this));
    addDomHandler(GlobalKey.STOP_PROPAGATION, KeyPressEvent.getType());
  }

  @Override
  ScreenLoadCallback<ConfigInfoCache.Entry> getScreenLoadCallback(
      final CommentsCollections comments) {
    return new ScreenLoadCallback<ConfigInfoCache.Entry>(SideBySide.this) {
      @Override
      protected void preDisplay(ConfigInfoCache.Entry result) {
        commentManager =
            new SideBySideCommentManager(
                SideBySide.this,
                base,
                revision,
                path,
                result.getCommentLinkProcessor(),
                getChangeStatus().isOpen());
        setTheme(result.getTheme());
        display(comments);
        header.setupPrevNextFiles(comments);
      }
    };
  }

  @Override
  public void onShowView() {
    super.onShowView();

    operation(
        new Runnable() {
          @Override
          public void run() {
            resizeCodeMirror();
            chunkManager.adjustPadding();
            cmA.refresh();
            cmB.refresh();
          }
        });
    setLineLength(Patch.COMMIT_MSG.equals(path) ? 72 : prefs.lineLength());
    diffTable.refresh();

    if (getStartLine() == 0) {
      DiffChunkInfo d = chunkManager.getFirst();
      if (d != null) {
        if (d.isEdit() && d.getSide() == DisplaySide.A) {
          setStartSide(DisplaySide.B);
          setStartLine(lineOnOther(d.getSide(), d.getStart()).getLine() + 1);
        } else {
          setStartSide(d.getSide());
          setStartLine(d.getStart() + 1);
        }
      }
    }
    if (getStartSide() != null && getStartLine() > 0) {
      CodeMirror cm = getCmFromSide(getStartSide());
      cm.scrollToLine(getStartLine() - 1);
      cm.focus();
    } else {
      cmA.setCursor(Pos.create(0));
      cmA.focus();
    }
    if (Gerrit.isSignedIn() && prefs.autoReview()) {
      header.autoReview();
    }
    prefetchNextFile();
  }

  @Override
  void registerCmEvents(final CodeMirror cm) {
    super.registerCmEvents(cm);

    KeyMap keyMap =
        KeyMap.create()
            .on("Shift-A", diffTable.toggleA())
            .on("Shift-Left", moveCursorToSide(cm, DisplaySide.A))
            .on("Shift-Right", moveCursorToSide(cm, DisplaySide.B));
    cm.addKeyMap(keyMap);
    maybeRegisterRenderEntireFileKeyMap(cm);
  }

  @Override
  public void registerKeys() {
    super.registerKeys();

    getKeysNavigation()
        .add(
            new NoOpKeyCommand(KeyCommand.M_SHIFT, KeyCodes.KEY_LEFT, PatchUtil.C.focusSideA()),
            new NoOpKeyCommand(KeyCommand.M_SHIFT, KeyCodes.KEY_RIGHT, PatchUtil.C.focusSideB()));
    getKeysAction()
        .add(
            new KeyCommand(KeyCommand.M_SHIFT, 'a', PatchUtil.C.toggleSideA()) {
              @Override
              public void onKeyPress(KeyPressEvent event) {
                diffTable.toggleA().run();
              }
            });

    registerHandlers();
  }

  @Override
  FocusHandler getFocusHandler() {
    return new FocusHandler() {
      @Override
      public void onFocus(FocusEvent event) {
        cmB.focus();
      }
    };
  }

  private void display(final CommentsCollections comments) {
    final DiffInfo diff = getDiff();
    setThemeStyles(prefs.theme().isDark());
    setShowIntraline(prefs.intralineDifference());
    if (prefs.showLineNumbers()) {
      diffTable.addStyleName(Resources.I.diffTableStyle().showLineNumbers());
    }

    cmA = newCm(diff.metaA(), diff.textA(), diffTable.cmA);
    cmB = newCm(diff.metaB(), diff.textB(), diffTable.cmB);

    getDiffTable()
        .setUpBlameIconA(
            cmA,
            base.isBaseOrAutoMerge(),
            base.isBaseOrAutoMerge() ? revision : base.asPatchSetId(),
            path);
    getDiffTable().setUpBlameIconB(cmB, revision, path);

    cmA.extras().side(DisplaySide.A);
    cmB.extras().side(DisplaySide.B);
    setShowTabs(prefs.showTabs());

    chunkManager = new SideBySideChunkManager(this, cmA, cmB, diffTable.scrollbar);

    operation(
        new Runnable() {
          @Override
          public void run() {
            // Estimate initial CodeMirror height, fixed up in onShowView.
            int height = Window.getClientHeight() - (Gerrit.getHeaderFooterHeight() + 18);
            cmA.setHeight(height);
            cmB.setHeight(height);

            render(diff);
            commentManager.render(comments, prefs.expandAllComments());
            skipManager.render(prefs.context(), diff);
          }
        });

    registerCmEvents(cmA);
    registerCmEvents(cmB);
    scrollSynchronizer = new ScrollSynchronizer(diffTable, cmA, cmB, chunkManager.lineMapper);

    setPrefsAction(new PreferencesAction(this, prefs));
    header.init(getPrefsAction(), getUnifiedDiffLink(), diff.sideBySideWebLinks());
    scrollSynchronizer.setAutoHideDiffTableHeader(prefs.autoHideDiffTableHeader());

    setupSyntaxHighlighting();
  }

  private List<InlineHyperlink> getUnifiedDiffLink() {
    InlineHyperlink toUnifiedDiffLink = new InlineHyperlink();
    toUnifiedDiffLink.setHTML(new ImageResourceRenderer().render(Gerrit.RESOURCES.unifiedDiff()));
    toUnifiedDiffLink.setTargetHistoryToken(Dispatcher.toUnified(base, revision, path));
    toUnifiedDiffLink.setTitle(PatchUtil.C.unifiedDiff());
    return Collections.singletonList(toUnifiedDiffLink);
  }

  @Override
  CodeMirror newCm(DiffInfo.FileMeta meta, String contents, Element parent) {
    return CodeMirror.create(
        parent,
        Configuration.create()
            .set("cursorBlinkRate", prefs.cursorBlinkRate())
            .set("cursorHeight", 0.85)
            .set("inputStyle", "textarea")
            .set("keyMap", "vim_ro")
            .set("lineNumbers", prefs.showLineNumbers())
            .set("matchBrackets", prefs.matchBrackets())
            .set("lineWrapping", prefs.lineWrapping())
            .set("mode", getFileSize() == FileSize.SMALL ? getContentType(meta) : null)
            .set("readOnly", true)
            .set("scrollbarStyle", "overlay")
            .set("showTrailingSpace", prefs.showWhitespaceErrors())
            .set("styleSelectedText", true)
            .set("tabSize", prefs.tabSize())
            .set("theme", prefs.theme().name().toLowerCase())
            .set("value", meta != null ? contents : "")
            .set("viewportMargin", renderEntireFile() ? POSITIVE_INFINITY : 10));
  }

  @Override
  void setShowLineNumbers(boolean b) {
    super.setShowLineNumbers(b);

    cmA.setOption("lineNumbers", b);
    cmB.setOption("lineNumbers", b);
  }

  @Override
  void setSyntaxHighlighting(boolean b) {
    final DiffInfo diff = getDiff();
    if (b) {
      injectMode(
          diff,
          new AsyncCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
              if (prefs.syntaxHighlighting()) {
                cmA.setOption("mode", getContentType(diff.metaA()));
                cmB.setOption("mode", getContentType(diff.metaB()));
              }
            }

            @Override
            public void onFailure(Throwable caught) {
              prefs.syntaxHighlighting(false);
            }
          });
    } else {
      cmA.setOption("mode", (String) null);
      cmB.setOption("mode", (String) null);
    }
  }

  @Override
  void setAutoHideDiffHeader(boolean hide) {
    scrollSynchronizer.setAutoHideDiffTableHeader(hide);
  }

  CodeMirror otherCm(CodeMirror me) {
    return me == cmA ? cmB : cmA;
  }

  @Override
  CodeMirror getCmFromSide(DisplaySide side) {
    return side == DisplaySide.A ? cmA : cmB;
  }

  @Override
  int getCmLine(int line, DisplaySide side) {
    return line;
  }

  @Override
  Runnable updateActiveLine(final CodeMirror cm) {
    final CodeMirror other = otherCm(cm);
    return new Runnable() {
      @Override
      public void run() {
        // The rendering of active lines has to be deferred. Reflow
        // caused by adding and removing styles chokes Firefox when arrow
        // key (or j/k) is held down. Performance on Chrome is fine
        // without the deferral.
        //
        Scheduler.get()
            .scheduleDeferred(
                new ScheduledCommand() {
                  @Override
                  public void execute() {
                    operation(
                        new Runnable() {
                          @Override
                          public void run() {
                            LineHandle handle =
                                cm.getLineHandleVisualStart(cm.getCursor("end").line());
                            if (!cm.extras().activeLine(handle)) {
                              return;
                            }

                            LineOnOtherInfo info = lineOnOther(cm.side(), cm.getLineNumber(handle));
                            if (info.isAligned()) {
                              other.extras().activeLine(other.getLineHandle(info.getLine()));
                            } else {
                              other.extras().clearActiveLine();
                            }
                          }
                        });
                  }
                });
      }
    };
  }

  private Runnable moveCursorToSide(final CodeMirror cmSrc, DisplaySide sideDst) {
    final CodeMirror cmDst = getCmFromSide(sideDst);
    if (cmDst == cmSrc) {
      return new Runnable() {
        @Override
        public void run() {}
      };
    }

    final DisplaySide sideSrc = cmSrc.side();
    return new Runnable() {
      @Override
      public void run() {
        if (cmSrc.extras().hasActiveLine()) {
          cmDst.setCursor(
              Pos.create(
                  lineOnOther(sideSrc, cmSrc.getLineNumber(cmSrc.extras().activeLine()))
                      .getLine()));
        }
        cmDst.focus();
      }
    };
  }

  void syncScroll(DisplaySide masterSide) {
    if (scrollSynchronizer != null) {
      scrollSynchronizer.syncScroll(masterSide);
    }
  }

  @Override
  void operation(final Runnable apply) {
    cmA.operation(
        new Runnable() {
          @Override
          public void run() {
            cmB.operation(
                new Runnable() {
                  @Override
                  public void run() {
                    apply.run();
                  }
                });
          }
        });
  }

  @Override
  CodeMirror[] getCms() {
    return new CodeMirror[] {cmA, cmB};
  }

  @Override
  SideBySideTable getDiffTable() {
    return diffTable;
  }

  @Override
  SideBySideChunkManager getChunkManager() {
    return chunkManager;
  }

  @Override
  SideBySideCommentManager getCommentManager() {
    return commentManager;
  }

  @Override
  boolean isSideBySide() {
    return true;
  }

  @Override
  String getLineNumberClassName() {
    return LINE_NUMBER_CLASSNAME;
  }
}
