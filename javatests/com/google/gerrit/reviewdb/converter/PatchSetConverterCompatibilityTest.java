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
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.OrmException;
import com.google.gwtorm.protobuf.CodecFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;
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
public class PatchSetConverterCompatibilityTest {

  private final ProtobufCodec<PatchSet> patchSetCodec = CodecFactory.encoder(PatchSet.class);
  private final PatchSetProtoConverter patchSetProtoConverter = PatchSetProtoConverter.INSTANCE;

  @Test
  public void changeIndexFieldWithAllValuesIsBinaryCompatible() throws Exception {
    PatchSet patchSet = new PatchSet(new PatchSet.Id(new Change.Id(103), 73));
    patchSet.setRevision(new RevId("aabbccddeeff"));
    patchSet.setUploader(new Account.Id(452));
    patchSet.setCreatedOn(new Timestamp(930349320L));
    patchSet.setGroups(ImmutableList.of("group1, group2"));
    patchSet.setPushCertificate("my push certificate");
    patchSet.setDescription("This is a patch set description.");
    ImmutableList<PatchSet> patchSets = ImmutableList.of(patchSet);

    byte[] resultOfOldConverter = getOnlyElement(convertToProtos_old(patchSetCodec, patchSets));
    byte[] resultOfNewConverter =
        getOnlyElement(convertToProtos_new(patchSetProtoConverter, patchSets));

    assertThat(resultOfNewConverter).isEqualTo(resultOfOldConverter);
  }

  @Test
  public void changeIndexFieldWithMandatoryValuesIsBinaryCompatible() throws Exception {
    PatchSet patchSet = new PatchSet(new PatchSet.Id(new Change.Id(103), 73));
    ImmutableList<PatchSet> patchSets = ImmutableList.of(patchSet);

    byte[] resultOfOldConverter = getOnlyElement(convertToProtos_old(patchSetCodec, patchSets));
    byte[] resultOfNewConverter =
        getOnlyElement(convertToProtos_new(patchSetProtoConverter, patchSets));

    assertThat(resultOfNewConverter).isEqualTo(resultOfOldConverter);
  }

  @Test
  public void changeNotesFieldWithAllValuesIsBinaryCompatible() {
    PatchSet patchSet = new PatchSet(new PatchSet.Id(new Change.Id(103), 73));
    patchSet.setRevision(new RevId("aabbccddeeff"));
    patchSet.setUploader(new Account.Id(452));
    patchSet.setCreatedOn(new Timestamp(930349320L));
    patchSet.setGroups(ImmutableList.of("group1, group2"));
    patchSet.setPushCertificate("my push certificate");
    patchSet.setDescription("This is a patch set description.");

    ByteString resultOfOldConverter = Protos.toByteString(patchSet, patchSetCodec);
    ByteString resultOfNewConverter = toByteString(patchSet, patchSetProtoConverter);

    assertThat(resultOfNewConverter).isEqualTo(resultOfOldConverter);
  }

  @Test
  public void changeNotesFieldWithMandatoryValuesIsBinaryCompatible() {
    PatchSet patchSet = new PatchSet(new PatchSet.Id(new Change.Id(103), 73));

    ByteString resultOfOldConverter = Protos.toByteString(patchSet, patchSetCodec);
    ByteString resultOfNewConverter = toByteString(patchSet, patchSetProtoConverter);

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

  // Copied from ChangeNotesState.Serializer.
  private static <T> ByteString toByteString(T object, ProtoConverter<?, T> converter) {
    MessageLite message = converter.toProto(object);
    return Protos.toByteString(message);
  }
}
