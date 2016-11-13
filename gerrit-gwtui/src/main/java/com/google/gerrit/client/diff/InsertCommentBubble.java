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

package com.google.gerrit.client.diff;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;
import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.Rect;

/** Bubble displayed near a selected region to create a comment. */
class InsertCommentBubble extends Composite {
  interface Binder extends UiBinder<HTMLPanel, InsertCommentBubble> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField Image icon;

  InsertCommentBubble(final CommentManager commentManager, final CodeMirror cm) {
    initWidget(uiBinder.createAndBindUi(this));
    addDomHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            setVisible(false);
            commentManager.newDraftCallback(cm).run();
          }
        },
        ClickEvent.getType());
  }

  void position(Rect r) {
    Style s = getElement().getStyle();
    int top = (int) (r.top() - (getOffsetHeight() - 8));
    if (top < 0) {
      s.setTop(-3, Unit.PX);
      s.setLeft(r.right() + 2, Unit.PX);
    } else {
      s.setTop(top, Unit.PX);
      s.setLeft((int) (r.right() - 14), Unit.PX);
    }
  }
}
