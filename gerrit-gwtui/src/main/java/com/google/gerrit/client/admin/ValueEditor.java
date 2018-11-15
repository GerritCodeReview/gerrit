// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.client.admin;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.editor.client.EditorError;
import com.google.gwt.editor.client.HasEditorErrors;
import com.google.gwt.editor.client.IsEditor;
import com.google.gwt.editor.client.LeafValueEditor;
import com.google.gwt.editor.ui.client.adapters.ValueBoxEditor;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiChild;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focusable;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.ValueBoxBase;
import com.google.gwt.user.client.ui.Widget;
import java.text.ParseException;
import java.util.List;

public class ValueEditor<T> extends Composite
    implements HasEditorErrors<T>, IsEditor<ValueBoxEditor<T>>, LeafValueEditor<T>, Focusable {
  interface Binder extends UiBinder<Widget, ValueEditor<?>> {}

  static final Binder uiBinder = GWT.create(Binder.class);

  @UiField SimplePanel textPanel;
  private Label textLabel;
  private StartEditHandlers startHandlers;

  @UiField Image editIcon;

  @UiField SimplePanel editPanel;

  @UiField DivElement errorLabel;

  private ValueBoxBase<T> editChild;
  private ValueBoxEditor<T> editProxy;
  private boolean ignoreEditorValue;
  private T value;

  public ValueEditor() {
    startHandlers = new StartEditHandlers();
    initWidget(uiBinder.createAndBindUi(this));
    editPanel.setVisible(false);
    editIcon.addClickHandler(startHandlers);
  }

  public void edit() {
    textPanel.removeFromParent();
    textPanel = null;
    textLabel = null;

    editIcon.removeFromParent();
    editIcon = null;
    startHandlers = null;

    editPanel.setVisible(true);
  }

  @Override
  public ValueBoxEditor<T> asEditor() {
    if (editProxy == null) {
      editProxy = new EditorProxy();
    }
    return editProxy;
  }

  @Override
  public T getValue() {
    return ignoreEditorValue ? value : asEditor().getValue();
  }

  @Override
  public void setValue(T value) {
    this.value = value;
    asEditor().setValue(value);
  }

  void setIgnoreEditorValue(boolean off) {
    ignoreEditorValue = off;
  }

  public void setEditTitle(String title) {
    editIcon.setTitle(title);
  }

  @UiChild(limit = 1, tagname = "display")
  public void setDisplay(Label widget) {
    textLabel = widget;
    textPanel.add(textLabel);

    textLabel.addClickHandler(startHandlers);
    textLabel.addDoubleClickHandler(startHandlers);
  }

  @UiChild(limit = 1, tagname = "editor")
  public void setEditor(ValueBoxBase<T> widget) {
    editChild = widget;
    editPanel.add(editChild);
    editProxy = null;
  }

  public void setEnabled(boolean enabled) {
    editIcon.setVisible(enabled);
    startHandlers.enabled = enabled;
  }

  @Override
  public void showErrors(List<EditorError> errors) {
    StringBuilder buf = new StringBuilder();
    for (EditorError error : errors) {
      if (error.getEditor().equals(editProxy)) {
        buf.append("\n");
        if (error.getUserData() instanceof ParseException) {
          buf.append(((ParseException) error.getUserData()).getMessage());
        } else {
          buf.append(error.getMessage());
        }
      }
    }

    if (0 < buf.length()) {
      errorLabel.setInnerText(buf.substring(1));
      errorLabel.getStyle().setDisplay(Display.BLOCK);
    } else {
      errorLabel.setInnerText("");
      errorLabel.getStyle().setDisplay(Display.NONE);
    }
  }

  @Override
  public void setAccessKey(char key) {
    editChild.setAccessKey(key);
  }

  @Override
  public void setFocus(boolean focused) {
    editChild.setFocus(focused);
    if (focused) {
      editChild.setCursorPos(editChild.getText().length());
    }
  }

  @Override
  public int getTabIndex() {
    return editChild.getTabIndex();
  }

  @Override
  public void setTabIndex(int index) {
    editChild.setTabIndex(index);
  }

  private class StartEditHandlers implements ClickHandler, DoubleClickHandler {
    boolean enabled;

    @Override
    public void onClick(ClickEvent event) {
      if (enabled && event.getNativeButton() == NativeEvent.BUTTON_LEFT) {
        edit();
      }
    }

    @Override
    public void onDoubleClick(DoubleClickEvent event) {
      if (enabled && event.getNativeButton() == NativeEvent.BUTTON_LEFT) {
        edit();
      }
    }
  }

  private class EditorProxy extends ValueBoxEditor<T> {
    EditorProxy() {
      super(editChild);
    }

    @Override
    public void setValue(T value) {
      super.setValue(value);
      if (textLabel == null) {
        setDisplay(new Label());
      }
      textLabel.setText(editChild.getText());
    }
  }
}
