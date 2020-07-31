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
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {customElement, property} from '@polymer/decorators';
import {CommentRange, PatchSetNum} from '../../../types/common';

export interface StorageLocation {
  changeNum: number;
  patchNum: PatchSetNum;
  path: string;
  line: number;
  range: CommentRange;
}

export interface StorageObject {
  message?: string;
  updated: number;
}

const DURATION_DAY = 24 * 60 * 60 * 1000;

// Clean up old entries no more frequently than one day.
const CLEANUP_THROTTLE_INTERVAL = DURATION_DAY;

const CLEANUP_PREFIXES_MAX_AGE_MAP = new Map<string, number>();
CLEANUP_PREFIXES_MAX_AGE_MAP.set('respectfultip', 14 * DURATION_DAY);
CLEANUP_PREFIXES_MAX_AGE_MAP.set('draft', DURATION_DAY);
CLEANUP_PREFIXES_MAX_AGE_MAP.set('editablecontent', DURATION_DAY);

declare global {
  interface HTMLElementTagNameMap {
    'gr-storage': GrStorage;
  }
}

export interface GrStorage {
  $: {};
}

@customElement('gr-storage')
export class GrStorage extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  @property({type: Number})
  _lastCleanup = 0;

  @property({type: Object})
  _storage = window.localStorage;

  @property({type: Boolean})
  _exceededQuota = false;

  getDraftComment(location: StorageLocation): StorageObject | null {
    this._cleanupItems();
    return this._getObject(this._getDraftKey(location));
  }

  setDraftComment(location: StorageLocation, message: string) {
    const key = this._getDraftKey(location);
    this._setObject(key, {message, updated: Date.now()});
  }

  eraseDraftComment(location: StorageLocation) {
    const key = this._getDraftKey(location);
    this._storage.removeItem(key);
  }

  getEditableContentItem(key: string): StorageObject | null {
    this._cleanupItems();
    return this._getObject(this._getEditableContentKey(key));
  }

  setEditableContentItem(key: string, message: string) {
    this._setObject(this._getEditableContentKey(key), {
      message,
      updated: Date.now(),
    });
  }

  getRespectfulTipVisibility(): StorageObject | null {
    this._cleanupItems();
    return this._getObject('respectfultip:visibility');
  }

  setRespectfulTipVisibility(delayDays = 0) {
    this._cleanupItems();
    this._setObject('respectfultip:visibility', {
      updated: Date.now() + delayDays * DURATION_DAY,
    });
  }

  eraseEditableContentItem(key: string) {
    this._storage.removeItem(this._getEditableContentKey(key));
  }

  _getDraftKey(location: StorageLocation): string {
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

  _getEditableContentKey(key: string): string {
    return `editablecontent:${key}`;
  }

  _cleanupItems() {
    // Throttle cleanup to the throttle interval.
    if (
      this._lastCleanup &&
      Date.now() - this._lastCleanup < CLEANUP_THROTTLE_INTERVAL
    ) {
      return;
    }
    this._lastCleanup = Date.now();

    Object.keys(this._storage).forEach(key => {
      const entries = CLEANUP_PREFIXES_MAX_AGE_MAP.entries();
      for (const [prefix, expiration] of entries) {
        if (key.startsWith(prefix)) {
          const item = this._getObject(key);
          if (!item || Date.now() - item.updated > expiration) {
            this._storage.removeItem(key);
          }
        }
      }
    });
  }

  _getObject(key: string): StorageObject | null {
    const serial = this._storage.getItem(key);
    if (!serial) {
      return null;
    }
    return JSON.parse(serial);
  }

  _setObject(key: string, obj: StorageObject) {
    if (this._exceededQuota) {
      return;
    }
    try {
      this._storage.setItem(key, JSON.stringify(obj));
    } catch (exc) {
      // Catch for QuotaExceededError and disable writes on local storage the
      // first time that it occurs.
      if (exc.code === 22) {
        this._exceededQuota = true;
        console.warn('Local storage quota exceeded: disabling');
        return;
      } else {
        throw exc;
      }
    }
  }
}
