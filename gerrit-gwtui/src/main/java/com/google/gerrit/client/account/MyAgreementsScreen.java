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
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.AgreementInfo;
import com.google.gerrit.reviewdb.AbstractAgreement;
import com.google.gerrit.reviewdb.AccountAgreement;
import com.google.gerrit.reviewdb.AccountGroupAgreement;
import com.google.gerrit.reviewdb.ContributorAgreement;
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
    Util.ACCOUNT_SVC.myAgreements(new GerritCallback<AgreementInfo>() {
      public void onSuccess(final AgreementInfo result) {
        agreements.display(result);
        display();
      }
    });
  }

  private class AgreementTable extends FancyFlexTable<AbstractAgreement> {
    AgreementTable() {
      table.setWidth("");
      table.setText(0, 1, Util.C.agreementStatus());
      table.setText(0, 2, Util.C.agreementName());
      table.setText(0, 3, Util.C.agreementDescription());
      table.setText(0, 4, Util.C.agreementAccepted());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      for (int c = 1; c <= 4; c++) {
        fmt.addStyleName(0, c, Gerrit.RESOURCES.css().dataHeader());
      }
    }

    void display(final AgreementInfo result) {
      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final AccountAgreement k : result.userAccepted) {
        addOne(result, k);
      }
      for (final AccountGroupAgreement k : result.groupAccepted) {
        addOne(result, k);
      }
    }

    void addOne(final AgreementInfo info, final AbstractAgreement k) {
      final int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);

      final ContributorAgreement cla = info.agreements.get(k.getAgreementId());
      final String statusName;
      if (cla == null || !cla.isActive()) {
        statusName = Util.C.agreementStatus_EXPIRED();
      } else {
        switch (k.getStatus()) {
          case NEW:
            statusName = Util.C.agreementStatus_NEW();
            break;
          case REJECTED:
            statusName = Util.C.agreementStatus_REJECTED();
            break;
          case VERIFIED:
            statusName = Util.C.agreementStatus_VERIFIED();
            break;
          default:
            statusName = k.getStatus().name();
        }
      }
      table.setText(row, 1, statusName);

      if (cla == null) {
        table.setText(row, 2, "");
        table.setText(row, 3, "");
      } else {
        final String url = cla.getAgreementUrl();
        if (url != null && url.length() > 0) {
          final Anchor a = new Anchor(cla.getShortName(), url);
          a.setTarget("_blank");
          table.setWidget(row, 2, a);
        } else {
          table.setText(row, 2, cla.getShortName());
        }
        table.setText(row, 3, cla.getShortDescription());
      }

      final SafeHtmlBuilder b = new SafeHtmlBuilder();
      b.append(FormatUtil.mediumFormat(k.getAcceptedOn()));
      b.br();
      b.append(FormatUtil.mediumFormat(k.getReviewedOn()));
      SafeHtml.set(table, row, 4, b);

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      for (int c = 1; c <= 4; c++) {
        fmt.addStyleName(row, c, Gerrit.RESOURCES.css().dataCell());
      }
      fmt.addStyleName(row, 4, Gerrit.RESOURCES.css().cLastUpdate());

      setRowItem(row, k);
    }
  }
}
