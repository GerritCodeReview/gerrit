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

  var EventType = {
    HISTORY: 'history',
    LABEL_CHANGE: 'labelchange',
    SHOW_CHANGE: 'showchange',
    SUBMIT_CHANGE: 'submitchange',
    COMMIT_MSG_EDIT: 'commitmsgedit',
    COMMENT: 'comment',
    REVERT: 'revert',
    POST_REVERT: 'postrevert',
  };

  var Element = {
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

    Element: Element,
    EventType: EventType,

    handleEvent: function(type, detail) {
      Gerrit.awaitPluginsLoaded().then(function() {
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
      }.bind(this));
    },

    addElement: function(key, el) {
      this._elements[key] = el;
    },

    getElement: function(key) {
      return this._elements[key];
    },

    addEventCallback: function(eventName, callback) {
      if (!this._eventCallbacks[eventName]) {
        this._eventCallbacks[eventName] = [];
      }
      this._eventCallbacks[eventName].push(callback);
    },

    canSubmitChange: function(change, revision) {
      var submitCallbacks = this._getEventCallbacks(EventType.SUBMIT_CHANGE);
      var cancelSubmit = submitCallbacks.some(function(callback) {
        try {
          return callback(change, revision) === false;
        } catch (err) {
          console.error(err);
        }
        return false;
      });

      return !cancelSubmit;
    },

    _removeEventCallbacks: function() {
      for (var k in EventType) {
        this._eventCallbacks[EventType[k]] = [];
      }
    },

    _handleHistory: function(detail) {
      this._getEventCallbacks(EventType.HISTORY).forEach(function(cb) {
        try {
          cb(detail.path);
        } catch (err) {
          console.error(err);
        }
      });
    },

    _handleShowChange: function(detail) {
      this._getEventCallbacks(EventType.SHOW_CHANGE).forEach(function(cb) {
        var change = detail.change;
        var patchNum = detail.patchNum;
        var revision;
        for (var rev in change.revisions) {
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

    handleCommitMessage: function(change, msg) {
      this._getEventCallbacks(EventType.COMMIT_MSG_EDIT).forEach(
          function(cb) {
            try {
              cb(change, msg);
            } catch (err) {
              console.error(err);
            }
          }
      );
    },

    _handleComment: function(detail) {
      this._getEventCallbacks(EventType.COMMENT).forEach(function(cb) {
        try {
          cb(detail.node);
        } catch (err) {
          console.error(err);
        }
      });
    },

    _handleLabelChange: function(detail) {
      this._getEventCallbacks(EventType.LABEL_CHANGE).forEach(function(cb) {
        try {
          cb(detail.change);
        } catch (err) {
          console.error(err);
        }
      });
    },

    modifyRevertMsg: function(change, revertMsg, origMsg) {
      this._getEventCallbacks(EventType.REVERT).forEach(function(callback) {
        try {
          revertMsg = callback(change, revertMsg, origMsg);
        } catch (err) {
          console.error(err);
        }
      });
      return revertMsg;
    },

    getLabelValuesPostRevert: function(change) {
      var labels = {};
      this._getEventCallbacks(EventType.POST_REVERT).forEach(
          function(callback) {
            try {
              labels = callback(change);
            } catch (err) {
              console.error(err);
            }
          }
      );
      return labels;
    },

    _getEventCallbacks: function(type) {
      return this._eventCallbacks[type] || [];
    },
  });
})();
