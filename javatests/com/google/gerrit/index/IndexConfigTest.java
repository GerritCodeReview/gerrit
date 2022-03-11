// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.index;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.index.IndexConfig.DEFAULT_MAX_PAGES;

import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class IndexConfigTest {
  @Test
  public void maxPagesIsLimitedByDefault() {
    // when
    IndexConfig config = IndexConfig.fromConfig(new Config()).build();

    // then
    assertThat(config.maxPages()).isEqualTo(DEFAULT_MAX_PAGES);
  }

  @Test
  public void maxPagesCanBeConfigured() {
    // given
    Config cfg = new Config();
    cfg.setInt("index", null, "maxPages", 10);

    // when
    IndexConfig config = IndexConfig.fromConfig(cfg).build();

    // then
    assertThat(config.maxPages()).isEqualTo(10);
  }

  @Test
  public void maxPagesCanBeConfiguredToUnlimited() {
    // given
    Config cfg = new Config();
    cfg.setInt("index", null, "maxPages", 0);

    // when
    IndexConfig config = IndexConfig.fromConfig(cfg).build();

    // then
    assertThat(config.maxPages()).isEqualTo(Integer.MAX_VALUE);
  }
}
