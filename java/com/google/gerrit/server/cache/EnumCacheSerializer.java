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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Enums;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class EnumCacheSerializer<E extends Enum<E>> implements CacheSerializer<E> {
  private final Class<E> clazz;

  public EnumCacheSerializer(Class<E> clazz) {
    this.clazz = clazz;
  }

  @Override
  public void serialize(E object, OutputStream out) throws IOException {
    out.write(object.name().getBytes(UTF_8));
  }

  @Override
  public E deserialize(InputStream in) throws IOException {
    String name = CharStreams.toString(new InputStreamReader(in, UTF_8));
    return Enums.getIfPresent(clazz, name)
        .toJavaUtil()
        .orElseThrow(() -> new IOException("Invalid " + clazz.getName() + " value: " + name));
  }

  @Override
  public int sizeHint(E object) {
    return object.name().length();
  }
}
