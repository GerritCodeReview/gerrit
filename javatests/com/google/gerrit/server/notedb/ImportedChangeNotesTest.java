// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.entities.Change;
import com.google.gerrit.server.git.RepositoryCaseMismatchException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.junit.Before;
import org.junit.Test;

public class ImportedChangeNotesTest extends AbstractChangeNotesTest {

  private static final String FOREIGN_SERVER_ID = "foreign-server-id";
  private static final String IMPORTED_SERVER_ID = "gerrit-imported-1";

  @Before
  @Override
  public void setUpTestEnvironment() throws Exception {
    setupTestPrerequisites();
  }

  private void initServerIds(String serverId, String... importedServerIds)
      throws Exception, RepositoryCaseMismatchException, RepositoryNotFoundException {
    injector = createTestInjector(serverId, importedServerIds);
    injector.injectMembers(this);
    createAllUsers(injector);
  }

  @Test
  public void allowChangeFromImportedServerId() throws Exception {
    initServerIds(LOCAL_SERVER_ID, IMPORTED_SERVER_ID);

    Change importedChange = newChange(createTestInjector(IMPORTED_SERVER_ID), false);
    Change localChange = newChange();

    assertThat(newNotes(importedChange).getServerId()).isEqualTo(IMPORTED_SERVER_ID);
    assertThat(newNotes(localChange).getServerId()).isEqualTo(LOCAL_SERVER_ID);
  }

  @Test
  public void rejectChangeWithForeignServerId() throws Exception {
    initServerIds(LOCAL_SERVER_ID);
    Change foreignChange = newChange(createTestInjector(FOREIGN_SERVER_ID), false);

    InvalidServerIdException invalidServerIdEx =
        assertThrows(InvalidServerIdException.class, () -> newNotes(foreignChange));

    String invalidServerIdMessage = invalidServerIdEx.getMessage();
    assertThat(invalidServerIdMessage).contains("expected " + LOCAL_SERVER_ID);
    assertThat(invalidServerIdMessage).contains("actual: " + FOREIGN_SERVER_ID);
  }
}
