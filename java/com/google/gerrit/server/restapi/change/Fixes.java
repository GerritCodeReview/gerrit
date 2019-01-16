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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.FixSuggestion;
import com.google.gerrit.reviewdb.client.RobotComment;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.change.FixResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;

@Singleton
public class Fixes implements ChildCollection<RevisionResource, FixResource> {

  private final DynamicMap<RestView<FixResource>> views;
  private final CommentsUtil commentsUtil;

  @Inject
  Fixes(DynamicMap<RestView<FixResource>> views, CommentsUtil commentsUtil) {
    this.views = views;
    this.commentsUtil = commentsUtil;
  }

  @Override
  public RestView<RevisionResource> list() throws ResourceNotFoundException {
    throw new ResourceNotFoundException();
  }

  @Override
  public FixResource parse(RevisionResource revisionResource, IdString id)
      throws ResourceNotFoundException {
    String fixId = id.get();
    ChangeNotes changeNotes = revisionResource.getNotes();

    List<RobotComment> robotComments =
        commentsUtil.robotCommentsByPatchSet(changeNotes, revisionResource.getPatchSet().getId());
    for (RobotComment robotComment : robotComments) {
      for (FixSuggestion fixSuggestion : robotComment.fixSuggestions) {
        if (Objects.equals(fixId, fixSuggestion.fixId)) {
          return new FixResource(revisionResource, fixSuggestion.replacements);
        }
      }
    }
    throw new ResourceNotFoundException(id);
  }

  @Override
  public DynamicMap<RestView<FixResource>> views() {
    return views;
  }
}
