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

package com.google.gerrit.server.events;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

public final class AutoValueAdapterFactory implements TypeAdapterFactory {

  @SuppressWarnings("unchecked")
  @Override
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
    Class<? super T> rawType = type.getRawType();

    Package rawTypePackage = rawType.getPackage();
    if (rawTypePackage == null) {
      return null;
    }

    String packageName = rawTypePackage.getName();
    String className = rawType.getName().substring(packageName.length() + 1).replace('$', '_');
    String autoValueName = packageName + ".AutoValue_" + className;

    try {
      Class<?> autoValueType = Class.forName(autoValueName);
      return (TypeAdapter<T>) gson.getAdapter(autoValueType);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }
}
