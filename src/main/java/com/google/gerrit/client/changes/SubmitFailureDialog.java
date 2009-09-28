// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.data.ChangeDetail;
import com.google.gerrit.client.reviewdb.ChangeMessage;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;

class SubmitFailureDialog extends AutoCenterDialogBox {
  SubmitFailureDialog(final ChangeDetail result, final ChangeMessage msg) {
    setText(Util.C.submitFailed());

    final FlowPanel body = new FlowPanel();
    final Widget msgText =
        new SafeHtmlBuilder().append(msg.getMessage().trim()).wikify()
            .toBlockWidget();
    body.add(msgText);

    final FlowPanel buttonPanel = new FlowPanel();
    buttonPanel.setStyleName("gerrit-CommentEditor-Buttons");
    Button close = new Button(Util.C.buttonClose());
    close.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        hide();
      }
    });
    buttonPanel.add(close);
    body.add(buttonPanel);

    add(body);
  }
}
