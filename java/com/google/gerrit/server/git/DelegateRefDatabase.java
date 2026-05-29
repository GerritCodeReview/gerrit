// Copyright (C) 2019 The Android Open Source Project
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

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;

/**
 * Wrapper around {@link RefDatabase} that delegates all calls to the wrapped {@link Repository}'s
 * {@link RefDatabase}.
 */
public class DelegateRefDatabase extends RefDatabase {

  private Repository delegate;

  public DelegateRefDatabase(Repository delegate) {
    this.delegate = delegate;
  }

  @Override
  public void create() throws IOException {
    delegate.getRefDatabase().create();
  }

  @Override
  public void close() {
    delegate.getRefDatabase().close();
  }

  @Override
  public boolean hasVersioning() {
    return delegate.getRefDatabase().hasVersioning();
  }

  @Override
  public boolean isNameConflicting(String name) throws IOException {
    return delegate.getRefDatabase().isNameConflicting(name);
  }

  @Override
  public Collection<String> getConflictingNames(String name) throws IOException {
    return delegate.getRefDatabase().getConflictingNames(name);
  }

  @Override
  public RefUpdate newUpdate(String name, boolean detach) throws IOException {
    return delegate.getRefDatabase().newUpdate(name, detach);
  }

  @Override
  public RefRename newRename(String fromName, String toName) throws IOException {
    return delegate.getRefDatabase().newRename(fromName, toName);
  }

  @Override
  public BatchRefUpdate newBatchUpdate() {
    return delegate.getRefDatabase().newBatchUpdate();
  }

  @Override
  public boolean performsAtomicTransactions() {
    return delegate.getRefDatabase().performsAtomicTransactions();
  }

  @Override
  public Ref exactRef(String name) throws IOException {
    return delegate.getRefDatabase().exactRef(name);
  }

  @Override
  public Map<String, Ref> exactRef(String... refs) throws IOException {
    return delegate.getRefDatabase().exactRef(refs);
  }

  @Override
  public Ref firstExactRef(String... refs) throws IOException {
    return delegate.getRefDatabase().firstExactRef(refs);
  }

  @Override
  public List<Ref> getRefs() throws IOException {
    return delegate.getRefDatabase().getRefs();
  }

  @SuppressWarnings("deprecation")
  @Override
  public Map<String, Ref> getRefs(String prefix) throws IOException {
    return delegate.getRefDatabase().getRefs(prefix);
  }

  @Override
  public ReflogReader getReflogReader(String refName) throws IOException {
    return delegate.getRefDatabase().getReflogReader(refName);
  }

  @Override
  @NonNull
  public ReflogReader getReflogReader(@NonNull Ref ref) throws IOException {
    return delegate.getRefDatabase().getReflogReader(ref);
  }

  @Override
  public List<Ref> getRefsByPrefix(String prefix) throws IOException {
    return delegate.getRefDatabase().getRefsByPrefix(prefix);
  }

  @Override
  public List<Ref> getRefsByPrefixWithExclusions(String include, Set<String> excludes)
      throws IOException {
    return delegate.getRefDatabase().getRefsByPrefixWithExclusions(include, excludes);
  }

  @Override
  public List<Ref> getRefsByPrefix(String... prefixes) throws IOException {
    return delegate.getRefDatabase().getRefsByPrefix(prefixes);
  }

  @Override
  @NonNull
  public Set<Ref> getTipsWithSha1(ObjectId id) throws IOException {
    return delegate.getRefDatabase().getTipsWithSha1(id);
  }

  @Override
  public boolean hasFastTipsWithSha1() throws IOException {
    return delegate.getRefDatabase().hasFastTipsWithSha1();
  }

  @Override
  public boolean hasRefs() throws IOException {
    return delegate.getRefDatabase().hasRefs();
  }

  @Override
  public List<Ref> getAdditionalRefs() throws IOException {
    return delegate.getRefDatabase().getAdditionalRefs();
  }

  @Override
  public Ref peel(Ref ref) throws IOException {
    return delegate.getRefDatabase().peel(ref);
  }

  @Override
  public void refresh() {
    delegate.getRefDatabase().refresh();
  }

  protected Repository getDelegate() {
    return delegate;
  }
}
