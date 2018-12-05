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

package com.google.gerrit.server.project.testing;

import com.google.gerrit.common.data.LabelFunction;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import java.util.Arrays;

public class TestLabels {
  public static LabelType codeReview() {
    return label(
        "Code-Review",
        value(2, "Looks good to me, approved"),
        value(1, "Looks good to me, but someone else must approve"),
        value(0, "No score"),
        value(-1, "I would prefer this is not merged as is"),
        value(-2, "This shall not be merged"));
  }

  public static LabelType verified() {
    return label("Verified", value(1, "Verified"), value(0, "No score"), value(-1, "Fails"));
  }

  public static LabelType patchSetLock() {
    LabelType label =
        label("Patch-Set-Lock", value(1, "Patch Set Locked"), value(0, "Patch Set Unlocked"));
    label.setFunction(LabelFunction.PATCH_SET_LOCK);
    return label;
  }

  public static LabelType labelLock() {
    LabelType label = label("Label-Lock", value(1, "Labels Locked"), value(0, "Labels Unlocked"));
    label.setFunction(LabelFunction.LABEL_LOCK);
    return label;
  }

  public static LabelValue value(int value, String text) {
    return new LabelValue((short) value, text);
  }

  public static LabelType label(String name, LabelValue... values) {
    return new LabelType(name, Arrays.asList(values));
  }

  private TestLabels() {}
}
