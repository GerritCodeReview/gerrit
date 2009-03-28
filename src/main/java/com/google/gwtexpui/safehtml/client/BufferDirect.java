// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gwtexpui.safehtml.client;

final class BufferDirect implements Buffer {
  private final StringBuilder strbuf = new StringBuilder();

  boolean isEmpty() {
    return strbuf.length() == 0;
  }

  public void append(final boolean v) {
    strbuf.append(v);
  }

  public void append(final char v) {
    strbuf.append(v);
  }

  public void append(final int v) {
    strbuf.append(v);
  }

  public void append(final long v) {
    strbuf.append(v);
  }

  public void append(final float v) {
    strbuf.append(v);
  }

  public void append(final double v) {
    strbuf.append(v);
  }

  public void append(final String v) {
    strbuf.append(v);
  }

  @Override
  public String toString() {
    return strbuf.toString();
  }
}
