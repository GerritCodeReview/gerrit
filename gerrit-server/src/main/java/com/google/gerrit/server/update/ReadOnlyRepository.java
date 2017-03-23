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

package com.google.gerrit.server.update;

import com.google.gerrit.server.git.RepositoryWrapper;
import java.io.IOException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

public class ReadOnlyRepository extends RepositoryWrapper {
  private static final String MSG = "Cannot modify a " + ReadOnlyRepository.class.getSimpleName();

  ReadOnlyRepository(Repository delegate) {
    super(delegate);
  }

  @Override
  protected ReadOnlyRefDatabase wrapRefDatabase(RefDatabase delegate) {
    return new ReadOnlyRefDatabase(delegate);
  }

  @Override
  protected ReadOnlyObjectDatabase wrapObjectDatabase(ObjectDatabase delegate) {
    return new ReadOnlyObjectDatabase(delegate);
  }

  @Override
  public void create(boolean bare) throws IOException {
    throw new UnsupportedOperationException(MSG);
  }

  @Override
  public ReadOnlyObjectDatabase getObjectDatabase() {
    return (ReadOnlyObjectDatabase) objdb;
  }

  @Override
  public ReadOnlyRefDatabase getRefDatabase() {
    return (ReadOnlyRefDatabase) refdb;
  }

  public static class ReadOnlyRefDatabase extends RefDatabaseWrapper {
    private ReadOnlyRefDatabase(RefDatabase delegate) {
      super(delegate);
    }

    @Deprecated
    @Override
    public void create() throws IOException {
      throw new UnsupportedOperationException(MSG);
    }

    @Deprecated
    @Override
    public BatchRefUpdate newBatchUpdate() {
      throw new UnsupportedOperationException(MSG);
    }

    @Deprecated
    @Override
    public RefUpdate newUpdate(String name, boolean detach) throws IOException {
      throw new UnsupportedOperationException(MSG);
    }

    @Deprecated
    @Override
    public RefRename newRename(String fromName, String toName) throws IOException {
      throw new UnsupportedOperationException(MSG);
    }
  }

  public static class ReadOnlyObjectDatabase extends ObjectDatabaseWrapper {
    private ReadOnlyObjectDatabase(ObjectDatabase delegate) {
      super(delegate);
    }

    @Deprecated
    @Override
    public ObjectInserter newInserter() {
      throw new UnsupportedOperationException(MSG);
    }
  }
}
