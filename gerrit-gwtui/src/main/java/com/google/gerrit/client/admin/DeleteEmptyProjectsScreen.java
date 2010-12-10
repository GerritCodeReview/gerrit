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
// limitations under the License.

package com.google.gerrit.client.admin;

import com.google.gerrit.client.ConfirmationCallback;
import com.google.gerrit.client.ConfirmationDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.EmptyProjectsDeletionTable;
import com.google.gerrit.client.ui.EmptyProjectsDeletionTable.ValueChangeListener;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.ProjectData;
import com.google.gerrit.common.errors.DeleteProjectException;
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.ArrayList;
import java.util.List;

public class DeleteEmptyProjectsScreen extends ProjectOptionsScreen {
  private EmptyProjectsDeletionTable projects;
  private Button deleteButton;
  private List<NameKey> projectsToDelete;

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.PROJECT_SVC.visibleProjects(new ScreenLoadCallback<List<ProjectData>>(
        this) {
      @Override
      protected void preDisplay(final List<ProjectData> result) {
        projects.display(result);
        projects.finishDisplay();
      }
    });
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.projectDeletionTitle());

    projectsToDelete = new ArrayList<NameKey>();
    projects = new EmptyProjectsDeletionTable();

    projects.setValueChangeListener(new ValueChangeListener() {

      @Override
      public void onValueChange(boolean value, NameKey selectedProject) {
        if (value) {
          projectsToDelete.add(selectedProject);
        } else {
          final int index = projectsToDelete.indexOf(selectedProject);
          projectsToDelete.remove(index);
          projects.resetSelectAllRow();
        }
        // Controls if the "Delete" button should be enabled or not
        if (projectsToDelete.size() > 0) {
          deleteButton.setEnabled(true);
        } else {
          deleteButton.setEnabled(false);
        }
      }
    });

    projects.setSavePointerId(PageLinks.ADMIN_PROJECTS);

    add(projects);

    /* Add delete button */
    deleteButton = new Button(Util.C.buttonDeleteProject());

    deleteButton.setEnabled(false);
    deleteButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        final String message = buildDeleteMessage();
        final ConfirmationDialog confirmationDialog =
            new ConfirmationDialog(Gerrit.C.projectDeletionDialogTitle(),
                new HTML(message), new ConfirmationCallback() {
                  @Override
                  public void onOk() {
                    Util.PROJECT_SVC.deleteEmptyProjects(projectsToDelete,
                        new GerritCallback<List<NameKey>>() {

                          @Override
                          public void onSuccess(List<NameKey> notDeletedProjects) {
                            for (NameKey p : projectsToDelete) {
                              if (!notDeletedProjects.contains(p)) {
                                projects.removeProject(p);
                              }
                            }
                            deleteButton.setEnabled(false);
                            projects.resetSelectAllRow();
                            projectsToDelete.clear();

                            if (!notDeletedProjects.isEmpty()) {
                              final StringBuilder errorMessage =
                                  new StringBuilder();
                              errorMessage
                                  .append("The following project(s) could not be deleted: ");
                              boolean addSeparator = false;
                              for (NameKey r : notDeletedProjects) {
                                if (addSeparator) {
                                  errorMessage.append(", ");
                                }

                                if (!addSeparator) addSeparator = true;

                                errorMessage.append(r);
                              }

                              DeleteProjectException exception =
                                  new DeleteProjectException(errorMessage
                                      .toString());
                              super.onFailure(exception);

                            }
                          }
                        });
                  }
                });
        confirmationDialog.center();
      }
    });

    add(deleteButton);
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    projects.setRegisterKeys(true);
  }

  private String buildDeleteMessage() {
    final SafeHtmlBuilder message = new SafeHtmlBuilder();

    message.openElement("b").append(
        Gerrit.C.projectDeletionConfirmationMessage()).closeElement("b");
    message.openElement("p");

    boolean addSeparator = false;
    if (projectsToDelete != null && !projectsToDelete.isEmpty()) {
      for (NameKey p : projectsToDelete) {
        if (addSeparator) {
          message.append(", ");
        }
        if (!addSeparator) addSeparator = true;

        message.append(p);
      }
    }
    message.closeElement("p");

    return message.asString();
  }
}
