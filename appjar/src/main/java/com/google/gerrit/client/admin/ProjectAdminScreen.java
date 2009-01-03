// Copyright 2008 Google Inc.
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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.data.GerritConfig;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountGroupSuggestOracle;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.client.ui.DomUtil;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.TextSaveButtonListener;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SourcesTableEvents;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.TableListener;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ProjectAdminScreen extends AccountScreen {
  private Project.Id projectId;

  private TextBox ownerTxtBox;
  private SuggestBox ownerTxt;
  private Button saveOwner;

  private TextArea descTxt;
  private Button saveDesc;

  private RightsTable rights;
  private Button delRight;

  public ProjectAdminScreen(final Project.Id toShow) {
    projectId = toShow;
  }

  @Override
  public void onLoad() {
    if (descTxt == null) {
      initUI();
    }

    enableForm(false);
    saveOwner.setEnabled(false);
    saveDesc.setEnabled(false);
    super.onLoad();

    Util.PROJECT_SVC.projectDetail(projectId,
        new GerritCallback<ProjectDetail>() {
          public void onSuccess(final ProjectDetail result) {
            enableForm(true);
            saveOwner.setEnabled(false);
            saveDesc.setEnabled(false);
            display(result);
          }
        });
  }

  private void enableForm(final boolean on) {
    ownerTxtBox.setEnabled(on);
    descTxt.setEnabled(on);
    delRight.setEnabled(on);
  }

  private void initUI() {
    initOwner();
    initDescription();
    initRights();
  }

  private void initOwner() {
    final VerticalPanel ownerPanel = new VerticalPanel();
    final Label ownerHdr = new Label(Util.C.headingOwner());
    ownerHdr.setStyleName("gerrit-SmallHeading");
    ownerPanel.add(ownerHdr);

    ownerTxtBox = new TextBox();
    ownerTxtBox.setVisibleLength(60);
    ownerTxt = new SuggestBox(new AccountGroupSuggestOracle(), ownerTxtBox);
    ownerPanel.add(ownerTxt);

    saveOwner = new Button(Util.C.buttonChangeGroupOwner());
    saveOwner.addClickListener(new ClickListener() {
      public void onClick(Widget sender) {
        final String newOwner = ownerTxt.getText().trim();
        if (newOwner.length() > 0) {
          Util.PROJECT_SVC.changeProjectOwner(projectId, newOwner,
              new GerritCallback<VoidResult>() {
                public void onSuccess(final VoidResult result) {
                  saveOwner.setEnabled(false);
                }
              });
        }
      }
    });
    ownerPanel.add(saveOwner);
    add(ownerPanel);

    new TextSaveButtonListener(ownerTxtBox, saveOwner);
  }

  private void initDescription() {
    final VerticalPanel vp = new VerticalPanel();
    final Label descHdr = new Label(Util.C.headingDescription());
    descHdr.setStyleName("gerrit-SmallHeading");
    vp.add(descHdr);

    descTxt = new TextArea();
    descTxt.setVisibleLines(6);
    descTxt.setCharacterWidth(60);
    vp.add(descTxt);

    saveDesc = new Button(Util.C.buttonSaveDescription());
    saveDesc.addClickListener(new ClickListener() {
      public void onClick(Widget sender) {
        final String txt = descTxt.getText().trim();
        Util.PROJECT_SVC.changeProjectDescription(projectId, txt,
            new GerritCallback<VoidResult>() {
              public void onSuccess(final VoidResult result) {
                saveDesc.setEnabled(false);
              }
            });
      }
    });
    vp.add(saveDesc);
    add(vp);

    new TextSaveButtonListener(descTxt, saveDesc);
  }

  private void initRights() {
    final Label rightsHdr = new Label(Util.C.headingAccessRights());
    rightsHdr.setStyleName("gerrit-SmallHeading");

    rights = new RightsTable();

    delRight = new Button(Util.C.buttonDeleteGroupMembers());
    delRight.addClickListener(new ClickListener() {
      public void onClick(final Widget sender) {
        rights.deleteChecked();
      }
    });

    add(rightsHdr);
    add(rights);
    add(delRight);
  }

  private void display(final ProjectDetail result) {
    final Project project = result.project;
    final AccountGroup owner = result.groups.get(project.getOwnerGroupId());
    setTitleText(Util.M.project(project.getName()));
    if (owner != null) {
      ownerTxt.setText(owner.getName());
    } else {
      ownerTxt.setText(Util.M.deletedGroup(project.getOwnerGroupId().get()));
    }
    descTxt.setText(project.getDescription());
    rights.display(result.groups, result.rights);
  }

  private class RightsTable extends FancyFlexTable<ProjectRight> {
    RightsTable() {
      table.setText(0, 2, Util.C.columnApprovalCategory());
      table.setText(0, 3, Util.C.columnGroupName());
      table.setText(0, 4, Util.C.columnRightRange());
      table.addTableListener(new TableListener() {
        public void onCellClicked(SourcesTableEvents sender, int row, int cell) {
          if (cell != 1 && getRowItem(row) != null) {
            movePointerTo(row);
          }
        }
      });

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, S_ICON_HEADER);
      fmt.addStyleName(0, 2, S_DATA_HEADER);
      fmt.addStyleName(0, 3, S_DATA_HEADER);
      fmt.addStyleName(0, 4, S_DATA_HEADER);
    }

    @Override
    protected Object getRowItemKey(final ProjectRight item) {
      return item.getKey();
    }

    @Override
    protected boolean onKeyPress(final char keyCode, final int modifiers) {
      if (super.onKeyPress(keyCode, modifiers)) {
        return true;
      }
      if (modifiers == 0) {
        switch (keyCode) {
          case 's':
          case 'c':
            toggleCurrentRow();
            return true;
        }
      }
      return false;
    }

    @Override
    protected void onOpenItem(final ProjectRight item) {
      toggleCurrentRow();
    }

    private void toggleCurrentRow() {
      final CheckBox cb = (CheckBox) table.getWidget(getCurrentRow(), 1);
      cb.setChecked(!cb.isChecked());
    }

    void deleteChecked() {
      final HashSet<ProjectRight.Key> ids = new HashSet<ProjectRight.Key>();
      for (int row = 1; row < table.getRowCount(); row++) {
        final ProjectRight k = getRowItem(row);
        if (k != null && table.getWidget(row, 1) instanceof CheckBox
            && ((CheckBox) table.getWidget(row, 1)).isChecked()) {
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
        populate(row, groups, k);
      }
    }

    void populate(final int row,
        final Map<AccountGroup.Id, AccountGroup> groups, final ProjectRight k) {
      final GerritConfig config = Gerrit.getGerritConfig();
      final ApprovalType ar = config.getApprovalType(k.getApprovalCategoryId());
      final AccountGroup group = groups.get(k.getAccountGroupId());

      if (ProjectRight.WILD_PROJECT.equals(k.getProjectId())) {
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
        final StringBuilder m = new StringBuilder();
        final ApprovalCategoryValue min, max;
        min = ar != null ? ar.getValue(k.getMinValue()) : null;
        max = ar != null ? ar.getValue(k.getMaxValue()) : null;

        formatValue(m, k.getMinValue(), min);
        if (k.getMinValue() != k.getMaxValue()) {
          m.append("<br>");
          formatValue(m, k.getMaxValue(), max);
        }
        table.setHTML(row, 4, m.toString());
      }

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, S_ICON_CELL);
      fmt.addStyleName(row, 2, S_DATA_CELL);
      fmt.addStyleName(row, 3, S_DATA_CELL);
      fmt.addStyleName(row, 4, S_DATA_CELL);
      fmt.addStyleName(row, 4, "gerrit-ProjectAdmin-ApprovalCategoryRangeLine");

      setRowItem(row, k);
    }

    private void formatValue(final StringBuilder m, final short v,
        final ApprovalCategoryValue e) {
      m.append("<span class=\"gerrit-ProjectAdmin-ApprovalCategoryValue\">");
      if (v == 0) {
        m.append(' ');
      } else if (v > 0) {
        m.append('+');
      }
      m.append(v);
      m.append("</span>");
      if (e != null) {
        m.append(": ");
        m.append(DomUtil.escape(e.getName()));
      }
    }
  }
}
