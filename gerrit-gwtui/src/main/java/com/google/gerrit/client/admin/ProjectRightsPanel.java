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

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountGroupSuggestOracle;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.GerritConfig;
import com.google.gerrit.common.data.InheritedRefRight;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ProjectRightsPanel extends Composite {
  private Project.NameKey projectName;

  private Panel parentPanel;
  private Hyperlink parentName;

  private RightsTable rights;
  private Button delRight;
  private Button addRight;
  private ListBox catBox;
  private ListBox rangeMinBox;
  private ListBox rangeMaxBox;
  private NpTextBox nameTxtBox;
  private SuggestBox nameTxt;
  private NpTextBox referenceTxt;

  private final FlowPanel addPanel = new FlowPanel();

  public ProjectRightsPanel(final Project.NameKey toShow) {
    projectName = toShow;

    final FlowPanel body = new FlowPanel();
    initParent(body);
    initRights(body);
    initWidget(body);
  }

  @Override
  protected void onLoad() {
    enableForm(false);
    super.onLoad();

    Util.PROJECT_SVC.projectDetail(projectName,
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
    referenceTxt.setEnabled(canAdd);
    catBox.setEnabled(canAdd);
    rangeMinBox.setEnabled(canAdd);
    rangeMaxBox.setEnabled(canAdd);
  }

  private void initParent(final Panel body) {
    parentPanel = new VerticalPanel();
    parentPanel.add(new SmallHeading(Util.C.headingParentProjectName()));

    parentName = new Hyperlink("", "");
    parentPanel.add(parentName);
    body.add(parentPanel);
  }

  private void initRights(final Panel body) {
    addPanel.setStyleName(Gerrit.RESOURCES.css().addSshKeyPanel());

    final Grid addGrid = new Grid(5, 2);

    catBox = new ListBox();
    rangeMinBox = new ListBox();
    rangeMaxBox = new ListBox();

    catBox.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(final ChangeEvent event) {
        updateCategorySelection();
      }
    });
    for (final ApprovalType at : Gerrit.getConfig().getApprovalTypes()
        .getApprovalTypes()) {
      final ApprovalCategory c = at.getCategory();
      catBox.addItem(c.getName(), c.getId().get());
    }
    for (final ApprovalType at : Gerrit.getConfig().getApprovalTypes()
        .getActionTypes()) {
      final ApprovalCategory c = at.getCategory();
      if (Gerrit.getConfig().getWildProject().equals(projectName)
          && ApprovalCategory.OWN.equals(c.getId())) {
        // Giving out control of the WILD_PROJECT to other groups beyond
        // Administrators is dangerous. Having control over WILD_PROJECT
        // is about the same as having Administrator access as users are
        // able to affect grants in all projects on the system.
        //
        continue;
      }
      catBox.addItem(c.getName(), c.getId().get());
    }

    addGrid.setText(0, 0, Util.C.columnApprovalCategory() + ":");
    addGrid.setWidget(0, 1, catBox);

    nameTxtBox = new NpTextBox();
    nameTxt = new SuggestBox(new AccountGroupSuggestOracle(), nameTxtBox);
    nameTxtBox.setVisibleLength(50);
    nameTxtBox.setText(Util.C.defaultAccountGroupName());
    nameTxtBox.addStyleName(Gerrit.RESOURCES.css().inputFieldTypeHint());
    nameTxtBox.addFocusHandler(new FocusHandler() {
      @Override
      public void onFocus(FocusEvent event) {
        if (Util.C.defaultAccountGroupName().equals(nameTxtBox.getText())) {
          nameTxtBox.setText("");
          nameTxtBox.removeStyleName(Gerrit.RESOURCES.css()
              .inputFieldTypeHint());
        }
      }
    });
    nameTxtBox.addBlurHandler(new BlurHandler() {
      @Override
      public void onBlur(BlurEvent event) {
        if ("".equals(nameTxtBox.getText())) {
          nameTxtBox.setText(Util.C.defaultAccountGroupName());
          nameTxtBox.addStyleName(Gerrit.RESOURCES.css().inputFieldTypeHint());
        }
      }
    });
    addGrid.setText(1, 0, Util.C.columnGroupName() + ":");
    addGrid.setWidget(1, 1, nameTxt);

    referenceTxt = new NpTextBox();
    referenceTxt.setVisibleLength(50);
    referenceTxt.setText("");
    referenceTxt.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getCharCode() == KeyCodes.KEY_ENTER) {
          doAddNewRight();
        }
      }
    });

    addGrid.setText(2, 0, Util.C.columnRefName() + ":");
    addGrid.setWidget(2, 1, referenceTxt);

    addGrid.setText(3, 0, Util.C.columnRightRange() + ":");
    addGrid.setWidget(3, 1, rangeMinBox);

    addGrid.setText(4, 0, "");
    addGrid.setWidget(4, 1, rangeMaxBox);

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
        final HashSet<RefRight.Key> refRightIds = rights.getRefRightIdsChecked();
        doDeleteRefRights(refRightIds);
      }
    });

    body.add(new SmallHeading(Util.C.headingAccessRights()));
    body.add(rights);
    body.add(delRight);
    body.add(addPanel);

    if (catBox.getItemCount() > 0) {
      catBox.setSelectedIndex(0);
      updateCategorySelection();
    }
  }

  void display(final ProjectDetail result) {
    final Project project = result.project;

    final Project.NameKey wildKey = Gerrit.getConfig().getWildProject();
    final boolean isWild = wildKey.equals(project.getNameKey());
    Project.NameKey parent = project.getParent();
    if (parent == null) {
      parent = wildKey;
    }

    parentPanel.setVisible(!isWild);
    parentName.setTargetHistoryToken(Dispatcher.toProjectAdmin(parent,
        ProjectAdminScreen.ACCESS_TAB));
    parentName.setText(parent.get());

    rights.display(result.groups, result.rights);

    addPanel.setVisible(result.canModifyAccess);
    delRight.setVisible(rights.getCanDelete());
  }

  private void doDeleteRefRights(final HashSet<RefRight.Key> refRightIds) {
    if (!refRightIds.isEmpty()) {
      Util.PROJECT_SVC.deleteRight(projectName, refRightIds,
          new GerritCallback<ProjectDetail>() {
        @Override
        public void onSuccess(final ProjectDetail result) {
          //The user could no longer modify access after deleting a ref right.
          display(result);
        }
      });
    }
  }

  private void doAddNewRight() {
    int idx = catBox.getSelectedIndex();
    final ApprovalType at;
    ApprovalCategoryValue min, max;
    if (idx < 0) {
      return;
    }
    at =
        Gerrit.getConfig().getApprovalTypes().getApprovalType(
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

    if (at.getCategory().isRange()) {
      idx = rangeMaxBox.getSelectedIndex();
      if (idx < 0) {
        return;
      }
      max = at.getValue(Short.parseShort(rangeMaxBox.getValue(idx)));
      if (max == null) {
        return;
      }
    } else {
      // If its not a range, the maximum box was disabled.  Use the min
      // value as the max, and select the min from the category values.
      //
      max = min;
      min = at.getMin();
      for (ApprovalCategoryValue v : at.getValues()) {
        if (0 <= v.getValue() && v.getValue() <= max.getValue()) {
          min = v;
          break;
        }
      }
    }

    final String groupName = nameTxt.getText();
    if ("".equals(groupName)
        || Util.C.defaultAccountGroupName().equals(groupName)) {
      return;
    }

    final String refPattern = referenceTxt.getText();

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
    Util.PROJECT_SVC.addRight(projectName, at.getCategory().getId(), groupName,
        refPattern, min.getValue(), max.getValue(),
        new GerritCallback<ProjectDetail>() {
          public void onSuccess(final ProjectDetail result) {
            addRight.setEnabled(true);
            nameTxt.setText("");
            referenceTxt.setText("");
            display(result);
          }

          @Override
          public void onFailure(final Throwable caught) {
            addRight.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  private void updateCategorySelection() {
    final int idx = catBox.getSelectedIndex();
    final ApprovalType at;
    if (idx >= 0) {
      at =
          Gerrit.getConfig().getApprovalTypes().getApprovalType(
              new ApprovalCategory.Id(catBox.getValue(idx)));
    } else {
      at = null;
    }

    if (at == null || at.getValues().isEmpty()) {
      rangeMinBox.setEnabled(false);
      rangeMaxBox.setEnabled(false);
      referenceTxt.setEnabled(false);
      addRight.setEnabled(false);
      return;
    }

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
    rangeMaxBox.setVisible(at.getCategory().isRange());

    addRight.setEnabled(true);
  }

  private class RightsTable extends FancyFlexTable<RefRight> {
    boolean canDelete;

    RightsTable() {
      table.setWidth("");
      table.setText(0, 2, Util.C.columnApprovalCategory());
      table.setText(0, 3, Util.C.columnGroupName());
      table.setText(0, 4, Util.C.columnRefName());
      table.setText(0, 5, Util.C.columnRightRange());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 4, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 5, Gerrit.RESOURCES.css().dataHeader());
    }

    HashSet<RefRight.Key> getRefRightIdsChecked() {
      final HashSet<RefRight.Key> refRightIds = new HashSet<RefRight.Key>();
      for (int row = 1; row < table.getRowCount(); row++) {
        RefRight r = getRowItem(row);
        if (r != null && table.getWidget(row, 1) instanceof CheckBox
            && ((CheckBox) table.getWidget(row, 1)).getValue()) {
          refRightIds.add(r.getKey());
        }
      }
      return refRightIds;
    }

    void display(final Map<AccountGroup.Id, AccountGroup> groups,
        final List<InheritedRefRight> refRights) {
      canDelete = false;

      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final InheritedRefRight r : refRights) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, groups, r);
      }
    }

    void populate(final int row, final Map<AccountGroup.Id, AccountGroup> groups,
        final InheritedRefRight r) {
      final GerritConfig config = Gerrit.getConfig();
      final RefRight right = r.getRight();
      final ApprovalType ar =
          config.getApprovalTypes().getApprovalType(
              right.getApprovalCategoryId());
      final AccountGroup group = groups.get(right.getAccountGroupId());

      if (r.isInherited() || !r.isOwner()) {
        table.setText(row, 1, "");
      } else {
        table.setWidget(row, 1, new CheckBox());
        canDelete = true;
      }

      if (ar != null) {
        table.setText(row, 2, ar.getCategory().getName());
      } else {
        table.setText(row, 2, right.getApprovalCategoryId().get());
      }

      if (group != null) {
        table.setText(row, 3, group.getName());
      } else {
        table.setText(row, 3, Util.M.deletedGroup(right.getAccountGroupId()
            .get()));
      }

      table.setText(row, 4, right.getRefPattern());

      {
        final SafeHtmlBuilder m = new SafeHtmlBuilder();
        final ApprovalCategoryValue min, max;
        min = ar != null ? ar.getValue(right.getMinValue()) : null;
        max = ar != null ? ar.getValue(right.getMaxValue()) : null;

        if (ar != null && ar.getCategory().isRange()) {
          formatValue(m, right.getMinValue(), min);
          m.br();
        }
        formatValue(m, right.getMaxValue(), max);
        SafeHtml.set(table, row, 5, m);
      }

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().iconCell());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 4, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 5, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 5, Gerrit.RESOURCES.css()
          .projectAdminApprovalCategoryRangeLine());

      setRowItem(row, right);
    }

    private void formatValue(final SafeHtmlBuilder m, final short v,
        final ApprovalCategoryValue e) {
      m.openSpan();
      m
          .setStyleName(Gerrit.RESOURCES.css()
              .projectAdminApprovalCategoryValue());
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

    private boolean getCanDelete() {
      return canDelete;
    }
  }
}
