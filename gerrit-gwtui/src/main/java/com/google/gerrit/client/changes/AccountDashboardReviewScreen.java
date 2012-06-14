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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.NotFoundScreen;
import com.google.gerrit.client.rpc.NativeList;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.reviewdb.client.Account;

import java.util.Collections;
import java.util.Comparator;

public class AccountDashboardReviewScreen extends Screen implements ChangeListScreen {
  private final Account.Id ownerId;
  private final boolean mine;
  private ChangeTable2 table;
  private ChangeTable2.Section byOwner;
  private ChangeTable2.Section forReview;
  private ChangeTable2.Section haveReviewed;

  public AccountDashboardReviewScreen(final Account.Id id) {
    ownerId = id;
    mine = Gerrit.isSignedIn() && ownerId.equals(Gerrit.getUserAccount().getId());
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    table = new ChangeTable2();
    table.addStyleName(Gerrit.RESOURCES.css().accountDashboard());

    byOwner = new ChangeTable2.Section();
    forReview = new ChangeTable2.Section();
    haveReviewed = new ChangeTable2.Section();

    final String name = Gerrit.getUserAccount().getFullName();
    byOwner.setTitleText(Util.M.changesStartedBy(name));
    forReview.setTitleText(Util.M.changesToBeReviewedBy(name));
    haveReviewed.setTitleText(Util.M.changesReviewedBy(name));

    table.addSection(byOwner);
    table.addSection(forReview);
    table.addSection(haveReviewed);
    add(table);
    table.setSavePointerId("owner:" + ownerId);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    String who = ownerId.toString();
    ChangeList.query(
        new ScreenLoadCallback<NativeList<ChangeList>>(this) {
          @Override
          protected void preDisplay(NativeList<ChangeList> result) {
            display(result);
          }
        },
        "is:open owner:" + who,
        "is:reviewable",
        "is:open -is:reviewable -is:draft -is:workinprogress AND " +
          "(is:starred OR is:watched OR reviewer:" + who + ")");
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    table.setRegisterKeys(true);
  }

  private void display(NativeList<ChangeList> result) {
    if (!mine) {
      // Only works for the currently logged in account.
      Gerrit.display(getToken(), new NotFoundScreen());
      return;
    }

    ChangeList open = result.get(0);
    ChangeList reviewable = result.get(1);
    ChangeList reviewed = result.get(2);

    setWindowTitle(Util.C.myDashboardTitle());
    setPageTitle(Util.C.myDashboardTitle());

    Collections.sort(open.asList(), oldestFirstComparator());
    Collections.sort(reviewable.asList(), oldestFirstComparator());
    Collections.sort(reviewed.asList(), oldestFirstComparator());

    table.updateColumnsForLabels(open, reviewable, reviewed);
    byOwner.display(open);
    forReview.display(reviewable);
    haveReviewed.display(reviewed);
    table.finishDisplay();
  }

  private Comparator<ChangeInfo> oldestFirstComparator() {
    return new Comparator<ChangeInfo>() {
      @Override
      public int compare(ChangeInfo a, ChangeInfo b) {
        int cmp = a.created().compareTo(b.created());
        if (cmp != 0) return cmp;
        return a._number() - b._number();
      }
    };
  }
}
