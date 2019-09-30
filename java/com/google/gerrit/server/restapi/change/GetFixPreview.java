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
import com.google.gerrit.reviewdb.client.FixReplacement;
import com.google.gerrit.reviewdb.client.Patch.ChangeType;
import com.google.gerrit.reviewdb.client.Patch.PatchType;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.change.FixResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.diff.DiffCalculator;
import com.google.gerrit.server.diff.DiffCalculator.DiffContent;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditJson;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.tree.TreeModification;
import com.google.gerrit.server.fixes.FixReplacementInterpreter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.ComparisonType;
import com.google.gerrit.server.patch.PatchScriptBuilder;
import com.google.gerrit.server.patch.PatchScriptBuilder.BuildCoreInput;
import com.google.gerrit.server.patch.PatchScriptFactory;
import com.google.gerrit.server.patch.Text;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

public class GetFixPreview implements RestReadView<FixResource> {

  private final GitRepositoryManager gitRepositoryManager;
  private final FixReplacementInterpreter fixReplacementInterpreter;
  private final ProjectCache projectCache;
  private final Provider<PatchScriptBuilder> builderFactory;

  @Inject
  public GetFixPreview(
      GitRepositoryManager gitRepositoryManager,
      FixReplacementInterpreter fixReplacementInterpreter,
      ChangeEditModifier changeEditModifier,
      ChangeEditJson changeEditJson,
      Provider<PatchScriptBuilder> builderFactory,
      ProjectCache projectCache) {
    this.gitRepositoryManager = gitRepositoryManager;
    this.fixReplacementInterpreter = fixReplacementInterpreter;
    this.projectCache = projectCache;
    this.builderFactory = builderFactory;
  }

  @Override
  public Response<Map<String, DiffInfo>> apply(FixResource fixResource)
      throws Exception {

    Map<String, List<FixReplacement>> fixReplacementsPerFilePath =
        fixReplacementInterpreter.groupByPath(fixResource.getFixReplacements());

    RevisionResource revisionResource = fixResource.getRevisionResource();
    Project.NameKey project = revisionResource.getProject();
    ProjectState projectState = projectCache.checkedGet(project);
    // PatchSet patchSet = revisionResource.getPatchSet();

    Map<String, DiffInfo> result = new HashMap<>();
    ChangeNotes notes = revisionResource.getNotes();
    PatchSet basePatchSet = revisionResource.getPatchSet();
    try (Repository repository = gitRepositoryManager.openRepository(project)) {
      for (Map.Entry<String, List<FixReplacement>> entry : fixReplacementsPerFilePath.entrySet()) {
        result.put(entry.getKey(), createDiffInfo(repository, notes, projectState, basePatchSet, entry.getKey(), entry.getValue()));
      }
      Response<Map<String, DiffInfo>> r = Response.ok(result);
      return r;
    }
  }

  private DiffInfo createDiffInfo(Repository repository, ChangeNotes notes, ProjectState state, PatchSet basePatchSet, String fileName, List<FixReplacement> replacements)
      throws IOException, ResourceNotFoundException, ResourceConflictException {
    PatchScriptBuilder b = builderFactory.get();
    b.setRepository(repository, notes.getProjectName());
    b.setChange(notes.getChange());
    b.setDiffPrefs(new DiffPreferencesInfo());
    b.setTrees(ComparisonType.againstOtherPatchSet(), ObjectId.zeroId(), ObjectId.zeroId());
    String oldContent = fixReplacementInterpreter.getFileContent(repository, state, basePatchSet.commitId(), fileName);
    String newContent = FixReplacementInterpreter.getNewFileContent(oldContent, replacements);
    byte[] newBytes = newContent.getBytes();
    Text newText = new Text(newBytes);
    newText.
    PatchScript ps = b.toPatchScript(new FixInput(fileName, newBytes, newText, calculateEdits(oldContent, replacements)));

    String fileNameA = MoreObjects.firstNonNull(ps.getOldName(), ps.getNewName());
    String fileNameB = ps.getNewName();

    WebLinksUtils webLinksUtils = null;
    DiffCalculator diffCalculator = new DiffCalculator();

    DiffContent.DiffFileInfo fileA = new DiffContent.DiffFileInfo(ps.getFileA(), fileNameA);
    DiffContent.DiffFileInfo fileB = new DiffContent.DiffFileInfo(ps.getFileB(), fileNameB);


    DiffInfo result = diffCalculator.createDiffInfo(fileA, fileB, webLinksUtils, ps, state, false);
    return result;
  }

  private ImmutableList<Edit> calculateEdits(String oldContent, List<FixReplacement> replacements) {

  }

  private static class WebLinksUtils implements DiffCalculator.WebLinksUtils {

    @Override
    public ImmutableList<DiffWebLinkInfo> createDiffWebLinks() {
      return null;
    }

    @Override
    public List<WebLinkInfo> getFileAWebLinks() {
      return null;
    }

    @Override
    public List<WebLinkInfo> getFileBWebLinks() {
      return null;
    }
  }

  public class FixInput implements BuildCoreInput {
    private final String name;
    private final byte[] fixedBytes;
    private final Text fixedText;
    private final ImmutableList<Edit> edits;
    public FixInput(String name, byte[] fixedBytes, Text fixedText, ImmutableList<Edit> edits) {
      this.name = name;
      this.fixedBytes = fixedBytes;
      this.fixedText = fixedText;
      this.edits = edits;
    }

    @Override
    public ChangeType getChangeType() {
      return ChangeType.MODIFIED;
    }

    @Override
    public PatchType getPatchType() {
      return PatchType.UNIFIED;
    }

    @Override
    public String getOldName() {
      return name;
    }

    @Override
    public String getNewName() {
      return name;
    }

    @Override
    public List<String> getHeaderLines() {
      return null;
    }

    public ImmutableList<Edit> getEdits() {
      return edits;
    }

    public byte[] getFixedBytes() {
      return fixedBytes;
    }

    public Text getFixedText() {
      return fixedText;
    }
  }
}
