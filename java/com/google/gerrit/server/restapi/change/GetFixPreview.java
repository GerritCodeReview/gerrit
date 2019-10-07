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
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.DiffWebLinkInfo;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.FixReplacement;
import com.google.gerrit.reviewdb.client.Patch.ChangeType;
import com.google.gerrit.reviewdb.client.Patch.PatchType;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.account.Preferences.Diff;
import com.google.gerrit.server.change.FixResource;
import com.google.gerrit.server.diff.DiffCalculator;
import com.google.gerrit.server.diff.DiffSide;
import com.google.gerrit.server.diff.DiffSide.Type;
import com.google.gerrit.server.diff.DiffWebLinksProvider;
import com.google.gerrit.server.fixes.FixCalculator;
import com.google.gerrit.server.fixes.FixCalculator.FixResult;
import com.google.gerrit.server.fixes.FixReplacementInterpreter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mime.FileTypeRegistry;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchScriptBuilder;
import com.google.gerrit.server.patch.PatchScriptBuilder.PatchScriptBuilderInput;
import com.google.gerrit.server.patch.PatchScriptBuilder.PatchSide;
import com.google.gerrit.server.patch.PatchScriptBuilder.ResolvedSides;
import com.google.gerrit.server.patch.PatchScriptBuilder.SidesResolver;
import com.google.gerrit.server.patch.PatchScriptBuilder.SidesResolverImpl;
import com.google.gerrit.server.patch.PatchScriptFactory;
import com.google.gerrit.server.patch.Text;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

public class GetFixPreview implements RestReadView<FixResource> {
  private final ProjectCache projectCache;
  private final FileTypeRegistry ftr;
  private final GitRepositoryManager repoManager;

  @Inject
  GetFixPreview(
      ProjectCache projectCache,
      FileTypeRegistry ftr,
      GitRepositoryManager repoManager) {
    this.projectCache = projectCache;
    this.ftr = ftr;
    this.repoManager = repoManager;

  }

    @Override
  public Response<Map<String, DiffInfo>> apply(FixResource resource)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    Map<String, DiffInfo> result = new HashMap<>();
    ObjectId baseId = resource.getRevisionResource().getPatchSet().commitId();
    ChangeNotes notes = resource.getRevisionResource().getNotes();
    Change change = notes.getChange();
    ProjectState state = projectCache.get(change.getProject());
    Map<String, List<FixReplacement>> fixReplacementsPerFilePath = FixReplacementInterpreter.getFixReplacementsGroupByFilePath(resource.getFixReplacements());
    try (Repository git = repoManager.openRepository(notes.getProjectName())) {
      for (Map.Entry<String, List<FixReplacement>> entry : fixReplacementsPerFilePath.entrySet()) {
        String fileName = entry.getKey();
        DiffInfo diffInfo = getFixPreviewForSingleFile(git, baseId, state, change, fileName, entry.getValue());
        result.put(fileName, diffInfo);
      }
    }
    return Response.ok(result);

  }

  private DiffInfo getFixPreviewForSingleFile(Repository git, ObjectId baseId, ProjectState state, Change change, String fileName, List<FixReplacement> fixReplacements) throws IOException {
    PatchScriptBuilder builder = new PatchScriptBuilder(ftr);
    builder.setChange(change);
    builder.setDiffPrefs(DiffPreferencesInfo.defaults());
    PreviewSidesResolverImpl sidesResolver = new PreviewSidesResolverImpl(git);
    sidesResolver.setBaseId(baseId);
    sidesResolver.setFixReplacements(fixReplacements);
    builder.setSidesResolver(sidesResolver);
    PatchScriptBuilderInput input = new PatchScriptBuilderInputImpl(fileName, ChangeType.MODIFIED, sidesResolver);//Is it ok to copy here?
    PatchScript ps = builder.toPatchScript(input, null, null);
    DiffSide sideA =
        new DiffSide(
            ps.getFileInfoA(),
            MoreObjects.firstNonNull(ps.getOldName(), ps.getNewName()),
            DiffSide.Type.SideA);
    DiffSide sideB = new DiffSide(ps.getFileInfoB(), ps.getNewName(), DiffSide.Type.SideB);

    DiffCalculator diffCalculator = new DiffCalculator(state, new DiffWebLinksProviderImpl(), false, true);
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

  private static class PatchScriptBuilderInputImpl implements PatchScriptBuilder.PatchScriptBuilderInput {
    private final ChangeType changeType;
    private final PreviewSidesResolverImpl sidesResolver;
    private final String fileName;

    public PatchScriptBuilderInputImpl(String fileName, ChangeType changeType, PreviewSidesResolverImpl sidesResolver) {
      this.changeType = changeType;
      this.sidesResolver = sidesResolver;
      this.fileName = fileName;
    }

    @Override
    public List<Edit> getEdits() {
      return sidesResolver.fixResult.edits;
    }

    @Override
    public ImmutableSet<Edit> getEditsDueToRebase() {
      return ImmutableSet.of();
    }

    @Override
    public List<String> getHeaderLines() {
      return ImmutableList.of();
    }

    @Override
    public String getNewName() {
      return changeType != ChangeType.DELETED ? fileName : null;
    }

    @Override
    public String getOldName() {
      return changeType != ChangeType.ADDED ? fileName : null;
    }

    @Override
    public ChangeType getChangeType() {
      return this.changeType;
    }

    @Override
    public PatchType getPatchType() {
      return PatchType.UNIFIED;
    }
  }
  private static class PreviewSidesResolverImpl implements SidesResolver {
    private final Repository db;
    private ObjectId baseId;
    private List<FixReplacement> fixReplacements;
    public FixResult fixResult;

    public PreviewSidesResolverImpl(Repository db) {
      this.db = db;
    }
    public void setBaseId(ObjectId baseId) {
      this.baseId = baseId;
    }
    public void setFixReplacements(List<FixReplacement> fixReplacements) {
      this.fixReplacements = fixReplacements;
    }
    @Override
    public ResolvedSides resolveSides(FileTypeRegistry ftr, String oldName, String newName)
        throws IOException {
      SidesResolverImpl impl = new SidesResolverImpl(db);
      try (ObjectReader reader = db.newObjectReader()) {
        PatchSide a = impl.resolve(ftr, reader, oldName, null, baseId, true);
        try {
          fixResult = FixCalculator.calculateFix(a.src, fixReplacements);
        } catch (Exception e) {
        }
        PatchSide b = new PatchSide(baseId, newName, ObjectId.zeroId(), a.mode, fixResult.text.getContent(), fixResult.text, a.mimeType, a.displayMethod, a.fileMode);
        return new ResolvedSides(a, b);
      }
    }
  }
}
