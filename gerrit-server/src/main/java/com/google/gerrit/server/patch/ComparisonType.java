// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.server.ioutil.BasicSerialization.readVarInt32;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeVarInt32;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ComparisonType {

  /** 1-based parent */
  private final Integer parentNum;

  private final boolean autoMerge;

  public static ComparisonType againstOtherPatchSet() {
    return new ComparisonType(null, false);
  }

  public static ComparisonType againstParent(int parentNum) {
    return new ComparisonType(parentNum, false);
  }

  public static ComparisonType againstAutoMerge() {
    return new ComparisonType(null, true);
  }

  private ComparisonType(Integer parentNum, boolean autoMerge) {
    this.parentNum = parentNum;
    this.autoMerge = autoMerge;
  }

  public boolean isAgainstParentOrAutoMerge() {
    return isAgainstParent() || isAgainstAutoMerge();
  }

  public boolean isAgainstParent() {
    return parentNum != null;
  }

  public boolean isAgainstAutoMerge() {
    return autoMerge;
  }

  public int getParentNum() {
    checkNotNull(parentNum);
    return parentNum;
  }

  void writeTo(OutputStream out) throws IOException {
    writeVarInt32(out, parentNum != null ? parentNum : 0);
    writeVarInt32(out, autoMerge ? 1 : 0);
  }

  static ComparisonType readFrom(InputStream in) throws IOException {
    int p = readVarInt32(in);
    Integer parentNum = p > 0 ? p : null;
    boolean autoMerge = readVarInt32(in) != 0;
    return new ComparisonType(parentNum, autoMerge);
  }
}
