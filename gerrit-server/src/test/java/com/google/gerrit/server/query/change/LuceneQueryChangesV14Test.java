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

package com.google.gerrit.server.query.change;

import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.inject.Injector;

import org.eclipse.jgit.lib.Config;
import org.junit.Ignore;
import org.junit.Test;

public class LuceneQueryChangesV14Test extends LuceneQueryChangesTest {
  @Override
  protected Injector createInjector(LifecycleManager lifecycle) {
    Config luceneConfig = new Config(config);
    InMemoryModule.setDefaults(luceneConfig);
    // Latest version with a Lucene 4 index.
    luceneConfig.setInt("index", "lucene", "testVersion", 14);
    return InMemoryModule.createInjector(lifecycle, luceneConfig);
  }

  @Override
  @Ignore
  @Test
  public void byCommentBy() {
    // Ignore.
  }

  @Override
  @Ignore
  @Test
  public void byFrom() {
    // Ignore.
  }
}
