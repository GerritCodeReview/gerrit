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

final class BufferSealElement implements Buffer {
  private final SafeHtmlBuilder shb;

  BufferSealElement(SafeHtmlBuilder safeHtmlBuilder) {
    shb = safeHtmlBuilder;
  }

  @Override
  public void append(boolean v) {
    shb.sealElement().append(v);
  }

  @Override
  public void append(char v) {
    shb.sealElement().append(v);
  }

  @Override
  public void append(double v) {
    shb.sealElement().append(v);
  }

  @Override
  public void append(float v) {
    shb.sealElement().append(v);
  }

  @Override
  public void append(int v) {
    shb.sealElement().append(v);
  }

  @Override
  public void append(long v) {
    shb.sealElement().append(v);
  }

  @Override
  public void append(String v) {
    shb.sealElement().append(v);
  }

  @Override
  public String toString() {
    return shb.sealElement().toString();
  }
}
