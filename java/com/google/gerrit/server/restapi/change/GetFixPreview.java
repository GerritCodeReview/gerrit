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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.DiffWebLinkInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.FixReplacement;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.change.FixResource;
import com.google.gerrit.server.diff.DiffCalculator;
import com.google.gerrit.server.diff.DiffSide;
import com.google.gerrit.server.diff.DiffSide.Type;
import com.google.gerrit.server.diff.DiffWebLinksProvider;
import com.google.gerrit.server.fixes.FixReplacementInterpreter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LargeObjectException;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchScriptFactory;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetFixPreview implements RestReadView<FixResource> {

  private final ProjectCache projectCache;
  private final GitRepositoryManager repoManager;
  private final PatchScriptFactory.Factory patchScriptFactoryFactory;

  @Inject
  GetFixPreview(
      ProjectCache projectCache,
      GitRepositoryManager repoManager,
      PatchScriptFactory.Factory patchScriptFactoryFactory) {
    this.projectCache = projectCache;
    this.repoManager = repoManager;
    this.patchScriptFactoryFactory = patchScriptFactoryFactory;
  }

  @Override
  public Response<Map<String, DiffInfo>> apply(FixResource resource)
      throws PermissionBackendException, AuthException, BadRequestException,
          ResourceConflictException, ResourceNotFoundException, IOException,
          InvalidChangeOperationException {
    Map<String, DiffInfo> result = new HashMap<>();
    PatchSet patchSet = resource.getRevisionResource().getPatchSet();
    ChangeNotes notes = resource.getRevisionResource().getNotes();
    Change change = notes.getChange();
    ProjectState state = projectCache.get(change.getProject());
    Map<String, List<FixReplacement>> fixReplacementsPerFilePath =
        FixReplacementInterpreter.getFixReplacementsGroupByFilePath(resource.getFixReplacements());
    try {
      for (Map.Entry<String, List<FixReplacement>> entry : fixReplacementsPerFilePath.entrySet()) {
        String fileName = entry.getKey();
        DiffInfo diffInfo =
            getFixPreviewForSingleFile(patchSet, state, notes, fileName, entry.getValue());
        result.put(fileName, diffInfo);
      }
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(e.getMessage(), e);
    } catch (LargeObjectException e) {
      throw new ResourceConflictException(e.getMessage(), e);
    }
    return Response.ok(result);
  }

  private DiffInfo getFixPreviewForSingleFile(
      PatchSet patchSet,
      ProjectState state,
      ChangeNotes notes,
      String fileName,
      List<FixReplacement> fixReplacements)
      throws PermissionBackendException, AuthException, LargeObjectException,
          InvalidChangeOperationException, IOException {
    PatchScriptFactory psf =
        patchScriptFactoryFactory.create(
            notes, fileName, patchSet, fixReplacements, DiffPreferencesInfo.defaults());
    psf.setLoadHistory(false);
    psf.setLoadComments(false);
    PatchScript ps = psf.call();

    DiffSide sideA =
        new DiffSide(
            ps.getFileInfoA(),
            MoreObjects.firstNonNull(ps.getOldName(), ps.getNewName()),
            DiffSide.Type.SideA);
    DiffSide sideB = new DiffSide(ps.getFileInfoB(), ps.getNewName(), DiffSide.Type.SideB);

    DiffCalculator diffCalculator = new DiffCalculator(state, new DiffWebLinksProviderImpl(), true);
    return diffCalculator.createDiffInfo(ps, sideA, sideB);
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
