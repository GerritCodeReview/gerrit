package com.google.gerrit.entities.converter;

import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.common.ConvertibleToProto;
import com.google.protobuf.MessageLite;

/**
 * An extension to {@link ProtoConverter} that enforces the Entity class and the Proto class to stay
 * in sync. The enforcement is done by {@link SafeProtoConverterTest}.
 *
 * <p>Requirements:
 *
 * <ul>
 *   <li>Implementing classes must be enums with a single value. Please prefer descriptive enum and
 *       instance names, such as {@code MyTypeConverter::MY_TYPE_CONVERTER}.
 *   <li>The Java Entity class must be annotated with {@link ConvertibleToProto}.
 * </ul>
 *
 * <p>All safe converters are tested using {@link SafeProtoConverterTest}. Therefore, unless your
 * Entity class has a {@code defaults()} method, or other methods besides simple getters and
 * setters, there is no need to explicitly test your safe converter.
 */
@Immutable
public interface SafeProtoConverter<P extends MessageLite, C> extends ProtoConverter<P, C> {

  Class<P> getProtoClass();

  Class<C> getEntityClass();
}
