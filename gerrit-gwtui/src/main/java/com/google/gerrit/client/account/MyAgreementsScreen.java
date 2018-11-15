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
import com.google.gerrit.client.info.AgreementInfo;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import java.util.List;

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
    AccountApi.getAgreements(
        "self",
        new ScreenLoadCallback<JsArray<AgreementInfo>>(this) {
          @Override
          public void preDisplay(JsArray<AgreementInfo> result) {
            agreements.display(Natives.asList(result));
          }
        });
  }

  private static class AgreementTable extends FancyFlexTable<ContributorAgreement> {
    AgreementTable() {
      table.setWidth("");
      table.setText(0, 1, Util.C.agreementName());
      table.setText(0, 2, Util.C.agreementDescription());

      FlexCellFormatter fmt = table.getFlexCellFormatter();
      for (int c = 1; c < 3; c++) {
        fmt.addStyleName(0, c, Gerrit.RESOURCES.css().dataHeader());
      }
    }

    void display(List<AgreementInfo> result) {
      while (1 < table.getRowCount()) {
        table.removeRow(table.getRowCount() - 1);
      }

      for (AgreementInfo info : result) {
        addOne(info);
      }
    }

    void addOne(AgreementInfo info) {
      int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);

      String url = info.url();
      if (url != null && url.length() > 0) {
        Anchor a = new Anchor(info.name(), url);
        a.setTarget("_blank");
        table.setWidget(row, 1, a);
      } else {
        table.setText(row, 1, info.name());
      }
      table.setText(row, 2, info.description());
      FlexCellFormatter fmt = table.getFlexCellFormatter();
      for (int c = 1; c < 3; c++) {
        fmt.addStyleName(row, c, Gerrit.RESOURCES.css().dataCell());
      }
    }
  }
}
