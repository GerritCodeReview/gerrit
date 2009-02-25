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
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SourcesTableEvents;
import com.google.gwt.user.client.ui.TableListener;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;

import java.util.HashSet;
import java.util.Set;

class ExternalIdPanel extends Composite {
  private IdTable identites;
  private Button deleteIdentity;

  ExternalIdPanel() {
    final FlowPanel body = new FlowPanel();

    identites = new IdTable();
    body.add(identites);

    deleteIdentity = new Button(Util.C.buttonDeleteIdentity());
    deleteIdentity.addClickListener(new ClickListener() {
      public void onClick(final Widget sender) {
        identites.deleteChecked();
      }
    });
    body.add(deleteIdentity);

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
    Util.ACCOUNT_SEC.myExternalIds(new GerritCallback<ExternalIdDetail>() {
      public void onSuccess(final ExternalIdDetail result) {
        identites.display(result);
        identites.finishDisplay(true);
      }
    });
  }

  private class IdTable extends FancyFlexTable<AccountExternalId> {
    IdTable() {
      table.setText(0, 2, Util.C.webIdLastUsed());
      table.setText(0, 3, Util.C.webIdStatus());
      table.setText(0, 4, Util.C.webIdEmail());
      table.setText(0, 5, Util.C.webIdIdentity());
      table.addTableListener(new TableListener() {
        public void onCellClicked(SourcesTableEvents sender, int row, int cell) {
          if (cell != 1 && getRowItem(row) != null) {
            movePointerTo(row);
          }
        }
      });

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, S_ICON_HEADER);
      fmt.addStyleName(0, 2, S_DATA_HEADER);
      fmt.addStyleName(0, 3, S_DATA_HEADER);
      fmt.addStyleName(0, 4, S_DATA_HEADER);
      fmt.addStyleName(0, 5, S_DATA_HEADER);
    }

    @Override
    protected Object getRowItemKey(final AccountExternalId item) {
      return item.getKey();
    }

    @Override
    protected boolean onKeyPress(final char keyCode, final int modifiers) {
      if (super.onKeyPress(keyCode, modifiers)) {
        return true;
      }
      if (modifiers == 0) {
        switch (keyCode) {
          case 's':
          case 'c':
            toggleCurrentRow();
            return true;
        }
      }
      return false;
    }

    @Override
    protected void onOpenItem(final AccountExternalId item) {
      toggleCurrentRow();
    }

    private void toggleCurrentRow() {
      final CheckBox cb = (CheckBox) table.getWidget(getCurrentRow(), 1);
      if (cb != null) {
        cb.setChecked(!cb.isChecked());
      }
    }

    void deleteChecked() {
      final HashSet<AccountExternalId.Key> keys =
          new HashSet<AccountExternalId.Key>();
      for (int row = 1; row < table.getRowCount(); row++) {
        final AccountExternalId k = getRowItem(row);
        if (k == null) {
          continue;
        }
        final CheckBox cb = (CheckBox) table.getWidget(row, 1);
        if (cb == null) {
          continue;
        }
        if (cb.isChecked()) {
          keys.add(k.getKey());
        }
      }
      if (!keys.isEmpty()) {
        deleteIdentity.setEnabled(false);
        Util.ACCOUNT_SEC.deleteExternalIds(keys,
            new GerritCallback<Set<AccountExternalId.Key>>() {
              public void onSuccess(final Set<AccountExternalId.Key> removed) {
                deleteIdentity.setEnabled(true);
                for (int row = 1; row < table.getRowCount();) {
                  final AccountExternalId k = getRowItem(row);
                  if (k != null && removed.contains(k.getKey())) {
                    table.removeRow(row);
                  } else {
                    row++;
                  }
                }
              }

              @Override
              public void onFailure(Throwable caught) {
                deleteIdentity.setEnabled(true);
                super.onFailure(caught);
              }
            });
      }
    }

    void display(final ExternalIdDetail result) {
      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final AccountExternalId k : result.getIds()) {
        addOneId(k, result);
      }

      final AccountExternalId mostRecent =
          AccountExternalId.mostRecent(result.getIds());
      if (mostRecent != null) {
        for (int row = 1; row < table.getRowCount(); row++) {
          if (getRowItem(row) == mostRecent) {
            // Remove the box from the most recent row, this prevents
            // the user from trying to delete the identity they last used
            // to login, possibly locking themselves out of the account.
            //
            table.setHTML(row, 1, "&nbsp;");
            break;
          }
        }
      }
    }

    void addOneId(final AccountExternalId k, final ExternalIdDetail detail) {
      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      final int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);

      if (k.canUserDelete()) {
        table.setWidget(row, 1, new CheckBox());
      } else {
        table.setHTML(row, 1, "&nbsp;");
      }
      if (k.getLastUsedOn() != null) {
        table.setText(row, 2, FormatUtil.mediumFormat(k.getLastUsedOn()));
      } else {
        table.setHTML(row, 2, "&nbsp;");
      }
      if (detail.isTrusted(k)) {
        table.setHTML(row, 3, "&nbsp;");
      } else {
        table.setText(row, 3, Util.C.untrustedProvider());
        fmt.addStyleName(row, 3, "gerrit-Identity-UntrustedExternalId");
      }
      table.setText(row, 4, k.getEmailAddress());
      table.setText(row, 5, k.getExternalId());

      fmt.addStyleName(row, 1, S_ICON_CELL);
      fmt.addStyleName(row, 2, S_DATA_CELL);
      fmt.addStyleName(row, 3, S_DATA_CELL);
      fmt.addStyleName(row, 3, "C_LAST_UPDATE");
      fmt.addStyleName(row, 4, S_DATA_CELL);
      fmt.addStyleName(row, 5, S_DATA_CELL);

      setRowItem(row, k);
    }
  }
}
