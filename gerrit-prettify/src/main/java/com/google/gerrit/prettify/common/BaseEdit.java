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

package com.google.gerrit.prettify.common;

import com.google.gwtorm.client.Column;

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.ReplaceEdit;
import org.eclipse.jgit.diff.Edit.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BaseEdit {
  @Column(id = 1)
  protected int beginA;

  @Column(id = 2)
  protected int endA;

  @Column(id = 3)
  protected int beginB;

  @Column(id = 4)
  protected int endB;

  protected BaseEdit() {
  }

  public BaseEdit(int beginA, int endA, int beginB, int endB) {
    this.beginA = beginA;
    this.endA = endA;
    this.beginB = beginB;
    this.endB = endB;
  }

  public BaseEdit(int beginA, int beginB) {
    this(beginA, beginA, beginB, beginB);
  }

  public int getBeginA() {
    return beginA;
  }

  public int getEndA() {
    return endA;
  }

  public int getBeginB() {
    return beginB;
  }

  public int getEndB() {
    return endB;
  }

  public final Type getType() {
    if (beginA == endA) {
      if (beginB < endB) {
        return Type.INSERT;
      }
      if (beginB == endB) {
        return Type.EMPTY;
      }
    }
    if (beginA < endA && beginB == endB) {
      return Type.DELETE;
    }
    return Type.REPLACE;
  }

  public static BaseEdit fromEdit(Edit e) {
    BaseEdit goe =
        new BaseEdit(e.getBeginA(), e.getEndA(), e.getBeginB(), e.getEndB());
    if (e instanceof ReplaceEdit) {
      ReplaceEdit re = (ReplaceEdit) e;
      List<BaseEdit> intlEdits = BaseEdit.fromEditList(re.getInternalEdits());
      goe = new com.google.gerrit.prettify.common.ReplaceEdit(goe, intlEdits);
    }
    return goe;
  }

  public static List<BaseEdit> fromEditList(List<Edit> edits) {
    ArrayList<BaseEdit> gwtOrmEdits = new ArrayList<BaseEdit>(edits.size());
    for (Edit e : edits) {
      gwtOrmEdits.add(fromEdit(e));
    }
    return Collections.unmodifiableList(gwtOrmEdits);
  }

  public Edit toEdit() {
    return toEdit(this);
  }

  public static Edit toEdit(BaseEdit goe) {
    Edit e = new Edit(goe.beginA, goe.endA, goe.beginB, goe.endB);
    if (goe instanceof com.google.gerrit.prettify.common.ReplaceEdit) {
      List<BaseEdit> intlEdits =
          ((com.google.gerrit.prettify.common.ReplaceEdit) goe).internalEdits;
      e = new ReplaceEdit(e, toEditList(intlEdits));
    }
    return e;
  }

  public static List<Edit> toEditList(List<BaseEdit> gwtOrmEdits) {
    ArrayList<Edit> edits = new ArrayList<Edit>(gwtOrmEdits.size());
    for (BaseEdit goe : gwtOrmEdits) {
      edits.add(toEdit(goe));
    }
    return Collections.unmodifiableList(edits);
  }
}
