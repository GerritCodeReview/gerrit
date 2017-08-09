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

  function ActionQueue() {
    this._items = [];
    this._changeListeners = [];

    this._onClearResolve = null;
    this._onClearPromise = null;
    this._initOnClear();
  };

  ActionQueue.prototype.enqueue = function(action, state) {
    return new Promise(resolve => {
      this._items.push({
        action,
        state,
        resolve,
        promise: null,
      });

      if (this._items.length === 1) {
        console.log('activating');
        this._step();
      }
    });
  };

  ActionQueue.prototype._step = function() {
    this._notifyListeners();

    if (!this._items.length) { return; }

    console.log('stepping');
    const item = this._items[0];
    item.promise = item.action().then(result => {
      item.resolve(result);
      this._items.shift();
      this._step();
    });
  };

  ActionQueue.prototype._notifyListeners = function() {
    this._changeListeners.forEach(listener => listener());

    if (!this._items.length) {
      this._onClearResolve();
      this._initOnClear();
      console.log('clear');
    }
  };

  ActionQueue.prototype._initOnClear = function() {
    this._onClearPromise = new Promise(resolve => {
      this._onClearResolve = resolve;
    });
  };

  ActionQueue.prototype.awaitClear = function() {
    return this._onClearPromise;
  };

  ActionQueue.prototype.getLength = function() {
    return this._items.length;
  };

  ActionQueue.prototype.addListener = function(fn) {
    this._changeListeners.push(fn);
  };







  Polymer({
    is: 'gr-draft-api',
    properties: {
      _commentActionQueues: {
        type: Object,
        value: {}, // Intended shared property.
      },
    },

    saveDraft(changeNum, patchNum, comment) {
      return this._enqueue(changeNum, patchNum, comment, () =>
        this.$.restAPI.saveDiffDraft(changeNum, patchNum, comment)
            .then(response => {
              if (!response.ok) { return response; }
              return this.$.restAPI.getResponseObject(response).then(obj => {
                obj.__draft = true;
                // Maintain the ephemeral draft ID for identification by other
                // elements.
                if (comment.__draftID) {
                  obj.__draftID = comment.__draftID;
                }
                return obj;
              });
            })
      );
    },

    discardDraft(changeNum, patchNum, comment) {
      return this._enqueue(changeNum, patchNum, comment, () =>
          this.$.restAPI.deleteDiffDraft(changeNum, patchNum, comment)
      );
    },

    /**
     * Enqueue an action for the given comment.
     * @param {!number} changeNum
     * @param {!number} patchNum
     * @param {!object} comment The comment object as it will appear after the
     *     action has been completed.
     * @param {!function(): Promise} Action a function to perform the action
     *     that returns a promise.
     * @return {Promise}
     */
    _enqueue(changeNum, patchNum, comment, action) {
      const key = this._getDraftKey(changeNum, patchNum, comment);

      if (!this._commentActionQueues[key]) {
        this._commentActionQueues[key] = new ActionQueue();
      }
      return this._commentActionQueues[key].enqueue(action, comment);
    },

    _getDraftKey(changeNum, patchNum, comment) {
      let key = [changeNum, patchNum, comment.path, comment.line || '']
          .join(':');
      if (comment.range) {
        key += ':' + [
          comment.range.start_line,
          comment.range.start_character,
          comment.range.end_character,
          comment.range.end_line,
        ].join('-');
      }
      return key;
    },
  });
})();
