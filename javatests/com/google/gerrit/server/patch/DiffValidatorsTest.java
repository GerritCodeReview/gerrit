// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.Patch.FileMode;
import com.google.gerrit.entities.Patch.PatchType;
import com.google.gerrit.server.git.LargeObjectException;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

/** Test class for {@link DiffValidators}. */
public class DiffValidatorsTest {
  @Inject private DiffValidators diffValidators;

  @Before
  public void setUpInjector() throws Exception {
    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);
  }

  @Test
  public void fileSizeExceeded() {
    int largeSize = 100000000;
    FileDiffOutput fileDiff =
        FileDiffOutput.builder()
            .oldCommitId(ObjectId.zeroId())
            .newCommitId(ObjectId.zeroId())
            .comparisonType(ComparisonType.againstRoot())
            .changeType(ChangeType.ADDED)
            .patchType(Optional.of(PatchType.UNIFIED))
            .oldPath(Optional.empty())
            .newPath(Optional.of("f.txt"))
            .oldMode(Optional.empty())
            .newMode(Optional.of(FileMode.REGULAR_FILE))
            .headerLines(ImmutableList.of())
            .edits(ImmutableList.of())
            .size(largeSize)
            .sizeDelta(largeSize)
            .build();
    Exception thrown =
        assertThrows(LargeObjectException.class, () -> diffValidators.validate(fileDiff));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "File size for file f.txt exceeded the max file size threshold."
                    + " Threshold = %d bytes, Actual size = %d bytes",
                DiffFileSizeValidator.MAX_FILE_SIZE, largeSize));
  }
}
