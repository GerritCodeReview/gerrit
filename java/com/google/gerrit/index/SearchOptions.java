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

package com.google.gerrit.index;

/** Tokenization options enabled on {@link StoredSchemaField}. */
public enum SearchOptions {
  /** Enables range queries on the field. */
  RANGE,
  /** Enables prefix-match search on the field. */
  PREFIX,

  /** Enables exact-match search on the field. */
  EXACT,
  /** Enables fuzzy-match search on the field. */
  FULL_TEXT,

  /** The field can not be searched and is only returned as a payload from the index. */
  STORE_ONLY,
}
