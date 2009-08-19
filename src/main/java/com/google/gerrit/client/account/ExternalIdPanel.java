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

package com.google.gerrit.client.account;

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.SignInDialog;
import com.google.gerrit.client.openid.OpenIdUtil;
import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ExternalIdPanel extends Composite {
  private IdTable identites;
  private Button deleteIdentity;

  ExternalIdPanel() {
    final FlowPanel body = new FlowPanel();

    identites = new IdTable();
    body.add(identites);

    deleteIdentity = new Button(Util.C.buttonDeleteIdentity());
    deleteIdentity.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        identites.deleteChecked();
      }
    });
    body.add(deleteIdentity);

    switch (Gerrit.getConfig().getAuthType()) {
      case OPENID: {
        final Button linkIdentity = new Button(Util.C.buttonLinkIdentity());
        linkIdentity.addClickHandler(new ClickHandler() {
          @Override
          public void onClick(final ClickEvent event) {
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
    final SignInDialog d = new SignInDialog(SignInDialog.Mode.LINK_IDENTIY);
    d.center();
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    refresh();
  }

  private void refresh() {
    Util.ACCOUNT_SEC
        .myExternalIds(new GerritCallback<List<AccountExternalId>>() {
          public void onSuccess(final List<AccountExternalId> result) {
            identites.display(result);
          }
        });
  }

  private class IdTable extends FancyFlexTable<AccountExternalId> {
    IdTable() {
      table.setWidth("");
      table.setText(0, 2, Util.C.webIdLastUsed());
      table.setText(0, 3, Util.C.webIdStatus());
      table.setText(0, 4, Util.C.webIdEmail());
      table.setText(0, 5, Util.C.webIdIdentity());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, S_ICON_HEADER);
      fmt.addStyleName(0, 2, S_DATA_HEADER);
      fmt.addStyleName(0, 3, S_DATA_HEADER);
      fmt.addStyleName(0, 4, S_DATA_HEADER);
      fmt.addStyleName(0, 5, S_DATA_HEADER);
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
        if (cb.getValue()) {
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

    void display(final List<AccountExternalId> result) {
      Collections.sort(result, new Comparator<AccountExternalId>() {
        @Override
        public int compare(AccountExternalId a, AccountExternalId b) {
          return emailOf(a).compareTo(emailOf(b));
        }

        private String emailOf(final AccountExternalId a) {
          return a.getEmailAddress() != null ? a.getEmailAddress() : "";
        }
      });

      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final AccountExternalId k : result) {
        addOneId(k);
      }

      final AccountExternalId mostRecent = AccountExternalId.mostRecent(result);
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

    void addOneId(final AccountExternalId k) {
      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      final int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);

      table.setWidget(row, 1, new CheckBox());
      if (k.getLastUsedOn() != null) {
        table.setText(row, 2, FormatUtil.mediumFormat(k.getLastUsedOn()));
      } else {
        table.setText(row, 2, "");
      }
      if (k.isTrusted()) {
        table.setText(row, 3, "");
      } else {
        table.setText(row, 3, Util.C.untrustedProvider());
        fmt.addStyleName(row, 3, "gerrit-Identity-UntrustedExternalId");
      }
      if (k.getEmailAddress() != null && k.getEmailAddress().length() > 0) {
        table.setText(row, 4, k.getEmailAddress());
      } else {
        table.setText(row, 4, "");
      }
      table.setText(row, 5, describe(k));

      fmt.addStyleName(row, 1, S_ICON_CELL);
      fmt.addStyleName(row, 2, S_DATA_CELL);
      fmt.addStyleName(row, 3, S_DATA_CELL);
      fmt.addStyleName(row, 3, "C_LAST_UPDATE");
      fmt.addStyleName(row, 4, S_DATA_CELL);
      fmt.addStyleName(row, 5, S_DATA_CELL);

      setRowItem(row, k);
    }

    private String describe(final AccountExternalId k) {
      if (k.isScheme(AccountExternalId.SCHEME_GERRIT)) {
        // A local user identity should just be itself.
        //
        return k.getSchemeRest(AccountExternalId.SCHEME_GERRIT);

      } else if (k.isScheme(AccountExternalId.SCHEME_MAILTO)) {
        // Describe a mailto address as just its email address, which
        // is already shown in the email address field.
        //
        return "";

      } else if (k.isScheme(OpenIdUtil.URL_GOOGLE)) {
        return OpenIdUtil.C.nameGoogle();

      } else if (k.isScheme(OpenIdUtil.URL_YAHOO)) {
        return OpenIdUtil.C.nameYahoo();

      } else if (k.isScheme(AccountExternalId.LEGACY_GAE)) {
        return OpenIdUtil.C.nameGoogle() + " (Imported from Google AppEngine)";

      } else {
        return k.getExternalId();
      }
    }
  }
}
