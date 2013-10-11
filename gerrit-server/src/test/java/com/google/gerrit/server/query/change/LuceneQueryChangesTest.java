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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.query.change;

import com.google.gerrit.testutil.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.eclipse.jgit.lib.Config;

public class LuceneQueryChangesTest extends AbstractIndexQueryChangesTest {
  protected Injector createInjector() {
    Config cfg = InMemoryModule.newDefaultConfig();
    cfg.setString("index", null, "type", "lucene");
    cfg.setBoolean("index", "lucene", "testInmemory", true);
    cfg.setInt("index", "lucene", "testVersion", 4);
    return Guice.createInjector(new InMemoryModule(cfg));
  }
}
