// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.change;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.api.ApiGlue;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.diff.DisplaySide;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

class LineComment extends Composite {
  interface Binder extends UiBinder<HTMLPanel, LineComment> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField Element sideLoc;
  @UiField Element psLoc;
  @UiField Element psNum;
  @UiField Element fileLoc;
  @UiField Element lineLoc;
  @UiField InlineHyperlink line;
  @UiField Element message;

  LineComment(
      CommentLinkProcessor clp,
      @Nullable Project.NameKey project,
      PatchSet.Id defaultPs,
      CommentInfo info) {
    initWidget(uiBinder.createAndBindUi(this));

    PatchSet.Id ps;
    if (info.patchSet() != defaultPs.get()) {
      ps = new PatchSet.Id(defaultPs.getParentKey(), info.patchSet());
      psNum.setInnerText(Integer.toString(info.patchSet()));
      sideLoc.removeFromParent();
      sideLoc = null;
    } else if (info.side() == Side.PARENT) {
      ps = defaultPs;
      psLoc.removeFromParent();
      psLoc = null;
      psNum = null;
    } else {
      ps = defaultPs;
      sideLoc.removeFromParent();
      sideLoc = null;
      psLoc.removeFromParent();
      psLoc = null;
      psNum = null;
    }

    if (info.hasLine()) {
      fileLoc.removeFromParent();
      fileLoc = null;

      line.setTargetHistoryToken(url(project, ps, info));
      line.setText(Integer.toString(info.line()));

    } else {
      lineLoc.removeFromParent();
      lineLoc = null;
      line = null;
    }

    if (info.message() != null) {
      message.setInnerSafeHtml(
          clp.apply(new SafeHtmlBuilder().append(info.message().trim()).wikify()));
      ApiGlue.fireEvent("comment", message);
    }
  }

  private static String url(@Nullable Project.NameKey project, PatchSet.Id ps, CommentInfo info) {
    return Dispatcher.toPatch(
        project,
        null,
        ps,
        info.path(),
        info.side() == Side.PARENT ? DisplaySide.A : DisplaySide.B,
        info.line());
  }
}
