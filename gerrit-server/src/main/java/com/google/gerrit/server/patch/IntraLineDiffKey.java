// Copyright (C) 2010 The Android Open Source Project
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

import com.google.auto.value.AutoValue;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import java.io.Serializable;
import org.eclipse.jgit.lib.ObjectId;

@AutoValue
public abstract class IntraLineDiffKey implements Serializable {
  public static final long serialVersionUID = 5L;

  public static IntraLineDiffKey create(ObjectId aId, ObjectId bId, Whitespace whitespace) {
    return new AutoValue_IntraLineDiffKey(aId, bId, whitespace);
  }

  public abstract ObjectId getBlobA();

  public abstract ObjectId getBlobB();

  public abstract Whitespace getWhitespace();
}
