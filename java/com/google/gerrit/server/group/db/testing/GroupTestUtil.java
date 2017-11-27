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

package com.google.gerrit.server.group.db.testing;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.server.group.db.GroupNameNotes.getGroupReference;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.CommitUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import java.io.IOException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

/** Test utilities for low-level NoteDb groups. */
public class GroupTestUtil {
  public static ImmutableMap<String, String> readNameToUuidMap(Repository repo) throws Exception {
    ImmutableMap.Builder<String, String> result = ImmutableMap.builder();
    try (RevWalk rw = new RevWalk(repo)) {
      Ref ref = repo.exactRef(RefNames.REFS_GROUPNAMES);
      if (ref != null) {
        NoteMap noteMap = NoteMap.read(rw.getObjectReader(), rw.parseCommit(ref.getObjectId()));
        for (Note note : noteMap) {
          GroupReference gr = getGroupReference(rw.getObjectReader(), note.getData());
          result.put(gr.getName(), gr.getUUID().get());
        }
      }
    }
    return result.build();
  }

  // TODO(dborowitz): Move somewhere even more common.
  public static ImmutableList<CommitInfo> log(Repository repo, String refName) throws Exception {
    try (RevWalk rw = new RevWalk(repo)) {
      Ref ref = repo.exactRef(refName);
      if (ref != null) {
        rw.sort(RevSort.REVERSE);
        rw.markStart(rw.parseCommit(ref.getObjectId()));
        return Streams.stream(rw)
            .map(
                c -> {
                  try {
                    return CommitUtil.toCommitInfo(c);
                  } catch (IOException e) {
                    throw new IllegalStateException(
                        "unexpected state when converting commit " + c.getName(), e);
                  }
                })
            .collect(toImmutableList());
      }
    }
    return ImmutableList.of();
  }

  public static void updateGroupFile(
      GitRepositoryManager repoManager,
      AllUsersName allUsers,
      PersonIdent serverIdent,
      String refname,
      String filename,
      String contents)
      throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      updateGroupFile(repo, serverIdent, refname, filename, contents);
    }
  }

  public static void updateGroupFile(
      Repository allUsers,
      PersonIdent serverIdent,
      String refname,
      String filename,
      String contents)
      throws Exception {
    try (RevWalk rw = new RevWalk(allUsers)) {
      TestRepository<Repository> testRepository = new TestRepository<>(allUsers, rw);
      TestRepository.CommitBuilder builder =
          testRepository
              .branch(refname)
              .commit()
              .add(filename, contents)
              .message("update group file")
              .author(serverIdent)
              .committer(serverIdent);

      Ref ref = allUsers.exactRef(refname);
      if (ref != null) {
        RevCommit c = rw.parseCommit(ref.getObjectId());
        if (c != null) {
          builder.parent(c);
        }
      }
      builder.create();
    }
  }

  private GroupTestUtil() {}
}
