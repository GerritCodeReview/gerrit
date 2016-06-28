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

  // TODO(davido): Add the rest of the change actions.
  var ChangeActions = {
    ABANDON: 'abandon',
    DELETE: '/',
    RESTORE: 'restore',
    REVERT: 'revert',
  };

  // TODO(andybons): Add the rest of the revision actions.
  var RevisionActions = {
    CHERRYPICK: 'cherrypick',
    DELETE: '/',
    PUBLISH: 'publish',
    REBASE: 'rebase',
    SUBMIT: 'submit',
  };

  var ActionLoadingLabels = {
    'abandon': 'Abandoning...',
    'cherrypick': 'Cherry-Picking...',
    'delete': 'Deleting...',
    'publish': 'Publishing...',
    'rebase': 'Rebasing...',
    'restore': 'Restoring...',
    'revert': 'Reverting...',
    'submit': 'Submitting...',
  };

  var ActionType = {
    CHANGE: 'change',
    REVISION: 'revision',
  };

  var ADDITIONAL_ACTION_KEY_PREFIX = '__additionalAction_';

  Polymer({
    is: 'gr-change-actions',

    /**
     * Fired when the change should be reloaded.
     *
     * @event reload-change
     */

    properties: {
      actions: {
        type: Object,
        value: function() { return {}; },
      },
      primaryActionKeys: {
        type: Array,
        value: function() {
          return [
            RevisionActions.PUBLISH,
            RevisionActions.SUBMIT,
          ];
        },
      },
      changeNum: String,
      patchNum: String,
      commitInfo: Object,

      _loading: {
        type: Boolean,
        value: true,
      },
      _revisionActions: {
        type: Object,
        value: function() { return {}; },
      },
      _revisionActionValues: {
        type: Array,
        computed: '_computeRevisionActionValues(_revisionActions.*, ' +
            'primaryActionKeys.*, _additionalActions.*)',
      },
      _changeActionValues: {
        type: Array,
        computed: '_computeChangeActionValues(actions.*, ' +
            'primaryActionKeys.*, _additionalActions.*)',
      },
      _additionalActions: {
        type: Array,
        value: function() { return []; },
      },
    },

    ActionType: ActionType,
    ChangeActions: ChangeActions,
    RevisionActions: RevisionActions,

    behaviors: [
      Gerrit.RESTClientBehavior,
    ],

    observers: [
      '_actionsChanged(actions.*, _revisionActions.*, _additionalActions.*)',
    ],

    ready: function() {
      this.$.jsAPI.addElement(this.$.jsAPI.Element.CHANGE_ACTIONS, this);
    },

    reload: function() {
      if (!this.changeNum || !this.patchNum) {
        return Promise.resolve();
      }

      this._loading = true;
      return this._getRevisionActions().then(function(revisionActions) {
        if (!revisionActions) { return; }

        this._revisionActions = revisionActions;
        this._loading = false;
      }.bind(this)).catch(function(err) {
        alert('Couldn’t load revision actions. Check the console ' +
            'and contact the PolyGerrit team for assistance.');
        this._loading = false;
        throw err;
      }.bind(this));
    },

    addActionButton: function(type, label) {
      if (type !== ActionType.CHANGE && type !== ActionType.REVISION) {
        throw Error('Invalid action type: ' + type);
      }
      var action = {
        enabled: true,
        label: label,
        __type: type,
        __key: ADDITIONAL_ACTION_KEY_PREFIX + Math.random().toString(36),
      };
      this.push('_additionalActions', action);
      return action.__key;
    },

    removeActionButton: function(key) {
      var idx = this._indexOfActionButtonWithKey(key);
      if (idx === -1) {
        return;
      }
      this.splice('_additionalActions', idx, 1);
    },

    setActionButtonProp: function(key, prop, value) {
      this.set([
        '_additionalActions',
        this._indexOfActionButtonWithKey(key),
        prop,
      ], value);
    },

    _indexOfActionButtonWithKey: function(key) {
      for (var i = 0; i < this._additionalActions.length; i++) {
        if (this._additionalActions[i].__key === key) {
          return i;
        }
      }
      return -1;
    },

    _getRevisionActions: function() {
      return this.$.restAPI.getChangeRevisionActions(this.changeNum,
          this.patchNum);
    },

    _actionCount: function(actionsChangeRecord, additionalActionsChangeRecord) {
      var additionalActions = (additionalActionsChangeRecord &&
          additionalActionsChangeRecord.base) || [];
      return this._keyCount(actionsChangeRecord) + additionalActions.length;
    },

    _keyCount: function(changeRecord) {
      return Object.keys((changeRecord && changeRecord.base) || {}).length;
    },

    _actionsChanged: function(actionsChangeRecord, revisionActionsChangeRecord,
        additionalActionsChangeRecord) {
      var additionalActions = (additionalActionsChangeRecord &&
          additionalActionsChangeRecord.base) || [];
      this.hidden = this._keyCount(actionsChangeRecord) === 0 &&
          this._keyCount(revisionActionsChangeRecord) === 0 &&
              additionalActions.length === 0;
    },

    _getValuesFor: function(obj) {
      return Object.keys(obj).map(function(key) {
        return obj[key];
      });
    },

    _computeRevisionActionValues: function(actionsChangeRecord,
        primariesChangeRecord, additionalActionsChangeRecord) {
      return this._getActionValues(actionsChangeRecord, primariesChangeRecord,
          additionalActionsChangeRecord, 'revision');
    },

    _computeChangeActionValues: function(actionsChangeRecord,
        primariesChangeRecord, additionalActionsChangeRecord) {
      return this._getActionValues(actionsChangeRecord, primariesChangeRecord,
          additionalActionsChangeRecord, 'change');
    },

    _getActionValues: function(actionsChangeRecord, primariesChangeRecord,
        additionalActionsChangeRecord, type) {
      if (!actionsChangeRecord || !primariesChangeRecord) { return []; }

      var actions = actionsChangeRecord.base || {};
      var primaryActionKeys = primariesChangeRecord.base || [];
      var result = [];
      var values = this._getValuesFor(
          type === ActionType.CHANGE ? ChangeActions : RevisionActions);
      for (var a in actions) {
        if (values.indexOf(a) === -1) { continue; }
        actions[a].__key = a;
        actions[a].__type = type;
        actions[a].__primary = primaryActionKeys.indexOf(a) !== -1;
        // Triggers a re-render by ensuring object inequality.
        // TODO(andybons): Polyfill for Object.assign.
        result.push(Object.assign({}, actions[a]));
      }

      var additionalActions = (additionalActionsChangeRecord &&
      additionalActionsChangeRecord.base) || [];
      additionalActions = additionalActions.filter(function(a) {
        return a.__type === type;
      }).map(function(a) {
        a.__primary = primaryActionKeys.indexOf(a.__key) !== -1;
        // Triggers a re-render by ensuring object inequality.
        // TODO(andybons): Polyfill for Object.assign.
        return Object.assign({}, a);
      });
      return result.concat(additionalActions);
    },

    _computeLoadingLabel: function(action) {
      return ActionLoadingLabels[action] || 'Working...';
    },

    _canSubmitChange: function() {
      return this.$.jsAPI.canSubmitChange();
    },

    _handleActionTap: function(e) {
      e.preventDefault();
      var el = Polymer.dom(e).rootTarget;
      var key = el.getAttribute('data-action-key');
      if (key.indexOf(ADDITIONAL_ACTION_KEY_PREFIX) === 0) {
        this.fire(key + '-tap', {node: el});
        return;
      }
      var type = el.getAttribute('data-action-type');
      if (type === ActionType.REVISION) {
        this._handleRevisionAction(key);
      } else if (key === ChangeActions.REVERT) {
        this._showActionDialog(this.$.confirmRevertDialog);
      } else if (key === ChangeActions.ABANDON) {
        this._showActionDialog(this.$.confirmAbandonDialog);
      } else {
        this._fireAction(this._prependSlash(key), this.actions[key], false);
      }
    },

    _handleRevisionAction: function(key) {
      switch (key) {
        case RevisionActions.REBASE:
          this._showActionDialog(this.$.confirmRebase);
          break;
        case RevisionActions.CHERRYPICK:
          this._showActionDialog(this.$.confirmCherrypick);
          break;
        case RevisionActions.SUBMIT:
          if (!this._canSubmitChange()) {
            return;
          }
          /* falls through */ // required by JSHint
        default:
          this._fireAction(this._prependSlash(key),
              this._revisionActions[key], true);
      }
    },

    _prependSlash: function(key) {
      return key === '/' ? key : '/' + key;
    },

    _handleConfirmDialogCancel: function() {
      var dialogEls =
          Polymer.dom(this.root).querySelectorAll('.confirmDialog');
      for (var i = 0; i < dialogEls.length; i++) {
        dialogEls[i].hidden = true;
      }
      this.$.overlay.close();
    },

    _handleRebaseConfirm: function() {
      var payload = {};
      var el = this.$.confirmRebase;
      if (el.clearParent) {
        // There is a subtle but important difference between setting the base
        // to an empty string and omitting it entirely from the payload. An
        // empty string implies that the parent should be cleared and the
        // change should be rebased on top of the target branch. Leaving out
        // the base implies that it should be rebased on top of its current
        // parent.
        payload.base = '';
      } else if (el.base && el.base.length > 0) {
        payload.base = el.base;
      }
      this.$.overlay.close();
      el.hidden = false;
      this._fireAction('/rebase', this._revisionActions.rebase, true, payload);
    },

    _handleCherrypickConfirm: function() {
      var el = this.$.confirmCherrypick;
      if (!el.branch) {
        // TODO(davido): Fix error handling
        alert('The destination branch can’t be empty.');
        return;
      }
      if (!el.message) {
        alert('The commit message can’t be empty.');
        return;
      }
      this.$.overlay.close();
      el.hidden = false;
      this._fireAction(
          '/cherrypick',
          this._revisionActions.cherrypick,
          true,
          {
            destination: el.branch,
            message: el.message,
          }
      );
    },

    _handleRevertDialogConfirm: function() {
      var el = this.$.confirmRevertDialog;
      this.$.overlay.close();
      el.hidden = false;
      this._fireAction('/revert', this.actions.revert, false,
          {message: el.message});
    },

    _handleAbandonDialogConfirm: function() {
      var el = this.$.confirmAbandonDialog;
      this.$.overlay.close();
      el.hidden = false;
      this._fireAction('/abandon', this.actions.abandon, false,
          {message: el.message});
    },

    _setLoadingOnButtonWithKey: function(key) {
      var buttonEl = this.$$('[data-action-key="' + key + '"]');
      buttonEl.setAttribute('loading', true);
      buttonEl.disabled = true;
      return function() {
        buttonEl.removeAttribute('loading');
        buttonEl.disabled = false;
      };
    },

    _fireAction: function(endpoint, action, revAction, opt_payload) {
      var cleanupFn = this._setLoadingOnButtonWithKey(action.__key);

      this._send(action.method, opt_payload, endpoint, revAction, cleanupFn)
          .then(this._handleResponse.bind(this, action));
    },

    _showActionDialog: function(dialog) {
      dialog.hidden = false;
      this.$.overlay.open();
    },

    _handleResponse: function(action, response) {
      return this.$.restAPI.getResponseObject(response).then(function(obj) {
        switch (action.__key) {
          case ChangeActions.REVERT:
          case RevisionActions.CHERRYPICK:
            page.show(this.changePath(obj._number));
            break;
          case RevisionActions.DELETE:
            page.show(this.changePath(this.changeNum));
            break;
          case ChangeActions.DELETE:
            page.show('/');
            break;
          default:
            this.fire('reload-change', null, {bubbles: false});
            break;
        }
      }.bind(this));
    },

    _handleResponseError: function(response) {
      if (response.ok) { return response; }

      return response.text().then(function(errText) {
        alert('Could not perform action: ' + errText);
        throw Error(errText);
      });
    },

    _send: function(method, payload, actionEndpoint, revisionAction,
        cleanupFn) {
      var url = this.$.restAPI.getChangeActionURL(this.changeNum,
          revisionAction ? this.patchNum : null, actionEndpoint);
      return this.$.restAPI.send(method, url, payload).then(function(response) {
        cleanupFn.call(this);
        return response;
      }.bind(this)).then(this._handleResponseError.bind(this));
    },
  });
})();
