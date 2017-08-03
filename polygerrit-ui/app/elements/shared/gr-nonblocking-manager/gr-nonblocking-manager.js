// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  /**
   * @typedef {Object} NonblockingStatus
   * @property {string?} action Descriptive text of the nonblocking action
   *     taking place. If null or undefined, no status is to be displayed
   *     globally. Text may contain a ${t} placeholder, wh ich will be replaced
   *     by the count of seconds remaining for this status as given by
   *     forMillis.
   * @property {string?} update Additional descriptive text to display below
   *     the action in the global display. Text may contain a ${t} placeholder,
   *     which will be replaced by the count of seconds remaining for this
   *     status as given by forMillis.
   * @property {number?} forMillis Lifetime of this status in milliseconds.
   *     The global display will update once per second to replace any ${t}
   *     placeholder in text.
   * @property {boolean?} done If true, indicates that all work for the
   *     action has completed.
   */

  var _nextManagerId = 0;
  var _globalNonblockingManager = new NonblockingManager();

  /**
   */
  function NonblockingManager(opt_id) {
    this._counter = 0;
    this._id = opt_id || _nextManagerId++;
    this._listeners = new Map();
    this.actionStatuses = new Map();

    if (!_globalNonblockingManager) {
      _globalNonblockingManager = {};
      _globalNonblockingManager = new NonblockingManager();
      _globalNonblockingManager._parent = null;
    }
    this._parent = _globalNonblockingManager;
  }

  Object.assign(NonblockingManager.prototype, {
    /**
     * @param {string} actionId Identifier of nonblocking action
     * @param {NonblockingStatus} status Initial status
     */
    updateActionStatus(actionId, status) {
      console.log('update action status:', actionId, status);
      if (status.done) {
        this.actionStatuses.delete(actionId);
      } else {
        this.actionStatuses.set(actionId, status);
      }
      if (this._parent) {
        this._parent.updateActionStatus(this._id + ':' + actionId, status);
      }
      this._broadcastUpdateActionStatus(actionId, status);
    },

    _broadcastUpdateActionStatus(actionId, status) {
      console.log('broadcasting to:', this._listeners);
      this._listeners.forEach(f => f(actionId, status));
    },

    mirrorParent() {
      this.actionStatuses = this._parent.actionStatuses;
      this.updateActionStatus = () => {
        throw new Error(
            'cannot update NonblockingManager after calling mirrorParent');
      };
      this._parent.addListener(this, '_broadcastUpdateActionStatus');
    },

    addListener(listener, propertyName) {
      this._listeners.set([listener, propertyName], (actionId, status) => {
        listener[propertyName].call(listener, actionId, status);
      });
    },

    removeListener(listener, propertyName) {
      this._listeners.delete([listener, propertyName]);
    },

    countPending(opt_selector) {
      const selector = opt_selector || (_ => true);
      let count = 0;
      const x = this.actionStatuses.forEach((status, actionId) => {
        if (selector(actionId, status)) {
          count++;
        }
      });
      return count;
    },
  });

  Polymer({
    is: 'gr-nonblocking-manager',

    properties: {
      global: {
        type: Boolean,
      },
      manager: {
        type: Object,
        value() {
          return new NonblockingManager();
        },
      },
    },

    attached() {
      this.manager.addListener(this, '_onUpdate');
    },

    detached() {
      this.manager.removeListener(this, '_onUpdate');
    },

    ready() {
      if (this.global) {
        this.manager.mirrorParent();
      }
    },

    _onUpdate(actionId, status) {
      this.fire('update-action-status', {actionId, status});
    },

    countPending(opt_selector) {
      return this.manager.countPending(opt_selector);
    },

    updateActionStatus(actionId, status) {
      this.manager.updateActionStatus(actionId, status);
    },
  });
})();
