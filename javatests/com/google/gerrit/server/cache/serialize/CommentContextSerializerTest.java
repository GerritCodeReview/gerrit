package com.google.gerrit.server.cache.serialize;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.comment.CommentContextCacheImpl.CommentContextSerializer.INSTANCE;

import com.google.gerrit.extensions.common.LabeledContextLineInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class CommentContextSerializerTest {
  @Test
  public void roundTrip() {
    List<LabeledContextLineInfo> context = new ArrayList<>();
    context.add(new LabeledContextLineInfo(1, "line_1"));
    context.add(new LabeledContextLineInfo(2, "line_2"));

    byte[] serialized = INSTANCE.serialize(context);
    List<LabeledContextLineInfo> deserialized = INSTANCE.deserialize(serialized);

    assertThat(context).containsExactlyElementsIn(deserialized);
  }
}
