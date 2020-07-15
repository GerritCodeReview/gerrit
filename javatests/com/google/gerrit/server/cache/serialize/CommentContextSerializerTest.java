package com.google.gerrit.server.cache.serialize;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.comment.CommentContextCacheImpl.CommentContextSerializer.INSTANCE;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ContextLine;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.comment.CommentContextCacheImpl.Key;
import com.google.gerrit.server.comment.CommentContextCacheImpl.Key.Serializer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class CommentContextSerializerTest {
  @Test
  public void roundTripValue() {
    List<ContextLine> context = new ArrayList<>();
    context.add(ContextLine.create(1, "line_1"));
    context.add(ContextLine.create(2, "line_2"));

    byte[] serialized = INSTANCE.serialize(context.stream().collect(toImmutableList()));
    List<ContextLine> deserialized = INSTANCE.deserialize(serialized);

    assertThat(context).containsExactlyElementsIn(deserialized);
  }

  @Test
  public void roundTripKey() {
    Project.NameKey proj = Project.NameKey.parse("proj");
    PatchSet.Id psId = PatchSet.id(Change.id(1), 2);

    Key k = Key.create(proj, psId, "commentId", "pathHash");
    byte[] serialized = Serializer.INSTANCE.serialize(k);
    assertThat(k).isEqualTo(Serializer.INSTANCE.deserialize(serialized));
  }
}
