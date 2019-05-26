// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.RobotComment;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;

public class RobotCommentNotes extends AbstractChangeNotes<RobotCommentNotes> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    RobotCommentNotes create(Change change);
  }

  private final Change change;

  private ImmutableListMultimap<ObjectId, RobotComment> comments;
  private RevisionNoteMap<RobotCommentsRevisionNote> revisionNoteMap;
  private ObjectId metaId;

  @Inject
  RobotCommentNotes(Args args, @Assisted Change change) {
    super(args, change.getId());
    this.change = change;
  }

  RevisionNoteMap<RobotCommentsRevisionNote> getRevisionNoteMap() {
    return revisionNoteMap;
  }

  public ImmutableListMultimap<ObjectId, RobotComment> getComments() {
    return comments;
  }

  public boolean containsComment(RobotComment c) {
    for (RobotComment existing : comments.values()) {
      if (c.key.equals(existing.key)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String getRefName() {
    return RefNames.robotCommentsRef(getChangeId());
  }

  @Nullable
  public ObjectId getMetaId() {
    return metaId;
  }

  @Override
  protected void onLoad(LoadHandle handle) throws IOException, ConfigInvalidException {
    metaId = handle.id();
    if (metaId == null) {
      loadDefaults();
      return;
    }
    metaId = metaId.copy();

    logger.atFine().log(
        "Load robot comment notes for change %s of project %s", getChangeId(), getProjectName());
    RevCommit tipCommit = handle.walk().parseCommit(metaId);
    ObjectReader reader = handle.walk().getObjectReader();
    revisionNoteMap =
        RevisionNoteMap.parseRobotComments(
            args.changeNoteJson, reader, NoteMap.read(reader, tipCommit));
    ListMultimap<ObjectId, RobotComment> cs = MultimapBuilder.hashKeys().arrayListValues().build();
    for (RobotCommentsRevisionNote rn : revisionNoteMap.revisionNotes.values()) {
      for (RobotComment c : rn.getEntities()) {
        cs.put(c.getCommitId(), c);
      }
    }
    comments = ImmutableListMultimap.copyOf(cs);
  }

  @Override
  protected void loadDefaults() {
    comments = ImmutableListMultimap.of();
  }

  @Override
  public Project.NameKey getProjectName() {
    return change.getProject();
  }
}
