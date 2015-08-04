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

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Key;
import com.google.inject.util.Providers;

import org.junit.Test;

public class DynamicSetTest {
  @Test
  public void testContainsWithEmpty() throws Exception {
    DynamicSet<Integer> ds = new DynamicSet<>();
    assertThat(ds.contains(2)).isFalse();
  }

  @Test
  public void testContainsTrueWithSingleElement() throws Exception {
    DynamicSet<Integer> ds = new DynamicSet<>();
    ds.add(2);

    assertThat(ds.contains(2)).isTrue();
  }

  @Test
  public void testContainsFalseWithSingleElement() throws Exception {
    DynamicSet<Integer> ds = new DynamicSet<>();
    ds.add(2);

    assertThat(ds.contains(3)).isFalse();
  }

  @Test
  public void testContainsTrueWithTwoElements() throws Exception {
    DynamicSet<Integer> ds = new DynamicSet<>();
    ds.add(2);
    ds.add(4);

    assertThat(ds.contains(4)).isTrue();
  }

  @Test
  public void testContainsFalseWithTwoElements() throws Exception {
    DynamicSet<Integer> ds = new DynamicSet<>();
    ds.add(2);
    ds.add(4);

    assertThat(ds.contains(3)).isFalse();
  }

  @Test
  public void testContainsDynamic() throws Exception {
    DynamicSet<Integer> ds = new DynamicSet<>();
    ds.add(2);

    Key<Integer> key = Key.get(Integer.class);
    ReloadableRegistrationHandle<Integer> handle = ds.add(key, Providers.of(4));

    ds.add(6);

    // At first, 4 is contained.
    assertThat(ds.contains(4)).isTrue();

    // Then we remove 4.
    handle.remove();

    // And now 4 should not longer be contained.
    assertThat(ds.contains(4)).isFalse();
  }
}
