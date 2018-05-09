// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.lucene;

import org.apache.lucene.util.InfoStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Lucene logging facility aimed for development purpose only */
public class LuceneInfoStream extends InfoStream {
  private static final Logger log = LoggerFactory.getLogger(LuceneInfoStream.class);

  @Override
  public void message(String component, String message) {
    log.trace("{} {}: {}", Thread.currentThread().getName(), component, message);
  }

  @Override
  public boolean isEnabled(String component) {
    return log.isTraceEnabled();
  }

  @Override
  public void close() {}
}
