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

package com.google.gerrit.truth;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import java.util.Map;

/**
 * A Truth subject for maps providing additional methods simplifying tests but missing on Truth's
 * {@link com.google.common.truth.MapSubject}.
 */
public class MapSubject extends com.google.common.truth.MapSubject {

  private final Map<?, ?> map;

  public static MapSubject assertThatMap(Map<?, ?> map) {
    return assertAbout(mapEntries()).that(map);
  }

  public static Subject.Factory<MapSubject, Map<?, ?>> mapEntries() {
    return MapSubject::new;
  }

  private MapSubject(FailureMetadata failureMetadata, Map<?, ?> map) {
    super(failureMetadata, map);
    this.map = map;
  }

  public IterableSubject keys() {
    isNotNull();
    return check("keys()").that(map.keySet());
  }

  public IterableSubject values() {
    isNotNull();
    return check("values()").that(map.values());
  }

  public IntegerSubject size() {
    isNotNull();
    return check("size()").that(map.size());
  }
}
