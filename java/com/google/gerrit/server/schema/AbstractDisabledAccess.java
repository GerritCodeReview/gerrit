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

abstract class AbstractDisabledAccess<T, K extends Key<?>> implements Access<T, K> {
  private static final String GONE = "ReviewDb is gone";

  private static <T> ResultSet<T> empty() {
    return new ListResultSet<>(ImmutableList.of());
  }

  @SuppressWarnings("deprecation")
  private static <T>
      com.google.common.util.concurrent.CheckedFuture<T, OrmException> emptyFuture() {
    return Futures.immediateCheckedFuture(null);
  }

  private final NoChangesReviewDb wrapper;

  AbstractDisabledAccess(NoChangesReviewDb wrapper) {
    this.wrapper = wrapper;
  }

  @Override
  public final int getRelationID() {
    throw new UnsupportedOperationException(GONE);
  }

  @Override
  public final String getRelationName() {
    throw new UnsupportedOperationException(GONE);
  }

  @Override
  public final K primaryKey(T entity) {
    throw new UnsupportedOperationException(GONE);
  }

  @Override
  public final Map<K, T> toMap(Iterable<T> iterable) {
    throw new UnsupportedOperationException(GONE);
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
    // Do nothing.
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
