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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.client.RobotComment;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;

public class RobotCommentNotes extends AbstractChangeNotes<RobotCommentNotes> {
  public interface Factory {
    RobotCommentNotes create(Change change);
  }

  private final Change change;

  private ImmutableListMultimap<RevId, RobotComment> comments;
  private RevisionNoteMap<RobotCommentsRevisionNote> revisionNoteMap;

  @AssistedInject
  RobotCommentNotes(Args args, @Assisted Change change) {
    super(args, change.getId(), PrimaryStorage.of(change), false);
    this.change = change;
  }

  RevisionNoteMap<RobotCommentsRevisionNote> getRevisionNoteMap() {
    return revisionNoteMap;
  }

  public ImmutableListMultimap<RevId, RobotComment> getComments() {
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
  protected String getRefName() {
    return RefNames.robotCommentsRef(getChangeId());
  }

  @Override
  protected void onLoad(LoadHandle handle) throws IOException, ConfigInvalidException {
    ObjectId rev = handle.id();
    if (rev == null) {
      loadDefaults();
      return;
    }

    RevCommit tipCommit = handle.walk().parseCommit(rev);
    ObjectReader reader = handle.walk().getObjectReader();
    revisionNoteMap =
        RevisionNoteMap.parseRobotComments(args.noteUtil, reader, NoteMap.read(reader, tipCommit));
    Multimap<RevId, RobotComment> cs = ArrayListMultimap.create();
    for (RobotCommentsRevisionNote rn : revisionNoteMap.revisionNotes.values()) {
      for (RobotComment c : rn.getComments()) {
        cs.put(new RevId(c.revId), c);
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
