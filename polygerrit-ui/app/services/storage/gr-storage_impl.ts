/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import {Finalizable} from '../registry';

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

  getAllKeys() {
    return Object.keys(this.storage);
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
