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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.JumpKeys;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.changes.ChangeList;
import com.google.gerrit.client.diff.LineMapper.LineOnOtherInfo;
import com.google.gerrit.client.diff.UnifiedChunkManager.LinePair;
import com.google.gerrit.client.projects.ConfigInfoCache;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.extensions.common.ListChangesOption;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.ShowHelpCommand;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.GutterClickHandler;
import net.codemirror.lib.CodeMirror.LineClassWhere;
import net.codemirror.lib.CodeMirror.LineHandle;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.KeyMap;
import net.codemirror.lib.LineCharacter;

import java.util.EnumSet;

public class Unified2 extends DiffScreen {
  interface Binder extends UiBinder<FlowPanel, Unified2> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField(provided = true)
  UnifiedTable2 diffTable;

  private CodeMirror cm;
  private Element columnMargin;
  private UnifiedChunkManager chunkManager;
  private UnifiedCommentManager commentManager;
  private UnifiedSkipManager skipManager;

  public Unified2(PatchSet.Id base, PatchSet.Id revision, String path, int startLine) {
    super(base, revision, path, startLine);

    diffTable = new UnifiedTable2(this, base, revision, path);
    add(uiBinder.createAndBindUi(this));
    addDomHandler(GlobalKey.STOP_PROPAGATION, KeyPressEvent.getType());
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    CallbackGroup cmGroup = new CallbackGroup();
    CodeMirror.initLibrary(cmGroup.add(CallbackGroup.<Void> emptyCallback()));
    final CallbackGroup group = new CallbackGroup();
    final AsyncCallback<Void> modeInjectorCb =
        group.add(CallbackGroup.<Void> emptyCallback());

    DiffApi.diff(revision, path).base(base).wholeFile()
        .intraline(prefs.intralineDifference())
        .ignoreWhitespace(prefs.ignoreWhitespace())
        .get(cmGroup.addFinal(new GerritCallback<DiffInfo>() {
          @Override
          public void onSuccess(DiffInfo diffInfo) {
            diff = diffInfo;
            fileSize = bucketFileSize(diffInfo);
            if (prefs.syntaxHighlighting()) {
              if (fileSize.compareTo(FileSize.SMALL) > 0) {
                modeInjectorCb.onSuccess(null);
              } else {
                injectMode(diffInfo, modeInjectorCb);
              }
            } else {
              modeInjectorCb.onSuccess(null);
            }
          }
        }));

    final CommentsCollections comments = new CommentsCollections();
    comments.load(base, revision, path, group);

    RestApi call = ChangeApi.detail(changeId.get());
    ChangeList.addOptions(call, EnumSet.of(ListChangesOption.ALL_REVISIONS));
    call.get(group.add(new GerritCallback<ChangeInfo>() {
      @Override
      public void onSuccess(ChangeInfo info) {
        info.revisions().copyKeysIntoChildren("name");
        JsArray<RevisionInfo> list = info.revisions().values();
        RevisionInfo.sortRevisionInfoByNumber(list);
        diffTable.set(prefs, list, diff);
        header.setChangeInfo(info);
      }
    }));

    ConfigInfoCache.get(changeId, group.addFinal(
        new ScreenLoadCallback<ConfigInfoCache.Entry>(Unified2.this) {
          @Override
          protected void preDisplay(ConfigInfoCache.Entry result) {
            commentManager = new UnifiedCommentManager(
                Unified2.this,
                base, revision, path,
                result.getCommentLinkProcessor());
            setTheme(result.getTheme());
            display(comments);
          }
        }));
  }

