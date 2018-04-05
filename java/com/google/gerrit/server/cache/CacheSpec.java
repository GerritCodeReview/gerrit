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

package com.google.gerrit.server.cache;

import com.google.auto.value.AutoValue;

/**
 * Immutable representation of the configuration associated with a specific {@link CacheBinding}.
 *
 * <p>Similar in spirit to {@link com.google.common.cache.CacheBuilderSpec}, but supports a
 * different set of configuration options. Only a small subset of {@code CacheBuilderSpec}'s
 * properties are supported, and there are additional properties not part of the Guava cache spec.
 */
@AutoValue
public abstract class CacheSpec {
  public static Builder builder() {
    return new AutoValue_CacheSpec.Builder();
  }

  /**
   * Create a spec from an immutable snapshot of the binding's current state.
   *
   * <p>In the case of a mutable binding such as {@link CacheProvider}, later mutations will not be
   * reflected.
   *
   * @param binding the cache binding.
   * @return immutable spec.
   */
  static CacheSpec create(CacheBinding<?, ?> binding) {
    return builder().maximumWeight(binding.maximumWeight()).diskLimit(binding.diskLimit()).build();
  }

  public abstract long maximumWeight();

  public abstract long diskLimit();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder maximumWeight(long maximumWeight);

    public abstract Builder diskLimit(long diskLimit);

    public abstract CacheSpec build();
  }
}
