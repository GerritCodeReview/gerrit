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

import com.google.gerrit.common.UsedAt;
import java.io.IOException;
import org.eclipse.jgit.attributes.AttributesNodeProvider;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;

/** Wrapper around {@link Repository} that delegates all calls to the wrapped {@link Repository}. */
@UsedAt(UsedAt.Project.PLUGINS_ALL)
public class DelegateRepository extends Repository {

  private final Repository delegate;

  protected DelegateRepository(Repository delegate) {
    super(toBuilder(delegate));
    this.delegate = delegate;
  }

  @Override
  public void create(boolean bare) throws IOException {
    delegate.create(bare);
  }

  @Override
  public String getIdentifier() {
    return delegate.getIdentifier();
  }

  @Override
  public ObjectDatabase getObjectDatabase() {
    return delegate.getObjectDatabase();
  }

  @Override
  public RefDatabase getRefDatabase() {
    return delegate.getRefDatabase();
  }

  @Override
  public StoredConfig getConfig() {
    return delegate.getConfig();
  }

  @Override
  public AttributesNodeProvider createAttributesNodeProvider() {
    return delegate.createAttributesNodeProvider();
  }

  @Override
  public void scanForRepoChanges() throws IOException {
    delegate.scanForRepoChanges();
  }

  @Override
  public void notifyIndexChanged(boolean internal) {
    delegate.notifyIndexChanged(internal);
  }

  @Override
  public ReflogReader getReflogReader(String refName) throws IOException {
    return delegate.getReflogReader(refName);
  }

  @SuppressWarnings("rawtypes")
  private static BaseRepositoryBuilder toBuilder(Repository repo) {
    if (!repo.isBare()) {
      throw new IllegalArgumentException(
          "non-bare repository is not supported: " + repo.getIdentifier());
    }

    return new BaseRepositoryBuilder<>().setFS(repo.getFS()).setGitDir(repo.getDirectory());
  }
}
