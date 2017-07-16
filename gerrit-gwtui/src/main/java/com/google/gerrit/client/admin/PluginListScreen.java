// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.client.admin;

import static com.google.gerrit.common.PageLinks.ADMIN_PLUGINS;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.api.ExtensionScreen;
import com.google.gerrit.client.plugins.PluginInfo;
import com.google.gerrit.client.plugins.PluginMap;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.client.ui.PagingHyperlink;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.ImageResourceRenderer;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PluginListScreen extends PaginatedPluginScreen {

  private Panel pluginPanel;
  private PluginTable pluginTable;
  private Hyperlink prev;
  private Hyperlink next;
  private NpTextBox filterTxt;

  private Query query;

  public PluginListScreen() {
    super();
  }

  public PluginListScreen(String params) {
    this();
    parseToken(params);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    query = new Query(match).start(start).run();
  }

  @Override
  public String getScreenToken() {
    return ADMIN_PLUGINS;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    initPluginList();
  }

  private void initPluginList() {
    setPageTitle(AdminConstants.I.plugins());
    initPageHeader();

    prev = PagingHyperlink.createPrev();
    prev.setVisible(false);

    next = PagingHyperlink.createNext();
    next.setVisible(false);

    pluginTable = new PluginTable();
    pluginTable.addStyleName(Gerrit.RESOURCES.css().pluginsTable());

    pluginPanel = new FlowPanel();
    pluginPanel.setWidth("500px");
    pluginPanel.add(pluginTable);
    add(pluginPanel);
    final HorizontalPanel buttons = new HorizontalPanel();
    buttons.setStyleName(Gerrit.RESOURCES.css().changeTablePrevNextLinks());
    buttons.add(prev);
    buttons.add(next);
    add(buttons);
  }

  private void initPageHeader() {
    final HorizontalPanel hp = new HorizontalPanel();
    hp.setStyleName(Gerrit.RESOURCES.css().pluginFilterPanel());
    final Label filterLabel = new Label(AdminConstants.I.pluginFilter());
    filterLabel.setStyleName(Gerrit.RESOURCES.css().pluginFilterLabel());
    hp.add(filterLabel);
    filterTxt = new NpTextBox();
    filterTxt.setValue(match);
    filterTxt.addKeyUpHandler(
        new KeyUpHandler() {
          @Override
          public void onKeyUp(KeyUpEvent event) {
            Query q =
                new Query(filterTxt.getValue())
                    .open(event.getNativeKeyCode() == KeyCodes.KEY_ENTER);
            if (match.equals(q.qMatch)) {
              q.start(start);
            }
            if (q.open || !match.equals(q.qMatch)) {
              if (query == null) {
                q.run();
              }
              query = q;
            }
          }
        });
    hp.add(filterTxt);
    add(hp);
  }

  private static class PluginTable extends FancyFlexTable<PluginInfo> {
    PluginTable() {
      table.setText(0, 1, AdminConstants.I.columnPluginName());
      table.setText(0, 2, AdminConstants.I.columnPluginSettings());
      table.setText(0, 3, AdminConstants.I.columnPluginVersion());
      table.setText(0, 4, AdminConstants.I.columnPluginStatus());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 4, Gerrit.RESOURCES.css().dataHeader());
    }

    public void display(PluginMap plugins) {
      displaySubset(plugins, 0, plugins.size());
    }

    public void displaySubset(PluginMap plugins, int fromIndex, int toIndex) {
      while (1 < table.getRowCount()) {
        table.removeRow(table.getRowCount() - 1);
      }

      List<PluginInfo> list = Natives.asList(plugins.values());
      Collections.sort(
          list,
          new Comparator<PluginInfo>() {
            @Override
            public int compare(PluginInfo a, PluginInfo b) {
              return a.name().compareTo(b.name());
            }
          });
      for (PluginInfo p : list.subList(fromIndex, toIndex)) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, p);
      }
    }

    void populate(int row, PluginInfo plugin) {
      if (plugin.disabled() || plugin.indexUrl() == null) {
        table.setText(row, 1, plugin.name());
      } else {
        table.setWidget(
            row, 1, new Anchor(plugin.name(), Gerrit.selfRedirect(plugin.indexUrl()), "_blank"));

        if (new ExtensionScreen(plugin.name() + "/settings").isFound()) {
          InlineHyperlink adminScreenLink = new InlineHyperlink();
          adminScreenLink.setHTML(new ImageResourceRenderer().render(Gerrit.RESOURCES.gear()));
          adminScreenLink.setTargetHistoryToken("/x/" + plugin.name() + "/settings");
          adminScreenLink.setTitle(AdminConstants.I.pluginSettingsToolTip());
          table.setWidget(row, 2, adminScreenLink);
        }
      }

      table.setText(row, 3, plugin.version());
      table.setText(
          row,
          4,
          plugin.disabled() ? AdminConstants.I.pluginDisabled() : AdminConstants.I.pluginEnabled());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 4, Gerrit.RESOURCES.css().dataCell());

      setRowItem(row, plugin);
    }
  }

  private class Query {
    private final String qMatch;
    private int qStart;
    private boolean open;

    Query(String match) {
      this.qMatch = match;
    }

    Query start(int start) {
      this.qStart = start;
      return this;
    }

    Query open(boolean open) {
      this.open = open;
      return this;
    }

    Query run() {
      int limit = open ? 1 : pageSize + 1;
      PluginMap.match(
          qMatch,
          limit,
          qStart,
          new GerritCallback<PluginMap>() {
            @Override
            public void onSuccess(PluginMap result) {
              if (!isAttached()) {
                // View has been disposed.
              } else if (query == Query.this) {
                query = null;
                showMap(result);
              } else {
                query.run();
              }
            }
          });
      return this;
    }

    private void showMap(PluginMap result) {
      if (open && !result.isEmpty()) {
        Gerrit.display(result.values().get(0).name());
        return;
      }

      setToken(getTokenForScreen(qMatch, qStart));
      PluginListScreen.this.match = qMatch;
      PluginListScreen.this.start = qStart;

      if (result.size() <= pageSize) {
        pluginTable.display(result);
        next.setVisible(false);
      } else {
        pluginTable.displaySubset(result, 0, result.size() - 1);
        setupNavigationLink(next, qMatch, qStart + pageSize);
      }

      if (qStart > 0) {
        setupNavigationLink(prev, qMatch, qStart - pageSize);
      } else {
        prev.setVisible(false);
      }

      if (!isCurrentView()) {
        display();
      }
    }
  }
}
