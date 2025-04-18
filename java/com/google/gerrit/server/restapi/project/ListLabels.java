// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import com.google.common.collect.ImmutableCollection;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.BranchUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.LabelPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.LabelDefinitionJson;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RefPatternMatcher;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.kohsuke.args4j.Option;

public class ListLabels implements RestReadView<ProjectResource> {
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final GitRepositoryManager repoManager;

  @Inject
  public ListLabels(
      Provider<CurrentUser> user,
      PermissionBackend permissionBackend,
      GitRepositoryManager repoManager) {
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.repoManager = repoManager;
  }

  @Option(name = "--inherited", usage = "to include inherited label definitions")
  private boolean inherited;

  public ListLabels withInherited(boolean inherited) {
    this.inherited = inherited;
    return this;
  }

  @Option(
      name = "--voteable-on-ref",
      usage =
          "to include only labels where the current user has permission to vote with positive"
              + " values on the given ref")
  private String voteableOnRef;

  public ListLabels withVoteableOnRef(String ref) {
    this.voteableOnRef = ref;
    return this;
  }

  @Override
  public Response<List<LabelDefinitionInfo>> apply(ProjectResource rsrc)
      throws AuthException,
          PermissionBackendException,
          ResourceConflictException,
          RepositoryNotFoundException,
          IOException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    if (inherited) {
      List<LabelDefinitionInfo> allLabels = new ArrayList<>();
      for (ProjectState projectState : rsrc.getProjectState().treeInOrder()) {
        try {
          permissionBackend
              .currentUser()
              .project(projectState.getNameKey())
              .check(ProjectPermission.READ_CONFIG);
        } catch (AuthException e) {
          throw new AuthException(projectState.getNameKey() + ": " + e.getMessage(), e);
        }
        allLabels.addAll(listLabels(projectState));
      }
      return Response.ok(filterLabelsThatUserCanVoteOnRef(rsrc, allLabels));
    }

    permissionBackend.currentUser().project(rsrc.getNameKey()).check(ProjectPermission.READ_CONFIG);
    return Response.ok(filterLabelsThatUserCanVoteOnRef(rsrc, listLabels(rsrc.getProjectState())));
  }

  private List<LabelDefinitionInfo> listLabels(ProjectState projectState) {
    ImmutableCollection<LabelType> labelTypes =
        projectState.getConfig().getLabelSections().values();
    List<LabelDefinitionInfo> labels = new ArrayList<>(labelTypes.size());
    for (LabelType labelType : labelTypes) {
      labels.add(LabelDefinitionJson.format(projectState.getNameKey(), labelType));
    }
    labels.sort(Comparator.comparing(l -> l.name));
    return labels;
  }

  public List<LabelDefinitionInfo> filterLabelsThatUserCanVoteOnRef(
      ProjectResource rsrc, List<LabelDefinitionInfo> allLabels)
      throws PermissionBackendException,
          ResourceConflictException,
          RepositoryNotFoundException,
          IOException {
    if (voteableOnRef == null) {
      return allLabels;
    }

    String refName = RefNames.fullName(voteableOnRef);
    BranchNameKey branchNameKey = BranchNameKey.create(rsrc.getNameKey(), refName);

    // Return the same error message whether the branch doesn't exist or is not visible to the user,
    // to prevent information disclosure about branch existence
    if (!BranchUtil.branchExists(repoManager, branchNameKey)
        || !permissionBackend
            .currentUser()
            .project(rsrc.getNameKey())
            .ref(branchNameKey.branch())
            .testOrFalse(RefPermission.READ)) {
      throw new ResourceConflictException(
          String.format("ref \"%s\" not found or not visible.", branchNameKey.branch()));
    }

    List<LabelDefinitionInfo> labelsThatUserCanVoteOn = new ArrayList<>();
    for (LabelDefinitionInfo label : allLabels) {
      java.util.Optional<LabelType> lt = rsrc.getProjectState().getLabelTypes().byLabel(label.name);
      if (!lt.isPresent()) {
        continue;
      }

      LabelType labelType = lt.get();
      if (!matchesAnyRefPattern(labelType, branchNameKey.branch())) {
        continue;
      }

      // We assume that user is interested in labels that can be voted on if
      // the user is the change owner.
      Set<LabelPermission.WithValue> can =
          permissionBackend
              .currentUser()
              .project(rsrc.getNameKey())
              .ref(branchNameKey.branch())
              .changeToBeCreated(/* isOwner= */ true)
              .test(labelType);

      for (LabelValue v : labelType.getValues()) {
        boolean ok = can.contains(new LabelPermission.WithValue(labelType, v));
        if (ok && v.getValue() > 0) {
          labelsThatUserCanVoteOn.add(label);
          break;
        }
      }
    }

    return labelsThatUserCanVoteOn;
  }

  private boolean matchesAnyRefPattern(LabelType labelType, String branchName) {
    if (labelType.getRefPatterns() == null) {
      return true;
    }

    for (String refPattern : labelType.getRefPatterns()) {
      if (RefPatternMatcher.getMatcher(refPattern).match(branchName, null)) {
        return true;
      }
    }
    return false;
  }
}
