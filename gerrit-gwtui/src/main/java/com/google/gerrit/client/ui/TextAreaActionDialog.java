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

package com.google.gerrit.client.ui;

import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwtexpui.globalkey.client.NpTextArea;

public abstract class TextAreaActionDialog extends CommentedActionDialog
    implements CloseHandler<PopupPanel> {
  protected final NpTextArea message;

  public TextAreaActionDialog(String title, String heading) {
    super(title, heading);

    message = new NpTextArea();
    message.setCharacterWidth(60);
    message.setVisibleLines(10);
    message.getElement().setPropertyBoolean("spellcheck", true);
    setFocusOn(message);

    contentPanel.add(message);
  }

  public String getMessageText() {
    return message.getText().trim();
  }
}
