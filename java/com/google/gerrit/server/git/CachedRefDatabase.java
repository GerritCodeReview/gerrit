// Copyright (C) 2021 The Android Open Source Project
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

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

public class CachedRefDatabase<T extends Repository & WithDelegate<?>> extends RefDatabase {
  public interface TemplateFactory<T extends Repository & WithDelegate<?>> {
    CachedRefDatabase<T> create(T repo);
  }

  interface Factory extends TemplateFactory<CachedRefRepository> {}

  private final RefByNameCache refsCache;
  private final BatchRefUpdateWithCacheUpdate.Factory batchUpdateFactory;
  private final RefUpdateWithCacheUpdate.Factory updateFactory;
  private final RefRenameWithCacheUpdate.Factory renameFactory;
  private final T repo;

  @Inject
  CachedRefDatabase(
      RefByNameCache refsCache,
      BatchRefUpdateWithCacheUpdate.Factory batchUpdateFactory,
      RefUpdateWithCacheUpdate.Factory updateFactory,
      RefRenameWithCacheUpdate.Factory renameFactory,
      @Assisted T repo) {
    this.refsCache = refsCache;
    this.batchUpdateFactory = batchUpdateFactory;
    this.updateFactory = updateFactory;
    this.renameFactory = renameFactory;
    this.repo = repo;
  }

  @Override
  public void create() throws IOException {
    repo.getDelegate().getRefDatabase().create();
  }

  @Override
  public void close() {
    repo.getDelegate().getRefDatabase().close();
  }

  @Override
  public boolean isNameConflicting(String name) throws IOException {
    return repo.getDelegate().getRefDatabase().isNameConflicting(name);
  }

  @Override
  public RefUpdate newUpdate(String name, boolean detach) throws IOException {
    return updateFactory.create(
        this, repo, repo.getDelegate().getRefDatabase().newUpdate(name, detach));
  }

  @Override
  public RefRename newRename(String fromName, String toName) throws IOException {
    return renameFactory.create(
        repo,
        repo.getDelegate().getRefDatabase().newRename(fromName, toName),
        newUpdate(fromName, false),
        newUpdate(toName, false));
  }

  @Override
  public Ref exactRef(String name) throws IOException {
    return refsCache.computeIfAbsent(
        repo.getDelegate().getIdentifier(),
        name,
        () -> Optional.ofNullable(repo.getDelegate().getRefDatabase().exactRef(name)));
  }

  @Deprecated
  @Override
  public Map<String, Ref> getRefs(String prefix) throws IOException {
    return repo.getDelegate().getRefDatabase().getRefs(prefix);
  }

  @Override
  public List<Ref> getAdditionalRefs() throws IOException {
    return repo.getDelegate().getRefDatabase().getAdditionalRefs();
  }

  @Override
  public Ref peel(Ref ref) throws IOException {
    return repo.getDelegate().getRefDatabase().peel(ref);
  }

  @Override
  public boolean hasVersioning() {
    return repo.getDelegate().getRefDatabase().hasVersioning();
  }

  @Override
  public Collection<String> getConflictingNames(String name) throws IOException {
    return repo.getDelegate().getRefDatabase().getConflictingNames(name);
  }

  @Override
  public BatchRefUpdate newBatchUpdate() {
    return batchUpdateFactory.create(repo, repo.getDelegate().getRefDatabase().newBatchUpdate());
  }

  @Override
  public boolean performsAtomicTransactions() {
    return repo.getDelegate().getRefDatabase().performsAtomicTransactions();
  }

  @Override
  public Map<String, Ref> exactRef(String... refs) throws IOException {
    return repo.getDelegate().getRefDatabase().exactRef(refs);
  }

  @Override
  public Ref firstExactRef(String... refs) throws IOException {
    return repo.getDelegate().getRefDatabase().firstExactRef(refs);
  }

  @Override
  public List<Ref> getRefs() throws IOException {
    return repo.getDelegate().getRefDatabase().getRefs();
  }

  @Override
  public List<Ref> getRefsByPrefix(String prefix) throws IOException {
    return repo.getDelegate().getRefDatabase().getRefsByPrefix(prefix);
  }

  @Override
  public List<Ref> getRefsByPrefix(String... prefixes) throws IOException {
    return repo.getDelegate().getRefDatabase().getRefsByPrefix(prefixes);
  }

  @Override
  public Set<Ref> getTipsWithSha1(ObjectId id) throws IOException {
    return repo.getDelegate().getRefDatabase().getTipsWithSha1(id);
  }

  @Override
  public boolean hasFastTipsWithSha1() throws IOException {
    return repo.getDelegate().getRefDatabase().hasFastTipsWithSha1();
  }

  @Override
  public boolean hasRefs() throws IOException {
    return repo.getDelegate().getRefDatabase().hasRefs();
  }

  @Override
  public void refresh() {
    repo.getDelegate().getRefDatabase().refresh();
  }
}
