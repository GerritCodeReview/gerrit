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

import static com.google.gerrit.extensions.client.DiffPreferencesInfo.WHOLE_FILE_CONTEXT;
import static java.lang.Double.POSITIVE_INFINITY;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.JumpKeys;
import com.google.gerrit.client.account.DiffPreferences;
import com.google.gerrit.client.blame.BlameInfo;
import com.google.gerrit.client.change.ChangeScreen;
import com.google.gerrit.client.change.FileTable;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeList;
import com.google.gerrit.client.diff.DiffInfo.FileMeta;
import com.google.gerrit.client.diff.LineMapper.LineOnOtherInfo;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.info.ChangeInfo.EditInfo;
import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.info.FileInfo;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.projects.ConfigInfoCache;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ImageResourceRenderer;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.globalkey.client.ShowHelpCommand;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.BeforeSelectionChangeHandler;
import net.codemirror.lib.CodeMirror.GutterClickHandler;
import net.codemirror.lib.CodeMirror.LineHandle;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.KeyMap;
import net.codemirror.lib.Pos;
import net.codemirror.mode.ModeInfo;
import net.codemirror.mode.ModeInjector;
import net.codemirror.theme.ThemeLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class SideBySide extends Screen {
  private static final KeyMap RENDER_ENTIRE_FILE_KEYMAP = KeyMap.create()
      .propagate("Ctrl-F");

  interface Binder extends UiBinder<FlowPanel, SideBySide> {}
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
  private Change.Status changeStatus;

  private CodeMirror cmA;
  private CodeMirror cmB;

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

  public SideBySide(
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
    setWindowTitle(FileInfo.getFileName(path));
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    CallbackGroup group1 = new CallbackGroup();
    final CallbackGroup group2 = new CallbackGroup();

    CodeMirror.initLibrary(group1.add(new AsyncCallback<Void>() {
      final AsyncCallback<Void> themeCallback = group2.addEmpty();

      @Override
      public void onSuccess(Void result) {
        // Load theme after CM library to ensure theme can override CSS.
        ThemeLoader.loadTheme(prefs.theme(), themeCallback);
      }

      @Override
      public void onFailure(Throwable caught) {
      }
    }));

    DiffApi.diff(revision, path)
      .base(base)
      .wholeFile()
      .intraline(prefs.intralineDifference())
      .ignoreWhitespace(prefs.ignoreWhitespace())
      .get(group1.addFinal(new GerritCallback<DiffInfo>() {
        final AsyncCallback<Void> modeInjectorCb = group2.addEmpty();

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
      ChangeApi.edit(changeId.get(), group2.add(
          new AsyncCallback<EditInfo>() {
            @Override
            public void onSuccess(EditInfo result) {
              edit = result;
            }

            @Override
            public void onFailure(Throwable caught) {
            }
          }));
    }

    final CommentsCollections comments = new CommentsCollections();
    comments.load(base, revision, path, group2);

    RestApi call = ChangeApi.detail(changeId.get());
    ChangeList.addOptions(call, EnumSet.of(
        ListChangesOption.ALL_REVISIONS));
    call.get(group2.add(new AsyncCallback<ChangeInfo>() {
      @Override
      public void onSuccess(ChangeInfo info) {
        changeStatus = info.status();
        info.revisions().copyKeysIntoChildren("name");
        if (edit != null) {
          edit.setName(edit.commit().commit());
          info.setEdit(edit);
          info.revisions().put(edit.name(), RevisionInfo.fromEdit(edit));
        }
        String currentRevision = info.currentRevision();
        boolean current = currentRevision != null &&
            revision.get() == info.revision(currentRevision)._number();
        JsArray<RevisionInfo> list = info.revisions().values();
        RevisionInfo.sortRevisionInfoByNumber(list);
        diffTable.set(prefs, list, diff,
          new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
              if (cmA.getBlameInfo() != null) {
                cmA.toggleAnnotation();
              } else {
                PatchSet.Id rev;
                if (base != null) {
                  rev = base;
                } else {
                  rev = revision;
                }
                boolean isBase = base == null;
                ChangeApi.blame(rev, path, isBase)
                  .get(new GerritCallback<BlameInfo>() {

                    @Override
                    public void onSuccess(BlameInfo result) {
                      cmA.toggleAnnotation(result);
                    }
                  });
              }
            }
          },
          new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
              if (cmB.getBlameInfo() != null) {
                cmB.toggleAnnotation();
              } else {
                ChangeApi.blame(revision, path, false)
                  .get(new GerritCallback<BlameInfo>() {

                    @Override
                    public void onSuccess(BlameInfo result) {
                      cmB.toggleAnnotation(result);
                    }
                  });
              }
            }
          },
          edit != null, current, changeStatus.isOpen(), diff.binary());

        header.setChangeInfo(info);
      }

      @Override
      public void onFailure(Throwable caught) {
      }
    }));

    ConfigInfoCache.get(changeId, group2.addFinal(
        new ScreenLoadCallback<ConfigInfoCache.Entry>(SideBySide.this) {
          @Override
          protected void preDisplay(ConfigInfoCache.Entry result) {
            commentManager = new CommentManager(
                SideBySide.this,
                base, revision, path,
                result.getCommentLinkProcessor(),
                changeStatus.isOpen());
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

    operation(new Runnable() {
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
      CodeMirror cm = getCmFromSide(startSide);
      cm.scrollToLine(startLine - 1);
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
    cm.on("cursorActivity", updateActiveLine(cm));
    cm.on("focus", updateActiveLine(cm));
    KeyMap keyMap = KeyMap.create()
        .on("A", upToChange(true))
        .on("U", upToChange(false))
        .on("[", header.navigate(Direction.PREV))
        .on("]", header.navigate(Direction.NEXT))
        .on("R", header.toggleReviewed())
        .on("O", commentManager.toggleOpenBox(cm))
        .on("Enter", commentManager.toggleOpenBox(cm))
        .on("N", maybeNextVimSearch(cm))
        .on("E", openEditScreen(cm))
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
            cm.vim().handleKey("<C-d>");
          }
        })
        .on("Shift-Space", new Runnable() {
          @Override
          public void run() {
            cm.vim().handleKey("<C-u>");
          }
        })
        .on("Ctrl-F", new Runnable() {
          @Override
          public void run() {
            cm.vim().handleKey("/");
          }
        })
        .on("Ctrl-A", new Runnable() {
          @Override
          public void run() {
            cm.execCommand("selectAll");
          }
        });
    if (revision.get() != 0) {
      cm.on("beforeSelectionChange", onSelectionChange(cm));
      cm.on("gutterClick", onGutterClick(cm));
      keyMap.on("C", commentManager.insertNewDraft(cm));
    }
    cm.addKeyMap(keyMap);
    if (renderEntireFile()) {
      cm.addKeyMap(RENDER_ENTIRE_FILE_KEYMAP);
    }
  }

  private BeforeSelectionChangeHandler onSelectionChange(final CodeMirror cm) {
    return new BeforeSelectionChangeHandler() {
      private InsertCommentBubble bubble;

      @Override
      public void handle(CodeMirror cm, Pos anchor, Pos head) {
        if (anchor.equals(head)) {
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

      private void init(Pos anchor) {
        bubble = new InsertCommentBubble(commentManager, cm);
        add(bubble);
        cm.addWidget(anchor, bubble.getElement());
      }
    };
  }

  @Override
  public void registerKeys() {
    super.registerKeys();

    keysNavigation.add(new UpToChangeCommand(revision, 0, 'u'));
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
    keysAction.add(new NoOpKeyCommand(0, 'e', PatchUtil.C.openEditScreen()));
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
    setShowIntraline(prefs.intralineDifference());
    if (prefs.showLineNumbers()) {
      diffTable.addStyleName(DiffTable.style.showLineNumbers());
    }

    cmA = newCM(diff.metaA(), diff.textA(), diffTable.cmA);
    cmB = newCM(diff.metaB(), diff.textB(), diffTable.cmB);

    cmA.extras().side(DisplaySide.A);
    cmB.extras().side(DisplaySide.B);
    setShowTabs(prefs.showTabs());

    chunkManager = new ChunkManager(this, cmA, cmB, diffTable.scrollbar);
    skipManager = new SkipManager(this, commentManager);

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
    header.init(prefsAction, getLinks(), diff.sideBySideWebLinks());
    scrollSynchronizer.setAutoHideDiffTableHeader(prefs.autoHideDiffTableHeader());

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

  private List<InlineHyperlink> getLinks() {
    InlineHyperlink toUnifiedDiffLink = new InlineHyperlink();
    toUnifiedDiffLink.setHTML(
        new ImageResourceRenderer().render(Gerrit.RESOURCES.unifiedDiff()));
    toUnifiedDiffLink.setTargetHistoryToken(
        Dispatcher.toUnified(base, revision, path));
    toUnifiedDiffLink.setTitle(PatchUtil.C.unifiedDiff());
    return Collections.singletonList(toUnifiedDiffLink);
  }

  private CodeMirror newCM(
      DiffInfo.FileMeta meta,
      String contents,
      Element parent) {
    return CodeMirror.create(parent, Configuration.create()
      .set("readOnly", true)
      .set("cursorBlinkRate", prefs.cursorBlinkRate())
      .set("cursorHeight", 0.85)
      .set("lineNumbers", prefs.showLineNumbers())
      .set("tabSize", prefs.tabSize())
      .set("mode", fileSize == FileSize.SMALL ? getContentType(meta) : null)
      .set("lineWrapping", false)
      .set("scrollbarStyle", "overlay")
      .set("styleSelectedText", true)
      .set("showTrailingSpace", prefs.showWhitespaceErrors())
      .set("keyMap", "vim_ro")
      .set("theme", prefs.theme().name().toLowerCase())
      .set("value", meta != null ? contents : "")
      .set("viewportMargin", renderEntireFile() ? POSITIVE_INFINITY : 10));
  }

  DiffInfo.IntraLineStatus getIntraLineStatus() {
    return diff.intralineStatus();
  }

  boolean renderEntireFile() {
    return prefs.renderEntireFile() && canRenderEntireFile(prefs);
  }

  boolean canRenderEntireFile(DiffPreferences prefs) {
    // CodeMirror is too slow to layout an entire huge file.
    return fileSize.compareTo(FileSize.HUGE) < 0
        || (prefs.context() != WHOLE_FILE_CONTEXT && prefs.context() < 100);
  }

  String getContentType() {
    return getContentType(diff.metaB());
  }

  void setThemeStyles(boolean d) {
    if (d) {
      diffTable.addStyleName(DiffTable.style.dark());
    } else {
      diffTable.removeStyleName(DiffTable.style.dark());
    }
  }

  void setShowTabs(boolean show) {
    cmA.extras().showTabs(show);
    cmB.extras().showTabs(show);
  }

  void setLineLength(int columns) {
    cmA.extras().lineLength(columns);
    cmB.extras().lineLength(columns);
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

  void setContext(final int context) {
    operation(new Runnable() {
      @Override
      public void run() {
        skipManager.removeAll();
        skipManager.render(context, diff);
        updateRenderEntireFile();
      }
    });
  }

  void setAutoHideDiffHeader(boolean hide) {
    scrollSynchronizer.setAutoHideDiffTableHeader(hide);
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
                LineHandle handle =
                    cm.getLineHandleVisualStart(cm.getCursor("end").line());
                if (!cm.extras().activeLine(handle)) {
                  return;
                }

                LineOnOtherInfo info =
                    lineOnOther(cm.side(), cm.getLineNumber(handle));
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

  private GutterClickHandler onGutterClick(final CodeMirror cm) {
    return new GutterClickHandler() {
      @Override
      public void handle(CodeMirror instance, final int line, String gutter,
          NativeEvent clickEvent) {
        if (clickEvent.getButton() == NativeEvent.BUTTON_LEFT
            && !clickEvent.getMetaKey()
            && !clickEvent.getAltKey()
            && !clickEvent.getCtrlKey()
            && !clickEvent.getShiftKey()) {
          cm.setCursor(Pos.create(line));
          if (CodeMirror.ANNOTATION_GUTTER_ID.equals(gutter)) {
            BlameInfo.BlameLine blame = cm.getBlame(line);
            if (blame != null) {
              Gerrit.display(
                PageLinks.toChange(new Change.Id(blame.changeId()), String.valueOf(blame.patchSetId())) + "/" + path);
            }
          } else {
            Scheduler.get().scheduleDeferred(new ScheduledCommand() {
              @Override
              public void execute() {
                commentManager.newDraft(cm, line + 1);
              }
            });
          }
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
              new ChangeScreen(changeId, b, rev, openReplyBox,
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
        if (cmSrc.extras().hasActiveLine()) {
          cmDst.setCursor(Pos.create(lineOnOther(
              sideSrc,
              cmSrc.getLineNumber(cmSrc.extras().activeLine())).getLine()));
        }
        cmDst.focus();
      }
    };
  }

  private Runnable maybePrevVimSearch(final CodeMirror cm) {
    return new Runnable() {
      @Override
      public void run() {
        if (cm.vim().hasSearchHighlight()) {
          cm.vim().handleKey("N");
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
        if (cm.vim().hasSearchHighlight()) {
          cm.vim().handleKey("n");
        } else {
          chunkManager.diffChunkNav(cm, Direction.NEXT).run();
        }
      }
    };
  }

  private int adjustCommitMessageLine(int line) {
    /* When commit messages are shown in the side-by-side screen they include
      a header block that looks like this:

      1 Parent:     deadbeef (Parent commit title)
      2 Author:     A. U. Thor <author@example.com>
      3 AuthorDate: 2015-02-27 19:20:52 +0900
      4 Commit:     A. U. Thor <author@example.com>
      5 CommitDate: 2015-02-27 19:20:52 +0900
      6 [blank line]
      7 Commit message title
      8
      9 Commit message body
     10 ...
     11 ...

    If the commit is a merge commit, both parent commits are listed in the
    first two lines instead of a 'Parent' line:

      1 Merge Of:   deadbeef (Parent 1 commit title)
      2             beefdead (Parent 2 commit title)

    */

    // Offset to compensate for header lines until the blank line
    // after 'CommitDate'
    int offset = 6;

    // Adjust for merge commits, which have two parent lines
    if (diff.textB().startsWith("Merge")) {
      offset += 1;
    }

    // If the cursor is inside the header line, reset to the first line of the
    // commit message. Otherwise if the cursor is on an actual line of the commit
    // message, adjust the line number to compensate for the header lines, so the
    // focus is on the correct line.
    if (line <= offset) {
      return 1;
    } else {
      return line - offset;
    }
  }

  private Runnable openEditScreen(final CodeMirror cm) {
    return new Runnable() {
      @Override
      public void run() {
        LineHandle handle = cm.extras().activeLine();
        int line = cm.getLineNumber(handle) + 1;
        if (Patch.COMMIT_MSG.equals(path)) {
          line = adjustCommitMessageLine(line);
        }
        String token = Dispatcher.toEditScreen(revision, path, line);
        if (!Gerrit.isSignedIn()) {
          Gerrit.doSignIn(token);
        } else {
          Gerrit.display(token);
        }
      }
    };
  }

  void updateRenderEntireFile() {
    cmA.removeKeyMap(RENDER_ENTIRE_FILE_KEYMAP);
    cmB.removeKeyMap(RENDER_ENTIRE_FILE_KEYMAP);

    boolean entireFile = renderEntireFile();
    if (entireFile) {
      cmA.addKeyMap(RENDER_ENTIRE_FILE_KEYMAP);
      cmB.addKeyMap(RENDER_ENTIRE_FILE_KEYMAP);
    }
    cmA.setOption("viewportMargin", entireFile ? POSITIVE_INFINITY : 10);
    cmB.setOption("viewportMargin", entireFile ? POSITIVE_INFINITY : 10);
  }

  void resizeCodeMirror() {
    int hdr = header.getOffsetHeight() + diffTable.getHeaderHeight();
    cmA.adjustHeight(hdr);
    cmB.adjustHeight(hdr);
  }

  void syncScroll(DisplaySide masterSide) {
    if (scrollSynchronizer != null) {
      scrollSynchronizer.syncScroll(masterSide);
    }
  }

  private String getContentType(DiffInfo.FileMeta meta) {
    if (prefs.syntaxHighlighting() && meta != null
        && meta.contentType() != null) {
     ModeInfo m = ModeInfo.findMode(meta.contentType(), path);
     return m != null ? m.mime() : null;
   }
   return null;
  }

  private void injectMode(DiffInfo diffInfo, AsyncCallback<Void> cb) {
    new ModeInjector()
      .add(getContentType(diffInfo.metaA()))
      .add(getContentType(diffInfo.metaB()))
      .inject(cb);
  }

  String getPath() {
    return path;
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
              .add(getContentType(info.metaA()))
              .add(getContentType(info.metaB()))
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
                diffTable.scrollbar.removeDiffAnnotations();
                setShowIntraline(prefs.intralineDifference());
                render(diff);
                chunkManager.adjustPadding();
                skipManager.render(prefs.context(), diff);
              }
            });
          }
        }
      });
  }

  private static FileSize bucketFileSize(DiffInfo diff) {
    FileMeta a = diff.metaA();
    FileMeta b = diff.metaB();
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
