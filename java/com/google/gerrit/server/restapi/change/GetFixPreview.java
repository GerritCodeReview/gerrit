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
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.DiffWebLinkInfo;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Patch.ChangeType;
import com.google.gerrit.reviewdb.client.Patch.PatchType;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.account.Preferences.Diff;
import com.google.gerrit.server.change.FixResource;
import com.google.gerrit.server.diff.DiffCalculator;
import com.google.gerrit.server.diff.DiffSide;
import com.google.gerrit.server.diff.DiffSide.Type;
import com.google.gerrit.server.diff.DiffWebLinksProvider;
import com.google.gerrit.server.mime.FileTypeRegistry;
import com.google.gerrit.server.patch.PatchScriptBuilder;
import com.google.gerrit.server.patch.PatchScriptBuilder.PatchScriptBuilderInput;
import com.google.gerrit.server.patch.PatchScriptFactory;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.diff.Edit;

public class GetFixPreview implements RestReadView<FixResource> {
  private final ProjectCache projectCache;
  private final FileTypeRegistry ftr;

  @Inject
  GetFixPreview(
      ProjectCache projectCache,
      FileTypeRegistry ftr) {
    this.projectCache = projectCache;
    this.ftr = ftr;

  }

    @Override
  public Response<Map<String, DiffInfo>> apply(FixResource resource)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    Map<String, DiffInfo> result = new HashMap<>();
    ProjectState state = projectCache.get(resource.getRevisionResource().getChange().getProject());

    return Response.ok(result);

  }

  private List<Edit> getEdits(List<FixReplacementInfo> fixes) {
    List<Edit> result = new ArrayList<>();
    //Sort
    //result.add(new Edit(fixes))
    return result;

  }

  private DiffInfo getFixPreviewForSingleFile(ProjectState state, FixResource resource, String fileName) throws IOException

  {
    PatchScriptBuilder builder = new PatchScriptBuilder(ftr);
    PatchScriptBuilderInput input = new PatchScriptBuilderInputImpl(getEdits(fixes), fileName, ChangeType.MODIFIED);
    PatchScript ps = builder.toPatchScript(input, null, null);
    DiffSide sideA =
        new DiffSide(
            ps.getFileInfoA(),
            MoreObjects.firstNonNull(ps.getOldName(), ps.getNewName()),
            DiffSide.Type.SideA);
    DiffSide sideB = new DiffSide(ps.getFileInfoB(), ps.getNewName(), DiffSide.Type.SideB);

    DiffCalculator diffCalculator = new DiffCalculator(state, new DiffWebLinksProviderImpl(), false, true);
    diffCalculator.createDiffInfo(ps, sideA, sideB);
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
    private final ImmutableList<Edit> edits;
    private final String fileName;

    public PatchScriptBuilderInputImpl(String fileName, ChangeType changeType, ImmutableList<Edit> edits) {
      this.changeType = changeType;
      this.edits = edits;
      this.fileName = fileName;
    }

    @Override
    public ImmutableList<Edit> getEdits() {
      return edits;
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
}
