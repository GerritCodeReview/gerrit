// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.testing.TestChanges;
import org.junit.Before;
import org.junit.Test;

public class ChangeTripletTest {
  private Account.Id userId;

  @Before
  public void setUp() {
    userId = Account.id(1);
  }

  @Test
  public void testReversibleFormatAndParseWithSpecialChars() throws Exception {
    Project.NameKey project = Project.nameKey("Repository with special ~!@#$%^&*()-=/.\"'`\\ שלום");
    Change c = TestChanges.newChange(project, userId);
    String formatted = ChangeTriplet.format(c);
    assertThat(formatted)
        .isEqualTo(
            String.format(
                "%s~%s~%s",
                Url.encode(c.getDest().project().get()),
                Url.encode(c.getDest().shortName()),
                c.getKey()));

    ChangeTriplet t = ChangeTriplet.parse(formatted).get();
    assertThat(t.project()).isEqualTo(project);
    assertThat(t.branch()).isEqualTo(c.getDest());
    assertThat(t.id()).isEqualTo(c.getKey());
  }
}
