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

  /**
   * @enum {number}
   */
  var LabelStatus = {
    /**
     * This label provides what is necessary for submission.
     */
    OK: 'OK',
    /**
     * This label prevents the change from being submitted.
     */
    REJECT: 'REJECT',
    /**
     * The label may be set, but it's neither necessary for submission
     * nor does it block submission if set.
     */
    MAY: 'MAY',
    /**
     * The label is required for submission, but has not been satisfied.
     */
    NEED: 'NEED',
    /**
     * The label is required for submission, but is impossible to complete.
     * The likely cause is access has not been granted correctly by the
     * project owner or site administrator.
     */
    IMPOSSIBLE: 'IMPOSSIBLE',
  };

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

  /**
   * Keys for actions to appear in the overflow menu rather than the top-level
   * set of action buttons.
   */
  var MENU_ACTION_KEYS = [
    'cherrypick',
    '/', // '/' is the key for the delete action.
  ];

  Polymer({
    is: 'gr-change-actions',

    /**
     * Fired when the change should be reloaded.
     *
     * @event reload-change
     */

    /**
     * Fired when an action is tapped.
     *
     * @event <action key>-tap
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
      _hasKnownChainState: {
        type: Boolean,
        value: false,
      },
      changeNum: String,
      changeStatus: String,
      commitNum: String,
      hasParent: {
        type: Boolean,
        observer: '_computeChainState',
      },
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
      _actionLoadingMessage: {
        type: String,
        value: null,
      },
      _allActionValues: {
        type: Array,
        computed: '_computeAllActions(actions.*, revisionActions.*,' +
            'primaryActionKeys.*, _additionalActions.*, change)',
      },
      _topLevelActions: {
        type: Array,
        computed: '_computeTopLevelActions(_allActionValues.*, ' +
            '_hiddenActions.*)',
      },
      _menuActions: {
        type: Array,
        computed: '_computeMenuActions(_allActionValues.*, _hiddenActions.*)',
      },
      _additionalActions: {
        type: Array,
        value: function() { return []; },
      },
      _hiddenActions: {
        type: Array,
        value: function() { return []; },
      },
      _disabledMenuActions: {
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
      this._loading = false;
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
      if (type !== ActionType.CHANGE && type !== ActionType.REVISION) {
        throw Error('Invalid action type given: ' + type);
      }

      var idx = this._hiddenActions.indexOf(key);
      if (hidden && idx === -1) {
        this.push('_hiddenActions', key);
      } else if (!hidden && idx !== -1) {
        this.splice('_hiddenActions', idx, 1);
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

    _shouldHideActions: function(actions, loading) {
      return loading || !actions || !actions.base || !actions.base.length;
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
      this._actionLoadingMessage = null;
      this._disabledMenuActions = [];
    },

    _getValuesFor: function(obj) {
      return Object.keys(obj).map(function(key) {
        return obj[key];
      });
    },

    _getLabelStatus: function(label) {
      if (label.approved) {
        return LabelStatus.OK;
      } else if (label.rejected) {
        return LabelStatus.REJECT;
      } else if (label.optional) {
        return LabelStatus.OPTIONAL;
      } else {
        return LabelStatus.NEED;
      }
    },

    /**
     * Get highest score for last missing permitted label for current change.
     * Returns null if no labels permitted or more than one label missing.
     *
     * @return {{label: string, score: string}}
     */
    _getTopMissingApproval: function() {
      if (!this.change ||
          !this.change.labels ||
          !this.change.permitted_labels) {
        return null;
      }
      var result;
      for (var label in this.change.labels) {
        if (!(label in this.change.permitted_labels)) {
          continue;
        }
        if (this.change.permitted_labels[label].length === 0) {
          continue;
        }
        var status = this._getLabelStatus(this.change.labels[label]);
        if (status === LabelStatus.NEED) {
          if (result) {
            // More than one label is missing, so it's unclear which to quick
            // approve, return null;
            return null;
          }
          result = label;
        } else if (status === LabelStatus.REJECT ||
                   status === LabelStatus.IMPOSSIBLE) {
          return null;
        }
      }
      if (result) {
        var score = this.change.permitted_labels[result].slice(-1)[0];
        var maxScore =
            Object.keys(this.change.labels[result].values).slice(-1)[0];
        if (score === maxScore) {
          // Allow quick approve only for maximal score.
          return {
            label: result,
            score: score,
          };
        }
      }
      return null;
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
      } else if (key === ChangeActions.REVERT) {
        this.showRevertDialog();
      } else if (key === ChangeActions.ABANDON) {
        this._showActionDialog(this.$.confirmAbandonDialog);
      } else if (key === QUICK_APPROVE_ACTION.key) {
        var action = this._allActionValues.find(function(o) {
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

    /**
     * This the is used to determine if the rebase button should be enabled or
     * not. chainState is used as parameter in _calculateDisabled instead of
     * hasParent because hasParent begins as undefined, and would cause the top
     * level action buttons to not render until it had a value. Instead, use
     * the chainState, which is set to have a value initially.
     */
    _computeChainState: function(hasParent) {
      this._hasKnownChainState = true;
    },

    _calculateDisabled: function(action, hasKnownChainState) {
      if (action.__key === 'rebase') {
        if (hasKnownChainState === false) {
          return true;
        }
      }
      return !action.enabled;
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
      var el = this.$.confirmRebase;
      var payload = {base: el.base};
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
      this._actionLoadingMessage = this._computeLoadingLabel(key);

      // If the action appears in the overflow menu.
      if (MENU_ACTION_KEYS.indexOf(key) !== -1) {
        this.push('_disabledMenuActions', key === '/' ? 'delete' : key);
        return function() {
          this._actionLoadingMessage = null;
          this._disabledMenuActions = [];
        }.bind(this);
      }

      // Otherwise it's a top-level action.
      var buttonEl = this.$$('[data-action-key="' + key + '"]');
      buttonEl.setAttribute('loading', true);
      buttonEl.disabled = true;
      return function() {
        this._actionLoadingMessage = null;
        buttonEl.removeAttribute('loading');
        buttonEl.disabled = false;
      }.bind(this);
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
      if (response) {
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
                this.dispatchEvent(new CustomEvent('reload-change',
                    {detail: {action: action.__key}, bubbles: false}));
                break;
            }
        }.bind(this));
      }
    },

    _handleResponseError: function(response) {
      return response.text().then(function(errText) {
        this.fire('show-alert',
            { message: 'Could not perform action: ' + errText });
        if (errText.indexOf('Change is already up to date') !== 0) {
          throw Error(errText);
        }
      }.bind(this));
    },

    _send: function(method, payload, actionEndpoint, revisionAction,
        cleanupFn, opt_errorFn) {
      var url = this.$.restAPI.getChangeActionURL(this.changeNum,
          revisionAction ? this.patchNum : null, actionEndpoint);
      return this.$.restAPI.send(method, url, payload,
          this._handleResponseError, this).then(function(response) {
            cleanupFn.call(this);
            return response;
      }.bind(this));
    },

    _handleAbandonTap: function() {
      this._showActionDialog(this.$.confirmAbandonDialog);
    },

    _handleCherrypickTap: function() {
      this.$.confirmCherrypick.branch = '';
      this._showActionDialog(this.$.confirmCherrypick);
    },

    _handleDeleteTap: function() {
      this._showActionDialog(this.$.confirmDeleteDialog);
    },

    /**
     * Merge sources of change actions into a single ordered array of action
     * values.
     * @param {splices} changeActionsRecord
     * @param {splices} revisionActionsRecord
     * @param {splices} primariesRecord
     * @param {splices} additionalActionsRecord
     * @param {Object} change The change object.
     * @return {Array}
     */
    _computeAllActions: function(changeActionsRecord, revisionActionsRecord,
        primariesRecord, additionalActionsRecord, change) {
      var revisionActionValues = this._getActionValues(revisionActionsRecord,
          primariesRecord, additionalActionsRecord, ActionType.REVISION);
      var changeActionValues = this._getActionValues(changeActionsRecord,
          primariesRecord, additionalActionsRecord, ActionType.CHANGE, change);
      var quickApprove = this._getQuickApproveAction();
      if (quickApprove) {
        changeActionValues.unshift(quickApprove);
      }
      return revisionActionValues
          .concat(changeActionValues)
          .sort(this._actionComparator);
    },

    /**
     * Sort comparator to define the order of change actions.
     */
    _actionComparator: function(actionA, actionB) {
      // The code review action always appears first.
      if (actionA.__key === 'review') {
        return -1;
      } else if (actionB.__key === 'review') {
        return 1;
      }

      // Primary actions always appear last.
      if (actionA.__primary) {
        return 1;
      } else if (actionB.__primary) {
        return -1;
      }

      // Change actions appear before revision actions.
     if (actionA.__type === 'change' && actionB.__type === 'revision') {
        return 1;
      } else if (actionA.__type === 'revision' && actionB.__type === 'change') {
        return -1;
      }

      // Otherwise, sort by the button label.
      return actionA.label > actionB.label ? 1 : -1;
    },

    _computeTopLevelActions: function(actionRecord, hiddenActionsRecord) {
      var hiddenActions = hiddenActionsRecord.base || [];
      return actionRecord.base.filter(function(a) {
        return MENU_ACTION_KEYS.indexOf(a.__key) === -1 &&
                hiddenActions.indexOf(a.__key) === -1;
      });
    },

    _computeMenuActions: function(actionRecord, hiddenActionsRecord) {
      var hiddenActions = hiddenActionsRecord.base || [];
      return actionRecord.base
          .filter(function(a) {
            return MENU_ACTION_KEYS.indexOf(a.__key) !== -1 &&
                hiddenActions.indexOf(a.__key) === -1;
          })
          .map(function(action) {
            var key = action.__key;
            if (key === '/') { key = 'delete'; }
            return {name: action.label, id: key, };
          });
    },
  });
})();
