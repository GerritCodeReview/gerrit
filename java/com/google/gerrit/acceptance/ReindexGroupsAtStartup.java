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

package com.google.gerrit.acceptance;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Reindex all groups at Gerrit daemon startup. */
public class ReindexGroupsAtStartup implements LifecycleListener {
  private final GroupIndexer groupIndexer;
  private final Groups groups;

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(ReindexGroupsAtStartup.class).in(Scopes.SINGLETON);
    }
  }

  @Inject
  public ReindexGroupsAtStartup(GroupIndexer groupIndexer, Groups groups) {
    this.groupIndexer = groupIndexer;
    this.groups = groups;
  }

  @Override
  public void start() {
    try {
      groups
          .getAllGroupReferences()
          .forEach(
              group -> {
                try {
                  groupIndexer.index(group.getUUID());
                } catch (IOException e) {
                  throw new IllegalStateException(
                      String.format("Unable to index %s, tests may fail", group), e);
                }
              });
    } catch (ConfigInvalidException e) {
      throw new IllegalStateException("Unable to reindex groups, tests may fail", e);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void stop() {}
}
