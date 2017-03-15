// Copyright (C) 2017 The Android Open Source Project
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

public class AddressInfo {
  public String name;
  public String email;

  public AddressInfo(String name, String email) {
    this.name = name;
    this.email = email;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof AddressInfo) {
      AddressInfo a = (AddressInfo) o;
      return Objects.equals(name, a.name) && Objects.equals(email, a.email);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, email);
  }
}
