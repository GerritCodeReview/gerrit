package com.google.gerrit.server.cache.serialize;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.comment.CommentContextCacheImpl.CommentContextSerializer.INSTANCE;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ContextLines;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.comment.CommentContextCacheImpl.Key;
import com.google.gerrit.server.comment.CommentContextCacheImpl.Key.Serializer;
import org.junit.Test;

public class CommentContextSerializerTest {
  @Test
  public void roundTripValue() {
    ContextLines contextLines = ContextLines.create(ImmutableMap.of(1, "line_1", 2, "line_2"));

    byte[] serialized = INSTANCE.serialize(contextLines);
    ContextLines deserialized = INSTANCE.deserialize(serialized);

    assertThat(contextLines).isEqualTo(deserialized);
  }

  @Test
  public void roundTripKey() {
    Project.NameKey proj = Project.NameKey.parse("proj");
    Change.Id changeId = Change.Id.tryParse("1234").get();

    Key k = Key.create(proj, changeId, 3, "commentId", "pathHash");
    byte[] serialized = Serializer.INSTANCE.serialize(k);
    assertThat(k).isEqualTo(Serializer.INSTANCE.deserialize(serialized));
  }
}