  @Override
  public void onShowView() {
    super.onShowView();
    Window.enableScrolling(false);
    JumpKeys.enable(false);
    if (prefs.hideTopMenu()) {
      Gerrit.setHeaderVisible(false);
    }
    resizeHandler = Window.addResizeHandler(new ResizeHandler() {
      @Override
      public void onResize(ResizeEvent event) {
        resizeCodeMirror();
      }
    });

    final int height = getCodeMirrorHeight();
    operation(new Runnable() {
      @Override
      public void run() {
        cm.setHeight(height);
        cm.refresh();
      }
    });
    setLineLength(prefs.lineLength());
    diffTable.refresh();

    if (startLine == 0) {
      DiffChunkInfo d = chunkManager.getFirst();
      if (d != null) {
        if (d.isEdit() && d.getSide() == DisplaySide.A) {
          // startLine = lineOnOther(d.getSide(), d.getStart()).getLine() + 1;
        } else {
          startLine = d.getStart() + 1;
        }
      }
    }
    if (startLine > 0) {
      int line = startLine - 1;
      if (cm.lineAtHeight(height - 20) < line) {
        cm.scrollToY(cm.heightAtLine(line, "local") - 0.5 * height);
      }
      cm.setCursor(LineCharacter.create(line));
      cm.focus();
    } else {
      cm.setCursor(LineCharacter.create(0));
      cm.focus();
    }
    if (Gerrit.isSignedIn() && prefs.autoReview()) {
      header.autoReview();
    }
    prefetchNextFile();
  }

  @Override
  protected void onUnload() {
    super.onUnload();

    removeKeyHandlerRegistrations();
    if (commentManager != null) {
      CallbackGroup group = new CallbackGroup();
      commentManager.saveAllDrafts(group);
      group.done();
    }
    if (resizeHandler != null) {
      resizeHandler.removeHandler();
      resizeHandler = null;
    }
    if (cm != null) {
      cm.getWrapperElement().removeFromParent();
    }
    if (prefsAction != null) {
      prefsAction.hide();
    }

    Window.enableScrolling(true);
    Gerrit.setHeaderVisible(true);
    JumpKeys.enable(true);
  }

  private void registerCmEvents(final CodeMirror cm) {
    // cm.on("beforeSelectionChange", onSelectionChange(cm));
    cm.on("cursorActivity", updateActiveLine());
    // cm.on("gutterClick", onGutterClick(cm));
    cm.on("focus", updateActiveLine());
    cm.addKeyMap(KeyMap.create().on("A", upToChange(true))
        .on("U", upToChange(false)).on("[", header.navigate(Direction.PREV))
        .on("]", header.navigate(Direction.NEXT))
        .on("R", header.toggleReviewed())
        .on("O", commentManager.toggleOpenBox(cm))
        .on("Enter", commentManager.toggleOpenBox(cm))
        .on("C", commentManager.insertNewDraft(cm))
        .on("N", maybeNextVimSearch(cm))
        .on("P", chunkManager.diffChunkNav(cm, Direction.PREV))
        .on("Shift-M", header.reviewedAndNext())
        .on("Shift-N", maybePrevVimSearch(cm))
        .on("Shift-P", commentManager.commentNav(cm, Direction.PREV))
        .on("Shift-O", commentManager.openCloseAll(cm)).on("I", new Runnable() {
          public void run() {
            switch (getIntraLineStatus()) {
              case OFF:
              case OK:
                toggleShowIntraline();
                break;
              default:
                break;
            }
          }
        }).on("','", new Runnable() {
          @Override
          public void run() {
            prefsAction.show();
          }
        }).on("Shift-/", new Runnable() {
          @Override
          public void run() {
            new ShowHelpCommand().onKeyPress(null);
          }
        }).on("Space", new Runnable() {
          @Override
          public void run() {
            CodeMirror.handleVimKey(cm, "<C-d>");
          }
        }).on("Shift-Space", new Runnable() {
          @Override
          public void run() {
            CodeMirror.handleVimKey(cm, "<C-u>");
          }
        }).on("Ctrl-F", new Runnable() {
          @Override
          public void run() {
            CodeMirror.handleVimKey(cm, "/");
          }
        }).on("Ctrl-A", new Runnable() {
          @Override
          public void run() {
            cm.execCommand("selectAll");
          }
        }));
    if (prefs.renderEntireFile()) {
      cm.addKeyMap(RENDER_ENTIRE_FILE_KEYMAP);
    }
  }

