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
import com.google.gerrit.common.data.GerritConfig;
import com.google.gerrit.common.data.InheritedNewRefRight;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.reviewdb.AccessCategory;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.NewRefRight;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.SubmitLabel;
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
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class NewProjectRightsPanel extends Composite {
  private Project.NameKey projectName;

  private Panel parentPanel;
  private Hyperlink parentName;

  private RightsTable rights;
  private Button delRight;
  private Button addRight;
  private ListBox permissionListBox;
  private NpTextBox nameTxtBox;
  private SuggestBox nameTxt;
  private NpTextBox referenceTxt;
  private Label submitLabel;
  private NpTextBox submitLabelTxtBox;

  private final FlowPanel addPanel = new FlowPanel();

  public NewProjectRightsPanel(final Project.NameKey toShow) {
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

    final boolean canAdd = on && permissionListBox.getItemCount() > 0;
    addRight.setEnabled(canAdd);
    nameTxtBox.setEnabled(canAdd);
    referenceTxt.setEnabled(canAdd);
    permissionListBox.setEnabled(canAdd);
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

    permissionListBox = new ListBox();

    permissionListBox.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(final ChangeEvent event) {
        updateCategorySelection();
      }
    });
    for (final AccessCategory ac : Gerrit.getConfig().getAccessCategories()
        .values()) {
      if (Gerrit.getConfig().getWildProject().equals(projectName)
          && !AccessCategory.OWN.equals(ac.getId())) {
        permissionListBox.addItem(ac.getDescription(), ac.getId().get());
      }
    }

    addGrid.setText(0, 0, Util.C.columnPermission() + ":");
    addGrid.setWidget(0, 1, permissionListBox);

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

    submitLabel = new Label("Labels:");
    submitLabelTxtBox = new NpTextBox();
    submitLabelTxtBox.setVisibleLength(50);
    submitLabelTxtBox.setText("CodeReview+2 Verified DrNo");
    submitLabelTxtBox.addStyleName(Gerrit.RESOURCES.css().inputFieldTypeHint());
    submitLabelTxtBox.addFocusHandler(new FocusHandler() {
      @Override
      public void onFocus(FocusEvent event) {
        if ("CodeReview+2 Verified DrNo".equals(submitLabelTxtBox.getText())) {
          submitLabelTxtBox.setText("");
          submitLabelTxtBox.removeStyleName(Gerrit.RESOURCES.css()
              .inputFieldTypeHint());
        }
      }
    });
    submitLabelTxtBox.addBlurHandler(new BlurHandler() {
      @Override
      public void onBlur(BlurEvent event) {
        if ("".equals(submitLabelTxtBox.getText())) {
          submitLabelTxtBox.setText("CodeReview+2 Verified DrNo");
          submitLabelTxtBox.addStyleName(Gerrit.RESOURCES.css()
              .inputFieldTypeHint());
        }
      }
    });
    addGrid.setWidget(3, 0, submitLabel);
    addGrid.setWidget(3, 1, submitLabelTxtBox);

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
        final HashSet<NewRefRight.Id> refRightIds =
            rights.getRefRightIdsChecked();
        doDeleteRefRights(refRightIds);
      }
    });

    body.add(new SmallHeading(Util.C.headingAccessRights()));
    body.add(rights);
    body.add(delRight);
    body.add(addPanel);

    if (permissionListBox.getItemCount() > 0) {
      permissionListBox.setSelectedIndex(0);
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
        ProjectAdminScreen.NEW_ACCESS_TAB));
    parentName.setText(parent.get());

    rights.display(result.groups, result.newRights, result.submitLabels);

    addPanel.setVisible(result.canModifyAccess);
    delRight.setVisible(rights.getCanDelete());
  }

  private void doDeleteRefRights(final HashSet<NewRefRight.Id> refRightIds) {
    // TODO
  }

  private void doAddNewRight() {
    // TODO
  }

  private void updateCategorySelection() {
    boolean showSubmitLabel =
        permissionListBox.getValue(permissionListBox.getSelectedIndex()).equals(
            AccessCategory.SUBMIT.get());

    submitLabel.setVisible(showSubmitLabel);
    submitLabelTxtBox.setVisible(showSubmitLabel);
  }

  private class RightsTable extends FancyFlexTable<NewRefRight> {
    boolean canDelete;

    RightsTable() {
      table.setWidth("");
      table.setText(0, 2, Util.C.columnPermission());
      table.setText(0, 3, Util.C.columnGroupName());
      table.setText(0, 4, Util.C.columnRefName());
      table.setText(0, 5, Util.C.columnLabels());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 4, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 5, Gerrit.RESOURCES.css().dataHeader());
    }

    HashSet<NewRefRight.Id> getRefRightIdsChecked() {
      final HashSet<NewRefRight.Id> refRightIds = new HashSet<NewRefRight.Id>();
      for (int row = 1; row < table.getRowCount(); row++) {
        NewRefRight r = getRowItem(row);
        if (r != null && table.getWidget(row, 1) instanceof CheckBox
            && ((CheckBox) table.getWidget(row, 1)).getValue()) {
          refRightIds.add(r.getId());
        }
      }
      return refRightIds;
    }

    void display(final Map<AccountGroup.Id, AccountGroup> groups,
        final List<InheritedNewRefRight> refRights,
        final Map<NewRefRight.Id, List<SubmitLabel>> submitLabels) {
      canDelete = false;

      while (1 < table.getRowCount()) {
        table.removeRow(table.getRowCount() - 1);
      }

      for (final InheritedNewRefRight r : refRights) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, groups, r, submitLabels.get(r.getRight().getId()));
      }
    }

    void populate(final int row,
        final Map<AccountGroup.Id, AccountGroup> groups,
        final InheritedNewRefRight r, final List<SubmitLabel> sl) {
      final GerritConfig config = Gerrit.getConfig();
      final NewRefRight right = r.getRight();
      final AccessCategory ac =
          config.getAccessCategories().get(right.getAccessCategoryId());
      final AccountGroup group = groups.get(right.getAccountGroupId());
      final Collection<SubmitLabel> submitLabels =
          ((sl != null) ? sl : new ArrayList<SubmitLabel>());

      if (r.isInherited() || !r.isOwner()) {
        table.setText(row, 1, "");
      } else {
        table.setWidget(row, 1, new CheckBox());
        canDelete = true;
      }

      if (ac != null) {
        table.setText(row, 2, r.getRight().getAccessCategoryId().get());
      }

      if (group != null) {
        table.setText(row, 3, group.getName());
      } else {
        table.setText(row, 3, Util.M.deletedGroup(right.getAccountGroupId()
            .get()));
      }

      table.setText(row, 4, right.getRefPatternForDisplay());

      {
        final SafeHtmlBuilder m = new SafeHtmlBuilder();

        m.openSpan();
        m.addStyleName(Gerrit.RESOURCES.css()
            .projectAdminApprovalCategoryValue());

        for (SubmitLabel label : submitLabels) {
          m.append(label.getKey().getRequiredLabel().get());
          m.br();
        }
        m.closeSpan();

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

    private boolean getCanDelete() {
      return canDelete;
    }
  }
}
