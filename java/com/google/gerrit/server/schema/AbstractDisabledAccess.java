// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.gerrit.reviewdb.server.ReviewDbWrapper;
import com.google.gwtorm.client.Key;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import java.util.Map;
import java.util.function.Function;

abstract class AbstractDisabledAccess<T, K extends Key<?>> implements Access<T, K> {
  private static <T> ResultSet<T> empty() {
    return new ListResultSet<>(ImmutableList.of());
  }

  @SuppressWarnings("deprecation")
  private static <T>
      com.google.common.util.concurrent.CheckedFuture<T, OrmException> emptyFuture() {
    return Futures.immediateCheckedFuture(null);
  }

  // Don't even hold a reference to delegate, so it's not possible to use it
  // accidentally.
  private final ReviewDbWrapper wrapper;
  private final String relationName;
  private final int relationId;
  private final Function<T, K> primaryKey;
  private final Function<Iterable<T>, Map<K, T>> toMap;

  AbstractDisabledAccess(ReviewDbWrapper wrapper, Access<T, K> delegate) {
    this.wrapper = wrapper;
    this.relationName = delegate.getRelationName();
    this.relationId = delegate.getRelationID();
    this.primaryKey = delegate::primaryKey;
    this.toMap = delegate::toMap;
  }

  @Override
  public final int getRelationID() {
    return relationId;
  }

  @Override
  public final String getRelationName() {
    return relationName;
  }

  @Override
  public final K primaryKey(T entity) {
    return primaryKey.apply(entity);
  }

  @Override
  public final Map<K, T> toMap(Iterable<T> iterable) {
    return toMap.apply(iterable);
  }

  @Override
  public final ResultSet<T> iterateAllEntities() {
    return empty();
  }

  @SuppressWarnings("deprecation")
  @Override
  public final com.google.common.util.concurrent.CheckedFuture<T, OrmException> getAsync(K key) {
    return emptyFuture();
  }

  @Override
  public final ResultSet<T> get(Iterable<K> keys) {
    return empty();
  }

  @Override
  public final void insert(Iterable<T> instances) {
    // Do nothing.
  }

  @Override
  public final void update(Iterable<T> instances) {
    // Do nothing.
  }

  @Override
  public final void upsert(Iterable<T> instances) {
    // Do nothing.
  }

  @Override
  public final void deleteKeys(Iterable<K> keys) {
    // Do nothing.
  }

  @Override
  public final void delete(Iterable<T> instances) {
    // Do nothing.
  }

  @Override
  public final void beginTransaction(K key) {
    // Keep track of when we've started a transaction so that we can avoid calling commit/rollback
    // on the underlying ReviewDb. This is just a simple arm's-length approach, and may produce
    // slightly different results from a native ReviewDb in corner cases like:
    // * beginning transactions on different tables simultaneously
    // * doing work between commit and rollback
    // These kinds of things are already misuses of ReviewDb, and shouldn't be happening in current
    // code anyway.
    checkState(!wrapper.inTransaction(), "already in transaction");
    wrapper.beginTransaction();
  }

  @Override
  public final T atomicUpdate(K key, AtomicUpdate<T> update) {
    return null;
  }

  @Override
  public final T get(K id) {
    return null;
  }
}
