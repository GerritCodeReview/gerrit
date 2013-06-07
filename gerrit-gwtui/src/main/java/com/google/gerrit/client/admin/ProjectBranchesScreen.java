// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.client.ConfirmationCallback;
import com.google.gerrit.client.ConfirmationDialog;
import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.GitwebLink;
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.access.AccessMap;
import com.google.gerrit.client.access.ProjectAccessInfo;
import com.google.gerrit.client.projects.BranchInfo;
import com.google.gerrit.client.projects.ProjectApi;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProjectBranchesScreen extends ProjectScreen {
  private BranchesTable branchTable;
  private Button delBranch;
  private Button addBranch;
  private HintTextBox nameTxtBox;
  private HintTextBox irevTxtBox;
  private FlowPanel addPanel;

  public ProjectBranchesScreen(final Project.NameKey toShow) {
    super(toShow);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    addPanel.setVisible(false);
    AccessMap.get(getProjectKey(),
        new GerritCallback<ProjectAccessInfo>() {
          @Override
          public void onSuccess(ProjectAccessInfo result) {
            addPanel.setVisible(result.canAddRefs());
          }
        });
    refreshBranches();
    savedPanel = BRANCH;
  }

  private void refreshBranches() {
    ProjectApi.getBranches(getProjectKey(),
        new ScreenLoadCallback<JsArray<BranchInfo>>(this) {
          @Override
          public void preDisplay(final JsArray<BranchInfo> result) {
            Set<String> checkedRefs = branchTable.getCheckedRefs();
            display(Natives.asList(result));
            branchTable.setChecked(checkedRefs);
            updateForm();
          }
        });
  }

  private void display(final List<BranchInfo> branches) {
    branchTable.display(branches);
    delBranch.setVisible(branchTable.hasBranchCanDelete());
  }

  private void updateForm() {
    branchTable.updateDeleteButton();
    addBranch.setEnabled(true);
    nameTxtBox.setEnabled(true);
    irevTxtBox.setEnabled(true);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();

    addPanel = new FlowPanel();

    final Grid addGrid = new Grid(2, 2);
    addGrid.setStyleName(Gerrit.RESOURCES.css().addBranch());
    final int texBoxLength = 50;

    nameTxtBox = new HintTextBox();
    nameTxtBox.setVisibleLength(texBoxLength);
    nameTxtBox.setHintText(Util.C.defaultBranchName());
    nameTxtBox.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
          doAddNewBranch();
        }
      }
    });
    addGrid.setText(0, 0, Util.C.columnBranchName() + ":");
    addGrid.setWidget(0, 1, nameTxtBox);

    irevTxtBox = new HintTextBox();
    irevTxtBox.setVisibleLength(texBoxLength);
    irevTxtBox.setHintText(Util.C.defaultRevisionSpec());
    irevTxtBox.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
          doAddNewBranch();
        }
      }
    });
    addGrid.setText(1, 0, Util.C.initialRevision() + ":");
    addGrid.setWidget(1, 1, irevTxtBox);

    addBranch = new Button(Util.C.buttonAddBranch());
    addBranch.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doAddNewBranch();
      }
    });
    addPanel.add(addGrid);
    addPanel.add(addBranch);

    branchTable = new BranchesTable();

    delBranch = new Button(Util.C.buttonDeleteBranch());
    delBranch.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        branchTable.deleteChecked();
      }
    });

    add(branchTable);
    add(delBranch);
    add(addPanel);
  }

  private void doAddNewBranch() {
    final String branchName = nameTxtBox.getText().trim();
    if ("".equals(branchName)) {
      nameTxtBox.setFocus(true);
      return;
    }

    final String rev = irevTxtBox.getText().trim();
    if ("".equals(rev)) {
      irevTxtBox.setText("HEAD");
      Scheduler.get().scheduleDeferred(new ScheduledCommand() {
        @Override
        public void execute() {
          irevTxtBox.selectAll();
          irevTxtBox.setFocus(true);
        }
      });
      return;
    }

    addBranch.setEnabled(false);
    ProjectApi.createBranch(getProjectKey(), branchName, rev,
        new GerritCallback<BranchInfo>() {
          @Override
          public void onSuccess(BranchInfo branch) {
            addBranch.setEnabled(true);
            nameTxtBox.setText("");
            irevTxtBox.setText("");
            branchTable.insert(branch);
          }

      @Override
      public void onFailure(Throwable caught) {
        addBranch.setEnabled(true);
        selectAllAndFocus(nameTxtBox);
        new ErrorDialog(caught.getMessage()).center();
      }
    });
  }

  private static void selectAllAndFocus(final TextBox textBox) {
    textBox.selectAll();
    textBox.setFocus(true);
  }

  private class BranchesTable extends FancyFlexTable<BranchInfo> {
    private ValueChangeHandler<Boolean> updateDeleteHandler;
    boolean canDelete;

    BranchesTable() {
      table.setWidth("");
      table.setText(0, 2, Util.C.columnBranchName());
      table.setText(0, 3, Util.C.columnBranchRevision());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
      if (Gerrit.getGitwebLink() != null) {
        fmt.addStyleName(0, 4, Gerrit.RESOURCES.css().dataHeader());
      }

      updateDeleteHandler = new ValueChangeHandler<Boolean>() {
        @Override
        public void onValueChange(ValueChangeEvent<Boolean> event) {
          updateDeleteButton();
        }
      };
    }

    Set<String> getCheckedRefs() {
      Set<String> refs = new HashSet<String>();
      for (int row = 1; row < table.getRowCount(); row++) {
        final BranchInfo k = getRowItem(row);
        if (k != null && table.getWidget(row, 1) instanceof CheckBox
            && ((CheckBox) table.getWidget(row, 1)).getValue()) {
          refs.add(k.ref());
        }
      }
      return refs;
    }

    void setChecked(Set<String> refs) {
      for (int row = 1; row < table.getRowCount(); row++) {
        final BranchInfo k = getRowItem(row);
        if (k != null && refs.contains(k.ref()) &&
            table.getWidget(row, 1) instanceof CheckBox) {
          ((CheckBox) table.getWidget(row, 1)).setValue(true);
        }
      }
    }

    void deleteChecked() {
      final Set<String> refs = getCheckedRefs();

      SafeHtmlBuilder b = new SafeHtmlBuilder();
      b.openElement("b");
      b.append(Gerrit.C.branchDeletionConfirmationMessage());
      b.closeElement("b");

      b.openElement("p");
      boolean first = true;
      for (String ref : refs) {
        if (!first) {
          b.append(",").br();
        }
        b.append(ref);
        first = false;
      }
      b.closeElement("p");

      if (refs.isEmpty()) {
        updateDeleteButton();
        return;
      }

      delBranch.setEnabled(false);
      ConfirmationDialog confirmationDialog =
          new ConfirmationDialog(Gerrit.C.branchDeletionDialogTitle(),
              b.toSafeHtml(), new ConfirmationCallback() {
        @Override
        public void onOk() {
          deleteBranches(refs);
        }

        @Override
        public void onCancel() {
          branchTable.updateDeleteButton();
        }
      });
      confirmationDialog.center();
    }

    private void deleteBranches(final Set<String> branches) {
      ProjectApi.deleteBranches(getProjectKey(), branches,
          new GerritCallback<VoidResult>() {
            public void onSuccess(VoidResult result) {
              for (int row = 1; row < table.getRowCount();) {
                BranchInfo k = getRowItem(row);
                if (k != null && branches.contains(k.ref())) {
                  table.removeRow(row);
                } else {
                  row++;
                }
              }
              updateDeleteButton();
            }

            @Override
            public void onFailure(Throwable caught) {
              refreshBranches();
              super.onFailure(caught);
            }
          });
    }

    void display(List<BranchInfo> branches) {
      canDelete = false;

      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final BranchInfo k : branches) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, k);
      }
    }

    void insert(BranchInfo info) {
      Comparator<BranchInfo> c = new Comparator<BranchInfo>() {
        @Override
        public int compare(BranchInfo a, BranchInfo b) {
          return a.ref().compareTo(b.ref());
        }
      };
      int insertPos = getInsertRow(c, info);
      if (insertPos >= 0) {
        table.insertRow(insertPos);
        applyDataRowStyle(insertPos);
        populate(insertPos, info);
      }
    }

    void populate(int row, BranchInfo k) {
      final GitwebLink c = Gerrit.getGitwebLink();

      if (k.canDelete()) {
        CheckBox sel = new CheckBox();
        sel.addValueChangeHandler(updateDeleteHandler);
        table.setWidget(row, 1, sel);
        canDelete = true;
      } else {
        table.setText(row, 1, "");
      }

      table.setText(row, 2, k.getShortName());

      if (k.revision() != null) {
        table.setText(row, 3, k.revision());
      } else {
        table.setText(row, 3, "");
      }

      if (c != null) {
        table.setWidget(row, 4, new Anchor(c.getLinkName(), false,
            c.toBranch(new Branch.NameKey(getProjectKey(), k.ref()))));
      }

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      String iconCellStyle = Gerrit.RESOURCES.css().iconCell();
      String dataCellStyle = Gerrit.RESOURCES.css().dataCell();
      if ("refs/meta/config".equals(k.getShortName())
          || "HEAD".equals(k.getShortName())) {
        iconCellStyle = Gerrit.RESOURCES.css().specialBranchIconCell();
        dataCellStyle = Gerrit.RESOURCES.css().specialBranchDataCell();
        fmt.setStyleName(row, 0, iconCellStyle);
      }
      fmt.addStyleName(row, 1, iconCellStyle);
      fmt.addStyleName(row, 2, dataCellStyle);
      fmt.addStyleName(row, 3, dataCellStyle);
      if (c != null) {
        fmt.addStyleName(row, 4, dataCellStyle);
      }

      setRowItem(row, k);
    }

    boolean hasBranchCanDelete() {
      return canDelete;
    }

    void updateDeleteButton() {
      boolean on = false;
      for (int row = 1; row < table.getRowCount(); row++) {
        Widget w = table.getWidget(row, 1);
        if (w != null && w instanceof CheckBox) {
          CheckBox sel = (CheckBox) w;
          if (sel.getValue()) {
            on = true;
            break;
          }
        }
      }
      delBranch.setEnabled(on);
    }
  }
}
