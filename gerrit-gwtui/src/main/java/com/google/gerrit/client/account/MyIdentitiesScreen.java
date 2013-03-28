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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.auth.openid.OpenIdUtil;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.common.auth.openid.OpenIdUrls;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window.Location;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MyIdentitiesScreen extends SettingsScreen {
  private IdTable identites;
  private Button deleteIdentity;

  @Override
  protected void onInitUI() {
    super.onInitUI();

    identites = new IdTable();
    add(identites);

    deleteIdentity = new Button(Util.C.buttonDeleteIdentity());
    deleteIdentity.setEnabled(false);
    deleteIdentity.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        identites.deleteChecked();
      }
    });
    add(deleteIdentity);

    if (Gerrit.getConfig().getAuthType() == AuthType.OPENID) {
      Button linkIdentity = new Button(Util.C.buttonLinkIdentity());
      linkIdentity.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          Location.assign(Gerrit.loginRedirect(History.getToken()) + "?link");
        }
      });
      add(linkIdentity);
    }
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.ACCOUNT_SEC
        .myExternalIds(new ScreenLoadCallback<List<AccountExternalId>>(this) {
          public void preDisplay(final List<AccountExternalId> result) {
            identites.display(result);
          }
        });
  }

  private class IdTable extends FancyFlexTable<AccountExternalId> {
    private ValueChangeHandler<Boolean> updateDeleteHandler;

    IdTable() {
      table.setWidth("");
      table.setText(0, 2, Util.C.webIdStatus());
      table.setText(0, 3, Util.C.webIdEmail());
      table.setText(0, 4, Util.C.webIdIdentity());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 4, Gerrit.RESOURCES.css().dataHeader());

      updateDeleteHandler = new ValueChangeHandler<Boolean>() {
        @Override
        public void onValueChange(ValueChangeEvent<Boolean> event) {
          updateDeleteButton();
        }
      };
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
      if (keys.isEmpty()) {
        updateDeleteButton();
      } else {
        deleteIdentity.setEnabled(false);
        Util.ACCOUNT_SEC.deleteExternalIds(keys,
            new GerritCallback<Set<AccountExternalId.Key>>() {
              public void onSuccess(final Set<AccountExternalId.Key> removed) {
                for (int row = 1; row < table.getRowCount();) {
                  final AccountExternalId k = getRowItem(row);
                  if (k != null && removed.contains(k.getKey())) {
                    table.removeRow(row);
                  } else {
                    row++;
                  }
                }
                updateDeleteButton();
              }

              @Override
              public void onFailure(Throwable caught) {
                updateDeleteButton();
                super.onFailure(caught);
              }
            });
      }
    }

    void updateDeleteButton() {
      int off = 0;
      boolean on = false;
      for (int row = 1; row < table.getRowCount(); row++) {
        if (table.getWidget(row, 1) == null) {
          off++;
        } else {
          CheckBox sel = (CheckBox) table.getWidget(row, 1);
          if (sel.getValue()) {
            on = true;
            break;
          }
        }
      }
      deleteIdentity.setVisible(off < table.getRowCount() - 1);
      deleteIdentity.setEnabled(on);
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
      updateDeleteButton();
    }

    void addOneId(final AccountExternalId k) {
      if (k.isScheme(AccountExternalId.SCHEME_USERNAME)) {
        // Don't display the username as an identity here.
        return;
      }

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      final int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);

      if (k.canDelete()) {
        final CheckBox sel = new CheckBox();
        sel.addValueChangeHandler(updateDeleteHandler);
        table.setWidget(row, 1, sel);
      } else {
        table.setText(row, 1, "");
      }
      if (k.isTrusted()) {
        table.setText(row, 2, "");
      } else {
        table.setText(row, 2, Util.C.untrustedProvider());
        fmt.addStyleName(row, 2, Gerrit.RESOURCES.css()
            .identityUntrustedExternalId());
      }
      if (k.getEmailAddress() != null && k.getEmailAddress().length() > 0) {
        table.setText(row, 3, k.getEmailAddress());
      } else {
        table.setText(row, 3, "");
      }
      table.setText(row, 4, describe(k));

      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().iconCell());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 4, Gerrit.RESOURCES.css().dataCell());

      setRowItem(row, k);
    }

    private String describe(final AccountExternalId k) {
      if (k.isScheme(AccountExternalId.SCHEME_GERRIT)) {
        // A local user identity should just be itself.
        //
        return k.getSchemeRest();

      } else if (k.isScheme(AccountExternalId.SCHEME_USERNAME)) {
        // A local user identity should just be itself.
        //
        return k.getSchemeRest();

      } else if (k.isScheme(AccountExternalId.SCHEME_MAILTO)) {
        // Describe a mailto address as just its email address, which
        // is already shown in the email address field.
        //
        return "";

      } else if (k.isScheme(OpenIdUrls.URL_GOOGLE)) {
        return OpenIdUtil.C.nameGoogle();

      } else if (k.isScheme(OpenIdUrls.URL_YAHOO)) {
        return OpenIdUtil.C.nameYahoo();

      } else if (k.isScheme(AccountExternalId.LEGACY_GAE)) {
        return OpenIdUtil.C.nameGoogle() + " (Imported from Google AppEngine)";

      } else {
        return k.getExternalId();
      }
    }
  }
}
