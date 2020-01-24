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

package com.google.gerrit.server.restapi.change;

import static java.util.stream.Collectors.groupingBy;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.DiffWebLinkInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.FixResource;
import com.google.gerrit.server.diff.DiffInfoCreator;
import com.google.gerrit.server.diff.DiffSide;
import com.google.gerrit.server.diff.DiffSide.Type;
import com.google.gerrit.server.diff.DiffWebLinksProvider;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LargeObjectException;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchScriptFactoryForAutoFix;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class GetFixPreview implements RestReadView<FixResource> {

  private final ProjectCache projectCache;
  private final GitRepositoryManager repoManager;
  private final PatchScriptFactoryForAutoFix.Factory patchScriptFactoryFactory;

  @Inject
  GetFixPreview(
      ProjectCache projectCache,
      GitRepositoryManager repoManager,
      PatchScriptFactoryForAutoFix.Factory patchScriptFactoryFactory) {
    this.projectCache = projectCache;
    this.repoManager = repoManager;
    this.patchScriptFactoryFactory = patchScriptFactoryFactory;
  }

  @Override
  public Response<Map<String, DiffInfo>> apply(FixResource resource)
      throws PermissionBackendException, ResourceNotFoundException, ResourceConflictException,
          AuthException, IOException, InvalidChangeOperationException {
    Map<String, DiffInfo> result = new HashMap<>();
    PatchSet patchSet = resource.getRevisionResource().getPatchSet();
    ChangeNotes notes = resource.getRevisionResource().getNotes();
    Change change = notes.getChange();
    ProjectState state = projectCache.get(change.getProject());
    Map<String, List<FixReplacement>> fixReplacementsPerFilePath =
        resource.getFixReplacements().stream()
            .collect(groupingBy(fixReplacement -> fixReplacement.path));
    try {
      try (Repository git = repoManager.openRepository(notes.getProjectName())) {
        for (Map.Entry<String, List<FixReplacement>> entry :
            fixReplacementsPerFilePath.entrySet()) {
          String fileName = entry.getKey();
          DiffInfo diffInfo =
              getFixPreviewForSingleFile(
                  git, patchSet, state, notes, fileName, ImmutableList.copyOf(entry.getValue()));
          result.put(fileName, diffInfo);
        }
      }
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(e.getMessage(), e);
    } catch (LargeObjectException e) {
      throw new ResourceConflictException(e.getMessage(), e);
    }
    return Response.ok(result);
  }

  private DiffInfo getFixPreviewForSingleFile(
      Repository git,
      PatchSet patchSet,
      ProjectState state,
      ChangeNotes notes,
      String fileName,
      ImmutableList<FixReplacement> fixReplacements)
      throws PermissionBackendException, AuthException, LargeObjectException,
          InvalidChangeOperationException, IOException, ResourceNotFoundException {
    PatchScriptFactoryForAutoFix psf =
        patchScriptFactoryFactory.create(
            git, notes, fileName, patchSet, fixReplacements, DiffPreferencesInfo.defaults());
    PatchScript ps = psf.call();

    DiffSide sideA =
        DiffSide.create(
            ps.getFileInfoA(),
            MoreObjects.firstNonNull(ps.getOldName(), ps.getNewName()),
            Type.SIDE_A);
    DiffSide sideB = DiffSide.create(ps.getFileInfoB(), ps.getNewName(), DiffSide.Type.SIDE_B);

    DiffInfoCreator diffInfoCreator =
        new DiffInfoCreator(state, new DiffWebLinksProviderImpl(), true);
    return diffInfoCreator.create(ps, sideA, sideB);
  }

  private static class DiffWebLinksProviderImpl implements DiffWebLinksProvider {

    @Override
    public ImmutableList<DiffWebLinkInfo> getDiffLinks() {
      return ImmutableList.of();
    }

    @Override
    public ImmutableList<WebLinkInfo> getFileWebLinks(Type fileInfoType) {
      return ImmutableList.of();
    }
  }
}
