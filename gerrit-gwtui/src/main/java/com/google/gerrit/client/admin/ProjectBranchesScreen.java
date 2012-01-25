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
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.BranchLink;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.common.data.DeleteBranchesResult;
import com.google.gerrit.common.data.GitwebLink;
import com.google.gerrit.common.data.ListBranchesResult;
import com.google.gerrit.common.errors.InvalidNameException;
import com.google.gerrit.common.errors.InvalidRevisionException;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Branch.NameKey;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtjsonrpc.client.RemoteJsonException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProjectBranchesScreen extends ProjectScreen {
  private BranchesTable branches;
  private CheckBox abandonOpenChanges;
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
    Util.PROJECT_SVC.listBranches(getProjectKey(),
        new ScreenLoadCallback<ListBranchesResult>(this) {
          @Override
          public void preDisplay(final ListBranchesResult result) {
            if (result.getNoRepository()) {
              branches.setVisible(false);
              addPanel.setVisible(false);
              abandonOpenChanges.setVisible(false);
              delBranch.setVisible(false);

              Label no = new Label(Util.C.errorNoGitRepository());
              no.setStyleName(Gerrit.RESOURCES.css().smallHeading());
              add(no);

            } else {
              enableForm(true);
              display(result.getBranches());
              addPanel.setVisible(result.getCanAdd());
            }
          }
        });
  }

  private void display(final List<Branch> listBranches) {
    branches.display(listBranches);
    abandonOpenChanges.setVisible(branches.hasBranchCanDelete());
    delBranch.setVisible(branches.hasBranchCanDelete());
  }

  private void enableForm(final boolean on) {
    abandonOpenChanges.setEnabled(on);
    delBranch.setEnabled(on);
    addBranch.setEnabled(on);
    nameTxtBox.setEnabled(on);
    irevTxtBox.setEnabled(on);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();

    addPanel = new FlowPanel();
    addPanel.setStyleName(Gerrit.RESOURCES.css().addSshKeyPanel());

    final Grid addGrid = new Grid(2, 2);

    nameTxtBox = new HintTextBox();
    nameTxtBox.setVisibleLength(50);
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
    irevTxtBox.setVisibleLength(50);
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

    branches = new BranchesTable();

    abandonOpenChanges = new CheckBox(Util.C.checkboxAbandonOpenChanges());

    delBranch = new Button(Util.C.buttonDeleteBranch());
    delBranch.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        branches.deleteChecked();
      }
    });

    add(branches);
    final VerticalPanel v = new VerticalPanel();
    v.add(abandonOpenChanges);
    v.add(delBranch);
    add(v);
    add(addPanel);
  }

  private void doAddNewBranch() {
    String branchName = nameTxtBox.getText();
    if ("".equals(branchName)) {
      nameTxtBox.setFocus(true);
      return;
    }

    String rev = irevTxtBox.getText();
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

    if (!branchName.startsWith(Branch.R_REFS)) {
      branchName = Branch.R_HEADS + branchName;
    }

    addBranch.setEnabled(false);
    Util.PROJECT_SVC.addBranch(getProjectKey(), branchName, rev,
        new GerritCallback<ListBranchesResult>() {
          public void onSuccess(final ListBranchesResult result) {
            addBranch.setEnabled(true);
            nameTxtBox.setText("");
            irevTxtBox.setText("");
            display(result.getBranches());
          }

          @Override
          public void onFailure(final Throwable caught) {
            if (caught instanceof InvalidNameException
                || caught instanceof RemoteJsonException
                && caught.getMessage().equals(InvalidNameException.MESSAGE)) {
              nameTxtBox.selectAll();
              nameTxtBox.setFocus(true);

            } else if (caught instanceof InvalidRevisionException
                || caught instanceof RemoteJsonException
                && caught.getMessage().equals(InvalidRevisionException.MESSAGE)) {
              irevTxtBox.selectAll();
              irevTxtBox.setFocus(true);
            }

            addBranch.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  private class BranchesTable extends FancyFlexTable<Branch> {
    boolean canDelete;

    BranchesTable() {
      table.setWidth("");
      table.setText(0, 2, Util.C.columnBranchName());
      table.setText(0, 3, Util.C.columnBranchRevision());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
      if (Gerrit.getConfig().getGitwebLink() != null) {
        fmt.addStyleName(0, 4, Gerrit.RESOURCES.css().dataHeader());
      }
    }

    void deleteChecked() {
      final SafeHtmlBuilder b = new SafeHtmlBuilder();
      b.openElement("b");
      if (abandonOpenChanges.getValue()) {
        b.append(Gerrit.C.branchDeletionAndAbandonOpenChangesConfirmationMessage());
      } else {
        b.append(Gerrit.C.branchDeletionConfirmationMessage());
      }
      b.closeElement("b");

      b.openElement("p");
      final HashSet<Branch.NameKey> ids = new HashSet<Branch.NameKey>();
      for (int row = 1; row < table.getRowCount(); row++) {
        final Branch k = getRowItem(row);
        if (k != null && table.getWidget(row, 1) instanceof CheckBox
            && ((CheckBox) table.getWidget(row, 1)).getValue()) {
          if (!ids.isEmpty()) {
            b.append(",").br();
          }
          b.append(k.getName());
          ids.add(k.getNameKey());
        }
      }
      b.closeElement("p");
      if (ids.isEmpty()) {
        return;
      }

      ConfirmationDialog confirmationDialog =
          new ConfirmationDialog(Gerrit.C.branchDeletionDialogTitle(),
              b.toSafeHtml(), new ConfirmationCallback() {
        @Override
        public void onOk() {
          deleteBranches(ids, abandonOpenChanges.getValue());
        }
      });
      confirmationDialog.center();
    }

    private void deleteBranches(final Set<Branch.NameKey> branchIds,
        final boolean abandonOpenChanges) {
      Util.PROJECT_SVC.deleteBranch(getProjectKey(), branchIds,
          abandonOpenChanges, new GerritCallback<DeleteBranchesResult>() {
            public void onSuccess(final DeleteBranchesResult result) {
              final Set<NameKey> deletedBranches = result.getDeletedBranches();
              if (!deletedBranches.isEmpty()) {
                for (int row = 1; row < table.getRowCount();) {
                  final Branch k = getRowItem(row);
                  if (k != null && deletedBranches.contains(k.getNameKey())) {
                    table.removeRow(row);
                  } else {
                    row++;
                  }
                }
              }

              if (result.hasErrors()) {
                final SafeHtmlBuilder b = new SafeHtmlBuilder();
                final Set<Branch.NameKey> branchesWithOpenChanges =
                    new HashSet<Branch.NameKey>();
                final Set<Branch.NameKey> branchesWithOpenChangesThatCannotBeAbandoned =
                    new HashSet<Branch.NameKey>();
                for (final DeleteBranchesResult.Error e : result.getErrors()) {
                  switch (e.getType()) {
                    case OPEN_CHANGES:
                      branchesWithOpenChanges.add(e.getBranchKey());
                      break;

                    case ABANDON_FAILED:
                      branchesWithOpenChangesThatCannotBeAbandoned.add(e
                          .getBranchKey());
                      break;

                    default:
                      b.append(Gerrit.M.branchDeletionFailed(e.getBranchKey()
                          .get(), e.toString()));
                      b.br();
                  }
                }
                if (!branchesWithOpenChanges.isEmpty()) {
                  b.append(Util.C.branchDeletionOpenChanges());
                  b.br();
                  appendBranchOpenChangesLinks(b, branchesWithOpenChanges);
                }
                if (!branchesWithOpenChangesThatCannotBeAbandoned.isEmpty()) {
                  b.append(Util.C.branchDeletionAbandonFailed());
                  b.br();
                  appendBranchOpenChangesLinks(b,
                      branchesWithOpenChangesThatCannotBeAbandoned);
                }
                new ErrorDialog(b.toSafeHtml()).center();
              }
            }

            private void appendBranchOpenChangesLinks(final SafeHtmlBuilder b,
                final Set<Branch.NameKey> branches) {
              b.openElement("p");
              for (final Branch.NameKey branch : branches) {
                b.openAnchor();
                final BranchLink link =
                    new BranchLink(branch.getParentKey(), Change.Status.NEW,
                        branch.get(), null);
                b.setAttribute("href", "/#" + link.getTargetHistoryToken());
                b.setAttribute("target", "_blank");
                b.append(branch.get());
                b.closeAnchor();
                b.br();
              }
              b.closeElement("p");
            }
          });
    }

    void display(final List<Branch> result) {
      canDelete = false;

      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final Branch k : result) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, k);
      }
    }

    void populate(final int row, final Branch k) {
      final GitwebLink c = Gerrit.getConfig().getGitwebLink();

      if (k.getCanDelete()) {
        table.setWidget(row, 1, new CheckBox());
        canDelete = true;
      } else {
        table.setText(row, 1, "");
      }

      table.setText(row, 2, k.getShortName());

      if (k.getRevision() != null) {
        table.setText(row, 3, k.getRevision().get());
      } else {
        table.setText(row, 3, "");
      }

      if (c != null) {
        table.setWidget(row, 4, new Anchor(c.getLinkName(), false, c.toBranch(k
            .getNameKey())));
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
  }
}
