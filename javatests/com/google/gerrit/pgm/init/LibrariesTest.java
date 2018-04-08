// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertNotNull;

import com.google.gerrit.config.SitePaths;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.inject.Provider;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.Test;

public class LibrariesTest {
  @Test
  public void create() throws Exception {
    final SitePaths site = new SitePaths(Paths.get("."));
    final ConsoleUI ui = createStrictMock(ConsoleUI.class);
    final StaleLibraryRemover remover = createStrictMock(StaleLibraryRemover.class);

    replay(ui);

    Libraries lib =
        new Libraries(
            new Provider<LibraryDownloader>() {
              @Override
              public LibraryDownloader get() {
                return new LibraryDownloader(ui, site, remover);
              }
            },
            Collections.<String>emptyList(),
            false);

    assertNotNull(lib.mysqlDriver);

    verify(ui);
  }
}
