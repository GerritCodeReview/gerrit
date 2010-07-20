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

import com.google.gwtorm.client.Column;

import java.util.Collections;
import java.util.List;

public class GwtOrmReplaceEdit extends GwtOrmBaseEdit {
  @Column(id = 1)
  protected List<GwtOrmBaseEdit> internalEdits;

  public GwtOrmReplaceEdit(int beginA, int endA, int beginB, int endB,
      List<GwtOrmBaseEdit> internalEdits) {
    super(beginA, endA, beginB, endB);
    this.internalEdits = Collections.unmodifiableList(internalEdits);
  }

  public GwtOrmReplaceEdit(GwtOrmBaseEdit baseEdit,
      List<GwtOrmBaseEdit> internalEdits) {
    super(baseEdit.beginA, baseEdit.endA, baseEdit.beginB, baseEdit.endB);
    this.internalEdits = Collections.unmodifiableList(internalEdits);
  }

  public List<GwtOrmBaseEdit> getInternalEdits() {
    return internalEdits;
  }
}
