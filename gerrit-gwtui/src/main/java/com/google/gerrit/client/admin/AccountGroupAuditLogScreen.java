// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.gerrit.client.FormatUtil.mediumFormat;
import static com.google.gerrit.client.FormatUtil.name;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.groups.GroupApi;
import com.google.gerrit.client.groups.GroupAuditEventInfo;
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.info.GroupInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import java.util.List;

public class AccountGroupAuditLogScreen extends AccountGroupScreen {
  private AuditEventTable auditEventTable;

  public AccountGroupAuditLogScreen(GroupInfo toShow, String token) {
    super(toShow, token);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    add(new SmallHeading(Util.C.headingAuditLog()));
    auditEventTable = new AuditEventTable();
    add(auditEventTable);
  }

  @Override
  protected void display(GroupInfo group, boolean canModify) {
    GroupApi.getAuditLog(
        group.getGroupUUID(),
        new GerritCallback<JsArray<GroupAuditEventInfo>>() {
          @Override
          public void onSuccess(JsArray<GroupAuditEventInfo> result) {
            auditEventTable.display(Natives.asList(result));
          }
        });
  }

  private class AuditEventTable extends FancyFlexTable<GroupAuditEventInfo> {
    AuditEventTable() {
      table.setText(0, 1, Util.C.columnDate());
      table.setText(0, 2, Util.C.columnType());
      table.setText(0, 3, Util.C.columnMember());
      table.setText(0, 4, Util.C.columnByUser());

      FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 4, Gerrit.RESOURCES.css().dataHeader());
    }

    void display(List<GroupAuditEventInfo> auditEvents) {
      while (1 < table.getRowCount()) {
        table.removeRow(table.getRowCount() - 1);
      }

      for (GroupAuditEventInfo auditEvent : auditEvents) {
        int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, auditEvent);
      }
    }

    void populate(int row, GroupAuditEventInfo auditEvent) {
      FlexCellFormatter fmt = table.getFlexCellFormatter();
      table.setText(row, 1, mediumFormat(auditEvent.date()));

      switch (auditEvent.type()) {
        case ADD_USER:
        case ADD_GROUP:
          table.setText(row, 2, Util.C.typeAdded());
          break;
        case REMOVE_USER:
        case REMOVE_GROUP:
          table.setText(row, 2, Util.C.typeRemoved());
          break;
      }

      switch (auditEvent.type()) {
        case ADD_USER:
        case REMOVE_USER:
          table.setText(row, 3, formatAccount(auditEvent.memberAsUser()));
          break;
        case ADD_GROUP:
        case REMOVE_GROUP:
          GroupInfo member = auditEvent.memberAsGroup();
          if (AccountGroup.isInternalGroup(member.getGroupUUID())) {
            table.setWidget(
                row, 3, new Hyperlink(member.name(), Dispatcher.toGroup(member.getGroupUUID())));
            fmt.getElement(row, 3).setTitle(null);
          } else if (member.url() != null) {
            Anchor a = new Anchor();
            a.setText(member.name());
            a.setHref(member.url());
            a.setTitle("UUID " + member.getGroupUUID().get());
            table.setWidget(row, 3, a);
            fmt.getElement(row, 3).setTitle(null);
          } else {
            table.setText(row, 3, member.name());
            fmt.getElement(row, 3).setTitle("UUID " + member.getGroupUUID().get());
          }
          break;
      }

      table.setText(row, 4, formatAccount(auditEvent.user()));

      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 4, Gerrit.RESOURCES.css().dataCell());

      setRowItem(row, auditEvent);
    }
  }

  private static String formatAccount(AccountInfo account) {
    StringBuilder b = new StringBuilder();
    b.append(name(account));
    b.append(" (");
    b.append(account._accountId());
    b.append(")");
    return b.toString();
  }
}
