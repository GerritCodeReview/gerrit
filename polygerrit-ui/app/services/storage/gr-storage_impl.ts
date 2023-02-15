/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  StoredContentItem,
  StorageService,
  LatestPatchsetNumberSeenItem,
  StorageItem,
} from './gr-storage';
import {Finalizable} from '../registry';
import {NumericChangeId, PatchSetNumber} from '../../types/common';
import {define} from '../../models/dependency';

export const DURATION_DAY = 24 * 60 * 60 * 1000;

// Clean up old entries no more frequently than one day.
const CLEANUP_THROTTLE_INTERVAL = DURATION_DAY;

const CLEANUP_PREFIXES_MAX_AGE_MAP = new Map<string, number>();
CLEANUP_PREFIXES_MAX_AGE_MAP.set('draft', DURATION_DAY);
CLEANUP_PREFIXES_MAX_AGE_MAP.set('editablecontent', DURATION_DAY);

export const storageServiceToken = define<StorageService>('storage-service');

export class GrStorageService implements StorageService, Finalizable {
  private lastCleanup = 0;

  // visible for testing
  storage = window.localStorage;

  // visible for testing
  exceededQuota = false;

  finalize() {}

  getEditableContentItem(key: string): StoredContentItem | null {
    this.cleanupItems();
    return this.getObject(this.getEditableContentKey(key));
  }

  setEditableContentItem(key: string, message: string) {
    const item: StoredContentItem = {
      updated: Date.now(),
      message,
    };
    this.setObject(this.getEditableContentKey(key), item);
  }

  eraseEditableContentItem(key: string) {
    this.storage.removeItem(this.getEditableContentKey(key));
  }

  /**
   * Deletes all keys for cached edits.
   *
   * @param changeNum
   */
  eraseEditableContentItemsForChangeEdit(changeNum?: NumericChangeId) {
    if (!changeNum) return;

    // Fetch all keys and then match them up to the keys we want.
    for (const key of Object.keys(this.storage)) {
      // Only delete the value that starts with editablecontent:c${changeNum}_ps
      // to prevent deleting unrelated keys.
      if (key.startsWith(`editablecontent:c${changeNum}_ps`)) {
        // We have to remove editablecontent: from the string as it is
        // automatically added to the string within the storage.
        this.eraseEditableContentItem(key.replace('editablecontent:', ''));
      }
    }
  }

  getLatestPatchsetNumberSeen(
    changeId: NumericChangeId
  ): LatestPatchsetNumberSeenItem | null {
    console.log('getting changeId', changeId);
    this.cleanupItems();
    const result: LatestPatchsetNumberSeenItem | null = this.getObject(
      this.getLastPatchsetSeenKey(changeId)
    );
    console.log('stored', result);
    return result;
  }

  setLatestPatchsetNumberSeen(
    changeId: NumericChangeId,
    patchsetNumber: PatchSetNumber
  ): void {
    console.log(
      'setting changeId',
      changeId,
      'to patchsetNumber',
      patchsetNumber
    );
    const currentStoredItem = this.getLatestPatchsetNumberSeen(changeId);
    if (
      currentStoredItem?.patchsetNumber &&
      currentStoredItem.patchsetNumber >= patchsetNumber
    ) {
      console.log('ignoring set request');
      return;
    }
    const item: LatestPatchsetNumberSeenItem = {
      updated: Date.now(),
      changeId,
      patchsetNumber,
    };
    this.setObject(this.getLastPatchsetSeenKey(changeId), item);
  }

  // visible for testing
  getEditableContentKey(key: string): string {
    return `editablecontent:${key}`;
  }

  getLastPatchsetSeenKey(changeId: NumericChangeId): string {
    return `lastpatchsetseen:${changeId}`;
  }

  // visible for testing
  cleanupItems() {
    // Throttle cleanup to the throttle interval.
    if (
      this.lastCleanup &&
      Date.now() - this.lastCleanup < CLEANUP_THROTTLE_INTERVAL
    ) {
      return;
    }
    this.lastCleanup = Date.now();

    Object.keys(this.storage).forEach(key => {
      const entries = CLEANUP_PREFIXES_MAX_AGE_MAP.entries();
      for (const [prefix, expiration] of entries) {
        if (key.startsWith(prefix)) {
          const item = this.getObject(key);
          if (!item || Date.now() - item.updated > expiration) {
            this.storage.removeItem(key);
          }
        }
      }
    });
  }

  private getObject<T extends StorageItem>(key: string): T | null {
    const serial = this.storage.getItem(key);
    if (!serial) {
      return null;
    }
    return JSON.parse(serial) as T;
  }

  private setObject(key: string, obj: StorageItem) {
    if (this.exceededQuota) {
      return;
    }
    try {
      console.log('setObject', key, JSON.stringify(obj));
      this.storage.setItem(key, JSON.stringify(obj));
    } catch (exc: unknown) {
      if (exc instanceof DOMException) {
        // Catch for QuotaExceededError and disable writes on local storage the
        // first time that it occurs.
        if (exc.code === 22) {
          this.exceededQuota = true;
          console.warn('Local storage quota exceeded: disabling');
          return;
        }
      }
      throw exc;
    }
  }
}
