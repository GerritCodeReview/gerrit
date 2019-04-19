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

package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import java.io.IOException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class IntBlobTest {
  // Note: Can't easily test GitRefUpdated behavior, since binding GitRefUpdated requires a thick
  // stack of dependencies, and it's not just a simple interface or abstract class.

  private Project.NameKey projectName;
  private InMemoryRepository repo;
  private TestRepository<InMemoryRepository> tr;
  private RevWalk rw;

  @Before
  public void setUp() throws Exception {
    projectName = Project.nameKey("repo");
    repo = new InMemoryRepository(new DfsRepositoryDescription(projectName.get()));
    tr = new TestRepository<>(repo);
    rw = tr.getRevWalk();
  }

  @Test
  public void parseNoRef() throws Exception {
    assertThat(IntBlob.parse(repo, "refs/nothing")).isEmpty();
  }

  @Test
  public void parseNonBlob() throws Exception {
    String refName = "refs/foo/master";
    tr.branch(refName).commit().create();
    try {
      IntBlob.parse(repo, refName);
      assert_().fail("Expected IncorrectObjectTypeException");
    } catch (IncorrectObjectTypeException e) {
      // Expected.
    }
  }

  @Test
  public void parseValid() throws Exception {
    String refName = "refs/foo";
    ObjectId id = tr.update(refName, tr.blob("123"));
    assertThat(IntBlob.parse(repo, refName)).value().isEqualTo(IntBlob.create(id, 123));
  }

  @Test
  public void parseWithWhitespace() throws Exception {
    String refName = "refs/foo";
    ObjectId id = tr.update(refName, tr.blob(" 123 "));
    assertThat(IntBlob.parse(repo, refName)).value().isEqualTo(IntBlob.create(id, 123));
  }

  @Test
  public void parseInvalid() throws Exception {
    String refName = "refs/foo";
    ObjectId id = tr.update(refName, tr.blob("1 2 3"));
    try {
      IntBlob.parse(repo, refName);
      assert_().fail("Expected StorageException");
    } catch (StorageException e) {
      assertThat(e).hasMessageThat().isEqualTo("invalid value in refs/foo blob at " + id.name());
    }
  }

  @Test
  public void tryStoreNoOldId() throws Exception {
    String refName = "refs/foo";
    RefUpdate ru =
        IntBlob.tryStore(repo, rw, projectName, refName, null, 123, GitReferenceUpdated.DISABLED);
    assertThat(ru.getResult()).isEqualTo(RefUpdate.Result.NEW);
    assertThat(ru.getName()).isEqualTo(refName);
    assertThat(IntBlob.parse(repo, refName))
        .value()
        .isEqualTo(IntBlob.create(ru.getNewObjectId(), 123));
  }

  @Test
  public void tryStoreOldIdZero() throws Exception {
    String refName = "refs/foo";
    RefUpdate ru =
        IntBlob.tryStore(
            repo, rw, projectName, refName, ObjectId.zeroId(), 123, GitReferenceUpdated.DISABLED);
    assertThat(ru.getResult()).isEqualTo(RefUpdate.Result.NEW);
    assertThat(ru.getName()).isEqualTo(refName);
    assertThat(IntBlob.parse(repo, refName))
        .value()
        .isEqualTo(IntBlob.create(ru.getNewObjectId(), 123));
  }

  @Test
  public void tryStoreCorrectOldId() throws Exception {
    String refName = "refs/foo";
    ObjectId id = tr.update(refName, tr.blob("123"));
    RefUpdate ru =
        IntBlob.tryStore(repo, rw, projectName, refName, id, 456, GitReferenceUpdated.DISABLED);
    assertThat(ru.getResult()).isEqualTo(RefUpdate.Result.FORCED);
    assertThat(ru.getName()).isEqualTo(refName);
    assertThat(IntBlob.parse(repo, refName))
        .value()
        .isEqualTo(IntBlob.create(ru.getNewObjectId(), 456));
  }

  @Test
  public void tryStoreWrongOldId() throws Exception {
    String refName = "refs/foo";
    RefUpdate ru =
        IntBlob.tryStore(
            repo,
            rw,
            projectName,
            refName,
            ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"),
            123,
            GitReferenceUpdated.DISABLED);
    assertThat(ru.getResult()).isEqualTo(RefUpdate.Result.LOCK_FAILURE);
    assertThat(ru.getName()).isEqualTo(refName);
    assertThat(IntBlob.parse(repo, refName)).isEmpty();
  }

  @Test
  public void storeNoOldId() throws Exception {
    String refName = "refs/foo";
    IntBlob.store(repo, rw, projectName, refName, null, 123, GitReferenceUpdated.DISABLED);
    assertThat(IntBlob.parse(repo, refName))
        .value()
        .isEqualTo(IntBlob.create(getRef(refName), 123));
  }

  @Test
  public void storeOldIdZero() throws Exception {
    String refName = "refs/foo";
    IntBlob.store(
        repo, rw, projectName, refName, ObjectId.zeroId(), 123, GitReferenceUpdated.DISABLED);
    assertThat(IntBlob.parse(repo, refName))
        .value()
        .isEqualTo(IntBlob.create(getRef(refName), 123));
  }

  @Test
  public void storeCorrectOldId() throws Exception {
    String refName = "refs/foo";
    ObjectId id = tr.update(refName, tr.blob("123"));
    IntBlob.store(repo, rw, projectName, refName, id, 456, GitReferenceUpdated.DISABLED);
    assertThat(IntBlob.parse(repo, refName))
        .value()
        .isEqualTo(IntBlob.create(getRef(refName), 456));
  }

  @Test
  public void storeWrongOldId() throws Exception {
    String refName = "refs/foo";
    try {
      IntBlob.store(
          repo,
          rw,
          projectName,
          refName,
          ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"),
          123,
          GitReferenceUpdated.DISABLED);
      assert_().fail("expected LockFailureException");
    } catch (LockFailureException e) {
      assertThat(e.getFailedRefs()).containsExactly("refs/foo");
    }
    assertThat(IntBlob.parse(repo, refName)).isEmpty();
  }

  private ObjectId getRef(String refName) throws IOException {
    return repo.exactRef(refName).getObjectId();
  }
}
