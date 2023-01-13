// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.lucene;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.server.config.ConfigUtil;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.eclipse.jgit.lib.Config;

/** Combination of Lucene {@link IndexWriterConfig} with additional Gerrit-specific options. */
class GerritIndexWriterConfig {
  private static final ImmutableMap<String, String> CUSTOM_CHAR_MAPPING =
      ImmutableMap.of("_", " ", ".", " ");

  private final CustomMappingAnalyzer analyzer;
  private final boolean isShareableIndexEnabled;
  private final int maxMergeCount;
  private final int maxThreadCount;
  private final boolean enableAutoIOThrottle;
  private final double ramBufferSizeMB;
  private final int maxBufferedDocs;
  private long commitWithinMs;

  GerritIndexWriterConfig(Config cfg, String name) {
    analyzer =
        new CustomMappingAnalyzer(
            new StandardAnalyzer(CharArraySet.EMPTY_SET), CUSTOM_CHAR_MAPPING);

    isShareableIndexEnabled = cfg.getBoolean("index", name, "enableSharing", false);
    maxMergeCount = cfg.getInt("index", name, "maxMergeCount", -1);
    maxThreadCount = cfg.getInt("index", name, "maxThreadCount", -1);
    enableAutoIOThrottle = cfg.getBoolean("index", name, "enableAutoIOThrottle", true);
    maxBufferedDocs =
        cfg.getInt("index", name, "maxBufferedDocs", IndexWriterConfig.DEFAULT_MAX_BUFFERED_DOCS);

    double mb = 1 << 20;
    mb =
        cfg.getLong(
                "index",
                name,
                "ramBufferSize",
                (long) (IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB * mb))
            / mb;
    ramBufferSizeMB = mb;

    try {
      commitWithinMs =
          ConfigUtil.getTimeUnit(
              cfg, "index", name, "commitWithin", MILLISECONDS.convert(5, MINUTES), MILLISECONDS);
    } catch (IllegalArgumentException e) {
      commitWithinMs = cfg.getLong("index", name, "commitWithin", 0);
    }
  }

  CustomMappingAnalyzer getAnalyzer() {
    return analyzer;
  }

  IndexWriterConfig getLuceneConfig() {
    IndexWriterConfig luceneConfig =
        new IndexWriterConfig(analyzer)
            .setOpenMode(OpenMode.CREATE_OR_APPEND)
            .setCommitOnClose(true);

    if (maxMergeCount != -1 || maxThreadCount != -1 || !enableAutoIOThrottle) {
      ConcurrentMergeScheduler mergeScheduler = new ConcurrentMergeScheduler();
      if (maxMergeCount != -1 || maxThreadCount != -1) {
        mergeScheduler.setMaxMergesAndThreads(maxMergeCount, maxThreadCount);
      }
      if (!enableAutoIOThrottle) {
        mergeScheduler.disableAutoIOThrottle();
      }
      luceneConfig.setMergeScheduler(mergeScheduler);
    }

    luceneConfig.setRAMBufferSizeMB(ramBufferSizeMB);
    luceneConfig.setMaxBufferedDocs(maxBufferedDocs);
    return luceneConfig;
  }

  boolean isShareableIndexEnabled() {
    return isShareableIndexEnabled;
  }

  long getCommitWithinMs() {
    return commitWithinMs;
  }
}
