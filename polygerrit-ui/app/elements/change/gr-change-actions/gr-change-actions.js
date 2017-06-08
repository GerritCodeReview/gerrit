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
  const LabelStatus = {
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
  const ChangeActions = {
    ABANDON: 'abandon',
    DELETE: '/',
    IGNORE: 'ignore',
    MUTE: 'mute',
    PRIVATE: 'private',
    PRIVATE_DELETE: 'private.delete',
    RESTORE: 'restore',
    REVERT: 'revert',
    UNIGNORE: 'unignore',
    UNMUTE: 'unmute',
    WIP: 'wip',
  };

  // TODO(andybons): Add the rest of the revision actions.
  const RevisionActions = {
    CHERRYPICK: 'cherrypick',
    DELETE: '/',
    PUBLISH: 'publish',
    REBASE: 'rebase',
    SUBMIT: 'submit',
    DOWNLOAD: 'download',
  };

  const ActionLoadingLabels = {
    abandon: 'Abandoning...',
    cherrypick: 'Cherry-Picking...',
    delete: 'Deleting...',
    publish: 'Publishing...',
    rebase: 'Rebasing...',
    restore: 'Restoring...',
    revert: 'Reverting...',
    submit: 'Submitting...',
  };

  const ActionType = {
    CHANGE: 'change',
    REVISION: 'revision',
  };

  const ADDITIONAL_ACTION_KEY_PREFIX = '__additionalAction_';

  const QUICK_APPROVE_ACTION = {
    __key: 'review',
    __type: 'change',
    enabled: true,
    key: 'review',
    label: 'Quick Approve',
    method: 'POST',
  };

  const ActionPriority = {
    CHANGE: 2,
    DEFAULT: 0,
    PRIMARY: 3,
    REVIEW: -3,
    REVISION: 1,
  };

  const DOWNLOAD_ACTION = {
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
        value() { return {}; },
      },
      primaryActionKeys: {
        type: Array,
        value() {
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
        value() { return {}; },
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
        value() {
          const value = [
            {
              type: ActionType.CHANGE,
              key: ChangeActions.WIP,
            },
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
            {
              type: ActionType.CHANGE,
              key: ChangeActions.PRIVATE,
            },
            {
              type: ActionType.CHANGE,
              key: ChangeActions.PRIVATE_DELETE,
            },
          ];
          return value;
        },
      },
      _actionPriorityOverrides: {
        type: Array,
        value() { return []; },
      },
      _additionalActions: {
        type: Array,
        value() { return []; },
      },
      _hiddenActions: {
        type: Array,
        value() { return []; },
      },
      _disabledMenuActions: {
        type: Array,
        value() { return []; },
      },
    },

    ActionType,
    ChangeActions,
    RevisionActions,

    behaviors: [
      Gerrit.PatchSetBehavior,
      Gerrit.RESTClientBehavior,
    ],

    observers: [
      '_actionsChanged(actions.*, revisionActions.*, _additionalActions.*)',
    ],

    ready() {
      this.$.jsAPI.addElement(this.$.jsAPI.Element.CHANGE_ACTIONS, this);
      this._loading = false;
    },

    reload() {
      if (!this.changeNum || !this.patchNum) {
        return Promise.resolve();
      }

      this._loading = true;
      return this._getRevisionActions().then(revisionActions => {
        if (!revisionActions) { return; }

        this.revisionActions = revisionActions;
        this._loading = false;
      }).catch(err => {
        alert('Couldn’t load revision actions. Check the console ' +
            'and contact the PolyGerrit team for assistance.');
        this._loading = false;
        throw err;
      });
    },

    addActionButton(type, label) {
      if (type !== ActionType.CHANGE && type !== ActionType.REVISION) {
        throw Error(`Invalid action type: ${type}`);
      }
      const action = {
        enabled: true,
        label,
        __type: type,
        __key: ADDITIONAL_ACTION_KEY_PREFIX +
            Math.random().toString(36).substr(2),
      };
      this.push('_additionalActions', action);
      return action.__key;
    },

    removeActionButton(key) {
      const idx = this._indexOfActionButtonWithKey(key);
      if (idx === -1) {
        return;
      }
      this.splice('_additionalActions', idx, 1);
    },

    setActionButtonProp(key, prop, value) {
      this.set([
        '_additionalActions',
        this._indexOfActionButtonWithKey(key),
        prop,
      ], value);
    },

    setActionOverflow(type, key, overflow) {
      if (type !== ActionType.CHANGE && type !== ActionType.REVISION) {
        throw Error(`Invalid action type given: ${type}`);
      }
      const index = this._getActionOverflowIndex(type, key);
      const action = {
        type,
        key,
        overflow,
      };
      if (!overflow && index !== -1) {
        this.splice('_overflowActions', index, 1);
      } else if (overflow) {
        this.push('_overflowActions', action);
      }
    },

    setActionPriority(type, key, priority) {
      if (type !== ActionType.CHANGE && type !== ActionType.REVISION) {
        throw Error(`Invalid action type given: ${type}`);
      }
      const index = this._actionPriorityOverrides.findIndex(action => {
        return action.type === type && action.key === key;
      });
      const action = {
        type,
        key,
        priority,
      };
      if (index !== -1) {
        this.set('_actionPriorityOverrides', index, action);
      } else {
        this.push('_actionPriorityOverrides', action);
      }
    },

    setActionHidden(type, key, hidden) {
      if (type !== ActionType.CHANGE && type !== ActionType.REVISION) {
        throw Error(`Invalid action type given: ${type}`);
      }

      const idx = this._hiddenActions.indexOf(key);
      if (hidden && idx === -1) {
        this.push('_hiddenActions', key);
      } else if (!hidden && idx !== -1) {
        this.splice('_hiddenActions', idx, 1);
      }
    },

    _indexOfActionButtonWithKey(key) {
      for (let i = 0; i < this._additionalActions.length; i++) {
        if (this._additionalActions[i].__key === key) {
          return i;
        }
      }
      return -1;
    },

    _getRevisionActions() {
      return this.$.restAPI.getChangeRevisionActions(this.changeNum,
          this.patchNum);
    },

    _shouldHideActions(actions, loading) {
      return loading || !actions || !actions.base || !actions.base.length;
    },

    _keyCount(changeRecord) {
      return Object.keys((changeRecord && changeRecord.base) || {}).length;
    },

    _actionsChanged(actionsChangeRecord, revisionActionsChangeRecord,
        additionalActionsChangeRecord) {
      const additionalActions = (additionalActionsChangeRecord &&
          additionalActionsChangeRecord.base) || [];
      this.hidden = this._keyCount(actionsChangeRecord) === 0 &&
          this._keyCount(revisionActionsChangeRecord) === 0 &&
              additionalActions.length === 0;
      this._actionLoadingMessage = null;
      this._disabledMenuActions = [];

      const revisionActions = revisionActionsChangeRecord.base || {};
      if (Object.keys(revisionActions).length !== 0 &&
          !revisionActions.download) {
        this.set('revisionActions.download', DOWNLOAD_ACTION);
      }
    },

    _getValuesFor(obj) {
      return Object.keys(obj).map(key => {
        return obj[key];
      });
    },

    _getLabelStatus(label) {
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
    _getTopMissingApproval() {
      if (!this.change ||
          !this.change.labels ||
          !this.change.permitted_labels) {
        return null;
      }
      let result;
      for (const label in this.change.labels) {
        if (!(label in this.change.permitted_labels)) {
          continue;
        }
        if (this.change.permitted_labels[label].length === 0) {
          continue;
        }
        const status = this._getLabelStatus(this.change.labels[label]);
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
        const score = this.change.permitted_labels[result].slice(-1)[0];
        const maxScore =
            Object.keys(this.change.labels[result].values).slice(-1)[0];
        if (score === maxScore) {
          // Allow quick approve only for maximal score.
          return {
            label: result,
            score,
          };
        }
      }
      return null;
    },

    _getQuickApproveAction() {
      const approval = this._getTopMissingApproval();
      if (!approval) {
        return null;
      }
      const action = Object.assign({}, QUICK_APPROVE_ACTION);
      action.label = approval.label + approval.score;
      const review = {
        drafts: 'PUBLISH_ALL_REVISIONS',
        labels: {},
      };
      review.labels[approval.label] = approval.score;
      action.payload = review;
      return action;
    },

    _getActionValues(actionsChangeRecord, primariesChangeRecord,
        additionalActionsChangeRecord, type) {
      if (!actionsChangeRecord || !primariesChangeRecord) { return []; }

      const actions = actionsChangeRecord.base || {};
      const primaryActionKeys = primariesChangeRecord.base || [];
      const result = [];
      const values = this._getValuesFor(
          type === ActionType.CHANGE ? ChangeActions : RevisionActions);
      for (const a in actions) {
        if (!values.includes(a)) { continue; }
        actions[a].__key = a;
        actions[a].__type = type;
        actions[a].__primary = primaryActionKeys.includes(a);
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

      let additionalActions = (additionalActionsChangeRecord &&
      additionalActionsChangeRecord.base) || [];
      additionalActions = additionalActions.filter(a => {
        return a.__type === type;
      }).map(a => {
        a.__primary = primaryActionKeys.includes(a.__key);
        // Triggers a re-render by ensuring object inequality.
        return Object.assign({}, a);
      });
      return result.concat(additionalActions);
    },

    _computeLoadingLabel(action) {
      return ActionLoadingLabels[action] || 'Working...';
    },

    _canSubmitChange() {
      return this.$.jsAPI.canSubmitChange(this.change,
          this._getRevision(this.change, this.patchNum));
    },

    _getRevision(change, patchNum) {
      const num = window.parseInt(patchNum, 10);
      for (const rev of Object.values(change.revisions)) {
        if (rev._number === num) {
          return rev;
        }
      }
      return null;
    },

    _modifyRevertMsg() {
      return this.$.jsAPI.modifyRevertMsg(this.change,
          this.$.confirmRevertDialog.message, this.commitMessage);
    },

    showRevertDialog() {
      this.$.confirmRevertDialog.populateRevertMessage(
          this.commitMessage, this.change.current_revision);
      this.$.confirmRevertDialog.message = this._modifyRevertMsg();
      this._showActionDialog(this.$.confirmRevertDialog);
    },

    _handleActionTap(e) {
      e.preventDefault();
      const el = Polymer.dom(e).rootTarget;
      const key = el.getAttribute('data-action-key');
      if (key.startsWith(ADDITIONAL_ACTION_KEY_PREFIX)) {
        this.fire(`${key}-tap`, {node: el});
        return;
      }
      const type = el.getAttribute('data-action-type');
      this._handleAction(type, key);
    },

    _handleOveflowItemTap(e) {
      this._handleAction(e.detail.action.__type, e.detail.action.__key);
    },

    _handleAction(type, key) {
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

    _handleChangeAction(key) {
      let action;
      switch (key) {
        case ChangeActions.REVERT:
          this.showRevertDialog();
          break;
        case ChangeActions.ABANDON:
          this._showActionDialog(this.$.confirmAbandonDialog);
          break;
        case QUICK_APPROVE_ACTION.key:
          action = this._allActionValues.find(o => {
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

    _handleRevisionAction(key) {
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
        // eslint-disable-next-line no-fallthrough
        default:
          this._fireAction(this._prependSlash(key),
              this.revisionActions[key], true);
      }
    },

    _prependSlash(key) {
      return key === '/' ? key : `/${key}`;
    },

    /**
     * Returns true if hasParent is defined (can be either true or false).
     * returns false otherwise.
     * @return {boolean} hasParent
     */
    _computeChainState(hasParent) {
      this._hasKnownChainState = true;
    },

    _calculateDisabled(action, hasKnownChainState) {
      if (action.__key === 'rebase' && hasKnownChainState === false) {
        return true;
      }
      return !action.enabled;
    },

    _handleConfirmDialogCancel() {
      this._hideAllDialogs();
    },

    _hideAllDialogs() {
      const dialogEls =
          Polymer.dom(this.root).querySelectorAll('.confirmDialog');
      for (const dialogEl of dialogEls) { dialogEl.hidden = true; }
      this.$.overlay.close();
    },

    _handleRebaseConfirm() {
      const el = this.$.confirmRebase;
      const payload = {base: el.base};
      this.$.overlay.close();
      el.hidden = true;
      this._fireAction('/rebase', this.revisionActions.rebase, true, payload);
    },

    _handleCherrypickConfirm() {
      const el = this.$.confirmCherrypick;
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

    _handleRevertDialogConfirm() {
      const el = this.$.confirmRevertDialog;
      this.$.overlay.close();
      el.hidden = true;
      this._fireAction('/revert', this.actions.revert, false,
          {message: el.message});
    },

    _handleAbandonDialogConfirm() {
      const el = this.$.confirmAbandonDialog;
      this.$.overlay.close();
      el.hidden = true;
      this._fireAction('/abandon', this.actions.abandon, false,
          {message: el.message});
    },

    _handleDeleteConfirm() {
      this._fireAction('/', this.actions[ChangeActions.DELETE], false);
    },

    _getActionOverflowIndex(type, key) {
      return this._overflowActions.findIndex(action => {
        return action.type === type && action.key === key;
      });
    },

    _setLoadingOnButtonWithKey(type, key) {
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
      const buttonEl = this.$$(`[data-action-key="${key}"]`);
      buttonEl.setAttribute('loading', true);
      buttonEl.disabled = true;
      return function() {
        this._actionLoadingMessage = null;
        buttonEl.removeAttribute('loading');
        buttonEl.disabled = false;
      }.bind(this);
    },

    _fireAction(endpoint, action, revAction, opt_payload) {
      const cleanupFn =
          this._setLoadingOnButtonWithKey(action.__type, action.__key);
      this._send(action.method, opt_payload, endpoint, revAction, cleanupFn)
          .then(this._handleResponse.bind(this, action));
    },

    _showActionDialog(dialog) {
      this._hideAllDialogs();

      dialog.hidden = false;
      this.$.overlay.open().then(() => {
        if (dialog.resetFocus) {
          dialog.resetFocus();
        }
      });
    },

    // TODO(rmistry): Redo this after
    // https://bugs.chromium.org/p/gerrit/issues/detail?id=4671 is resolved.
    _setLabelValuesOnRevert(newChangeId) {
      const labels = this.$.jsAPI.getLabelValuesPostRevert(this.change);
      if (labels) {
        const url = `/changes/${newChangeId}/revisions/current/review`;
        this.$.restAPI.send(this.actions.revert.method, url, {labels});
      }
    },

    _handleResponse(action, response) {
      if (!response) { return; }
      return this.$.restAPI.getResponseObject(response).then(obj => {
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
      });
    },

    _handleResponseError(response) {
      return response.text().then(errText => {
        this.fire('show-alert',
            {message: `Could not perform action: ${errText}`});
        if (!errText.startsWith('Change is already up to date')) {
          throw Error(errText);
        }
      });
    },

    _send(method, payload, actionEndpoint, revisionAction, cleanupFn,
        opt_errorFn) {
      return this.fetchIsLatestKnown(this.change, this.$.restAPI)
          .then(isLatest => {
            if (!isLatest) {
              this.fire('show-alert', {
                message: 'Cannot set label: a newer patch has been ' +
                    'uploaded to this change.',
                action: 'Reload',
                callback: () => {
                  // Load the current change without any patch range.
                  Gerrit.Nav.navigateToChange(this.change);
                },
              });
              cleanupFn();
              return Promise.resolve();
            }

            const url = this.$.restAPI.getChangeActionURL(this.changeNum,
                revisionAction ? this.patchNum : null, actionEndpoint);
            return this.$.restAPI.send(method, url, payload,
                this._handleResponseError, this).then(response => {
                  cleanupFn.call(this);
                  return response;
                });
          });
    },

    _handleAbandonTap() {
      this._showActionDialog(this.$.confirmAbandonDialog);
    },

    _handleCherrypickTap() {
      this.$.confirmCherrypick.branch = '';
      this._showActionDialog(this.$.confirmCherrypick);
    },

    _handleDownloadTap() {
      this.fire('download-tap', null, {bubbles: false});
    },

    _handleDeleteTap() {
      this._showActionDialog(this.$.confirmDeleteDialog);
    },

    _handleWipTap() {
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
    _computeAllActions(changeActionsRecord, revisionActionsRecord,
        primariesRecord, additionalActionsRecord, change) {
      const revisionActionValues = this._getActionValues(revisionActionsRecord,
          primariesRecord, additionalActionsRecord, ActionType.REVISION);
      const changeActionValues = this._getActionValues(changeActionsRecord,
          primariesRecord, additionalActionsRecord, ActionType.CHANGE, change);
      const quickApprove = this._getQuickApproveAction();
      if (quickApprove) {
        changeActionValues.unshift(quickApprove);
      }
      return revisionActionValues
          .concat(changeActionValues)
          .sort(this._actionComparator.bind(this));
    },

    _getActionPriority(action) {
      if (action.__type && action.__key) {
        const overrideAction = this._actionPriorityOverrides.find(i => {
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
    _actionComparator(actionA, actionB) {
      const priorityDelta = this._getActionPriority(actionA) -
          this._getActionPriority(actionB);
      // Sort by the button label if same priority.
      if (priorityDelta === 0) {
        return actionA.label > actionB.label ? 1 : -1;
      } else {
        return priorityDelta;
      }
    },

    _computeTopLevelActions(actionRecord, hiddenActionsRecord) {
      const hiddenActions = hiddenActionsRecord.base || [];
      return actionRecord.base.filter(a => {
        const overflow = this._getActionOverflowIndex(a.__type, a.__key) !== -1;
        return !(overflow || hiddenActions.includes(a.__key));
      });
    },

    _computeMenuActions(actionRecord, hiddenActionsRecord) {
      const hiddenActions = hiddenActionsRecord.base || [];
      return actionRecord.base.filter(a => {
        const overflow = this._getActionOverflowIndex(a.__type, a.__key) !== -1;
        return overflow && !hiddenActions.includes(a.__key);
      }).map(action => {
        let key = action.__key;
        if (key === '/') { key = 'delete'; }
        return {
          name: action.label,
          id: `${key}-${action.__type}`,
          action,
        };
      });
    },
  });
})();
