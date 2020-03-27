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
import '../../../scripts/bundled-polymer.js';

import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';

const DURATION_DAY = 24 * 60 * 60 * 1000;

// Clean up old entries no more frequently than one day.
const CLEANUP_THROTTLE_INTERVAL = DURATION_DAY;

const CLEANUP_PREFIXES_MAX_AGE_MAP = {
  // respectfultip has a 14-day expiration
  'respectfultip:': 14 * DURATION_DAY,
  'draft:': DURATION_DAY,
  'editablecontent:': DURATION_DAY,
};

/** @extends Polymer.Element */
class GrStorage extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get is() { return 'gr-storage'; }

  static get properties() {
    return {
      _lastCleanup: Number,
      /** @type {?Storage} */
      _storage: {
        type: Object,
        value() {
          return window.localStorage;
        },
      },
      _exceededQuota: {
        type: Boolean,
        value: false,
      },
    };
  }

  getDraftComment(location) {
    this._cleanupItems();
    return this._getObject(this._getDraftKey(location));
  }

  setDraftComment(location, message) {
    const key = this._getDraftKey(location);
    this._setObject(key, {message, updated: Date.now()});
  }

  eraseDraftComment(location) {
    const key = this._getDraftKey(location);
    this._storage.removeItem(key);
  }

  getEditableContentItem(key) {
    this._cleanupItems();
    return this._getObject(this._getEditableContentKey(key));
  }

  setEditableContentItem(key, message) {
    this._setObject(this._getEditableContentKey(key),
        {message, updated: Date.now()});
  }

  getRespectfulTipVisibility() {
    this._cleanupItems();
    return this._getObject('respectfultip:visibility');
  }

  setRespectfulTipVisibility(delayDays = 0) {
    this._cleanupItems();
    this._setObject(
        'respectfultip:visibility',
        {updated: Date.now() + delayDays * DURATION_DAY}
    );
  }

  eraseEditableContentItem(key) {
    this._storage.removeItem(this._getEditableContentKey(key));
  }

  _getDraftKey(location) {
    const range = location.range ?
      `${location.range.start_line}-${location.range.start_character}` +
            `-${location.range.end_character}-${location.range.end_line}` :
      null;
    let key = ['draft', location.changeNum, location.patchNum, location.path,
      location.line || ''].join(':');
    if (range) {
      key = key + ':' + range;
    }
    return key;
  }

  _getEditableContentKey(key) {
    return `editablecontent:${key}`;
  }

  _cleanupItems() {
    // Throttle cleanup to the throttle interval.
    if (this._lastCleanup &&
        Date.now() - this._lastCleanup < CLEANUP_THROTTLE_INTERVAL) {
      return;
    }
    this._lastCleanup = Date.now();

    let item;
    Object.keys(this._storage).forEach(key => {
      Object.keys(CLEANUP_PREFIXES_MAX_AGE_MAP).forEach(prefix => {
        if (key.startsWith(prefix)) {
          item = this._getObject(key);
          const expiration = CLEANUP_PREFIXES_MAX_AGE_MAP[prefix];
          if (Date.now() - item.updated > expiration) {
            this._storage.removeItem(key);
          }
        }
      });
    });
  }

  _getObject(key) {
    const serial = this._storage.getItem(key);
    if (!serial) { return null; }
    return JSON.parse(serial);
  }

  _setObject(key, obj) {
    if (this._exceededQuota) { return; }
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

customElements.define(GrStorage.is, GrStorage);
