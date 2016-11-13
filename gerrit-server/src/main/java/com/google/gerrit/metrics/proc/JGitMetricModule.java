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

package com.google.gerrit.metrics.proc;

import com.google.common.base.Supplier;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.MetricMaker;
import org.eclipse.jgit.internal.storage.file.WindowCacheStatAccessor;

public class JGitMetricModule extends MetricModule {
  @Override
  protected void configure(MetricMaker metrics) {
    metrics.newCallbackMetric(
        "jgit/block_cache/cache_used",
        Long.class,
        new Description("Bytes of memory retained in JGit block cache.")
            .setGauge()
            .setUnit(Units.BYTES),
        new Supplier<Long>() {
          @Override
          public Long get() {
            return WindowCacheStatAccessor.getOpenBytes();
          }
        });

    metrics.newCallbackMetric(
        "jgit/block_cache/open_files",
        Integer.class,
        new Description("File handles held open by JGit block cache.").setGauge().setUnit("fds"),
        new Supplier<Integer>() {
          @Override
          public Integer get() {
            return WindowCacheStatAccessor.getOpenFiles();
          }
        });
  }
}
