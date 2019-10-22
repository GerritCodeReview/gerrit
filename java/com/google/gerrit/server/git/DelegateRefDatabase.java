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
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

/**
 * Wrapper around {@link RefDatabase} that delegates all calls to the wrapped {@link RefDatabase}.
 */
public class DelegateRefDatabase extends RefDatabase {

  private Repository delegate;

  DelegateRefDatabase(Repository delegate) {
    this.delegate = delegate;
  }

  @Override
  public void create() throws IOException {
    delegate.getRefDatabase().create();
  }

  @Override
  public void close() {
    delegate.close();
  }

  @Override
  public boolean isNameConflicting(String name) throws IOException {
    return delegate.getRefDatabase().isNameConflicting(name);
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
  public Ref exactRef(String name) throws IOException {
    return delegate.getRefDatabase().exactRef(name);
  }

  @SuppressWarnings("deprecation")
  @Override
  public Map<String, Ref> getRefs(String prefix) throws IOException {
    return delegate.getRefDatabase().getRefs(prefix);
  }

  @Override
  @NonNull
  public Set<Ref> getTipsWithSha1(ObjectId id) throws IOException {
    return delegate.getRefDatabase().getTipsWithSha1(id);
  }

  @Override
  public List<Ref> getAdditionalRefs() throws IOException {
    return delegate.getRefDatabase().getAdditionalRefs();
  }

  @Override
  public Ref peel(Ref ref) throws IOException {
    return delegate.getRefDatabase().peel(ref);
  }

  Repository getDelegate() {
    return delegate;
  }
}
