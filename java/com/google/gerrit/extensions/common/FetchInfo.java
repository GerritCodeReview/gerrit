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

import com.google.gerrit.proto.ProtoField;
import java.util.Map;
import java.util.Objects;

public class FetchInfo {
  @ProtoField(protoTag = 1)
  public String url;

  @ProtoField(protoTag = 2)
  public String ref;

  @ProtoField(protoTag = 3)
  public Map<String, String> commands;

  public FetchInfo(String url, String ref) {
    this.url = url;
    this.ref = ref;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof FetchInfo) {
      FetchInfo fetchInfo = (FetchInfo) o;
      return Objects.equals(url, fetchInfo.url)
          && Objects.equals(ref, fetchInfo.ref)
          && Objects.equals(commands, fetchInfo.commands);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(url, ref, commands);
  }

  public FetchInfo() {}
}
