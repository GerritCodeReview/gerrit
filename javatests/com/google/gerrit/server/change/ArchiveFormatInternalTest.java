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

package com.google.gerrit.server.change;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.gerrit.extensions.client.ArchiveFormat;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ArchiveFormatInternalTest {
  @Test
  public void internalAndExternalArchiveFormatEnumsMatch() throws Exception {
    assertThat(getEnumNames(ArchiveFormatInternal.class))
        .containsExactlyElementsIn(getEnumNames(ArchiveFormat.class));
  }

  private static List<String> getEnumNames(Class<? extends Enum<?>> e) {
    return Arrays.stream(e.getEnumConstants()).map(Enum::name).collect(toList());
  }
}
