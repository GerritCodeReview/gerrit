// Copyright 2008 Google Inc.
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

import com.google.gerrit.client.data.AccountDashboardInfo;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.ui.Screen;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;


public class MineScreen extends Screen {
  private ChangeTable table;
  private ChangeTable.Section byOwner;
  private ChangeTable.Section forReview;
  private ChangeTable.Section closed;

  public MineScreen() {
    this(null);
  }

  public MineScreen(final Account.Id id) {
    super(Util.C.mineHeading());

    table = new ChangeTable();
    byOwner = new ChangeTable.Section(Util.C.mineByMe());
    forReview = new ChangeTable.Section(Util.C.mineForReview());
    closed = new ChangeTable.Section(Util.C.mineClosed());

    Util.LIST_SVC.forAccount(id, new AsyncCallback<AccountDashboardInfo>() {
      public void onSuccess(final AccountDashboardInfo r) {
        byOwner.display(r.getByOwner());
        forReview.display(r.getForReview());
        closed.display(r.getClosed());
      }

      public void onFailure(final Throwable caught) {
        GWT.log("Fail", caught);
      }
    });

    table.addSection(byOwner);
    table.addSection(forReview);
    table.addSection(closed);

    add(table);
  }
}
