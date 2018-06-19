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

package com.google.gerrit.acceptance;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.util.concurrent.AtomicLongMap;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.events.ChangeIndexedListener;

public class ChangeIndexedCounter implements ChangeIndexedListener {
  private final AtomicLongMap<Integer> countsByChange = AtomicLongMap.create();

  @Override
  public void onChangeIndexed(int id) {
    countsByChange.incrementAndGet(id);
  }

  @Override
  public void onChangeDeleted(int id) {
    countsByChange.incrementAndGet(id);
  }

  public void clear() {
    countsByChange.clear();
  }

  long getCount(ChangeInfo info) {
    return countsByChange.get(info._number);
  }

  public void assertReindexOf(ChangeInfo info) {
    assertReindexOf(info, 1);
  }

  public void assertReindexOf(ChangeInfo info, int expectedCount) {
    assertThat(getCount(info)).isEqualTo(expectedCount);
    assertThat(countsByChange).hasSize(1);
    clear();
  }
}
