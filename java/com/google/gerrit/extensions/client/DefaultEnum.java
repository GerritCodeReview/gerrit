// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.extensions.client;

/**
 * This interface should be implemented by enums that want to provide a default value. The default
 * value is used as a fallback while converting enums to / from proto to handle unrecognized values.
 * This is used as a safety net to guard against forward-compatibility issues. See {@link
 * com.google.gerrit.entities.converter.SafeEnumStringConverter}.
 */
public interface DefaultEnum<V> {
  V getDefaultValue();
}
