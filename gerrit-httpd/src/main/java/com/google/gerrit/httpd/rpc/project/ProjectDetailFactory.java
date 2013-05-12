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

package com.google.gerrit.httpd.rpc.project;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.common.data.UiCommandDetail;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.webui.UiCommand;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.InheritedBoolean;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.ProjectsCollection;
import com.google.gerrit.server.util.UiCommandUtil;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

class ProjectDetailFactory extends Handler<ProjectDetail> {
  private static final Logger log =
      LoggerFactory.getLogger(ProjectDetailFactory.class);

  interface Factory {
    ProjectDetailFactory create(@Assisted Project.NameKey name);
  }

  private final ProjectControl.Factory projectControlFactory;
  private final GitRepositoryManager gitRepositoryManager;
  final ProjectsCollection projectCollection;

  private final Project.NameKey projectName;

  @Inject
  ProjectDetailFactory(final ProjectControl.Factory projectControlFactory,
      final GitRepositoryManager gitRepositoryManager,
      final ProjectsCollection projectCollection,
      @Assisted final Project.NameKey name) {
    this.projectControlFactory = projectControlFactory;
    this.gitRepositoryManager = gitRepositoryManager;
    this.projectCollection = projectCollection;
    this.projectName = name;
  }

  @Override
  public ProjectDetail call() throws NoSuchProjectException, IOException {
    final ProjectControl pc =
        projectControlFactory.validateFor(projectName, ProjectControl.OWNER
            | ProjectControl.VISIBLE);
    final ProjectState projectState = pc.getProjectState();
    final ProjectDetail detail = new ProjectDetail();
    detail.setProject(projectState.getProject());

    final boolean userIsOwner = pc.isOwner();
    final boolean userIsOwnerAnyRef = pc.isOwnerAnyRef();

    detail.setCanModifyAccess(userIsOwnerAnyRef);
    detail.setCanModifyAgreements(userIsOwner);
    detail.setCanModifyDescription(userIsOwner);
    detail.setCanModifyMergeType(userIsOwner);
    detail.setCanModifyState(userIsOwner);

    final InheritedBoolean useContributorAgreements = new InheritedBoolean();
    final InheritedBoolean useSignedOffBy = new InheritedBoolean();
    final InheritedBoolean useContentMerge = new InheritedBoolean();
    final InheritedBoolean requireChangeID = new InheritedBoolean();
    useContributorAgreements.setValue(projectState.getProject()
        .getUseContributorAgreements());
    useSignedOffBy.setValue(projectState.getProject().getUseSignedOffBy());
    useContentMerge.setValue(projectState.getProject().getUseContentMerge());
    requireChangeID.setValue(projectState.getProject().getRequireChangeID());
    ProjectState parentState = Iterables.getFirst(projectState.parents(), null);
    if (parentState != null) {
      useContributorAgreements.setInheritedValue(parentState
          .isUseContributorAgreements());
      useSignedOffBy.setInheritedValue(parentState.isUseSignedOffBy());
      useContentMerge.setInheritedValue(parentState.isUseContentMerge());
      requireChangeID.setInheritedValue(parentState.isRequireChangeID());
    }
    detail.setUseContributorAgreements(useContributorAgreements);
    detail.setUseSignedOffBy(useSignedOffBy);
    detail.setUseContentMerge(useContentMerge);
    detail.setRequireChangeID(requireChangeID);

    final Project.NameKey projectName = projectState.getProject().getNameKey();
    Repository git;
    try {
      git = gitRepositoryManager.openRepository(projectName);
    } catch (RepositoryNotFoundException err) {
      throw new NoSuchProjectException(projectName);
    }
    try {
      Ref head = git.getRef(Constants.HEAD);
      if (head != null && head.isSymbolic()
          && GitRepositoryManager.REF_CONFIG.equals(head.getLeaf().getName())) {
        detail.setPermissionOnly(true);
      }
    } catch (IOException err) {
      throw new NoSuchProjectException(projectName);
    } finally {
      git.close();
    }
    // collect UiCommands only if we are signed in
    final CurrentUser user = pc.getCurrentUser();
    if (user instanceof IdentifiedUser) {
      ProjectResource proj = new ProjectResource(pc);
      detail.setCommands(buildCommands(proj));
    }

    return detail;
  }

  private List<UiCommandDetail> buildCommands(ProjectResource prj) {
    List<UiCommandDetail> all = Lists.newArrayList();
    for (DynamicMap.Entry<RestView<ProjectResource>> e : projectCollection
        .views()) {
      int d = e.getExportName().indexOf('.');
      if (d < 0) {
        continue;
      }
      String method = e.getExportName().substring(0, d);
      String name = e.getExportName().substring(d + 1);
      RestView<ProjectResource> view;
      try {
        view = e.getProvider().get();
      } catch (RuntimeException err) {
        log.error(
            String.format("error in view %s.%s", e.getPluginName(),
                e.getExportName()), err);
        continue;
      }
      if (!(view instanceof UiCommand)) {
        continue;
      }

      UiCommand<ProjectResource> cmd = (UiCommand<ProjectResource>) view;
      if (cmd.getPlace() != UiCommand.Place.PROJECT_INFO_ACTION_PANEL
          || !cmd.isVisible(prj)) {
        continue;
      }
      all.add(UiCommandUtil.createCommandDetail(e.getPluginName(), name,
          method, cmd, prj));
    }
    return UiCommandUtil.sortCommands(all);
  }
}
