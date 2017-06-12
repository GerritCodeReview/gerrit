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
import com.google.gerrit.client.diff.UnifiedChunkManager.LineRegionInfo;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.projects.ConfigInfoCache;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DiffView;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ImageResourceRenderer;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import java.util.Collections;
import java.util.List;
import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineHandle;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.Pos;
import net.codemirror.lib.ScrollInfo;

public class Unified extends DiffScreen {
  interface Binder extends UiBinder<FlowPanel, Unified> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField(provided = true)
  UnifiedTable diffTable;

  private CodeMirror cm;

  private UnifiedChunkManager chunkManager;
  private UnifiedCommentManager commentManager;

  private boolean autoHideDiffTableHeader;

  public Unified(
      DiffObject base, DiffObject revision, String path, DisplaySide startSide, int startLine) {
    super(base, revision, path, startSide, startLine, DiffView.UNIFIED_DIFF);

    diffTable = new UnifiedTable(this, base, revision, path);
    add(uiBinder.createAndBindUi(this));
    addDomHandler(GlobalKey.STOP_PROPAGATION, KeyPressEvent.getType());
  }

  @Override
  ScreenLoadCallback<ConfigInfoCache.Entry> getScreenLoadCallback(
      final CommentsCollections comments) {
    return new ScreenLoadCallback<ConfigInfoCache.Entry>(Unified.this) {
      @Override
      protected void preDisplay(ConfigInfoCache.Entry result) {
        commentManager =
            new UnifiedCommentManager(
                Unified.this,
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
            cm.refresh();
          }
        });
    setLineLength(Patch.COMMIT_MSG.equals(path) ? 72 : prefs.lineLength());
    diffTable.refresh();

    if (getStartLine() == 0) {
      DiffChunkInfo d = chunkManager.getFirst();
      if (d != null) {
        if (d.isEdit() && d.getSide() == DisplaySide.A) {
          setStartSide(DisplaySide.B);
        } else {
          setStartSide(d.getSide());
        }
        setStartLine(chunkManager.getCmLine(d.getStart(), d.getSide()) + 1);
      }
    }
    if (getStartSide() != null && getStartLine() > 0) {
      cm.scrollToLine(chunkManager.getCmLine(getStartLine() - 1, getStartSide()));
      cm.focus();
    } else {
      cm.setCursor(Pos.create(0));
      cm.focus();
    }
    if (Gerrit.isSignedIn() && prefs.autoReview()) {
      header.autoReview();
    }
    prefetchNextFile();
  }

  @Override
  void registerCmEvents(final CodeMirror cm) {
    super.registerCmEvents(cm);

    cm.on(
        "scroll",
        new Runnable() {
          @Override
          public void run() {
            ScrollInfo si = cm.getScrollInfo();
            if (autoHideDiffTableHeader) {
              updateDiffTableHeader(si);
            }
          }
        });
    maybeRegisterRenderEntireFileKeyMap(cm);
  }

  @Override
  public void registerKeys() {
    super.registerKeys();

    registerHandlers();
  }

