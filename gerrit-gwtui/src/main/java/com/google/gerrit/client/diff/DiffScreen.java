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

import static com.google.gerrit.reviewdb.client.AccountDiffPreference.WHOLE_FILE_CONTEXT;
import static java.lang.Double.POSITIVE_INFINITY;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.JumpKeys;
import com.google.gerrit.client.account.DiffPreferences;
import com.google.gerrit.client.change.ChangeScreen2;
import com.google.gerrit.client.diff.DiffInfo.FileMeta;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.globalkey.client.ShowHelpCommand;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineClassWhere;
import net.codemirror.lib.CodeMirror.LineHandle;
import net.codemirror.lib.KeyMap;
import net.codemirror.lib.ModeInjector;

import java.util.ArrayList;
import java.util.List;

/** Base class for SideBySide2 and Unified2 */
abstract class DiffScreen extends Screen {
  static final KeyMap RENDER_ENTIRE_FILE_KEYMAP = KeyMap.create().on("Ctrl-F",
      false);

  enum FileSize {
    SMALL(0), LARGE(500), HUGE(4000);

    final int lines;

    FileSize(int n) {
      this.lines = n;
    }
  }

  final Change.Id changeId;
  final PatchSet.Id base;
  final PatchSet.Id revision;
  final String path;
  int startLine;
  DiffPreferences prefs;

  HandlerRegistration resizeHandler;
  DiffInfo diff;
  FileSize fileSize;

  private KeyCommandSet keysNavigation;
  private KeyCommandSet keysAction;
  private KeyCommandSet keysComment;
  private List<HandlerRegistration> handlers;
  PreferencesAction prefsAction;
  private int reloadVersionId;

  @UiField(provided = true)
  Header header;

  DiffScreen(PatchSet.Id base, PatchSet.Id revision, String path, int startLine) {
    this.base = base;
    this.revision = revision;
    this.changeId = revision.getParentKey();
    this.path = path;
    this.startLine = startLine;

    prefs = DiffPreferences.create(Gerrit.getAccountDiffPreference());
    handlers = new ArrayList<HandlerRegistration>(6);
    keysNavigation = new KeyCommandSet(Gerrit.C.sectionNavigation());
    header = new Header(keysNavigation, base, revision, path);

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
    JumpKeys.enable(true);
  }

