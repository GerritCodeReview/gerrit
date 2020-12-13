/**
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
import {
  Category,
  CheckRun,
} from '../../elements/plugins/gr-checks-api/gr-checks-api-types';

export function worstCategory(run: CheckRun) {
  const results = run.results ?? [];
  if (results.some(r => r.category === Category.ERROR)) return Category.ERROR;
  if (results.some(r => r.category === Category.WARNING))
    return Category.WARNING;
  if (results.some(r => r.category === Category.INFO)) return Category.INFO;
  return undefined;
}

export function compareByWorstCategory(a: CheckRun, b: CheckRun) {
  return level(worstCategory(b)) - level(worstCategory(a));
}

export function level(cat?: Category) {
  if (!cat) return -1;
  switch (cat) {
    case Category.INFO:
      return 0;
    case Category.WARNING:
      return 1;
    case Category.ERROR:
      return 2;
  }
}
