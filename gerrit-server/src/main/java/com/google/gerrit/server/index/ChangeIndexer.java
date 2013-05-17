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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.index;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/** Runnable for (re)indexing a change document. */
public class ChangeIndexer implements Runnable {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeIndexer.class);

  public static interface Factory {
    ChangeIndexer create(Change change);
  }

  private final ChangeIndex index;
  private final Change change;

  @Inject
  ChangeIndexer(ChangeIndex index,
      @Assisted Change change) {
    this.index = index;
    this.change = change;
  }

  @Override
  public void run() {
    ChangeData cd = new ChangeData(change);
    try {
      index.replace(cd);
    } catch (IOException e) {
      log.error("Error indexing change", e);
    }
  }

  @Override
  public String toString() {
    return "index-change";
  }
}
