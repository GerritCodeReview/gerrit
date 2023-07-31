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
import org.apache.lucene.index.IndexDeletionPolicy;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.eclipse.jgit.lib.Config;

/** Combination of Lucene {@link IndexWriterConfig} with additional Gerrit-specific options. */
class GerritIndexWriterConfig {
  private static final ImmutableMap<String, String> CUSTOM_CHAR_MAPPING =
      ImmutableMap.of("_", " ", ".", " ");

  private final IndexWriterConfig luceneConfig;
  private long commitWithinMs;
  private final CustomMappingAnalyzer analyzer;

  GerritIndexWriterConfig(Config cfg, String name) {
    analyzer =
        new CustomMappingAnalyzer(
            new StandardAnalyzer(CharArraySet.EMPTY_SET), CUSTOM_CHAR_MAPPING);
    luceneConfig =
        new IndexWriterConfig(analyzer)
            .setOpenMode(OpenMode.CREATE_OR_APPEND)
            .setCommitOnClose(true);

    int maxMergeCount = cfg.getInt("index", name, "maxMergeCount", -1);
    int maxThreadCount = cfg.getInt("index", name, "maxThreadCount", -1);
    boolean enableAutoIOThrottle = cfg.getBoolean("index", name, "enableAutoIOThrottle", true);
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

    double m = 1 << 20;
    luceneConfig.setRAMBufferSizeMB(
        cfg.getLong(
                "index",
                name,
                "ramBufferSize",
                (long) (IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB * m))
            / m);
    luceneConfig.setMaxBufferedDocs(
        cfg.getInt("index", name, "maxBufferedDocs", IndexWriterConfig.DEFAULT_MAX_BUFFERED_DOCS));
    try {
      commitWithinMs =
          ConfigUtil.getTimeUnit(
              cfg, "index", name, "commitWithin", MILLISECONDS.convert(5, MINUTES), MILLISECONDS);
    } catch (IllegalArgumentException e) {
      commitWithinMs = cfg.getLong("index", name, "commitWithin", 0);
    }
  }

  void setIndexDeletionPolicy(IndexDeletionPolicy indexDeletionPolicy) {
    luceneConfig.setIndexDeletionPolicy(indexDeletionPolicy);
  }

  IndexDeletionPolicy getIndexDeletionPolicy() {
    return luceneConfig.getIndexDeletionPolicy();
  }

  CustomMappingAnalyzer getAnalyzer() {
    return analyzer;
  }

  IndexWriterConfig getLuceneConfig() {
    return luceneConfig;
  }

  long getCommitWithinMs() {
    return commitWithinMs;
  }
}
