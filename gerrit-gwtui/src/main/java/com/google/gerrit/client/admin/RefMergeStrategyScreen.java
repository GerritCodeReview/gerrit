// Copyright (C) 2010 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.client.admin;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.data.InheritedRefMergeStrategy;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefMergeStrategy;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.HashSet;
import java.util.List;

/**
 * Composite containing the user interface elements to edit the
 * Merge Strategy applied to ref patterns of a project, or to
 * see the ones inherited from the wild project.
 */
public class RefMergeStrategyScreen extends ProjectScreen {
  private Panel parentPanel;
  private Hyperlink parentName;

  private RefMergeStrategiesTable refMergeStrategies;
  private Button delRefMergeStrategy;
  private Button addRefMergeStrategy;
  private ListBox submitTypeBox;
  private NpTextBox refPatternTxt;

  public RefMergeStrategyScreen(final Project.NameKey toShow) {
    super(toShow);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    initParent();
    initStrategies();
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

  private void initParent() {
    parentPanel = new VerticalPanel();
    parentPanel.add(new SmallHeading(Util.C.headingRmsParentProjectName()));

    parentName = new Hyperlink("", "");
    parentPanel.add(parentName);
    add(parentPanel);
  }

  private void enableForm(final boolean on) {
    delRefMergeStrategy.setEnabled(on);

    final boolean canAdd = on && submitTypeBox.getItemCount() > 0;
    addRefMergeStrategy.setEnabled(canAdd);
    refPatternTxt.setEnabled(canAdd);
  }

  private void initStrategies() {
    final FlowPanel addPanel = new FlowPanel();
    addPanel.setStyleName(Gerrit.RESOURCES.css().addSshKeyPanel());

    final Grid addGrid = new Grid(3, 2);

    addGrid.setText(0, 0, Util.C.columnRefName() + ":");

    refPatternTxt = new NpTextBox();
    refPatternTxt.setVisibleLength(50);
    refPatternTxt.setText("");
    refPatternTxt.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getCharCode() == KeyCodes.KEY_ENTER) {
          doAddNewRight();
        }
      }
    });
    refPatternTxt.addKeyUpHandler(new KeyUpHandler() {
      @Override
      public void onKeyUp(KeyUpEvent event) {
        addRefMergeStrategy.setEnabled(
            !refPatternTxt.getText().isEmpty());
      }
    });

    addGrid.setWidget(0, 1, refPatternTxt);

    submitTypeBox = new ListBox();
    for (final RefMergeStrategy.SubmitType type : RefMergeStrategy.SubmitType.values()) {
      submitTypeBox.addItem(Util.toLongString(type), type.name());
    }
    submitTypeBox.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(final ChangeEvent event) {
          addRefMergeStrategy.setEnabled(
              !refPatternTxt.getText().isEmpty());
      }
    });

    addGrid.setText(1, 0, Util.C.columnSubmitAction() + ":");
    addGrid.setWidget(1, 1, submitTypeBox);

    addRefMergeStrategy = new Button(Util.C.buttonAddRefMergeStrategy());
    addRefMergeStrategy.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doAddNewRight();
      }
    });
    addPanel.add(addGrid);
    addPanel.add(addRefMergeStrategy);

    refMergeStrategies = new RefMergeStrategiesTable();

    delRefMergeStrategy = new Button(Util.C.buttonDeleteGroupMembers());
    delRefMergeStrategy.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        refMergeStrategies.deleteChecked();
      }
    });

    add(refMergeStrategies);
    add(delRefMergeStrategy);
    add(addPanel);

    if (submitTypeBox.getItemCount() > 0) {
      submitTypeBox.setSelectedIndex(0);
    }
  }

  private void doAddNewRight() {
    final RefMergeStrategy.SubmitType submitType;

    if (submitTypeBox.getSelectedIndex() >= 0) {
      submitType = RefMergeStrategy.SubmitType.valueOf(submitTypeBox
          .getValue(submitTypeBox.getSelectedIndex()));
    } else {
      return;
    }

    final String refPattern = refPatternTxt.getText().trim();

    addRefMergeStrategy.setEnabled(false);
    Util.PROJECT_SVC.addRefMergeStrategy(getProjectKey(),
        refPattern, submitType,
        new GerritCallback<ProjectDetail>() {
          public void onSuccess(final ProjectDetail result) {
            addRefMergeStrategy.setEnabled(true);
            refPatternTxt.setText("");
            display(result);
          }

          @Override
          public void onFailure(final Throwable caught) {
            addRefMergeStrategy.setEnabled(true);
            super.onFailure(caught);
          }
        });
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
        ProjectScreen.REF_MERGE_STRATEGY_TAB));
    parentName.setText(parent.get());

    refMergeStrategies.display(result.refMergeStrategies);
    addRefMergeStrategy.setEnabled(false);
  }

  /**
   * Table to show the merge strategies per ref pattern.
   */
  private class RefMergeStrategiesTable extends FancyFlexTable<InheritedRefMergeStrategy> {
    RefMergeStrategiesTable() {
      table.setWidth("");
      table.setText(0, 2, Util.C.columnRefName());
      table.setText(0, 3, Util.C.columnSubmitAction());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
    }

    /**
     * It deletes the checked ref merge strategies (rows).
     */
    void deleteChecked() {
      final HashSet<RefMergeStrategy.Key> refStrategyIds = new HashSet<RefMergeStrategy.Key>();
      for (int row = 1; row < table.getRowCount(); row++) {
        RefMergeStrategy r = getRowItem(row).getRefMergeStrategy();
        if (r != null && table.getWidget(row, 1) instanceof CheckBox
            && ((CheckBox) table.getWidget(row, 1)).getValue()) {
          refStrategyIds.add(r.getKey());
        }
      }

      GerritCallback<VoidResult> updateTable =
          new GerritCallback<VoidResult>() {
            @Override
            public void onSuccess(final VoidResult result) {
              for (int row = 1; row < table.getRowCount();) {
                RefMergeStrategy r = getRowItem(row).getRefMergeStrategy();
                if (r != null && refStrategyIds.contains(r.getKey())) {
                  table.removeRow(row);
                } else {
                  row++;
                }
              }
            }
          };
      if (!refStrategyIds.isEmpty()) {
        Util.PROJECT_SVC.deleteRefMergeStrategy(getProjectKey(), refStrategyIds, updateTable);
      }
    }

    /**
     * Display the ref merge strategies in the table.
     * @param refMergeStrategies The list of ref merge strategies to display.
     */
    void display(final List<InheritedRefMergeStrategy> refMergeStrategies) {
      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final InheritedRefMergeStrategy r : refMergeStrategies) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, r);
      }
    }


    /**
     * It populates a row of the table.
     * @param row The row index.
     * @param rms The ref merge strategy to populate.
     */
    void populate(final int row, final InheritedRefMergeStrategy rms) {
      if (rms.isInherited()) {
        //It is an inherited merge strategy: it cannot be checked to be deleted.
        table.setText(row, 1, "");
      } else {
        table.setWidget(row, 1, new CheckBox());
      }

      table.setText(row, 2, rms.getRefMergeStrategy().getRefPattern());

      table.setText(row, 3, Util.toLongString(rms.getRefMergeStrategy().getSubmitType()));

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().iconCell());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());

      setRowItem(row, rms);
    }
  }
}
