// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.base.Predicate;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
/**
 * The suggest oracle may be called many times in rapid succession during the
 * course of one operation.
 * It would be easy to have a simple Cache<Boolean, List<Account>> with a short
 * expiration time of 30s.
 * Cache only has a single key we're just using Cache for the expiration behavior.
 */
@Singleton
public class ReviewerSuggestionCache {
  private static final Logger log = LoggerFactory
      .getLogger(ReviewerSuggestionCache.class);
  private final LoadingCache<Boolean, List<Account>> cache;

  @Inject
  ReviewerSuggestionCache(final Provider<ReviewDb> dbProvider) {
    this.cache =
        CacheBuilder.newBuilder().maximumSize(1)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build(new CacheLoader<Boolean, List<Account>>() {
              @Override
              public List<Account> load(Boolean key) throws Exception {
                return ImmutableList.copyOf(Iterables.filter(
                    dbProvider.get().accounts().all(),
                    new Predicate<Account>() {
                      @Override
                      public boolean apply(Account in) {
                        return in.isActive();
                      }
                    }));
              }
            });
  }

  List<Account> get() {
    try {
      return cache.get(true);
    } catch (ExecutionException e) {
      log.warn("Cannot fetch reviewers from cache", e);
      return Collections.emptyList();
    }
  }
}
