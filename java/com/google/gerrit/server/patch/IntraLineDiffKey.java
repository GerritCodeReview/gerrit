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

import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.InlineMe;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import java.io.Serializable;
import org.eclipse.jgit.lib.ObjectId;

public record IntraLineDiffKey(ObjectId blobA, ObjectId blobB, Whitespace whitespace)
    implements Serializable {
  public IntraLineDiffKey {
    requireNonNull(blobA, "blobA");
    requireNonNull(blobB, "blobB");
    requireNonNull(whitespace, "whitespace");
  }

  @InlineMe(replacement = "this.blobA()")
  public ObjectId getBlobA() {
    return blobA();
  }

  @InlineMe(replacement = "this.blobB()")
  public ObjectId getBlobB() {
    return blobB();
  }

  @InlineMe(replacement = "this.whitespace()")
  public Whitespace getWhitespace() {
    return whitespace();
  }

  public static final long serialVersionUID = 13L;

  public static IntraLineDiffKey create(ObjectId aId, ObjectId bId, Whitespace whitespace) {
    return new IntraLineDiffKey(aId, bId, whitespace);
  }

}
