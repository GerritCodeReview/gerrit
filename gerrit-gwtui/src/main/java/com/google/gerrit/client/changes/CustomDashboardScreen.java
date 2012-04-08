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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.NativeList;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Screen;
import com.google.gwt.http.client.URL;

import java.util.ArrayList;
import java.util.List;

public class CustomDashboardScreen extends Screen implements ChangeListScreen {
  private String title;
  private List<String> titles;
  private List<String> queries;
  private ChangeTable2 table;
  private List<ChangeTable2.Section> sections;

  public CustomDashboardScreen(String params) {
    titles = new ArrayList<String>();
    queries = new ArrayList<String>();
    for (String kvPair : params.split("[,;&]")) {
      String[] kv = kvPair.split("=", 2);
      if (kv.length != 2 || kv[0].isEmpty()) {
        continue;
      }

      if ("title".equals(kv[0])) {
        title = URL.decodeQueryString(kv[1]);
      } else {
        titles.add(URL.decodeQueryString(kv[0]));
        queries.add(URL.decodeQueryString(kv[1]));
      }
    }
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();

    if (title != null) {
      setWindowTitle(title);
      setPageTitle(title);
    }

    table = new ChangeTable2();
    table.addStyleName(Gerrit.RESOURCES.css().accountDashboard());

    sections = new ArrayList<ChangeTable2.Section>();
    for (String title : titles) {
      ChangeTable2.Section s = new ChangeTable2.Section();
      s.setTitleText(title);
      table.addSection(s);
      sections.add(s);
    }
    add(table);
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    if (queries.isEmpty()) {
      display();
    } else if (queries.size() == 1) {
      ChangeList.next(queries.get(0),
          0, PagedSingleListScreen.MAX_SORTKEY,
          new ScreenLoadCallback<ChangeList>(this) {
            @Override
            protected void preDisplay(ChangeList result) {
              table.updateColumnsForLabels(result);
              sections.get(0).display(result);
              table.finishDisplay();
            }
        });
    } else {
      ChangeList.query(
          new ScreenLoadCallback<NativeList<ChangeList>>(this) {
            @Override
            protected void preDisplay(NativeList<ChangeList> result) {
              table.updateColumnsForLabels(
                  result.asList().toArray(new ChangeList[result.size()]));
              for (int i = 0; i < result.size(); i++) {
                sections.get(i).display(result.get(i));
              }
              table.finishDisplay();
            }
          },
          queries.toArray(new String[queries.size()]));
    }
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    table.setRegisterKeys(true);
  }
}
