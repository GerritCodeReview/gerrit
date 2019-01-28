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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

/**
 * Wrapper around {@link DelegateRefDatabase} that filters all refs using {@link
 * com.google.gerrit.server.permissions.PermissionBackend}.
 */
public class PermissionAwareReadOnlyRefDatabase extends DelegateRefDatabase {

  private final PermissionBackend.ForProject forProject;

  @Inject
  PermissionAwareReadOnlyRefDatabase(
      Repository delegateRepository, PermissionBackend.ForProject forProject) {
    super(delegateRepository);
    this.forProject = forProject;
  }

  @Override
  public boolean isNameConflicting(String name) {
    throw new UnsupportedOperationException("PermissionAwareReadOnlyRefDatabase is read-only");
  }

  @Override
  public RefUpdate newUpdate(String name, boolean detach) {
    throw new UnsupportedOperationException("PermissionAwareReadOnlyRefDatabase is read-only");
  }

  @Override
  public RefRename newRename(String fromName, String toName) {
    throw new UnsupportedOperationException("PermissionAwareReadOnlyRefDatabase is read-only");
  }

  @Override
  public Ref exactRef(String name) throws IOException {
    Ref ref = getDelegate().getRefDatabase().exactRef(name);
    if (ref == null) {
      return null;
    }

    Map<String, Ref> result;
    try {
      result =
          forProject.filter(ImmutableMap.of(name, ref), getDelegate(), RefFilterOptions.defaults());
    } catch (PermissionBackendException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw new IOException(e);
    }
    if (result.isEmpty()) {
      return null;
    }

    Preconditions.checkState(
        result.size() == 1, "Only one element expected, but was: " + result.size());
    return Iterables.getOnlyElement(result.values());
  }

  @Override
  public Map<String, Ref> getRefs(String prefix) throws IOException {
    Map<String, Ref> refs = getDelegate().getRefDatabase().getRefs(prefix);
    if (refs.isEmpty()) {
      return refs;
    }

    Map<String, Ref> result;
    try {
      result = forProject.filter(refs, getDelegate(), RefFilterOptions.defaults());
    } catch (PermissionBackendException e) {
      throw new IOException("");
    }
    return result;
  }

  @Override
  public List<Ref> getRefsByPrefix(String prefix) throws IOException {
    return super.getRefsByPrefix(prefix);
  }

  @Override
  public Map<String, Ref> exactRef(String... refs) throws IOException {
    return super.exactRef(refs);
  }

  @Override
  public Ref firstExactRef(String... refs) throws IOException {
    return super.firstExactRef(refs);
  }

  @Override
  public List<Ref> getAdditionalRefs() throws IOException {
    return super.getAdditionalRefs();
  }

  @Override
  public Collection<String> getConflictingNames(String name) throws IOException {
    return super.getConflictingNames(name);
  }

  @Override
  public List<Ref> getRefs() throws IOException {
    return super.getRefs();
  }

  @Override
  public List<Ref> getRefsByPrefix(String... prefixes) throws IOException {
    return super.getRefsByPrefix(prefixes);
  }

  @Override
  public Set<Ref> getTipsWithSha1(ObjectId id) throws IOException {
    return super.getTipsWithSha1(id);
  }

  @Override
  public boolean hasRefs() throws IOException {
    return super.hasRefs();
  }

  @Override
  public boolean hasVersioning() {
    return super.hasVersioning();
  }

  @Override
  public BatchRefUpdate newBatchUpdate() {
    return super.newBatchUpdate();
  }

  @Override
  public Ref peel(Ref ref) throws IOException {
    return super.peel(ref);
  }

  @Override
  public boolean performsAtomicTransactions() {
    return super.performsAtomicTransactions();
  }
}
