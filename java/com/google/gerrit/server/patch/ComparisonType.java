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

import static com.google.gerrit.server.ioutil.BasicSerialization.readVarInt32;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeVarInt32;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

/** Relation between the old and new commits used in the diff. */
@AutoValue
public abstract class ComparisonType {

  /** 1-based parent */
  abstract Optional<Integer> parentNum();

  abstract boolean autoMerge();

  public static ComparisonType againstOtherPatchSet() {
    return new AutoValue_ComparisonType(Optional.empty(), false);
  }

  public static ComparisonType againstParent(int parentNum) {
    return new AutoValue_ComparisonType(Optional.of(parentNum), false);
  }

  public static ComparisonType againstAutoMerge() {
    return new AutoValue_ComparisonType(Optional.empty(), true);
  }

  static ComparisonType create(Optional<Integer> parent, boolean automerge) {
    return new AutoValue_ComparisonType(parent, automerge);
  }

  public boolean isAgainstParentOrAutoMerge() {
    return isAgainstParent() || isAgainstAutoMerge();
  }

  public boolean isAgainstParent() {
    return parentNum().isPresent();
  }

  public boolean isAgainstAutoMerge() {
    return autoMerge();
  }

  public int getParentNum() {
    Integer parentNum = parentNum().isPresent() ? parentNum().get() : null;
    requireNonNull(parentNum);
    return parentNum;
  }

  void writeTo(OutputStream out) throws IOException {
    writeVarInt32(out, parentNum().isPresent() ? parentNum().get() : 0);
    writeVarInt32(out, autoMerge() ? 1 : 0);
  }

  static ComparisonType readFrom(InputStream in) throws IOException {
    int p = readVarInt32(in);
    Optional<Integer> parentNum = p > 0 ? Optional.of(p) : Optional.empty();
    boolean autoMerge = readVarInt32(in) != 0;
    return create(parentNum, autoMerge);
  }
}
