// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

import java.sql.Timestamp;

public abstract class Comment {
  public String id;
  public String path;
  public Side side;
  public int line;
  public Range range;
  public String inReplyTo;
  public Timestamp updated;
  public String message;

  public static enum Side {
    PARENT, REVISION
  }

  public static class Range {
    public int startLine;
    public int startCharacter;
    public int endLine;
    public int endCharacter;
  }
}
