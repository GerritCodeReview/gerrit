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

  var QUICK_APPROVE_ACTION = {
    __key: 'review',
    __type: 'change',
    enabled: true,
    key: 'review',
    label: 'Quick Approve',
    method: 'POST',
  };

  Polymer({
    is: 'gr-change-actions',

    /**
     * Fired when the change should be reloaded.
     *
     * @event reload-change
     */

    properties: {
      change: Object,
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
      changeStatus: String,
      commitNum: String,
      patchNum: String,
      commitMessage: {
        type: String,
        value: '',
      },
      revisionActions: {
        type: Object,
        value: function() { return {}; },
      },

      _loading: {
        type: Boolean,
        value: true,
      },
      _revisionActionValues: {
        type: Array,
        computed: '_computeRevisionActionValues(revisionActions.*, ' +
            'primaryActionKeys.*, _additionalActions.*)',
      },
      _changeActionValues: {
        type: Array,
        computed: '_computeChangeActionValues(actions.*, ' +
            'primaryActionKeys.*, _additionalActions.*, change)',
      },
      _additionalActions: {
        type: Array,
        value: function() { return []; },
      },
      _hiddenChangeActions: {
        type: Array,
        value: function() { return []; },
      },
      _hiddenRevisionActions: {
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
      '_actionsChanged(actions.*, revisionActions.*, _additionalActions.*)',
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

        this.revisionActions = revisionActions;
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

    setActionHidden: function(type, key, hidden) {
      var path;
      if (type === ActionType.CHANGE) {
        path = '_hiddenChangeActions';
      } else if (type === ActionType.REVISION) {
        path = '_hiddenRevisionActions';
      } else {
        throw Error('Invalid action type given: ' + type);
      }

      var idx = this.get(path).indexOf(key);
      if (hidden && idx === -1) {
        this.push(path, key);
      } else if (!hidden && idx !== -1) {
        this.splice(path, idx, 1);
      }
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
          additionalActionsChangeRecord, ActionType.REVISION);
    },

    _computeChangeActionValues: function(actionsChangeRecord,
        primariesChangeRecord, additionalActionsChangeRecord, change) {
      var actions = this._getActionValues(
        actionsChangeRecord, primariesChangeRecord,
        additionalActionsChangeRecord, ActionType.CHANGE, change);
      var quickApprove = this._getQuickApproveAction();
      if (quickApprove) {
        actions.unshift(quickApprove);
      }
      return actions;
    },

    _getMaxScoreTextForLabel: function(label) {
      if (!this.change ||
          !this.change.permitted_labels ||
          !this.change.permitted_labels[label] ||
          !this.change.permitted_labels[label].length) {
        return null;
      }
      return this.change.permitted_labels[label].slice(-1)[0];
    },

    _getMaxScoreForLabel: function(label) {
      return parseInt(this._getMaxScoreTextForLabel(label), 10);
    },

    /**
     * Get highest score for missing permitted label for current change.
     *
     * @return {{label: string, score: string}}
     */
    _getTopMissingApproval: function() {
      var change = this.change;
      if (!change || !change.labels || !change.permitted_labels) {
        return null;
      }

      // Use only labels that satisfy all of following:
      // - label scoring is permitted.
      // - label is not approved yet.
      // - label score is less than max permitted.
      var missingApprovals = Object.keys(change.labels)
          .filter(function(label) {
            return label in change.permitted_labels &&
                !change.labels[label].approved &&
                (change.labels[label].value == null ||
                  change.labels[label].value <
                    this._getMaxScoreForLabel(label));
          }.bind(this))
          .sort(function(a, b) {
            // Sort descending by max permitted score.
            return this._getMaxScoreForLabel(b) - this._getMaxScoreForLabel(a);
          }.bind(this));
      if (!missingApprovals.length) {
        return null;
      }
      var score = this._getMaxScoreForLabel(missingApprovals[0]);
      // Guard against votes that fail to parse as integers. (Shouldn't happen.)
      if (isNaN(score) || score <= 0) {
        return null;
      }
      return {
        label: missingApprovals[0],
        score: this._getMaxScoreTextForLabel(missingApprovals[0]),
      };
    },

    _getQuickApproveAction: function() {
      var approval = this._getTopMissingApproval();
      if (!approval) {
        return null;
      }
      var action = Object.assign({}, QUICK_APPROVE_ACTION);
      action.label = approval.label + approval.score;
      var review = {
        drafts: 'PUBLISH_ALL_REVISIONS',
        labels: {},
      };
      review.labels[approval.label] = approval.score;
      action.payload = review;
      return action;
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
        if (actions[a].label === 'Delete') {
          // This label is common within change and revision actions. Make it
          // more explicit to the user.
          if (type === ActionType.CHANGE) {
            actions[a].label += ' Change';
          } else if (type === ActionType.REVISION) {
            actions[a].label += ' Revision';
          }
        }
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
      return this.$.jsAPI.canSubmitChange(this.change,
          this._getRevision(this.change, this.patchNum));
    },

    _computeActionHidden: function(key, hiddenActionsChangeRecord) {
      var hiddenActions =
          (hiddenActionsChangeRecord && hiddenActionsChangeRecord.base) || [];
      return hiddenActions.indexOf(key) !== -1;
    },

    _getRevision: function(change, patchNum) {
      var num = window.parseInt(patchNum, 10);
      for (var hash in change.revisions) {
        var rev = change.revisions[hash];
        if (rev._number === num) {
          return rev;
        }
      }
      return null;
    },

    _modifyRevertMsg: function() {
      return this.$.jsAPI.modifyRevertMsg(this.change,
          this.$.confirmRevertDialog.message, this.commitMessage);
    },

    showRevertDialog: function() {
      this.$.confirmRevertDialog.populateRevertMessage(
          this.commitMessage, this.change.current_revision);
      this.$.confirmRevertDialog.message = this._modifyRevertMsg();
      this._showActionDialog(this.$.confirmRevertDialog);
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
      } else if (key === ChangeActions.DELETE) {
        this._showActionDialog(this.$.confirmDeleteDialog);
      } else if (key === ChangeActions.REVERT) {
        this.showRevertDialog();
      } else if (key === ChangeActions.ABANDON) {
        this._showActionDialog(this.$.confirmAbandonDialog);
      } else if (key === QUICK_APPROVE_ACTION.key) {
        var action = this._changeActionValues.find(function(o) {
          return o.key === key;
        });
        this._fireAction(
          this._prependSlash(key), action, true, action.payload);
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
          this.$.confirmCherrypick.branch = '';
          this._showActionDialog(this.$.confirmCherrypick);
          break;
        case RevisionActions.SUBMIT:
          if (!this._canSubmitChange()) {
            return;
          }
        /* falls through */ // required by JSHint
        default:
          this._fireAction(this._prependSlash(key),
              this.revisionActions[key], true);
      }
    },

    _prependSlash: function(key) {
      return key === '/' ? key : '/' + key;
    },

    _handleConfirmDialogCancel: function() {
      this._hideAllDialogs();
    },

    _hideAllDialogs: function() {
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
      el.hidden = true;
      this._fireAction('/rebase', this.revisionActions.rebase, true, payload);
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
      el.hidden = true;
      this._fireAction(
          '/cherrypick',
          this.revisionActions.cherrypick,
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
      el.hidden = true;
      this._fireAction('/revert', this.actions.revert, false,
          {message: el.message});
    },

    _handleAbandonDialogConfirm: function() {
      var el = this.$.confirmAbandonDialog;
      this.$.overlay.close();
      el.hidden = true;
      this._fireAction('/abandon', this.actions.abandon, false,
          {message: el.message});
    },

    _handleDeleteConfirm: function() {
      this._fireAction('/', this.actions[ChangeActions.DELETE], false);
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
      this._hideAllDialogs();

      dialog.hidden = false;
      this.$.overlay.open().then(function() {
        if (dialog.resetFocus) {
          dialog.resetFocus();
        }
      });
    },

    // TODO(rmistry): Redo this after
    // https://bugs.chromium.org/p/gerrit/issues/detail?id=4671 is resolved.
    _setLabelValuesOnRevert: function(newChangeId) {
      var labels = this.$.jsAPI.getLabelValuesPostRevert(this.change);
      if (labels) {
        var url = '/changes/' + newChangeId + '/revisions/current/review';
        this.$.restAPI.send(this.actions.revert.method, url, {labels: labels});
      }
    },

    _handleResponse: function(action, response) {
      return this.$.restAPI.getResponseObject(response).then(function(obj) {
        switch (action.__key) {
          case ChangeActions.REVERT:
            this._setLabelValuesOnRevert(obj.change_id);
            /* falls through */
          case RevisionActions.CHERRYPICK:
            page.show(this.changePath(obj._number));
            break;
          case ChangeActions.DELETE:
          case RevisionActions.DELETE:
            if (action.__type === ActionType.CHANGE) {
              page.show('/');
            } else {
              page.show(this.changePath(this.changeNum));
            }
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
