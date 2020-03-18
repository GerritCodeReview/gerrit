package com.google.gerrit.server.cache.serialize;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.account.GroupIncludeCacheImpl.ExternalGroupsSerializer;
import com.google.gerrit.server.cache.proto.Cache.AllExternalGroupsProto;
import com.google.gerrit.server.cache.proto.Cache.AllExternalGroupsProto.ExternalGroupProto;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Test;

public class ExternalGroupsSerializerTest {
  private ImmutableList<AccountGroup.UUID> groups =
      ImmutableList.of(
          AccountGroup.UUID.parse("593f90fcf688109f61b0fd4aa47ddf65abb96012"),
          AccountGroup.UUID.parse("bc9f75584ac0362584a64fb3f0095d905415b153"));

  @Test
  public void serialize() throws InvalidProtocolBufferException {
    byte[] serialized = ExternalGroupsSerializer.INSTANCE.serialize(groups);

    assertThat(AllExternalGroupsProto.parseFrom(serialized))
        .isEqualTo(
            AllExternalGroupsProto.newBuilder()
                .addAllExternalGroup(
                    groups.stream()
                        .map(g -> ExternalGroupProto.newBuilder().setGroupUuid(g.get()).build())
                        .collect(toImmutableList()))
                .build());
  }

  @Test
  public void deserialize() {
    byte[] serialized = ExternalGroupsSerializer.INSTANCE.serialize(groups);
    assertThat(ExternalGroupsSerializer.INSTANCE.deserialize(serialized)).isEqualTo(groups);
  }
}
