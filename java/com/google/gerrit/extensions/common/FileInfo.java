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

import java.util.Objects;

public class FileInfo {
  public Character status;
  public Boolean binary;
  public String oldPath;
  public Integer linesInserted;
  public Integer linesDeleted;
  public long sizeDelta;
  public long size;

  @Override
  public boolean equals(Object o) {
    if (o instanceof FileInfo) {
      FileInfo fileInfo = (FileInfo) o;
      return Objects.equals(status, fileInfo.status)
          && Objects.equals(binary, fileInfo.binary)
          && Objects.equals(oldPath, fileInfo.oldPath)
          && Objects.equals(linesInserted, fileInfo.linesInserted)
          && Objects.equals(linesDeleted, fileInfo.linesDeleted)
          && Objects.equals(sizeDelta, fileInfo.sizeDelta)
          && Objects.equals(size, fileInfo.size);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, binary, oldPath, linesInserted, linesDeleted, sizeDelta, size);
  }
}
