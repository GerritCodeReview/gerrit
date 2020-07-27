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
import '../../admin/gr-create-change-dialog/gr-create-change-dialog.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-dialog/gr-dialog.js';
import '../../shared/gr-dropdown/gr-dropdown.js';
import '../../shared/gr-icons/gr-icons.js';
import '../../shared/gr-js-api-interface/gr-js-api-interface.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-confirm-abandon-dialog/gr-confirm-abandon-dialog.js';
import '../gr-confirm-cherrypick-dialog/gr-confirm-cherrypick-dialog.js';
import '../gr-confirm-cherrypick-conflict-dialog/gr-confirm-cherrypick-conflict-dialog.js';
import '../gr-confirm-move-dialog/gr-confirm-move-dialog.js';
import '../gr-confirm-rebase-dialog/gr-confirm-rebase-dialog.js';
import '../gr-confirm-revert-dialog/gr-confirm-revert-dialog.js';
import '../gr-confirm-revert-submission-dialog/gr-confirm-revert-submission-dialog.js';
import '../gr-confirm-submit-dialog/gr-confirm-submit-dialog.js';
import '../../../styles/shared-styles.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-change-actions_html.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {pluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';
import {appContext} from '../../../services/app-context.js';
import {
  fetchChangeUpdates,
  patchNumEquals,
} from '../../../utils/patch-set-util.js';
import {
  changeIsOpen,
  ListChangesOption,
  listChangesOptionsToHex,
} from '../../../utils/change-util.js';

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
  REBASE: 'rebase',
  REBASE_EDIT: 'rebaseEdit',
  READY: 'ready',
  RESTORE: 'restore',
  REVERT: 'revert',
  REVERT_SUBMISSION: 'revert_submission',
  REVIEWED: 'reviewed',
  STOP_EDIT: 'stopEdit',
  SUBMIT: 'submit',
  UNIGNORE: 'unignore',
  UNREVIEWED: 'unreviewed',
  WIP: 'wip',
};

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
  revert_submission: 'Reverting Submission...',
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

// Set of keys that have icons. As more icons are added to gr-icons.html, this
// set should be expanded.
const ACTIONS_WITH_ICONS = new Set([
  ChangeActions.ABANDON,
  ChangeActions.DELETE_EDIT,
  ChangeActions.EDIT,
  ChangeActions.PUBLISH_EDIT,
  ChangeActions.READY,
  ChangeActions.REBASE_EDIT,
  ChangeActions.RESTORE,
  ChangeActions.REVERT,
  ChangeActions.REVERT_SUBMISSION,
  ChangeActions.STOP_EDIT,
  QUICK_APPROVE_ACTION.key,
  RevisionActions.REBASE,
  RevisionActions.SUBMIT,
]);

const AWAIT_CHANGE_ATTEMPTS = 5;
const AWAIT_CHANGE_TIMEOUT_MS = 1000;

const REVERT_TYPES = {
  REVERT_SINGLE_CHANGE: 1,
  REVERT_SUBMISSION: 2,
};

/* Revert submission is skipped as the normal revert dialog will now show
the user a choice between reverting single change or an entire submission.
Hence, a second button is not needed.
*/
const SKIP_ACTION_KEYS = [ChangeActions.REVERT_SUBMISSION];

const SKIP_ACTION_KEYS_ATTENTION_SET = [
  ChangeActions.REVIEWED,
  ChangeActions.UNREVIEWED,
];

/**
 * @extends PolymerElement
 */
