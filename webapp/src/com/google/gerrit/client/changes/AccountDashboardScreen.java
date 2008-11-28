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
import com.google.gerrit.client.data.AccountInfo;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.ui.Screen;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;


public class AccountDashboardScreen extends Screen {
  private Account.Id ownerId;
  private ChangeTable table;
  private ChangeTable.Section byOwner;
  private ChangeTable.Section forReview;
  private ChangeTable.Section closed;

  public AccountDashboardScreen() {
    this(null);
  }

  public AccountDashboardScreen(final Account.Id id) {
    super("");

    ownerId = id;
    table = new ChangeTable();
    byOwner = new ChangeTable.Section("");
    forReview = new ChangeTable.Section("");
    closed = new ChangeTable.Section("");

    table.addSection(byOwner);
    table.addSection(forReview);
    table.addSection(closed);
    add(table);

    if (ownerId == null) {
      setRequiresSignIn(true);
    }
  }

  @Override
  public void onLoad() {
    super.onLoad();

    Util.LIST_SVC.forAccount(ownerId,
        new AsyncCallback<AccountDashboardInfo>() {
          public void onSuccess(final AccountDashboardInfo r) {
            display(r);
          }

          public void onFailure(final Throwable caught) {
            GWT.log("Fail", caught);
          }
        });
  }

  private void display(final AccountDashboardInfo r) {
    final AccountInfo o = r.getOwner();

    setTitleText(Util.M.accountDashboardTitle(o.getFullName()));
    byOwner.setTitleText(Util.M.changesUploadedBy(o.getFullName()));
    forReview.setTitleText(Util.M.changesReviewableBy(o.getFullName()));
    closed.setTitleText(Util.C.changesRecentlyClosed());

    byOwner.display(r.getByOwner());
    forReview.display(r.getForReview());
    closed.display(r.getClosed());
  }
}
