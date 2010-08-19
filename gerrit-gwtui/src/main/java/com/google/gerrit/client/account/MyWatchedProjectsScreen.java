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

package com.google.gerrit.client.account;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.ProjectLink;
import com.google.gerrit.client.ui.ProjectNameSuggestOracle;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.AccountProjectWatchInfo;
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.Change.Status;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


public class MyWatchedProjectsScreen extends SettingsScreen {
  private WatchTable watches;

  private Button addNew;
  private NpTextBox nameBox;
  private SuggestBox nameTxt;
  private NpTextBox filterTxt;
  private Button delSel;
  private boolean submitOnSelection;
  private ProjectTable projects;
  private List<Project> allProjects;
  private List<AccountProjectWatchInfo> watchedProjects;

  @Override
  protected void onInitUI() {
    super.onInitUI();

    {
      nameBox = new NpTextBox();
      nameTxt = new SuggestBox(new ProjectNameSuggestOracle(), nameBox);
      nameBox.setVisibleLength(50);
      nameBox.setText(Util.C.defaultProjectName());
      nameBox.addStyleName(Gerrit.RESOURCES.css().inputFieldTypeHint());
      nameBox.addFocusHandler(new FocusHandler() {
        @Override
        public void onFocus(FocusEvent event) {
          if (Util.C.defaultProjectName().equals(nameBox.getText())) {
            nameBox.setText("");
            nameBox.removeStyleName(Gerrit.RESOURCES.css().inputFieldTypeHint());
          }
        }
      });
      nameBox.addBlurHandler(new BlurHandler() {
        @Override
        public void onBlur(BlurEvent event) {
          if ("".equals(nameBox.getText())) {
            nameBox.setText(Util.C.defaultProjectName());
            nameBox.addStyleName(Gerrit.RESOURCES.css().inputFieldTypeHint());
          }
        }
      });
      nameBox.addKeyPressHandler(new KeyPressHandler() {
        @Override
        public void onKeyPress(KeyPressEvent event) {
          submitOnSelection = false;

          if (event.getCharCode() == KeyCodes.KEY_ENTER) {
            if (nameTxt.isSuggestionListShowing()) {
              submitOnSelection = true;
            } else {
              doAddNew();
            }
          }
        }
      });
      nameTxt.addSelectionHandler(new SelectionHandler<Suggestion>() {
        @Override
        public void onSelection(SelectionEvent<Suggestion> event) {
          if (submitOnSelection) {
            submitOnSelection = false;
            doAddNew();
          }
        }
      });

      filterTxt = new NpTextBox();
      filterTxt.setVisibleLength(50);
      filterTxt.setText(Util.C.defaultFilter());
      filterTxt.addStyleName(Gerrit.RESOURCES.css().inputFieldTypeHint());
      filterTxt.addFocusHandler(new FocusHandler() {
        @Override
        public void onFocus(FocusEvent event) {
          if (Util.C.defaultFilter().equals(filterTxt.getText())) {
            filterTxt.setText("");
            filterTxt.removeStyleName(Gerrit.RESOURCES.css().inputFieldTypeHint());
          }
        }
      });
      filterTxt.addBlurHandler(new BlurHandler() {
        @Override
        public void onBlur(BlurEvent event) {
          if ("".equals(filterTxt.getText())) {
            filterTxt.setText(Util.C.defaultFilter());
            filterTxt.addStyleName(Gerrit.RESOURCES.css().inputFieldTypeHint());
          }
        }
      });
      filterTxt.addKeyPressHandler(new KeyPressHandler() {
        @Override
        public void onKeyPress(KeyPressEvent event) {
          if (event.getCharCode() == KeyCodes.KEY_ENTER) {
            doAddNew();
          }
        }
      });

      projects = new ProjectTable();

      addNew = new Button(Util.C.buttonWatchProject());
      addNew.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          doAddNew();
        }
      });

      final Grid grid = new Grid(2, 2);
      grid.setStyleName(Gerrit.RESOURCES.css().infoBlock());
      grid.setText(0, 0, Util.C.watchedProjectName());
      grid.setWidget(0, 1, nameTxt);

      grid.setText(1, 0, Util.C.watchedProjectFilter());
      grid.setWidget(1, 1, filterTxt);

      final CellFormatter fmt = grid.getCellFormatter();
      fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
      fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().header());
      fmt.addStyleName(1, 0, Gerrit.RESOURCES.css().header());
      fmt.addStyleName(1, 0, Gerrit.RESOURCES.css().bottomheader());

      DisclosurePanel unwatched = new DisclosurePanel(Util.C.unwatchedProjects());
      unwatched.setContent(projects);

      final Grid grid2 = new Grid(1, 2);
      final FlowPanel fp2 = new FlowPanel();
      fp2.add(addNew);
      grid2.setWidget(0, 0, fp2);
      grid2.setWidget(0, 1, unwatched);

      final CellFormatter fmt2 = grid2.getCellFormatter();
      fmt2.setAlignment(0, 0, HasHorizontalAlignment.ALIGN_LEFT,
        HasVerticalAlignment.ALIGN_TOP);

      final FlowPanel fp = new FlowPanel();
      fp.setStyleName(Gerrit.RESOURCES.css().addWatchPanel());
      fp.add(grid);
      fp.add(grid2);
      add(fp);
    }

    watches = new WatchTable();
    add(watches);

    delSel = new Button(Util.C.buttonDeleteSshKey());
    delSel.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        watches.deleteChecked();
      }
    });
    add(delSel);
  }

  void doAddNew() {
    final String projectName = nameTxt.getText();
    if (projectName == null || projectName.length() == 0
        || Util.C.defaultProjectName().equals(projectName)) {
      return;
    }

    String filter = filterTxt.getText();
    if (filter == null || filter.isEmpty()
        || filter.equals(Util.C.defaultFilter())) {
      filter = null;
    }

    addNew.setEnabled(false);
    nameBox.setEnabled(false);
    filterTxt.setEnabled(false);

    Util.ACCOUNT_SVC.addProjectWatch(projectName, filter,
        new GerritCallback<AccountProjectWatchInfo>() {
          public void onSuccess(final AccountProjectWatchInfo result) {
            addNew.setEnabled(true);
            nameBox.setEnabled(true);
            filterTxt.setEnabled(true);

            nameTxt.setText("");
            watches.insertWatch(result);
          }

          @Override
          public void onFailure(final Throwable caught) {
            addNew.setEnabled(true);
            nameBox.setEnabled(true);
            filterTxt.setEnabled(true);

            super.onFailure(caught);
          }
        });
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.ACCOUNT_SVC
        .myProjectWatch(new ScreenLoadCallback<List<AccountProjectWatchInfo>>(
            this) {
          public void preDisplay(final List<AccountProjectWatchInfo> result) {
            watchedProjects = result;
            watches.display(result);
            displayUnwatchedProjects();
          }
        });
    Util.PROJECT_SVC.visibleProjects(new ScreenLoadCallback<List<Project>>(this) {
          @Override
          protected void preDisplay(final List<Project> result) {
            allProjects = new ArrayList(result);
            allProjects.remove(0); // unneeded fake "--all projects--" entry
            displayUnwatchedProjects();
          }
        });
  }

  protected void displayUnwatchedProjects() {
    if (watchedProjects != null && allProjects != null) {
      List<Project> unwatched = new ArrayList(allProjects);
      for (final AccountProjectWatchInfo w : watchedProjects) {
        int i = 0;
        for (final Project p : unwatched) {
          if (p.getName().equals(w.getProject().getName())) {
            unwatched.remove(i);
            break;
          }
          i++;
        }
      }
      projects.display(unwatched);
    }
  }

  private class WatchTable extends FancyFlexTable<AccountProjectWatchInfo> {
    WatchTable() {
      table.setWidth("");
      table.insertRow(1);
      table.setText(0, 2, Util.C.watchedProjectName());
      table.setText(0, 3, Util.C.watchedProjectColumnEmailNotifications());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
      fmt.setRowSpan(0, 0, 2);
      fmt.setRowSpan(0, 1, 2);
      fmt.setRowSpan(0, 2, 2);
      DOM.setElementProperty(fmt.getElement(0, 3), "align", "center");

      fmt.setColSpan(0, 3, 3);
      table.setText(1, 0, Util.C.watchedProjectColumnNewChanges());
      table.setText(1, 1, Util.C.watchedProjectColumnAllComments());
      table.setText(1, 2, Util.C.watchedProjectColumnSubmittedChanges());
      fmt.addStyleName(1, 0, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(1, 1, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(1, 2, Gerrit.RESOURCES.css().dataHeader());
    }

    void deleteChecked() {
      final HashSet<AccountProjectWatch.Key> ids =
          new HashSet<AccountProjectWatch.Key>();
      for (int row = 1; row < table.getRowCount(); row++) {
        final AccountProjectWatchInfo k = getRowItem(row);
        if (k != null && ((CheckBox) table.getWidget(row, 1)).getValue()) {
          ids.add(k.getWatch().getKey());
        }
      }
      if (!ids.isEmpty()) {
        Util.ACCOUNT_SVC.deleteProjectWatches(ids,
            new GerritCallback<VoidResult>() {
              public void onSuccess(final VoidResult result) {
                for (int row = 1; row < table.getRowCount();) {
                  final AccountProjectWatchInfo k = getRowItem(row);
                  if (k != null && ids.contains(k.getWatch().getKey())) {
                    table.removeRow(row);
                    watchedProjects.remove(row - 2);
                    displayUnwatchedProjects();
                  } else {
                    row++;
                  }
                }
              }
            });
      }
    }

    void insertWatch(final AccountProjectWatchInfo k) {
      final String newName = k.getProject().getName();
      int row = 1;
      for (; row < table.getRowCount(); row++) {
        final AccountProjectWatchInfo i = getRowItem(row);
        if (i != null && i.getProject().getName().compareTo(newName) >= 0) {
          break;
        }
      }

      table.insertRow(row);
      applyDataRowStyle(row);
      populate(row, k);
      watchedProjects.add(row - 2, k);
      displayUnwatchedProjects();
    }

    void display(final List<AccountProjectWatchInfo> result) {
      while (2 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final AccountProjectWatchInfo k : result) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, k);
      }
    }

    void populate(final int row, final AccountProjectWatchInfo k) {
      final FlowPanel fp = new FlowPanel();
      fp.add(new ProjectLink(k.getProject().getNameKey(), Status.NEW));
      if (k.getWatch().getFilter() != null) {
        Label filter = new Label(k.getWatch().getFilter());
        filter.setStyleName(Gerrit.RESOURCES.css().watchedProjectFilter());
        fp.add(filter);
      }

      table.setWidget(row, 1, new CheckBox());
      table.setWidget(row, 2, fp);
      {
        final CheckBox notifyNewChanges = new CheckBox();
        notifyNewChanges.addClickHandler(new ClickHandler() {
          @Override
          public void onClick(final ClickEvent event) {
            final boolean oldVal = k.getWatch().isNotifyNewChanges();
            k.getWatch().setNotifyNewChanges(notifyNewChanges.getValue());
            Util.ACCOUNT_SVC.updateProjectWatch(k.getWatch(),
                new GerritCallback<VoidResult>() {
                  public void onSuccess(final VoidResult result) {
                  }

                  @Override
                  public void onFailure(final Throwable caught) {
                    k.getWatch().setNotifyNewChanges(oldVal);
                    notifyNewChanges.setValue(oldVal);
                    super.onFailure(caught);
                  }
                });
          }
        });
        notifyNewChanges.setValue(k.getWatch().isNotifyNewChanges());
        table.setWidget(row, 3, notifyNewChanges);
      }
      {
        final CheckBox notifyAllComments = new CheckBox();
        notifyAllComments.addClickHandler(new ClickHandler() {
          @Override
          public void onClick(final ClickEvent event) {
            final boolean oldVal = k.getWatch().isNotifyAllComments();
            k.getWatch().setNotifyAllComments(notifyAllComments.getValue());
            Util.ACCOUNT_SVC.updateProjectWatch(k.getWatch(),
                new GerritCallback<VoidResult>() {
                  public void onSuccess(final VoidResult result) {
                  }

                  @Override
                  public void onFailure(final Throwable caught) {
                    k.getWatch().setNotifyAllComments(oldVal);
                    notifyAllComments.setValue(oldVal);
                    super.onFailure(caught);
                  }
                });
          }
        });
        notifyAllComments.setValue(k.getWatch().isNotifyAllComments());
        table.setWidget(row, 4, notifyAllComments);
      }
      {
        final CheckBox notifySubmittedChanges = new CheckBox();
        notifySubmittedChanges.addClickHandler(new ClickHandler() {
          @Override
          public void onClick(final ClickEvent event) {
            final boolean oldVal = k.getWatch().isNotifySubmittedChanges();
            k.getWatch().setNotifySubmittedChanges(
                notifySubmittedChanges.getValue());
            Util.ACCOUNT_SVC.updateProjectWatch(k.getWatch(),
                new GerritCallback<VoidResult>() {
                  public void onSuccess(final VoidResult result) {
                  }

                  @Override
                  public void onFailure(final Throwable caught) {
                    k.getWatch().setNotifySubmittedChanges(oldVal);
                    notifySubmittedChanges.setValue(oldVal);
                    super.onFailure(caught);
                  }
                });
          }
        });
        notifySubmittedChanges
            .setValue(k.getWatch().isNotifySubmittedChanges());
        table.setWidget(row, 5, notifySubmittedChanges);
      }

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().iconCell());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 4, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 5, Gerrit.RESOURCES.css().dataCell());

      setRowItem(row, k);
    }
  }

  private class ProjectTable extends NavigationTable<Project> {
    ProjectTable() {
      setSavePointerId(PageLinks.SETTINGS_PROJECTS);
      keysNavigation.add(new PrevKeyCommand(0, 'k',
        Util.C.unwatchedProjectListPrev()));
      keysNavigation.add(new NextKeyCommand(0, 'j',
        Util.C.unwatchedProjectListNext()));
      keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER,
        Util.C.unwatchedProjectListOpen()));

      table.setText(0, 1, Util.C.unwatchedProjectName());
      table.setText(0, 2, Util.C.unwatchedProjectDescription());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());

      table.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          onOpenRow(table.getCellForEvent(event).getRowIndex());
        }
      });
    }

    @Override
    protected Object getRowItemKey(final Project item) {
      return item.getNameKey();
    }

    @Override
    protected void onOpenRow(final int row) {
      if (row > 0) {
        movePointerTo(row);
        nameBox.setText(getRowItem(row).getName());
      }
    }

    void display(final List<Project> projects) {
      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final Project k : projects)
        insert(table.getRowCount(), k);

      finishDisplay();
    }

    void insert(final int row, final Project k) {
      table.insertRow(row);

      applyDataRowStyle(row);

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().cPROJECT());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());

      populate(row, k);
    }

    void populate(final int row, final Project k) {
      table.setText(row, 1, k.getName());
      table.setText(row, 2, k.getDescription());

      setRowItem(row, k);
    }
  }
}
