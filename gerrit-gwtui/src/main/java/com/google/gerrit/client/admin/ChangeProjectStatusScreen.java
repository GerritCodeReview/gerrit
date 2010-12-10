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

import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.ChangeProjectStatusTable;
import com.google.gerrit.client.ui.ChangeProjectStatusTable.ListBoxChangeListener;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.ProjectData;
import com.google.gerrit.reviewdb.Project.Status;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.ArrayList;
import java.util.List;

public class ChangeProjectStatusScreen extends ProjectOptionsScreen {
  private ChangeProjectStatusTable projects;
  private Button saveProject;
  private List<ProjectData> projectsToUpdate;

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
    setPageTitle(Util.C.projectChangeStatusTitle());

    projectsToUpdate = new ArrayList<ProjectData>();

    projects = new ChangeProjectStatusTable();
    projects.setSavePointerId(PageLinks.ADMIN_PROJECTS);
    projects.setListBoxChangeListener(new ListBoxChangeListener() {

      @Override
      public void onChange(ProjectData projectData, Status newStatus) {
        projectData.setStatus(newStatus);
        projectsToUpdate.add(projectData);
        if (!saveProject.isEnabled()) {
          saveProject.setEnabled(true);
        }

      }
    });

    add(projects);

    saveProject = new Button(Util.C.buttonSaveProjectsStatus());
    saveProject.setEnabled(false);
    saveProject.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        doSave();
      }
    });

    add(saveProject);
  }

  private void doSave() {
    Util.PROJECT_SVC.changeProjectStatus(projectsToUpdate,
        new GerritCallback<VoidResult>() {
          @Override
          public void onSuccess(VoidResult result) {
            saveProject.setEnabled(false);
            projects.disableStatusList(projectsToUpdate);
          }
        });
  }
}
