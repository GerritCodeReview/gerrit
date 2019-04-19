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

package com.google.gerrit.testing;

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RepositoryCaseMismatchException;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;

/** Repository manager that uses in-memory repositories. */
public class InMemoryRepositoryManager implements GitRepositoryManager {
  public static InMemoryRepository newRepository(Project.NameKey name) {
    return new Repo(name);
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

    private Repo(Project.NameKey name) {
      super(new Description(name));
      setPerformsAtomicTransactions(true);
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

  private final Map<String, Repo> repos;

  @Inject
  public InMemoryRepositoryManager() {
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
      repo = new Repo(name);
      repos.put(normalize(name), repo);
    }
    return repo;
  }

  @Override
  public synchronized SortedSet<Project.NameKey> list() {
    SortedSet<Project.NameKey> names = Sets.newTreeSet();
    for (DfsRepository repo : repos.values()) {
      names.add(Project.nameKey(repo.getDescription().getRepositoryName()));
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
