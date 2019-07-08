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
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.http.client.URL;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class DashboardTable extends ChangeTable {
  private List<Section> sections;
  private String title;
  private List<String> titles;
  private List<String> queries;

  public DashboardTable(final Screen screen, String params) {
    titles = new ArrayList<>();
    queries = new ArrayList<>();
    String foreach = null;
    for (String kvPair : params.split("[,;&]")) {
      String[] kv = kvPair.split("=", 2);
      if (kv.length != 2 || kv[0].isEmpty()) {
        continue;
      }

      if ("title".equals(kv[0])) {
        title = URL.decodeQueryString(kv[1]);
      } else if ("foreach".equals(kv[0])) {
        foreach = URL.decodeQueryString(kv[1]);
      } else {
        titles.add(URL.decodeQueryString(kv[0]));
        queries.add(URL.decodeQueryString(kv[1]));
      }
    }

    if (foreach != null) {
      ListIterator<String> it = queries.listIterator();
      while (it.hasNext()) {
        it.set(it.next() + " " + foreach);
      }
    }

    addStyleName(Gerrit.RESOURCES.css().accountDashboard());

    sections = new ArrayList<>();
    int i = 0;
    for (String title : titles) {
      Section s = new Section();
      String query = removeLimitAndAge(queries.get(i++));
      s.setTitleWidget(new InlineHyperlink(title, PageLinks.toChangeQuery(query)));
      addSection(s);
      sections.add(s);
    }

    keysNavigation.add(
        new KeyCommand(0, 'R', Util.C.keyReloadSearch()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            Gerrit.display(screen.getToken());
          }
        });
  }

  private String removeLimitAndAge(String query) {
    StringBuilder unlimitedQuery = new StringBuilder();
    String[] operators = query.split(" ");
    for (String o : operators) {
      if (!o.startsWith("limit:") && !o.startsWith("age:") && !o.startsWith("-age:")) {
        unlimitedQuery.append(o).append(" ");
      }
    }
    return unlimitedQuery.toString().trim();
  }

  @Override
  public String getTitle() {
    return title;
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    ChangeList.queryMultiple(
        new GerritCallback<JsArray<ChangeList>>() {
          @Override
          public void onSuccess(JsArray<ChangeList> result) {
            List<ChangeList> cls = Natives.asList(result);
            updateColumnsForLabels(cls.toArray(new ChangeList[cls.size()]));
            for (int i = 0; i < cls.size(); i++) {
              sections.get(i).display(cls.get(i));
            }
            finishDisplay();
          }
        },
        OPTIONS,
        queries.toArray(new String[queries.size()]));
  }
}
