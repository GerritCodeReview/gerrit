// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.plugins;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.eclipse.jgit.lib.Config;

@Singleton
public class MandatoryPluginsCollection {
  private final CopyOnWriteArraySet<String> members;

  @Inject
  MandatoryPluginsCollection(@GerritServerConfig Config cfg) {
    members = Sets.newCopyOnWriteArraySet();
    members.addAll(Arrays.asList(cfg.getStringList("plugins", null, "mandatory")));
  }

  public boolean contains(String name) {
    return members.contains(name);
  }

  public Set<String> asSet() {
    return ImmutableSet.copyOf(members);
  }

  @VisibleForTesting
  public void add(String name) {
    members.add(name);
  }
}
