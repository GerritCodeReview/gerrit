// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.realm.guice;

import static com.google.common.collect.Iterables.transform;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.gerrit.realm.RealmProvider;

public final class RealmProviderUtil {

  private static final Function<RealmProvider, String> TRANSFORM_REALM_ROVIDER_TO_ITS_NAME =
      new Function<RealmProvider, String>() {
        @Override
        @Nullable
        public String apply(@Nullable RealmProvider input) {
          return input.getName();
        }
      };

  public static Iterable<String> getNames(
      Iterable<RealmProvider> realmProviders) {
    return transform(realmProviders, TRANSFORM_REALM_ROVIDER_TO_ITS_NAME);
  }

  public static Optional<RealmProvider> getByName(
      Iterable<RealmProvider> realmProviders, final String name) {
    return Iterables.tryFind(realmProviders, new Predicate<RealmProvider>() {
      @Override
      public boolean apply(@Nullable RealmProvider input) {
        return input != null && name.equalsIgnoreCase(input.getName());
      }
    });
  }

  private RealmProviderUtil() {
    // limit access
  }
}
