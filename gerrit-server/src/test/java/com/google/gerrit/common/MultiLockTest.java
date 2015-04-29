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

package com.google.gerrit.common;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Sets;

import org.junit.Test;

public class MultiLockTest {
  @Test
  public void testSingleResource() {
    MultiLock<String> m = new MultiLock<>();
    // do not pass the same hashset into the lock function,
    // we want to test locking by value not by reference of the set
    assertThat(m.lock(Sets.newHashSet("A"))).isTrue();
    assertThat(m.lock(Sets.newHashSet("A"))).isFalse();
    m.unlock(Sets.newHashSet("A"));
    assertThat(m.lock(Sets.newHashSet("A"))).isTrue();
  }

  @Test
  public void testPartialUnlocking() {
    MultiLock<String> m = new MultiLock<>();
    assertThat(m.lock(Sets.newHashSet("A", "B"))).isTrue();
    m.unlock(Sets.newHashSet("A"));
    m.unlock(Sets.newHashSet("B"));
    assertThat(m.lock(Sets.newHashSet("A", "B"))).isTrue();
  }

  @Test
  public void testNonConflictingResources() {
    MultiLock<String> m = new MultiLock<>();
    assertThat(m.lock(Sets.newHashSet("A", "B"))).isTrue();
    assertThat(m.lock(Sets.newHashSet("C", "D"))).isTrue();
  }

  @Test
  public void testConflictingResources() {
    MultiLock<String> m = new MultiLock<>();
    assertThat(m.lock(Sets.newHashSet("A", "B", "C"))).isTrue();
    assertThat(m.lock(Sets.newHashSet("C", "D"))).isFalse();
    // check D wasn't locked by accident:
    assertThat(m.lock(Sets.newHashSet("D"))).isTrue();
  }
}