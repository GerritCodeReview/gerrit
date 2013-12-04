// Copyright (C) 2014 Digia Plc and/or its subsidiary(-ies).
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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.NpTextArea;

public class Message extends Composite {
  private class SavedState {
    final PatchSet.Id patchSetId;
    final String text;
    public SavedState(final Message message) {
      patchSetId = message.patchSetId;
      text = message.getText();
    }
  }
  private static SavedState lastState;
  private boolean saveState = true;
  private PatchSet.Id patchSetId;

  private NpTextArea message;

  public Message(final PatchSet.Id patchSetId) {
    this.patchSetId = patchSetId;

    VerticalPanel body = new VerticalPanel();

    body.add(new SmallHeading(Util.C.headingCoverMessage()));

    final VerticalPanel editAreaContainer = new VerticalPanel();
    editAreaContainer.setStyleName(Gerrit.RESOURCES.css().coverMessage());
    body.add(editAreaContainer);

    message = new NpTextArea();
    message.setCharacterWidth(60);
    message.setVisibleLines(10);
    message.setSpellCheck(true);
    editAreaContainer.add(message);

    initWidget(body);
  }

  public String getText() {
    return message.getText();
  }

  public void setFocus(final boolean focus) {
    message.setFocus(focus);
  }

  public void setSaveState(boolean saveState) {
    this.saveState = saveState;
  }

  @Override
  protected void onLoad() {
    if (lastState != null && patchSetId.equals(lastState.patchSetId)) {
      message.setText(lastState.text);
    }
  }

  @Override
  protected void onUnload() {
    lastState = saveState ? new SavedState(this) : null;
  }
}

