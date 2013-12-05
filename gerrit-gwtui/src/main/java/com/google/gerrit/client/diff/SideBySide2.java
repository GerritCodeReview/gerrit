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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.change.ChangeScreen2;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.changes.ChangeList;
import com.google.gerrit.client.changes.CommentApi;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.diff.DiffInfo.Region;
import com.google.gerrit.client.diff.DiffInfo.Span;
import com.google.gerrit.client.diff.LineMapper.LineOnOtherInfo;
import com.google.gerrit.client.diff.PaddingManager.LinePaddingWidgetWrapper;
import com.google.gerrit.client.diff.PaddingManager.PaddingWidgetWrapper;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.patches.SkippedLine;
import com.google.gerrit.client.projects.ConfigInfoCache;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.changes.ListChangesOption;
import com.google.gerrit.common.changes.Side;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style.Unit;
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
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.globalkey.client.ShowHelpCommand;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.GutterClickHandler;
import net.codemirror.lib.CodeMirror.LineClassWhere;
import net.codemirror.lib.CodeMirror.LineHandle;
import net.codemirror.lib.CodeMirror.RenderLineHandler;
import net.codemirror.lib.CodeMirror.Viewport;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.KeyMap;
import net.codemirror.lib.LineCharacter;
import net.codemirror.lib.LineWidget;
import net.codemirror.lib.ModeInjector;
import net.codemirror.lib.TextMarker.FromTo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SideBySide2 extends Screen {
  interface Binder extends UiBinder<FlowPanel, SideBySide2> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  private static final JsArrayString EMPTY =
      JavaScriptObject.createArray().cast();

  @UiField(provided = true)
  Header header;

  @UiField(provided = true)
  DiffTable diffTable;

  private final Change.Id changeId;
  private final PatchSet.Id base;
  private final PatchSet.Id revision;
  private final String path;
  private AccountDiffPreference pref;

  private CodeMirror cmA;
  private CodeMirror cmB;
  private ScrollSynchronizer scrollingGlue;
  private HandlerRegistration resizeHandler;
  private JsArray<CommentInfo> publishedBase;
  private JsArray<CommentInfo> publishedRevision;
  private JsArray<CommentInfo> draftsBase;
  private JsArray<CommentInfo> draftsRevision;
  private DiffInfo diff;
  private LineMapper mapper;
  private CommentLinkProcessor commentLinkProcessor;
  private Map<String, PublishedBox> publishedMap;
  private Map<LineHandle, CommentBox> lineActiveBoxMap;
  private Map<LineHandle, List<PublishedBox>> linePublishedBoxesMap;
  private Map<LineHandle, PaddingManager> linePaddingManagerMap;
  private Map<LineHandle, LinePaddingWidgetWrapper> linePaddingOnOtherSideMap;
  private List<DiffChunkInfo> diffChunks;
  private List<SkippedLine> skips;
  private Set<DraftBox> unsaved;
  private int context;

  private KeyCommandSet keysNavigation;
  private KeyCommandSet keysAction;
  private KeyCommandSet keysComment;
  private List<HandlerRegistration> handlers;
  private List<Runnable> deferred;

  public SideBySide2(
      PatchSet.Id base,
      PatchSet.Id revision,
      String path) {
    this.base = base;
    this.revision = revision;
    this.changeId = revision.getParentKey();
    this.path = path;

    pref = Gerrit.getAccountDiffPreference();
    if (pref == null) {
      pref = AccountDiffPreference.createDefault(null);
    }
    context = pref.getContext();

    unsaved = new HashSet<DraftBox>();
    handlers = new ArrayList<HandlerRegistration>(6);
    // TODO: Re-implement necessary GlobalKey bindings.
    addDomHandler(GlobalKey.STOP_PROPAGATION, KeyPressEvent.getType());
    keysNavigation = new KeyCommandSet(Gerrit.C.sectionNavigation());
    header = new Header(keysNavigation, base, revision, path);
    diffTable = new DiffTable(this, base, revision, path);
    add(uiBinder.createAndBindUi(this));
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
      .intraline(pref.isIntralineDifference())
      .ignoreWhitespace(pref.getIgnoreWhitespace())
      .get(cmGroup.addFinal(new GerritCallback<DiffInfo>() {
        @Override
        public void onSuccess(DiffInfo diffInfo) {
          diff = diffInfo;
          new ModeInjector()
            .add(getContentType(diff.meta_a()))
            .add(getContentType(diff.meta_b()))
            .inject(modeInjectorCb);
        }
      }));

    if (base != null) {
      CommentApi.comments(base, group.add(getCommentCallback(DisplaySide.A, false)));
    }
    CommentApi.comments(revision, group.add(getCommentCallback(DisplaySide.B, false)));

    if (Gerrit.isSignedIn()) {
      if (base != null) {
        CommentApi.drafts(base, group.add(getCommentCallback(DisplaySide.A, true)));
      }
      CommentApi.drafts(revision, group.add(getCommentCallback(DisplaySide.B, true)));
    }

    RestApi call = ChangeApi.detail(changeId.get());
    ChangeList.addOptions(call, EnumSet.of(
        ListChangesOption.ALL_REVISIONS));
    call.get(group.add(new GerritCallback<ChangeInfo>() {
      @Override
      public void onSuccess(ChangeInfo info) {
        info.revisions().copyKeysIntoChildren("name");
        JsArray<RevisionInfo> list = info.revisions().values();
        RevisionInfo.sortRevisionInfoByNumber(list);
        diffTable.setUpPatchSetNav(list, diff);
      }}));

    ConfigInfoCache.get(changeId, group.addFinal(
        new ScreenLoadCallback<ConfigInfoCache.Entry>(SideBySide2.this) {
          @Override
          protected void preDisplay(ConfigInfoCache.Entry result) {
            commentLinkProcessor = result.getCommentLinkProcessor();
            setTheme(result.getTheme());

            DiffInfo diffInfo = diff;
            diff = null;
            display(diffInfo);
          }
        }));
  }

  @Override
  public void onShowView() {
    super.onShowView();
    Window.enableScrolling(false);

    final int height = getCodeMirrorHeight();
    operation(new Runnable() {
      @Override
      public void run() {
        cmA.setHeight(height);
        cmB.setHeight(height);
        cmA.refresh();
        cmB.refresh();
        cmB.setCursor(LineCharacter.create(0));
        cmB.focus();
      }
    });
    diffTable.sidePanel.adjustGutters(cmB);

    prefetchNextFile();
  }

  @Override
  protected void onUnload() {
    super.onUnload();

    saveAllDrafts(null);
    removeKeyHandlerRegs();
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

    Window.enableScrolling(true);
    Gerrit.setHeaderVisible(true);
  }

  private void removeKeyHandlerRegs() {
    for (HandlerRegistration h : handlers) {
      h.removeHandler();
    }
    handlers.clear();
  }

  private void registerCmEvents(final CodeMirror cm) {
    cm.on("cursorActivity", updateActiveLine(cm));
    cm.on("gutterClick", onGutterClick(cm));
    cm.on("renderLine", resizeLinePadding(getSideFromCm(cm)));
    cm.on("viewportChange", adjustGutters(cm));
    cm.on("focus", new Runnable() {
      @Override
      public void run() {
        updateActiveLine(cm).run();
      }
    });
    cm.addKeyMap(KeyMap.create()
        .on("A", upToChange(true))
        .on("U", upToChange(false))
        .on("R", toggleReviewed())
        .on("O", toggleOpenBox(cm))
        .on("Enter", toggleOpenBox(cm))
        .on("C", insertNewDraft(cm))
        .on("Alt-U", new Runnable() {
          public void run() {
            cm.getInputField().blur();
            clearActiveLine(cm);
            clearActiveLine(otherCm(cm));
          }
        })
        .on("[", new Runnable() {
          @Override
          public void run() {
            (header.hasPrev() ? header.prev : header.up).go();
          }
        })
        .on("]", new Runnable() {
          @Override
          public void run() {
            (header.hasNext() ? header.next : header.up).go();
          }
        })
        .on("Shift-/", new Runnable() {
          @Override
          public void run() {
            new ShowHelpCommand().onKeyPress(null);
          }
        })
        .on("Ctrl-F", new Runnable() {
          @Override
          public void run() {
            CodeMirror.handleVimKey(cm, "/");
          }
        })
        .on("Space", new Runnable() {
          @Override
          public void run() {
            CodeMirror.handleVimKey(cm, "<PageDown>");
          }
        })
        .on("Ctrl-A", new Runnable() {
          @Override
          public void run() {
            cm.execCommand("selectAll");
          }
        })
        .on("N", maybeNextVimSearch(cm))
        .on("P", diffChunkNav(cm, true))
        .on("Shift-O", openClosePublished(cm))
        .on("Shift-Left", flipCursorSide(cm, true))
        .on("Shift-Right", flipCursorSide(cm, false)));
  }

  @Override
  public void registerKeys() {
    super.registerKeys();

    keysNavigation.add(new UpToChangeCommand2(revision, 0, 'u'));
    keysNavigation.add(new NoOpKeyCommand(0, 'j', PatchUtil.C.lineNext()));
    keysNavigation.add(new NoOpKeyCommand(0, 'k', PatchUtil.C.linePrev()));
    keysNavigation.add(new NoOpKeyCommand(0, 'n', PatchUtil.C.chunkNext2()));
    keysNavigation.add(new NoOpKeyCommand(0, 'p', PatchUtil.C.chunkPrev2()));

    keysAction = new KeyCommandSet(Gerrit.C.sectionActions());
    keysAction.add(new NoOpKeyCommand(0, KeyCodes.KEY_ENTER,
        PatchUtil.C.expandComment()));
    keysAction.add(new NoOpKeyCommand(0, 'o', PatchUtil.C.expandComment()));
    keysAction.add(new NoOpKeyCommand(
        KeyCommand.M_SHIFT, 'o', PatchUtil.C.expandAllCommentsOnCurrentLine()));
    keysAction.add(new KeyCommand(0, 'r', PatchUtil.C.toggleReviewed()) {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        toggleReviewed().run();
      }
    });
    keysAction.add(new KeyCommand(0, 'a', PatchUtil.C.openReply()) {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        upToChange(true).run();
      }
    });

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
    removeKeyHandlerRegs();
    handlers.add(GlobalKey.add(this, keysNavigation));
    if (keysComment != null) {
      handlers.add(GlobalKey.add(this, keysComment));
    }
    handlers.add(GlobalKey.add(this, keysAction));
  }

  private GerritCallback<NativeMap<JsArray<CommentInfo>>> getCommentCallback(
      final DisplaySide side, final boolean toDrafts) {
    return new GerritCallback<NativeMap<JsArray<CommentInfo>>>() {
      @Override
      public void onSuccess(NativeMap<JsArray<CommentInfo>> result) {
        JsArray<CommentInfo> in = result.get(path);
        if (in != null) {
          if (toDrafts) {
            if (side == DisplaySide.A) {
              draftsBase = in;
            } else {
              draftsRevision = in;
            }
          } else {
            if (side == DisplaySide.A) {
              publishedBase = in;
            } else {
              publishedRevision = in;
            }
          }
        }
      }
    };
  }

  private void display(final DiffInfo diffInfo) {
    skips = new ArrayList<SkippedLine>();
    linePaddingOnOtherSideMap = new HashMap<LineHandle, LinePaddingWidgetWrapper>();
    diffChunks = new ArrayList<DiffChunkInfo>();
    lineActiveBoxMap = new HashMap<LineHandle, CommentBox>();
    linePublishedBoxesMap = new HashMap<LineHandle, List<PublishedBox>>();
    linePaddingManagerMap = new HashMap<LineHandle, PaddingManager>();
    if (publishedBase != null || publishedRevision != null) {
      publishedMap = new HashMap<String, PublishedBox>();
    }

    if (pref.isShowTabs()) {
      diffTable.addStyleName(DiffTable.style.showtabs());
    }

    cmA = createCodeMirror(diffInfo.meta_a(), diffInfo.text_a(), diffTable.cmA);
    cmB = createCodeMirror(diffInfo.meta_b(), diffInfo.text_b(), diffTable.cmB);

    cmA.operation(new Runnable() {
      @Override
      public void run() {
        cmB.operation(new Runnable() {
          @Override
          public void run() {
            // Estimate initial CM3 height, fixed up in onShowView.
            int height = Window.getClientHeight()
                - (Gerrit.getHeaderFooterHeight() + 18);
            cmA.setHeight(height);
            cmB.setHeight(height);

            render(diffInfo);
            if (publishedBase != null) {
              renderPublished(publishedBase);
            }
            if (publishedRevision != null) {
              renderPublished(publishedRevision);
            }
            if (draftsBase != null) {
              renderDrafts(draftsBase);
            }
            if (draftsRevision != null) {
              renderDrafts(draftsRevision);
            }
            renderSkips();
          }
        });
      }
    });

    registerCmEvents(cmA);
    registerCmEvents(cmB);

    scrollingGlue = GWT.create(ScrollSynchronizer.class);
    scrollingGlue.init(diffTable, cmA, cmB, mapper);
    resizeHandler = Window.addResizeHandler(new ResizeHandler() {
      @Override
      public void onResize(ResizeEvent event) {
        resizeCodeMirror();
      }
    });
  }

  private CodeMirror createCodeMirror(
      DiffInfo.FileMeta meta,
      String contents,
      Element parent) {
    Configuration cfg = Configuration.create()
      .set("readOnly", true)
      .set("cursorBlinkRate", 0)
      .set("cursorHeight", 0.85)
      .set("lineNumbers", true)
      .set("tabSize", pref.getTabSize())
      .set("mode", getContentType(meta))
      .set("lineWrapping", false)
      .set("styleSelectedText", true)
      .set("showTrailingSpace", pref.isShowWhitespaceErrors())
      .set("keyMap", "vim_ro")
      .set("value", meta != null ? contents : "");
    return CodeMirror.create(parent, cfg);
  }

  private void render(DiffInfo diff) {
    JsArray<Region> regions = diff.content();
    if (!(regions.length() == 0 ||
        regions.length() == 1 && regions.get(0).ab() != null)) {
      header.removeNoDiff();
    }

    String diffColor = diff.meta_a() == null || diff.meta_b() == null
        ? DiffTable.style.intralineBg()
        : DiffTable.style.diff();
    mapper = new LineMapper();
    for (int i = 0; i < regions.length(); i++) {
      Region current = regions.get(i);
      int origLineA = mapper.getLineA();
      int origLineB = mapper.getLineB();
      if (current.ab() != null) { // Common
        int length = current.ab().length();
        mapper.appendCommon(length);
        if (i == 0 && length > context + 1) {
          skips.add(new SkippedLine(0, 0, length - context));
        } else if (i == regions.length() - 1 && length > context + 1) {
          skips.add(new SkippedLine(origLineA + context, origLineB + context,
              length - context));
        } else if (length > 2 * context + 1) {
          skips.add(new SkippedLine(origLineA + context, origLineB + context,
              length - 2 * context));
        }
      } else { // Insert, Delete or Edit
        JsArrayString currentA = current.a() == null ? EMPTY : current.a();
        JsArrayString currentB = current.b() == null ? EMPTY : current.b();
        int aLength = currentA.length();
        int bLength = currentB.length();
        String color = currentA == EMPTY || currentB == EMPTY
            ? diffColor
            : DiffTable.style.intralineBg();
        colorLines(cmA, color, origLineA, aLength);
        colorLines(cmB, color, origLineB, bLength);
        int commonCnt = Math.min(aLength, bLength);
        mapper.appendCommon(commonCnt);
        if (aLength < bLength) { // Edit with insertion
          int insertCnt = bLength - aLength;
          mapper.appendInsert(insertCnt);
        } else if (aLength > bLength) { // Edit with deletion
          int deleteCnt = aLength - bLength;
          mapper.appendDelete(deleteCnt);
        }
        int chunkEndA = mapper.getLineA() - 1;
        int chunkEndB = mapper.getLineB() - 1;
        if (aLength > 0) {
          addDiffChunkAndPadding(cmB, chunkEndB, chunkEndA, aLength, bLength > 0);
        }
        if (bLength > 0) {
          addDiffChunkAndPadding(cmA, chunkEndA, chunkEndB, bLength, aLength > 0);
        }
        markEdit(cmA, currentA, current.edit_a(), origLineA);
        markEdit(cmB, currentB, current.edit_b(), origLineB);
        if (aLength == 0) {
          diffTable.sidePanel.addGutter(cmB, origLineB, SidePanel.GutterType.INSERT);
        } else if (bLength == 0) {
          diffTable.sidePanel.addGutter(cmA, origLineA, SidePanel.GutterType.DELETE);
        } else {
          diffTable.sidePanel.addGutter(cmB, origLineB, SidePanel.GutterType.EDIT);
        }
      }
    }
  }

  private DraftBox addNewDraft(CodeMirror cm, int line, FromTo fromTo) {
    DisplaySide side = getSideFromCm(cm);
    return addDraftBox(CommentInfo.createRange(
        path,
        getStoredSideFromDisplaySide(side),
        line + 1,
        null,
        null,
        CommentRange.create(fromTo)), side);
  }

  CommentInfo createReply(CommentInfo replyTo) {
    if (!replyTo.has_line() && replyTo.range() == null) {
      return CommentInfo.createFile(path, replyTo.side(), replyTo.id(), null);
    } else {
      return CommentInfo.createRange(path, replyTo.side(), replyTo.line(),
          replyTo.id(), null, replyTo.range());
    }
  }

  DraftBox addDraftBox(CommentInfo info, DisplaySide side) {
    CodeMirror cm = getCmFromSide(side);
    final DraftBox box = new DraftBox(this, cm, side, commentLinkProcessor,
        getPatchSetIdFromSide(side), info);
    if (info.id() == null) {
      Scheduler.get().scheduleDeferred(new ScheduledCommand() {
        @Override
        public void execute() {
          box.setOpen(true);
          box.setEdit(true);
        }
      });
    }
    if (!info.has_line()) {
      return box;
    }
    addCommentBox(info, box);
    box.setVisible(true);
    LineHandle handle = cm.getLineHandle(info.line() - 1);
    lineActiveBoxMap.put(handle, box);
    return box;
  }

  CommentBox addCommentBox(CommentInfo info, final CommentBox box) {
    box.setParent(this);
    diffTable.add(box);
    DisplaySide side = box.getSide();
    CodeMirror cm = getCmFromSide(side);
    CodeMirror other = otherCm(cm);
    int line = info.line() - 1; // CommentInfo is 1-based, but CM is 0-based
    LineHandle handle = cm.getLineHandle(line);
    PaddingManager manager;
    if (linePaddingManagerMap.containsKey(handle)) {
      manager = linePaddingManagerMap.get(handle);
    } else {
      // Estimated height at 28px, fixed by deferring after display
      manager = new PaddingManager(addPaddingWidget(cm, line, 0, Unit.PX, 0));
      linePaddingManagerMap.put(handle, manager);
    }
    int lineToPad = mapper.lineOnOther(side, line).getLine();
    LineHandle otherHandle = other.getLineHandle(lineToPad);
    DiffChunkInfo myChunk = getDiffChunk(side, line);
    DiffChunkInfo otherChunk = getDiffChunk(getSideFromCm(other), lineToPad);
    PaddingManager otherManager;
    if (linePaddingManagerMap.containsKey(otherHandle)) {
      otherManager = linePaddingManagerMap.get(otherHandle);
    } else {
      otherManager =
          new PaddingManager(addPaddingWidget(other, lineToPad, 0, Unit.PX, 0));
      linePaddingManagerMap.put(otherHandle, otherManager);
    }
    if ((myChunk == null && otherChunk == null) || (myChunk != null && otherChunk != null)) {
      PaddingManager.link(manager, otherManager);
    }
    int index = manager.getCurrentCount();
    manager.insert(box, index);
    Configuration config = Configuration.create()
      .set("coverGutter", true)
      .set("insertAt", index)
      .set("noHScroll", true);
    LineWidget boxWidget = addLineWidget(cm, line, box, config);
    box.setPaddingManager(manager);
    box.setSelfWidgetWrapper(new PaddingWidgetWrapper(boxWidget, box.getElement()));
    if (otherChunk == null) {
      box.setDiffChunkInfo(myChunk);
    }
    box.setGutterWrapper(diffTable.sidePanel.addGutter(cm, info.line() - 1,
        box instanceof DraftBox
          ? SidePanel.GutterType.DRAFT
          : SidePanel.GutterType.COMMENT));
    if (box instanceof DraftBox) {
      boxWidget.onRedraw(new Runnable() {
        @Override
        public void run() {
          DraftBox draftBox = (DraftBox) box;
          if (draftBox.isEdit()) {
            draftBox.editArea.setFocus(true);
          }
        }
      });
    }
    return box;
  }

  void removeDraft(DraftBox box, int line) {
    LineHandle handle = getCmFromSide(box.getSide()).getLineHandle(line);
    lineActiveBoxMap.remove(handle);
    if (linePublishedBoxesMap.containsKey(handle)) {
      List<PublishedBox> list = linePublishedBoxesMap.get(handle);
      lineActiveBoxMap.put(handle, list.get(list.size() - 1));
    }
    unsaved.remove(box);
  }

  void updateUnsaved(DraftBox box, boolean isUnsaved) {
    if (isUnsaved) {
      unsaved.add(box);
    } else {
      unsaved.remove(box);
    }
  }

  private void saveAllDrafts(CallbackGroup cb) {
    for (DraftBox box : unsaved) {
      box.save(cb);
    }
  }

  void addFileCommentBox(CommentBox box) {
    diffTable.addFileCommentBox(box);
  }

  void removeFileCommentBox(DraftBox box) {
    diffTable.onRemoveDraftBox(box);
  }

  private List<CommentInfo> sortComment(JsArray<CommentInfo> unsorted) {
    List<CommentInfo> sorted = new ArrayList<CommentInfo>();
    for (int i = 0; i < unsorted.length(); i++) {
      sorted.add(unsorted.get(i));
    }
    Collections.sort(sorted, new Comparator<CommentInfo>() {
      @Override
      public int compare(CommentInfo o1, CommentInfo o2) {
        return o1.updated().compareTo(o2.updated());
      }
    });
    return sorted;
  }

  private void renderPublished(JsArray<CommentInfo> published) {
    List<CommentInfo> sorted = sortComment(published);
    for (CommentInfo info : sorted) {
      DisplaySide side;
      if (info.side() == Side.PARENT) {
        if (base != null) {
          continue;
        }
        side = DisplaySide.A;
      } else {
        side = published == publishedBase ? DisplaySide.A : DisplaySide.B;
      }
      CodeMirror cm = getCmFromSide(side);
      PublishedBox box = new PublishedBox(this, cm, side, commentLinkProcessor,
          getPatchSetIdFromSide(side), info);
      publishedMap.put(info.id(), box);
      if (!info.has_line()) {
        diffTable.addFileCommentBox(box);
        continue;
      }
      int line = info.line() - 1;
      LineHandle handle = cm.getLineHandle(line);
      if (linePublishedBoxesMap.containsKey(handle)) {
        linePublishedBoxesMap.get(handle).add(box);
      } else {
        List<PublishedBox> list = new ArrayList<PublishedBox>();
        list.add(box);
        linePublishedBoxesMap.put(handle, list);
      }
      lineActiveBoxMap.put(handle, box);
      addCommentBox(info, box);
    }
  }

  private void renderDrafts(JsArray<CommentInfo> drafts) {
    List<CommentInfo> sorted = sortComment(drafts);
    for (CommentInfo info : sorted) {
      DisplaySide side;
      if (info.side() == Side.PARENT) {
        if (base != null) {
          continue;
        }
        side = DisplaySide.A;
      } else {
        side = drafts == draftsBase ? DisplaySide.A : DisplaySide.B;
      }
      DraftBox box = new DraftBox(
          this, getCmFromSide(side), side, commentLinkProcessor,
          getPatchSetIdFromSide(side), info);
      if (publishedBase != null || publishedRevision != null) {
        PublishedBox replyToBox = publishedMap.get(info.in_reply_to());
        if (replyToBox != null) {
          replyToBox.registerReplyBox(box);
        }
      }
      if (!info.has_line()) {
        diffTable.addFileCommentBox(box);
        continue;
      }
      lineActiveBoxMap.put(
          getCmFromSide(side).getLineHandle(info.line() - 1), box);
      addCommentBox(info, box);
    }
  }

  private void renderSkips() {
    if (context == AccountDiffPreference.WHOLE_FILE_CONTEXT) {
      return;
    }

    /**
     * TODO: This is not optimal, but shouldn't bee too costly in most cases.
     * Maybe rewrite after done keeping track of diff chunk positions.
     */
    for (CommentBox box : lineActiveBoxMap.values()) {
      List<SkippedLine> temp = new ArrayList<SkippedLine>();
      for (SkippedLine skip : skips) {
        CommentInfo info = box.getCommentInfo();
        int startLine = box.getSide() == DisplaySide.A
            ? skip.getStartA()
            : skip.getStartB();
        int boxLine = info.line();
        int deltaBefore = boxLine - startLine;
        int deltaAfter = startLine + skip.getSize() - boxLine;
        if (deltaBefore < -context || deltaAfter < -context) {
          temp.add(skip); // Size guaranteed to be greater than 1
        } else if (deltaBefore > context && deltaAfter > context) {
          SkippedLine before = new SkippedLine(
              skip.getStartA(), skip.getStartB(),
              skip.getSize() - deltaAfter - context);
          skip.incrementStart(deltaBefore + context);
          checkAndAddSkip(temp, before);
          checkAndAddSkip(temp, skip);
        } else if (deltaAfter > context) {
          skip.incrementStart(deltaBefore + context);
          checkAndAddSkip(temp, skip);
        } else if (deltaBefore > context) {
          skip.reduceSize(deltaAfter + context);
          checkAndAddSkip(temp, skip);
        }
      }
      if (temp.isEmpty()) {
        return;
      }
      skips = temp;
    }
    for (SkippedLine skip : skips) {
      SkipBar barA = renderSkipHelper(cmA, skip);
      SkipBar barB = renderSkipHelper(cmB, skip);
      SkipBar.link(barA, barB);
    }
  }

  private void checkAndAddSkip(List<SkippedLine> list, SkippedLine toAdd) {
    if (toAdd.getSize() > 1) {
      list.add(toAdd);
    }
  }

  private SkipBar renderSkipHelper(CodeMirror cm, SkippedLine skip) {
    int size = skip.getSize();
    int markStart = cm == cmA ? skip.getStartA() : skip.getStartB();
    int markEnd = markStart + size - 1;
    SkipBar bar = new SkipBar(cm);
    diffTable.add(bar);
    Configuration markerConfig = Configuration.create()
        .set("collapsed", true)
        .set("inclusiveLeft", true)
        .set("inclusiveRight", true);
    Configuration lineWidgetConfig = Configuration.create()
        .set("coverGutter", true)
        .set("noHScroll", true);
    if (markStart == 0) {
      bar.setWidget(addLineWidget(
          cm, markEnd + 1, bar, lineWidgetConfig.set("above", true)));
    } else {
      bar.setWidget(addLineWidget(
          cm, markStart - 1, bar, lineWidgetConfig));
    }
    bar.setMarker(cm.markText(CodeMirror.pos(markStart, 0),
        CodeMirror.pos(markEnd), markerConfig), size);
    return bar;
  }

  private CodeMirror otherCm(CodeMirror me) {
    return me == cmA ? cmB : cmA;
  }

  private PatchSet.Id getPatchSetIdFromSide(DisplaySide side) {
    return side == DisplaySide.A && base != null ? base : revision;
  }

  private CodeMirror getCmFromSide(DisplaySide side) {
    return side == DisplaySide.A ? cmA : cmB;
  }

  private DisplaySide getSideFromCm(CodeMirror cm) {
    return cm == cmA ? DisplaySide.A : DisplaySide.B;
  }

  Side getStoredSideFromDisplaySide(DisplaySide side) {
    return side == DisplaySide.A && base == null ? Side.PARENT : Side.REVISION;
  }

  private void markEdit(CodeMirror cm, JsArrayString lines,
      JsArray<Span> edits, int startLine) {
    if (edits == null) {
      return;
    }
    EditIterator iter = new EditIterator(lines, startLine);
    Configuration intralineBgOpt = Configuration.create()
        .set("className", DiffTable.style.intralineBg())
        .set("readOnly", true);
    Configuration diffOpt = Configuration.create()
        .set("className", DiffTable.style.diff())
        .set("readOnly", true);
    LineCharacter last = CodeMirror.pos(0, 0);
    for (int i = 0; i < edits.length(); i++) {
      Span span = edits.get(i);
      LineCharacter from = iter.advance(span.skip());
      LineCharacter to = iter.advance(span.mark());
      int fromLine = from.getLine();
      if (last.getLine() == fromLine) {
        cm.markText(last, from, intralineBgOpt);
      } else {
        cm.markText(CodeMirror.pos(fromLine, 0), from, intralineBgOpt);
      }
      cm.markText(from, to, diffOpt);
      last = to;
      for (int line = fromLine; line < to.getLine(); line++) {
        cm.addLineClass(line, LineClassWhere.BACKGROUND,
            DiffTable.style.diff());
      }
    }
  }

  private void colorLines(CodeMirror cm, String color, int line, int cnt) {
    for (int i = 0; i < cnt; i++) {
      cm.addLineClass(line + i, LineClassWhere.WRAP, color);
    }
  }

  private void addDiffChunkAndPadding(CodeMirror cmToPad, int lineToPad,
      int lineOnOther, int chunkSize, boolean edit) {
    CodeMirror otherCm = otherCm(cmToPad);
    linePaddingOnOtherSideMap.put(otherCm.getLineHandle(lineOnOther),
        new LinePaddingWidgetWrapper(addPaddingWidget(cmToPad,
            lineToPad, 0, Unit.EM, null), lineToPad, chunkSize));
    diffChunks.add(new DiffChunkInfo(getSideFromCm(otherCm),
        lineOnOther - chunkSize + 1, lineOnOther, edit));
  }

  private PaddingWidgetWrapper addPaddingWidget(CodeMirror cm,
      int line, double height, Unit unit, Integer index) {
    SimplePanel padding = new SimplePanel();
    padding.getElement().getStyle().setHeight(height, unit);
    Configuration config = Configuration.create()
        .set("coverGutter", true)
        .set("above", line == -1)
        .set("noHScroll", true);
    if (index != null) {
      config = config.set("insertAt", index);
    }
    LineWidget widget = addLineWidget(cm, line == -1 ? 0 : line, padding, config);
    return new PaddingWidgetWrapper(widget, padding.getElement());
  }

  /**
   * A LineWidget needs to be added to diffTable in order to respond to browser
   * events, but CodeMirror doesn't render the widget until the containing line
   * is scrolled into viewportMargin, causing it to appear at the bottom of the
   * DOM upon loading. Fix by hiding the widget until it is first scrolled into
   * view (when CodeMirror fires a "redraw" event on the widget).
   */
  private LineWidget addLineWidget(CodeMirror cm, int line,
      final Widget widget, Configuration options) {
    widget.setVisible(false);
    LineWidget lineWidget = cm.addLineWidget(line, widget.getElement(), options);
    lineWidget.onFirstRedraw(new Runnable() {
      @Override
      public void run() {
        widget.setVisible(true);
      }
    });
    return lineWidget;
  }

  private void clearActiveLine(CodeMirror cm) {
    if (cm.hasActiveLine()) {
      LineHandle activeLine = cm.getActiveLine();
      cm.removeLineClass(activeLine,
          LineClassWhere.WRAP, DiffTable.style.activeLine());
      cm.setActiveLine(null);
    }
  }

  private Runnable adjustGutters(final CodeMirror cm) {
    return new Runnable() {
      @Override
      public void run() {
        Viewport fromTo = cm.getViewport();
        int size = fromTo.getTo() - fromTo.getFrom() + 1;
        if (cm.getOldViewportSize() == size) {
          return;
        }
        cm.setOldViewportSize(size);
        diffTable.sidePanel.adjustGutters(cmB);
      }
    };
  }

  private Runnable updateActiveLine(final CodeMirror cm) {
    final CodeMirror other = otherCm(cm);
    return new Runnable() {
      public void run() {
        /**
         * The rendering of active lines has to be deferred. Reflow
         * caused by adding and removing styles chokes Firefox when arrow
         * key (or j/k) is held down. Performance on Chrome is fine
         * without the deferral.
         */
        defer(new Runnable() {
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
                mapper.lineOnOther(getSideFromCm(cm), cm.getLineNumber(handle));
            if (info.isAligned()) {
              LineHandle oLineHandle = other.getLineHandle(info.getLine());
              other.setActiveLine(oLineHandle);
              other.addLineClass(oLineHandle, LineClassWhere.WRAP,
                  DiffTable.style.activeLine());
            }
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
        if (!(cm.hasActiveLine() &&
            cm.getLineNumber(cm.getActiveLine()) == line)) {
          cm.setCursor(LineCharacter.create(line));
        }
        Scheduler.get().scheduleDeferred(new ScheduledCommand() {
          @Override
          public void execute() {
            insertNewDraft(cm).run();
          }
        });
      }
    };
  }

  private Runnable insertNewDraft(final CodeMirror cm) {
    if (!Gerrit.isSignedIn()) {
      return new Runnable() {
        @Override
        public void run() {
          Gerrit.doSignIn(getToken());
        }
     };
    }
    return new Runnable() {
      public void run() {
        LineHandle handle = cm.getActiveLine();
        int line = cm.getLineNumber(handle);
        CommentBox box = lineActiveBoxMap.get(handle);
        FromTo fromTo = cm.getSelectedRange();
        if (cm.somethingSelected()) {
          lineActiveBoxMap.put(handle,
              addNewDraft(cm, line, fromTo.getTo().getLine() == line ? fromTo : null));
        } else if (box == null) {
          lineActiveBoxMap.put(handle, addNewDraft(cm, line, null));
        } else if (box instanceof DraftBox) {
          ((DraftBox) box).setEdit(true);
        } else {
          ((PublishedBox) box).doReply();
        }
      }
    };
  }

  private Runnable toggleOpenBox(final CodeMirror cm) {
    return new Runnable() {
      public void run() {
        CommentBox box = lineActiveBoxMap.get(cm.getActiveLine());
        if (box != null) {
          box.setOpen(!box.isOpen());
        }
      }
    };
  }

  private Runnable upToChange(final boolean openReplyBox) {
    return new Runnable() {
      public void run() {
        if (unsaved.isEmpty()) {
          goUpToChange(openReplyBox);
        } else {
          CallbackGroup group = new CallbackGroup();
          saveAllDrafts(group);
          group.addFinal(new GerritCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
              goUpToChange(openReplyBox);
            }
          }).onSuccess(null);
        }
      }
    };
  }

  private void goUpToChange(boolean openReplyBox) {
    String b = base != null ? String.valueOf(base.get()) : null;
    String rev = String.valueOf(revision.get());
    Gerrit.display(
      PageLinks.toChange(changeId, rev),
      new ChangeScreen2(changeId, b, rev, openReplyBox));
  }

  private Runnable openClosePublished(final CodeMirror cm) {
    return new Runnable() {
      @Override
      public void run() {
        if (cm.hasActiveLine()) {
          List<PublishedBox> list =
              linePublishedBoxesMap.get(cm.getActiveLine());
          if (list == null) {
            return;
          }
          boolean open = false;
          for (PublishedBox box : list) {
            if (!box.isOpen()) {
              open = true;
              break;
            }
          }
          for (PublishedBox box : list) {
            box.setOpen(open);
          }
        }
      }
    };
  }

  private Runnable toggleReviewed() {
    return new Runnable() {
     public void run() {
       header.setReviewed(!header.isReviewed());
     }
    };
  }

  private Runnable flipCursorSide(final CodeMirror cm, final boolean toLeft) {
    return new Runnable() {
      public void run() {
        if (cm.hasActiveLine() && (toLeft && cm == cmB || !toLeft && cm == cmA)) {
          CodeMirror other = otherCm(cm);
          other.setCursor(LineCharacter.create(
              mapper.lineOnOther(
                  getSideFromCm(cm), cm.getLineNumber(cm.getActiveLine())).getLine()));
          other.focus();
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
          diffChunkNav(cm, false).run();
        }
      }
    };
  }

  private Runnable diffChunkNav(final CodeMirror cm, final boolean prev) {
    return new Runnable() {
      @Override
      public void run() {
        int line = cm.hasActiveLine() ? cm.getLineNumber(cm.getActiveLine()) : 0;
        int res = Collections.binarySearch(
                diffChunks,
                new DiffChunkInfo(getSideFromCm(cm), line, 0, false),
                getDiffChunkComparator());
        if (res < 0) {
          res = -res - (prev ? 1 : 2);
        }

        res = res + (prev ? -1 : 1);
        DiffChunkInfo lookUp = diffChunks.get(getWrapAroundDiffChunkIndex(res));
        // If edit, skip the deletion chunk and set focus on the insertion one.
        if (lookUp.isEdit() && lookUp.getSide() == DisplaySide.A) {
          res = res + (prev ? -1 : 1);
        }
        DiffChunkInfo target = diffChunks.get(getWrapAroundDiffChunkIndex(res));
        CodeMirror targetCm = getCmFromSide(target.getSide());
        targetCm.setCursor(LineCharacter.create(target.getStart()));
        targetCm.focus();
        targetCm.scrollToY(
            targetCm.heightAtLine(target.getStart(), "local") -
            0.5 * cmB.getScrollbarV().getClientHeight());
      }
    };
  }

  /**
   * Diff chunks are ordered by their starting lines. If it's a deletion,
   * use its corresponding line on the revision side for comparison. In
   * the edit case, put the deletion chunk right before the insertion chunk.
   * This placement guarantees well-ordering.
   */
  private Comparator<DiffChunkInfo> getDiffChunkComparator() {
    return new Comparator<DiffChunkInfo>() {
      @Override
      public int compare(DiffChunkInfo o1, DiffChunkInfo o2) {
        if (o1.getSide() == o2.getSide()) {
          return o1.getStart() - o2.getStart();
        } else if (o1.getSide() == DisplaySide.A) {
          int comp = mapper.lineOnOther(o1.getSide(), o1.getStart())
              .getLine() - o2.getStart();
          return comp == 0 ? -1 : comp;
        } else {
          int comp = o1.getStart() -
              mapper.lineOnOther(o2.getSide(), o2.getStart()).getLine();
          return comp == 0 ? 1 : comp;
        }
      }
    };
  }

  private DiffChunkInfo getDiffChunk(DisplaySide side, int line) {
    int res = Collections.binarySearch(
        diffChunks,
        new DiffChunkInfo(side, line, 0, false), // Dummy DiffChunkInfo
        getDiffChunkComparator());
    if (res >= 0) {
      return diffChunks.get(res);
    } else { // The line might be within a DiffChunk
      res = -res - 1;
      if (res > 0) {
        DiffChunkInfo info = diffChunks.get(res - 1);
        if (info.getSide() == side && info.getStart() <= line &&
            line <= info.getEnd()) {
          return info;
        }
      }
    }
    return null;
  }

  private int getWrapAroundDiffChunkIndex(int index) {
    return (index + diffChunks.size()) % diffChunks.size();
  }

  void defer(Runnable thunk) {
    if (deferred == null) {
      final ArrayList<Runnable> list = new ArrayList<Runnable>();
      deferred = list;
      Scheduler.get().scheduleDeferred(new ScheduledCommand() {
        @Override
        public void execute() {
          deferred = null;
          cmA.operation(new Runnable() {
            @Override
            public void run() {
              cmB.operation(new Runnable() {
                @Override
                public void run() {
                  for (Runnable thunk : list) {
                    thunk.run();
                  }
                }
              });
            }
          });
        }
      });
    }
    deferred.add(thunk);
  }

  void resizePaddingOnOtherSide(DisplaySide mySide, int line) {
    CodeMirror cm = getCmFromSide(mySide);
    LineHandle handle = cm.getLineHandle(line);
    final LinePaddingWidgetWrapper otherWrapper = linePaddingOnOtherSideMap.get(handle);
    double myChunkHeight = cm.heightAtLine(line + 1) -
        cm.heightAtLine(line - otherWrapper.getChunkLength() + 1);
    Element otherPadding = otherWrapper.getElement();
    int otherPaddingHeight = otherPadding.getOffsetHeight();
    CodeMirror otherCm = otherCm(cm);
    int otherLine = otherWrapper.getOtherLine();
    LineHandle other = otherCm.getLineHandle(otherLine);
    if (linePaddingOnOtherSideMap.containsKey(other)) {
      LinePaddingWidgetWrapper myWrapper = linePaddingOnOtherSideMap.get(other);
      Element myPadding = linePaddingOnOtherSideMap.get(other).getElement();
      int myPaddingHeight = myPadding.getOffsetHeight();
      myChunkHeight -= myPaddingHeight;
      double otherChunkHeight = otherCm.heightAtLine(otherLine + 1) -
          otherCm.heightAtLine(otherLine - myWrapper.getChunkLength() + 1) -
          otherPaddingHeight;
      double delta = myChunkHeight - otherChunkHeight;
      if (delta > 0) {
        if (myPaddingHeight != 0) {
          setHeightInPx(myPadding, 0);
          myWrapper.getWidget().changed();
        }
        if (otherPaddingHeight != delta) {
          setHeightInPx(otherPadding, delta);
          otherWrapper.getWidget().changed();
        }
      } else {
        if (myPaddingHeight != -delta) {
          setHeightInPx(myPadding, -delta);
          myWrapper.getWidget().changed();
        }
        if (otherPaddingHeight != 0) {
          setHeightInPx(otherPadding, 0);
          otherWrapper.getWidget().changed();
        }
      }
    } else if (otherPaddingHeight != myChunkHeight) {
      setHeightInPx(otherPadding, myChunkHeight);
      otherWrapper.getWidget().changed();
    }
  }

  // TODO: Maybe integrate this with PaddingManager.
  private RenderLineHandler resizeLinePadding(final DisplaySide side) {
    return new RenderLineHandler() {
      @Override
      public void handle(final CodeMirror instance, final LineHandle handle,
          Element element) {
        if (lineActiveBoxMap.containsKey(handle)) {
          lineActiveBoxMap.get(handle).resizePaddingWidget();
        }
        if (linePaddingOnOtherSideMap.containsKey(handle)) {
          defer(new Runnable() {
            @Override
            public void run() {
              resizePaddingOnOtherSide(side, instance.getLineNumber(handle));
            }
          });
        }
      }
    };
  }

  void resizeCodeMirror() {
    int height = getCodeMirrorHeight();
    cmA.setHeight(height);
    cmB.setHeight(height);
    diffTable.sidePanel.adjustGutters(cmB);
  }

  private int getCodeMirrorHeight() {
    int rest = Gerrit.getHeaderFooterHeight()
        + header.getOffsetHeight()
        + diffTable.getHeaderHeight()
        + 5; // Estimate
    return Window.getClientHeight() - rest;
  }

  static void setHeightInPx(Element ele, double height) {
    ele.getStyle().setHeight(height, Unit.PX);
  }

  private String getContentType(DiffInfo.FileMeta meta) {
    return pref.isSyntaxHighlighting()
          && meta != null
          && meta.content_type() != null
        ? ModeInjector.getContentType(meta.content_type())
        : null;
  }

  CodeMirror getCmA() {
    return cmA;
  }

  CodeMirror getCmB() {
    return cmB;
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
        .intraline(pref.isIntralineDifference())
        .ignoreWhitespace(pref.getIgnoreWhitespace())
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
}
