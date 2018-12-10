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

package com.google.gerrit.reviewdb.converter;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwtorm.protobuf.CodecFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;
import com.google.gwtorm.server.OrmException;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.MessageLite;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import org.junit.Test;

// TODO(aliceks): Delete after proving binary compatibility.
public class ChangeMessageConverterCompatibilityTest {

  private final ProtobufCodec<ChangeMessage> changeMessageCodec =
      CodecFactory.encoder(ChangeMessage.class);
  private final ChangeMessageProtoConverter changeMessageProtoConverter =
      ChangeMessageProtoConverter.INSTANCE;

  @Test
  public void changeIndexFieldWithAllValuesIsBinaryCompatible() throws Exception {
    ChangeMessage changeMessage =
        new ChangeMessage(
            new ChangeMessage.Key(new Change.Id(543), "change-message-21"),
            new Account.Id(63),
            new Timestamp(9876543),
            new PatchSet.Id(new Change.Id(34), 13));
    changeMessage.setMessage("This is a change message.");
    changeMessage.setTag("An arbitrary tag.");
    changeMessage.setRealAuthor(new Account.Id(10003));
    ImmutableList<ChangeMessage> changeMessages = ImmutableList.of(changeMessage);

    byte[] resultOfOldConverter =
        getOnlyElement(convertToProtos_old(changeMessageCodec, changeMessages));
    byte[] resultOfNewConverter =
        getOnlyElement(convertToProtos_new(changeMessageProtoConverter, changeMessages));

    assertThat(resultOfNewConverter).isEqualTo(resultOfOldConverter);
  }

  @Test
  public void changeIndexFieldWithMandatoryValuesIsBinaryCompatible() throws Exception {
    ChangeMessage changeMessage =
        new ChangeMessage(
            new ChangeMessage.Key(new Change.Id(543), "change-message-21"), null, null, null);
    ImmutableList<ChangeMessage> changeMessages = ImmutableList.of(changeMessage);

    byte[] resultOfOldConverter =
        getOnlyElement(convertToProtos_old(changeMessageCodec, changeMessages));
    byte[] resultOfNewConverter =
        getOnlyElement(convertToProtos_new(changeMessageProtoConverter, changeMessages));

    assertThat(resultOfNewConverter).isEqualTo(resultOfOldConverter);
  }

  // Copied from ChangeField.
  private static <T> List<byte[]> convertToProtos_old(ProtobufCodec<T> codec, Collection<T> objs)
      throws OrmException {
    List<byte[]> result = Lists.newArrayListWithCapacity(objs.size());
    ByteArrayOutputStream out = new ByteArrayOutputStream(256);
    try {
      for (T obj : objs) {
        out.reset();
        CodedOutputStream cos = CodedOutputStream.newInstance(out);
        codec.encode(obj, cos);
        cos.flush();
        result.add(out.toByteArray());
      }
    } catch (IOException e) {
      throw new OrmException(e);
    }
    return result;
  }

  // Copied from ChangeField.
  private static <T> List<byte[]> convertToProtos_new(
      ProtoConverter<?, T> converter, Collection<T> objects) {
    return objects
        .stream()
        .map(converter::toProto)
        .map(Protos::toByteArray)
        .collect(toImmutableList());
  }

  @Test
  public void changeNotesFieldWithAllValuesIsBinaryCompatible() {
    ChangeMessage changeMessage =
        new ChangeMessage(
            new ChangeMessage.Key(new Change.Id(543), "change-message-21"),
            new Account.Id(63),
            new Timestamp(9876543),
            new PatchSet.Id(new Change.Id(34), 13));
    changeMessage.setMessage("This is a change message.");
    changeMessage.setTag("An arbitrary tag.");
    changeMessage.setRealAuthor(new Account.Id(10003));

    ByteString resultOfOldConverter = Protos.toByteString(changeMessage, changeMessageCodec);
    ByteString resultOfNewConverter = toByteString(changeMessage, changeMessageProtoConverter);

    assertThat(resultOfNewConverter).isEqualTo(resultOfOldConverter);
  }

  @Test
  public void changeNotesFieldWithMainValuesIsBinaryCompatible() {
    ChangeMessage changeMessage =
        new ChangeMessage(
            new ChangeMessage.Key(new Change.Id(543), "change-message-21"),
            new Account.Id(63),
            new Timestamp(9876543),
            new PatchSet.Id(new Change.Id(34), 13));

    ByteString resultOfOldConverter = Protos.toByteString(changeMessage, changeMessageCodec);
    ByteString resultOfNewConverter = toByteString(changeMessage, changeMessageProtoConverter);

    assertThat(resultOfNewConverter).isEqualTo(resultOfOldConverter);
  }

  @Test
  public void changeNotesFieldWithoutRealAuthorButAuthorIsBinaryCompatible() {
    ChangeMessage changeMessage =
        new ChangeMessage(
            new ChangeMessage.Key(new Change.Id(543), "change-message-21"),
            new Account.Id(63),
            null,
            null);

    ByteString resultOfOldConverter = Protos.toByteString(changeMessage, changeMessageCodec);
    ByteString resultOfNewConverter = toByteString(changeMessage, changeMessageProtoConverter);

    assertThat(resultOfNewConverter).isEqualTo(resultOfOldConverter);
  }

  @Test
  public void changeNotesFieldWithoutSameRealAuthorAndAuthorIsBinaryCompatible() {
    ChangeMessage changeMessage =
        new ChangeMessage(
            new ChangeMessage.Key(new Change.Id(543), "change-message-21"),
            new Account.Id(63),
            null,
            null);
    changeMessage.setRealAuthor(new Account.Id(63));

    ByteString resultOfOldConverter = Protos.toByteString(changeMessage, changeMessageCodec);
    ByteString resultOfNewConverter = toByteString(changeMessage, changeMessageProtoConverter);

    assertThat(resultOfNewConverter).isEqualTo(resultOfOldConverter);
  }

  @Test
  public void changeNotesFieldWithMandatoryValuesIsBinaryCompatible() {
    ChangeMessage changeMessage =
        new ChangeMessage(
            new ChangeMessage.Key(new Change.Id(543), "change-message-21"), null, null, null);

    ByteString resultOfOldConverter = Protos.toByteString(changeMessage, changeMessageCodec);
    ByteString resultOfNewConverter = toByteString(changeMessage, changeMessageProtoConverter);

    assertThat(resultOfNewConverter).isEqualTo(resultOfOldConverter);
  }

  // Copied from ChangeNotesState.Serializer.
  private static <T> ByteString toByteString(T object, ProtoConverter<?, T> converter) {
    MessageLite message = converter.toProto(object);
    return Protos.toByteString(message);
  }
}
