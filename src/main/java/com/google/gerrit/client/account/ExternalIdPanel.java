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

package com.google.gerrit.client.account;

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.SignInDialog;
import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SourcesTableEvents;
import com.google.gwt.user.client.ui.TableListener;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;

import java.util.List;

class ExternalIdPanel extends Composite {
  private IdTable identites;

  ExternalIdPanel() {
    final FlowPanel body = new FlowPanel();

    identites = new IdTable();
    body.add(identites);

    switch (Common.getGerritConfig().getLoginType()) {
      case OPENID: {
        final Button linkIdentity = new Button(Util.C.buttonLinkIdentity());
        linkIdentity.addClickListener(new ClickListener() {
          public void onClick(final Widget sender) {
            doLinkIdentity();
          }
        });
        body.add(linkIdentity);
        break;
      }
    }

    initWidget(body);
  }

  void doLinkIdentity() {
    final SignInDialog d =
        new SignInDialog(SignInDialog.Mode.LINK_IDENTIY,
            new GerritCallback<Object>() {
              public void onSuccess(final Object result) {
                refresh();
              }
            });
    d.center();
  }

  @Override
  public void onLoad() {
    super.onLoad();
    refresh();
  }

  private void refresh() {
    Util.ACCOUNT_SEC
        .myExternalIds(new GerritCallback<List<AccountExternalId>>() {
          public void onSuccess(final List<AccountExternalId> result) {
            identites.display(result);
            identites.finishDisplay(true);
          }
        });
  }

  private class IdTable extends FancyFlexTable<AccountExternalId> {
    IdTable() {
      table.setText(0, 1, Util.C.webIdLastUsed());
      table.setText(0, 2, Util.C.webIdEmail());
      table.setText(0, 3, Util.C.webIdIdentity());
      table.addTableListener(new TableListener() {
        public void onCellClicked(SourcesTableEvents sender, int row, int cell) {
          movePointerTo(row);
        }
      });

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, S_DATA_HEADER);
      fmt.addStyleName(0, 2, S_DATA_HEADER);
      fmt.addStyleName(0, 3, S_DATA_HEADER);
    }

    @Override
    protected Object getRowItemKey(final AccountExternalId item) {
      return item.getKey();
    }

    void display(final List<AccountExternalId> result) {
      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final AccountExternalId k : result) {
        addOneId(k);
      }
    }

    void addOneId(final AccountExternalId k) {
      final int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);

      table.setText(row, 1, FormatUtil.mediumFormat(k.getLastUsedOn()));
      table.setText(row, 2, k.getEmailAddress());
      table.setText(row, 3, k.getExternalId());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, S_DATA_CELL);
      fmt.addStyleName(row, 1, "C_LAST_UPDATE");
      fmt.addStyleName(row, 2, S_DATA_CELL);
      fmt.addStyleName(row, 3, S_DATA_CELL);

      setRowItem(row, k);
    }
  }
}
