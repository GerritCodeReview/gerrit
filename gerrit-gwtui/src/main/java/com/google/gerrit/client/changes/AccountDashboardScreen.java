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
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;

public class AccountDashboardScreen extends Screen implements ChangeListScreen {
  // If changing default options, also update in
  // ChangeIT#defaultSearchDoesNotTouchDatabase().
  private static final Set<ListChangesOption> MY_DASHBOARD_OPTIONS;

  static {
    EnumSet<ListChangesOption> options = EnumSet.copyOf(ChangeTable.OPTIONS);
    options.add(ListChangesOption.REVIEWED);
    MY_DASHBOARD_OPTIONS = Collections.unmodifiableSet(options);
  }

  private final Account.Id ownerId;
  private final boolean mine;
  private ChangeTable table;
  private ChangeTable.Section outgoing;
  private ChangeTable.Section incoming;
  private ChangeTable.Section closed;

  public AccountDashboardScreen(Account.Id id) {
    ownerId = id;
    mine = Gerrit.isSignedIn() && ownerId.equals(Gerrit.getUserAccount().getId());
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    table =
        new ChangeTable() {
          {
            keysNavigation.add(
                new KeyCommand(0, 'R', Util.C.keyReloadSearch()) {
                  @Override
                  public void onKeyPress(KeyPressEvent event) {
                    Gerrit.display(getToken());
                  }
                });
          }
        };
    table.addStyleName(Gerrit.RESOURCES.css().accountDashboard());

    outgoing = new ChangeTable.Section();
    incoming = new ChangeTable.Section();
    closed = new ChangeTable.Section();

    String who = mine ? "self" : ownerId.toString();
    outgoing.setTitleWidget(
        new InlineHyperlink(Util.C.outgoingReviews(), PageLinks.toChangeQuery(queryOutgoing(who))));
    incoming.setTitleWidget(
        new InlineHyperlink(Util.C.incomingReviews(), PageLinks.toChangeQuery(queryIncoming(who))));
    incoming.setHighlightUnreviewed(mine);
    closed.setTitleWidget(
        new InlineHyperlink(Util.C.recentlyClosed(), PageLinks.toChangeQuery(queryClosed(who))));

    table.addSection(outgoing);
    table.addSection(incoming);
    table.addSection(closed);
    add(table);
    table.setSavePointerId("owner:" + ownerId);
  }

  private static String queryOutgoing(String who) {
    return "is:open owner:" + who;
  }

  private static String queryIncoming(String who) {
    return "is:open ((reviewer:"
        + who
        + " -owner:"
        + who
        + " -is:ignored) OR assignee:"
        + who
        + ")";
  }

  private static String queryClosed(String who) {
    return "is:closed (owner:" + who + " OR reviewer:" + who + " OR assignee:" + who + ")";
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    String who = mine ? "self" : ownerId.toString();
    ChangeList.queryMultiple(
        new ScreenLoadCallback<JsArray<ChangeList>>(this) {
          @Override
          protected void preDisplay(JsArray<ChangeList> result) {
            display(result);
          }
        },
        mine ? MY_DASHBOARD_OPTIONS : DashboardTable.OPTIONS,
        queryOutgoing(who),
        queryIncoming(who),
        queryClosed(who) + " -age:4w limit:10");
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    table.setRegisterKeys(true);
  }

  private void display(JsArray<ChangeList> result) {
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

    Collections.sort(Natives.asList(out), outComparator());

    table.updateColumnsForLabels(out, in, done);
    outgoing.display(out);
    incoming.display(in);
    closed.display(done);
    table.finishDisplay();
  }

  private Comparator<ChangeInfo> outComparator() {
    return new Comparator<ChangeInfo>() {
      @Override
      public int compare(ChangeInfo a, ChangeInfo b) {
        int cmp = a.created().compareTo(b.created());
        if (cmp != 0) {
          return cmp;
        }
        return a._number() - b._number();
      }
    };
  }

  private boolean hasChanges(JsArray<ChangeList> result) {
    for (ChangeList list : Natives.asList(result)) {
      if (list.length() != 0) {
        return true;
      }
    }
    return false;
  }

  private static String guessName(ChangeList list) {
    for (ChangeInfo change : Natives.asList(list)) {
      if (change.owner() != null && change.owner().name() != null) {
        return change.owner().name();
      }
    }
    return null;
  }
}
