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

package com.google.gerrit.testutil;

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RepositoryCaseMismatchException;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;

/** Repository manager that uses in-memory repositories. */
public class InMemoryRepositoryManager implements GitRepositoryManager {
  public static InMemoryRepository newRepository(Project.NameKey name) {
    return new Repo(new TestNotesMigration(), name);
  }

  public static class Description extends DfsRepositoryDescription {
    private final Project.NameKey name;

    private Description(Project.NameKey name) {
      super(name.get());
      this.name = name;
    }

    public Project.NameKey getProject() {
      return name;
    }
  }

  public static class Repo extends InMemoryRepository {
    private String description;

    private Repo(NotesMigration notesMigration, Project.NameKey name) {
      super(new Description(name));
      // Normally, mimic the behavior of JGit FileRepository, the standard Gerrit repository
      // backend, and don't support atomic ref updates. The exception is when we're testing with
      // fused ref updates, which requires atomic ref updates to function.
      //
      // TODO(dborowitz): Change to match the behavior of JGit FileRepository after fixing
      // https://bugs.eclipse.org/bugs/show_bug.cgi?id=515678
      setPerformsAtomicTransactions(notesMigration.fuseUpdates());
    }

    @Override
    public Description getDescription() {
      return (Description) super.getDescription();
    }

    @Override
    public String getGitwebDescription() {
      return description;
    }

    @Override
    public void setGitwebDescription(String d) {
      description = d;
    }
  }

  private final NotesMigration notesMigration;
  private final Map<String, Repo> repos;

  public InMemoryRepositoryManager() {
    this(new TestNotesMigration());
  }

  @Inject
  InMemoryRepositoryManager(NotesMigration notesMigration) {
    this.notesMigration = notesMigration;
    this.repos = new HashMap<>();
  }

  @Override
  public synchronized Repo openRepository(Project.NameKey name) throws RepositoryNotFoundException {
    return get(name);
  }

  @Override
  public synchronized Repo createRepository(Project.NameKey name)
      throws RepositoryCaseMismatchException, RepositoryNotFoundException {
    Repo repo;
    try {
      repo = get(name);
      if (!repo.getDescription().getRepositoryName().equals(name.get())) {
        throw new RepositoryCaseMismatchException(name);
      }
    } catch (RepositoryNotFoundException e) {
      repo = new Repo(notesMigration, name);
      repos.put(normalize(name), repo);
    }
    return repo;
  }

  @Override
  public synchronized SortedSet<Project.NameKey> list() {
    SortedSet<Project.NameKey> names = Sets.newTreeSet();
    for (DfsRepository repo : repos.values()) {
      names.add(new Project.NameKey(repo.getDescription().getRepositoryName()));
    }
    return ImmutableSortedSet.copyOf(names);
  }

  public synchronized void deleteRepository(Project.NameKey name) {
    repos.remove(normalize(name));
  }

  private synchronized Repo get(Project.NameKey name) throws RepositoryNotFoundException {
    Repo repo = repos.get(normalize(name));
    if (repo != null) {
      repo.incrementOpen();
      return repo;
    }
    throw new RepositoryNotFoundException(name.get());
  }

  private static String normalize(Project.NameKey name) {
    return name.get().toLowerCase();
  }
}
