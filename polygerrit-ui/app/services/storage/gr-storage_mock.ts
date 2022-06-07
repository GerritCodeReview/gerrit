/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {NumericChangeId} from '../../types/common';
import {StorageLocation, StorageObject, StorageService} from './gr-storage';

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
  finalize(): void {},
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

  eraseEditableContentItem(key: string): void {
    storage.delete(getEditableContentKey(key));
  },

  eraseEditableContentItemsForChangeEdit(changeNum?: NumericChangeId): void {
    for (const key of Array.from(storage.keys())) {
      if (key.startsWith(`editablecontent:c${changeNum}_ps`)) {
        this.eraseEditableContentItem(key.replace('editablecontent:', ''));
      }
    }
  },
};
