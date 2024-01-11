// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.events;

import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.patch.DiffOperationsForCommitValidation;
import java.io.IOException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

public class CommitReceivedEvent extends RefEvent implements AutoCloseable {
  static final String TYPE = "commit-received";
  public ReceiveCommand command;
  public Project project;
  public String refName;
  public ImmutableListMultimap<String, String> pushOptions;
  public Config repoConfig;
  public RevWalk revWalk;
  public RevCommit commit;
  public IdentifiedUser user;

  /**
   * Use this for computing the modified files of the received commits. Using {@link
   * com.google.gerrit.server.patch.DiffOperations} from commit validators is not safe, see javadoc
   * on {@link DiffOperationsForCommitValidation}.
   */
  public DiffOperationsForCommitValidation diffOperations;

  public CommitReceivedEvent() {
    super(TYPE);
  }

  public CommitReceivedEvent(
      ReceiveCommand command,
      Project project,
      String refName,
      ImmutableListMultimap<String, String> pushOptions,
      Config repoConfig,
      ObjectReader reader,
      ObjectId commitId,
      IdentifiedUser user,
      DiffOperationsForCommitValidation diffOperations)
      throws IOException {
    this();
    this.command = command;
    this.project = project;
    this.refName = refName;
    this.pushOptions = pushOptions;
    this.repoConfig = repoConfig;
    this.revWalk = new RevWalk(reader);
    this.commit = revWalk.parseCommit(commitId);
    this.user = user;
    this.diffOperations = diffOperations;
    revWalk.parseBody(commit);
  }

  @Override
  public Project.NameKey getProjectNameKey() {
    return project.getNameKey();
  }

  @Override
  public String getRefName() {
    return refName;
  }

  @Override
  public void close() {
    revWalk.close();
  }
}
