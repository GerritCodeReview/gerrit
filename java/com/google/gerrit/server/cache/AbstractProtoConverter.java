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

package com.google.gerrit.server.cache;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Enums;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import java.lang.reflect.InvocationTargetException;

public abstract class AbstractProtoConverter<
        T, P extends GeneratedMessageV3, B extends GeneratedMessageV3.Builder<B>>
    implements ProtoConverter<T> {
  protected static <I extends Enum<I>, O extends Enum<O>> O convertEnumByName(
      I input, Class<O> outClass) {
    // TODO(dborowitz): Consider replacing with static BiMaps.
    return Enums.getIfPresent(outClass, input.name())
        .toJavaUtil()
        .orElseThrow(() -> new IllegalArgumentException("mismatched enum value: " + input.name()));
  }

  private final P defaultInstance;
  private final FieldDescriptor versionFieldDescriptor;
  private final int version;

  public AbstractProtoConverter(Class<P> protoClass, Class<B> builderClass, int version) {
    this.defaultInstance = getDefaultInstance(protoClass);
    this.versionFieldDescriptor = defaultInstance.getDescriptorForType().findFieldByName("version");
    this.version = version;

    checkArgument(
        versionFieldDescriptor != null
            && versionFieldDescriptor.getType() == FieldDescriptor.Type.INT32,
        "proto %s must contain an int32 version field, found: %s",
        protoClass.getName(),
        versionFieldDescriptor);

    // TODO(dborowitz): Determine if this is possible to enforce at compile time.
    Class<?> actualBuilderClass = defaultInstance.newBuilderForType().getClass();
    checkArgument(
        actualBuilderClass == builderClass,
        "expected builder for proto class %s to match builder class %s, found: %s",
        protoClass.getName(),
        builderClass.getName(),
        actualBuilderClass.getName());
  }

  @SuppressWarnings("unchecked")
  private static <P extends MessageLite> P getDefaultInstance(Class<P> protoClass) {
    try {
      return (P) protoClass.getMethod("getDefaultInstance").invoke(null);
    } catch (IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException
        | NoSuchMethodException
        | SecurityException e) {
      throw new RuntimeException("Unable to initialize parser for " + protoClass.getName(), e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public final byte[] toProtoBytes(T obj) {
    B builder = (B) defaultInstance.newBuilderForType();
    builder.setField(versionFieldDescriptor, version);
    populateBuilder(builder, obj);
    return builder.build().toByteArray();
  }

  protected abstract void populateBuilder(B builder, T obj);

  @Override
  public final T fromProtoBytes(byte[] bytes) {
    try {
      P proto = getParser().parseFrom(bytes);
      int actualVersion = (int) proto.getField(versionFieldDescriptor);
      checkArgument(
          actualVersion == version, "expected proto with version %s, found: %s", actualVersion);
      return fromProto(proto);
    } catch (InvalidProtocolBufferException e) {
      // TODO(dborowitz): Checked exception?
      throw new IllegalArgumentException("failed to parse proto");
    }
  }

  @SuppressWarnings("unchecked")
  private Parser<P> getParser() {
    return (Parser<P>) defaultInstance.getParserForType();
  }

  protected abstract T fromProto(P proto);
}