  /*
   * private BeforeSelectionChangeHandler onSelectionChange(final CodeMirror cm)
   * { return new BeforeSelectionChangeHandler() { private InsertCommentBubble
   * bubble;
   *
   * @Override public void handle(CodeMirror cm, LineCharacter anchor,
   * LineCharacter head) { if (anchor == head || (anchor.getLine() ==
   * head.getLine() && anchor.getCh() == head.getCh())) { if (bubble != null) {
   * bubble.setVisible(false); } return; } else if (bubble == null) {
   * init(anchor); } else { bubble.setVisible(true); }
   * bubble.position(cm.charCoords(head, "local")); }
   *
   * private void init(LineCharacter anchor) { bubble = new
   * InsertCommentBubble(commentManager, cm); add(bubble); cm.addWidget(anchor,
   * bubble.getElement(), false); } }; }
   */

  private void display(final CommentsCollections comments) {
    setThemeStyles(prefs.theme().isDark());
    setShowTabs(prefs.showTabs());
    setShowIntraline(prefs.intralineDifference());
    /*if (prefs.showLineNumbers()) {
      diffTable.addStyleName(DiffTable.style.showLineNumbers());
    }*/

    cm = newCm(diff.meta_a() == null ? diff.meta_a() : diff.meta_b(),
        diff.text_unified(), DisplaySide.B, diffTable.cm);
    diffTable.overview.init(cm);
    chunkManager = new UnifiedChunkManager(this, cm, diffTable.overview);
    skipManager = new UnifiedSkipManager(this, commentManager);

    columnMargin = DOM.createDiv();
    columnMargin.setClassName(UnifiedTable2.style.columnMargin());
    cm.getMoverElement().appendChild(columnMargin);

    if (prefs.renderEntireFile() && !canEnableRenderEntireFile(prefs)) {
      // CodeMirror is too slow to layout an entire huge file.
      prefs.renderEntireFile(false);
    }

    operation(new Runnable() {
      public void run() {
        // Estimate initial CM3 height, fixed up in onShowView.
        int height = Window.getClientHeight()
            - (Gerrit.getHeaderFooterHeight() + 18);
        cm.setHeight(height);

        render(diff);
        commentManager.render(comments, prefs.expandAllComments());
        skipManager.render(prefs.context(), diff);
      }
    });

    registerCmEvents(cm);
    // scrollSynchronizer

    prefsAction = new PreferencesAction(this, prefs);
    header.init(prefsAction);

    if (prefs.syntaxHighlighting() && fileSize.compareTo(FileSize.SMALL) > 0) {
      Scheduler.get().scheduleFixedDelay(new RepeatingCommand() {
        @Override
        public boolean execute() {
          if (prefs.syntaxHighlighting() && isAttached()) {
            setSyntaxHighlighting(prefs.syntaxHighlighting());
          }
          return false;
        }
      }, 250);
    }
  }

  @Override
  CodeMirror newCm(
      DiffInfo.FileMeta meta,
      String contents,
      DisplaySide side,
      Element parent) {
    String mode = fileSize == FileSize.SMALL
        ? getContentType(meta)
        : null;
    JsArrayString gutters = JavaScriptObject.createArray().cast();
    gutters.push(UnifiedTable2.style.lineNumbersLeft());
    gutters.push(UnifiedTable2.style.lineNumbersRight());
    return CodeMirror.create(parent, Configuration.create()
        .set("readOnly", true)
        .set("cursorBlinkRate", 0)
        .set("cursorHeight", 0.85)
        .set("lineNumbers", false)
        .set("gutters", gutters)
        .set("tabSize", prefs.tabSize())
        .set("mode", mode)
        .set("lineWrapping", false)
        .set("styleSelectedText", true)
        .set("showTrailingSpace", prefs.showWhitespaceErrors())
        .set("keyMap", "vim_ro")
        .set("theme", prefs.theme().name().toLowerCase())
        .set("value", meta != null ? contents : "")
        .set("viewportMargin", prefs.renderEntireFile() ? POSITIVE_INFINITY : 10));
  }

  LineOnOtherInfo lineOnOther(DisplaySide side, int line) {
    return chunkManager.getLineMapper().lineOnOther(side, line);
  }

