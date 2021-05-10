// Copyright (C) 2021 The Android Open Source Project
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

import com.google.common.collect.Iterables;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.InMemoryModule;
import com.google.gerrit.testing.IndexConfig;
import com.google.gerrit.testing.IndexVersions;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.eclipse.jgit.lib.Config;

public class FakeQueryChangesPreviousIndexVersionTest extends AbstractQueryChangesTest {
  @Override
  protected Injector createInjector() {
    Config fakeConfig = new Config(config);
    InMemoryModule.setDefaults(fakeConfig);
    fakeConfig.setString("index", null, "type", "fake");
    return Guice.createInjector(new InMemoryModule(fakeConfig));
  }

  @ConfigSuite.Default
  public static Config againstPreviousIndexVersion() {
    // the current schema version is already tested by the inherited default config suite
    return Iterables.getOnlyElement(
        IndexVersions.asConfigMap(
                ChangeSchemaDefinitions.INSTANCE,
                IndexVersions.getWithoutLatest(ChangeSchemaDefinitions.INSTANCE),
                "againstIndexVersion",
                IndexConfig.createForFake())
            .values());
  }
}
