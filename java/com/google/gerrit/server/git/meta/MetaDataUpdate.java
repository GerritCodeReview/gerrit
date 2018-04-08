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

package com.google.gerrit.server.git.meta;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.config.GerritPersonIdent;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

/** Helps with the updating of a {@link VersionedMetaData}. */
public class MetaDataUpdate implements AutoCloseable {
  public static class User {
    private final InternalFactory factory;
    private final GitRepositoryManager mgr;
    private final PersonIdent serverIdent;
    private final Provider<IdentifiedUser> identifiedUser;

    @Inject
    User(
        InternalFactory factory,
        GitRepositoryManager mgr,
        @GerritPersonIdent PersonIdent serverIdent,
        Provider<IdentifiedUser> identifiedUser) {
      this.factory = factory;
      this.mgr = mgr;
      this.serverIdent = serverIdent;
      this.identifiedUser = identifiedUser;
    }

    public PersonIdent getUserPersonIdent() {
      return createPersonIdent(identifiedUser.get());
    }

    public MetaDataUpdate create(Project.NameKey name)
        throws RepositoryNotFoundException, IOException {
      return create(name, identifiedUser.get());
    }

    public MetaDataUpdate create(Project.NameKey name, IdentifiedUser user)
        throws RepositoryNotFoundException, IOException {
      return create(name, user, null);
    }

    /**
     * Create an update using an existing batch ref update.
     *
     * <p>This allows batching together updates to multiple metadata refs. For making multiple
     * commits to a single metadata ref, see {@link VersionedMetaData#openUpdate(MetaDataUpdate)}.
     *
     * @param name project name.
     * @param user user for the update.
     * @param batch batch update to use; the caller is responsible for committing the update.
     */
    public MetaDataUpdate create(Project.NameKey name, IdentifiedUser user, BatchRefUpdate batch)
        throws RepositoryNotFoundException, IOException {
      Repository repo = mgr.openRepository(name);
      MetaDataUpdate md = create(name, repo, user, batch);
      md.setCloseRepository(true);
      return md;
    }

    /**
     * Create an update using an existing batch ref update.
     *
     * <p>This allows batching together updates to multiple metadata refs. For making multiple
     * commits to a single metadata ref, see {@link VersionedMetaData#openUpdate(MetaDataUpdate)}.
     *
     * <p>Important: Create a new MetaDataUpdate instance for each update:
     *
     * <pre>
     * <code>
     *   try (Repository repo = repoMgr.openRepository(allUsersName);
     *       RevWalk rw = new RevWalk(repo)) {
     *     BatchRefUpdate batchUpdate = repo.getRefDatabase().newBatchUpdate();
     *     // WRONG: create the MetaDataUpdate instance here and reuse it for
     *     //        all updates in the loop
     *     for{@code (Map.Entry<Account.Id, DiffPreferencesInfo> e : diffPrefsFromDb)} {
     *       // CORRECT: create a new MetaDataUpdate instance for each update
     *       try (MetaDataUpdate md =
     *           metaDataUpdateFactory.create(allUsersName, batchUpdate)) {
     *         md.setMessage("Import diff preferences from reviewdb\n");
     *         VersionedAccountPreferences vPrefs =
     *             VersionedAccountPreferences.forUser(e.getKey());
     *         storeSection(vPrefs.getConfig(), UserConfigSections.DIFF, null,
     *             e.getValue(), DiffPreferencesInfo.defaults());
     *         vPrefs.commit(md);
     *       } catch (ConfigInvalidException e) {
     *         // TODO handle exception
     *       }
     *     }
     *     batchUpdate.execute(rw, NullProgressMonitor.INSTANCE);
     *   }
     * </code>
     * </pre>
     *
     * @param name project name.
     * @param repository the repository to update; the caller is responsible for closing the
     *     repository.
     * @param user user for the update.
     * @param batch batch update to use; the caller is responsible for committing the update.
     */
    public MetaDataUpdate create(
        Project.NameKey name, Repository repository, IdentifiedUser user, BatchRefUpdate batch) {
      MetaDataUpdate md = factory.create(name, repository, batch);
      md.getCommitBuilder().setCommitter(serverIdent);
      md.setAuthor(user);
      return md;
    }

