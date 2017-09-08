// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.extensions.registration;

import static com.google.common.truth.Truth.assertThat;

import com.google.inject.Key;
import com.google.inject.util.Providers;
import org.junit.Test;

public class DynamicSetTest {
  // In tests for {@link DynamicSet#contains(Object)}, be sure to avoid
  // {@code assertThat(ds).contains(...) @} and
  // {@code assertThat(ds).DoesNotContains(...) @} as (since
  // {@link DynamicSet@} is not a {@link Collection@}) those boil down to
  // iterating over the {@link DynamicSet@} and checking equality instead
  // of calling {@link DynamicSet#contains(Object)}.
  // To test for {@link DynamicSet#contains(Object)}, use
  // {@code assertThat(ds.contains(...)).isTrue() @} and
  // {@code assertThat(ds.contains(...)).isFalse() @} instead.

  @Test
  public void containsWithEmpty() throws Exception {
    DynamicSet<Integer> ds = new DynamicSet<>();
    assertThat(ds.contains(2)).isFalse(); // See above comment about ds.contains
  }

  @Test
  public void containsTrueWithSingleElement() throws Exception {
    DynamicSet<Integer> ds = new DynamicSet<>();
    ds.add(2);

    assertThat(ds.contains(2)).isTrue(); // See above comment about ds.contains
  }

  @Test
  public void containsFalseWithSingleElement() throws Exception {
    DynamicSet<Integer> ds = new DynamicSet<>();
    ds.add(2);

    assertThat(ds.contains(3)).isFalse(); // See above comment about ds.contains
  }

  @Test
  public void containsTrueWithTwoElements() throws Exception {
    DynamicSet<Integer> ds = new DynamicSet<>();
    ds.add(2);
    ds.add(4);

    assertThat(ds.contains(4)).isTrue(); // See above comment about ds.contains
  }

  @Test
  public void containsFalseWithTwoElements() throws Exception {
    DynamicSet<Integer> ds = new DynamicSet<>();
    ds.add(2);
    ds.add(4);

    assertThat(ds.contains(3)).isFalse(); // See above comment about ds.contains
  }

  @Test
  public void containsDynamic() throws Exception {
    DynamicSet<Integer> ds = new DynamicSet<>();
    ds.add(2);

    Key<Integer> key = Key.get(Integer.class);
    ReloadableRegistrationHandle<Integer> handle = ds.add(key, Providers.of(4));

    ds.add(6);

    // At first, 4 is contained.
    assertThat(ds.contains(4)).isTrue(); // See above comment about ds.contains

    // Then we remove 4.
    handle.remove();

    // And now 4 should no longer be contained.
    assertThat(ds.contains(4)).isFalse(); // See above comment about ds.contains
  }
}
