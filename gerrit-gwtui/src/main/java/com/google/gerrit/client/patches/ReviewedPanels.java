// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.client.patches;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.patches.PatchTable.PatchValidator;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

class ReviewedPanels {
  final FlowPanel top;
  final FlowPanel bottom;

  private Patch.Key patchKey;
  private PatchTable fileList;
  private InlineHyperlink reviewedLink;
  private CheckBox checkBoxTop;
  private CheckBox checkBoxBottom;

  ReviewedPanels() {
    this.top = new FlowPanel();
    this.bottom = new FlowPanel();
    this.bottom.setStyleName(Gerrit.RESOURCES.css().reviewedPanelBottom());
  }

  void populate(Patch.Key pk, PatchTable pt, int patchIndex) {
    patchKey = pk;
    fileList = pt;
    reviewedLink = createReviewedLink(patchIndex);

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

  boolean getValue() {
    return checkBoxTop.getValue();
  }

  void setValue(final boolean value) {
    checkBoxTop.setValue(value);
    checkBoxBottom.setValue(value);
  }

  void setReviewedByCurrentUser(boolean reviewed) {
    PatchSet.Id ps = patchKey.getParentKey();
    if (ps.get() != 0) {
      if (fileList != null) {
        fileList.updateReviewedStatus(patchKey, reviewed);
      }

      RestApi api = new RestApi("/changes/").id(ps.getParentKey().get())
          .view("revisions").id(ps.get())
          .view("files").id(patchKey.getFileName())
          .view("reviewed");

      AsyncCallback<VoidResult> cb = new AsyncCallback<VoidResult>() {
        @Override
        public void onFailure(Throwable arg0) {
          // nop
        }

        @Override
        public void onSuccess(VoidResult result) {
          // nop
        }
      };
      if (reviewed) {
        api.put(cb);
      } else {
        api.delete(cb);
      }
    }
  }

  void go() {
    if (reviewedLink != null) {
      setReviewedByCurrentUser(true);
      reviewedLink.go();
    }
  }

  private InlineHyperlink createReviewedLink(final int patchIndex) {
    final PatchValidator unreviewedValidator = new PatchValidator() {
      @Override
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
            fileList.createLink(nextUnreviewedPatchIndex, null, null);
        reviewedLink.setText("");
      }
    }
    return reviewedLink;
  }

  private Anchor createReviewedAnchor() {
    SafeHtmlBuilder text = new SafeHtmlBuilder();
    text.append(PatchUtil.C.next());
    text.append(SafeHtml.asis(Util.C.nextPatchLinkIcon()));

    Anchor reviewedAnchor = new Anchor("");
    SafeHtml.set(reviewedAnchor, text);

    reviewedAnchor.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        setReviewedByCurrentUser(true);
        reviewedLink.go();
      }
    });

    return reviewedAnchor;
  }
}
