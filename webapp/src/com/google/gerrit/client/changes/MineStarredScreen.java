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
import com.google.gerrit.client.data.ChangeInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountScreen;

import java.util.List;


public class MineStarredScreen extends AccountScreen {
  private ChangeTable table;
  private ChangeTable.Section starred;

  public MineStarredScreen() {
    super(Util.C.starredHeading());

    table = new ChangeTable();
    starred = new ChangeTable.Section();

    table.addSection(starred);
    table.setSavePointerId(Link.MINE_STARRED);

    add(table);
  }

  @Override
  public Object getScreenCacheToken() {
    return Link.MINE_STARRED;
  }

  @Override
  public void onLoad() {
    super.onLoad();
    Util.LIST_SVC.myStarredChanges(new GerritCallback<List<ChangeInfo>>() {
      public void onSuccess(final List<ChangeInfo> result) {
        starred.display(result);
        table.finishDisplay();
      }
    });
  }
}
