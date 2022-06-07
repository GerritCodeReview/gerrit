/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {StorageLocation, StorageObject, StorageService} from './gr-storage';
import {Finalizable} from '../registry';
import {NumericChangeId} from '../../types/common';

export const DURATION_DAY = 24 * 60 * 60 * 1000;

// Clean up old entries no more frequently than one day.
const CLEANUP_THROTTLE_INTERVAL = DURATION_DAY;

const CLEANUP_PREFIXES_MAX_AGE_MAP = new Map<string, number>();
CLEANUP_PREFIXES_MAX_AGE_MAP.set('draft', DURATION_DAY);
CLEANUP_PREFIXES_MAX_AGE_MAP.set('editablecontent', DURATION_DAY);

export class GrStorageService implements StorageService, Finalizable {
  private lastCleanup = 0;

  private readonly storage = window.localStorage;

  private exceededQuota = false;

  finalize() {}

  getDraftComment(location: StorageLocation): StorageObject | null {
    this.cleanupItems();
    return this.getObject(this.getDraftKey(location));
  }

  setDraftComment(location: StorageLocation, message: string) {
    const key = this.getDraftKey(location);
    this.setObject(key, {message, updated: Date.now()});
  }

  eraseDraftComment(location: StorageLocation) {
    const key = this.getDraftKey(location);
    this.storage.removeItem(key);
  }

  getEditableContentItem(key: string): StorageObject | null {
    this.cleanupItems();
    return this.getObject(this.getEditableContentKey(key));
  }

  setEditableContentItem(key: string, message: string) {
    this.setObject(this.getEditableContentKey(key), {
      message,
      updated: Date.now(),
    });
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

  private getDraftKey(location: StorageLocation): string {
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
  }

  private getEditableContentKey(key: string): string {
    return `editablecontent:${key}`;
  }

  private cleanupItems() {
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

  private getObject(key: string): StorageObject | null {
    const serial = this.storage.getItem(key);
    if (!serial) {
      return null;
    }
    return JSON.parse(serial) as StorageObject;
  }

  private setObject(key: string, obj: StorageObject) {
    if (this.exceededQuota) {
      return;
    }
    try {
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