    private PersonIdent createPersonIdent(IdentifiedUser user) {
      return user.newCommitterIdent(serverIdent.getWhen(), serverIdent.getTimeZone());
    }
  }

  public static class Server {
    private final InternalFactory factory;
    private final GitRepositoryManager mgr;
    private final PersonIdent serverIdent;

    @Inject
    Server(
        InternalFactory factory,
        GitRepositoryManager mgr,
        @GerritPersonIdent PersonIdent serverIdent) {
      this.factory = factory;
      this.mgr = mgr;
      this.serverIdent = serverIdent;
    }

    public MetaDataUpdate create(Project.NameKey name)
        throws RepositoryNotFoundException, IOException {
      return create(name, null);
    }

    /** @see User#create(Project.NameKey, IdentifiedUser, BatchRefUpdate) */
    public MetaDataUpdate create(Project.NameKey name, BatchRefUpdate batch)
        throws RepositoryNotFoundException, IOException {
      Repository repo = mgr.openRepository(name);
      MetaDataUpdate md = factory.create(name, repo, batch);
      md.setCloseRepository(true);
      md.getCommitBuilder().setAuthor(serverIdent);
      md.getCommitBuilder().setCommitter(serverIdent);
      return md;
    }
  }

  public interface InternalFactory {
    MetaDataUpdate create(
        @Assisted Project.NameKey projectName,
        @Assisted Repository repository,
        @Assisted @Nullable BatchRefUpdate batch);
  }

  private final GitReferenceUpdated gitRefUpdated;
  private final Project.NameKey projectName;
  private final Repository repository;
  private final BatchRefUpdate batch;
  private final CommitBuilder commit;
  private boolean allowEmpty;
  private boolean insertChangeId;
  private boolean closeRepository;
  private IdentifiedUser author;

  @Inject
  public MetaDataUpdate(
      GitReferenceUpdated gitRefUpdated,
      @Assisted Project.NameKey projectName,
      @Assisted Repository repository,
      @Assisted @Nullable BatchRefUpdate batch) {
    this.gitRefUpdated = gitRefUpdated;
    this.projectName = projectName;
    this.repository = repository;
    this.batch = batch;
    this.commit = new CommitBuilder();
  }

  public MetaDataUpdate(
      GitReferenceUpdated gitRefUpdated, Project.NameKey projectName, Repository repository) {
    this(gitRefUpdated, projectName, repository, null);
  }

  /** Set the commit message used when committing the update. */
  public void setMessage(String message) {
    getCommitBuilder().setMessage(message);
  }

  public void setAuthor(IdentifiedUser author) {
    this.author = author;
    getCommitBuilder()
        .setAuthor(
            author.newCommitterIdent(
                getCommitBuilder().getCommitter().getWhen(),
                getCommitBuilder().getCommitter().getTimeZone()));
  }

  public void setAllowEmpty(boolean allowEmpty) {
    this.allowEmpty = allowEmpty;
  }

  public void setInsertChangeId(boolean insertChangeId) {
    this.insertChangeId = insertChangeId;
  }

  public void setCloseRepository(boolean closeRepository) {
    this.closeRepository = closeRepository;
  }

  /** @return batch in which to run the update, or {@code null} for no batch. */
  BatchRefUpdate getBatch() {
    return batch;
  }

  /** Close the cached Repository handle. */
  @Override
  public void close() {
    if (closeRepository) {
      getRepository().close();
    }
  }

  public Project.NameKey getProjectName() {
    return projectName;
  }

  public Repository getRepository() {
    return repository;
  }

  boolean allowEmpty() {
    return allowEmpty;
  }

  boolean insertChangeId() {
    return insertChangeId;
  }

  public CommitBuilder getCommitBuilder() {
    return commit;
  }

  protected void fireGitRefUpdatedEvent(RefUpdate ru) {
    gitRefUpdated.fire(projectName, ru, author == null ? null : author.state());
  }
}
