// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.cache.serialize.entities;

import com.google.gerrit.entities.ConfiguredMimeTypes;
import com.google.gerrit.server.cache.proto.Cache;
import java.util.regex.PatternSyntaxException;
import org.eclipse.jgit.errors.InvalidPatternException;

public class ConfiguredMimeTypeSerializer {
  public static ConfiguredMimeTypes.TypeMatcher deserialize(Cache.ConfiguredMimeTypeProto proto) {
    try {
      return proto.getIsRegularExpression()
          ? new ConfiguredMimeTypes.ReType(proto.getType(), proto.getPattern())
          : new ConfiguredMimeTypes.FnType(proto.getType(), proto.getPattern());
    } catch (PatternSyntaxException | InvalidPatternException e) {
      throw new IllegalStateException(e);
    }
  }

  public static Cache.ConfiguredMimeTypeProto serialize(ConfiguredMimeTypes.TypeMatcher value) {
    return Cache.ConfiguredMimeTypeProto.newBuilder()
        .setType(value.getType())
        .setPattern(value.getPattern())
        .setIsRegularExpression(value instanceof ConfiguredMimeTypes.ReType)
        .build();
  }
}