class GrChangeActions extends GestureEventListeners(
    LegacyElementMixin(PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-change-actions'; }
  /**
   * Fired when the change should be reloaded.
   *
   * @event reload-change
   */

  /**
   * Fired when an action is tapped.
   *
   * @event custom-tap - naming pattern: <action key>-tap
   */

  /**
   * Fires to show an alert when a send is attempted on the non-latest patch.
   *
   * @event show-alert
   */

  /**
   * Fires when a change action fails.
   *
   * @event show-error
   */

  constructor() {
    super();
    this.ActionType = ActionType;
    this.ChangeActions = ChangeActions;
    this.RevisionActions = RevisionActions;
    this.reporting = appContext.reportingService;
  }

  static get properties() {
    return {
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
            ChangeActions.READY,
            RevisionActions.SUBMIT,
          ];
        },
      },
      disableEdit: {
        type: Boolean,
        value: false,
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
        notify: true,
        value() { return {}; },
      },
      // If property binds directly to [[revisionActions.submit]] it is not
      // updated when revisionActions doesn't contain submit action.
      /** @type {?} */
      _revisionSubmitAction: {
        type: Object,
        computed: '_getSubmitAction(revisionActions)',
      },
      // If property binds directly to [[revisionActions.rebase]] it is not
      // updated when revisionActions doesn't contain rebase action.
      /** @type {?} */
      _revisionRebaseAction: {
        type: Object,
        computed: '_getRebaseAction(revisionActions)',
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
          '_config, _actionPriorityOverrides.*)',
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
        computed: '_computeMenuActions(_allActionValues.*, ' +
          '_hiddenActions.*, _overflowActions.*)',
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
      _config: Object,
    };
  }

  static get observers() {
    return [
      '_actionsChanged(actions.*, revisionActions.*, _additionalActions.*)',
      '_changeChanged(change)',
      '_editStatusChanged(editMode, editPatchsetLoaded, ' +
        'editBasedOnCurrentPatchSet, disableEdit, actions.*, change.*)',
    ];
  }

  /** @override */
  created() {
    super.created();
    this.addEventListener('fullscreen-overlay-opened',
        () => this._handleHideBackgroundContent());
    this.addEventListener('fullscreen-overlay-closed',
        () => this._handleShowBackgroundContent());
  }

  /** @override */
  ready() {
    super.ready();
    this.$.jsAPI.addElement(this.$.jsAPI.Element.CHANGE_ACTIONS, this);
    this.$.restAPI.getConfig().then(config => {
      this._config = config;
    });
    this._handleLoadingComplete();
  }

  _getSubmitAction(revisionActions) {
    return this._getRevisionAction(revisionActions, 'submit', null);
  }

  _getRebaseAction(revisionActions) {
    return this._getRevisionAction(revisionActions, 'rebase', null);
  }

  _getRevisionAction(revisionActions, actionName, emptyActionValue) {
    if (!revisionActions) {
      return undefined;
    }
    if (revisionActions[actionName] === undefined) {
      // Return null to fire an event when reveisionActions was loaded
      // but doesn't contain actionName. undefined doesn't fire an event
      return emptyActionValue;
    }
    return revisionActions[actionName];
  }

  reload() {
    if (!this.changeNum || !this.latestPatchNum) {
      return Promise.resolve();
    }

    this._loading = true;
    return this._getRevisionActions()
        .then(revisionActions => {
          if (!revisionActions) { return; }

          this.revisionActions = revisionActions;
          this._sendShowRevisionActions({
            change: this.change,
            revisionActions,
          });
          this._handleLoadingComplete();
        })
        .catch(err => {
          this.dispatchEvent(new CustomEvent('show-alert', {
            detail: {message: ERR_REVISION_ACTIONS},
            composed: true, bubbles: true,
          }));
          this._loading = false;
          throw err;
        });
  }

  _handleLoadingComplete() {
    pluginLoader.awaitPluginsLoaded().then(() => this._loading = false);
  }

  _sendShowRevisionActions(detail) {
    this.$.jsAPI.handleEvent(
        this.$.jsAPI.EventType.SHOW_REVISION_ACTIONS,
        detail
    );
  }

  _changeChanged() {
    this.reload();
  }

  addActionButton(type, label) {
    if (type !== ActionType.CHANGE && type !== ActionType.REVISION) {
      throw Error(`Invalid action type: ${type}`);
    }
    const action = {
      enabled: true,
      label,
      __type: type,
      __key: ADDITIONAL_ACTION_KEY_PREFIX +
          Math.random().toString(36)
              .substr(2),
    };
    this.push('_additionalActions', action);
    return action.__key;
  }

  removeActionButton(key) {
    const idx = this._indexOfActionButtonWithKey(key);
    if (idx === -1) {
      return;
    }
    this.splice('_additionalActions', idx, 1);
  }

  setActionButtonProp(key, prop, value) {
    this.set([
      '_additionalActions',
      this._indexOfActionButtonWithKey(key),
      prop,
    ], value);
  }

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
  }

  setActionPriority(type, key, priority) {
    if (type !== ActionType.CHANGE && type !== ActionType.REVISION) {
      throw Error(`Invalid action type given: ${type}`);
    }
    const index = this._actionPriorityOverrides
        .findIndex(action => action.type === type && action.key === key);
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
  }

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
  }

  getActionDetails(action) {
    if (this.revisionActions[action]) {
      return this.revisionActions[action];
    } else if (this.actions[action]) {
      return this.actions[action];
    }
  }

  _indexOfActionButtonWithKey(key) {
    for (let i = 0; i < this._additionalActions.length; i++) {
      if (this._additionalActions[i].__key === key) {
        return i;
      }
    }
    return -1;
  }

  _getRevisionActions() {
    return this.$.restAPI.getChangeRevisionActions(this.changeNum,
        this.latestPatchNum);
  }

  _shouldHideActions(actions, loading) {
    return loading || !actions || !actions.base || !actions.base.length;
  }

  _keyCount(changeRecord) {
    return Object.keys((changeRecord && changeRecord.base) || {}).length;
  }

  _actionsChanged(actionsChangeRecord, revisionActionsChangeRecord,
      additionalActionsChangeRecord) {
    // Polymer 2: check for undefined
    if ([
      actionsChangeRecord,
      revisionActionsChangeRecord,
      additionalActionsChangeRecord,
    ].includes(undefined)) {
      return;
    }

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
  }

  /**
   * @param {string=} actionName
   */
  _deleteAndNotify(actionName) {
    if (this.actions && this.actions[actionName]) {
      delete this.actions[actionName];
      // We assign a fake value of 'false' to support Polymer 2
      // see https://github.com/Polymer/polymer/issues/2631
      this.notifyPath('actions.' + actionName, false);
    }
  }

  _editStatusChanged(editMode, editPatchsetLoaded,
      editBasedOnCurrentPatchSet, disableEdit) {
    // Polymer 2: check for undefined
    if ([
      editMode,
      editBasedOnCurrentPatchSet,
      disableEdit,
    ].includes(undefined)) {
      return;
    }

    if (disableEdit) {
      this._deleteAndNotify('publishEdit');
      this._deleteAndNotify('rebaseEdit');
      this._deleteAndNotify('deleteEdit');
      this._deleteAndNotify('stopEdit');
      this._deleteAndNotify('edit');
      return;
    }
    if (this.actions && editPatchsetLoaded) {
      // Only show actions that mutate an edit if an actual edit patch set
      // is loaded.
      if (changeIsOpen(this.change)) {
        if (editBasedOnCurrentPatchSet) {
          if (!this.actions.publishEdit) {
            this.set('actions.publishEdit', PUBLISH_EDIT);
          }
          this._deleteAndNotify('rebaseEdit');
        } else {
          if (!this.actions.rebaseEdit) {
            this.set('actions.rebaseEdit', REBASE_EDIT);
          }
          this._deleteAndNotify('publishEdit');
        }
      }
      if (!this.actions.deleteEdit) {
        this.set('actions.deleteEdit', DELETE_EDIT);
      }
    } else {
      this._deleteAndNotify('publishEdit');
      this._deleteAndNotify('rebaseEdit');
      this._deleteAndNotify('deleteEdit');
    }

    if (this.actions && changeIsOpen(this.change)) {
      // Only show edit button if there is no edit patchset loaded and the
      // file list is not in edit mode.
      if (editPatchsetLoaded || editMode) {
        this._deleteAndNotify('edit');
      } else {
        if (!this.actions.edit) { this.set('actions.edit', EDIT); }
      }
      // Only show STOP_EDIT if edit mode is enabled, but no edit patch set
      // is loaded.
      if (editMode && !editPatchsetLoaded) {
        if (!this.actions.stopEdit) {
          this.set('actions.stopEdit', STOP_EDIT);
        }
      } else {
        this._deleteAndNotify('stopEdit');
      }
    } else {
      // Remove edit button.
      this._deleteAndNotify('edit');
    }
  }

  _getValuesFor(obj) {
    return Object.keys(obj).map(key => obj[key]);
  }

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
  }

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
  }

  hideQuickApproveAction() {
    this._topLevelSecondaryActions =
      this._topLevelSecondaryActions
          .filter(sa => sa.key !== QUICK_APPROVE_ACTION.key);
    this._hideQuickApproveAction = true;
  }

  _getQuickApproveAction() {
    if (this._hideQuickApproveAction) {
      return null;
    }
    const approval = this._getTopMissingApproval();
    if (!approval) {
      return null;
    }
    const action = {...QUICK_APPROVE_ACTION};
    action.label = approval.label + approval.score;
    const review = {
      drafts: 'PUBLISH_ALL_REVISIONS',
      labels: {},
    };
    review.labels[approval.label] = approval.score;
    action.payload = review;
    return action;
  }

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
      actions[a].label = this._getActionLabel(actions[a]);

      // Triggers a re-render by ensuring object inequality.
      result.push({...actions[a]});
    });

    let additionalActions = (additionalActionsChangeRecord &&
    additionalActionsChangeRecord.base) || [];
    additionalActions = additionalActions
        .filter(a => a.__type === type)
        .map(a => {
          a.__primary = primaryActionKeys.includes(a.__key);
          // Triggers a re-render by ensuring object inequality.
          return {...a};
        });
    return result.concat(additionalActions).concat(pluginActions);
  }

  _populateActionUrl(action) {
    const patchNum =
          action.__type === ActionType.REVISION ? this.latestPatchNum : null;
    this.$.restAPI.getChangeActionURL(
        this.changeNum, patchNum, '/' + action.__key)
        .then(url => action.__url = url);
  }

  /**
   * Given a change action, return a display label that uses the appropriate
   * casing or includes explanatory details.
   */
  _getActionLabel(action) {
    if (action.label === 'Delete') {
      // This label is common within change and revision actions. Make it more
      // explicit to the user.
      return 'Delete change';
    } else if (action.label === 'WIP') {
      return 'Mark as work in progress';
    }
    // Otherwise, just map the name to sentence case.
    return this._toSentenceCase(action.label);
  }

  /**
   * Capitalize the first letter and lowecase all others.
   *
   * @param {string} s
   * @return {string}
   */
  _toSentenceCase(s) {
    if (!s.length) { return ''; }
    return s[0].toUpperCase() + s.slice(1).toLowerCase();
  }

  _computeLoadingLabel(action) {
    return ActionLoadingLabels[action] || 'Working...';
  }

  _canSubmitChange() {
    return this.$.jsAPI.canSubmitChange(this.change,
        this._getRevision(this.change, this.latestPatchNum));
  }

  _getRevision(change, patchNum) {
    for (const rev of Object.values(change.revisions)) {
      if (patchNumEquals(rev._number, patchNum)) {
        return rev;
      }
    }
    return null;
  }

  showRevertDialog() {
    // The search is still broken if there is a " in the topic.
    const query = `submissionid: "${this.change.submission_id}"`;
    /* A chromium plugin expects that the modifyRevertMsg hook will only
    be called after the revert button is pressed, hence we populate the
    revert dialog after revert button is pressed. */
    this.$.restAPI.getChanges('', query)
        .then(changes => {
          this.$.confirmRevertDialog.populate(this.change,
              this.commitMessage, changes);
          this._showActionDialog(this.$.confirmRevertDialog);
        });
  }

  showRevertSubmissionDialog() {
    const query = 'submissionid:' + this.change.submission_id;
    this.$.restAPI.getChanges('', query)
        .then(changes => {
          this.$.confirmRevertSubmissionDialog.
              _populateRevertSubmissionMessage(
                  this.commitMessage, this.change, changes);
          this._showActionDialog(this.$.confirmRevertSubmissionDialog);
        });
  }

  _handleActionTap(e) {
    e.preventDefault();
    let el = dom(e).localTarget;
    while (el.tagName.toLowerCase() !== 'gr-button') {
      if (!el.parentElement) { return; }
      el = el.parentElement;
    }

    const key = el.getAttribute('data-action-key');
    if (key.startsWith(ADDITIONAL_ACTION_KEY_PREFIX) ||
        key.indexOf('~') !== -1) {
      this.dispatchEvent(new CustomEvent(`${key}-tap`, {
        detail: {node: el},
        composed: true, bubbles: true,
      }));
      return;
    }
    const type = el.getAttribute('data-action-type');
    this._handleAction(type, key);
  }

  _handleOverflowItemTap(e) {
    e.preventDefault();
    const el = dom(e).localTarget;
    const key = e.detail.action.__key;
    if (key.startsWith(ADDITIONAL_ACTION_KEY_PREFIX) ||
        key.indexOf('~') !== -1) {
      this.dispatchEvent(new CustomEvent(`${key}-tap`, {
        detail: {node: el},
        composed: true, bubbles: true,
      }));
      return;
    }
    this._handleAction(e.detail.action.__type, e.detail.action.__key);
  }

  _handleAction(type, key) {
    this.reporting.reportInteraction(`${type}-${key}`);
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
  }

  _handleChangeAction(key) {
    let action;
    switch (key) {
      case ChangeActions.REVERT:
        this.showRevertDialog();
        break;
      case ChangeActions.REVERT_SUBMISSION:
        this.showRevertSubmissionDialog();
        break;
      case ChangeActions.ABANDON:
        this._showActionDialog(this.$.confirmAbandonDialog);
        break;
      case QUICK_APPROVE_ACTION.key:
        action = this._allActionValues.find(o => o.key === key);
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
  }

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
  }

  _prependSlash(key) {
    return key === '/' ? key : `/${key}`;
  }

  /**
   * _hasKnownChainState set to true true if hasParent is defined (can be
   * either true or false). set to false otherwise.
   */
  _computeChainState(hasParent) {
    this._hasKnownChainState = true;
  }

  _calculateDisabled(action, hasKnownChainState) {
    if (action.__key === 'rebase') {
      // Rebase button is only disabled when change has no parent(s).
      return hasKnownChainState === false;
    }
    return !action.enabled;
  }

  _handleConfirmDialogCancel() {
    this._hideAllDialogs();
  }

  _hideAllDialogs() {
    const dialogEls =
        dom(this.root).querySelectorAll('.confirmDialog');
    for (const dialogEl of dialogEls) { dialogEl.hidden = true; }
    this.$.overlay.close();
  }

  _handleRebaseConfirm(e) {
    const el = this.$.confirmRebase;
    const payload = {base: e.detail.base};
    this.$.overlay.close();
    el.hidden = true;
    this._fireAction('/rebase', this.revisionActions.rebase, true, payload);
  }

  _handleCherrypickConfirm() {
    this._handleCherryPickRestApi(false);
  }

  _handleCherrypickConflictConfirm() {
    this._handleCherryPickRestApi(true);
  }

  _handleCherryPickRestApi(conflicts) {
    const el = this.$.confirmCherrypick;
    if (!el.branch) {
      this.dispatchEvent(new CustomEvent('show-alert', {
        detail: {message: ERR_BRANCH_EMPTY},
        composed: true, bubbles: true,
      }));
      return;
    }
    if (!el.message) {
      this.dispatchEvent(new CustomEvent('show-alert', {
        detail: {message: ERR_COMMIT_EMPTY},
        composed: true, bubbles: true,
      }));
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
          base: el.baseCommit ? el.baseCommit : null,
          message: el.message,
          allow_conflicts: conflicts,
        }
    );
  }

  _handleMoveConfirm() {
    const el = this.$.confirmMove;
    if (!el.branch) {
      this.dispatchEvent(new CustomEvent('show-alert', {
        detail: {message: ERR_BRANCH_EMPTY},
        composed: true, bubbles: true,
      }));
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
  }

  _handleRevertDialogConfirm(e) {
    const revertType = e.detail.revertType;
    const message = e.detail.message;
    const el = this.$.confirmRevertDialog;
    this.$.overlay.close();
    el.hidden = true;
    switch (revertType) {
      case REVERT_TYPES.REVERT_SINGLE_CHANGE:
        this._fireAction('/revert', this.actions.revert, false,
            {message});
        break;
      case REVERT_TYPES.REVERT_SUBMISSION:
        this._fireAction('/revert_submission', this.actions.revert_submission,
            false, {message});
        break;
      default:
        console.error('invalid revert type');
    }
  }

  _handleRevertSubmissionDialogConfirm() {
    const el = this.$.confirmRevertSubmissionDialog;
    this.$.overlay.close();
    el.hidden = true;
    this._fireAction('/revert_submission', this.actions.revert_submission,
        false, {message: el.message});
  }

  _handleAbandonDialogConfirm() {
    const el = this.$.confirmAbandonDialog;
    this.$.overlay.close();
    el.hidden = true;
    this._fireAction('/abandon', this.actions.abandon, false,
        {message: el.message});
  }

  _handleCreateFollowUpChange() {
    this.$.createFollowUpChange.handleCreateChange();
    this._handleCloseCreateFollowUpChange();
  }

  _handleCloseCreateFollowUpChange() {
    this.$.overlay.close();
  }

  _handleDeleteConfirm() {
    this._fireAction('/', this.actions[ChangeActions.DELETE], false);
  }

  _handleDeleteEditConfirm() {
    this._hideAllDialogs();

    this._fireAction('/edit', this.actions.deleteEdit, false);
  }

  _handleSubmitConfirm() {
    if (!this._canSubmitChange()) { return; }
    this._hideAllDialogs();
    this._fireAction('/submit', this.revisionActions.submit, true);
  }

  _getActionOverflowIndex(type, key) {
    return this._overflowActions
        .findIndex(action => action.type === type && action.key === key);
  }

  _setLoadingOnButtonWithKey(type, key) {
    this._actionLoadingMessage = this._computeLoadingLabel(key);
    let buttonKey = key;
    // TODO(dhruvsri): clean this up later
    // If key is revert-submission, then button key should be 'revert'
    if (buttonKey === ChangeActions.REVERT_SUBMISSION) {
      // Revert submission button no longer exists
      buttonKey = ChangeActions.REVERT;
    }

    // If the action appears in the overflow menu.
    if (this._getActionOverflowIndex(type, buttonKey) !== -1) {
      this.push('_disabledMenuActions', buttonKey === '/' ? 'delete' :
        buttonKey);
      return function() {
        this._actionLoadingMessage = '';
        this._disabledMenuActions = [];
      }.bind(this);
    }

    // Otherwise it's a top-level action.
    const buttonEl = this.shadowRoot
        .querySelector(`[data-action-key="${buttonKey}"]`);
    buttonEl.setAttribute('loading', true);
    buttonEl.disabled = true;
    return function() {
      this._actionLoadingMessage = '';
      buttonEl.removeAttribute('loading');
      buttonEl.disabled = false;
    }.bind(this);
  }

  /**
   * @param {string} endpoint
   * @param {!Object|undefined} action
   * @param {boolean} revAction
   * @param {!Object|string=} opt_payload
   */
  _fireAction(endpoint, action, revAction, opt_payload) {
    const cleanupFn =
        this._setLoadingOnButtonWithKey(action.__type, action.__key);

    this._send(action.method, opt_payload, endpoint, revAction, cleanupFn,
        action).then(this._handleResponse.bind(this, action));
  }

  _showActionDialog(dialog) {
    this._hideAllDialogs();

    dialog.hidden = false;
    this.$.overlay.open().then(() => {
      if (dialog.resetFocus) {
        dialog.resetFocus();
      }
    });
  }

  // TODO(rmistry): Redo this after
  // https://bugs.chromium.org/p/gerrit/issues/detail?id=4671 is resolved.
  _setLabelValuesOnRevert(newChangeId) {
    const labels = this.$.jsAPI.getLabelValuesPostRevert(this.change);
    if (!labels) { return Promise.resolve(); }
    return this.$.restAPI.saveChangeReview(newChangeId, 'current', {labels});
  }

  _handleResponse(action, response) {
    if (!response) { return; }
    return this.$.restAPI.getResponseObject(response).then(obj => {
      switch (action.__key) {
        case ChangeActions.REVERT:
          this._waitForChangeReachable(obj._number)
              .then(() => this._setLabelValuesOnRevert(obj._number))
              .then(() => {
                GerritNav.navigateToChange(obj);
              });
          break;
        case RevisionActions.CHERRYPICK:
          this._waitForChangeReachable(obj._number).then(() => {
            GerritNav.navigateToChange(obj);
          });
          break;
        case ChangeActions.DELETE:
          if (action.__type === ActionType.CHANGE) {
            GerritNav.navigateToRelativeUrl(GerritNav.getUrlForRoot());
          }
          break;
        case ChangeActions.WIP:
        case ChangeActions.DELETE_EDIT:
        case ChangeActions.PUBLISH_EDIT:
        case ChangeActions.REBASE_EDIT:
        case ChangeActions.REBASE:
        case ChangeActions.SUBMIT:
          GerritNav.navigateToChange(this.change);
          break;
        case ChangeActions.REVERT_SUBMISSION:
          if (!obj.revert_changes || !obj.revert_changes.length) return;
          /* If there is only 1 change then gerrit will automatically
             redirect to that change */
          GerritNav.navigateToSearchQuery('topic: ' +
              obj.revert_changes[0].topic);
          break;
        default:
          this.dispatchEvent(new CustomEvent('reload-change',
              {detail: {action: action.__key}, bubbles: false}));
          break;
      }
    });
  }

  _handleShowRevertSubmissionChangesConfirm() {
    this._hideAllDialogs();
  }

  _handleResponseError(action, response, body) {
    if (action && action.__key === RevisionActions.CHERRYPICK) {
      if (response && response.status === 409 &&
          body && !body.allow_conflicts) {
        return this._showActionDialog(
            this.$.confirmCherrypickConflict);
      }
    }
    return response.text().then(errText => {
      this.dispatchEvent(new CustomEvent('show-error', {
        detail: {message: `Could not perform action: ${errText}`},
        composed: true, bubbles: true,
      }));
      if (!errText.startsWith('Change is already up to date')) {
        throw Error(errText);
      }
    });
  }

  /**
   * @param {string} method
   * @param {string|!Object|undefined} payload
   * @param {string} actionEndpoint
   * @param {boolean} revisionAction
   * @param {?Function} cleanupFn
   * @param {!Object|undefined} action
   */
  _send(method, payload, actionEndpoint, revisionAction, cleanupFn, action) {
    const handleError = response => {
      cleanupFn.call(this);
      this._handleResponseError(action, response, payload);
    };
    return fetchChangeUpdates(this.change, this.$.restAPI)
        .then(result => {
          if (!result.isLatest) {
            this.dispatchEvent(new CustomEvent('show-alert', {
              detail: {
                message: 'Cannot set label: a newer patch has been ' +
                  'uploaded to this change.',
                action: 'Reload',
                callback: () => {
                // Load the current change without any patch range.
                  GerritNav.navigateToChange(this.change);
                },
              },
              composed: true, bubbles: true,
            }));

            // Because this is not a network error, call the cleanup function
            // but not the error handler.
            cleanupFn();

            return Promise.resolve();
          }
          const patchNum = revisionAction ? this.latestPatchNum : null;
          return this.$.restAPI.executeChangeAction(this.changeNum, method,
              actionEndpoint, patchNum, payload, handleError)
              .then(response => {
                cleanupFn.call(this);
                return response;
              });
        });
  }

  _handleAbandonTap() {
    this._showActionDialog(this.$.confirmAbandonDialog);
  }

  _handleCherrypickTap() {
    this.$.confirmCherrypick.branch = '';
    const query = `topic: "${this.change.topic}"`;
    const options =
      listChangesOptionsToHex(ListChangesOption.MESSAGES,
          ListChangesOption.ALL_REVISIONS);
    this.$.restAPI.getChanges('', query, undefined, options)
        .then(changes => {
          this.$.confirmCherrypick.updateChanges(changes);
          this._showActionDialog(this.$.confirmCherrypick);
        });
  }

  _handleMoveTap() {
    this.$.confirmMove.branch = '';
    this.$.confirmMove.message = '';
    this._showActionDialog(this.$.confirmMove);
  }

  _handleDownloadTap() {
    this.dispatchEvent(new CustomEvent('download-tap', {
      composed: true, bubbles: true,
    }));
  }

  _handleDeleteTap() {
    this._showActionDialog(this.$.confirmDeleteDialog);
  }

  _handleDeleteEditTap() {
    this._showActionDialog(this.$.confirmDeleteEditDialog);
  }

  _handleFollowUpTap() {
    this._showActionDialog(this.$.createFollowUpDialog);
  }

  _handleWipTap() {
    this._fireAction('/wip', this.actions.wip, false);
  }

  _handlePublishEditTap() {
    this._fireAction('/edit:publish', this.actions.publishEdit, false);
  }

  _handleRebaseEditTap() {
    this._fireAction('/edit:rebase', this.actions.rebaseEdit, false);
  }

  _handleHideBackgroundContent() {
    this.$.mainContent.classList.add('overlayOpen');
  }

  _handleShowBackgroundContent() {
    this.$.mainContent.classList.remove('overlayOpen');
  }

  /**
   * Merge sources of change actions into a single ordered array of action
   * values.
   *
   * @param {!Array} changeActionsRecord
   * @param {!Array} revisionActionsRecord
   * @param {!Array} primariesRecord
   * @param {!Array} additionalActionsRecord
   * @param {!Object} change The change object.
   * @param {!Object} config server configuration info
   * @return {!Array}
   */
  _computeAllActions(changeActionsRecord, revisionActionsRecord,
      primariesRecord, additionalActionsRecord, change, config) {
    // Polymer 2: check for undefined
    if ([
      changeActionsRecord,
      revisionActionsRecord,
      primariesRecord,
      additionalActionsRecord,
      change,
    ].includes(undefined)) {
      return [];
    }

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
        .sort(this._actionComparator.bind(this))
        .map(action => {
          if (ACTIONS_WITH_ICONS.has(action.__key)) {
            action.icon = action.__key;
          }
          // TODO(brohlfs): Temporary hack until change 269573 is live in all
          // backends.
          if (action.__key === ChangeActions.READY) {
            action.label = 'Mark as Active';
          }
          // End of hack
          return action;
        })
        .filter(action => !this._shouldSkipAction(action, config));
  }

  _getActionPriority(action) {
    if (action.__type && action.__key) {
      const overrideAction = this._actionPriorityOverrides
          .find(i => i.type === action.__type && i.key === action.__key);

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
  }

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
  }

  _shouldSkipAction(action, config) {
    const skipActionKeys = [...SKIP_ACTION_KEYS];
    const isAttentionSetEnabled = !!config && !!config.change
        && config.change.enable_attention_set;
    if (isAttentionSetEnabled) {
      skipActionKeys.push(...SKIP_ACTION_KEYS_ATTENTION_SET);
    }
    return skipActionKeys.includes(action.__key);
  }

  _computeTopLevelActions(actionRecord, hiddenActionsRecord) {
    const hiddenActions = hiddenActionsRecord.base || [];
    return actionRecord.base.filter(a => {
      const overflow = this._getActionOverflowIndex(a.__type, a.__key) !== -1;
      return !(overflow || hiddenActions.includes(a.__key));
    });
  }

  _filterPrimaryActions(_topLevelActions) {
    this._topLevelPrimaryActions = _topLevelActions.filter(action =>
      action.__primary);
    this._topLevelSecondaryActions = _topLevelActions.filter(action =>
      !action.__primary);
  }

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
  }

  _computeRebaseOnCurrent(revisionRebaseAction) {
    if (revisionRebaseAction) {
      return !!revisionRebaseAction.enabled;
    }
    return null;
  }

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
  }

  _handleEditTap() {
    this.dispatchEvent(new CustomEvent('edit-tap', {bubbles: false}));
  }

  _handleStopEditTap() {
    this.dispatchEvent(new CustomEvent('stop-edit-tap', {bubbles: false}));
  }

  _computeHasTooltip(title) {
    return !!title;
  }

  _computeHasIcon(action) {
    return action.icon ? '' : 'hidden';
  }
}

customElements.define(GrChangeActions.is, GrChangeActions);