  void removeKeyHandlerRegistrations() {
    for (HandlerRegistration h : handlers) {
      h.removeHandler();
    }
    handlers.clear();
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setHeaderVisible(false);
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
        if (getDiffTable() instanceof SideBySideTable2) {
          ((SideBySideTable2) getDiffTable()).toggleA().run();
        }
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
        CodeMirror[] cms = getCms();
        cms[cms.length - 1].focus();
      }
    }));
  }

  abstract CodeMirror newCm(
      DiffInfo.FileMeta meta,
      String contents,
      DisplaySide side,
      Element parent);

  void render(DiffInfo diff) {
    header.setNoDiff(diff);
    getChunkManager().render(diff);
  }

  void setShowLineNumbers(boolean b) {
    for (CodeMirror cm : getCms()) {
      cm.setOption("lineNumbers", b);
    }
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

  void toggleShowIntraline() {
    prefs.intralineDifference(!prefs.intralineDifference());
    setShowIntraline(prefs.intralineDifference());
    prefsAction.update();
  }

  void setSyntaxHighlighting(boolean b) {
    final CodeMirror[] cms = getCms();
    if (b) {
      injectMode(diff, new AsyncCallback<Void>() {
        @Override
        public void onSuccess(Void result) {
          if (prefs.syntaxHighlighting()) {
            for (CodeMirror cm : cms) {
              cm.setOption("mode", getContentType(diff.meta_a()));
            }
          }
        }

        @Override
        public void onFailure(Throwable caught) {
          prefs.syntaxHighlighting(false);
        }
      });
    } else {
      for (CodeMirror cm : cms) {
        cm.setOption("mode", (String) null);
      }
    }
  }

  void setContext(final int context) {
    operation(new Runnable() {
      @Override
      public void run() {
        getSkipManager().removeAll();
        getSkipManager().render(context, diff);
        getDiffTable().overview.refresh();
      }
    });
  }

  void updateRenderEntireFile() {
    for (CodeMirror cm : getCms()) {
      cm.removeKeyMap(RENDER_ENTIRE_FILE_KEYMAP);
      cm.removeKeyMap(RENDER_ENTIRE_FILE_KEYMAP);
      if (prefs.renderEntireFile()) {
        cm.addKeyMap(RENDER_ENTIRE_FILE_KEYMAP);
      }
      cm.setOption("viewportMargin", prefs.renderEntireFile()
          ? POSITIVE_INFINITY : 10);
    }
  }

  void resizeCodeMirror() {
    int height = getCodeMirrorHeight();
    for (CodeMirror cm : getCms()) {
      cm.setHeight(height);
    }
    getDiffTable().overview.refresh();
  }

  abstract int getCodeMirrorHeight();

  abstract ChunkManager getChunkManager();

  abstract CommentManager getCommentManager();

  abstract SkipManager getSkipManager();

  DiffPreferences getPrefs() {
    return prefs;
  }

  abstract void operation(final Runnable apply);

  Runnable upToChange(final boolean openReplyBox) {
    return new Runnable() {
      public void run() {
        CallbackGroup group = new CallbackGroup();
        getCommentManager().saveAllDrafts(group);
        group.done();
        group.addListener(new GerritCallback<Void>() {
          @Override
          public void onSuccess(Void result) {
            String b = base != null ? String.valueOf(base.get()) : null;
            String rev = String.valueOf(revision.get());
            Gerrit.display(
              PageLinks.toChange(changeId, b, rev),
              new ChangeScreen2(changeId, b, rev, openReplyBox));
          }
        });
      }
    };
  }

  Runnable maybePrevVimSearch(final CodeMirror cm) {
    return new Runnable() {
      @Override
      public void run() {
        if (cm.hasVimSearchHighlight()) {
          CodeMirror.handleVimKey(cm, "N");
        } else {
          getCommentManager().commentNav(cm, Direction.NEXT).run();
        }
      }
    };
  }

  Runnable maybeNextVimSearch(final CodeMirror cm) {
    return new Runnable() {
      @Override
      public void run() {
        if (cm.hasVimSearchHighlight()) {
          CodeMirror.handleVimKey(cm, "n");
        } else {
          getChunkManager().diffChunkNav(cm, Direction.NEXT).run();
        }
      }
    };
  }

  void clearActiveLine(CodeMirror cm) {
    if (cm.hasActiveLine()) {
      LineHandle activeLine = cm.getActiveLine();
      cm.removeLineClass(activeLine,
          LineClassWhere.WRAP, Resources.I.diffTableStyle().activeLine());
      cm.setActiveLine(null);
    }
  }

  boolean canEnableRenderEntireFile(DiffPreferences prefs) {
    return fileSize.compareTo(FileSize.HUGE) < 0
        || (prefs.context() != WHOLE_FILE_CONTEXT && prefs.context() < 100);
  }

  DiffInfo.IntraLineStatus getIntraLineStatus() {
    return diff.intraline_status();
  }

  void setThemeStyles(boolean d) {
    if (d) {
      getDiffTable().addStyleName(Resources.I.diffTableStyle().dark());
    } else {
      getDiffTable().removeStyleName(Resources.I.diffTableStyle().dark());
    }
  }

  void setShowTabs(boolean b) {
    if (b) {
      getDiffTable().addStyleName(Resources.I.diffTableStyle().showTabs());
    } else {
      getDiffTable().removeStyleName(Resources.I.diffTableStyle().showTabs());
    }
  }

  String getContentType(DiffInfo.FileMeta meta) {
    return prefs.syntaxHighlighting() && meta != null
        && meta.content_type() != null ? ModeInjector.getContentType(meta
        .content_type()) : null;
  }

  String getContentType() {
    return getContentType(diff.meta_b());
  }

  void injectMode(DiffInfo diffInfo, AsyncCallback<Void> cb) {
    new ModeInjector().add(getContentType(diffInfo.meta_a()))
        .add(getContentType(diffInfo.meta_b())).inject(cb);
  }

  void prefetchNextFile() {
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
                getSkipManager().removeAll();
                getChunkManager().reset();
                getDiffTable().overview.clearDiffMarkers();
                setShowIntraline(prefs.intralineDifference());
                render(diff);
                getSkipManager().render(prefs.context(), diff);
              }
            });
          }
        }
      });
  }

  static FileSize bucketFileSize(DiffInfo diff) {
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

  abstract CodeMirror[] getCms();

  abstract CodeMirror getCmFromSide(DisplaySide side);

  abstract void setLineLength(int columns);

  abstract DiffTable getDiffTable();
}
