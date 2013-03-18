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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountLink;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

public class SubmitConditionBlock extends Composite {
  private final Widget missing;
  private ChangeInfo lastChange;

  public SubmitConditionBlock() {
    missing = new Widget() {
      {
        setElement(DOM.createElement("ul"));
      }
    };
    missing.setStyleName(Gerrit.RESOURCES.css().missingApprovalList());

    initWidget(missing);
  }

  void display(ChangeInfo change) {
    lastChange = change;
    removeAllChildren(missing.getElement());
    for (String condition : change.conditions()) {
      if (change.condition(condition).status() ==
          SubmitRecord.Condition.Status.NEED) {
        addMissingLabel(Util.M.needApproval(condition));
      }
    }

    missing.setVisible(DOM.getChildCount(missing.getElement()) > 0);
  }

  private void removeAllChildren(Element el) {
    for (int i = DOM.getChildCount(el) - 1; i >= 0; i--) {
      DOM.removeChild(el, DOM.getChild(el, i));
    }
  }

  private void addMissingLabel(String text) {
    Element li = DOM.createElement("li");
    li.setClassName(Gerrit.RESOURCES.css().missingApproval());
    DOM.setInnerText(li, text);
    DOM.appendChild(missing.getElement(), li);
  }

  private void reload() {
    ChangeApi.detail(lastChange.legacy_id().get(),
        new GerritCallback<ChangeInfo>() {
          @Override
          public void onSuccess(ChangeInfo result) {
            display(result);
          }
        });
  }
}