  LineHandle setLineNumber(DisplaySide side, final int cmLine, int line) {
    Label gutter = new Label(String.valueOf(line));
    gutter.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        onGutterClick(cmLine);
      }
    });
    diffTable.add(gutter);
    gutter.setStyleName(UnifiedTable2.style.lineNumber());
    return cm.setGutterMarker(cmLine,
        side == DisplaySide.A ? UnifiedTable2.style.lineNumbersLeft()
            : UnifiedTable2.style.lineNumbersRight(), gutter.getElement());
  }

  /*void removeDraft(DraftBox box, int line) {
    LineHandle handle = cm.getLineHandle(getCmLine(box.getSide(), line));
    lineActiveBoxMap.remove(handle);
    if (linePublishedBoxesMap.containsKey(handle)) {
      List<PublishedBox> list = linePublishedBoxesMap.get(handle);
      lineActiveBoxMap.put(handle, list.get(list.size() - 1));
    }
  }*/

  /*
   * private Runnable adjustGutters() { return new Runnable() {
   *
   * @Override public void run() { Viewport fromTo = cm.getViewport(); int size
   * = fromTo.getTo() - fromTo.getFrom() + 1; if (cm.getOldViewportSize() ==
   * size) { return; } cm.setOldViewportSize(size);
   * diffTable.sidePanel.adjustGutters(cmB); } }; }
   */

  private Runnable updateActiveLine() {
    return new Runnable() {
      public void run() {
        // The rendering of active lines has to be deferred. Reflow
        // caused by adding and removing styles chokes Firefox when arrow
        // key (or j/k) is held down. Performance on Chrome is fine
        // without the deferral.
        //
        Scheduler.get().scheduleDeferred(new ScheduledCommand() {
          @Override
          public void execute() {
            LineHandle handle =
                cm.getLineHandleVisualStart(cm.getCursor("end").getLine());
            if (cm.hasActiveLine() && cm.getActiveLine().equals(handle)) {
              return;
            }

            clearActiveLine(cm);
            cm.setActiveLine(handle);
            cm.addLineClass(handle, LineClassWhere.WRAP,
                UnifiedTable2.style.activeLine());
          }
        });
      }
    };
  }

  private GutterClickHandler onGutterClick(final int cmLine) {
    return new GutterClickHandler() {
      @Override
      public void handle(CodeMirror instance, int line, String gutter,
          NativeEvent clickEvent) {
        if (clickEvent.getButton() == NativeEvent.BUTTON_LEFT
            && !clickEvent.getMetaKey()
            && !clickEvent.getAltKey()
            && !clickEvent.getCtrlKey()
            && !clickEvent.getShiftKey()) {
          if (!(cm.hasActiveLine() && cm.getLineNumber(cm.getActiveLine()) == cmLine)) {
            cm.setCursor(LineCharacter.create(cmLine));
          }
          Scheduler.get().scheduleDeferred(new ScheduledCommand() {
            @Override
            public void execute() {
              commentManager.insertNewDraft(cm).run();
            }
          });
        }
      }
    };
  }

  void setLineLength(int columns) {
    columnMargin.getStyle().setMarginLeft(columns * cm.defaultCharWidth(),
        Unit.PX);
  }

  CodeMirror getCmFromSide(DisplaySide side) {
    return cm;
  }

  @Override
  int getCodeMirrorHeight() {
    int rest =
        Gerrit.getHeaderFooterHeight() + header.getOffsetHeight()
            + diffTable.getHeaderHeight() + 5; // Estimate
    return Window.getClientHeight() - rest;
  }

  UnifiedChunkManager getChunkManager() {
    return chunkManager;
  }

  UnifiedCommentManager getCommentManager() {
    return commentManager;
  }

  int getCmLine(DisplaySide side, int line) {
    return chunkManager.getCmLine(side, line);
  }

  int getCmLineFromLinePair(LinePair pair) {
    return chunkManager.getCmLineFromLinePair(pair);
  }

  LinePair getLinePairFromCmLine(int cmLine) {
    return chunkManager.getLinePairFromCmLine(cmLine);
  }

  void operation(final Runnable apply) {
    cm.operation(new Runnable() {
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

  CodeMirror getCm() {
    return cm;
  }

  @Override
  DiffTable getDiffTable() {
    return diffTable;
  }

  @Override
  SkipManager getSkipManager() {
    return skipManager;
  }
}