  @Override
  FocusHandler getFocusHandler() {
    return new FocusHandler() {
      @Override
      public void onFocus(FocusEvent event) {
        cm.focus();
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

    cm =
        newCm(diff.metaA() == null ? diff.metaB() : diff.metaA(), diff.textUnified(), diffTable.cm);
    setShowTabs(prefs.showTabs());

    chunkManager = new UnifiedChunkManager(this, cm, diffTable.scrollbar);

    operation(
        new Runnable() {
          @Override
          public void run() {
            // Estimate initial CodeMirror height, fixed up in onShowView.
            int height = Window.getClientHeight() - (Gerrit.getHeaderFooterHeight() + 18);
            cm.setHeight(height);

            render(diff);
            commentManager.render(comments, prefs.expandAllComments());
            skipManager.render(prefs.context(), diff);
          }
        });

    registerCmEvents(cm);

    setPrefsAction(new PreferencesAction(this, prefs));
    header.init(getPrefsAction(), getSideBySideDiffLink(), diff.unifiedWebLinks());
    setAutoHideDiffHeader(prefs.autoHideDiffTableHeader());

    setupSyntaxHighlighting();
  }

  private List<InlineHyperlink> getSideBySideDiffLink() {
    InlineHyperlink toSideBySideDiffLink = new InlineHyperlink();
    toSideBySideDiffLink.setHTML(
        new ImageResourceRenderer().render(Gerrit.RESOURCES.sideBySideDiff()));
    toSideBySideDiffLink.setTargetHistoryToken(Dispatcher.toSideBySide(base, revision, path));
    toSideBySideDiffLink.setTitle(PatchUtil.C.sideBySideDiff());
    return Collections.singletonList(toSideBySideDiffLink);
  }

  @Override
  CodeMirror newCm(DiffInfo.FileMeta meta, String contents, Element parent) {
    JsArrayString gutters = JavaScriptObject.createArray().cast();
    gutters.push(UnifiedTable.style.lineNumbersLeft());
    gutters.push(UnifiedTable.style.lineNumbersRight());

    return CodeMirror.create(
        parent,
        Configuration.create()
            .set("cursorBlinkRate", prefs.cursorBlinkRate())
            .set("cursorHeight", 0.85)
            .set("gutters", gutters)
            .set("inputStyle", "textarea")
            .set("keyMap", "vim_ro")
            .set("lineNumbers", false)
            .set("lineWrapping", prefs.lineWrapping())
            .set("matchBrackets", prefs.matchBrackets())
            .set("mode", getFileSize() == FileSize.SMALL ? getContentType(meta) : null)
            .set("readOnly", true)
            .set("scrollbarStyle", "overlay")
            .set("styleSelectedText", true)
            .set("showTrailingSpace", prefs.showWhitespaceErrors())
            .set("tabSize", prefs.tabSize())
            .set("theme", prefs.theme().name().toLowerCase())
            .set("value", meta != null ? contents : "")
            .set("viewportMargin", renderEntireFile() ? POSITIVE_INFINITY : 10));
  }

  @Override
  void setShowLineNumbers(boolean b) {
    super.setShowLineNumbers(b);

    cm.refresh();
  }

  private void setLineNumber(DisplaySide side, int cmLine, Integer line, String styleName) {
    SafeHtml html = SafeHtml.asis(line != null ? line.toString() : "&nbsp;");
    InlineHTML gutter = new InlineHTML(html);
    diffTable.add(gutter);
    gutter.setStyleName(styleName);
    cm.setGutterMarker(
        cmLine,
        side == DisplaySide.A
            ? UnifiedTable.style.lineNumbersLeft()
            : UnifiedTable.style.lineNumbersRight(),
        gutter.getElement());
  }

  void setLineNumber(DisplaySide side, int cmLine, int line) {
    setLineNumber(side, cmLine, line, UnifiedTable.style.unifiedLineNumber());
  }

  void setLineNumberEmpty(DisplaySide side, int cmLine) {
    setLineNumber(side, cmLine, null, UnifiedTable.style.unifiedLineNumberEmpty());
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
                cm.setOption(
                    "mode", getContentType(diff.metaA() == null ? diff.metaB() : diff.metaA()));
              }
            }

            @Override
            public void onFailure(Throwable caught) {
              prefs.syntaxHighlighting(false);
            }
          });
    } else {
      cm.setOption("mode", (String) null);
    }
  }

  @Override
  void setAutoHideDiffHeader(boolean autoHide) {
    if (autoHide) {
      updateDiffTableHeader(cm.getScrollInfo());
    } else {
      diffTable.setHeaderVisible(true);
    }
    autoHideDiffTableHeader = autoHide;
  }

  private void updateDiffTableHeader(ScrollInfo si) {
    if (si.top() == 0) {
      diffTable.setHeaderVisible(true);
    } else if (si.top() > 0.5 * si.clientHeight()) {
      diffTable.setHeaderVisible(false);
    }
  }

  @Override
  Runnable updateActiveLine(final CodeMirror cm) {
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
                    LineHandle handle = cm.getLineHandleVisualStart(cm.getCursor("end").line());
                    cm.extras().activeLine(handle);
                  }
                });
      }
    };
  }

  @Override
  CodeMirror getCmFromSide(DisplaySide side) {
    return cm;
  }

  @Override
  int getCmLine(int line, DisplaySide side) {
    return chunkManager.getCmLine(line, side);
  }

  LineRegionInfo getLineRegionInfoFromCmLine(int cmLine) {
    return chunkManager.getLineRegionInfoFromCmLine(cmLine);
  }

  @Override
  void operation(final Runnable apply) {
    cm.operation(
        new Runnable() {
          @Override
          public void run() {
            apply.run();
          }
        });
  }

  @Override
  CodeMirror[] getCms() {
    return new CodeMirror[] {cm};
  }

  @Override
  UnifiedTable getDiffTable() {
    return diffTable;
  }

  @Override
  UnifiedChunkManager getChunkManager() {
    return chunkManager;
  }

  @Override
  UnifiedCommentManager getCommentManager() {
    return commentManager;
  }

  @Override
  boolean isSideBySide() {
    return false;
  }

  @Override
  String getLineNumberClassName() {
    return UnifiedTable.style.unifiedLineNumber();
  }
}
