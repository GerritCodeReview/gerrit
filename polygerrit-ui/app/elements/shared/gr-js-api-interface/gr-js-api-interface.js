// Copyright (C) 2016 The Android Open Source Project
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

  const EventType = {
    HISTORY: 'history',
    LABEL_CHANGE: 'labelchange',
    SHOW_CHANGE: 'showchange',
    SUBMIT_CHANGE: 'submitchange',
    COMMIT_MSG_EDIT: 'commitmsgedit',
    COMMENT: 'comment',
    REVERT: 'revert',
    POST_REVERT: 'postrevert',
  };

  const Element = {
    CHANGE_ACTIONS: 'changeactions',
    REPLY_DIALOG: 'replydialog',
  };

  Polymer({
    is: 'gr-js-api-interface',

    properties: {
      _elements: {
        type: Object,
        value: {},  // Shared across all instances.
      },
      _eventCallbacks: {
        type: Object,
        value: {},  // Shared across all instances.
      },
    },

    Element,
    EventType,

    handleEvent(type, detail) {
      Gerrit.awaitPluginsLoaded().then(() => {
        switch (type) {
          case EventType.HISTORY:
            this._handleHistory(detail);
            break;
          case EventType.SHOW_CHANGE:
            this._handleShowChange(detail);
            break;
          case EventType.COMMENT:
            this._handleComment(detail);
            break;
          case EventType.LABEL_CHANGE:
            this._handleLabelChange(detail);
            break;
          default:
            console.warn('handleEvent called with unsupported event type:',
                type);
            break;
        }
      });
    },

    addElement(key, el) {
      this._elements[key] = el;
    },

    getElement(key) {
      return this._elements[key];
    },

    addEventCallback(eventName, callback) {
      if (!this._eventCallbacks[eventName]) {
        this._eventCallbacks[eventName] = [];
      }
      this._eventCallbacks[eventName].push(callback);
    },

    canSubmitChange(change, revision) {
      const submitCallbacks = this._getEventCallbacks(EventType.SUBMIT_CHANGE);
      const cancelSubmit = submitCallbacks.some(callback => {
        try {
          return callback(change, revision) === false;
        } catch (err) {
          console.error(err);
        }
        return false;
      });

      return !cancelSubmit;
    },

    _removeEventCallbacks() {
      for (const k in EventType) {
        this._eventCallbacks[EventType[k]] = [];
      }
    },

    _handleHistory(detail) {
      this._getEventCallbacks(EventType.HISTORY).forEach(cb => {
        try {
          cb(detail.path);
        } catch (err) {
          console.error(err);
        }
      });
    },

    _handleShowChange(detail) {
      this._getEventCallbacks(EventType.SHOW_CHANGE).forEach(cb => {
        const change = detail.change;
        const patchNum = detail.patchNum;
        let revision;
        for (const rev in change.revisions) {
          if (change.revisions[rev]._number == patchNum) {
            revision = change.revisions[rev];
            break;
          }
        }
        try {
          cb(change, revision);
        } catch (err) {
          console.error(err);
        }
      });
    },

    handleCommitMessage(change, msg) {
      this._getEventCallbacks(EventType.COMMIT_MSG_EDIT).forEach(
          cb => {
            try {
              cb(change, msg);
            } catch (err) {
              console.error(err);
            }
          }
      );
    },

    _handleComment(detail) {
      this._getEventCallbacks(EventType.COMMENT).forEach(cb => {
        try {
          cb(detail.node);
        } catch (err) {
          console.error(err);
        }
      });
    },

    _handleLabelChange(detail) {
      this._getEventCallbacks(EventType.LABEL_CHANGE).forEach(cb => {
        try {
          cb(detail.change);
        } catch (err) {
          console.error(err);
        }
      });
    },

    modifyRevertMsg(change, revertMsg, origMsg) {
      this._getEventCallbacks(EventType.REVERT).forEach(callback => {
        try {
          revertMsg = callback(change, revertMsg, origMsg);
        } catch (err) {
          console.error(err);
        }
      });
      return revertMsg;
    },

    getLabelValuesPostRevert(change) {
      let labels = {};
      this._getEventCallbacks(EventType.POST_REVERT).forEach(
          callback => {
            try {
              labels = callback(change);
            } catch (err) {
              console.error(err);
            }
          }
      );
      return labels;
    },

    _getEventCallbacks(type) {
      return this._eventCallbacks[type] || [];
    },
  });
})();
