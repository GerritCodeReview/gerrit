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

package com.google.gerrit.client.diff;

import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ReviewInfo;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;

class ReviewedPanel extends Composite {
  interface Binder extends UiBinder<HTMLPanel, ReviewedPanel> {}
  private static UiBinder<HTMLPanel, ReviewedPanel> uiBinder =
      GWT.create(Binder.class);

  @UiField
  Element fileName;

  @UiField
  CheckBox checkBox;

  @UiField
  Anchor nextLink;

  private PatchSet.Id patchId;
  private String fileId;

  ReviewedPanel(PatchSet.Id id, String path) {
    initWidget(uiBinder.createAndBindUi(this));
    patchId = id;
    fileId = path;
    fileName.setInnerText(path);
    nextLink.setHTML(PatchUtil.C.next() + Util.C.nextPatchLinkIcon());
  }

  void setReviewed(boolean reviewed) {
    RestApi api = ChangeApi.revision(patchId)
      .view("files")
      .id(fileId)
      .view("reviewed");
    if (reviewed) {
      api.put(CallbackGroup.<ReviewInfo>emptyCallback());
    } else {
      api.delete(CallbackGroup.<ReviewInfo>emptyCallback());
    }
  }

  boolean isReviewed() {
    return checkBox.getValue();
  }

  @UiHandler("checkBox")
  void onValueChange(ValueChangeEvent<Boolean> event) {
    setReviewed(event.getValue());
  }

  // TODO: Implement this to go to the next file in the patchset.
  void onNext(ClickEvent e) {
    setReviewed(true);
  }
}
