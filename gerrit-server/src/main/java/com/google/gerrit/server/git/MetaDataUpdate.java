// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

/** Helps with the updating of a {@link VersionedMetaData}. */
public class MetaDataUpdate {
  public static class User {
    private final InternalFactory factory;
    private final GitRepositoryManager mgr;
    private final PersonIdent serverIdent;
    private final Provider<IdentifiedUser> identifiedUser;

    @Inject
    User(InternalFactory factory, GitRepositoryManager mgr,
        @GerritPersonIdent PersonIdent serverIdent,
        Provider<IdentifiedUser> identifiedUser) {
      this.factory = factory;
      this.mgr = mgr;
      this.serverIdent = serverIdent;
      this.identifiedUser = identifiedUser;
    }

    public PersonIdent getUserPersonIdent() {
      return createPersonIdent();
    }

    public MetaDataUpdate create(Project.NameKey name)
        throws RepositoryNotFoundException, IOException {
      MetaDataUpdate md = factory.create(name, mgr.openRepository(name));
      md.getCommitBuilder().setAuthor(createPersonIdent());
      md.getCommitBuilder().setCommitter(serverIdent);
      return md;
    }

    private PersonIdent createPersonIdent() {
      return identifiedUser.get().newCommitterIdent(
          serverIdent.getWhen(), serverIdent.getTimeZone());
    }
  }

  public static class Server {
    private final InternalFactory factory;
    private final GitRepositoryManager mgr;
    private final PersonIdent serverIdent;

    @Inject
    Server(InternalFactory factory, GitRepositoryManager mgr,
        @GerritPersonIdent PersonIdent serverIdent) {
      this.factory = factory;
      this.mgr = mgr;
      this.serverIdent = serverIdent;
    }

    public MetaDataUpdate create(Project.NameKey name)
        throws RepositoryNotFoundException, IOException {
      MetaDataUpdate md = factory.create(name, mgr.openRepository(name));
      md.getCommitBuilder().setAuthor(serverIdent);
      md.getCommitBuilder().setCommitter(serverIdent);
      return md;
    }
  }

  interface InternalFactory {
    MetaDataUpdate create(@Assisted Project.NameKey projectName,
        @Assisted Repository db);
  }

  private final GitReferenceUpdated gitRefUpdated;
  private final Project.NameKey projectName;
  private final Repository db;
  private final CommitBuilder commit;

  @Inject
  public MetaDataUpdate(GitReferenceUpdated gitRefUpdated,
      @Assisted Project.NameKey projectName, @Assisted Repository db) {
    this.gitRefUpdated = gitRefUpdated;
    this.projectName = projectName;
    this.db = db;
    this.commit = new CommitBuilder();
  }

  /** Set the commit message used when committing the update. */
  public void setMessage(String message) {
    getCommitBuilder().setMessage(message);
  }

  public void setAuthor(IdentifiedUser user) {
    getCommitBuilder().setAuthor(user.newCommitterIdent(
        getCommitBuilder().getCommitter().getWhen(),
        getCommitBuilder().getCommitter().getTimeZone()));
  }

  /** Close the cached Repository handle. */
  public void close() {
    getRepository().close();
  }

  Project.NameKey getProjectName() {
    return projectName;
  }

  Repository getRepository() {
    return db;
  }

  public CommitBuilder getCommitBuilder() {
    return commit;
  }

  void fireGitRefUpdatedEvent(RefUpdate ru) {
    gitRefUpdated.fire(projectName, ru);
  }
}
