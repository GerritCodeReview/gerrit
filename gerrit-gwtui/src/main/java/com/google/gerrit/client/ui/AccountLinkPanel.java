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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.AvatarImage;
import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.user.client.ui.FlowPanel;
import java.util.function.Function;

/** Link to any user's account dashboard. */
public class AccountLinkPanel extends FlowPanel {
  public static AccountLinkPanel create(AccountInfo ai) {
    return withStatus(ai, Change.Status.NEW);
  }

  public static AccountLinkPanel withStatus(AccountInfo ai, Change.Status status) {
    return new AccountLinkPanel(ai, name -> PageLinks.toAccountQuery(name, status));
  }

  public static AccountLinkPanel forAssignee(AccountInfo ai) {
    return new AccountLinkPanel(ai, PageLinks::toAssigneeQuery);
  }

  private AccountLinkPanel(AccountInfo ai, Function<String, String> nameToQuery) {
    addStyleName(Gerrit.RESOURCES.css().accountLinkPanel());

    InlineHyperlink l =
        new InlineHyperlink(FormatUtil.name(ai), nameToQuery.apply(name(ai))) {
          @Override
          public void go() {
            Gerrit.display(getTargetHistoryToken());
          }
        };
    l.setTitle(FormatUtil.nameEmail(ai));

    add(new AvatarImage(ai));
    add(l);
  }

  private static String name(AccountInfo ai) {
    if (ai.email() != null) {
      return ai.email();
    } else if (ai.name() != null) {
      return ai.name();
    } else if (ai._accountId() != 0) {
      return "" + ai._accountId();
    } else {
      return "";
    }
  }
}
