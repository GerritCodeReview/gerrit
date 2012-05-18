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

public class AccountDashboardScreen extends Screen implements ChangeListScreen {
  private final Account.Id ownerId;
  private final boolean mine;
  private ChangeTable2 table;
  private ChangeTable2.Section outgoing;
  private ChangeTable2.Section incoming;
  private ChangeTable2.Section closed;

  public AccountDashboardScreen(final Account.Id id) {
    ownerId = id;
    mine = Gerrit.isSignedIn() && ownerId.equals(Gerrit.getUserAccount().getId());
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    table = new ChangeTable2();
    table.addStyleName(Gerrit.RESOURCES.css().accountDashboard());

    outgoing = new ChangeTable2.Section();
    incoming = new ChangeTable2.Section();
    closed = new ChangeTable2.Section();

    outgoing.setTitleText(Util.C.outgoingReviews());
    incoming.setTitleText(Util.C.incomingReviews());
    closed.setTitleText(Util.C.recentlyClosed());

    table.addSection(outgoing);
    table.addSection(incoming);
    table.addSection(closed);
    add(table);
    table.setSavePointerId("owner:" + ownerId);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    String who = mine ? "self" : ownerId.toString();
    ChangeList.query(
        new ScreenLoadCallback<NativeList<ChangeList>>(this) {
          @Override
          protected void preDisplay(NativeList<ChangeList> result) {
            display(result);
          }
        },
        "is:open owner:" + who,
        "is:open reviewer:" + who + " -owner:" + who,
        "is:closed owner:" + who + " -age:1w limit:10");
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    table.setRegisterKeys(true);
  }

  private void display(NativeList<ChangeList> result) {
    if (!mine && !hasChanges(result)) {
      // When no results are returned and the data is not for the
      // current user, the target user is presumed to not exist.
      Gerrit.display(getToken(), new NotFoundScreen());
      return;
    }

    ChangeList out = result.get(0);
    ChangeList in = result.get(1);
    ChangeList done = result.get(2);

    if (mine) {
      setWindowTitle(Util.C.myDashboardTitle());
      setPageTitle(Util.C.myDashboardTitle());
    } else {
      // The server doesn't tell us who the dashboard is for. Try to guess
      // by looking at a change started by the owner and extract the name.
      String name = guessName(out);
      if (name == null) {
        name = guessName(done);
      }
      if (name != null) {
        setWindowTitle(name);
        setPageTitle(Util.M.accountDashboardTitle(name));
      } else {
        setWindowTitle(Util.C.unknownDashboardTitle());
        setWindowTitle(Util.C.unknownDashboardTitle());
      }
    }

    Collections.sort(out.asList(), compare());
    Collections.sort(in.asList(), compare());

    table.updateColumnsForLabels(out, in, done);
    outgoing.display(out);
    incoming.display(in);
    closed.display(done);
    table.finishDisplay();
  }

  private Comparator<ChangeInfo> compare() {
    return new Comparator<ChangeInfo>() {
      @Override
      public int compare(ChangeInfo a, ChangeInfo b) {
        int cmp = a.project().compareTo(b.project());
        if (cmp != 0) return cmp;
        cmp = a.branch().compareTo(b.branch());
        if (cmp != 0) return cmp;

        String at = a.topic() != null ? a.topic() : "";
        String bt = b.topic() != null ? b.topic() : "";
        cmp = at.compareTo(bt);
        if (cmp != 0) return cmp;
        return a._number() - b._number();
      }
    };
  }

  private boolean hasChanges(NativeList<ChangeList> result) {
    for (ChangeList list : result.asList()) {
      if (!list.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private static String guessName(ChangeList list) {
    for (ChangeInfo change : list.asList()) {
      if (change.owner() != null && change.owner().name() != null) {
        return change.owner().name();
      }
    }
    return null;
  }
}
