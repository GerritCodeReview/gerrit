/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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

import {StorageLocation, StorageObject, StorageService} from './gr-storage';
import {DURATION_DAY} from './gr-storage_impl';

const storage = new Map<string, StorageObject>();

const getDraftKey = (location: StorageLocation): string => {
  const range = location.range
    ? `${location.range.start_line}-${location.range.start_character}` +
      `-${location.range.end_character}-${location.range.end_line}`
    : null;
  let key = [
    'draft',
    location.changeNum,
    location.patchNum,
    location.path,
    location.line || '',
  ].join(':');
  if (range) {
    key = key + ':' + range;
  }
  return key;
};

const getEditableContentKey = (key: string): string => `editablecontent:${key}`;

export function cleanUpStorage() {
  storage.clear();
}

export const grStorageMock: StorageService = {
  getDraftComment(location: StorageLocation): StorageObject | null {
    return storage.get(getDraftKey(location)) ?? null;
  },

  setDraftComment(location: StorageLocation, message: string) {
    const key = getDraftKey(location);
    storage.set(key, {message, updated: Date.now()});
  },

  eraseDraftComment(location: StorageLocation) {
    const key = getDraftKey(location);
    storage.delete(key);
  },

  getEditableContentItem(key: string): StorageObject | null {
    return storage.get(getEditableContentKey(key)) ?? null;
  },

  setEditableContentItem(key: string, message: string): void {
    storage.set(getEditableContentKey(key), {
      message,
      updated: Date.now(),
    });
  },

  getRespectfulTipVisibility(): StorageObject | null {
    return storage.get('respectfultip:visibility') ?? null;
  },

  setRespectfulTipVisibility(delayDays = 0): void {
    storage.set('respectfultip:visibility', {
      updated: Date.now() + delayDays * DURATION_DAY,
    });
  },

  eraseEditableContentItem(key: string): void {
    storage.delete(getEditableContentKey(key));
  },
};
