// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.client.editor;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.JumpKeys;
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.account.DiffPreferences;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeFileApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.diff.FileInfo;
import com.google.gerrit.client.diff.Header;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.safehtml.client.SafeHtml;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.ModeInjector;

public class EditScreen extends Screen {
  interface Binder extends UiBinder<HTMLPanel, EditScreen> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Style extends CssResource {
    String edit();
    String showTabs();
    String columnMargin();
  }

  static {
    com.google.gerrit.client.diff.Resources.I
      .scrollbarStyle()
      .ensureInjected();
  }

  private final PatchSet.Id revision;
  private final String path;
  private DiffPreferences prefs;
  private CodeMirror cm;
  private String type;

  @UiField Style style;
  @UiField Element header;
  @UiField Element project;
  @UiField Element filePath;
  @UiField Button cancel;
  @UiField Button save;
  @UiField Element editor;

  private HandlerRegistration resizeHandler;
  private Element columnMargin;
  private double charWidthPx;

  public EditScreen(Patch.Key patch) {
    this.revision = patch.getParentKey();
    this.path = patch.get();
    prefs = DiffPreferences.create(Gerrit.getAccountDiffPreference());
    add(uiBinder.createAndBindUi(this));
    addStyleName(style.edit());
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

    CallbackGroup cmGroup = new CallbackGroup();
    CodeMirror.initLibrary(cmGroup.add(CallbackGroup.<Void> emptyCallback()));
    CallbackGroup group = new CallbackGroup();
    if (prefs.syntaxHighlighting() && !Patch.COMMIT_MSG.equals(path)) {
      final AsyncCallback<Void> modeInjectorCb =
          group.add(CallbackGroup.<Void> emptyCallback());
      ChangeFileApi.getContentType(revision, path,
          cmGroup.add(new GerritCallback<String>() {
            @Override
            public void onSuccess(String result) {
              type = result;
              injectMode(result, modeInjectorCb);
            }
          }));
    }
    cmGroup.done();

    ChangeApi.detail(revision.getParentKey().get(),
        group.add(new GerritCallback<ChangeInfo>() {
          @Override
          public void onSuccess(ChangeInfo c) {
            project.setInnerText(c.project());
            SafeHtml.setInnerHTML(filePath, Header.formatPath(path, null, null));
          }
        }));

    ChangeFileApi.getContentOrMessage(revision, path,
        group.addFinal(new ScreenLoadCallback<String>(this) {
          @Override
          protected void preDisplay(String content) {
            setShowTabs(prefs.showTabs());
            initEditor(content);
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
        adjustCodeMirrorHeight();
      }
    });

    adjustCodeMirrorHeight();
    setLineLength(prefs.lineLength());
    cm.refresh();
    cm.focus();
  }

  @Override
  protected void onUnload() {
    super.onUnload();
    if (cm != null) {
      cm.getWrapperElement().removeFromParent();
    }
    if (resizeHandler != null) {
      resizeHandler.removeHandler();
    }
    Window.enableScrolling(true);
    Gerrit.setHeaderVisible(true);
    JumpKeys.enable(true);
  }

  private void adjustCodeMirrorHeight() {
    int rest = Gerrit.getHeaderFooterHeight()
        + header.getOffsetHeight()
        + 5; // Estimate
    cm.setHeight(Window.getClientHeight() - rest);
  }

  @UiHandler("save")
  void onSave(@SuppressWarnings("unused") ClickEvent e) {
    ChangeFileApi.putContentOrMessage(revision, path, cm.getValue(),
        new GerritCallback<VoidResult>() {
          @Override
          public void onSuccess(VoidResult result) {
            Gerrit.display(PageLinks.toChangeInEditMode(
                revision.getParentKey()));
          }
        });
  }

  @UiHandler("cancel")
  void onCancel(@SuppressWarnings("unused") ClickEvent e) {
    Gerrit.display(PageLinks.toChangeInEditMode(revision.getParentKey()));
  }

  void setShowTabs(boolean b) {
    if (b) {
      addStyleName(style.showTabs());
    } else {
      removeStyleName(style.showTabs());
    }
  }

  void setLineLength(int columns) {
    double w = columns * getCharWidthPx();
    columnMargin.getStyle().setMarginLeft(w, Unit.PX);
  }

  private double getCharWidthPx() {
    if (charWidthPx <= 1) {
      int len = 100;
      StringBuilder s = new StringBuilder();
      for (int i = 0; i < len; i++) {
        s.append('m');
      }

      Element e = DOM.createSpan();
      e.getStyle().setDisplay(Display.INLINE_BLOCK);
      e.setInnerText(s.toString());

      cm.measure().appendChild(e);
      charWidthPx = ((double) e.getOffsetWidth()) / len;
      e.removeFromParent();
    }
    return charWidthPx;
  }

  private void initEditor(String content) {
    String mode = prefs.syntaxHighlighting()
        ? ModeInjector.getContentType(type)
        : null;
    cm = CodeMirror.create(editor, Configuration.create()
      .set("readOnly", false)
      .set("cursorBlinkRate", 0)
      .set("cursorHeight", 0.85)
      .set("lineNumbers", true)
      .set("tabSize", prefs.tabSize())
      .set("lineWrapping", false)
      .set("scrollbarStyle", "overlay")
      .set("styleSelectedText", true)
      .set("showTrailingSpace", true)
      .set("keyMap", "default")
      .set("theme", prefs.theme().name().toLowerCase())
      .set("mode", mode));
    cm.setValue(content);

    columnMargin = DOM.createDiv();
    columnMargin.setClassName(style.columnMargin());
    cm.mover().appendChild(columnMargin);
  }

  private void injectMode(String type, AsyncCallback<Void> cb) {
    new ModeInjector().add(type).inject(cb);
  }
}
