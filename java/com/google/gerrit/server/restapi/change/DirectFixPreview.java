// Copyright (C) 2022 The Android Open Source Project
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

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment.Range;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.DirectFixInput;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Previews fix provided as part of the request body. */
@Singleton
public class DirectFixPreview implements RestModifyView<RevisionResource, DirectFixInput> {
  private final ProjectCache projectCache;
  private final GitRepositoryManager repoManager;
  private final GetFixPreview getFixPreview;

  @Inject
  DirectFixPreview(
      ProjectCache projectCache, GitRepositoryManager repoManager, GetFixPreview getFixPreview) {
    this.projectCache = projectCache;
    this.repoManager = repoManager;
    this.getFixPreview = getFixPreview;
  }

  @Override
  public Response<Map<String, DiffInfo>> apply(
      RevisionResource revisionResource, DirectFixInput directFixInput)
      throws PermissionBackendException, ResourceNotFoundException, ResourceConflictException,
          AuthException, IOException, InvalidChangeOperationException {
    List<FixReplacement> fixReplacementList = new ArrayList<FixReplacement>();
    PatchSet patchSet = revisionResource.getPatchSet();
    ChangeNotes notes = revisionResource.getNotes();
    Change change = notes.getChange();
    ProjectState state =
        projectCache.get(change.getProject()).orElseThrow(illegalState(change.getProject()));

    for (FixReplacementInfo fix : directFixInput.fixReplacementInfos) {
      Range range = new Range(fix.range);
      FixReplacement fixReplacement = new FixReplacement(fix.path, range, fix.replacement);
      fixReplacementList.add(fixReplacement);
    }
    Map<String, List<FixReplacement>> fixReplacementsPerFilePath =
        fixReplacementList.stream().collect(groupingBy(fixReplacement -> fixReplacement.path));

    Map<String, DiffInfo> result =
        getFixPreview.applyFixPreviewForAllFiles(
            repoManager, patchSet, notes, state, fixReplacementsPerFilePath);
    return Response.ok(result);
  }
}
