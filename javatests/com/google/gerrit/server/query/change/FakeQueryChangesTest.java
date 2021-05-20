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

import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.eclipse.jgit.lib.Config;

/**
 * Test against {@link com.google.gerrit.index.testing.AbstractFakeIndex}. This test might seem
 * obsolete, but it makes sure that the fake index implementation used in tests gives the same
 * results as production indices.
 */
public abstract class FakeQueryChangesTest extends AbstractQueryChangesTest {
  @Override
  protected Injector createInjector() {
    Config fakeConfig = new Config(config);
    InMemoryModule.setDefaults(fakeConfig);
    fakeConfig.setString("index", null, "type", "fake");
    return Guice.createInjector(new InMemoryModule(fakeConfig));
  }
}
