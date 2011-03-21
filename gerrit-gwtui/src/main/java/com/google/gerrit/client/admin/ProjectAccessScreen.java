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
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.GerritConfig;
import com.google.gerrit.common.data.InheritedRefRight;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ProjectAccessScreen extends ProjectScreen {
  private Panel parentPanel;
  private Hyperlink parentName;

  private RightsTable rights;
  private Button delRight;
  private AccessRightEditor rightEditor;

  public ProjectAccessScreen(final Project.NameKey toShow) {
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
    rightEditor.enableForm(on);
  }

  private void initParent() {
    parentName = new Hyperlink("", "");

    final CheckBox show = new CheckBox();
    show.setChecked(true);
    show.addClickHandler(new ClickHandler() {
      public void onClick(ClickEvent event) {
        rights.showInherited(show.isChecked());
      }
    });

    Grid g = new Grid(2, 3);
    g.setWidget(0, 0, new SmallHeading(Util.C.headingParentProjectName()));
    g.setWidget(1, 0, parentName);
    g.setWidget(1, 1, show);
    g.setText(1, 2, Util.C.headingShowInherited());

    parentPanel = new VerticalPanel();
    parentPanel.add(g);
    add(parentPanel);
  }

  private void initRights() {
    rights = new RightsTable();

    delRight = new Button(Util.C.buttonDeleteGroupMembers());
    delRight.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        final HashSet<RefRight.Key> refRightIds = rights.getRefRightIdsChecked();
        doDeleteRefRights(refRightIds);
      }
    });

    rightEditor = new AccessRightEditor(getProjectKey());
    rightEditor.addValueChangeHandler(new ValueChangeHandler<ProjectDetail>() {
        @Override
        public void onValueChange(ValueChangeEvent<ProjectDetail> event) {
          display(event.getValue());
        }
      });

    add(new SmallHeading(Util.C.headingAccessRights()));
    add(rights);
    add(delRight);
    add(rightEditor);
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
    parentName.setTargetHistoryToken(Dispatcher.toProjectAdmin(parent, ACCESS));
    parentName.setText(parent.get());

    rights.display(result.groups, result.rights);

    rightEditor.setVisible(result.canModifyAccess);
    delRight.setVisible(rights.getCanDelete());
  }

  private void doDeleteRefRights(final HashSet<RefRight.Key> refRightIds) {
    if (!refRightIds.isEmpty()) {
      Util.PROJECT_SVC.deleteRight(getProjectKey(), refRightIds,
          new GerritCallback<ProjectDetail>() {
        @Override
        public void onSuccess(final ProjectDetail result) {
          //The user could no longer modify access after deleting a ref right.
          display(result);
        }
      });
    }
  }

  private class RightsTable extends FancyFlexTable<RefRight> {
    boolean canDelete;
    Map<AccountGroup.Id, AccountGroup> groups;

    RightsTable() {
      table.setWidth("");
      table.setText(0, 2, Util.C.columnRightOrigin());
      table.setText(0, 3, Util.C.columnApprovalCategory());
      table.setText(0, 4, Util.C.columnGroupName());
      table.setText(0, 5, Util.C.columnRefName());
      table.setText(0, 6, Util.C.columnRightRange());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 4, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 5, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 6, Gerrit.RESOURCES.css().dataHeader());

      table.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          onOpenRow(table.getCellForEvent(event).getRowIndex());
        }
      });
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

    void display(final Map<AccountGroup.Id, AccountGroup> grps,
        final List<InheritedRefRight> refRights) {
      groups = grps;
      canDelete = false;

      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final InheritedRefRight r : refRights) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, r);
      }
    }
    protected void onOpenRow(final int row) {
      if (row > 0) {
        RefRight right = getRowItem(row);
        rightEditor.load(right, groups.get(right.getAccountGroupId()));
      }
    }

    void populate(final int row, final InheritedRefRight r) {
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

      if (r.isInherited()) {
        Project.NameKey fromProject = right.getKey().getProjectNameKey();
        table.setWidget(row, 2, new Hyperlink(fromProject.get(), Dispatcher
            .toProjectAdmin(fromProject, ACCESS)));
      } else {
        table.setText(row, 2, "");
      }

      table.setText(row, 3, ar != null ? ar.getCategory().getName()
                                       : right.getApprovalCategoryId().get() );

      if (group != null) {
        table.setWidget(row, 4, new Hyperlink(group.getName(), Dispatcher
            .toAccountGroup(group.getId())));
      } else {
        table.setText(row, 4, Util.M.deletedGroup(right.getAccountGroupId()
            .get()));
      }

      table.setText(row, 5, right.getRefPatternForDisplay());

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
        SafeHtml.set(table, row, 6, m);
      }

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().iconCell());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 4, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 5, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 6, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 6, Gerrit.RESOURCES.css()
          .projectAdminApprovalCategoryRangeLine());

      setRowItem(row, right);
    }

    public void showInherited(boolean visible) {
      for (int r = 0; r < table.getRowCount(); r++) {
        if (table.getWidget(r, 2) instanceof Hyperlink) {
          table.getRowFormatter().setVisible(r, visible);
        }
      }
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
