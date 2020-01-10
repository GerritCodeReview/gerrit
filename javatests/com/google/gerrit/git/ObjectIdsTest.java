// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.git.ObjectIds.abbreviateName;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_STRING_LENGTH;
import static org.junit.Assert.assertThrows;

import java.util.function.Function;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.junit.Test;

public class ObjectIdsTest {
  private static final ObjectId ID =
      ObjectId.fromString("0000000000100000000000000000000000000000");
  private static final ObjectId AMBIGUOUS_BLOB_ID =
      ObjectId.fromString("0000000000b36b6aa7ea4b75318ed078f55505c3");
  private static final ObjectId AMBIGUOUS_TREE_ID =
      ObjectId.fromString("0000000000cdcf04beb2fab69e65622616294984");

  @Test
  public void abbreviateNameDefaultLength() throws Exception {
    assertRuntimeException(() -> abbreviateName(null));
    assertThat(abbreviateName(ID)).isEqualTo("0000000");
    assertThat(abbreviateName(AMBIGUOUS_BLOB_ID)).isEqualTo(abbreviateName(ID));
    assertThat(abbreviateName(AMBIGUOUS_TREE_ID)).isEqualTo(abbreviateName(ID));
  }

  @Test
  public void abbreviateNameCustomLength() throws Exception {
    assertRuntimeException(() -> abbreviateName(null, 1));
    assertRuntimeException(() -> abbreviateName(ID, -1));
    assertRuntimeException(() -> abbreviateName(ID, 0));
    assertRuntimeException(() -> abbreviateName(ID, 41));
    assertThat(abbreviateName(ID, 5)).isEqualTo("00000");
    assertThat(abbreviateName(ID, 40)).isEqualTo(ID.name());
  }

  @Test
  public void abbreviateNameDefaultLengthWithReader() throws Exception {
    assertRuntimeException(() -> abbreviateName(ID, null));

    ObjectReader reader = newReaderWithAmbiguousIds();
    assertThat(abbreviateName(ID, reader)).isEqualTo("00000000001");
  }

  @Test
  public void abbreviateNameCustomLengthWithReader() throws Exception {
    ObjectReader reader = newReaderWithAmbiguousIds();
    assertRuntimeException(() -> abbreviateName(ID, -1, reader));
    assertRuntimeException(() -> abbreviateName(ID, 0, reader));
    assertRuntimeException(() -> abbreviateName(ID, 41, reader));
    assertRuntimeException(() -> abbreviateName(ID, 5, null));

    String shortest = "00000000001";
    assertThat(abbreviateName(ID, 1, reader)).isEqualTo(shortest);
    assertThat(abbreviateName(ID, 7, reader)).isEqualTo(shortest);
    assertThat(abbreviateName(ID, shortest.length(), reader)).isEqualTo(shortest);
    assertThat(abbreviateName(ID, shortest.length() + 1, reader)).isEqualTo("000000000010");
  }

  @Test
  public void copyOrNull() throws Exception {
    testCopy(ObjectIds::copyOrNull);
    assertThat(ObjectIds.copyOrNull(null)).isNull();
  }

  @Test
  public void copyOrZero() throws Exception {
    testCopy(ObjectIds::copyOrZero);
    assertThat(ObjectIds.copyOrZero(null)).isEqualTo(ObjectId.zeroId());
  }

  private void testCopy(Function<AnyObjectId, ObjectId> copyFunc) {
    MyObjectId myId = new MyObjectId(ID);
    assertThat(myId).isEqualTo(ID);

    ObjectId copy = copyFunc.apply(myId);
    assertThat(copy).isEqualTo(myId);
    assertThat(copy).isNotSameInstanceAs(myId);
    assertThat(copy.getClass()).isEqualTo(ObjectId.class);
  }

  @Test
  public void matchesAbbreviation() throws Exception {
    assertThat(ObjectIds.matchesAbbreviation(null, "")).isFalse();
    assertThat(ObjectIds.matchesAbbreviation(null, "0")).isFalse();
    assertThat(ObjectIds.matchesAbbreviation(null, "00000")).isFalse();
    assertThat(ObjectIds.matchesAbbreviation(null, "not a SHA-1")).isFalse();
    assertThat(ObjectIds.matchesAbbreviation(null, ID.name())).isFalse();

    assertThat(ObjectIds.matchesAbbreviation(ID, "")).isTrue();
    for (int i = 1; i <= OBJECT_ID_STRING_LENGTH; i++) {
      String prefix = ID.name().substring(0, i);
      assertWithMessage("match %s against %s", ID.name(), prefix)
          .that(ObjectIds.matchesAbbreviation(ID, prefix))
          .isTrue();
    }

    assertThat(ObjectIds.matchesAbbreviation(ID, "1")).isFalse();
    assertThat(ObjectIds.matchesAbbreviation(ID, "x")).isFalse();
    assertThat(ObjectIds.matchesAbbreviation(ID, "not a SHA-1")).isFalse();
    assertThat(ObjectIds.matchesAbbreviation(ID, AMBIGUOUS_BLOB_ID.name())).isFalse();
  }

  @FunctionalInterface
  private interface Func {
    void call() throws Exception;
  }

  private static void assertRuntimeException(Func func) throws Exception {
    assertThrows(RuntimeException.class, () -> func.call());
  }

  private static ObjectReader newReaderWithAmbiguousIds() throws Exception {
    // Recipe for creating ambiguous IDs courtesy of git core:
    // https://github.com/git/git/blob/df799f5d99ac51d4fc791d546de3f936088582fc/t/t1512-rev-parse-disambiguation.sh
    try (TestRepository<Repository> tr =
        new TestRepository<>(new InMemoryRepository(new DfsRepositoryDescription("repo")))) {
      String blobData = "0\n1\n2\n3\n4\n5\n6\n7\n8\n9\n\nb1rwzyc3\n";
      RevBlob blob = tr.blob(blobData);
      assertThat(blob.name()).isEqualTo(AMBIGUOUS_BLOB_ID.name());
      assertThat(tr.tree(tr.file("a0blgqsjc", blob)).name()).isEqualTo(AMBIGUOUS_TREE_ID.name());
      return tr.getRevWalk().getObjectReader();
    }
  }

  private static class MyObjectId extends ObjectId {
    private static final long serialVersionUID = 1L;

    MyObjectId(AnyObjectId src) {
      super(src);
    }
  }
}
