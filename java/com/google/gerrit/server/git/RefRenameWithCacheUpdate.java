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
import java.util.EnumSet;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;

class RefRenameWithCacheUpdate extends RefRename {
  interface Factory {
    RefRenameWithCacheUpdate create(
        Repository repo,
        RefRename delegate,
        @Assisted("src") RefUpdate src,
        @Assisted("dst") RefUpdate dst);
  }

  private static final String NOT_SUPPORTED_MSG = "Should never be called";
  private static final EnumSet<Result> SUCCESSFUL_RENAMES =
      EnumSet.of(Result.NEW, Result.FORCED, Result.FAST_FORWARD, Result.RENAMED);

  private final RefByNameCache refsCache;
  private final Repository repo;
  private final RefRename delegate;
  private final RefUpdate src;

  @Inject
  RefRenameWithCacheUpdate(
      RefByNameCache refsCache,
      @Assisted Repository repo,
      @Assisted RefRename delegate,
      @Assisted("src") RefUpdate src,
      @Assisted("dst") RefUpdate dst) {
    super(src, dst);
    this.refsCache = refsCache;
    this.repo = repo;
    this.delegate = delegate;
    this.src = src;
  }

  @Override
  public PersonIdent getRefLogIdent() {
    return delegate.getRefLogIdent();
  }

  @Override
  public void setRefLogIdent(PersonIdent pi) {
    delegate.setRefLogIdent(pi);
  }

  @Override
  public String getRefLogMessage() {
    return delegate.getRefLogMessage();
  }

  @Override
  public void setRefLogMessage(String msg) {
    // Method is called from the super constructor and delegate is not available yet therefore
    // 'null' has to be checked. It doesn't matter as wrapped instance is properly constructed and
    // its ref log message is used.
    if (delegate != null) {
      delegate.setRefLogMessage(msg);
    }
  }

  @Override
  public void disableRefLog() {
    delegate.disableRefLog();
  }

  @Override
  public Result getResult() {
    return delegate.getResult();
  }

  @Override
  public Result rename() throws IOException {
    Result r = delegate.rename();
    if (SUCCESSFUL_RENAMES.contains(r)) {
      refsCache.evict(repo.getIdentifier(), src.getName());
    }
    return r;
  }

  @Override
  protected Result doRename() throws IOException {
    throw new UnsupportedOperationException(NOT_SUPPORTED_MSG);
  }
}
