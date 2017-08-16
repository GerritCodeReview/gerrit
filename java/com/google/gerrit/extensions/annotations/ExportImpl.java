// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.extensions.annotations;

import java.io.Serializable;
import java.lang.annotation.Annotation;

final class ExportImpl implements Export, Serializable {
  private static final long serialVersionUID = 0;
  private final String value;

  ExportImpl(String value) {
    this.value = value;
  }

  @Override
  public Class<? extends Annotation> annotationType() {
    return Export.class;
  }

  @Override
  public String value() {
    return value;
  }

  @Override
  public int hashCode() {
    return (127 * "value".hashCode()) ^ value.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Export && value.equals(((Export) o).value());
  }

  @Override
  public String toString() {
    return "@" + Export.class.getName() + "(value=" + value + ")";
  }
}
