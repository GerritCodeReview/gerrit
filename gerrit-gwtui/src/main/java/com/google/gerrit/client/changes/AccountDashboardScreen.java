// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeTable.ApprovalViewType;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.AccountDashboardInfo;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.common.data.RemoteAccountDashboardInfo;
import com.google.gerrit.reviewdb.Account;


public class AccountDashboardScreen extends Screen implements ChangeListScreen {
  private final Account.Id ownerId;
  private ChangeTable table;
  private ChangeTable.Section byOwner;
  private ChangeTable.Section forReview;
  private ChangeTable.Section closed;

  public AccountDashboardScreen(final Account.Id id) {
    ownerId = id;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    table = new ChangeTable(true);
    table.addStyleName(Gerrit.RESOURCES.css().accountDashboard());
    byOwner = new ChangeTable.Section("", ApprovalViewType.STRONGEST, null);
    forReview = new ChangeTable.Section("", ApprovalViewType.USER, ownerId);
    closed = new ChangeTable.Section("", ApprovalViewType.STRONGEST, null);

    table.addSection(byOwner);
    table.addSection(forReview);
    table.addSection(closed);
    add(table);
    table.setSavePointerId(PageLinks.toAccountDashboard(ownerId));
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.LIST_SVC.forAccount(ownerId,
        new ScreenLoadCallback<AccountDashboardInfo>(this) {
          @Override
          protected void preDisplay(final AccountDashboardInfo r) {
            display(r);
          }
        });
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    table.setRegisterKeys(true);
  }

  private void display(final AccountDashboardInfo ad) {
    table.setAccountInfoCache(ad.getAccounts());

    final AccountInfo o = ad.getAccounts().get(ad.getOwner());
    final String name = FormatUtil.name(o);
    setWindowTitle(name);
    setPageTitle(Util.M.accountDashboardTitle(name));
    byOwner.setTitleText(Util.M.changesStartedBy(name));
    forReview.setTitleText(Util.M.changesReviewableBy(name));
    closed.setTitleText(Util.C.changesRecentlyClosed());

    byOwner.display(ad.getByOwner());
    forReview.display(ad.getForReview());
    closed.display(ad.getClosed());

    boolean appendByOwner = !ad.getByOwner().isEmpty();
    boolean appendForReview = !ad.getForReview().isEmpty();
    boolean appendClosed = !ad.getClosed().isEmpty();

    // Verify if there are changes for current user in the specified
    // (gerrit.config) remote server.
    if (ad.getRemoteChanges() != null && !ad.getRemoteChanges().isEmpty()) {
      for (final RemoteAccountDashboardInfo rChanges : ad.getRemoteChanges()) {
        table.setRemoteAccountInfoCache(rChanges.getRemoteAccounts());

        byOwner.display(rChanges.getByOwner(), rChanges.getRemoteUrl(),
            appendByOwner);
        if (!appendByOwner) {
          appendByOwner = !rChanges.getByOwner().isEmpty();
        }

        forReview.setRemoteOwnerId(rChanges.getRemoteOwnerId());
        forReview.display(rChanges.getForReview(), rChanges.getRemoteUrl(),
            appendForReview);

        if (!appendForReview) {
          appendForReview = !rChanges.getForReview().isEmpty();
        }

        closed.display(rChanges.getClosed(), rChanges.getRemoteUrl(),
            appendClosed);
        if (!appendClosed) {
          appendClosed = !rChanges.getClosed().isEmpty();
        }
      }
    }

    table.finishDisplay();
  }
}