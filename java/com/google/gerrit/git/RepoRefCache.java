// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.git;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

/** {@link RefCache} backed directly by a repository. */
public class RepoRefCache implements RefCache {
  private final RefDatabase refdb;
  private final Map<String, Optional<ObjectId>> ids;

  public RepoRefCache(Repository repo) {
    this.refdb = repo.getRefDatabase();
    this.ids = new HashMap<>();
  }

  @Override
  public Optional<ObjectId> get(String refName) throws IOException {
    Optional<ObjectId> id = ids.get(refName);
    if (id != null) {
      return id;
    }
    Ref ref = refdb.exactRef(refName);
    id = Optional.ofNullable(ref).map(Ref::getObjectId);
    ids.put(refName, id);
    return id;
  }

  /** @return an unmodifiable view of the refs that have been cached by this instance. */
  public Map<String, Optional<ObjectId>> getCachedRefs() {
    return Collections.unmodifiableMap(ids);
  }
}
