// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import org.eclipse.jgit.lib.Config;

public class IndexType {
  private static final String LUCENE = "lucene";
  private static final String ELASTICSEARCH = "elasticsearch";

  private final String type;

  public IndexType(@Nullable String type) {
    this.type = type == null ? getDefault() : type.toLowerCase();
  }

  public IndexType(@Nullable Config cfg) {
    this(cfg != null ? cfg.getString("index", null, "type") : null);
  }

  public static String getDefault() {
    return LUCENE;
  }

  public static ImmutableSet<String> getKnownTypes() {
    return ImmutableSet.of(LUCENE, ELASTICSEARCH);
  }

  public boolean isLucene() {
    return type.equals(LUCENE);
  }

  public boolean isElasticsearch() {
    return type.equals(ELASTICSEARCH);
  }

  public static boolean isElasticsearch(String type) {
    return type.equals(ELASTICSEARCH);
  }

  @Override
  public String toString() {
    return type;
  }
}
