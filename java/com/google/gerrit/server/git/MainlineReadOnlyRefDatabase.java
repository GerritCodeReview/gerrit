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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

/** Read only ref DB that prefixes all refs with refs/origin/. */
public class MainlineReadOnlyRefDatabase extends DelegateRefDatabase {

  MainlineReadOnlyRefDatabase(Repository delegateRepository) {
    super(delegateRepository);
  }

  @Override
  public boolean isNameConflicting(String name) {
    throw new UnsupportedOperationException("PermissionAwareReadOnlyRefDatabase is read-only");
  }

  @Override
  public Collection<String> getConflictingNames(String name) throws IOException {
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
  public BatchRefUpdate newBatchUpdate() {
    throw new UnsupportedOperationException("PermissionAwareReadOnlyRefDatabase is read-only");
  }

  @Nullable
  @Override
  public Ref exactRef(String name) throws IOException {
    Ref r = getDelegate().exactRef(toMainlineName(name));
    if (r == null) {
      return null;
    }
    return prefix(r);
  }

  private static String toMainlineName(String refName) {
    return "refs/" + refName.substring("refs/origin/".length());
  }

  private static String fromMainlineName(String refName) {
    return "refs/origin/" + refName.substring("refs/".length());
  }

  @Nullable
  private static Ref prefix(@Nullable Ref ref) {
    if (ref == null) {
      return null;
    }

    return new Ref() {
      @Override
      public String getName() {
        return fromMainlineName(ref.getName());
      }

      @Override
      public boolean isSymbolic() {
        return ref.isSymbolic();
      }

      @Override
      public Ref getLeaf() {
        return ref.getLeaf();
      }

      @Override
      public Ref getTarget() {
        throw new UnsupportedOperationException("not implemented");
      }

      @Override
      public ObjectId getObjectId() {
        return ref.getObjectId();
      }

      @Override
      public ObjectId getPeeledObjectId() {
        return ref.getPeeledObjectId();
      }

      @Override
      public boolean isPeeled() {
        return ref.isPeeled();
      }

      @Override
      public Storage getStorage() {
        return ref.getStorage();
      }
    };
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
    return getDelegate().getRefDatabase().getRefsByPrefix(toMainlineName(prefix)).stream()
        .map(r -> prefix(r))
        .collect(Collectors.toList());
  }

  @Override
  public List<Ref> getRefsByPrefixWithExclusions(String include, Set<String> excludes)
      throws IOException {
    Stream<Ref> refs = getRefs(include).values().stream();
    for (String exclude : excludes) {
      refs = refs.filter(r -> !r.getName().startsWith(exclude));
    }
    return Collections.unmodifiableList(refs.map(r -> prefix(r)).collect(Collectors.toList()));
  }

  @Override
  public List<Ref> getRefsByPrefix(String... prefixes) throws IOException {
    List<Ref> result = new ArrayList<>();
    for (String prefix : prefixes) {
      result.addAll(getRefsByPrefix(prefix));
    }
    return Collections.unmodifiableList(result);
  }

  @Override
  @NonNull
  public Map<String, Ref> exactRef(String... refs) throws IOException {
    Map<String, Ref> result = new HashMap<>(refs.length);
    for (String name : refs) {
      Ref ref = exactRef(toMainlineName(name));
      if (ref != null) {
        result.put(name, prefix(ref));
      }
    }
    return result;
  }

  @Override
  @Nullable
  public Ref firstExactRef(String... refs) throws IOException {
    for (String name : refs) {
      Ref ref = exactRef(toMainlineName(name));
      if (ref != null) {
        return prefix(ref);
      }
    }
    return null;
  }

  @Override
  public List<Ref> getRefs() throws IOException {
    return getRefsByPrefix(ALL);
  }

  @Override
  public boolean hasRefs() throws IOException {
    return !getRefs().isEmpty();
  }
}
