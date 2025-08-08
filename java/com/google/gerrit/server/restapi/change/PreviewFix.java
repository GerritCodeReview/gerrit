// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,//
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.stream.Collectors.groupingBy;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment.Range;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.common.ApplyProvidedFixInput;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.DiffWebLinkInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.FixResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.diff.DiffInfoCreator;
import com.google.gerrit.server.diff.DiffSide;
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
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.Repository;

public class PreviewFix {
  public interface Factory {
    PreviewFix create(RevisionResource revisionResource);
  }

  private final GitRepositoryManager repoManager;
  private final PatchScriptFactoryForAutoFix.Factory patchScriptFactoryFactory;
  private final PatchSet patchSet;
  private final ChangeNotes notes;
  private final ProjectState state;

  @Inject
  PreviewFix(
      GitRepositoryManager repoManager,
      PatchScriptFactoryForAutoFix.Factory patchScriptFactoryFactory,
      ProjectCache projectCache,
      @Assisted RevisionResource revisionResource) {
    this.repoManager = repoManager;
    this.patchScriptFactoryFactory = patchScriptFactoryFactory;
    patchSet = revisionResource.getPatchSet();
    notes = revisionResource.getNotes();
    Change change = notes.getChange();
    state = projectCache.get(change.getProject()).orElseThrow(illegalState(change.getProject()));
  }

  @Singleton
  public static class Stored implements RestReadView<FixResource> {
    private final PreviewFix.Factory previewFixFactory;

    @Inject
    Stored(PreviewFix.Factory previewFixFactory) {
      this.previewFixFactory = previewFixFactory;
    }

    @Override
    public Response<Map<String, DiffInfo>> apply(FixResource fixResource)
        throws PermissionBackendException,
            BadRequestException,
            ResourceNotFoundException,
            ResourceConflictException,
            AuthException,
            IOException,
            InvalidChangeOperationException {

      PreviewFix previewFix = previewFixFactory.create(fixResource.getRevisionResource());

      Map<String, List<FixReplacement>> fixReplacementsPerFilePath =
          fixResource.getFixReplacements().stream()
              .collect(groupingBy(fixReplacement -> fixReplacement.path));

      return Response.ok(previewFix.previewAllFiles(fixReplacementsPerFilePath));
    }
  }

  @Singleton
  public static class Provided implements RestModifyView<RevisionResource, ApplyProvidedFixInput> {
    private final PreviewFix.Factory previewFixFactory;

    @Inject
    Provided(PreviewFix.Factory previewFixFactory) {
      this.previewFixFactory = previewFixFactory;
    }

    @Override
    public Response<Map<String, DiffInfo>> apply(
        RevisionResource revisionResource, ApplyProvidedFixInput applyProvidedFixInput)
        throws BadRequestException,
            PermissionBackendException,
            ResourceNotFoundException,
            ResourceConflictException,
            AuthException,
            IOException,
            InvalidChangeOperationException {
      if (applyProvidedFixInput == null) {
        throw new BadRequestException("applyProvidedFixInput is required");
      }
      if (applyProvidedFixInput.fixReplacementInfos == null) {
        throw new BadRequestException("applyProvidedFixInput.fixReplacementInfos is required");
      }
      if (applyProvidedFixInput.originalPatchsetForFix != null
          && applyProvidedFixInput.originalPatchsetForFix > 0) {
        throw new BadRequestException(
            "applyProvidedFixInput.originalPatchsetForFix is not supported on preview.");
      }

      PreviewFix previewFix = previewFixFactory.create(revisionResource);

      Map<String, List<FixReplacement>> fixReplacementsPerFilePath =
          applyProvidedFixInput.fixReplacementInfos.stream()
              .map(fix -> new FixReplacement(fix.path, new Range(fix.range), fix.replacement))
              .collect(groupingBy(fixReplacement -> fixReplacement.path));

      return Response.ok(previewFix.previewAllFiles(fixReplacementsPerFilePath));
    }
  }

  private Map<String, DiffInfo> previewAllFiles(
      Map<String, List<FixReplacement>> fixReplacementsPerFilePath)
      throws PermissionBackendException,
          BadRequestException,
          ResourceNotFoundException,
          ResourceConflictException,
          AuthException,
          IOException,
          InvalidChangeOperationException {
    Map<String, DiffInfo> result = new HashMap<>();
    try (Repository git = repoManager.openRepository(notes.getProjectName())) {
      for (Map.Entry<String, List<FixReplacement>> entry : fixReplacementsPerFilePath.entrySet()) {
        String fileName = entry.getKey();
        DiffInfo diffInfo =
            previewSingleFile(git, fileName, ImmutableList.copyOf(entry.getValue()));
        result.put(fileName, diffInfo);
      }
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(e.getMessage(), e);
    } catch (LargeObjectException e) {
      throw new ResourceConflictException(e.getMessage(), e);
    }
    return result;
  }

  private DiffInfo previewSingleFile(
      Repository git, String fileName, ImmutableList<FixReplacement> fixReplacements)
      throws PermissionBackendException,
          AuthException,
          BadRequestException,
          LargeObjectException,
          InvalidChangeOperationException,
          IOException,
          ResourceNotFoundException {
    PatchScriptFactoryForAutoFix psf =
        patchScriptFactoryFactory.create(
            git, notes, fileName, patchSet, fixReplacements, DiffPreferencesInfo.defaults());
    PatchScript ps = psf.call();

    DiffSide sideA =
        DiffSide.create(
            ps.getFileInfoA(),
            MoreObjects.firstNonNull(ps.getOldName(), ps.getNewName()),
            DiffSide.Type.SIDE_A);
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
    public ImmutableList<WebLinkInfo> getEditWebLinks() {
      return ImmutableList.of();
    }

    @Override
    public ImmutableList<WebLinkInfo> getFileWebLinks(DiffSide.Type fileInfoType) {
      return ImmutableList.of();
    }
  }
}
