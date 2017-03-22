// Copyright (C) 2017 The Android Open Source Project
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
package com.google.gerrit.server.change;

import com.google.common.base.Strings;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectsCollection;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;

@Singleton
public class CherryPickCommit
    implements RestModifyView<TopLevelResource, CherryPickCommit.Input>,
        UiAction<TopLevelResource> {

  public static class Input {
    public String project;
    public String commit;
    public String message;
    public String destination;
    public Integer parent;
  }

  private final ProjectsCollection projectsCollection;
  private final Provider<ReviewDb> dbProvider;
  private final CherryPickChange cherryPickChange;
  private final ChangeJson.Factory json;

  private String projectName;
  private String commit;
  private String message;
  private String destination;
  private int parent;

  @Inject
  CherryPickCommit(
      ProjectsCollection projectsCollection,
      Provider<ReviewDb> dbProvider,
      CherryPickChange cherryPickChange,
      ChangeJson.Factory json) {
    this.projectsCollection = projectsCollection;
    this.dbProvider = dbProvider;
    this.cherryPickChange = cherryPickChange;
    this.json = json;
  }

  @Override
  public ChangeInfo apply(TopLevelResource revision, CherryPickCommit.Input input)
      throws OrmException, IOException, UpdateException, RestApiException {
    checkAndCleanInput(input);

    // TODO(xchangcheng): do we need to check if the commit is visible?
    ProjectResource rsrc = projectsCollection.parse(input.project);
    ProjectControl projectControl = rsrc.getControl();
    Capable capable = projectControl.canPushToAtLeastOneRef();
    if (capable != Capable.OK) {
      throw new AuthException(capable.getMessage());
    }
    String refName = RefNames.fullName(destination);
    RefControl refControl = rsrc.getControl().controlForRef(refName);
    if (!refControl.canUpload()) {
      throw new AuthException("Not allowed to cherry pick " + commit + " to " + input.destination);
    }

    Project.NameKey project = rsrc.getNameKey();
    try {
      Change.Id cherryPickedChangeId =
          cherryPickChange.cherryPick(
              null,
              null,
              null,
              null,
              project,
              ObjectId.fromString(commit),
              input.message,
              refName,
              refControl,
              parent);
      return json.noOptions().format(project, cherryPickedChangeId);
    } catch (InvalidChangeOperationException e) {
      throw new BadRequestException(e.getMessage());
    } catch (IntegrationException | NoSuchChangeException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }

  @Override
  public UiAction.Description getDescription(TopLevelResource resource) {
    /*new UiAction.Description()
    .setLabel("Cherry Pick")
    .setTitle("Cherry pick change to a different branch")
    .setVisible(resource.getControl().getProjectControl().canUpload() && resource.isCurrent());*/
    return null;
  }

  private void checkAndCleanInput(Input input) throws BadRequestException {
    projectName = input.project;
    commit = input.commit;
    message = input.message;
    destination = input.destination;
    parent = input.parent == null ? 1 : input.parent;

    if (projectName != null) {
      projectName = projectName.trim();
    }

    if (commit != null) {
      commit = commit.trim();
    }

    if (message != null) {
      message = message.trim();
    }

    if (destination != null) {
      destination = destination.trim();
    }

    if (Strings.isNullOrEmpty(projectName)) {
      throw new BadRequestException("project must be non-empty");
    }

    if (Strings.isNullOrEmpty(commit)) {
      throw new BadRequestException("commit must be non-empty");
    }

    if (Strings.isNullOrEmpty(message)) {
      throw new BadRequestException("message must be non-empty");
    }
    if (Strings.isNullOrEmpty(destination)) {
      throw new BadRequestException("destination must be non-empty");
    }
  }
}
