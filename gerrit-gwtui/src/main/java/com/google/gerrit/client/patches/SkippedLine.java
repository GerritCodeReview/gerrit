// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.client.patches;

public class SkippedLine {

  private int a;
  private int b;
  private int sz;

  public SkippedLine(int startA, int startB, int size) {
    a = startA;
    b = startB;
    sz = size;
  }

  public int getStartA() {
    return a;
  }

  public int getStartB() {
    return b;
  }

  public int getSize() {
    return sz;
  }

  public void incrementStart(int n) {
    a += n;
    b += n;
    reduceSize(n);
  }

  public void reduceSize(int n) {
    sz -= n;
  }
}
