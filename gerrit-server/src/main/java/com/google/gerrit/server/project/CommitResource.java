// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.TypeLiteral;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.Closeable;

public class CommitResource implements RestResource, Closeable {
  public static final TypeLiteral<RestView<CommitResource>> COMMIT_KIND =
      new TypeLiteral<RestView<CommitResource>>() {};

  private final ProjectResource project;
  private final Repository repo;
  private final RevWalk rw;
  private final RevCommit commit;

  public CommitResource(ProjectResource project, Repository repo, RevWalk rw,
      RevCommit commit) {
    this.project = project;
    this.repo = repo;
    this.rw = rw;
    this.commit = commit;
  }

  public Project.NameKey getProject() {
    return project.getNameKey();
  }

  public Repository getRepository() {
    return repo;
  }

  public RevWalk getRevWalk() {
    return rw;
  }

  public RevCommit getCommit() {
    return commit;
  }

  @Override
  public void close() {
    rw.release();
    repo.close();
  }
}
