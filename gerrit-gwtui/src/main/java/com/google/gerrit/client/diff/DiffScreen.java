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
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or impl ied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.client.diff;

import static com.google.gerrit.extensions.client.DiffPreferencesInfo.WHOLE_FILE_CONTEXT;
import static java.lang.Double.POSITIVE_INFINITY;

import com.google.gerrit.client.DiffObject;
import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.DiffPreferences;
import com.google.gerrit.client.change.ChangeScreen;
import com.google.gerrit.client.change.FileTable;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeList;
import com.google.gerrit.client.diff.DiffInfo.FileMeta;
import com.google.gerrit.client.diff.LineMapper.LineOnOtherInfo;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.info.ChangeInfo.CommitInfo;
import com.google.gerrit.client.info.ChangeInfo.EditInfo;
import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.info.FileInfo;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.projects.ConfigInfoCache;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DiffView;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.globalkey.client.ShowHelpCommand;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.BeforeSelectionChangeHandler;
import net.codemirror.lib.CodeMirror.GutterClickHandler;
import net.codemirror.lib.CodeMirror.LineHandle;
import net.codemirror.lib.KeyMap;
import net.codemirror.lib.Pos;
import net.codemirror.mode.ModeInfo;
import net.codemirror.mode.ModeInjector;
import net.codemirror.theme.ThemeLoader;

/** Base class for SideBySide and Unified */
abstract class DiffScreen extends Screen {
  private static final KeyMap RENDER_ENTIRE_FILE_KEYMAP =
      KeyMap.create().propagate("Ctrl-F").propagate("Ctrl-G").propagate("Shift-Ctrl-G");

  enum FileSize {
    SMALL(0),
    LARGE(500),
    HUGE(4000);

    final int lines;

    FileSize(int n) {
      this.lines = n;
    }
  }

  private final Project.NameKey project;
  private final Change.Id changeId;
  final DiffObject base;
  final PatchSet.Id revision;
  final String path;
  final DiffPreferences prefs;
  final SkipManager skipManager;

  private DisplaySide startSide;
  private int startLine;
  private Change.Status changeStatus;

  private HandlerRegistration resizeHandler;
  private DiffInfo diff;
  private FileSize fileSize;
  private EditInfo edit;

  private KeyCommandSet keysNavigation;
  private KeyCommandSet keysAction;
  private KeyCommandSet keysComment;
  private List<HandlerRegistration> handlers;
  private PreferencesAction prefsAction;
  private int reloadVersionId;
  private int parents;

  @UiField(provided = true)
  Header header;

