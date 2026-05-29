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

package com.google.gerrit.acceptance.server.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

/** Tests for {@link CodeReviewCommit}. */
public class CodeReviewCommitTest {

  @Test
  public void checkSerializable_withStatusMessage() throws Exception {
    CodeReviewCommit commit = new CodeReviewCommit(createCommit());
    commit.setStatusMessage("Status");
    CodeReviewCommit deserializedCommit = serializeAndReadBack(commit);
    assertThat(deserializedCommit).isEqualTo(commit);
    assertThat(deserializedCommit.getStatusMessage().get()).isEqualTo("Status");
  }

  @Test
  public void checkSerializable_emptyStatusMessage() throws Exception {
    CodeReviewCommit commit = new CodeReviewCommit(createCommit());
    CodeReviewCommit deserializedCommit = serializeAndReadBack(commit);
    assertThat(deserializedCommit).isEqualTo(commit);
    assertThat(deserializedCommit.getStatusMessage().isPresent()).isFalse();
  }

  @SuppressWarnings("BanSerializableRead")
  private CodeReviewCommit serializeAndReadBack(CodeReviewCommit codeReviewCommit)
      throws Exception {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos)) {
      out.writeObject(codeReviewCommit);
      out.flush();
      try (ByteArrayInputStream fileIn = new ByteArrayInputStream(bos.toByteArray());
          ObjectInputStream in = new ObjectInputStream(fileIn); ) {
        return (CodeReviewCommit) in.readObject();
      }
    }
  }

  private ObjectId createCommit() throws Exception {
    InMemoryRepositoryManager repoManager = new InMemoryRepositoryManager();
    Project.NameKey project = Project.nameKey("test");
    try (Repository repo = repoManager.createRepository(project);
        TestRepository<Repository> tr = new TestRepository<>(repo)) {
      PersonIdent ident = new PersonIdent(new PersonIdent("Test Ident", "test@test.com"));
      return tr.commit().author(ident).committer(ident).message("Test commit").create();
    }
  }
}
