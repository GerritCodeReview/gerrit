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
import com.google.gerrit.client.WebLinkInfo;
import com.google.gerrit.client.access.AccessMap;
import com.google.gerrit.client.access.ProjectAccessInfo;
import com.google.gerrit.client.actions.ActionButton;
import com.google.gerrit.client.actions.ActionInfo;
import com.google.gerrit.client.projects.BranchInfo;
import com.google.gerrit.client.projects.ProjectApi;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
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
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProjectBranchesScreen extends ProjectScreen {
  private Hyperlink prev;
  private Hyperlink next;
  private BranchesTable branchTable;
  private Button delBranch;
  private Button addBranch;
  private HintTextBox nameTxtBox;
  private HintTextBox irevTxtBox;
  private FlowPanel addPanel;
  private int pageSize;
  private int start;
  private Query query;

  public ProjectBranchesScreen(final Project.NameKey toShow) {
    super(toShow);
    configurePageSize();
  }

  private void configurePageSize() {
    if (Gerrit.isSignedIn()) {
      AccountGeneralPreferences p =
          Gerrit.getUserAccount().getGeneralPreferences();
      short m = p.getMaximumPageSize();
      pageSize = 0 < m ? m : AccountGeneralPreferences.DEFAULT_PAGESIZE;
    } else {
      pageSize = AccountGeneralPreferences.DEFAULT_PAGESIZE;
    }
  }

  private void parseToken() {
    String token = getToken();

    for (String kvPair : token.split("[,;&/?]")) {
      String[] kv = kvPair.split("=", 2);
      if (kv.length != 2 || kv[0].isEmpty()) {
        continue;
      }

      if ("skip".equals(kv[0])
          && URL.decodeQueryString(kv[1]).matches("^[\\d]+")) {
        start = Integer.parseInt(URL.decodeQueryString(kv[1]));
      }
    }
  }

  private void setupNavigationLink(Hyperlink link, int skip) {
    link.setTargetHistoryToken(getTokenForScreen(skip));
    link.setVisible(true);
  }

  private String getTokenForScreen(int skip) {
    String token = PageLinks.toProjectBranches(getProjectKey());

    if (skip > 0) {
      token += "?skip=" + skip;
    }
    return token;
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
    query = new Query().start(start).run();
    savedPanel = BRANCH;
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
    parseToken();

    prev = new Hyperlink(Util.C.pagedListPrev(), true, "");
    prev.setVisible(false);

    next = new Hyperlink(Util.C.pagedListNext(), true, "");
    next.setVisible(false);

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
    HorizontalPanel buttons = new HorizontalPanel();
    buttons.setSpacing(10);
    buttons.add(delBranch);
    buttons.add(prev);
    buttons.add(next);
    add(branchTable);
    add(buttons);
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
            showAddedBranch(branch);
            addBranch.setEnabled(true);
            nameTxtBox.setText("");
            irevTxtBox.setText("");
            branchTable.insert(branch);
            delBranch.setVisible(branchTable.hasBranchCanDelete());
          }

          @Override
          public void onFailure(Throwable caught) {
            addBranch.setEnabled(true);
            selectAllAndFocus(nameTxtBox);
            new ErrorDialog(caught.getMessage()).center();
          }
        });
  }

  void showAddedBranch(BranchInfo branch) {

    SafeHtmlBuilder b = new SafeHtmlBuilder();
    b.openElement("b");
    b.append(Gerrit.C.branchCreationConfirmationMessage());
    b.closeElement("b");

    b.openElement("p");
    b.append(branch.ref());
    b.closeElement("p");

    ConfirmationDialog confirmationDialog =
        new ConfirmationDialog(Gerrit.C.branchCreationDialogTitle(),
            b.toSafeHtml(), new ConfirmationCallback() {
      @Override
      public void onOk() {
        //do nothing
      }
    });
    confirmationDialog.center();
    confirmationDialog.setCancelVisible(false);
  }

  private static void selectAllAndFocus(TextBox textBox) {
    textBox.selectAll();
    textBox.setFocus(true);
  }

  private class BranchesTable extends NavigationTable<BranchInfo> {
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
      fmt.addStyleName(0, 4, Gerrit.RESOURCES.css().dataHeader());

      updateDeleteHandler = new ValueChangeHandler<Boolean>() {
        @Override
        public void onValueChange(ValueChangeEvent<Boolean> event) {
          updateDeleteButton();
        }
      };
    }

    Set<String> getCheckedRefs() {
      Set<String> refs = new HashSet<>();
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
            @Override
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
              delBranch.setVisible(branchTable.hasBranchCanDelete());
            }

            @Override
            public void onFailure(Throwable caught) {
              query = new Query().start(start).run();
              super.onFailure(caught);
            }
          });
    }

    void display(List<BranchInfo> branches) {
      displaySubset(branches, 0, branches.size());
    }

    void displaySubset(List<BranchInfo> branches, int fromIndex, int toIndex) {
      canDelete = false;

      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (BranchInfo k : branches.subList(fromIndex, toIndex)) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, k);
      }
    }

    void insert(BranchInfo info) {
      if (table.getRowCount() <= pageSize || pageSize == 0) {
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
      } else {
        next.setVisible(true);
        setupNavigationLink(next, ProjectBranchesScreen.this.start + pageSize);
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
        if ("HEAD".equals(k.getShortName())) {
          setHeadRevision(row, 3, k.revision());
        } else {
          table.setText(row, 3, k.revision());
        }
      } else {
        table.setText(row, 3, "");
      }

      FlowPanel actionsPanel = new FlowPanel();
      if (c != null) {
        actionsPanel.add(new Anchor(c.getLinkName(), false,
            c.toBranch(new Branch.NameKey(getProjectKey(), k.ref()))));
      }
      if (k.web_links() != null) {
        for (WebLinkInfo webLink : Natives.asList(k.web_links())) {
          actionsPanel.add(webLink.toAnchor());
        }
      }
      if (k.actions() != null) {
        k.actions().copyKeysIntoChildren("id");
        for (ActionInfo a : Natives.asList(k.actions().values())) {
          actionsPanel.add(new ActionButton(getProjectKey(), k, a));
        }
      }
      table.setWidget(row, 4, actionsPanel);

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      String iconCellStyle = Gerrit.RESOURCES.css().iconCell();
      String dataCellStyle = Gerrit.RESOURCES.css().dataCell();
      if (RefNames.REFS_CONFIG.equals(k.getShortName())
          || "HEAD".equals(k.getShortName())) {
        iconCellStyle = Gerrit.RESOURCES.css().specialBranchIconCell();
        dataCellStyle = Gerrit.RESOURCES.css().specialBranchDataCell();
        fmt.setStyleName(row, 0, iconCellStyle);
      }
      fmt.addStyleName(row, 1, iconCellStyle);
      fmt.addStyleName(row, 2, dataCellStyle);
      fmt.addStyleName(row, 3, dataCellStyle);
      fmt.addStyleName(row, 4, dataCellStyle);

      setRowItem(row, k);
    }

    private void setHeadRevision(final int row, final int column,
        final String rev) {
      AccessMap.get(getProjectKey(),
          new GerritCallback<ProjectAccessInfo>() {
            @Override
            public void onSuccess(ProjectAccessInfo result) {
              if (result.isOwner()) {
                table.setWidget(row, column, getHeadRevisionWidget(rev));
              } else {
                table.setText(row, 3, rev);
              }
            }
          });
    }

    private Widget getHeadRevisionWidget(final String headRevision) {
      FlowPanel p = new FlowPanel();
      final InlineLabel l = new InlineLabel(headRevision);
      final Image edit = new Image(Gerrit.RESOURCES.edit());
      edit.addStyleName(Gerrit.RESOURCES.css().editHeadButton());

      final NpTextBox input = new NpTextBox();
      input.setVisibleLength(35);
      input.setValue(headRevision);
      input.setVisible(false);
      final Button save = new Button();
      save.setText(Util.C.saveHeadButton());
      save.setVisible(false);
      save.setEnabled(false);
      final Button cancel = new Button();
      cancel.setText(Util.C.cancelHeadButton());
      cancel.setVisible(false);

      OnEditEnabler e = new OnEditEnabler(save);
      e.listenTo(input);

      edit.addClickHandler(new  ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          l.setVisible(false);
          edit.setVisible(false);
          input.setVisible(true);
          save.setVisible(true);
          cancel.setVisible(true);
        }
      });
      save.addClickHandler(new  ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          save.setEnabled(false);
          ProjectApi.setHead(getProjectKey(), input.getValue().trim(),
              new GerritCallback<NativeString>() {
            @Override
            public void onSuccess(NativeString result) {
              Gerrit.display(PageLinks.toProjectBranches(getProjectKey()));
            }

            @Override
            public void onFailure(Throwable caught) {
              super.onFailure(caught);
              save.setEnabled(true);
            }
          });
        }
      });
      cancel.addClickHandler(new  ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          l.setVisible(true);
          edit.setVisible(true);
          input.setVisible(false);
          input.setValue(headRevision);
          save.setVisible(false);
          save.setEnabled(false);
          cancel.setVisible(false);
        }
      });

      p.add(l);
      p.add(edit);
      p.add(input);
      p.add(save);
      p.add(cancel);
      return p;
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

    @Override
    protected void onOpenRow(int row) {
      if (row > 0) {
        movePointerTo(row);
      }
    }

    @Override
    protected Object getRowItemKey(BranchInfo item) {
      return item.ref();
    }
  }

  private class Query {
    private int qStart;
    private boolean open;

    Query start(int start) {
      this.qStart = start;
      return this;
    }

    Query run() {
      // Retrieve one more branch than page size to determine if there are more
      // branches to display
      ProjectApi.getBranches(getProjectKey(), pageSize + 1, qStart,
              new ScreenLoadCallback<JsArray<BranchInfo>>(ProjectBranchesScreen.this) {
                @Override
                public void preDisplay(JsArray<BranchInfo> result) {
                  if (!isAttached()) {
                    // View has been disposed.
                  } else if (query == Query.this) {
                    query = null;
                    showList(result);
                  } else {
                    query.run();
                  }
                }
          });
      return this;
    }

    void showList(JsArray<BranchInfo> result) {
      if (open && (result.length() != 0)) {
        Gerrit.display(PageLinks.toProjectBranches(getProjectKey()));
        return;
      }
      setToken(getTokenForScreen(qStart));
      ProjectBranchesScreen.this.start = qStart;

      if (result.length() <= pageSize) {
        branchTable.display(Natives.asList(result));
        next.setVisible(false);
      } else {
        branchTable.displaySubset(Natives.asList(result), 0,
            result.length() - 1);
        setupNavigationLink(next, qStart + pageSize);
      }
      if (qStart > 0) {
        setupNavigationLink(prev, qStart - pageSize);
      } else {
        prev.setVisible(false);
      }

      delBranch.setVisible(branchTable.hasBranchCanDelete());
      Set<String> checkedRefs = branchTable.getCheckedRefs();
      branchTable.setChecked(checkedRefs);
      updateForm();

      if (!isCurrentView()) {
        display();
      }
    }
  }
}
