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
(function() {
  'use strict';

  const ERR_BRANCH_EMPTY = 'The destination branch can’t be empty.';
  const ERR_COMMIT_EMPTY = 'The commit message can’t be empty.';
  const ERR_REVISION_ACTIONS = 'Couldn’t load revision actions.';
  /**
   * @enum {string}
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
    OPTIONAL: 'OPTIONAL',
  };

  // TODO(davido): Add the rest of the change actions.
  const ChangeActions = {
    ABANDON: 'abandon',
    DELETE: '/',
    DELETE_EDIT: 'deleteEdit',
    EDIT: 'edit',
    FOLLOW_UP: 'followup',
    IGNORE: 'ignore',
    MOVE: 'move',
    PRIVATE: 'private',
    PRIVATE_DELETE: 'private.delete',
    PUBLISH_EDIT: 'publishEdit',
    REBASE_EDIT: 'rebaseEdit',
    RESTORE: 'restore',
    REVERT: 'revert',
    REVIEWED: 'reviewed',
    STOP_EDIT: 'stopEdit',
    UNIGNORE: 'unignore',
    UNREVIEWED: 'unreviewed',
    WIP: 'wip',
  };

  // TODO(andybons): Add the rest of the revision actions.
  const RevisionActions = {
    CHERRYPICK: 'cherrypick',
    REBASE: 'rebase',
    SUBMIT: 'submit',
    DOWNLOAD: 'download',
  };

  const ActionLoadingLabels = {
    abandon: 'Abandoning...',
    cherrypick: 'Cherry-picking...',
    delete: 'Deleting...',
    move: 'Moving..',
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
    label: 'Quick approve',
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

  const REBASE_EDIT = {
    enabled: true,
    label: 'Rebase edit',
    title: 'Rebase change edit',
    __key: 'rebaseEdit',
    __primary: false,
    __type: 'change',
    method: 'POST',
  };

  const PUBLISH_EDIT = {
    enabled: true,
    label: 'Publish edit',
    title: 'Publish change edit',
    __key: 'publishEdit',
    __primary: false,
    __type: 'change',
    method: 'POST',
  };

  const DELETE_EDIT = {
    enabled: true,
    label: 'Delete edit',
    title: 'Delete change edit',
    __key: 'deleteEdit',
    __primary: false,
    __type: 'change',
    method: 'DELETE',
  };

  const EDIT = {
    enabled: true,
    label: 'Edit',
    title: 'Edit this change',
    __key: 'edit',
    __primary: false,
    __type: 'change',
  };

  const STOP_EDIT = {
    enabled: true,
    label: 'Stop editing',
    title: 'Stop editing this change',
    __key: 'stopEdit',
    __primary: false,
    __type: 'change',
  };

  const AWAIT_CHANGE_ATTEMPTS = 5;
  const AWAIT_CHANGE_TIMEOUT_MS = 1000;

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
      /**
       * @type {{
       *    _number: number,
       *    branch: string,
       *    id: string,
       *    project: string,
       *    subject: string,
       *  }}
       */
      change: Object,
      actions: {
        type: Object,
        value() { return {}; },
      },
      primaryActionKeys: {
        type: Array,
        value() {
          return [
            RevisionActions.SUBMIT,
          ];
        },
      },
      _hasKnownChainState: {
        type: Boolean,
        value: false,
      },
      _hideQuickApproveAction: {
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
      latestPatchNum: String,
      commitMessage: {
        type: String,
        value: '',
      },
      /** @type {?} */
      revisionActions: {
        type: Object,
        value() { return {}; },
      },
      privateByDefault: String,

      _loading: {
        type: Boolean,
        value: true,
      },
      _actionLoadingMessage: {
        type: String,
        value: '',
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
        observer: '_filterPrimaryActions',
      },
      _topLevelPrimaryActions: Array,
      _topLevelSecondaryActions: Array,
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
              key: RevisionActions.CHERRYPICK,
            },
            {
              type: ActionType.CHANGE,
              key: ChangeActions.MOVE,
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
              key: ChangeActions.REVIEWED,
            },
            {
              type: ActionType.CHANGE,
              key: ChangeActions.UNREVIEWED,
            },
            {
              type: ActionType.CHANGE,
              key: ChangeActions.PRIVATE,
            },
            {
              type: ActionType.CHANGE,
              key: ChangeActions.PRIVATE_DELETE,
            },
            {
              type: ActionType.CHANGE,
              key: ChangeActions.FOLLOW_UP,
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
      // editPatchsetLoaded == "does the current selected patch range have
      // 'edit' as one of either basePatchNum or patchNum".
      editPatchsetLoaded: {
        type: Boolean,
        value: false,
      },
      // editMode == "is edit mode enabled in the file list".
      editMode: {
        type: Boolean,
        value: false,
      },
      editBasedOnCurrentPatchSet: {
        type: Boolean,
        value: true,
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
      '_changeChanged(change)',
      '_editStatusChanged(editMode, editPatchsetLoaded, ' +
          'editBasedOnCurrentPatchSet, actions.*, change)',
    ],

    listeners: {
      'fullscreen-overlay-opened': '_handleHideBackgroundContent',
      'fullscreen-overlay-closed': '_handleShowBackgroundContent',
    },

    ready() {
      this.$.jsAPI.addElement(this.$.jsAPI.Element.CHANGE_ACTIONS, this);
      this._handleLoadingComplete();
    },

    reload() {
      if (!this.changeNum || !this.latestPatchNum) {
        return Promise.resolve();
      }

      this._loading = true;
      return this._getRevisionActions().then(revisionActions => {
        if (!revisionActions) { return; }

        this.revisionActions = revisionActions;
        this._handleLoadingComplete();
      }).catch(err => {
        this.fire('show-alert', {message: ERR_REVISION_ACTIONS});
        this._loading = false;
        throw err;
      });
    },

    _handleLoadingComplete() {
      Gerrit.awaitPluginsLoaded().then(() => this._loading = false);
    },

    _changeChanged() {
      this.reload();
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

    getActionDetails(action) {
      if (this.revisionActions[action]) {
        return this.revisionActions[action];
      } else if (this.actions[action]) {
        return this.actions[action];
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
          this.latestPatchNum);
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
      this._actionLoadingMessage = '';
      this._disabledMenuActions = [];

      const revisionActions = revisionActionsChangeRecord.base || {};
      if (Object.keys(revisionActions).length !== 0) {
        if (!revisionActions.download) {
          this.set('revisionActions.download', DOWNLOAD_ACTION);
        }
      }
    },

    _editStatusChanged(editMode, editPatchsetLoaded,
        editBasedOnCurrentPatchSet) {
      const changeActions = this.actions;

      if (editPatchsetLoaded) {
        // Only show actions that mutate an edit if an actual edit patch set
        // is loaded.
        if (this.changeIsOpen(this.change.status)) {
          if (editBasedOnCurrentPatchSet) {
            if (!changeActions.publishEdit) {
              this.set('actions.publishEdit', PUBLISH_EDIT);
            }
            if (changeActions.rebaseEdit) {
              delete this.actions.rebaseEdit;
              this.notifyPath('actions.rebaseEdit');
            }
          } else {
            if (!changeActions.rebaseEdit) {
              this.set('actions.rebaseEdit', REBASE_EDIT);
            }
            if (changeActions.publishEdit) {
              delete this.actions.publishEdit;
              this.notifyPath('actions.publishEdit');
            }
          }
        }
        if (!changeActions.deleteEdit) {
          this.set('actions.deleteEdit', DELETE_EDIT);
        }
      } else {
        if (changeActions.publishEdit) {
          delete this.actions.publishEdit;
          this.notifyPath('actions.publishEdit');
        }
        if (changeActions.rebaseEdit) {
          delete this.actions.rebaseEdit;
          this.notifyPath('actions.rebaseEdit');
        }
        if (changeActions.deleteEdit) {
          delete this.actions.deleteEdit;
          this.notifyPath('actions.deleteEdit');
        }
      }

      if (this.changeIsOpen(this.change.status)) {
        // Only show edit button if there is no edit patchset loaded and the
        // file list is not in edit mode.
        if (editPatchsetLoaded || editMode) {
          if (changeActions.edit) {
            delete this.actions.edit;
            this.notifyPath('actions.edit');
          }
        } else {
          if (!changeActions.edit) { this.set('actions.edit', EDIT); }
        }
        // Only show STOP_EDIT if edit mode is enabled, but no edit patch set
        // is loaded.
        if (editMode && !editPatchsetLoaded) {
          if (!changeActions.stopEdit) {
            this.set('actions.stopEdit', STOP_EDIT);
          }
        } else {
          if (changeActions.stopEdit) {
            delete this.actions.stopEdit;
            this.notifyPath('actions.stopEdit');
          }
        }
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
     * @return {{label: string, score: string}|null}
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

    hideQuickApproveAction() {
      this._topLevelSecondaryActions =
        this._topLevelSecondaryActions.filter(sa => {
          return sa.key !== QUICK_APPROVE_ACTION.key;
        });
      this._hideQuickApproveAction = true;
    },

    _getQuickApproveAction() {
      if (this._hideQuickApproveAction) {
        return null;
      }
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
      const pluginActions = [];
      Object.keys(actions).forEach(a => {
        actions[a].__key = a;
        actions[a].__type = type;
        actions[a].__primary = primaryActionKeys.includes(a);
        // Plugin actions always contain ~ in the key.
        if (a.indexOf('~') !== -1) {
          this._populateActionUrl(actions[a]);
          pluginActions.push(actions[a]);
          // Add server-side provided plugin actions to overflow menu.
          this._overflowActions.push({
            type,
            key: a,
          });
          return;
        } else if (!values.includes(a)) {
          return;
        }
        actions[a].label = this._getActionLabel(actions[a], type);

        // Triggers a re-render by ensuring object inequality.
        result.push(Object.assign({}, actions[a]));
      });

      let additionalActions = (additionalActionsChangeRecord &&
      additionalActionsChangeRecord.base) || [];
      additionalActions = additionalActions.filter(a => {
        return a.__type === type;
      }).map(a => {
        a.__primary = primaryActionKeys.includes(a.__key);
        // Triggers a re-render by ensuring object inequality.
        return Object.assign({}, a);
      });
      return result.concat(additionalActions).concat(pluginActions);
    },

    _populateActionUrl(action) {
      const patchNum =
            action.__type === ActionType.REVISION ? this.latestPatchNum : null;
      this.$.restAPI.getChangeActionURL(
          this.changeNum, patchNum, '/' + action.__key)
          .then(url => action.__url = url);
    },

    /**
     * Given a change action, return a display label that uses the appropriate
     * casing or includes explanatory details.
     */
    _getActionLabel(action, type) {
      if (action.label === 'Delete' && type === ActionType.CHANGE) {
        // This label is common within change and revision actions. Make it more
        // explicit to the user.
        return 'Delete change';
      } else if (action.label === 'WIP' && type === ActionType.CHANGE) {
        return 'Mark as work in progress';
      }
      // Otherwise, just map the anme to sentence case.
      return this._toSentenceCase(action.label);
    },

    /**
     * Capitalize the first letter and lowecase all others.
     * @param {string} s
     * @return {string}
     */
    _toSentenceCase(s) {
      if (!s.length) { return ''; }
      return s[0].toUpperCase() + s.slice(1).toLowerCase();
    },

    _computeLoadingLabel(action) {
      return ActionLoadingLabels[action] || 'Working...';
    },

    _canSubmitChange() {
      return this.$.jsAPI.canSubmitChange(this.change,
          this._getRevision(this.change, this.latestPatchNum));
    },

    _getRevision(change, patchNum) {
      for (const rev of Object.values(change.revisions)) {
        if (this.patchNumEquals(rev._number, patchNum)) {
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
      const el = Polymer.dom(e).localTarget;
      const key = el.getAttribute('data-action-key');
      if (key.startsWith(ADDITIONAL_ACTION_KEY_PREFIX) ||
          key.indexOf('~') !== -1) {
        this.fire(`${key}-tap`, {node: el});
        return;
      }
      const type = el.getAttribute('data-action-type');
      this._handleAction(type, key);
    },

    _handleOveflowItemTap(e) {
      e.preventDefault();
      const el = Polymer.dom(e).localTarget;
      const key = e.detail.action.__key;
      if (key.startsWith(ADDITIONAL_ACTION_KEY_PREFIX) ||
          key.indexOf('~') !== -1) {
        this.fire(`${key}-tap`, {node: el});
        return;
      }
      this._handleAction(e.detail.action.__type, e.detail.action.__key);
    },

    _handleAction(type, key) {
      this.$.reporting.reportInteraction(`${type}-${key}`);
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
        case ChangeActions.EDIT:
          this._handleEditTap();
          break;
        case ChangeActions.STOP_EDIT:
          this._handleStopEditTap();
          break;
        case ChangeActions.DELETE:
          this._handleDeleteTap();
          break;
        case ChangeActions.DELETE_EDIT:
          this._handleDeleteEditTap();
          break;
        case ChangeActions.FOLLOW_UP:
          this._handleFollowUpTap();
          break;
        case ChangeActions.WIP:
          this._handleWipTap();
          break;
        case ChangeActions.MOVE:
          this._handleMoveTap();
          break;
        case ChangeActions.PUBLISH_EDIT:
          this._handlePublishEditTap();
          break;
        case ChangeActions.REBASE_EDIT:
          this._handleRebaseEditTap();
          break;
        default:
          this._fireAction(this._prependSlash(key), this.actions[key], false);
      }
    },

    _handleRevisionAction(key) {
      switch (key) {
        case RevisionActions.REBASE:
          this._showActionDialog(this.$.confirmRebase);
          this.$.confirmRebase.fetchRecentChanges();
          break;
        case RevisionActions.CHERRYPICK:
          this._handleCherrypickTap();
          break;
        case RevisionActions.DOWNLOAD:
          this._handleDownloadTap();
          break;
        case RevisionActions.SUBMIT:
          if (!this._canSubmitChange()) { return; }
          this._showActionDialog(this.$.confirmSubmitDialog);
          break;
        default:
          this._fireAction(this._prependSlash(key),
              this.revisionActions[key], true);
      }
    },

    _prependSlash(key) {
      return key === '/' ? key : `/${key}`;
    },

    /**
     * _hasKnownChainState set to true true if hasParent is defined (can be
     * either true or false). set to false otherwise.
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

    _handleRebaseConfirm(e) {
      const el = this.$.confirmRebase;
      const payload = {base: e.detail.base};
      this.$.overlay.close();
      el.hidden = true;
      this._fireAction('/rebase', this.revisionActions.rebase, true, payload);
    },

    _handleCherrypickConfirm() {
      const el = this.$.confirmCherrypick;
      if (!el.branch) {
        // TODO(davido): Fix error handling
        this.fire('show-alert', {message: ERR_BRANCH_EMPTY});
        return;
      }
      if (!el.message) {
        this.fire('show-alert', {message: ERR_COMMIT_EMPTY});
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

    _handleMoveConfirm() {
      const el = this.$.confirmMove;
      if (!el.branch) {
        this.fire('show-alert', {message: ERR_BRANCH_EMPTY});
        return;
      }
      this.$.overlay.close();
      el.hidden = true;
      this._fireAction(
          '/move',
          this.actions.move,
          false,
          {
            destination_branch: el.branch,
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

    _handleCreateFollowUpChange() {
      this.$.createFollowUpChange.handleCreateChange();
      this._handleCloseCreateFollowUpChange();
    },

    _handleCloseCreateFollowUpChange() {
      this.$.overlay.close();
    },

    _handleDeleteConfirm() {
      this._fireAction('/', this.actions[ChangeActions.DELETE], false);
    },

    _handleDeleteEditConfirm() {
      this._hideAllDialogs();

      this._fireAction('/edit', this.actions.deleteEdit, false);
    },

    _handleSubmitConfirm() {
      if (!this._canSubmitChange()) { return; }
      this._hideAllDialogs();
      this._fireAction('/submit', this.revisionActions.submit, true);
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
          this._actionLoadingMessage = '';
          this._disabledMenuActions = [];
        }.bind(this);
      }

      // Otherwise it's a top-level action.
      const buttonEl = this.$$(`[data-action-key="${key}"]`);
      buttonEl.setAttribute('loading', true);
      buttonEl.disabled = true;
      return function() {
        this._actionLoadingMessage = '';
        buttonEl.removeAttribute('loading');
        buttonEl.disabled = false;
      }.bind(this);
    },

    /**
     * @param {string} endpoint
     * @param {!Object|undefined} action
     * @param {boolean} revAction
     * @param {!Object|string=} opt_payload
     */
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
      if (!labels) { return Promise.resolve(); }
      return this.$.restAPI.getChangeURLAndSend(newChangeId,
          this.actions.revert.method, 'current', '/review', {labels});
    },

    _handleResponse(action, response) {
      if (!response) { return; }
      return this.$.restAPI.getResponseObject(response).then(obj => {
        switch (action.__key) {
          case ChangeActions.REVERT:
            this._waitForChangeReachable(obj._number)
                .then(() => this._setLabelValuesOnRevert(obj._number))
                .then(() => {
                  Gerrit.Nav.navigateToChange(obj);
                });
            break;
          case RevisionActions.CHERRYPICK:
            this._waitForChangeReachable(obj._number).then(() => {
              Gerrit.Nav.navigateToChange(obj);
            });
            break;
          case ChangeActions.DELETE:
            if (action.__type === ActionType.CHANGE) {
              page.show('/');
            }
            break;
          case ChangeActions.WIP:
          case ChangeActions.DELETE_EDIT:
          case ChangeActions.PUBLISH_EDIT:
          case ChangeActions.REBASE_EDIT:
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

    /**
     * @param {string} method
     * @param {string|!Object|undefined} payload
     * @param {string} actionEndpoint
     * @param {boolean} revisionAction
     * @param {?Function} cleanupFn
     * @param {?Function=} opt_errorFn
     */
    _send(method, payload, actionEndpoint, revisionAction, cleanupFn,
        opt_errorFn) {
      const handleError = response => {
        cleanupFn.call(this);
        this._handleResponseError(response);
      };

      return this.fetchChangeUpdates(this.change, this.$.restAPI)
          .then(result => {
            if (!result.isLatest) {
              this.fire('show-alert', {
                message: 'Cannot set label: a newer patch has been ' +
                    'uploaded to this change.',
                action: 'Reload',
                callback: () => {
                  // Load the current change without any patch range.
                  Gerrit.Nav.navigateToChange(this.change);
                },
              });

              // Because this is not a network error, call the cleanup function
              // but not the error handler.
              cleanupFn();

              return Promise.resolve();
            }
            const patchNum = revisionAction ? this.latestPatchNum : null;
            return this.$.restAPI.getChangeURLAndSend(this.changeNum, method,
                patchNum, actionEndpoint, payload, handleError, this)
                .then(response => {
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

    _handleMoveTap() {
      this.$.confirmMove.branch = '';
      this.$.confirmMove.message = '';
      this._showActionDialog(this.$.confirmMove);
    },

    _handleDownloadTap() {
      this.fire('download-tap', null, {bubbles: false});
    },

    _handleDeleteTap() {
      this._showActionDialog(this.$.confirmDeleteDialog);
    },

    _handleDeleteEditTap() {
      this._showActionDialog(this.$.confirmDeleteEditDialog);
    },

    _handleFollowUpTap() {
      this._showActionDialog(this.$.createFollowUpDialog);
    },

    _handleWipTap() {
      this._fireAction('/wip', this.actions.wip, false);
    },

    _handlePublishEditTap() {
      this._fireAction('/edit:publish', this.actions.publishEdit, false);
    },

    _handleRebaseEditTap() {
      this._fireAction('/edit:rebase', this.actions.rebaseEdit, false);
    },

    _handleHideBackgroundContent() {
      this.$.mainContent.classList.add('overlayOpen');
    },

    _handleShowBackgroundContent() {
      this.$.mainContent.classList.remove('overlayOpen');
    },

    /**
     * Merge sources of change actions into a single ordered array of action
     * values.
     * @param {!Array} changeActionsRecord
     * @param {!Array} revisionActionsRecord
     * @param {!Array} primariesRecord
     * @param {!Array} additionalActionsRecord
     * @param {!Object} change The change object.
     * @return {!Array}
     */
    _computeAllActions(changeActionsRecord, revisionActionsRecord,
        primariesRecord, additionalActionsRecord, change) {
      const revisionActionValues = this._getActionValues(revisionActionsRecord,
          primariesRecord, additionalActionsRecord, ActionType.REVISION);
      const changeActionValues = this._getActionValues(changeActionsRecord,
          primariesRecord, additionalActionsRecord, ActionType.CHANGE);
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

    _filterPrimaryActions(_topLevelActions) {
      this._topLevelPrimaryActions = _topLevelActions.filter(action =>
          action.__primary);
      this._topLevelSecondaryActions = _topLevelActions.filter(action =>
          !action.__primary);
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
          tooltip: action.title,
        };
      });
    },

    /**
     * Occasionally, a change created by a change action is not yet knwon to the
     * API for a brief time. Wait for the given change number to be recognized.
     *
     * Returns a promise that resolves with true if a request is recognized, or
     * false if the change was never recognized after all attempts.
     *
     * @param  {number} changeNum
     * @return {Promise<boolean>}
     */
    _waitForChangeReachable(changeNum) {
      let attempsRemaining = AWAIT_CHANGE_ATTEMPTS;
      return new Promise(resolve => {
        const check = () => {
          attempsRemaining--;
          // Pass a no-op error handler to avoid the "not found" error toast.
          this.$.restAPI.getChange(changeNum, () => {}).then(response => {
            // If the response is 404, the response will be undefined.
            if (response) {
              resolve(true);
              return;
            }

            if (attempsRemaining) {
              this.async(check, AWAIT_CHANGE_TIMEOUT_MS);
            } else {
              resolve(false);
            }
          });
        };
        check();
      });
    },

    _handleEditTap() {
      this.dispatchEvent(new CustomEvent('edit-tap', {bubbles: false}));
    },

    _handleStopEditTap() {
      this.dispatchEvent(new CustomEvent('stop-edit-tap', {bubbles: false}));
    },

    _computeHasTooltip(title) {
      return !!title;
    },
  });
})();
