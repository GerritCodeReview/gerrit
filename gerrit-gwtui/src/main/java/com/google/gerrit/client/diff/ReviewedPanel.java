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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.PatchTable;
import com.google.gerrit.client.changes.PatchTable.PatchValidator;
import com.google.gerrit.client.changes.ReviewInfo;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.diff.DraftBox.Binder;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

class ReviewedPanel extends Composite {
  interface Binder extends UiBinder<HTMLPanel, ReviewedPanel> {}
  private static UiBinder<HTMLPanel, ReviewedPanel> uiBinder =
      GWT.create(Binder.class);

  interface ReviewedPanelStyle extends CssResource {
    String isBottom();
  }

  @UiField
  Element fileName;

  @UiField
  CheckBox checkBox;

  @UiField
  Anchor nextLink;

  @UiField
  ReviewedPanelStyle style;

  private PatchSet.Id patchId;
  private String fileId;

  private Patch.Key patchKey;
  private ReviewedPanel other;
  private InlineHyperlink reviewedLink;
  private CheckBox checkBoxTop;
  private CheckBox checkBoxBottom;


  ReviewedPanel(boolean isBottom) {
    initWidget(uiBinder.createAndBindUi(this));
    if (isBottom) {
      fileName.addClassName(style.isBottom());
    }
  }

  static void link(ReviewedPanel top, ReviewedPanel bottom) {
    top.other = bottom;
    bottom.other = top;
  }

  public void populate(Patch.Key pk, PatchTable pt, int patchIndex,
      PatchScreen.Type patchScreenType) {
    patchKey = pk;
    fileList = pt;
    reviewedLink = createReviewedLink(patchIndex, patchScreenType);

    top.clear();
    checkBoxTop = createReviewedCheckbox();
    top.add(checkBoxTop);
    top.add(createReviewedAnchor());

    bottom.clear();
    checkBoxBottom = createReviewedCheckbox();
    bottom.add(checkBoxBottom);
    bottom.add(createReviewedAnchor());
  }

  private CheckBox createReviewedCheckbox() {
    final CheckBox checkBox = new CheckBox(PatchUtil.C.reviewedAnd() + " ");
    checkBox.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
      @Override
      public void onValueChange(ValueChangeEvent<Boolean> event) {
        final boolean value = event.getValue();
        setReviewedByCurrentUser(value);
        if (checkBoxTop.getValue() != value) {
          checkBoxTop.setValue(value);
        }
        if (checkBoxBottom.getValue() != value) {
          checkBoxBottom.setValue(value);
        }
      }
    });
    return checkBox;
  }

  public boolean getValue() {
    return checkBoxTop.getValue();
  }

  public void setValue(final boolean value) {
    checkBoxTop.setValue(value);
    checkBoxBottom.setValue(value);
  }

  public void setReviewedByCurrentUser(boolean reviewed) {
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

  public void go() {
    if (reviewedLink != null) {
      setReviewedByCurrentUser(true);
      reviewedLink.go();
    }
  }

  private InlineHyperlink createReviewedLink(final int patchIndex,
      final PatchScreen.Type patchScreenType) {
    final PatchValidator unreviewedValidator = new PatchValidator() {
      public boolean isValid(Patch patch) {
        return !patch.isReviewedByCurrentUser();
      }
    };

    InlineHyperlink reviewedLink = new ChangeLink("", patchKey.getParentKey());
    if (fileList != null) {
      int nextUnreviewedPatchIndex =
          fileList.getNextPatch(patchIndex, true, unreviewedValidator,
              fileList.PREFERENCE_VALIDATOR);

      if (nextUnreviewedPatchIndex > -1) {
        // Create invisible patch link to change page
        reviewedLink =
            fileList.createLink(nextUnreviewedPatchIndex, patchScreenType,
                null, null);
        reviewedLink.setText("");
      }
    }
    return reviewedLink;
  }

  private void setReviewedAnchor() {
    SafeHtmlBuilder text = new SafeHtmlBuilder();
    text.append(PatchUtil.C.next());
    text.append(SafeHtml.asis(Util.C.nextPatchLinkIcon()));

    Anchor reviewedAnchor = new Anchor("");
    SafeHtml.set(reviewedAnchor, text);
  }

  @UiHandler("checkBox")
  void onValueChange(ValueChangeEvent<Boolean> event) {
    boolean value = event.getValue();
    setReviewedByCurrentUser(value);
    other.checkBoxBottom;
    if (checkBoxTop.getValue() != value) {
      checkBoxTop.setValue(value);
    }
    if (checkBoxBottom.getValue() != value) {
      checkBoxBottom.setValue(value);
    }
  }

  @UiHandler("nextLink")
  void onNext(ClickEvent e) {
    setReviewedByCurrentUser(true);
    reviewedLink.go();
  }
}
