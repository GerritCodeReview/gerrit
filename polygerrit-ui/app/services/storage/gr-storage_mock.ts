/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {NumericChangeId} from '../../types/common';
import {StorageObject, StorageService} from './gr-storage';

const storage = new Map<string, StorageObject>();

const getEditableContentKey = (key: string): string => `editablecontent:${key}`;

export function cleanUpStorage() {
  storage.clear();
}

export const grStorageMock: StorageService = {
  finalize(): void {},

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
