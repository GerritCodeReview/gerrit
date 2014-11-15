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
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.account.AccountApi;
import com.google.gerrit.client.account.EditPreferences;
import com.google.gerrit.client.changes.ChangeFileApi;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.common.EditPreferencesInfo;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwtexpui.globalkey.client.GlobalKey;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.ModeInjector;

public class EditScreen extends Screen {

  interface Binder extends UiBinder<HTMLPanel, EditScreen> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  interface EditScreenStyle extends CssResource {
    String columnMargin();
    String showTabs();
    String showLineNumbers();
  }

  private final PatchSet.Id revision;
  private final String path;
  private EditPreferences prefs;
  private EditPreferencesAction editPrefsAction;
  private CodeMirror cm;
  private String type;
  private Element columnMargin;
  private double charWidthPx;

  @UiField HTMLPanel panel;
  @UiField Element filePath;
  @UiField Button cancel;
  @UiField Button save;
  @UiField Image editSettings;
  @UiField Element editor;
  @UiField EditScreenStyle style;

  public EditScreen(Patch.Key patch) {
    this.revision = patch.getParentKey();
    this.path = patch.get();
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
    final CallbackGroup cmGroup = new CallbackGroup();
    CodeMirror.initLibrary(cmGroup.add(CallbackGroup.<Void> emptyCallback()));
    CallbackGroup group = new CallbackGroup();
    final AsyncCallback<Void> modeInjectorCb =
        group.add(CallbackGroup.<Void> emptyCallback());

    EditPreferencesInfo current = Gerrit.getEditPreferences();
    if (current == null) {
      AccountApi.getEditPreferences(cmGroup.addFinal(
          new GerritCallback<EditPreferences>() {
            @Override
            public void onSuccess(EditPreferences r) {
              prefs = r;
              EditPreferencesInfo global = new EditPreferencesInfo();
              r.copyTo(global);
              Gerrit.setEditPreferences(global);
              initContentType(modeInjectorCb);
            }
          }));
    } else {
      prefs = EditPreferences.create(current);
      cmGroup.addFinal(CallbackGroup.<Void> emptyCallback());
      initContentType(modeInjectorCb);
    }

    ChangeFileApi.getContentOrMessage(revision, path,
        group.addFinal(new ScreenLoadCallback<String>(this) {
          @Override
          protected void preDisplay(String content) {
            display(content);
          }
        }));
  }

  private void initContentType(
      final AsyncCallback<Void> modeInjectorCb) {
    if (prefs.syntaxHighlighting()) {
      ChangeFileApi.getContentType(revision, path,
          new GerritCallback<String>() {
            @Override
            public void onSuccess(String result) {
              type = result;
              injectMode(result, modeInjectorCb);
            }
          });
    } else {
      modeInjectorCb.onSuccess(null);
    }
  }

  @Override
  public void onShowView() {
    super.onShowView();
    int rest = Gerrit.getHeaderFooterHeight()
        + 30; // Estimate
    cm.setHeight(Window.getClientHeight() - rest);
    cm.refresh();
    cm.focus();
    setLineLength(prefs.lineLength());
  }

  CodeMirror getEditor() {
    return cm;
  }

  void setShowTabs(boolean b) {
    if (b) {
      panel.addStyleName(style.showTabs());
    } else {
      panel.removeStyleName(style.showTabs());
    }
  }

  void setLineLength(int columns) {
    double w = columns * getCharWidthPx();
    columnMargin.getStyle().setMarginLeft(w, Style.Unit.PX);
  }

  void setLineWrapping(boolean b) {
    cm.setOption("lineWrapping", b);
  }

  void setShowTrailingSpace(boolean b) {
    cm.setOption("showTrailingSpace", b);
  }

  void setShowLineNumbers(boolean b) {
    cm.setOption("lineNumbers", b);
    if (b) {
      panel.addStyleName(style.showLineNumbers());
    } else {
      panel.removeStyleName(style.showLineNumbers());
    }
  }

  void setSyntaxHighlighting(boolean b) {
    if (b) {
      final AsyncCallback<Void> cb = new AsyncCallback<Void>() {
        @Override
        public void onSuccess(Void result) {
          if (prefs.syntaxHighlighting()) {
            cm.setOption("mode", type);
          }
        }

        @Override
        public void onFailure(Throwable caught) {
          prefs.syntaxHighlighting(false);
        }
      };

      // When content type retrieval was skipped, we have to do it now
      if (type == null) {
        ChangeFileApi.getContentType(revision, path,
            new GerritCallback<String>() {
              @Override
              public void onSuccess(String result) {
                type = result;
                injectMode(result, cb);
              }
            });
      } else {
        injectMode(type, cb);
      }
    } else {
      cm.setOption("mode", (String) null);
    }
  }

  @UiHandler("editSettings")
  void onEditSetting(@SuppressWarnings("unused") ClickEvent e) {
    editPrefsAction.show();
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

  private void display(String content) {
    cm = CodeMirror.create(editor, getConfig());
    cm.setValue(content);
    editPrefsAction = new EditPreferencesAction(this, prefs);
    initPath();
    setShowLineNumbers(prefs.showLineNumbers());
    setShowTabs(prefs.showTabs());
    columnMargin = DOM.createDiv();
    columnMargin.setClassName(style.columnMargin());
    cm.getMoverElement().appendChild(columnMargin);
  }

  private void injectMode(String type, AsyncCallback<Void> cb) {
    new ModeInjector().add(type).inject(cb);
  }

  private Configuration getConfig() {
    String mode = prefs.syntaxHighlighting()
        ? ModeInjector.getContentType(type)
        : null;
    return Configuration.create()
        .set("cursorBlinkRate", 0)
        .set("cursorHeight", 0.85)
        .set("lineNumbers", prefs.showLineNumbers())
        .set("tabSize", prefs.tabSize())
        .set("lineWrapping", prefs.lineWrapping())
        .set("styleSelectedText", true)
        .set("showTrailingSpace", prefs.showTrailingSpace())
        .set("keyMap", prefs.keyMap().name().toLowerCase())
        .set("theme", prefs.theme().name().toLowerCase())
        .set("mode", mode);
  }

  private void initPath() {
    filePath.setInnerText(path);
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

      cm.getMoverElement().appendChild(e);
      double a = ((double) e.getOffsetWidth()) / len;
      e.removeFromParent();

      charWidthPx = a;
    }
    return charWidthPx;
  }
}