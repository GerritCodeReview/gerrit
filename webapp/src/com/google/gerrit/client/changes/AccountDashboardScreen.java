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

import com.google.gerrit.client.Link;
import com.google.gerrit.client.data.AccountDashboardInfo;
import com.google.gerrit.client.data.AccountInfo;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Screen;


public class AccountDashboardScreen extends Screen {
  private Account.Id ownerId;
  private ChangeTable table;
  private ChangeTable.Section byOwner;
  private ChangeTable.Section forReview;
  private ChangeTable.Section closed;

  public AccountDashboardScreen(final Account.Id id) {
    ownerId = id;
  }

  @Override
  public Object getScreenCacheToken() {
    return getClass();
  }

  @Override
  public Screen recycleThis(final Screen newScreen) {
    ownerId = ((AccountDashboardScreen) newScreen).ownerId;
    return this;
  }

  @Override
  public void onLoad() {
    if (table == null) {
      table = new ChangeTable();
      byOwner = new ChangeTable.Section("");
      forReview = new ChangeTable.Section("");
      closed = new ChangeTable.Section("");

      table.addSection(byOwner);
      table.addSection(forReview);
      table.addSection(closed);
      add(table);
    }
    table.setSavePointerId(Link.toAccountDashboard(ownerId));
    super.onLoad();
    Util.LIST_SVC.forAccount(ownerId,
        new ScreenLoadCallback<AccountDashboardInfo>() {
          public void onSuccess(final AccountDashboardInfo r) {
            // TODO Actually we want to cancel the RPC if detached.
            if (isAttached()) {
              display(r);
            }
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
    table.finishDisplay();
  }
}
