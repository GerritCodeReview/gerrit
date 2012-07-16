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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.plugins.PluginDisabled;
import com.google.gerrit.client.plugins.PluginInfo;
import com.google.gerrit.client.plugins.PluginMap;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Panel;

import java.util.LinkedList;
import java.util.List;

public class PluginListScreen extends PluginScreen {

  private Panel pluginPanel;
  private PluginTable pluginTable;
  private Button disableButton;

  @Override
  protected void onInitUI() {
    super.onInitUI();
    initPluginList();
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    PluginMap.all(new ScreenLoadCallback<PluginMap>(this) {
      @Override
      protected void preDisplay(final PluginMap result) {
        pluginTable.display(result);
      }
    });
  }

  private void initPluginList() {
    pluginTable = new PluginTable();
    pluginTable.addStyleName(Gerrit.RESOURCES.css().pluginsTable());

    disableButton = new Button(Util.C.buttonDisablePlugins());
    disableButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        doDisablePlugins();
      }
    });

    HorizontalPanel buttonPanel = new HorizontalPanel();
    buttonPanel.add(disableButton);
    buttonPanel.addStyleName(Gerrit.RESOURCES.css().pluginsTableButtonPanel());

    pluginPanel = new FlowPanel();
    pluginPanel.setWidth("500px");
    pluginPanel.add(pluginTable);
    pluginPanel.add(buttonPanel);
    add(pluginPanel);
  }

  private class PluginTable extends FancyFlexTable<PluginInfo> {
    PluginTable() {
      table.setText(0, 2, Util.C.columnPluginName());
      table.setText(0, 3, Util.C.columnPluginVersion());
      table.setText(0, 4, Util.C.columnPluginStatus());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 4, Gerrit.RESOURCES.css().dataHeader());
    }

    void display(final PluginMap plugins) {
      while (1 < table.getRowCount()) {
        table.removeRow(table.getRowCount() - 1);
      }

      for (final PluginInfo p : plugins.values().asList()) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, p);
      }
    }

    void populate(final int row, final PluginInfo plugin) {
      CheckBox checkBox = new CheckBox();
      table.setWidget(row, 1, checkBox);
      if (plugin.isDisabled()) {
        checkBox.setEnabled(false);
        table.setText(row, 2, plugin.name());
      } else {
        table.setWidget(
            row,
            2,
            new Anchor(plugin.name(), Gerrit.selfRedirect("/plugins/"
                + plugin.name() + "/")));
      }
      table.setText(row, 3, plugin.version());
      if (plugin.isDisabled()) {
        table.setText(row, 4, Util.C.pluginDisabled());
      }

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().iconCell());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 4, Gerrit.RESOURCES.css().dataCell());

      setRowItem(row, plugin);
    }

    private List<String> getSelectedPlugins() {
      List<String> selectedList = new LinkedList<String>();
      for (int i = 0; i < table.getRowCount(); i++) {
        PluginInfo plugin = getRowItem(i);
        if (plugin == null || plugin.isDisabled()) {
          continue;
        }
        CheckBox checkbox = (CheckBox) table.getWidget(i, 1);
        if (checkbox.getValue()) {
          selectedList.add(plugin.name());
        }
      }
      return selectedList;
    }
  }

  private void doDisablePlugins() {
    disableButton.setEnabled(false);
    PluginMap.disable(pluginTable.getSelectedPlugins(),
        new ScreenLoadCallback<PluginDisabled>(this) {
          @Override
          protected void preDisplay(PluginDisabled result) {
            PluginMap.all(new ScreenLoadCallback<PluginMap>(
                PluginListScreen.this) {
              @Override
              protected void preDisplay(final PluginMap result) {
                disableButton.setEnabled(true);
                pluginTable.display(result);
              }

              @Override
              public void onFailure(Throwable caught) {
                super.onFailure(caught);
                disableButton.setEnabled(true);
              }
            });
          }

          @Override
          public void onFailure(Throwable caught) {
            super.onFailure(caught);
            disableButton.setEnabled(true);
          }
        });
  }
}
