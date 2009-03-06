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

import com.google.gerrit.client.data.ChangeInfo;
import com.google.gerrit.client.data.SingleListChangeInfo;
import com.google.gerrit.client.ui.Screen;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Hyperlink;

import java.util.List;


public abstract class AllSingleListScreen extends Screen {
  protected static final String MIN_SORTKEY = "";
  protected static final String MAX_SORTKEY = "z";

  protected static final int pageSize = 25;
  private ChangeTable table;
  private ChangeTable.Section section;
  protected Hyperlink prev;
  protected Hyperlink next;
  protected List<ChangeInfo> changes;

  protected final String anchorPrefix;
  protected boolean useLoadPrev;
  protected String pos;

  public AllSingleListScreen(final String title, final String anchorToken,
      final String positionToken) {
    super(title);
    anchorPrefix = anchorToken;
    useLoadPrev = positionToken.startsWith("p,");
    pos = positionToken.substring(2);
  }

  @Override
  public Object getScreenCacheToken() {
    return anchorPrefix;
  }

  @Override
  public Screen recycleThis(final Screen newScreen) {
    final AllSingleListScreen o = (AllSingleListScreen) newScreen;
    useLoadPrev = o.useLoadPrev;
    pos = o.pos;
    return this;
  }

  @Override
  public void onLoad() {
    if (table == null) {
      table = new ChangeTable();
      section = new ChangeTable.Section();

      table.addSection(section);
      table.setSavePointerId(anchorPrefix);
      add(table);

      prev = new Hyperlink(Util.C.pagedChangeListPrev(), true, "");
      prev.setVisible(false);

      next = new Hyperlink(Util.C.pagedChangeListNext(), true, "");
      next.setVisible(false);

      final HorizontalPanel buttons = new HorizontalPanel();
      buttons.setStyleName("gerrit-ChangeTable-PrevNextLinks");
      buttons.add(prev);
      buttons.add(next);
      add(buttons);
    }

    super.onLoad();

    if (useLoadPrev) {
      loadPrev();
    } else {
      loadNext();
    }
  }

  protected abstract void loadPrev();

  protected abstract void loadNext();

  protected void display(final SingleListChangeInfo result) {
    changes = result.getChanges();

    if (!changes.isEmpty()) {
      final ChangeInfo f = changes.get(0);
      final ChangeInfo l = changes.get(changes.size() - 1);

      prev.setTargetHistoryToken(anchorPrefix + ",p," + f.getSortKey());
      next.setTargetHistoryToken(anchorPrefix + ",n," + l.getSortKey());

      if (useLoadPrev) {
        prev.setVisible(!result.isAtEnd());
        next.setVisible(!MIN_SORTKEY.equals(pos));
      } else {
        prev.setVisible(!MAX_SORTKEY.equals(pos));
        next.setVisible(!result.isAtEnd());
      }
    }

    table.setAccountInfoCache(result.getAccounts());
    section.display(result.getChanges());
    table.finishDisplay(true);
  }
}
