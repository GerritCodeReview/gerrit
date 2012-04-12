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
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.AgreementInfo;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

public class MyAgreementsScreen extends SettingsScreen {
  private AgreementTable agreements;

  @Override
  protected void onInitUI() {
    super.onInitUI();

    agreements = new AgreementTable();
    add(agreements);
    add(new Hyperlink(Util.C.newAgreement(), PageLinks.SETTINGS_NEW_AGREEMENT));
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.ACCOUNT_SVC.myAgreements(new ScreenLoadCallback<AgreementInfo>(this) {
      public void preDisplay(final AgreementInfo result) {
        agreements.display(result);
      }
    });
  }

  private class AgreementTable extends FancyFlexTable<ContributorAgreement> {
    AgreementTable() {
      table.setWidth("");
      table.setText(0, 1, Util.C.agreementStatus());
      table.setText(0, 2, Util.C.agreementName());
      table.setText(0, 3, Util.C.agreementDescription());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      for (int c = 1; c < 4; c++) {
        fmt.addStyleName(0, c, Gerrit.RESOURCES.css().dataHeader());
      }
    }

    void display(final AgreementInfo result) {
      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final String k : result.accepted) {
        addOne(result, k);
      }
    }

    void addOne(final AgreementInfo info, final String k) {
      final int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);

      final ContributorAgreement cla = info.agreements.get(k);
      final String statusName;
      if (cla == null) {
        statusName = Util.C.agreementStatus_EXPIRED();
      } else {
        statusName = Util.C.agreementStatus_VERIFIED();
      }
      table.setText(row, 1, statusName);

      if (cla == null) {
        table.setText(row, 2, "");
        table.setText(row, 3, "");
      } else {
        final String url = cla.getAgreementUrl();
        if (url != null && url.length() > 0) {
          final Anchor a = new Anchor(cla.getName(), url);
          a.setTarget("_blank");
          table.setWidget(row, 2, a);
        } else {
          table.setText(row, 2, cla.getName());
        }
        table.setText(row, 3, cla.getDescription());
      }
      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      for (int c = 1; c < 4; c++) {
        fmt.addStyleName(row, c, Gerrit.RESOURCES.css().dataCell());
      }

      setRowItem(row, cla);
    }
  }
}
