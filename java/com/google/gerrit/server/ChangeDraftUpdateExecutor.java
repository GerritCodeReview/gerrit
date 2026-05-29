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

package com.google.gerrit.server;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.server.update.BatchUpdateListener;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
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
 *       asynchronously, based on {@link #canRunAsync}.
 *   <li>Call both {@link #executeAllSyncUpdates} and {@link #executeAllAsyncUpdates} methods.
 *       Running these methods with no pending updates is a no-op.
 * </ol>
 */
public interface ChangeDraftUpdateExecutor {
  interface AbstractFactory {
    // Guice cannot bind either:
    // - A parameterized entity.
    // - A factory creating an interface (rather than a class).
    // To overcome this - we declare the create method in this non-parameterized interface, then
    // extend it with a factory returning an actual class.
    ChangeDraftUpdateExecutor create(CurrentUser currentUser);
  }

  interface Factory<T extends ChangeDraftUpdateExecutor> extends AbstractFactory {
    @Override
    T create(CurrentUser currentUser);
  }

  /**
   * Queues all provided updates for later execution.
   *
   * <p>The updates are queued to either run synchronously just after change repositories updates,
   * or to run asynchronously afterwards, based on {@link #canRunAsync}.
   */
  void queueAllDraftUpdates(ListMultimap<String, ChangeDraftUpdate> updates) throws IOException;

  /**
   * Extracts all drafts (of all authors) for the given change and queue their deletion.
   *
   * <p>See {@link #canRunAsync} for whether the deletions are scheduled as synchronous or
   * asynchronous.
   */
  void queueDeletionForChangeDrafts(Change.Id id) throws IOException;

  /**
   * Execute all previously queued sync updates.
   *
   * <p>NOTE that {@link BatchUpdateListener#beforeUpdateRefs} events are not fired by this method.
   * post-update events can be fired by the caller only for implementations that return a valid
   * {@link BatchRefUpdate}.
   *
   * @param dryRun whether this is a dry run - i.e. no updates should be made
   * @param refLogIdent user to log as the update creator
   * @param refLogMessage message to put in the updates log
   * @return the executed update, if supported by the implementing class
   * @throws IOException in case of an update failure.
   */
  Optional<BatchRefUpdate> executeAllSyncUpdates(
      boolean dryRun, @Nullable PersonIdent refLogIdent, @Nullable String refLogMessage)
      throws IOException;

  /**
   * Execute all previously queued async updates.
   *
   * @param refLogIdent user to log as the update creator
   * @param refLogMessage message to put in the updates log
   * @param pushCert to use for the update
   */
  void executeAllAsyncUpdates(
      @Nullable PersonIdent refLogIdent,
      @Nullable String refLogMessage,
      @Nullable PushCertificate pushCert);

  /** Returns whether any updates are queued. */
  boolean isEmpty();

  /** Returns the given updates that match the provided type. */
  default <UpdateT extends ChangeDraftUpdate> ListMultimap<String, UpdateT> filterTypedUpdates(
      ListMultimap<String, ChangeDraftUpdate> updates, Class<UpdateT> updateType) {
    ListMultimap<String, UpdateT> res = MultimapBuilder.hashKeys().arrayListValues().build();
    for (String key : updates.keySet()) {
      res.putAll(
          key,
          updates.get(key).stream()
              .map(u -> u.toOptionalChangeDraftUpdateSubtype(updateType))
              .filter(Optional::isPresent)
              .map(Optional::get)
              .collect(toImmutableList()));
    }
    return res;
  }

  /** Returns whether all provided updates can run asynchronously. */
  default boolean canRunAsync(Collection<? extends ChangeDraftUpdate> updates) {
    return updates.stream().allMatch(u -> u.canRunAsync());
  }
}
