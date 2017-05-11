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
    IGNORE: 'ignore',
    MUTE: 'mute',
    RESTORE: 'restore',
    REVERT: 'revert',
    UNIGNORE: 'unignore',
    UNMUTE: 'unmute',
    WIP: 'wip',
  };

  // TODO(andybons): Add the rest of the revision actions.
  var RevisionActions = {
    CHERRYPICK: 'cherrypick',
    DELETE: '/',
    PUBLISH: 'publish',
    REBASE: 'rebase',
    SUBMIT: 'submit',
    DOWNLOAD: 'download',
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

  var ActionPriority = {
    CHANGE: 2,
    DEFAULT: 0,
    PRIMARY: 3,
    REVIEW: -3,
    REVISION: 1,
  };

  var DOWNLOAD_ACTION = {
    enabled: true,
    label: 'Download patch',
    title: 'Open download dialog',
    __key: 'download',
    __primary: false,
    __type: 'revision',
  };

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

    /**
     * Fires to show an alert when a send is attempted on the non-latest patch.
     *
     * @event show-alert
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
            'primaryActionKeys.*, _additionalActions.*, change, ' +
            '_actionPriorityOverrides.*)',
      },
      _topLevelActions: {
        type: Array,
        computed: '_computeTopLevelActions(_allActionValues.*, ' +
            '_hiddenActions.*, _overflowActions.*)',
      },
      _menuActions: {
        type: Array,
        computed: '_computeMenuActions(_allActionValues.*, _hiddenActions.*, ' +
            '_overflowActions.*)',
      },
      _overflowActions: {
        type: Array,
        value: function() {
          var value = [
            {
              type: ActionType.CHANGE,
              key: ChangeActions.DELETE,
            },
            {
              type: ActionType.REVISION,
              key: RevisionActions.DELETE,
            },
            {
              type: ActionType.REVISION,
              key: RevisionActions.CHERRYPICK,
            },
            {
              type: ActionType.REVISION,
              key: RevisionActions.DOWNLOAD,
            },
            {
              type: ActionType.CHANGE,
              key: ChangeActions.IGNORE,
            },
            {
              type: ActionType.CHANGE,
              key: ChangeActions.UNIGNORE,
            },
            {
              type: ActionType.CHANGE,
              key: ChangeActions.MUTE,
            },
            {
              type: ActionType.CHANGE,
              key: ChangeActions.UNMUTE,
            },
          ];
          return value;
        },
      },
      _actionPriorityOverrides: {
        type: Array,
        value: function() { return []; },
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
      Gerrit.PatchSetBehavior,
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
        __key: ADDITIONAL_ACTION_KEY_PREFIX +
            Math.random().toString(36).substr(2),
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

    setActionOverflow: function(type, key, overflow) {
      if (type !== ActionType.CHANGE && type !== ActionType.REVISION) {
        throw Error('Invalid action type given: ' + type);
      }
      var index = this._getActionOverflowIndex(type, key);
      var action = {
        type: type,
        key: key,
        overflow: overflow,
      };
      if (!overflow && index !== -1) {
        this.splice('_overflowActions', index, 1);
      } else if (overflow) {
        this.push('_overflowActions', action);
      }
    },

    setActionPriority: function(type, key, priority) {
      if (type !== ActionType.CHANGE && type !== ActionType.REVISION) {
        throw Error('Invalid action type given: ' + type);
      }
      var index = this._actionPriorityOverrides.findIndex(function(action) {
        return action.type === type && action.key === key;
      });
      var action = {
        type: type,
        key: key,
        priority: priority,
      };
      if (index !== -1) {
        this.set('_actionPriorityOverrides', index, action);
      } else {
        this.push('_actionPriorityOverrides', action);
      }
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

      var revisionActions = revisionActionsChangeRecord.base || {};
      if (Object.keys(revisionActions).length !== 0 &&
          !revisionActions.download) {
        this.set('revisionActions.download', DOWNLOAD_ACTION);
      }
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
      this._handleAction(type, key);
    },

    _handleOveflowItemTap: function(e) {
      this._handleAction(e.detail.action.__type, e.detail.action.__key);
    },

    _handleAction: function(type, key) {
      switch (type) {
        case ActionType.REVISION:
          this._handleRevisionAction(key);
          break;
        case ActionType.CHANGE:
          this._handleChangeAction(key);
          break;
        default:
          this._fireAction(this._prependSlash(key), this.actions[key], false);
      }
    },

    _handleChangeAction: function(key) {
      switch (key) {
        case ChangeActions.REVERT:
          this.showRevertDialog();
          break;
        case ChangeActions.ABANDON:
          this._showActionDialog(this.$.confirmAbandonDialog);
          break;
        case QUICK_APPROVE_ACTION.key:
          var action = this._allActionValues.find(function(o) {
            return o.key === key;
          });
          this._fireAction(
              this._prependSlash(key), action, true, action.payload);
          break;
        case ChangeActions.DELETE:
          this._handleDeleteTap();
          break;
        case ChangeActions.WIP:
          this._handleWipTap();
          break;
        default:
          this._fireAction(this._prependSlash(key), this.actions[key], false);
      }
    },

    _handleRevisionAction: function(key) {
      switch (key) {
        case RevisionActions.REBASE:
          this._showActionDialog(this.$.confirmRebase);
          break;
        case RevisionActions.DELETE:
          this._handleDeleteConfirm();
          break;
        case RevisionActions.CHERRYPICK:
          this._handleCherrypickTap();
          break;
        case RevisionActions.DOWNLOAD:
          this._handleDownloadTap();
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
     * Returns true if hasParent is defined (can be either true or false).
     * returns false otherwise.
     * @return {boolean} hasParent
     */
    _computeChainState: function(hasParent) {
      this._hasKnownChainState = true;
    },

    _calculateDisabled: function(action, hasKnownChainState) {
      if (action.__key === 'rebase' && hasKnownChainState === false) {
        return true;
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

    _getActionOverflowIndex: function(type, key) {
      return this._overflowActions.findIndex(function(action) {
        return action.type === type && action.key === key;
      });
    },

    _setLoadingOnButtonWithKey: function(type, key) {
      this._actionLoadingMessage = this._computeLoadingLabel(key);

      // If the action appears in the overflow menu.
      if (this._getActionOverflowIndex(type, key) !== -1) {
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
      var cleanupFn =
          this._setLoadingOnButtonWithKey(action.__type, action.__key);
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
      if (!response) { return; }
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
          case ChangeActions.WIP:
            page.show(this.changePath(this.changeNum));
            break;
          default:
            this.dispatchEvent(new CustomEvent('reload-change',
                {detail: {action: action.__key}, bubbles: false}));
            break;
        }
      }.bind(this));
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

    _send: function(method, payload, actionEndpoint, revisionAction, cleanupFn,
        opt_errorFn) {
      return this.fetchIsLatestKnown(this.change, this.$.restAPI)
          .then(function(isLatest) {
            if (!isLatest) {
              this.fire('show-alert', {
                  message: 'Cannot set label: a newer patch has been ' +
                      'uploaded to this change.',
                  action: 'Reload',
                  callback: function() {
                    // Load the current change without any patch range.
                    location.href = this.getBaseUrl() + '/c/' +
                        this.change._number;
                  }.bind(this),
              });
              cleanupFn();
              return Promise.resolve();
            }

            var url = this.$.restAPI.getChangeActionURL(this.changeNum,
                revisionAction ? this.patchNum : null, actionEndpoint);
            return this.$.restAPI.send(method, url, payload,
                this._handleResponseError, this).then(function(response) {
                  cleanupFn.call(this);
                  return response;
            }.bind(this));
        }.bind(this));
    },

    _handleAbandonTap: function() {
      this._showActionDialog(this.$.confirmAbandonDialog);
    },

    _handleCherrypickTap: function() {
      this.$.confirmCherrypick.branch = '';
      this._showActionDialog(this.$.confirmCherrypick);
    },

    _handleDownloadTap: function() {
      this.fire('download-tap', null, {bubbles: false});
    },

    _handleDeleteTap: function() {
      this._showActionDialog(this.$.confirmDeleteDialog);
    },

    _handleWipTap: function() {
      this._fireAction('/wip', this.actions.wip, false);
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
          .sort(this._actionComparator.bind(this));
    },

    _getActionPriority: function(action) {
      if (action.__type && action.__key) {
        var overrideAction = this._actionPriorityOverrides.find(function(i) {
          return i.type === action.__type && i.key === action.__key;
        });

        if (overrideAction !== undefined) {
          return overrideAction.priority;
        }
      }
      if (action.__key === 'review') {
        return ActionPriority.REVIEW;
      } else if (action.__primary) {
        return ActionPriority.PRIMARY;
      } else if (action.__type === ActionType.CHANGE) {
        return ActionPriority.CHANGE;
      } else if (action.__type === ActionType.REVISION) {
        return ActionPriority.REVISION;
      }
      return ActionPriority.DEFAULT;
    },

    /**
     * Sort comparator to define the order of change actions.
     */
    _actionComparator: function(actionA, actionB) {
      var priorityDelta = this._getActionPriority(actionA) -
          this._getActionPriority(actionB);
      // Sort by the button label if same priority.
      if (priorityDelta === 0) {
        return actionA.label > actionB.label ? 1 : -1;
      } else {
        return priorityDelta;
      }
    },

    _computeTopLevelActions: function(actionRecord, hiddenActionsRecord) {
      var hiddenActions = hiddenActionsRecord.base || [];
      return actionRecord.base.filter(function(a) {
        var overflow = this._getActionOverflowIndex(a.__type, a.__key) !== -1;
        return !overflow && hiddenActions.indexOf(a.__key) === -1;
      }.bind(this));
    },

    _computeMenuActions: function(actionRecord, hiddenActionsRecord) {
      var hiddenActions = hiddenActionsRecord.base || [];
      return actionRecord.base.filter(function(a) {
        var overflow = this._getActionOverflowIndex(a.__type, a.__key) !== -1;
        return overflow && hiddenActions.indexOf(a.__key) === -1;
      }.bind(this)).map(function(action) {
        var key = action.__key;
        if (key === '/') { key = 'delete'; }
        return {
          name: action.label,
          id: key + '-' + action.__type,
          action: action,
        };
      });
    },
  });
})();
