// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.attributes.AttributesNodeProvider;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;

class ReadOnlyRepository extends Repository {
  private static final String MSG = "Cannot modify a " + ReadOnlyRepository.class.getSimpleName();

  private static BaseRepositoryBuilder<?, ?> builder(Repository r) {
    checkNotNull(r);
    BaseRepositoryBuilder<?, ?> builder =
        new BaseRepositoryBuilder<>().setFS(r.getFS()).setGitDir(r.getDirectory());

    if (!r.isBare()) {
      builder.setWorkTree(r.getWorkTree()).setIndexFile(r.getIndexFile());
    }
    return builder;
  }

  private final Repository delegate;
  private final RefDb refdb;
  private final ObjDb objdb;

  ReadOnlyRepository(Repository delegate) {
    super(builder(delegate));
    this.delegate = delegate;
    this.refdb = new RefDb(delegate.getRefDatabase());
    this.objdb = new ObjDb(delegate.getObjectDatabase());
  }

  @Override
  public void create(boolean bare) throws IOException {
    throw new UnsupportedOperationException(MSG);
  }

  @Override
  public ObjectDatabase getObjectDatabase() {
    return objdb;
  }

  @Override
  public RefDatabase getRefDatabase() {
    return refdb;
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
  public void notifyIndexChanged() {
    delegate.notifyIndexChanged();
  }

  @Override
  public ReflogReader getReflogReader(String refName) throws IOException {
    return delegate.getReflogReader(refName);
  }

  private static class RefDb extends RefDatabase {
    private final RefDatabase delegate;

    private RefDb(RefDatabase delegate) {
      this.delegate = checkNotNull(delegate);
    }

    @Override
    public void create() throws IOException {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public void close() {
      delegate.close();
    }

    @Override
    public boolean isNameConflicting(String name) throws IOException {
      return delegate.isNameConflicting(name);
    }

    @Override
    public RefUpdate newUpdate(String name, boolean detach) throws IOException {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public RefRename newRename(String fromName, String toName) throws IOException {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public Ref getRef(String name) throws IOException {
      return delegate.getRef(name);
    }

    @Override
    public Map<String, Ref> getRefs(String prefix) throws IOException {
      return delegate.getRefs(prefix);
    }

    @Override
    public List<Ref> getAdditionalRefs() throws IOException {
      return delegate.getAdditionalRefs();
    }

    @Override
    public Ref peel(Ref ref) throws IOException {
      return delegate.peel(ref);
    }
  }

  private static class ObjDb extends ObjectDatabase {
    private final ObjectDatabase delegate;

    private ObjDb(ObjectDatabase delegate) {
      this.delegate = checkNotNull(delegate);
    }

    @Override
    public ObjectInserter newInserter() {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ObjectReader newReader() {
      return delegate.newReader();
    }

    @Override
    public void close() {
      delegate.close();
    }
  }
}
