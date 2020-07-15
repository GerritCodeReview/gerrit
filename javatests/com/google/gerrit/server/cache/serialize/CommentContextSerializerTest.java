package com.google.gerrit.server.cache.serialize;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.comment.CommentContextCacheImpl.CommentContextSerializer.INSTANCE;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.CommentContext;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.comment.CommentContextKey;
import org.junit.Test;

public class CommentContextSerializerTest {
  @Test
  public void roundTripValue() {
    CommentContext commentContext =
        CommentContext.create(ImmutableMap.of(1, "line_1", 2, "line_2"));

    byte[] serialized = INSTANCE.serialize(commentContext);
    CommentContext deserialized = INSTANCE.deserialize(serialized);

    assertThat(commentContext).isEqualTo(deserialized);
  }

  @Test
  public void roundTripKey() {
    Project.NameKey proj = Project.NameKey.parse("project");
    Change.Id changeId = Change.Id.tryParse("1234").get();

    CommentContextKey k =
        CommentContextKey.builder()
            .project(proj)
            .changeId(changeId)
            .id("commentId")
            .path("pathHash")
            .patchset(1)
            .build();
    byte[] serialized = CommentContextKey.Serializer.INSTANCE.serialize(k);
    assertThat(k).isEqualTo(CommentContextKey.Serializer.INSTANCE.deserialize(serialized));
  }
}
