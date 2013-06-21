//Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.ui.HTMLPanel;

import java.sql.Timestamp;

/** An HtmlPanel for displaying a published comment */
// TODO: Make the buttons functional.
class PublishedBox extends CommentBox {
  interface Binder extends UiBinder<HTMLPanel, PublishedBox> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  PublishedBox(AccountInfo author, Timestamp when, String message,
      CommentLinkProcessor linkProcessor) {
    initWidget(uiBinder.createAndBindUi(this));
    init(author, when, message, linkProcessor, false);
  }
}
