// Copyright (C) 2023 The Android Open Source Project
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

import static autovalue.shaded.com.google$.common.collect.$ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.server.ChangeDraftUpdate;
import com.google.gerrit.server.update.BatchUpdateListener;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.PushCertificate;

/**
 * An interface for executing updates of multiple {@link ChangeDraftUpdate} instances.
 *
 * <p>Expected usage flow:
 *
 * <ol>
 *   <li>Inject an instance of {@link AbstractFactory}.
 *   <li>Create an instance of this interface using the factory.
 *   <li>Call ({@link #queueAllDraftUpdates} or {@link #queueDeletionForChangeDrafts} for all
 *       expected updates. The changes are marked to be executed either synchronously or
 *       asynchronously.
 *   <li>Call both {@link #executeAllSyncUpdates} and {@link #executeAllAsyncUpdates} methods.
 *       Running these methods with no pending updates is a no-op.
 * </ol>
 */
public interface ChangeDraftUpdateExecutor {
  interface AbstractFactory<T extends ChangeDraftUpdateExecutor> {
    T create();
  }

  void queueAllDraftUpdates(ListMultimap<String, ChangeDraftUpdate> updaters) throws IOException;

  void queueDeletionForChangeDrafts(Change.Id id) throws IOException;

  Optional<BatchRefUpdate> executeAllSyncUpdates(
      boolean dryRun,
      ImmutableList<BatchUpdateListener> batchUpdateListeners,
      @Nullable PersonIdent refLogIdent,
      @Nullable String refLogMessage)
      throws IOException;

  void executeAllAsyncUpdates(
      @Nullable PersonIdent refLogIdent,
      @Nullable String refLogMessage,
      @Nullable PushCertificate pushCert);

  boolean isEmpty();

  default <UpdateT extends ChangeDraftUpdate> ListMultimap<String, UpdateT> filterTypedUpdaters(
      ListMultimap<String, ChangeDraftUpdate> updaters,
      Function<ChangeDraftUpdate, Boolean> isSubtype,
      Function<ChangeDraftUpdate, UpdateT> toSubtype) {
    ListMultimap<String, UpdateT> res = MultimapBuilder.hashKeys().arrayListValues().build();
    for (String key : updaters.keySet()) {
      res.putAll(
          key,
          updaters.get(key).stream()
              .filter(u -> isSubtype.apply(u))
              .map(u -> toSubtype.apply(u))
              .collect(toImmutableList()));
    }
    return res;
  }
}
