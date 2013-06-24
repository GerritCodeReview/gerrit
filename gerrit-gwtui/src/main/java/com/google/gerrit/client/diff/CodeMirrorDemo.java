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

import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.CommentApi;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.diff.DiffInfo.Region;
import com.google.gerrit.client.diff.DiffInfo.Span;
import com.google.gerrit.client.projects.ConfigInfoCache;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.changes.Side;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineClassWhere;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.LineCharacter;
import net.codemirror.lib.LineWidget;
import net.codemirror.lib.ModeInjector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CodeMirrorDemo extends Screen {
  private static final int HEADER_FOOTER = 60 + 15 * 2 + 38;
  private static final JsArrayString EMPTY =
      JavaScriptObject.createArray().cast();
  private static final Configuration COMMENT_BOX_CONFIG =
      Configuration.create().set("coverGutter", true);

  private final PatchSet.Id base;
  private final PatchSet.Id revision;
  private final String path;

  private DiffTable diffTable;
  private CodeMirror cmA;
  private CodeMirror cmB;
  private HandlerRegistration resizeHandler;
  private JsArray<CommentInfo> published;
  private JsArray<CommentInfo> drafts;
  private List<CommentBox> initialBoxes;
  private DiffInfo diff;
  private LineMapper mapper;
  private CommentLinkProcessor commentLinkProcessor;
  private Map<String, PublishedBox> publishedMap;

  public CodeMirrorDemo(PatchSet.Id base, PatchSet.Id revision, String path) {
    this.base = base;
    this.revision = revision;
    this.path = path;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setHeaderVisible(false);
    add(diffTable = new DiffTable());
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
        .intraline()
        .ignoreWhitespace(DiffApi.IgnoreWhitespace.NONE)
        .get(cmGroup.addFinal(new GerritCallback<DiffInfo>() {
          @Override
          public void onSuccess(DiffInfo diffInfo) {
            diff = diffInfo;
            new ModeInjector().add(getContentType(diff.meta_a()))
                .add(getContentType(diff.meta_b())).inject(modeInjectorCb);
          }
        }));
    CommentApi.comments(revision,
        group.add(new GerritCallback<NativeMap<JsArray<CommentInfo>>>() {
          @Override
          public void onSuccess(NativeMap<JsArray<CommentInfo>> m) {
            published = m.get(path);
          }
        }));
    CommentApi.drafts(revision,
        group.add(new GerritCallback<NativeMap<JsArray<CommentInfo>>>() {
          @Override
          public void onSuccess(NativeMap<JsArray<CommentInfo>> m) {
            drafts = m.get(path);
          }
        }));
    ChangeApi.detail(
        revision.getParentKey().get(), new GerritCallback<ChangeInfo>() {
          @Override
          public void onSuccess(ChangeInfo result) {
            Project.NameKey project = result.project_name_key();
            ConfigInfoCache.get(project, group.addFinal(new ScreenLoadCallback<
                ConfigInfoCache.Entry>(CodeMirrorDemo.this) {
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
        });
  }

  @Override
  public void onShowView() {
    super.onShowView();
    if (cmA != null) {
      cmA.refresh();
    }
    if (cmB != null) {
      cmB.refresh();
    }
    Window.enableScrolling(false);
    Scheduler.get().scheduleDeferred(new ScheduledCommand() {
      @Override
      public void execute() {
        for (CommentBox box : initialBoxes) {
          box.resizePaddingWidget();
        }
      }
    });
  }

  @Override
  protected void onUnload() {
    super.onUnload();

    if (resizeHandler != null) {
      resizeHandler.removeHandler();
      resizeHandler = null;
    }
    if (cmA != null) {
      cmA.getWrapperElement().removeFromParent();
      cmA = null;
    }
    if (cmB != null) {
      cmB.getWrapperElement().removeFromParent();
      cmB = null;
    }
    Window.enableScrolling(true);
    mapper = null;
    initialBoxes = null;
    publishedMap = null;
  }

  private void display(DiffInfo diffInfo) {
    cmA = displaySide(diffInfo.meta_a(), diffInfo.text_a(), diffTable.getCmA());
    cmB = displaySide(diffInfo.meta_b(), diffInfo.text_b(), diffTable.getCmB());
    render(diffInfo);
    initialBoxes = new ArrayList<CommentBox>();
    publishedMap = new HashMap<String, PublishedBox>(published.length());
    renderPublished();
    renderDrafts();
    published = null;
    drafts = null;

    // TODO: Probably need horizontal resize
    resizeHandler = Window.addResizeHandler(new ResizeHandler() {
      @Override
      public void onResize(ResizeEvent event) {
        if (cmA != null) {
          cmA.setHeight(event.getHeight() - HEADER_FOOTER);
          cmA.refresh();
        }
        if (cmB != null) {
          cmB.setHeight(event.getHeight() - HEADER_FOOTER);
          cmB.refresh();
        }
      }
    });
    cmA.on("scroll", doScroll(cmB));
    cmB.on("scroll", doScroll(cmA));
  }

  private CodeMirror displaySide(
      DiffInfo.FileMeta meta, String contents, Element ele) {
    if (meta == null) {
      contents = "";
    }
    Configuration cfg = Configuration.create()
        .set("readOnly", true)
        .set("lineNumbers", true)
        .set("tabSize", 2)
        .set("mode", getContentType(meta))
        .set("styleSelectedText", true)
        .set("value", contents);
    final CodeMirror cm = CodeMirror.create(ele, cfg);
    cm.setHeight(Window.getClientHeight() - HEADER_FOOTER);
    return cm;
  }

  private void render(DiffInfo diff) {
    JsArray<Region> regions = diff.content();
    mapper = new LineMapper();
    for (int i = 0; i < regions.length(); i++) {
      Region current = regions.get(i);
      int origLineA = mapper.getLineA();
      int origLineB = mapper.getLineB();
      if (current.ab() != null) { // Common
        // TODO: Handle skips.
        mapper.appendCommon(current.ab().length());
      } else { // Insert, Delete or Edit
        JsArrayString currentA = current.a() == null ? EMPTY : current.a();
        JsArrayString currentB = current.b() == null ? EMPTY : current.b();
        int aLength = currentA.length();
        int bLength = currentB.length();
        colorLines(cmA, origLineA, aLength);
        colorLines(cmB, origLineB, bLength);
        mapper.appendCommon(Math.min(aLength, bLength));
        if (aLength < bLength) { // Edit with insertion
          int insertCnt = bLength - aLength;
          insertEmptyLines(cmA, mapper.getLineA(), insertCnt);
          mapper.appendInsert(insertCnt);
        } else if (aLength > bLength) { // Edit with deletion
          int deleteCnt = aLength - bLength;
          insertEmptyLines(cmB, mapper.getLineB(), deleteCnt);
          mapper.appendDelete(deleteCnt);
        }
        markEdit(cmA, currentA, current.edit_a(), origLineA);
        markEdit(cmB, currentB, current.edit_b(), origLineB);
      }
    }
  }

  DraftBox addReplyBox(CommentInfo replyTo, String initMessage, boolean doSave) {
    CommentInfo info = CommentInfo.create(
        null,
        path,
        replyTo.side(),
        replyTo.line(),
        replyTo.id(),
        initMessage);
    DraftBox box = new DraftBox(this, revision, info, commentLinkProcessor,
        true, !doSave);
    addCommentBox(info, box);
    return box;
  }

  CommentBox addCommentBox(CommentInfo info, final CommentBox box) {
    diffTable.add(box);
    Side mySide = info.side();
    CodeMirror cm = mySide == Side.PARENT ? cmA : cmB;
    CodeMirror other = otherCM(cm);
    int line = info.line() - 1; // CommentInfo is 1-based, but CM is 0-based
    LineWidget boxWidget =
        cm.addLineWidget(line, box.getElement(), COMMENT_BOX_CONFIG);
    int lineToPad = mapper.lineOnOther(mySide, line);
    // Estimated height at 21px, fixed by deferring after display
    LineWidgetElementPair padding = addPaddingWidget(
        other, diffTable.style.padding(), lineToPad, 21, Unit.PX);
    box.setSelfWidget(boxWidget);
    box.setPadding(padding.widget, padding.element);
    return box;
  }

  private void renderPublished() {
    for (int i = 0; published != null && i < published.length(); i++) {
      CommentInfo info = published.get(i);
      final PublishedBox box =
          new PublishedBox(this, revision, info, commentLinkProcessor);
      addCommentBox(info, box);
      initialBoxes.add(box);
      publishedMap.put(info.id(), box);
    }
  }

  private void renderDrafts() {
    for (int i = 0; drafts != null && i < drafts.length(); i++) {
      CommentInfo info = drafts.get(i);
      final DraftBox box =
          new DraftBox(this, revision, info, commentLinkProcessor, false, false);
      addCommentBox(info, box);
      initialBoxes.add(box);
      PublishedBox replyToBox = publishedMap.get(info.in_reply_to());
      if (replyToBox != null) {
        replyToBox.registerReplyBox(box);
        box.registerReplyToBox(replyToBox);
      }
    }
  }

  private CodeMirror otherCM(CodeMirror me) {
    return me == cmA ? cmB : cmA;
  }

  private void markEdit(
      CodeMirror cm, JsArrayString lines, JsArray<Span> edits, int startLine) {
    if (edits == null) {
      return;
    }
    EditIterator iter = new EditIterator(lines, startLine);
    Configuration diffOpt = Configuration.create()
        .set("className", diffTable.style.diff()).set("readOnly", true);
    Configuration editOpt = Configuration.create()
        .set("className", diffTable.style.intraline()).set("readOnly", true);
    LineCharacter last = LineCharacter.create(0, 0);
    for (int i = 0; i < edits.length(); i++) {
      Span span = edits.get(i);
      LineCharacter from = iter.advance(span.skip());
      LineCharacter to = iter.advance(span.mark());
      int fromLine = from.getLine();
      if (last.getLine() == fromLine) {
        cm.markText(last, from, diffOpt);
      } else {
        cm.markText(LineCharacter.create(fromLine, 0), from, diffOpt);
      }
      cm.markText(from, to, editOpt);
      last = to;
      for (int line = fromLine; line < to.getLine(); line++) {
        cm.addLineClass(
            line, LineClassWhere.BACKGROUND, diffTable.style.intraline());
      }
    }
  }

  private void colorLines(CodeMirror cm, int line, int cnt) {
    for (int i = 0; i < cnt; i++) {
      cm.addLineClass(line + i, LineClassWhere.WRAP, diffTable.style.diff());
    }
  }

  private void insertEmptyLines(CodeMirror cm, int nextLine, int cnt) {
    // -1 to compensate for the line we went past when this method is called.
    addPaddingWidget(cm, diffTable.style.padding(), nextLine - 1, cnt, Unit.EM);
  }

  private LineWidgetElementPair addPaddingWidget(
      CodeMirror cm, String style, int line, int height, Unit unit) {
    Element div = DOM.createDiv();
    div.setClassName(style);
    div.getStyle().setHeight(height, unit);
    Configuration config = Configuration.create()
        .set("coverGutter", true).set("above", line == -1);
    LineWidget widget = cm.addLineWidget(line == -1 ? 0 : line, div, config);
    return new LineWidgetElementPair(widget, div);
  }

  private Runnable doScroll(final CodeMirror cm) {
    final CodeMirror other = otherCM(cm);
    return new Runnable() {
      public void run() {
        cm.scrollToY(other.getScrollInfo().getTop());
      }
    };
  }

  private static String getContentType(DiffInfo.FileMeta meta) {
    return meta != null && meta.content_type() != null
        ? ModeInjector.getContentType(meta.content_type()) : null;
  }

  private static class LineWidgetElementPair {
    private LineWidget widget;
    private Element element;

    private LineWidgetElementPair(LineWidget w, Element e) {
      widget = w;
      element = e;
    }
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
              startLine + currLineIndex, numOfChar + currLineOffset);
          currLineOffset += numOfChar;
          return at;
        }
        numOfChar -= lengthWithNewline;
        advanceLine();
      }
      throw new IllegalStateException("EditIterator index out of bound");
    }

    private void advanceLine() {
      currLineIndex++;
      currLineOffset = 0;
    }
  }
}
