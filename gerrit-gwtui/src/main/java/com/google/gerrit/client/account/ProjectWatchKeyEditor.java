// Copyright (C) 2011 The Android Open Source Project
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

import java.util.List;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.client.ui.ProjectsTable;
import com.google.gerrit.client.ui.ProjectNameSuggestOracle;
import com.google.gerrit.common.data.AccountProjectWatchInfo;
import com.google.gerrit.common.data.ProjectList;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestBox.DefaultSuggestionDisplay;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwt.user.client.Window;

import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.HidePopupPanelCommand;
import com.google.gwtexpui.user.client.PluginSafeDialogBox;

class ProjectWatchKeyEditor extends Composite implements
    ResizeHandler {
  private HintTextBox projectTxt, filterTxt;
  private Button save, browse, cancel;
  private boolean submitOnSelection;

  protected PluginSafeDialogBox popup;
  private Button close;
  private ProjectsTable projectsTab;
  private boolean firstPopupLoad = true;
  private boolean poppingUp;

  protected int preferredPopupWidth = -1;
  protected ScrollPanel sp;
  private PopupPanel.PositionCallback popupPosition;
  private HandlerRegistration regWindowResize;

  private AccountProjectWatch.Key key;

  ProjectWatchKeyEditor(AccountProjectWatch.Key key,
      boolean enableBrowse, boolean enableCancel) {
    projectTxt = new HintTextBox();
     final SuggestBox projectBox =
         new SuggestBox(new ProjectNameSuggestOracle(), projectTxt);
    projectTxt.setVisibleLength(50);
    projectTxt.setHintText(Util.C.defaultProjectName());
    projectTxt.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        submitOnSelection = false;

        if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
          if (((DefaultSuggestionDisplay) projectBox.getSuggestionDisplay())
              .isSuggestionListShowing()) {
            submitOnSelection = true;
          } else {
            onSave();
          }
        }
      }
    });
    projectBox.addSelectionHandler(new SelectionHandler<Suggestion>() {
      @Override
      public void onSelection(SelectionEvent<Suggestion> event) {
        if (submitOnSelection) {
          submitOnSelection = false;
          onSave();
        }
      }
    });

    filterTxt = new HintTextBox();
    filterTxt.setVisibleLength(50);
    filterTxt.setHintText(Util.C.defaultFilter());
    filterTxt.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
          onSave();
        }
      }
    });

    save = new Button(Util.C.buttonWatchProject());
    save.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        onSave();
      }
    });

    browse = new Button(Util.C.buttonBrowseProjects());
    browse.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        onBrowse();
      }
    });

    cancel = new Button(Util.C.buttonCancel());
    cancel.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        onCancel();
      }
    });

    Grid grid = new Grid(2, 2);
    grid.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    grid.setText(0, 0, Util.C.watchedProjectName());
    grid.setWidget(0, 1, projectBox);
    grid.setText(1, 0, Util.C.watchedProjectFilter());
    grid.setWidget(1, 1, filterTxt);
    final CellFormatter fmt = grid.getCellFormatter();
    fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().header());
    fmt.addStyleName(1, 0, Gerrit.RESOURCES.css().header());

    FlowPanel editPanel = new FlowPanel();
    editPanel.setStyleName(Gerrit.RESOURCES.css().addWatchPanel());
    editPanel.add(grid);
    editPanel.add(save);
    if (enableBrowse) {
      editPanel.add(browse);
    }
    if (enableCancel) {
      editPanel.add(cancel);
    }
    popup = new PluginSafeDialogBox();
    popup.setModal(false);
    popup.setText(Util.C.projects());

    projectsTab = new ProjectsTable() {
      {
        keysNavigation.add(new OpenKeyCommand(0, 'o', Util.C.projectListOpen()));
        keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER,
                                                      Util.C.projectListOpen()));
      }

      @Override
      protected void movePointerTo(final int row, final boolean scroll) {
        super.movePointerTo(row, scroll);

        // prevent user input from being overwritten by simply popping up
        if (! poppingUp || "".equals(getProjectTxt()) ) {
          setProjectTxt(getRowItem(row).getName());
        }
      }

      @Override
      protected void onOpenRow(final int row) {
        super.onOpenRow(row);
        setProjectTxt(getRowItem(row).getName());
        onSave();
      }
    };
    projectsTab.setSavePointerId(PageLinks.SETTINGS_PROJECTS);

    close = new Button(Util.C.projectsClose());
    close.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        closePopup();
      }
    });
    final FlowPanel pfp = new FlowPanel();
    sp = new ScrollPanel(projectsTab);
    pfp.add(sp);
    pfp.add(close);
    popup.setWidget(pfp);

    popupPosition = new PopupPanel.PositionCallback() {
      public void setPosition(int offsetWidth, int offsetHeight) {
        setPopupPosition(offsetWidth, offsetHeight);
      }
    };

    initWidget(editPanel);
    setWatchKey(key);
  }

  protected void setPopupPosition(int offsetWidth, int offsetHeight) {
    int top = this.getAbsoluteTop() - 45; // under page header

    // Try to place it to the right of everything else, but not
    // right justified
    int left = getAbsoluteLeft() + getOffsetWidth();

    if (top + offsetHeight > Window.getClientHeight()) {
      top = Window.getClientHeight() - offsetHeight;
    }
    if (left + offsetWidth > Window.getClientWidth()) {
      left = Window.getClientWidth() - offsetWidth;
    }

    if (top < 0) {
      setHeight((getOffsetHeight() + top) + "px");
      top = 0;
    }
    if (left < 0) {
      setWidth((getOffsetWidth() + left) + "px");
      left = 0;
    }
    popup.setPopupPosition(left, top);
  }

  protected void onSave() {
    String newProject = projectTxt.getText();
    String newFilter = filterTxt.getText();
    if(newProject == null || newProject.isEmpty()) {
      return;
    }
    if (newFilter == null || newFilter.isEmpty()
        || newFilter.equals(Util.C.defaultFilter())) {
      newFilter = null;
    }
    setEnabled(false);
    GerritCallback<AccountProjectWatchInfo> callback = new GerritCallback<AccountProjectWatchInfo>() {
        public void onSuccess(final AccountProjectWatchInfo result) {
          setWatchKey(result.getWatch().getKey());
          onUpdate(result);
          setEnabled(true);
        }
        @Override
        public void onFailure(final Throwable caught) {
          setEnabled(true);
          super.onFailure(caught);
        }
    };
    if(key != null) {
      String oldFilter = key.getFilter().get();
      if (oldFilter.equals(newFilter) && key.getProjectName().equals(newProject)) {
        return;
      }
      Util.ACCOUNT_SVC.updateProjectWatchKey(key, newProject, newFilter, callback);
    } else {
      Util.ACCOUNT_SVC.addProjectWatch(newProject, newFilter, callback);
    }
  }

  protected void setEnabled(boolean enable) {
    save.setEnabled(enable);
    cancel.setEnabled(enable);
    projectTxt.setEnabled(enable);
    filterTxt.setEnabled(enable);
  }

  public void setWatchKey(AccountProjectWatch.Key k) {
    key = k;
    display();
  }

  public void setSaveTxt(String s) {
    save.setHTML(s);
  }

  public String getProjectTxt() {
    return projectTxt.getText();
  }

  public void setProjectTxt(String prj) {
    projectTxt.setText(prj);
  }

  public void onBrowse() {
  }

  public void onCancel() {
    display();
  }

  public void onUpdate(AccountProjectWatchInfo info) {
  }

  public void display() {
    if (key == null) {
      projectTxt.setText("");
      filterTxt.setText("");
    } else {
      projectTxt.setText(key.getProjectName().get());
      String filter = key.getFilter().get();
      if ("*".equals(filter)) {
        filterTxt.setText(null);
      } else {
        filterTxt.setText(filter);
      }
    }
  }

  protected void displayPopup() {
    poppingUp = true;
    if (firstPopupLoad) { // For sizing/positioning, delay display until loaded
      populateProjects();
    } else {
      popup.setPopupPositionAndShow(popupPosition);

      GlobalKey.dialog(popup);
      GlobalKey.addApplication(popup, new HidePopupPanelCommand(0,
          KeyCodes.KEY_ESCAPE, popup));
      projectsTab.setRegisterKeys(true);

      projectsTab.finishDisplay();

      if (regWindowResize == null) {
        regWindowResize = Window.addResizeHandler(this);
      }

      poppingUp = false;
    }
  }

  protected void closePopup() {
    popup.hide();
    if (regWindowResize != null) {
      regWindowResize.removeHandler();
      regWindowResize = null;
    }
  }

  protected void populateProjects() {
    Util.PROJECT_SVC.visibleProjects(
        new GerritCallback<ProjectList>() {
      @Override
      public void onSuccess(final ProjectList result) {
        projectsTab.display(result.getProjects());
        if (firstPopupLoad) { // Display was delayed until table was loaded
          firstPopupLoad = false;
          displayPopup();
        }
      }
    });
  }

  @Override
  public void onResize(final ResizeEvent event) {
    sp.setSize("100%","100%");

    // For some reason keeping track of preferredWidth keeps the width better,
    // but using 100% for height works better.
    popup.setHeight("100%");
    popupPosition.setPosition(preferredPopupWidth, popup.getOffsetHeight());
  }
}

