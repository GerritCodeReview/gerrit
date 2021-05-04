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

package com.google.gerrit.server.change;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

public class FileResource implements RestResource {
  public static final TypeLiteral<RestView<FileResource>> FILE_KIND =
      new TypeLiteral<RestView<FileResource>>() {};

  private final RevisionResource rev;
  private final Patch.Key key;

  public FileResource(RevisionResource rev, String name) {
    this.rev = rev;
    this.key = Patch.key(rev.getPatchSet().id(), name);
  }

  public static FileResource create(
      GitRepositoryManager repoManager, RevisionResource rev, String name)
      throws ResourceNotFoundException, IOException {
    try (Repository repo = repoManager.openRepository(rev.getProject());
        RevWalk rw1 = new RevWalk(repo);
        RevWalk rw2 = new RevWalk(repo)) {
      RevTree tree = rw1.parseCommit(rev.getPatchSet().commitId()).getTree();

      if (TreeWalk.forPath(rw1.getObjectReader(), name, tree) != null) {
        return new FileResource(rev, name);
      }
      // this should open the repository again and go to the other ps we're diffing against.
      // Doesn't work since we don't have the other ps in this class.
      // tree =
      //     rw2.parseCommit(repo.exactRef(rev.getChange().getDest().branch()).getObjectId())
      //         .getTree();
      // if (TreeWalk.forPath(rw2.getObjectReader(), name, tree) != null) {
      //   return new FileResource(rev, name);
      // }
    }
    throw new ResourceNotFoundException(IdString.fromDecoded(name));
  }

  public Patch.Key getPatchKey() {
    return key;
  }

  public boolean isCacheable() {
    return rev.isCacheable();
  }

  public Account.Id getAccountId() {
    return rev.getAccountId();
  }

  public RevisionResource getRevision() {
    return rev;
  }
}