  DiffScreen(
      @Nullable Project.NameKey project,
      DiffObject base,
      DiffObject revision,
      String path,
      DisplaySide startSide,
      int startLine,
      DiffView diffScreenType) {
    this.project = project;
    this.base = base;
    this.revision = revision.asPatchSetId();
    this.changeId = revision.asPatchSetId().getParentKey();
    this.path = path;
    this.startSide = startSide;
    this.startLine = startLine;

    prefs = DiffPreferences.create(Gerrit.getDiffPreferences());
    handlers = new ArrayList<>(6);
    keysNavigation = new KeyCommandSet(Gerrit.C.sectionNavigation());
    header = new Header(keysNavigation, project, base, revision, path, diffScreenType, prefs);
    skipManager = new SkipManager(this);
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

    CodeMirror.initLibrary(
        group1.add(
            new AsyncCallback<Void>() {
              final AsyncCallback<Void> themeCallback = group2.addEmpty();

              @Override
              public void onSuccess(Void result) {
                // Load theme after CM library to ensure theme can override CSS.
                ThemeLoader.loadTheme(prefs.theme(), themeCallback);
              }

              @Override
              public void onFailure(Throwable caught) {}
            }));

    DiffApi.diff(revision, Project.NameKey.asStringOrNull(project), path)
        .base(base.asPatchSetId())
        .wholeFile()
        .intraline(prefs.intralineDifference())
        .ignoreWhitespace(prefs.ignoreWhitespace())
        .get(
            group1.addFinal(
                new GerritCallback<DiffInfo>() {
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
      ChangeApi.edit(
          changeId.get(),
          project == null ? null : project.get(),
          group2.add(
              new AsyncCallback<EditInfo>() {
                @Override
                public void onSuccess(EditInfo result) {
                  edit = result;
                }

                @Override
                public void onFailure(Throwable caught) {}
              }));
    }

    final CommentsCollections comments = new CommentsCollections(project, base, revision, path);
    comments.load(group2);

    countParents(group2);

    RestApi call = ChangeApi.detail(changeId.get(), project == null ? null : project.get());
    ChangeList.addOptions(call, EnumSet.of(ListChangesOption.ALL_REVISIONS));
    call.get(
        group2.add(
            new AsyncCallback<ChangeInfo>() {
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
                boolean current =
                    currentRevision != null
                        && revision.get() == info.revision(currentRevision)._number();
                JsArray<RevisionInfo> list = info.revisions().values();
                RevisionInfo.sortRevisionInfoByNumber(list);
                getDiffTable()
                    .set(
                        prefs,
                        list,
                        parents,
                        diff,
                        edit != null,
                        current,
                        changeStatus.isOpen(),
                        diff.binary());
                header.setChangeInfo(info);
              }

              @Override
              public void onFailure(Throwable caught) {}
            }));

    ConfigInfoCache.get(changeId, group2.addFinal(getScreenLoadCallback(comments)));
  }

  private void countParents(CallbackGroup cbg) {
    ChangeApi.revision(changeId.get(), project == null ? null : project.get(), revision.getId())
        .view("commit")
        .get(
            cbg.add(
                new AsyncCallback<CommitInfo>() {
                  @Override
                  public void onSuccess(CommitInfo info) {
                    parents = info.parents().length();
                  }

                  @Override
                  public void onFailure(Throwable caught) {
                    parents = 0;
                  }
                }));
  }

  @Override
  public void onShowView() {
    super.onShowView();

    Window.enableScrolling(false);
    if (prefs.hideTopMenu()) {
      Gerrit.setHeaderVisible(false);
    }
    resizeHandler =
        Window.addResizeHandler(
            new ResizeHandler() {
              @Override
              public void onResize(ResizeEvent event) {
                resizeCodeMirror();
              }
            });
  }

  KeyCommandSet getKeysNavigation() {
    return keysNavigation;
  }

  KeyCommandSet getKeysAction() {
    return keysAction;
  }

  @Override
  protected void onUnload() {
    super.onUnload();

    removeKeyHandlerRegistrations();
    if (getCommentManager() != null) {
      CallbackGroup group = new CallbackGroup();
      getCommentManager().saveAllDrafts(group);
      group.done();
    }
    if (resizeHandler != null) {
      resizeHandler.removeHandler();
      resizeHandler = null;
    }
    for (CodeMirror cm : getCms()) {
      if (cm != null) {
        cm.getWrapperElement().removeFromParent();
      }
    }
    if (prefsAction != null) {
      prefsAction.hide();
    }

    Window.enableScrolling(true);
    Gerrit.setHeaderVisible(true);
  }

  private void removeKeyHandlerRegistrations() {
    for (HandlerRegistration h : handlers) {
      h.removeHandler();
    }
    handlers.clear();
  }

  void registerCmEvents(CodeMirror cm) {
    cm.on("cursorActivity", updateActiveLine(cm));
    cm.on("focus", updateActiveLine(cm));
    KeyMap keyMap =
        KeyMap.create()
            .on("A", upToChange(true))
            .on("U", upToChange(false))
            .on("'['", header.navigate(Direction.PREV))
            .on("']'", header.navigate(Direction.NEXT))
            .on("R", header.toggleReviewed())
            .on("O", getCommentManager().toggleOpenBox(cm))
            .on("N", maybeNextVimSearch(cm))
            .on("Ctrl-Alt-E", openEditScreen(cm))
            .on("P", getChunkManager().diffChunkNav(cm, Direction.PREV))
            .on("Shift-M", header.reviewedAndNext())
            .on("Shift-N", maybePrevVimSearch(cm))
            .on("Shift-P", getCommentManager().commentNav(cm, Direction.PREV))
            .on("Shift-O", getCommentManager().openCloseAll(cm))
            .on(
                "I",
                () -> {
                  switch (getIntraLineStatus()) {
                    case OFF:
                    case OK:
                      toggleShowIntraline();
                      break;
                    case FAILURE:
                    case TIMEOUT:
                    default:
                      break;
                  }
                })
            .on("','", prefsAction::show)
            .on("Shift-/", () -> new ShowHelpCommand().onKeyPress(null))
            .on("Space", () -> cm.vim().handleKey("<C-d>"))
            .on("Shift-Space", () -> cm.vim().handleKey("<C-u>"))
            .on("Ctrl-F", () -> cm.execCommand("find"))
            .on("Ctrl-G", () -> cm.execCommand("findNext"))
            .on("Enter", maybeNextCmSearch(cm))
            .on("Shift-Ctrl-G", () -> cm.execCommand("findPrev"))
            .on("Shift-Enter", () -> cm.execCommand("findPrev"))
            .on(
                "Esc",
                () -> {
                  cm.setCursor(cm.getCursor());
                  cm.execCommand("clearSearch");
                  cm.vim().handleEx("nohlsearch");
                })
            .on("Ctrl-A", () -> cm.execCommand("selectAll"))
            .on("G O", () -> Gerrit.display(PageLinks.toChangeQuery("status:open")))
            .on("G M", () -> Gerrit.display(PageLinks.toChangeQuery("status:merged")))
            .on("G A", () -> Gerrit.display(PageLinks.toChangeQuery("status:abandoned")));
    if (Gerrit.isSignedIn()) {
      keyMap
          .on("G I", () -> Gerrit.display(PageLinks.MINE))
          .on("G D", () -> Gerrit.display(PageLinks.toChangeQuery("owner:self is:draft")))
          .on("G C", () -> Gerrit.display(PageLinks.toChangeQuery("has:draft")))
          .on("G W", () -> Gerrit.display(PageLinks.toChangeQuery("is:watched status:open")))
          .on("G S", () -> Gerrit.display(PageLinks.toChangeQuery("is:starred")));
    }

    if (revision.get() != 0) {
      cm.on("beforeSelectionChange", onSelectionChange(cm));
      cm.on("gutterClick", onGutterClick(cm));
      keyMap.on("C", getCommentManager().newDraftCallback(cm));
    }
    CodeMirror.normalizeKeyMap(keyMap); // Needed to for multi-stroke keymaps
    cm.addKeyMap(keyMap);
  }

  void maybeRegisterRenderEntireFileKeyMap(CodeMirror cm) {
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
        bubble = new InsertCommentBubble(getCommentManager(), cm);
        add(bubble);
        cm.addWidget(anchor, bubble.getElement());
      }
    };
  }

  @Override
  public void registerKeys() {
    super.registerKeys();

    keysNavigation.add(new UpToChangeCommand(project, revision, 0, 'u'));
    keysNavigation.add(
        new NoOpKeyCommand(0, 'j', PatchUtil.C.lineNext()),
        new NoOpKeyCommand(0, 'k', PatchUtil.C.linePrev()));
    keysNavigation.add(
        new NoOpKeyCommand(0, 'n', PatchUtil.C.chunkNext()),
        new NoOpKeyCommand(0, 'p', PatchUtil.C.chunkPrev()));
    keysNavigation.add(
        new NoOpKeyCommand(KeyCommand.M_SHIFT, 'n', PatchUtil.C.commentNext()),
        new NoOpKeyCommand(KeyCommand.M_SHIFT, 'p', PatchUtil.C.commentPrev()));
    keysNavigation.add(new NoOpKeyCommand(KeyCommand.M_CTRL, 'f', Gerrit.C.keySearch()));

    keysAction = new KeyCommandSet(Gerrit.C.sectionActions());
    keysAction.add(new NoOpKeyCommand(0, KeyCodes.KEY_ENTER, PatchUtil.C.expandComment()));
    keysAction.add(new NoOpKeyCommand(0, 'o', PatchUtil.C.expandComment()));
    keysAction.add(
        new NoOpKeyCommand(KeyCommand.M_SHIFT, 'o', PatchUtil.C.expandAllCommentsOnCurrentLine()));
    if (Gerrit.isSignedIn()) {
      keysAction.add(
          new KeyCommand(0, 'r', PatchUtil.C.toggleReviewed()) {
            @Override
            public void onKeyPress(KeyPressEvent event) {
              header.toggleReviewed().run();
            }
          });
      keysAction.add(
          new NoOpKeyCommand(KeyCommand.M_CTRL | KeyCommand.M_ALT, 'e', Gerrit.C.keyEditor()));
    }
    keysAction.add(
        new KeyCommand(KeyCommand.M_SHIFT, 'm', PatchUtil.C.markAsReviewedAndGoToNext()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            header.reviewedAndNext().run();
          }
        });
    keysAction.add(
        new KeyCommand(0, 'a', PatchUtil.C.openReply()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            upToChange(true).run();
          }
        });
    keysAction.add(
        new KeyCommand(0, ',', PatchUtil.C.showPreferences()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            prefsAction.show();
          }
        });
    if (getIntraLineStatus() == DiffInfo.IntraLineStatus.OFF
        || getIntraLineStatus() == DiffInfo.IntraLineStatus.OK) {
      keysAction.add(
          new KeyCommand(0, 'i', PatchUtil.C.toggleIntraline()) {
            @Override
            public void onKeyPress(KeyPressEvent event) {
              toggleShowIntraline();
            }
          });
    }

    if (Gerrit.isSignedIn()) {
      keysAction.add(new NoOpKeyCommand(0, 'c', PatchUtil.C.commentInsert()));
      keysComment = new KeyCommandSet(PatchUtil.C.commentEditorSet());
      keysComment.add(new NoOpKeyCommand(KeyCommand.M_CTRL, 's', PatchUtil.C.commentSaveDraft()));
      keysComment.add(new NoOpKeyCommand(0, KeyCodes.KEY_ESCAPE, PatchUtil.C.commentCancelEdit()));
    } else {
      keysComment = null;
    }
  }

  @Nullable
  public Project.NameKey getProject() {
    return project;
  }

  void registerHandlers() {
    removeKeyHandlerRegistrations();
    handlers.add(GlobalKey.add(this, keysAction));
    handlers.add(GlobalKey.add(this, keysNavigation));
    if (keysComment != null) {
      handlers.add(GlobalKey.add(this, keysComment));
    }
    handlers.add(ShowHelpCommand.addFocusHandler(getFocusHandler()));
  }

  void setupSyntaxHighlighting() {
    if (prefs.syntaxHighlighting() && fileSize.compareTo(FileSize.SMALL) > 0) {
      Scheduler.get()
          .scheduleFixedDelay(
              new RepeatingCommand() {
                @Override
                public boolean execute() {
                  if (prefs.syntaxHighlighting() && isAttached()) {
                    setSyntaxHighlighting(prefs.syntaxHighlighting());
                  }
                  return false;
                }
              },
              250);
    }
  }

  abstract CodeMirror newCm(DiffInfo.FileMeta meta, String contents, Element parent);

  void render(DiffInfo diff) {
    header.setNoDiff(diff);
    getChunkManager().render(diff);
  }

  void setShowLineNumbers(boolean b) {
    if (b) {
      getDiffTable().addStyleName(Resources.I.diffTableStyle().showLineNumbers());
    } else {
      getDiffTable().removeStyleName(Resources.I.diffTableStyle().showLineNumbers());
    }
  }

  void setShowIntraline(boolean b) {
    if (b && getIntraLineStatus() == DiffInfo.IntraLineStatus.OFF) {
      reloadDiffInfo();
    } else if (b) {
      getDiffTable().removeStyleName(Resources.I.diffTableStyle().noIntraline());
    } else {
      getDiffTable().addStyleName(Resources.I.diffTableStyle().noIntraline());
    }
  }

  private void toggleShowIntraline() {
    prefs.intralineDifference(!Boolean.valueOf(prefs.intralineDifference()));
    setShowIntraline(prefs.intralineDifference());
    prefsAction.update();
  }

  abstract void setSyntaxHighlighting(boolean b);

  void setContext(int context) {
    operation(
        () -> {
          skipManager.removeAll();
          skipManager.render(context, diff);
          updateRenderEntireFile();
        });
  }

  private int adjustCommitMessageLine(int line) {
    /* When commit messages are shown in the diff screen they include
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
    }
    return line - offset;
  }

  private Runnable openEditScreen(CodeMirror cm) {
    return () -> {
      LineHandle handle = cm.extras().activeLine();
      int line = cm.getLineNumber(handle) + 1;
      if (Patch.COMMIT_MSG.equals(path)) {
        line = adjustCommitMessageLine(line);
      }
      String token = Dispatcher.toEditScreen(project, revision, path, line);
      if (!Gerrit.isSignedIn()) {
        Gerrit.doSignIn(token);
      } else {
        Gerrit.display(token);
      }
    };
  }

  void updateRenderEntireFile() {
    boolean entireFile = renderEntireFile();
    for (CodeMirror cm : getCms()) {
      cm.removeKeyMap(RENDER_ENTIRE_FILE_KEYMAP);
      if (entireFile) {
        cm.addKeyMap(RENDER_ENTIRE_FILE_KEYMAP);
      }
      cm.setOption("viewportMargin", entireFile ? POSITIVE_INFINITY : 10);
    }
  }

  void resizeCodeMirror() {
    int height = header.getOffsetHeight() + getDiffTable().getHeaderHeight();
    for (CodeMirror cm : getCms()) {
      cm.adjustHeight(height);
    }
  }

  abstract ChunkManager getChunkManager();

  abstract CommentManager getCommentManager();

  Change.Status getChangeStatus() {
    return changeStatus;
  }

  int getStartLine() {
    return startLine;
  }

  void setStartLine(int startLine) {
    this.startLine = startLine;
  }

  DisplaySide getStartSide() {
    return startSide;
  }

  void setStartSide(DisplaySide startSide) {
    this.startSide = startSide;
  }

  DiffInfo getDiff() {
    return diff;
  }

  FileSize getFileSize() {
    return fileSize;
  }

  PreferencesAction getPrefsAction() {
    return prefsAction;
  }

  void setPrefsAction(PreferencesAction prefsAction) {
    this.prefsAction = prefsAction;
  }

  abstract void operation(Runnable apply);

  private Runnable upToChange(boolean openReplyBox) {
    return () -> {
      CallbackGroup group = new CallbackGroup();
      getCommentManager().saveAllDrafts(group);
      group.done();
      group.addListener(
          new GerritCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
              String rev = String.valueOf(revision.get());
              Gerrit.display(
                  PageLinks.toChange(project, changeId, base.asString(), rev),
                  new ChangeScreen(changeId, null, base, rev, openReplyBox, FileTable.Mode.REVIEW));
            }
          });
    };
  }

  private Runnable maybePrevVimSearch(CodeMirror cm) {
    return () -> {
      if (cm.vim().hasSearchHighlight()) {
        cm.vim().handleKey("N");
      } else {
        getCommentManager().commentNav(cm, Direction.NEXT).run();
      }
    };
  }

  private Runnable maybeNextVimSearch(CodeMirror cm) {
    return () -> {
      if (cm.vim().hasSearchHighlight()) {
        cm.vim().handleKey("n");
      } else {
        getChunkManager().diffChunkNav(cm, Direction.NEXT).run();
      }
    };
  }

  Runnable maybeNextCmSearch(CodeMirror cm) {
    return () -> {
      if (cm.hasSearchHighlight()) {
        cm.execCommand("findNext");
      } else {
        cm.execCommand("clearSearch");
        getCommentManager().toggleOpenBox(cm).run();
      }
    };
  }

  boolean renderEntireFile() {
    return prefs.renderEntireFile() && canRenderEntireFile(prefs);
  }

  boolean canRenderEntireFile(DiffPreferences prefs) {
    // CodeMirror is too slow to layout an entire huge file.
    return fileSize.compareTo(FileSize.HUGE) < 0
        || (prefs.context() != WHOLE_FILE_CONTEXT && prefs.context() < 100);
  }

  DiffInfo.IntraLineStatus getIntraLineStatus() {
    return diff.intralineStatus();
  }

  void setThemeStyles(boolean d) {
    if (d) {
      getDiffTable().addStyleName(Resources.I.diffTableStyle().dark());
    } else {
      getDiffTable().removeStyleName(Resources.I.diffTableStyle().dark());
    }
  }

  void setShowTabs(boolean show) {
    for (CodeMirror cm : getCms()) {
      cm.extras().showTabs(show);
    }
  }

  void setLineLength(int columns) {
    for (CodeMirror cm : getCms()) {
      cm.extras().lineLength(columns);
    }
  }

  String getContentType(DiffInfo.FileMeta meta) {
    if (prefs.syntaxHighlighting() && meta != null && meta.contentType() != null) {
      ModeInfo m = ModeInfo.findMode(meta.contentType(), path);
      return m != null ? m.mime() : null;
    }
    return null;
  }

  String getContentType() {
    return getContentType(diff.metaB());
  }

  void injectMode(DiffInfo diffInfo, AsyncCallback<Void> cb) {
    new ModeInjector()
        .add(getContentType(diffInfo.metaA()))
        .add(getContentType(diffInfo.metaB()))
        .inject(cb);
  }

  abstract void setAutoHideDiffHeader(boolean hide);

  void prefetchNextFile() {
    String nextPath = header.getNextPath();
    if (nextPath != null) {
      DiffApi.diff(revision, Project.NameKey.asStringOrNull(project), nextPath)
          .base(base.asPatchSetId())
          .wholeFile()
          .intraline(prefs.intralineDifference())
          .ignoreWhitespace(prefs.ignoreWhitespace())
          .get(
              new AsyncCallback<DiffInfo>() {
                @Override
                public void onSuccess(DiffInfo info) {
                  new ModeInjector()
                      .add(getContentType(info.metaA()))
                      .add(getContentType(info.metaB()))
                      .inject(CallbackGroup.<Void>emptyCallback());
                }

                @Override
                public void onFailure(Throwable caught) {}
              });
    }
  }

  void reloadDiffInfo() {
    int id = ++reloadVersionId;
    DiffApi.diff(revision, Project.NameKey.asStringOrNull(project), path)
        .base(base.asPatchSetId())
        .wholeFile()
        .intraline(prefs.intralineDifference())
        .ignoreWhitespace(prefs.ignoreWhitespace())
        .get(
            new GerritCallback<DiffInfo>() {
              @Override
              public void onSuccess(DiffInfo diffInfo) {
                if (id == reloadVersionId && isAttached()) {
                  diff = diffInfo;
                  operation(
                      () -> {
                        skipManager.removeAll();
                        getChunkManager().reset();
                        getDiffTable().scrollbar.removeDiffAnnotations();
                        setShowIntraline(prefs.intralineDifference());
                        render(diff);
                        skipManager.render(prefs.context(), diff);
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
      if ((a != null && s.lines <= a.lines()) || (b != null && s.lines <= b.lines())) {
        return s;
      }
    }
    return FileSize.SMALL;
  }

  abstract Runnable updateActiveLine(CodeMirror cm);

  private GutterClickHandler onGutterClick(final CodeMirror cm) {
    return new GutterClickHandler() {
      @Override
      public void handle(
          CodeMirror instance, final int line, final String gutterClass, NativeEvent clickEvent) {
        if (Element.as(clickEvent.getEventTarget()).hasClassName(getLineNumberClassName())
            && clickEvent.getButton() == NativeEvent.BUTTON_LEFT
            && !clickEvent.getMetaKey()
            && !clickEvent.getAltKey()
            && !clickEvent.getCtrlKey()
            && !clickEvent.getShiftKey()) {
          cm.setCursor(Pos.create(line));
          Scheduler.get()
              .scheduleDeferred(
                  new ScheduledCommand() {
                    @Override
                    public void execute() {
                      getCommentManager().newDraftOnGutterClick(cm, gutterClass, line + 1);
                    }
                  });
        }
      }
    };
  }

  abstract FocusHandler getFocusHandler();

  abstract CodeMirror[] getCms();

  abstract CodeMirror getCmFromSide(DisplaySide side);

  abstract DiffTable getDiffTable();

  abstract int getCmLine(int line, DisplaySide side);

  abstract String getLineNumberClassName();

  LineOnOtherInfo lineOnOther(DisplaySide side, int line) {
    return getChunkManager().lineMapper.lineOnOther(side, line);
  }

  abstract ScreenLoadCallback<ConfigInfoCache.Entry> getScreenLoadCallback(
      CommentsCollections comments);

  abstract boolean isSideBySide();
}
