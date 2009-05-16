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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.data.GerritConfig;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountGroupSuggestOracle;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ProjectRightsPanel extends Composite {
  private Project.Id projectId;

  private RightsTable rights;
  private Button delRight;
  private Button addRight;
  private ListBox catBox;
  private ListBox rangeMinBox;
  private ListBox rangeMaxBox;
  private NpTextBox nameTxtBox;
  private SuggestBox nameTxt;

  public ProjectRightsPanel(final Project.Id toShow) {
    final FlowPanel body = new FlowPanel();
    initRights(body);
    initWidget(body);

    projectId = toShow;
  }

  @Override
  protected void onLoad() {
    enableForm(false);
    super.onLoad();

    Util.PROJECT_SVC.projectDetail(projectId,
        new GerritCallback<ProjectDetail>() {
          public void onSuccess(final ProjectDetail result) {
            enableForm(true);
            display(result);
          }
        });
  }

  private void enableForm(final boolean on) {
    delRight.setEnabled(on);

    final boolean canAdd = on && catBox.getItemCount() > 0;
    addRight.setEnabled(canAdd);
    nameTxtBox.setEnabled(canAdd);
    catBox.setEnabled(canAdd);
    rangeMinBox.setEnabled(canAdd);
    rangeMaxBox.setEnabled(canAdd);
  }

  private void initRights(final Panel body) {
    final FlowPanel addPanel = new FlowPanel();
    addPanel.setStyleName("gerrit-AddSshKeyPanel");

    final Grid addGrid = new Grid(4, 2);

    catBox = new ListBox();
    rangeMinBox = new ListBox();
    rangeMaxBox = new ListBox();

    catBox.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(final ChangeEvent event) {
        populateRangeBoxes();
      }
    });
    for (final ApprovalType at : Common.getGerritConfig().getApprovalTypes()) {
      final ApprovalCategory c = at.getCategory();
      catBox.addItem(c.getName(), c.getId().get());
    }
    for (final ApprovalType at : Common.getGerritConfig().getActionTypes()) {
      final ApprovalCategory c = at.getCategory();
      catBox.addItem(c.getName(), c.getId().get());
    }
    if (catBox.getItemCount() > 0) {
      catBox.setSelectedIndex(0);
      populateRangeBoxes();
    }

    addGrid.setText(0, 0, Util.C.columnApprovalCategory() + ":");
    addGrid.setWidget(0, 1, catBox);

    nameTxtBox = new NpTextBox();
    nameTxt = new SuggestBox(new AccountGroupSuggestOracle(), nameTxtBox);
    nameTxtBox.setVisibleLength(50);
    nameTxtBox.setText(Util.C.defaultAccountGroupName());
    nameTxtBox.addStyleName("gerrit-InputFieldTypeHint");
    nameTxtBox.addFocusHandler(new FocusHandler() {
      @Override
      public void onFocus(FocusEvent event) {
        if (Util.C.defaultAccountGroupName().equals(nameTxtBox.getText())) {
          nameTxtBox.setText("");
          nameTxtBox.removeStyleName("gerrit-InputFieldTypeHint");
        }
      }
    });
    nameTxtBox.addBlurHandler(new BlurHandler() {
      @Override
      public void onBlur(BlurEvent event) {
        if ("".equals(nameTxtBox.getText())) {
          nameTxtBox.setText(Util.C.defaultAccountGroupName());
          nameTxtBox.addStyleName("gerrit-InputFieldTypeHint");
        }
      }
    });
    addGrid.setText(1, 0, Util.C.columnGroupName() + ":");
    addGrid.setWidget(1, 1, nameTxt);

    addGrid.setText(2, 0, Util.C.columnRightRange() + ":");
    addGrid.setWidget(2, 1, rangeMinBox);

    addGrid.setText(3, 0, "");
    addGrid.setWidget(3, 1, rangeMaxBox);

    addRight = new Button(Util.C.buttonAddProjectRight());
    addRight.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doAddNewRight();
      }
    });
    addPanel.add(addGrid);
    addPanel.add(addRight);

    rights = new RightsTable();

    delRight = new Button(Util.C.buttonDeleteGroupMembers());
    delRight.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        rights.deleteChecked();
      }
    });

    body.add(new SmallHeading(Util.C.headingAccessRights()));
    body.add(rights);
    body.add(delRight);
    body.add(addPanel);
  }

  void display(final ProjectDetail result) {
    rights.display(result.groups, result.rights);
  }

  private void doAddNewRight() {
    int idx = catBox.getSelectedIndex();
    final ApprovalType at;
    ApprovalCategoryValue min, max;
    if (idx < 0) {
      return;
    }
    at =
        Common.getGerritConfig().getApprovalType(
            new ApprovalCategory.Id(catBox.getValue(idx)));
    if (at == null) {
      return;
    }

    idx = rangeMinBox.getSelectedIndex();
    if (idx < 0) {
      return;
    }
    min = at.getValue(Short.parseShort(rangeMinBox.getValue(idx)));
    if (min == null) {
      return;
    }

    idx = rangeMaxBox.getSelectedIndex();
    if (idx < 0) {
      return;
    }
    max = at.getValue(Short.parseShort(rangeMaxBox.getValue(idx)));
    if (max == null) {
      return;
    }

    final String groupName = nameTxt.getText();
    if ("".equals(groupName)
        || Util.C.defaultAccountGroupName().equals(groupName)) {
      return;
    }

    if (min.getValue() > max.getValue()) {
      // If the user selects it backwards in the web UI, help them out
      // by reversing the order to what we would expect.
      //
      final ApprovalCategoryValue newMin = max;
      final ApprovalCategoryValue newMax = min;
      min = newMin;
      max = newMax;
    }

    addRight.setEnabled(false);
    Util.PROJECT_SVC.addRight(projectId, at.getCategory().getId(), groupName,
        min.getValue(), max.getValue(), new GerritCallback<ProjectDetail>() {
          public void onSuccess(final ProjectDetail result) {
            addRight.setEnabled(true);
            nameTxt.setText("");
            display(result);
          }

          @Override
          public void onFailure(final Throwable caught) {
            addRight.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  private void populateRangeBoxes() {
    final int idx = catBox.getSelectedIndex();
    final ApprovalType at;
    if (idx >= 0) {
      at =
          Common.getGerritConfig().getApprovalType(
              new ApprovalCategory.Id(catBox.getValue(idx)));
    } else {
      at = null;
    }

    if (at != null && !at.getValues().isEmpty()) {
      int curIndex = 0, minIndex = -1, maxIndex = -1;
      rangeMinBox.clear();
      rangeMaxBox.clear();
      for (final ApprovalCategoryValue v : at.getValues()) {
        final String vStr = String.valueOf(v.getValue());
        String nStr = vStr + ": " + v.getName();
        if (v.getValue() > 0) {
          nStr = "+" + nStr;
        }

        rangeMinBox.addItem(nStr, vStr);
        rangeMaxBox.addItem(nStr, vStr);

        if (v.getValue() < 0) {
          minIndex = curIndex;
        }
        if (maxIndex < 0 && v.getValue() > 0) {
          maxIndex = curIndex;
        }

        curIndex++;
      }
      if (ApprovalCategory.READ.equals(at.getCategory().getId())) {
        // Special case; for READ the most logical range is just
        // +1 READ, so assume that as the default for both.
        minIndex = maxIndex;
      }
      rangeMinBox.setSelectedIndex(minIndex >= 0 ? minIndex : 0);
      rangeMaxBox.setSelectedIndex(maxIndex >= 0 ? maxIndex : curIndex - 1);
    } else {
      rangeMinBox.setEnabled(false);
      rangeMaxBox.setEnabled(false);
    }
  }

  private class RightsTable extends FancyFlexTable<ProjectRight> {
    RightsTable() {
      table.setText(0, 2, Util.C.columnApprovalCategory());
      table.setText(0, 3, Util.C.columnGroupName());
      table.setText(0, 4, Util.C.columnRightRange());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, S_ICON_HEADER);
      fmt.addStyleName(0, 2, S_DATA_HEADER);
      fmt.addStyleName(0, 3, S_DATA_HEADER);
      fmt.addStyleName(0, 4, S_DATA_HEADER);
    }

    void deleteChecked() {
      final HashSet<ProjectRight.Key> ids = new HashSet<ProjectRight.Key>();
      for (int row = 1; row < table.getRowCount(); row++) {
        final ProjectRight k = getRowItem(row);
        if (k != null && table.getWidget(row, 1) instanceof CheckBox
            && ((CheckBox) table.getWidget(row, 1)).getValue()) {
          ids.add(k.getKey());
        }
      }
      if (!ids.isEmpty()) {
        Util.PROJECT_SVC.deleteRight(ids, new GerritCallback<VoidResult>() {
          public void onSuccess(final VoidResult result) {
            for (int row = 1; row < table.getRowCount();) {
              final ProjectRight k = getRowItem(row);
              if (k != null && ids.contains(k.getKey())) {
                table.removeRow(row);
              } else {
                row++;
              }
            }
          }
        });
      }
    }

    void display(final Map<AccountGroup.Id, AccountGroup> groups,
        final List<ProjectRight> result) {
      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final ProjectRight k : result) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, groups, k);
      }
    }

    void populate(final int row,
        final Map<AccountGroup.Id, AccountGroup> groups, final ProjectRight k) {
      final GerritConfig config = Common.getGerritConfig();
      final ApprovalType ar = config.getApprovalType(k.getApprovalCategoryId());
      final AccountGroup group = groups.get(k.getAccountGroupId());

      if (ProjectRight.WILD_PROJECT.equals(k.getProjectId())
          && !ProjectRight.WILD_PROJECT.equals(projectId)) {
        table.setText(row, 1, "");
      } else {
        table.setWidget(row, 1, new CheckBox());
      }

      if (ar != null) {
        table.setText(row, 2, ar.getCategory().getName());
      } else {
        table.setText(row, 2, k.getApprovalCategoryId().get());
      }

      if (group != null) {
        table.setText(row, 3, group.getName());
      } else {
        table.setText(row, 3, Util.M.deletedGroup(k.getAccountGroupId().get()));
      }

      {
        final SafeHtmlBuilder m = new SafeHtmlBuilder();
        final ApprovalCategoryValue min, max;
        min = ar != null ? ar.getValue(k.getMinValue()) : null;
        max = ar != null ? ar.getValue(k.getMaxValue()) : null;

        formatValue(m, k.getMinValue(), min);
        if (k.getMinValue() != k.getMaxValue()) {
          m.br();
          formatValue(m, k.getMaxValue(), max);
        }
        SafeHtml.set(table, row, 4, m);
      }

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, S_ICON_CELL);
      fmt.addStyleName(row, 2, S_DATA_CELL);
      fmt.addStyleName(row, 3, S_DATA_CELL);
      fmt.addStyleName(row, 4, S_DATA_CELL);
      fmt.addStyleName(row, 4, "gerrit-ProjectAdmin-ApprovalCategoryRangeLine");

      setRowItem(row, k);
    }

    private void formatValue(final SafeHtmlBuilder m, final short v,
        final ApprovalCategoryValue e) {
      m.openSpan();
      m.setStyleName("gerrit-ProjectAdmin-ApprovalCategoryValue");
      if (v == 0) {
        m.append(' ');
      } else if (v > 0) {
        m.append('+');
      }
      m.append(v);
      m.closeSpan();
      if (e != null) {
        m.append(": ");
        m.append(e.getName());
      }
    }
  }
}
