// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.base.Function;
import com.google.common.collect.Ordering;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;

class LabelOrdering {
  public static Ordering<String> create(final LabelTypes labelTypes) {
    return Ordering.natural().nullsLast().onResultOf(
        new Function<String, Short>() {
          @Override
          public Short apply(String n) {
            LabelType lt = labelTypes.byLabel(n);
            return lt != null ? lt.getPosition() : null;
          }
        }).compound(Ordering.natural());
  }

  private LabelOrdering() {
  }
}
