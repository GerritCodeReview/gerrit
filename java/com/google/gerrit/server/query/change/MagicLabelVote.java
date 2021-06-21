// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.gerrit.entities.LabelType;
import java.util.Locale;

@AutoValue
public abstract class MagicLabelVote {
  public static MagicLabelVote parseWithEquals(String text) {
    checkArgument(!Strings.isNullOrEmpty(text), "Empty label vote");
    int e = text.lastIndexOf('=');
    checkArgument(e >= 0, "Label vote missing '=': %s", text);
    String label = text.substring(0, e);
    String voteValue = text.substring(e + 1);
    return create(label, MagicLabelValue.valueOf(voteValue));
  }

  public static MagicLabelVote create(String label, MagicLabelValue value) {
    return new AutoValue_MagicLabelVote(LabelType.checkNameInternal(label), value);
  }

  public abstract String label();

  public abstract MagicLabelValue value();

  public String formatLabel() {
    return label().toLowerCase(Locale.US) + "=" + value().name();
  }
}
