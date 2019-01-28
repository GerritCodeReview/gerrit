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

import com.google.gerrit.server.permissions.PermissionBackend;
import java.io.IOException;
import org.eclipse.jgit.attributes.AttributesNodeProvider;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;

/**
 * Wrapper around {@link Repository} that delegates all calls to the wrapped {@link Repository}
 * except for {@link #getRefDatabase()} where it provides a {@link
 * PermissionAwareReadOnlyRefDatabase}.
 */
public class PermissionAwareRepository extends Repository {

  private final Repository delegate;
  private final PermissionAwareReadOnlyRefDatabase permissionAwareReadOnlyRefDatabase;

  public PermissionAwareRepository(Repository delegate, PermissionBackend.ForProject forProject) {
    super(toBuilder(delegate));
    this.delegate = delegate;
    this.permissionAwareReadOnlyRefDatabase =
        new PermissionAwareReadOnlyRefDatabase(delegate, forProject);
  }

  @Override
  public void create(boolean bare) throws IOException {
    delegate.create(bare);
  }

  @Override
  public String getPath() {
    return delegate.getPath();
  }

  @Override
  public ObjectDatabase getObjectDatabase() {
    return delegate.getObjectDatabase();
  }

  @Override
  public RefDatabase getRefDatabase() {
    return permissionAwareReadOnlyRefDatabase;
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

  Repository unwrap() {
    return delegate;
  }

  private static BaseRepositoryBuilder toBuilder(Repository repo) {
    BaseRepositoryBuilder b = new BaseRepositoryBuilder<>();
    b.setFS(repo.getFS());
    b.setGitDir(repo.getDirectory());
    if (!repo.isBare()) {
      b.setIndexFile(repo.getIndexFile());
      b.setWorkTree(repo.getWorkTree());
    }
    return b;
  }
}
