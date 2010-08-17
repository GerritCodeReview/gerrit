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
import com.google.gerrit.client.rpc.ScreenLoadCallback;
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

import java.util.List;
import java.util.Map;

public class NewProjectAccessScreen extends ProjectScreen {
  private Panel parentPanel;
  private Hyperlink parentName;

  private RightsTable rights;
  private Button delRight;
  private Button addRight;
  private ListBox permissionListBox;
  private NpTextBox nameTxtBox;
  private SuggestBox nameTxt;
  private NpTextBox referenceTxt;
  private FlowPanel addPanel;
  private Label label;
  private NpTextBox labelTxtBox;

  public NewProjectAccessScreen(final Project.NameKey toShow) {
    super(toShow);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    initParent();
    initRights();
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.PROJECT_SVC.projectDetail(getProjectKey(),
        new ScreenLoadCallback<ProjectDetail>(this) {
          public void preDisplay(final ProjectDetail result) {
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

  private void initParent() {
    parentPanel = new VerticalPanel();
    parentPanel.add(new SmallHeading(Util.C.headingParentProjectName()));

    parentName = new Hyperlink("", "");
    parentPanel.add(parentName);
    add(parentPanel);
  }

  private void initRights() {
    addPanel = new FlowPanel();
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
      if (Gerrit.getConfig().getWildProject().equals(getProjectKey())
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

    label = new Label("Labels:");
    labelTxtBox = new NpTextBox();
    labelTxtBox.setVisibleLength(50);
    labelTxtBox.setText("CodeReview+2 DrNo");
    labelTxtBox.addStyleName(Gerrit.RESOURCES.css().inputFieldTypeHint());
    labelTxtBox.addFocusHandler(new FocusHandler() {
      @Override
      public void onFocus(FocusEvent event) {
        if ("CodeReview+2 DrNo".equals(labelTxtBox.getText())) {
          labelTxtBox.setText("");
          labelTxtBox.removeStyleName(Gerrit.RESOURCES.css()
              .inputFieldTypeHint());
        }
      }
    });
    labelTxtBox.addBlurHandler(new BlurHandler() {
      @Override
      public void onBlur(BlurEvent event) {
        if ("".equals(labelTxtBox.getText())) {
          labelTxtBox.setText("CodeReview+2 DrNo");
          labelTxtBox.addStyleName(Gerrit.RESOURCES.css().inputFieldTypeHint());
        }
      }
    });

    addGrid.setWidget(3, 0, label);
    addGrid.setWidget(3, 1, labelTxtBox);

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
        doDeleteRefRights();
      }
    });

    add(new SmallHeading(Util.C.headingAccessRights()));
    add(rights);
    add(delRight);
    add(addPanel);

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
        NEW_ACCESS_TAB));
    parentName.setText(parent.get());

    rights.display(result.groups, result.newRights);

    addPanel.setVisible(result.canModifyAccess);
    delRight.setVisible(rights.getCanDelete());
  }

  private void doDeleteRefRights() {
    // TODO
  }

  private void doAddNewRight() {
    // TODO
  }

  private void updateCategorySelection() {
    final String permValue =
        permissionListBox.getValue(permissionListBox.getSelectedIndex());
    boolean showlabel =
        permValue.equals(AccessCategory.SUBMIT.get())
            || permValue.equals(AccessCategory.CODE_REVIEW.get());

    label.setVisible(showlabel);
    labelTxtBox.setVisible(showlabel);
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

    void display(final Map<AccountGroup.Id, AccountGroup> groups,
        final List<InheritedNewRefRight> refRights) {
      canDelete = false;

      while (1 < table.getRowCount()) {
        table.removeRow(table.getRowCount() - 1);
      }

      for (final InheritedNewRefRight r : refRights) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, groups, r);
      }
    }

    void populate(final int row,
        final Map<AccountGroup.Id, AccountGroup> groups,
        final InheritedNewRefRight r) {
      final GerritConfig config = Gerrit.getConfig();
      final NewRefRight right = r.getRight();
      final AccessCategory ac =
          config.getAccessCategories().get(right.getAccessCategoryId());
      final AccountGroup group = groups.get(right.getAccountGroupId());

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
        table.setText(row, 3,
            Util.M.deletedGroup(right.getAccountGroupId().get()));
      }

      table.setText(row, 4, right.getRefPatternForDisplay());

      {
        final SafeHtmlBuilder m = new SafeHtmlBuilder();

        m.openSpan();
        m.addStyleName(Gerrit.RESOURCES.css()
            .projectAdminApprovalCategoryValue());

        final String labels = r.getRight().getLabels();
        if (labels != null && !labels.isEmpty()) {
          String[] labelsSplit = labels.split(" ");
          for (String label : labelsSplit) {
            m.append(label);
            m.br();
          }
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
