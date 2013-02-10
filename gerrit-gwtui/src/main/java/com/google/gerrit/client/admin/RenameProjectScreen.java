// Copyright (C) 2013 The Android Open Source Project
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

import static com.google.gerrit.common.data.GlobalCapability.ADMINISTRATE_SERVER;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.NotFoundScreen;
import com.google.gerrit.client.account.AccountCapabilities;
import com.google.gerrit.client.projects.ProjectApi;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.client.ui.ProjectListPopup;
import com.google.gerrit.client.ui.ProjectNameSuggestOracle;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gerrit.client.VoidResult;

import org.eclipse.jgit.lib.Constants;

public class RenameProjectScreen extends Screen {
  private Grid grid;
  private NpTextBox destination;
  private Button rename;
  private Button browse;
  private HintTextBox sourceHints;
  private SuggestBox source;
  private ProjectListPopup projectsPopup;

  public RenameProjectScreen() {
    super();
    setRequiresSignIn(true);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    AccountCapabilities.all(new GerritCallback<AccountCapabilities>() {
      @Override
      public void onSuccess(AccountCapabilities ac) {
        if (ac.canPerform(ADMINISTRATE_SERVER)) {
          display();
        } else {
          Gerrit.display(PageLinks.ADMIN_RENAME_PROJECT, new NotFoundScreen());
        }
      }
    }, ADMINISTRATE_SERVER);
  }

  @Override
  protected void onUnload() {
    super.onUnload();
    projectsPopup.closePopup();
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.renameProjectTitle());
    addRenameProjectPanel();

    /* popup */
    projectsPopup = new ProjectListPopup() {
      @Override
      protected void onMovePointerTo(String projectName) {
        // prevent user input from being overwritten by simply poping up
        if (!projectsPopup.isPoppingUp() || "".equals(source.getText())) {
          source.setText(projectName);
        }
      }
    };
    projectsPopup.initPopup(Util.C.projects(), PageLinks.ADMIN_PROJECTS);
  }

  private void addRenameProjectPanel() {
    final VerticalPanel fp = new VerticalPanel();
    fp.setStyleName(Gerrit.RESOURCES.css().createProjectPanel());

    initTextBoxes();
    initButtons();

    addGrid(fp);

    fp.add(rename);

    add(fp);
  }

  private void initTextBoxes() {
    KeyPressHandler enterPressHandler = new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
          doRenameProject();
        }
      }
    };

    sourceHints = new HintTextBox();
    sourceHints.setVisibleLength(50);
    source = new SuggestBox(new ProjectNameSuggestOracle(), sourceHints);
    source.addKeyPressHandler(enterPressHandler);

    destination = new NpTextBox();
    destination.setVisibleLength(50);
    destination.addKeyPressHandler(enterPressHandler);
  }

  private void initButtons() {
    rename = new Button(Util.C.buttonRenameProject());
    rename.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doRenameProject();
      }
    });

    browse = new Button(Util.C.buttonBrowseProjects());
    browse.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        int top = grid.getAbsoluteTop() - 50; // under page header
        int left = 5 + grid.getAbsoluteLeft() + grid.getOffsetWidth();
        projectsPopup.setPreferredCoordinates(top, left);
        projectsPopup.displayPopup();
      }
    });
  }

  private void addGrid(final VerticalPanel fp) {
    grid = new Grid(2, 3);
    grid.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    grid.setText(0, 0, Util.C.columnProjectName() + ":");
    grid.setWidget(0, 1, source);
    grid.setWidget(0, 2, browse);
    grid.setText(1, 0, Util.C.newNameLabel() + ":");
    grid.setWidget(1, 1, destination);
    fp.add(grid);
  }

  private void doRenameProject() {
    final String sourceName = source.getText().trim();
    final String destinationName = destination.getText().trim();

    if ("".equals(sourceName)) {
      source.setFocus(true);
      return;
    }

    if ("".equals(destinationName)) {
      destination.setFocus(true);
      return;
    }

    enableForm(false);

    ProjectApi.renameProject(sourceName, destinationName,
        new GerritCallback<VoidResult>() {
          @Override
          public void onSuccess(final VoidResult result) {
            String nameWithoutSuffix = destinationName;
            if (nameWithoutSuffix.endsWith(Constants.DOT_GIT_EXT)) {
              // Be nice and drop the trailing ".git" suffix, which we never
              // keep in our database, but clients might mistakenly provide
              // anyway.
              nameWithoutSuffix = nameWithoutSuffix.substring(0, //
                  nameWithoutSuffix.length() - Constants.DOT_GIT_EXT.length());
              while (nameWithoutSuffix.endsWith("/")) {
                nameWithoutSuffix =
                    nameWithoutSuffix.substring(0,
                        nameWithoutSuffix.length() - 1);
              }
            }

            History.newItem(Dispatcher.toProjectAdmin(new Project.NameKey(
                nameWithoutSuffix), ProjectScreen.INFO));
          }

          @Override
          public void onFailure(final Throwable caught) {
            new ErrorDialog(caught.getMessage()).center();
            enableForm(true);
          }
        });
  }

  private void enableForm(final boolean enabled) {
    source.setEnabled(enabled);
    sourceHints.setEnabled(enabled);
    browse.setEnabled(enabled);
    destination.setEnabled(enabled);
    rename.setEnabled(enabled);
    if (!enabled) {
      projectsPopup.closePopup();
    }
  }
}
