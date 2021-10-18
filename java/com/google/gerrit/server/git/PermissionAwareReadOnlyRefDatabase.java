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

import static java.util.stream.Collectors.toMap;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackendException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
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

    Collection<Ref> result;
    try {
      result = forProject.filter(ImmutableList.of(ref), getDelegate(), RefFilterOptions.defaults());
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
    return Iterables.getOnlyElement(result);
  }

  // WARNING: This method is deprecated in JGit's RefDatabase and it will be removed on master.
  // Do not add any logic here but rather enrich the getRefsByPrefix method below.
  @Override
  public Map<String, Ref> getRefs(String prefix) throws IOException {
    return buildPrefixRefMap(prefix, getRefsByPrefix(prefix));
  }

  private Map<String, Ref> buildPrefixRefMap(String prefix, Collection<Ref> refs) {
    int prefixSlashPos = prefix.lastIndexOf('/') + 1;
    if (prefixSlashPos > 0) {
      return refs.stream()
          .collect(
              Collectors.toMap(
                  (Ref ref) -> ref.getName().substring(prefixSlashPos), Function.identity()));
    }

    return refs.stream().collect(toMap(Ref::getName, r -> r));
  }

  @Override
  public List<Ref> getRefsByPrefix(String prefix) throws IOException {
    List<Ref> refs = getDelegate().getRefDatabase().getRefsByPrefix(prefix);
    if (refs.isEmpty()) {
      return Collections.emptyList();
    }

    Collection<Ref> result;
    try {
      result = forProject.filter(refs, getDelegate(), RefFilterOptions.defaults());
    } catch (PermissionBackendException e) {
      throw new IOException("", e);
    }
    return result.stream().collect(Collectors.toList());
  }

  @Override
  @NonNull
  public Map<String, Ref> exactRef(String... refs) throws IOException {
    Map<String, Ref> result = new HashMap<>(refs.length);
    for (String name : refs) {
      Ref ref = exactRef(name);
      if (ref != null) {
        result.put(name, ref);
      }
    }
    return result;
  }

  @Override
  @Nullable
  public Ref firstExactRef(String... refs) throws IOException {
    for (String name : refs) {
      Ref ref = exactRef(name);
      if (ref != null) {
        return ref;
      }
    }
    return null;
  }

  @Override
  @NonNull
  public Set<Ref> getTipsWithSha1(ObjectId id) throws IOException {
    Set<Ref> unfiltered = super.getTipsWithSha1(id);
    Set<Ref> result = new HashSet<>(unfiltered.size());
    for (Ref ref : unfiltered) {
      if (exactRef(ref.getName()) != null) {
        result.add(ref);
      }
    }
    return result;
  }
}
