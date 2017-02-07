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

import com.google.gerrit.client.ui.AccountGroupSuggestOracle;
import com.google.gerrit.client.ui.RemoteSuggestBox;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.editor.client.LeafValueEditor;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.HasCloseHandlers;
import com.google.gwt.event.logical.shared.HasSelectionHandlers;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focusable;

public class GroupReferenceBox extends Composite
    implements LeafValueEditor<GroupReference>,
        HasSelectionHandlers<GroupReference>,
        HasCloseHandlers<GroupReferenceBox>,
        Focusable {
  private final AccountGroupSuggestOracle oracle;
  private final RemoteSuggestBox suggestBox;

  public GroupReferenceBox() {
    oracle = new AccountGroupSuggestOracle();
    suggestBox = new RemoteSuggestBox(oracle);
    initWidget(suggestBox);

    suggestBox.addSelectionHandler(
        new SelectionHandler<String>() {
          @Override
          public void onSelection(SelectionEvent<String> event) {
            SelectionEvent.fire(GroupReferenceBox.this, toValue(event.getSelectedItem()));
          }
        });
    suggestBox.addCloseHandler(
        new CloseHandler<RemoteSuggestBox>() {
          @Override
          public void onClose(CloseEvent<RemoteSuggestBox> event) {
            suggestBox.setText("");
            CloseEvent.fire(GroupReferenceBox.this, GroupReferenceBox.this);
          }
        });
  }

  public void setVisibleLength(int len) {
    suggestBox.setVisibleLength(len);
  }

  @Override
  public HandlerRegistration addSelectionHandler(SelectionHandler<GroupReference> handler) {
    return addHandler(handler, SelectionEvent.getType());
  }

  @Override
  public HandlerRegistration addCloseHandler(CloseHandler<GroupReferenceBox> handler) {
    return addHandler(handler, CloseEvent.getType());
  }

  @Override
  public GroupReference getValue() {
    return toValue(suggestBox.getText());
  }

  private GroupReference toValue(String name) {
    if (name != null && !name.isEmpty()) {
      return new GroupReference(oracle.getUUID(name), name);
    }
    return null;
  }

  @Override
  public void setValue(GroupReference value) {
    suggestBox.setText(value != null ? value.getName() : "");
  }

  @Override
  public int getTabIndex() {
    return suggestBox.getTabIndex();
  }

  @Override
  public void setTabIndex(int index) {
    suggestBox.setTabIndex(index);
  }

  @Override
  public void setFocus(boolean focused) {
    suggestBox.setFocus(focused);
  }

  @Override
  public void setAccessKey(char key) {
    suggestBox.setAccessKey(key);
  }

  public void setProject(Project.NameKey projectName) {
    oracle.setProject(projectName);
  }
}
