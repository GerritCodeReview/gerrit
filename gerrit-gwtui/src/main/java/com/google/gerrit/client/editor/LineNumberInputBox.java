// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.gwt.event.dom.client.KeyCodes.KEY_ESCAPE;

import com.google.gerrit.client.ui.NpIntTextBox;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.PopupPanel;

class LineNumberInputBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, LineNumberInputBox> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Style extends CssResource {
    String dialog();
  }

  private final EditScreen view;

  @UiField Style style;
  @UiField Anchor close;
  @UiField NpIntTextBox lineNumber;
  @UiField Button go;

  LineNumberInputBox(EditScreen view) {
    this.view = view;
    initWidget(uiBinder.createAndBindUi(this));
  }

  @Override
  public void onLoad() {
    super.onLoad();
    addDomHandler(new KeyDownHandler() {
      @Override
      public void onKeyDown(KeyDownEvent event) {
        if (event.getNativeKeyCode() == KEY_ESCAPE) {
          close();
        }
      }
    }, KeyDownEvent.getType());
  }

  @UiHandler("go")
  void onGotoLine(@SuppressWarnings("unused") ClickEvent e) {
    view.goToLine(lineNumber.getIntValue());
    close();
  }

  @UiHandler("close")
  void onClose(ClickEvent e) {
    e.preventDefault();
    close();
  }

  private void close() {
    ((PopupPanel) getParent()).hide();
  }
}
