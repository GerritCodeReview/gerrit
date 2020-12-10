/**
 * @fileoverview The API of Gerrit's diff viewer, gr-diff.
 *
 * This includes some types which are also defined as part of Gerrit's JSON API
 * which are used as inputs to gr-diff.
 *
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Diff type in preferences
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#preferences-input
 */
export enum DiffViewMode {
  SIDE_BY_SIDE = 'SIDE_BY_SIDE',
  UNIFIED = 'UNIFIED_DIFF',
}
