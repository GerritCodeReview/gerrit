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

package com.google.gerrit.client.admin;

import com.google.gwt.event.shared.GwtEvent;

/** Deleting table rows event*/
public class TableDeleteEvent extends GwtEvent<TableDeleteHandler> {
  private static final Type<TableDeleteHandler> TYPE =
      new Type<TableDeleteHandler>();

  private final String rowText;

  public TableDeleteEvent(final String rowText) {
    this.rowText = rowText;
  }

  public static Type<TableDeleteHandler> getType() {
    return TYPE;
  }

  /** @returns The deleted row text. */
  public String getRowText() {
    return rowText;
  }

  @Override
  protected void dispatch(TableDeleteHandler handler) {
    handler.onDeleteRow(this);
  }

  @Override
  public Type<TableDeleteHandler> getAssociatedType() {
    return TYPE;
  }
}
