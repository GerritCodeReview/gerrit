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
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.changes.Side;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.user.client.DialogVisibleEvent;
import com.google.gwtexpui.user.client.DialogVisibleHandler;
import com.google.gwtexpui.user.client.UserAgent;

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
import net.codemirror.lib.ScrollInfo;
import net.codemirror.lib.TextMarker.FromTo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SideBySide2 extends Screen {
  interface Binder extends UiBinder<FlowPanel, SideBySide2> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  enum DisplaySide {
    A, B;
  }

  private static final JsArrayString EMPTY =
      JavaScriptObject.createArray().cast();

  @UiField(provided = true)
  Header header;

  @UiField(provided = true)
  DiffTable diffTable;

  private final PatchSet.Id base;
  private final PatchSet.Id revision;
  private final String path;

  private CodeMirror cmA;
  private CodeMirror cmB;
  private Timer scrollTimerA;
  private Timer scrollTimerB;
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
  private Map<LineHandle, PublishedBox> lineLastPublishedBoxMap;
  private Map<LineHandle, PaddingManager> linePaddingManagerMap;
  private Map<LineHandle, LinePaddingWidgetWrapper> linePaddingOnOtherSideMap;
  private List<DiffChunkInfo> diffChunks;
  private List<SkippedLine> skips;
  private int context;

  private KeyCommandSet keysNavigation;
  private KeyCommandSet keysAction;
  private KeyCommandSet keysComment;
  private KeyCommandSet keysOpenByEnter;
  private List<HandlerRegistration> handlers;

  public SideBySide2(
      PatchSet.Id base,
      PatchSet.Id revision,
      String path) {
    this.base = base;
    this.revision = revision;
    this.path = path;
    this.handlers = new ArrayList<HandlerRegistration>(6);
    // TODO: Re-implement necessary GlobalKey bindings.
    addDomHandler(GlobalKey.STOP_PROPAGATION, KeyPressEvent.getType());
    keysNavigation = new KeyCommandSet(Gerrit.C.sectionNavigation());
    add(header = new Header(keysNavigation, revision, path));
    add(diffTable = new DiffTable(this, path));
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
    CodeMirror.initLibrary(cmGroup.add(CallbackGroup.<Void>emptyCallback()));
    final CallbackGroup group = new CallbackGroup();
    final AsyncCallback<Void> modeInjectorCb =
        group.add(CallbackGroup.<Void>emptyCallback());

    DiffApi.diff(revision, path)
      .base(base)
      .wholeFile()
      .intraline()
      .ignoreWhitespace(DiffApi.IgnoreWhitespace.NONE)
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
    ConfigInfoCache.get(revision.getParentKey(), group.addFinal(
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

    handlers.add(UserAgent.addDialogVisibleHandler(new DialogVisibleHandler() {
      @Override
      public void onDialogVisible(DialogVisibleEvent event) {
        diffTable.getElement().getStyle().setVisibility(
          event.isVisible()
              ? Style.Visibility.HIDDEN
              : Style.Visibility.VISIBLE);
      }
    }));
    resizeCodeMirror();

    Window.enableScrolling(false);
    cmA.setOption("viewportMargin", 10);
    cmB.setOption("viewportMargin", 10);
    cmB.focus();
  }

  @Override
  protected void onUnload() {
    super.onUnload();

    removeKeyHandlerRegs();
    if (resizeHandler != null) {
      resizeHandler.removeHandler();
      resizeHandler = null;
    }
    cmA.getWrapperElement().removeFromParent();
    cmB.getWrapperElement().removeFromParent();
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
    cm.on("scroll", doScroll(cm));
    scrollTimerA = new Timer() {
      @Override
      public void run() {
        fixScroll(cmA);
      }
    };
    scrollTimerB = new Timer() {
      @Override
      public void run() {
        fixScroll(cmB);
      }
    };
    cm.on("renderLine", resizeLinePadding(getSideFromCm(cm)));
    cm.on("viewportChange", adjustGutters(cm));
    // TODO: Prevent right click from updating the cursor.
    cm.addKeyMap(KeyMap.create()
        .on("'j'", moveCursorDown(cm, 1))
        .on("'k'", moveCursorDown(cm, -1))
        .on("'u'", upToChange())
        .on("'r'", toggleReviewed())
        .on("'o'", toggleOpenBox(cm))
        .on("Enter", toggleOpenBox(cm))
        .on("'c'", insertNewDraft(cm)));
  }

  @Override
  public void registerKeys() {
    super.registerKeys();

    keysNavigation.add(new UpToChangeCommand2(revision, 0, 'u'));
    keysNavigation.add(new NoOpKeyCommand(0, 'j', PatchUtil.C.lineNext()));
    keysNavigation.add(new NoOpKeyCommand(0, 'k', PatchUtil.C.linePrev()));

    keysAction = new KeyCommandSet(Gerrit.C.sectionActions());
    keysAction.add(new NoOpKeyCommand(0, 'o', PatchUtil.C.expandComment()));
    keysAction.add(new KeyCommand(0, 'r', PatchUtil.C.toggleReviewed()) {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        toggleReviewed().run();
      }
    });

    keysOpenByEnter = new KeyCommandSet(Gerrit.C.sectionNavigation());
    keysOpenByEnter.add(new NoOpKeyCommand(0, KeyCodes.KEY_ENTER,
        PatchUtil.C.expandComment()));

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
    handlers.add(GlobalKey.add(this, keysAction));
    handlers.add(GlobalKey.add(this, keysOpenByEnter));
    if (keysComment != null) {
      handlers.add(GlobalKey.add(this, keysComment));
    }
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

  private void display(DiffInfo diffInfo) {
    cmA = displaySide(diffInfo.meta_a(), diffInfo.text_a(), diffTable.cmA);
    cmB = displaySide(diffInfo.meta_b(), diffInfo.text_b(), diffTable.cmB);
    skips = new ArrayList<SkippedLine>();
    linePaddingOnOtherSideMap = new HashMap<LineHandle, LinePaddingWidgetWrapper>();
    diffChunks = new ArrayList<DiffChunkInfo>();
    render(diffInfo);
    lineActiveBoxMap = new HashMap<LineHandle, CommentBox>();
    lineLastPublishedBoxMap = new HashMap<LineHandle, PublishedBox>();
    linePaddingManagerMap = new HashMap<LineHandle, PaddingManager>();
    if (publishedBase != null || publishedRevision != null) {
      publishedMap = new HashMap<String, PublishedBox>();
    }
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
    registerCmEvents(cmA);
    registerCmEvents(cmB);
    resizeHandler = Window.addResizeHandler(new ResizeHandler() {
      @Override
      public void onResize(ResizeEvent event) {
        resizeCodeMirror();
      }
    });
  }

  private CodeMirror displaySide(DiffInfo.FileMeta meta, String contents,
      Element ele) {
    if (meta == null) {
      contents = "";
    }
    Configuration cfg = Configuration.create()
      .set("readOnly", true)
      .set("lineNumbers", true)
      .set("tabSize", 2)
      .set("mode", getContentType(meta))
      .set("lineWrapping", true)
      .set("styleSelectedText", true)
      .set("showTrailingSpace", true)
      .set("keyMap", "vim")
      .set("value", contents)
      /**
       * Without this, CM won't put line widgets too far down in the right spot,
       * and padding widgets will be informed of wrong offset height. Reset to
       * 10 (default) after initial rendering.
       */
      .setInfinity("viewportMargin");
    int h = Gerrit.getHeaderFooterHeight() + 18 /* reviewed estimate */;
    CodeMirror cm = CodeMirror.create(ele, cfg);
    cm.setHeight(Window.getClientHeight() - h);
    return cm;
  }

  private void render(DiffInfo diff) {
    AccountDiffPreference pref = Gerrit.getAccountDiffPreference();
    context = pref != null
        ? pref.getContext()
        : AccountDiffPreference.DEFAULT_CONTEXT;
    JsArray<Region> regions = diff.content();
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
        if (bLength > 0) {
          addDiffChunkAndPadding(cmA, chunkEndA, chunkEndB, bLength);
        }
        if (aLength > 0) {
          addDiffChunkAndPadding(cmB, chunkEndB, chunkEndA, aLength);
        }
        markEdit(cmA, currentA, current.edit_a(), origLineA);
        markEdit(cmB, currentB, current.edit_b(), origLineB);
        if (aLength == 0 || bLength == 0) {
          diffTable.sidePanel.addGutter(cmB, origLineB, aLength == 0
              ? SidePanel.GutterType.INSERT
              : SidePanel.GutterType.DELETE);
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
    LineHandle handle = cm.getLineHandle(info.line() - 1);
    lineActiveBoxMap.put(handle, box);
    return box;
  }

  CommentBox addCommentBox(CommentInfo info, CommentBox box) {
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
      manager = new PaddingManager(
          addPaddingWidget(cm, DiffTable.style.padding(), line, 0, Unit.PX, 0));
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
      otherManager = new PaddingManager(
          addPaddingWidget(other, DiffTable.style.padding(), lineToPad, 0, Unit.PX, 0));
      linePaddingManagerMap.put(otherHandle, otherManager);
    }
    if ((myChunk == null && otherChunk == null) || (myChunk != null && otherChunk != null)) {
      PaddingManager.link(manager, otherManager);
    }
    int index = manager.getCurrentCount();
    manager.insert(box, index);
    Configuration config = Configuration.create()
      .set("coverGutter", true)
      .set("insertAt", index);
    LineWidget boxWidget = cm.addLineWidget(line, box.getElement(), config);
    box.setPaddingManager(manager);
    box.setSelfWidgetWrapper(new PaddingWidgetWrapper(boxWidget, box.getElement()));
    box.setParent(this);
    if (otherChunk == null) {
      box.setDiffChunkInfo(myChunk);
    }
    box.setGutterWrapper(diffTable.sidePanel.addGutter(cm, info.line() - 1,
        box instanceof DraftBox ?
            SidePanel.GutterType.DRAFT
          : SidePanel.GutterType.COMMENT));
    return box;
  }

  void removeDraft(DraftBox box, int line) {
    LineHandle handle = getCmFromSide(box.getSide()).getLineHandle(line);
    lineActiveBoxMap.remove(handle);
    if (lineLastPublishedBoxMap.containsKey(handle)) {
      lineActiveBoxMap.put(handle, lineLastPublishedBoxMap.get(handle));
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
      lineLastPublishedBoxMap.put(handle, box);
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
        int startLine = info.side() == Side.PARENT
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

  private void checkAndAddSkip(List<SkippedLine> list,
      SkippedLine toAdd) {
    if (toAdd.getSize() > 1) {
      list.add(toAdd);
    }
  }

  private SkipBar renderSkipHelper(CodeMirror cm, SkippedLine skip) {
    int size = skip.getSize();
    int markStart = cm == cmA ? skip.getStartA() - 1 : skip.getStartB() - 1;
    int markEnd = markStart + size;
    SkipBar bar = new SkipBar(cm);
    diffTable.add(bar);
    /**
     * Due to CodeMirror limitation, there's no way to make the first
     * line disappear completely, and CodeMirror doesn't like manually
     * setting the display of a line to "none". The workaround here uses
     * inline widget for the first line and regular line widgets for others.
     */
    Configuration markerConfig;
    if (markStart == -1) {
      markerConfig = Configuration.create()
        .set("inclusiveLeft", true)
        .set("inclusiveRight", true)
        .set("replacedWith", bar.getElement());
      cm.addLineClass(0, LineClassWhere.WRAP, DiffTable.style.hideNumber());
    } else {
      markerConfig = Configuration.create().set("collapsed", true);
      Configuration config = Configuration.create().set("coverGutter", true);
      bar.setWidget(cm.addLineWidget(markStart, bar.getElement(), config));
    }
    bar.setMarker(cm.markText(CodeMirror.pos(markStart),
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
      int lineOnOther, int chunkSize) {
    CodeMirror otherCm = otherCm(cmToPad);
    linePaddingOnOtherSideMap.put(otherCm.getLineHandle(lineOnOther),
        new LinePaddingWidgetWrapper(addPaddingWidget(cmToPad, DiffTable.style.padding(),
            lineToPad, 0, Unit.EM, null), lineToPad, chunkSize));
    diffChunks.add(new DiffChunkInfo(getSideFromCm(otherCm),
        lineOnOther - chunkSize + 1, lineOnOther));
  }

  private PaddingWidgetWrapper addPaddingWidget(CodeMirror cm, String style,
      int line, double height, Unit unit, Integer index) {
    Element div = DOM.createDiv();
    div.setClassName(style);
    div.getStyle().setHeight(height, unit);
    Configuration config = Configuration.create()
        .set("coverGutter", true)
        .set("above", line == -1);
    if (index != null) {
      config = config.set("insertAt", index);
    }
    LineWidget widget = cm.addLineWidget(line == -1 ? 0 : line, div, config);
    return new PaddingWidgetWrapper(widget, div);
  }

  private Runnable doScroll(final CodeMirror cm) {
    final CodeMirror other = otherCm(cm);
    return new Runnable() {
      public void run() {
        // Hack to prevent feedback loop, Chrome seems fine but Firefox chokes.
        if (cm.getScrollSetAt() + 50 > System.currentTimeMillis()) {
          return;
        }

        ScrollInfo si = cm.getScrollInfo();
        if (si.getTop() == 0 && !Gerrit.isHeaderVisible()) {
          Gerrit.setHeaderVisible(true);
          diffTable.updateFileCommentVisibility(false);
          resizeCodeMirror();
        } else if (si.getTop() > 0.5 * si.getClientHeight()
            && Gerrit.isHeaderVisible()) {
          Gerrit.setHeaderVisible(false);
          diffTable.updateFileCommentVisibility(true);
          resizeCodeMirror();
        }

        /**
         * Since CM doesn't always take the height of line widgets into
         * account when calculating scrollInfo when scrolling too fast
         * (e.g. throw-scrolling), simply setting scrollTop to be the same
         * doesn't guarantee alignment, but should work in most cases. See the
         * hack in fixScroll();
         */
        other.scrollToY(si.getTop());
        other.setScrollSetAt(System.currentTimeMillis());
        (cm == cmA ? scrollTimerA : scrollTimerB).schedule(50);
      }
    };
  }

  private void fixScroll(CodeMirror cm) {
    CodeMirror other = otherCm(cm);
    Viewport fromTo = cm.getViewport();
    int line = (fromTo.getFrom() + fromTo.getTo()) / 2;
    LineOnOtherInfo info = mapper.lineOnOther(getSideFromCm(cm), line);
    /**
     * If the line in the middle of the viewPort isn't part of an insertion /
     * deletion gap, isAligned() will be true, and hopefully this will be
     * the majority of cases. We then manually examine if the lines that
     * should be aligned are at the same height. If not, perform additional
     * scrolling until the lines are aligned.
     */
    if (info.isAligned()) {
      double myHeight = cm.heightAtLine(line);
      double otherHeight = other.heightAtLine(info.getLine());
      if (myHeight != otherHeight) {
        other.scrollToY(other.getScrollInfo().getTop() + otherHeight - myHeight);
        other.setScrollSetAt(System.currentTimeMillis());
      }
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
        if (cm.hasActiveLine()) {
          LineHandle activeLine = cm.getActiveLine();
          cm.removeLineClass(activeLine,
              LineClassWhere.WRAP, DiffTable.style.activeLine());
          cm.removeLineClass(activeLine,
              LineClassWhere.BACKGROUND, DiffTable.style.activeLineBg());
        }
        if (other.hasActiveLine()) {
          LineHandle otherActiveLine = other.getActiveLine();
          other.removeLineClass(otherActiveLine,
              LineClassWhere.WRAP, DiffTable.style.activeLine());
          other.removeLineClass(otherActiveLine,
              LineClassWhere.BACKGROUND, DiffTable.style.activeLineBg());
        }
        LineHandle handle = cm.getLineHandleVisualStart(cm.getCursor("end").getLine());
        cm.setActiveLine(handle);
        if (cm.somethingSelected()) {
          return;
        }
        cm.addLineClass(handle, LineClassWhere.WRAP, DiffTable.style.activeLine());
        cm.addLineClass(handle, LineClassWhere.BACKGROUND, DiffTable.style.activeLineBg());
        LineOnOtherInfo info =
            mapper.lineOnOther(getSideFromCm(cm), cm.getLineNumber(handle));
        if (info.isAligned()) {
          LineHandle oLineHandle = other.getLineHandle(info.getLine());
          other.setActiveLine(oLineHandle);
          other.addLineClass(oLineHandle, LineClassWhere.WRAP,
              DiffTable.style.activeLine());
          other.addLineClass(oLineHandle, LineClassWhere.BACKGROUND,
              DiffTable.style.activeLineBg());
        }
      }
    };
  }

  private GutterClickHandler onGutterClick(final CodeMirror cm) {
    return new GutterClickHandler() {
      @Override
      public void handle(CodeMirror instance, int line, String gutter,
          NativeEvent clickEvent) {
        if (!(cm.hasActiveLine() &&
            instance.getLineNumber(cm.getActiveLine()) == line)) {
          instance.setCursor(LineCharacter.create(line));
          instance.setActiveLine(cm.getLineHandle(line));
        }
        insertNewDraft(cm).run();
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

  private Runnable moveCursorDown(final CodeMirror cm, final int numLines) {
    return new Runnable() {
      public void run() {
        cm.moveCursorDown(numLines);
      }
    };
  }

  private Runnable upToChange() {
    return new Runnable() {
      public void run() {
        Gerrit.display(PageLinks.toChange2(
          revision.getParentKey(),
          String.valueOf(revision.get())));
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

  private DiffChunkInfo getDiffChunk(DisplaySide side, int line) {
    for (DiffChunkInfo info : diffChunks) {
      if (info.getSide() == side && info.getStart() <= line &&
          line <= info.getEnd()) {
        return info;
      }
    }
    return null;
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
          Scheduler.get().scheduleDeferred(new ScheduledCommand() {
            @Override
            public void execute() {
              resizePaddingOnOtherSide(side, instance.getLineNumber(handle));
            }
          });
        }
      }
    };
  }

  void resizeCodeMirror() {
    if (cmA == null) {
      return;
    }
    int h = Gerrit.getHeaderFooterHeight()
        + header.getOffsetHeight()
        + diffTable.getHeaderHeight()
        + 10; // Estimate
    cmA.setHeight(Window.getClientHeight() - h);
    cmA.refresh();
    cmB.setHeight(Window.getClientHeight() - h);
    cmB.refresh();
    diffTable.sidePanel.adjustGutters(cmB);
  }

  static void setHeightInPx(Element ele, double height) {
    ele.getStyle().setHeight(height, Unit.PX);
  }

  private static String getContentType(DiffInfo.FileMeta meta) {
    return meta != null && meta.content_type() != null
        ? ModeInjector.getContentType(meta.content_type())
        : null;
  }

  CodeMirror getCmA() {
    return cmA;
  }

  CodeMirror getCmB() {
    return cmB;
  }

  static class EditIterator {
    private final JsArrayString lines;
    private final int startLine;
    private int currLineIndex;
    private int currLineOffset;

    EditIterator(JsArrayString lineArray, int start) {
      lines = lineArray;
      startLine = start;
    }

    LineCharacter advance(int numOfChar) {
      while (currLineIndex < lines.length()) {
        int lengthWithNewline =
            lines.get(currLineIndex).length() - currLineOffset + 1;
        if (numOfChar < lengthWithNewline) {
          LineCharacter at = LineCharacter.create(
              startLine + currLineIndex,
              numOfChar + currLineOffset);
          currLineOffset += numOfChar;
          return at;
        }
        numOfChar -= lengthWithNewline;
        advanceLine();
        if (numOfChar == 0) {
          return LineCharacter.create(startLine + currLineIndex, 0);
        }
      }
      throw new IllegalStateException("EditIterator index out of bound");
    }

    private void advanceLine() {
      currLineIndex++;
      currLineOffset = 0;
    }
  }

  static class DiffChunkInfo {
    private DisplaySide side;
    private int start;
    private int end;

    DiffChunkInfo(DisplaySide side, int start, int end) {
      this.side = side;
      this.start = start;
      this.end = end;
    }

    DisplaySide getSide() {
      return side;
    }

    int getStart() {
      return start;
    }

    int getEnd() {
      return end;
    }
  }

  private static class NoOpKeyCommand extends KeyCommand {
    private NoOpKeyCommand(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(KeyPressEvent event) {
    }
  }
}
