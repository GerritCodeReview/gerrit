// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

public class FileResource implements RestResource {
  public static final TypeLiteral<RestView<FileResource>> FILE_KIND =
      new TypeLiteral<RestView<FileResource>>() {};

  public static FileResource create(
      GitRepositoryManager repoManager, ProjectControl project, ObjectId rev, String path)
      throws ResourceNotFoundException, IOException {
    try (Repository repo = repoManager.openRepository(project.getProject().getNameKey());
        RevWalk rw = new RevWalk(repo)) {
      RevTree tree = rw.parseTree(rev);
      if (TreeWalk.forPath(repo, path, tree) != null) {
        return new FileResource(project, rev, path);
      }
    }
    throw new ResourceNotFoundException(IdString.fromDecoded(path));
  }

  private final ProjectControl project;
  private final ObjectId rev;
  private final String path;

  public FileResource(ProjectControl project, ObjectId rev, String path) {
    this.project = project;
    this.rev = rev;
    this.path = path;
  }

  public ProjectControl getProject() {
    return project;
  }

  public ObjectId getRev() {
    return rev;
  }

  public String getPath() {
    return path;
  }
}
