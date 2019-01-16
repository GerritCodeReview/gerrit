// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

@AutoValue
public abstract class IntBlob {
  public static Optional<IntBlob> parse(Repository repo, String refName)
      throws IOException, StorageException {
    try (ObjectReader or = repo.newObjectReader()) {
      return parse(repo, refName, or);
    }
  }

  public static Optional<IntBlob> parse(Repository repo, String refName, RevWalk rw)
      throws IOException, StorageException {
    return parse(repo, refName, rw.getObjectReader());
  }

  private static Optional<IntBlob> parse(Repository repo, String refName, ObjectReader or)
      throws IOException, StorageException {
    Ref ref = repo.exactRef(refName);
    if (ref == null) {
      return Optional.empty();
    }
    ObjectId id = ref.getObjectId();
    ObjectLoader ol = or.open(id, OBJ_BLOB);
    if (ol.getType() != OBJ_BLOB) {
      // In theory this should be thrown by open but not all implementations may do it properly
      // (certainly InMemoryRepository doesn't).
      throw new IncorrectObjectTypeException(id, OBJ_BLOB);
    }
    String str = CharMatcher.whitespace().trimFrom(new String(ol.getCachedBytes(), UTF_8));
    Integer value = Ints.tryParse(str);
    if (value == null) {
      throw new StorageException("invalid value in " + refName + " blob at " + id.name());
    }
    return Optional.of(IntBlob.create(id, value));
  }

  public static RefUpdate tryStore(
      Repository repo,
      RevWalk rw,
      Project.NameKey projectName,
      String refName,
      @Nullable ObjectId oldId,
      int val,
      GitReferenceUpdated gitRefUpdated)
      throws IOException {
    ObjectId newId;
    try (ObjectInserter ins = repo.newObjectInserter()) {
      newId = ins.insert(OBJ_BLOB, Integer.toString(val).getBytes(UTF_8));
      ins.flush();
    }
    RefUpdate ru = repo.updateRef(refName);
    if (oldId != null) {
      ru.setExpectedOldObjectId(oldId);
    }
    ru.disableRefLog();
    ru.setNewObjectId(newId);
    ru.setForceUpdate(true); // Required for non-commitish updates.
    RefUpdate.Result result = ru.update(rw);
    if (refUpdated(result)) {
      gitRefUpdated.fire(projectName, ru, null);
    }
    return ru;
  }

  public static void store(
      Repository repo,
      RevWalk rw,
      Project.NameKey projectName,
      String refName,
      @Nullable ObjectId oldId,
      int val,
      GitReferenceUpdated gitRefUpdated)
      throws IOException {
    RefUpdateUtil.checkResult(tryStore(repo, rw, projectName, refName, oldId, val, gitRefUpdated));
  }

  private static boolean refUpdated(RefUpdate.Result result) {
    return result == RefUpdate.Result.NEW || result == RefUpdate.Result.FORCED;
  }

  @VisibleForTesting
  static IntBlob create(AnyObjectId id, int value) {
    return new AutoValue_IntBlob(id.copy(), value);
  }

  public abstract ObjectId id();

  public abstract int value();
}
