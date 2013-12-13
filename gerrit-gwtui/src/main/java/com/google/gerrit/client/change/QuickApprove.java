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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.LabelInfo;
import com.google.gerrit.client.changes.ReviewInput;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

/** Applies a label with one mouse click. */
class QuickApprove extends Button implements ClickHandler {
  private Change.Id changeId;
  private String revision;
  private ReviewInput input;

  QuickApprove() {
    addClickHandler(this);
  }

  void set(ChangeInfo info, String commit) {
    if (!info.has_permitted_labels() || !info.status().isOpen()) {
      // Quick approve needs at least one label on an open change.
      setVisible(false);
      return;
    }

    String qName = null;
    String qValueStr = null;
    short qValue = 0;

    for (LabelInfo label : Natives.asList(info.all_labels().values())) {
      if (!info.permitted_labels().containsKey(label.name())) {
        continue;
      }

      JsArrayString values = info.permitted_values(label.name());
      if (values.length() == 0) {
        continue;
      }

      switch (label.status()) {
        case NEED: // Label is required for submit.
          break;

        case OK: // Label already applied.
        case MAY: // Label is not required.
          continue;

        case REJECT: // Submit cannot happen, do not quick approve.
        case IMPOSSIBLE:
          setVisible(false);
          return;
      }

      String s = values.get(values.length() - 1);
      short v = LabelInfo.parseValue(s);
      if (v > 0 && s.equals(label.max_value())) {
        if (qName != null) {
          // Quick approve is available for one label only.
          setVisible(false);
          return;
        }

        qName = label.name();
        qValueStr = s;
        qValue = v;
      }
    }

    if (qName != null)  {
      changeId = info.legacy_id();
      revision = commit;
      input = ReviewInput.create();
      input.label(qName, qValue);
      setText(qName + qValueStr);
    } else {
      setVisible(false);
    }
  }

  @Override
  public void setText(String text) {
    setHTML(new SafeHtmlBuilder().openDiv().append(text).closeDiv());
  }

  @Override
  public void onClick(ClickEvent event) {
    ChangeApi.revision(changeId.get(), revision)
      .view("review")
      .post(input, new GerritCallback<ReviewInput>() {
        @Override
        public void onSuccess(ReviewInput result) {
          Gerrit.display(PageLinks.toChange(changeId));
        }
      });
  }
}
