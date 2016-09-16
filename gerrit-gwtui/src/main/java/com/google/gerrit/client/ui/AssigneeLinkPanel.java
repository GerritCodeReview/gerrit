// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.client.AvatarImage;
import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.common.PageLinks;
import com.google.gwt.user.client.ui.FlowPanel;

/** Link to any assignees accounts dashboard. */
public class AssigneeLinkPanel extends FlowPanel {

  public AssigneeLinkPanel(AccountInfo info) {
    addStyleName(Gerrit.RESOURCES.css().accountLinkPanel());

    InlineHyperlink l =
        new InlineHyperlink(FormatUtil.name(info), PageLinks.toAssigneeQuery(
            assignedTo(info))) {
      @Override
      public void go() {
        Gerrit.display(getTargetHistoryToken());
      }
    };
    l.setTitle(FormatUtil.nameEmail(info));

    add(new AvatarImage(info));
    add(l);
  }

  public static String assignedTo(AccountInfo ai) {
    if (ai.email() != null) {
      return ai.email();
    } else if (ai.name() != null) {
      return ai.name();
    } else {
      return "";
    }
  }
}
