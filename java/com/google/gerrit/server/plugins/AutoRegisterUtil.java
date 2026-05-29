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

package com.google.gerrit.server.plugins;

import com.google.gerrit.extensions.annotations.Export;
import com.google.inject.internal.UniqueAnnotations;
import java.lang.annotation.Annotation;

public final class AutoRegisterUtil {

  public static Annotation calculateBindAnnotation(Class<Object> impl) {
    Annotation n = impl.getAnnotation(Export.class);
    if (n == null) {
      n = impl.getAnnotation(javax.inject.Named.class);
    }
    if (n == null) {
      n = impl.getAnnotation(com.google.inject.name.Named.class);
    }
    if (n == null) {
      n = UniqueAnnotations.create();
    }
    return n;
  }

  private AutoRegisterUtil() {
    // hide default constructor
  }
}
