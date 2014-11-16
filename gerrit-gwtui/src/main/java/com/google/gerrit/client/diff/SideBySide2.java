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

import static com.google.gerrit.extensions.common.DiffPreferencesInfo.WHOLE_FILE_CONTEXT;
import static java.lang.Double.POSITIVE_INFINITY;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.JumpKeys;
import com.google.gerrit.client.account.DiffPreferences;
import com.google.gerrit.client.change.ChangeScreen2;
import com.google.gerrit.client.change.FileTable;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.EditInfo;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.changes.ChangeList;
import com.google.gerrit.client.diff.DiffInfo.FileMeta;
import com.google.gerrit.client.diff.LineMapper.LineOnOtherInfo;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.projects.ConfigInfoCache;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.common.ListChangesOption;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.globalkey.client.ShowHelpCommand;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.BeforeSelectionChangeHandler;
import net.codemirror.lib.CodeMirror.GutterClickHandler;
import net.codemirror.lib.CodeMirror.LineClassWhere;
import net.codemirror.lib.CodeMirror.LineHandle;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.KeyMap;
import net.codemirror.lib.LineCharacter;
import net.codemirror.lib.ModeInjector;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class SideBySide2 extends Screen {
  private static final KeyMap RENDER_ENTIRE_FILE_KEYMAP = KeyMap.create()
      .on("Ctrl-F", false);

  interface Binder extends UiBinder<FlowPanel, SideBySide2> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  enum FileSize {
    SMALL(0),
    LARGE(500),
    HUGE(4000);

    final int lines;

    FileSize(int n) {
      this.lines = n;
    }
  }

  @UiField(provided = true)
  Header header;

  @UiField(provided = true)
  DiffTable diffTable;

  private final Change.Id changeId;
  private final PatchSet.Id base;
  private final PatchSet.Id revision;
  private final String path;
  private DisplaySide startSide;
  private int startLine;
  private DiffPreferences prefs;

  private CodeMirror cmA;
  private CodeMirror cmB;
  private Element columnMarginA;
  private Element columnMarginB;
  private double charWidthPx;
  private HandlerRegistration resizeHandler;
  private ScrollSynchronizer scrollSynchronizer;
  private DiffInfo diff;
  private FileSize fileSize;
  private EditInfo edit;
  private ChunkManager chunkManager;
  private CommentManager commentManager;
  private SkipManager skipManager;

  private KeyCommandSet keysNavigation;
  private KeyCommandSet keysAction;
  private KeyCommandSet keysComment;
  private List<HandlerRegistration> handlers;
  private PreferencesAction prefsAction;
  private int reloadVersionId;

  public SideBySide2(
      PatchSet.Id base,
      PatchSet.Id revision,
      String path,
      DisplaySide startSide,
      int startLine) {
    this.base = base;
    this.revision = revision;
    this.changeId = revision.getParentKey();
    this.path = path;
    this.startSide = startSide;
    this.startLine = startLine;

    prefs = DiffPreferences.create(Gerrit.getDiffPreferences());
    handlers = new ArrayList<>(6);
    keysNavigation = new KeyCommandSet(Gerrit.C.sectionNavigation());
    header = new Header(keysNavigation, base, revision, path);
    diffTable = new DiffTable(this, base, revision, path);
    add(uiBinder.createAndBindUi(this));
    addDomHandler(GlobalKey.STOP_PROPAGATION, KeyPressEvent.getType());
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setHeaderVisible(false);
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    CallbackGroup cmGroup = new CallbackGroup();
    CodeMirror.initLibrary(cmGroup.add(CallbackGroup.<Void> emptyCallback()));
    final CallbackGroup group = new CallbackGroup();
    final AsyncCallback<Void> modeInjectorCb =
        group.add(CallbackGroup.<Void> emptyCallback());

    DiffApi.diff(revision, path)
      .base(base)
      .wholeFile()
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

    if (Gerrit.isSignedIn()) {
      ChangeApi.edit(changeId.get(), group.add(
          new GerritCallback<EditInfo>() {
            @Override
            public void onSuccess(EditInfo result) {
              edit = result;
            }
          }));
    }

    final CommentsCollections comments = new CommentsCollections();
    comments.load(base, revision, path, group);

    RestApi call = ChangeApi.detail(changeId.get());
    ChangeList.addOptions(call, EnumSet.of(
        ListChangesOption.ALL_REVISIONS));
    call.get(group.add(new GerritCallback<ChangeInfo>() {
      @Override
      public void onSuccess(ChangeInfo info) {
        info.revisions().copyKeysIntoChildren("name");
        if (edit != null) {
          edit.set_name(edit.commit().commit());
          info.set_edit(edit);
          info.revisions().put(edit.name(), RevisionInfo.fromEdit(edit));
        }
        int currentPatchSet = info.revision(info.current_revision())._number();
        JsArray<RevisionInfo> list = info.revisions().values();
        RevisionInfo.sortRevisionInfoByNumber(list);
        diffTable.set(prefs, list, diff, edit != null, currentPatchSet,
            info.status().isOpen());
        header.setChangeInfo(info);
      }}));

    ConfigInfoCache.get(changeId, group.addFinal(
        new ScreenLoadCallback<ConfigInfoCache.Entry>(SideBySide2.this) {
          @Override
          protected void preDisplay(ConfigInfoCache.Entry result) {
            commentManager = new CommentManager(
                SideBySide2.this,
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
        cmA.setHeight(height);
        cmB.setHeight(height);
        cmA.refresh();
        cmB.refresh();
      }
    });
    setLineLength(prefs.lineLength());
    diffTable.refresh();

    if (startLine == 0) {
      DiffChunkInfo d = chunkManager.getFirst();
      if (d != null) {
        if (d.isEdit() && d.getSide() == DisplaySide.A) {
          startSide = DisplaySide.B;
          startLine = lineOnOther(d.getSide(), d.getStart()).getLine() + 1;
        } else {
          startSide = d.getSide();
          startLine = d.getStart() + 1;
        }
      }
    }
    if (startSide != null && startLine > 0) {
      int line = startLine - 1;
      CodeMirror cm = getCmFromSide(startSide);
      if (cm.lineAtHeight(height - 20) < line) {
        cm.scrollToY(cm.heightAtLine(line, "local") - 0.5 * height);
      }
      cm.setCursor(LineCharacter.create(line));
      cm.focus();
    } else {
      cmA.setCursor(LineCharacter.create(0));
      cmA.focus();
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
    if (cmA != null) {
      cmA.getWrapperElement().removeFromParent();
    }
    if (cmB != null) {
      cmB.getWrapperElement().removeFromParent();
    }
    if (prefsAction != null) {
      prefsAction.hide();
    }

    Window.enableScrolling(true);
    Gerrit.setHeaderVisible(true);
    JumpKeys.enable(true);
  }

  private void removeKeyHandlerRegistrations() {
    for (HandlerRegistration h : handlers) {
      h.removeHandler();
    }
    handlers.clear();
  }

  private void registerCmEvents(final CodeMirror cm) {
    cm.on("beforeSelectionChange", onSelectionChange(cm));
    cm.on("cursorActivity", updateActiveLine(cm));
    cm.on("gutterClick", onGutterClick(cm));
    cm.on("focus", updateActiveLine(cm));
    cm.addKeyMap(KeyMap.create()
        .on("A", upToChange(true))
        .on("U", upToChange(false))
        .on("[", header.navigate(Direction.PREV))
        .on("]", header.navigate(Direction.NEXT))
        .on("R", header.toggleReviewed())
        .on("O", commentManager.toggleOpenBox(cm))
        .on("Enter", commentManager.toggleOpenBox(cm))
        .on("C", commentManager.insertNewDraft(cm))
        .on("N", maybeNextVimSearch(cm))
        .on("P", chunkManager.diffChunkNav(cm, Direction.PREV))
        .on("Shift-A", diffTable.toggleA())
        .on("Shift-M", header.reviewedAndNext())
        .on("Shift-N", maybePrevVimSearch(cm))
        .on("Shift-P", commentManager.commentNav(cm, Direction.PREV))
        .on("Shift-O", commentManager.openCloseAll(cm))
        .on("Shift-Left", moveCursorToSide(cm, DisplaySide.A))
        .on("Shift-Right", moveCursorToSide(cm, DisplaySide.B))
        .on("I", new Runnable() {
          @Override
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
        })
        .on("','", new Runnable() {
          @Override
          public void run() {
            prefsAction.show();
          }
        })
        .on("Shift-/", new Runnable() {
          @Override
          public void run() {
            new ShowHelpCommand().onKeyPress(null);
          }
        })
        .on("Space", new Runnable() {
          @Override
          public void run() {
            CodeMirror.handleVimKey(cm, "<C-d>");
          }
        })
        .on("Shift-Space", new Runnable() {
          @Override
          public void run() {
            CodeMirror.handleVimKey(cm, "<C-u>");
          }
        })
        .on("Ctrl-F", new Runnable() {
          @Override
          public void run() {
            CodeMirror.handleVimKey(cm, "/");
          }
        })
        .on("Ctrl-A", new Runnable() {
          @Override
          public void run() {
            cm.execCommand("selectAll");
          }
        }));
    if (prefs.renderEntireFile()) {
      cm.addKeyMap(RENDER_ENTIRE_FILE_KEYMAP);
    }
  }

  private BeforeSelectionChangeHandler onSelectionChange(final CodeMirror cm) {
    return new BeforeSelectionChangeHandler() {
      private InsertCommentBubble bubble;

      @Override
      public void handle(CodeMirror cm, LineCharacter anchor, LineCharacter head) {
        if (anchor == head
            || (anchor.getLine() == head.getLine()
             && anchor.getCh() == head.getCh())) {
          if (bubble != null) {
            bubble.setVisible(false);
          }
          return;
        } else if (bubble == null) {
          init(anchor);
        } else {
          bubble.setVisible(true);
        }
        bubble.position(cm.charCoords(head, "local"));
      }

      private void init(LineCharacter anchor) {
        bubble = new InsertCommentBubble(commentManager, cm);
        add(bubble);
        cm.addWidget(anchor, bubble.getElement(), false);
      }
    };
  }

  @Override
  public void registerKeys() {
    super.registerKeys();

    keysNavigation.add(new UpToChangeCommand2(revision, 0, 'u'));
    keysNavigation.add(
        new NoOpKeyCommand(KeyCommand.M_SHIFT, KeyCodes.KEY_LEFT, PatchUtil.C.focusSideA()),
        new NoOpKeyCommand(KeyCommand.M_SHIFT, KeyCodes.KEY_RIGHT, PatchUtil.C.focusSideB()));
    keysNavigation.add(
        new NoOpKeyCommand(0, 'j', PatchUtil.C.lineNext()),
        new NoOpKeyCommand(0, 'k', PatchUtil.C.linePrev()));
    keysNavigation.add(
        new NoOpKeyCommand(0, 'n', PatchUtil.C.chunkNext2()),
        new NoOpKeyCommand(0, 'p', PatchUtil.C.chunkPrev2()));
    keysNavigation.add(
        new NoOpKeyCommand(KeyCommand.M_SHIFT, 'n', PatchUtil.C.commentNext()),
        new NoOpKeyCommand(KeyCommand.M_SHIFT, 'p', PatchUtil.C.commentPrev()));
    keysNavigation.add(
        new NoOpKeyCommand(KeyCommand.M_CTRL, 'f', Gerrit.C.keySearch()));

    keysAction = new KeyCommandSet(Gerrit.C.sectionActions());
    keysAction.add(new NoOpKeyCommand(0, KeyCodes.KEY_ENTER,
        PatchUtil.C.expandComment()));
    keysAction.add(new NoOpKeyCommand(0, 'o', PatchUtil.C.expandComment()));
    keysAction.add(new NoOpKeyCommand(
        KeyCommand.M_SHIFT, 'o', PatchUtil.C.expandAllCommentsOnCurrentLine()));
    if (Gerrit.isSignedIn()) {
      keysAction.add(new KeyCommand(0, 'r', PatchUtil.C.toggleReviewed()) {
        @Override
        public void onKeyPress(KeyPressEvent event) {
          header.toggleReviewed().run();
        }
      });
    }
    keysAction.add(new KeyCommand(
        KeyCommand.M_SHIFT, 'm', PatchUtil.C.markAsReviewedAndGoToNext()) {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        header.reviewedAndNext().run();
      }
    });
    keysAction.add(new KeyCommand(0, 'a', PatchUtil.C.openReply()) {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        upToChange(true).run();
      }
    });
    keysAction.add(new KeyCommand(
        KeyCommand.M_SHIFT, 'a', PatchUtil.C.toggleSideA()) {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        diffTable.toggleA().run();
      }
    });
    keysAction.add(new KeyCommand(0, ',', PatchUtil.C.showPreferences()) {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        prefsAction.show();
      }
    });
    if (getIntraLineStatus() == DiffInfo.IntraLineStatus.OFF
        || getIntraLineStatus() == DiffInfo.IntraLineStatus.OK) {
      keysAction.add(new KeyCommand(0, 'i', PatchUtil.C.toggleIntraline()) {
        @Override
        public void onKeyPress(KeyPressEvent event) {
          toggleShowIntraline();
        }
      });
    }

    if (Gerrit.isSignedIn()) {
      keysAction.add(new NoOpKeyCommand(0, 'c', PatchUtil.C.commentInsert()));
      keysComment = new KeyCommandSet(PatchUtil.C.commentEditorSet());
      keysComment.add(new NoOpKeyCommand(KeyCommand.M_CTRL, 's',
          PatchUtil.C.commentSaveDraft()));
      keysComment.add(new NoOpKeyCommand(0, KeyCodes.KEY_ESCAPE,
          PatchUtil.C.commentCancelEdit()));
    } else {
      keysComment = null;
    }

    removeKeyHandlerRegistrations();
    handlers.add(GlobalKey.add(this, keysAction));
    handlers.add(GlobalKey.add(this, keysNavigation));
    if (keysComment != null) {
      handlers.add(GlobalKey.add(this, keysComment));
    }
    handlers.add(ShowHelpCommand.addFocusHandler(new FocusHandler() {
      @Override
      public void onFocus(FocusEvent event) {
        cmB.focus();
      }
    }));
  }

  private void display(final CommentsCollections comments) {
    setThemeStyles(prefs.theme().isDark());
    setShowTabs(prefs.showTabs());
    setShowIntraline(prefs.intralineDifference());
    if (prefs.showLineNumbers()) {
      diffTable.addStyleName(DiffTable.style.showLineNumbers());
    }

    cmA = newCM(diff.meta_a(), diff.text_a(), DisplaySide.A, diffTable.cmA);
    cmB = newCM(diff.meta_b(), diff.text_b(), DisplaySide.B, diffTable.cmB);
    diffTable.overview.init(cmB);
    chunkManager = new ChunkManager(this, cmA, cmB, diffTable.overview);
    skipManager = new SkipManager(this, commentManager);

    columnMarginA = DOM.createDiv();
    columnMarginB = DOM.createDiv();
    columnMarginA.setClassName(DiffTable.style.columnMargin());
    columnMarginB.setClassName(DiffTable.style.columnMargin());
    cmA.getMoverElement().appendChild(columnMarginA);
    cmB.getMoverElement().appendChild(columnMarginB);

    if (prefs.renderEntireFile() && !canEnableRenderEntireFile(prefs)) {
      // CodeMirror is too slow to layout an entire huge file.
      prefs.renderEntireFile(false);
    }

    operation(new Runnable() {
      @Override
      public void run() {
        // Estimate initial CM3 height, fixed up in onShowView.
        int height = Window.getClientHeight()
            - (Gerrit.getHeaderFooterHeight() + 18);
        cmA.setHeight(height);
        cmB.setHeight(height);

        render(diff);
        commentManager.render(comments, prefs.expandAllComments());
        skipManager.render(prefs.context(), diff);
      }
    });

    registerCmEvents(cmA);
    registerCmEvents(cmB);
    scrollSynchronizer = new ScrollSynchronizer(diffTable, cmA, cmB,
            chunkManager.getLineMapper());

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

  private CodeMirror newCM(
      DiffInfo.FileMeta meta,
      String contents,
      DisplaySide side,
      Element parent) {
    String mode = fileSize == FileSize.SMALL
        ? getContentType(meta)
        : null;
    return CodeMirror.create(side, parent, Configuration.create()
      .set("readOnly", true)
      .set("cursorBlinkRate", 0)
      .set("cursorHeight", 0.85)
      .set("lineNumbers", prefs.showLineNumbers())
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

  DiffInfo.IntraLineStatus getIntraLineStatus() {
    return diff.intraline_status();
  }

  boolean canEnableRenderEntireFile(DiffPreferences prefs) {
    return fileSize.compareTo(FileSize.HUGE) < 0
        || (prefs.context() != WHOLE_FILE_CONTEXT && prefs.context() < 100);
  }

  String getContentType() {
    return getContentType(diff.meta_b());
  }

  void setThemeStyles(boolean d) {
    if (d) {
      diffTable.addStyleName(DiffTable.style.dark());
    } else {
      diffTable.removeStyleName(DiffTable.style.dark());
    }
  }

  void setShowTabs(boolean b) {
    if (b) {
      diffTable.addStyleName(DiffTable.style.showTabs());
    } else {
      diffTable.removeStyleName(DiffTable.style.showTabs());
    }
  }

  void setLineLength(int columns) {
    double w = columns * getCharWidthPx();
    columnMarginA.getStyle().setMarginLeft(w, Style.Unit.PX);
    columnMarginB.getStyle().setMarginLeft(w, Style.Unit.PX);
  }

  private double getCharWidthPx() {
    if (charWidthPx <= 1) {
      int len = 100;
      StringBuilder s = new StringBuilder();
      for (int i = 0; i < len; i++) {
        s.append('m');
      }
      Element e = DOM.createSpan();
      e.getStyle().setDisplay(Style.Display.INLINE_BLOCK);
      e.setInnerText(s.toString());

      cmA.getMoverElement().appendChild(e);
      double a = ((double) e.getOffsetWidth()) / len;
      e.removeFromParent();

      cmB.getMoverElement().appendChild(e);
      double b = ((double) e.getOffsetWidth()) / len;
      e.removeFromParent();
      charWidthPx = Math.max(a, b);
    }
    return charWidthPx;
  }

  void setShowLineNumbers(boolean b) {
    cmA.setOption("lineNumbers", b);
    cmB.setOption("lineNumbers", b);
    if (b) {
      diffTable.addStyleName(DiffTable.style.showLineNumbers());
    } else {
      diffTable.removeStyleName(DiffTable.style.showLineNumbers());
    }
  }

  void setShowIntraline(boolean b) {
    if (b && getIntraLineStatus() == DiffInfo.IntraLineStatus.OFF) {
      reloadDiffInfo();
    } else if (b) {
      diffTable.removeStyleName(DiffTable.style.noIntraline());
    } else {
      diffTable.addStyleName(DiffTable.style.noIntraline());
    }
  }

  private void toggleShowIntraline() {
    prefs.intralineDifference(!prefs.intralineDifference());
    setShowIntraline(prefs.intralineDifference());
    prefsAction.update();
  }

  void setSyntaxHighlighting(boolean b) {
    if (b) {
      injectMode(diff, new AsyncCallback<Void>() {
        @Override
        public void onSuccess(Void result) {
          if (prefs.syntaxHighlighting()) {
            cmA.setOption("mode", getContentType(diff.meta_a()));
            cmB.setOption("mode", getContentType(diff.meta_b()));
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

  void setContext(final int context) {
    operation(new Runnable() {
      @Override
      public void run() {
        skipManager.removeAll();
        skipManager.render(context, diff);
        diffTable.overview.refresh();
      }
    });
  }

  void setAutoHideDiffHeader(boolean hide) {
    diffTable.setAutoHideDiffHeader(hide);
  }

  private void render(DiffInfo diff) {
    header.setNoDiff(diff);
    chunkManager.render(diff);
  }

  CodeMirror otherCm(CodeMirror me) {
    return me == cmA ? cmB : cmA;
  }

  CodeMirror getCmFromSide(DisplaySide side) {
    return side == DisplaySide.A ? cmA : cmB;
  }

  LineOnOtherInfo lineOnOther(DisplaySide side, int line) {
    return chunkManager.getLineMapper().lineOnOther(side, line);
  }

  private void clearActiveLine(CodeMirror cm) {
    if (cm.hasActiveLine()) {
      LineHandle activeLine = cm.getActiveLine();
      cm.removeLineClass(activeLine,
          LineClassWhere.WRAP, DiffTable.style.activeLine());
      cm.setActiveLine(null);
    }
  }

  private Runnable updateActiveLine(final CodeMirror cm) {
    final CodeMirror other = otherCm(cm);
    return new Runnable() {
      @Override
      public void run() {
        // The rendering of active lines has to be deferred. Reflow
        // caused by adding and removing styles chokes Firefox when arrow
        // key (or j/k) is held down. Performance on Chrome is fine
        // without the deferral.
        //
        Scheduler.get().scheduleDeferred(new ScheduledCommand() {
          @Override
          public void execute() {
            operation(new Runnable() {
              @Override
              public void run() {
                LineHandle handle = cm.getLineHandleVisualStart(
                    cm.getCursor("end").getLine());
                if (cm.hasActiveLine() && cm.getActiveLine().equals(handle)) {
                  return;
                }

                clearActiveLine(cm);
                clearActiveLine(other);
                cm.setActiveLine(handle);
                cm.addLineClass(
                    handle, LineClassWhere.WRAP, DiffTable.style.activeLine());
                LineOnOtherInfo info =
                    lineOnOther(cm.side(), cm.getLineNumber(handle));
                if (info.isAligned()) {
                  LineHandle oLineHandle = other.getLineHandle(info.getLine());
                  other.setActiveLine(oLineHandle);
                  other.addLineClass(oLineHandle, LineClassWhere.WRAP,
                      DiffTable.style.activeLine());
                }
              }
            });
          }
        });
      }
    };
  }

  private GutterClickHandler onGutterClick(final CodeMirror cm) {
    return new GutterClickHandler() {
      @Override
      public void handle(CodeMirror instance, int line, String gutter,
          NativeEvent clickEvent) {
        if (clickEvent.getButton() == NativeEvent.BUTTON_LEFT
            && !clickEvent.getMetaKey()
            && !clickEvent.getAltKey()
            && !clickEvent.getCtrlKey()
            && !clickEvent.getShiftKey()) {
          if (!(cm.hasActiveLine() &&
              cm.getLineNumber(cm.getActiveLine()) == line)) {
            cm.setCursor(LineCharacter.create(line));
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

  private Runnable upToChange(final boolean openReplyBox) {
    return new Runnable() {
      @Override
      public void run() {
        CallbackGroup group = new CallbackGroup();
        commentManager.saveAllDrafts(group);
        group.done();
        group.addListener(new GerritCallback<Void>() {
          @Override
          public void onSuccess(Void result) {
            String b = base != null ? base.getId() : null;
            String rev = revision.getId();
            Gerrit.display(
              PageLinks.toChange(changeId, b, rev),
              new ChangeScreen2(changeId, b, rev, openReplyBox,
                  FileTable.Mode.REVIEW));
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
        public void run() {
        }
      };
    }

    final DisplaySide sideSrc = cmSrc.side();
    return new Runnable() {
      @Override
      public void run() {
        if (cmSrc.hasActiveLine()) {
          cmDst.setCursor(LineCharacter.create(lineOnOther(
              sideSrc,
              cmSrc.getLineNumber(cmSrc.getActiveLine())).getLine()));
        }
        cmDst.focus();
      }
    };
  }

  private Runnable maybePrevVimSearch(final CodeMirror cm) {
    return new Runnable() {
      @Override
      public void run() {
        if (cm.hasVimSearchHighlight()) {
          CodeMirror.handleVimKey(cm, "N");
        } else {
          commentManager.commentNav(cm, Direction.NEXT).run();
        }
      }
    };
  }

  private Runnable maybeNextVimSearch(final CodeMirror cm) {
    return new Runnable() {
      @Override
      public void run() {
        if (cm.hasVimSearchHighlight()) {
          CodeMirror.handleVimKey(cm, "n");
        } else {
          chunkManager.diffChunkNav(cm, Direction.NEXT).run();
        }
      }
    };
  }

  void updateRenderEntireFile() {
    cmA.removeKeyMap(RENDER_ENTIRE_FILE_KEYMAP);
    cmB.removeKeyMap(RENDER_ENTIRE_FILE_KEYMAP);
    if (prefs.renderEntireFile()) {
      cmA.addKeyMap(RENDER_ENTIRE_FILE_KEYMAP);
      cmB.addKeyMap(RENDER_ENTIRE_FILE_KEYMAP);
    }

    cmA.setOption("viewportMargin", prefs.renderEntireFile() ? POSITIVE_INFINITY : 10);
    cmB.setOption("viewportMargin", prefs.renderEntireFile() ? POSITIVE_INFINITY : 10);
  }

  void resizeCodeMirror() {
    int height = getCodeMirrorHeight();
    cmA.setHeight(height);
    cmB.setHeight(height);
    diffTable.overview.refresh();
  }

  private int getCodeMirrorHeight() {
    int rest = Gerrit.getHeaderFooterHeight()
        + header.getOffsetHeight()
        + diffTable.getHeaderHeight()
        + 5; // Estimate
    return Window.getClientHeight() - rest;
  }

  void syncScroll(DisplaySide masterSide) {
    if (scrollSynchronizer != null) {
      scrollSynchronizer.syncScroll(masterSide);
    }
  }

  private String getContentType(DiffInfo.FileMeta meta) {
    return prefs.syntaxHighlighting()
          && meta != null
          && meta.content_type() != null
        ? ModeInjector.getContentType(meta.content_type())
        : null;
  }

  private void injectMode(DiffInfo diffInfo, AsyncCallback<Void> cb) {
    new ModeInjector()
      .add(getContentType(diffInfo.meta_a()))
      .add(getContentType(diffInfo.meta_b()))
      .inject(cb);
  }

  DiffPreferences getPrefs() {
    return prefs;
  }

  ChunkManager getChunkManager() {
    return chunkManager;
  }

  CommentManager getCommentManager() {
    return commentManager;
  }

  SkipManager getSkipManager() {
    return skipManager;
  }

  void operation(final Runnable apply) {
    cmA.operation(new Runnable() {
      @Override
      public void run() {
        cmB.operation(new Runnable() {
          @Override
          public void run() {
            apply.run();
          }
        });
      }
    });
  }

  private void prefetchNextFile() {
    String nextPath = header.getNextPath();
    if (nextPath != null) {
      DiffApi.diff(revision, nextPath)
        .base(base)
        .wholeFile()
        .intraline(prefs.intralineDifference())
        .ignoreWhitespace(prefs.ignoreWhitespace())
        .get(new AsyncCallback<DiffInfo>() {
          @Override
          public void onSuccess(DiffInfo info) {
            new ModeInjector()
              .add(getContentType(info.meta_a()))
              .add(getContentType(info.meta_b()))
              .inject(CallbackGroup.<Void> emptyCallback());
          }

          @Override
          public void onFailure(Throwable caught) {
          }
        });
    }
  }

  void reloadDiffInfo() {
    final int id = ++reloadVersionId;
    DiffApi.diff(revision, path)
      .base(base)
      .wholeFile()
      .intraline(prefs.intralineDifference())
      .ignoreWhitespace(prefs.ignoreWhitespace())
      .get(new GerritCallback<DiffInfo>() {
        @Override
        public void onSuccess(DiffInfo diffInfo) {
          if (id == reloadVersionId && isAttached()) {
            diff = diffInfo;
            operation(new Runnable() {
              @Override
              public void run() {
                skipManager.removeAll();
                chunkManager.reset();
                diffTable.overview.clearDiffMarkers();
                setShowIntraline(prefs.intralineDifference());
                render(diff);
                skipManager.render(prefs.context(), diff);
              }
            });
          }
        }
      });
  }

  private static FileSize bucketFileSize(DiffInfo diff) {
    FileMeta a = diff.meta_a();
    FileMeta b = diff.meta_b();
    FileSize[] sizes = FileSize.values();
    for (int i = sizes.length - 1; 0 <= i; i--) {
      FileSize s = sizes[i];
      if ((a != null && s.lines <= a.lines())
          || (b != null && s.lines <= b.lines())) {
        return s;
      }
    }
    return FileSize.SMALL;
  }
}
