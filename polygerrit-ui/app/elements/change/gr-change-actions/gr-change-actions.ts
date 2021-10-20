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
import '../../admin/gr-create-change-dialog/gr-create-change-dialog';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-dropdown/gr-dropdown';
import '../../shared/gr-icons/gr-icons';
import '../../shared/gr-overlay/gr-overlay';
import '../gr-confirm-abandon-dialog/gr-confirm-abandon-dialog';
import '../gr-confirm-cherrypick-dialog/gr-confirm-cherrypick-dialog';
import '../gr-confirm-cherrypick-conflict-dialog/gr-confirm-cherrypick-conflict-dialog';
import '../gr-confirm-move-dialog/gr-confirm-move-dialog';
import '../gr-confirm-rebase-dialog/gr-confirm-rebase-dialog';
import '../gr-confirm-revert-dialog/gr-confirm-revert-dialog';
import '../gr-confirm-submit-dialog/gr-confirm-submit-dialog';
import '../../../styles/shared-styles';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-change-actions_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {appContext} from '../../../services/app-context';
import {CURRENT} from '../../../utils/patch-set-util';
import {
  changeIsOpen,
  isOwner,
  ListChangesOption,
  listChangesOptionsToHex,
} from '../../../utils/change-util';
import {
  ChangeStatus,
  DraftsAction,
  HttpMethod,
  NotifyType,
} from '../../../constants/constants';
import {EventType as PluginEventType, TargetElement} from '../../../api/plugin';
import {customElement, observe, property} from '@polymer/decorators';
import {
  AccountInfo,
  ActionInfo,
  ActionNameToActionInfoMap,
  BranchName,
  ChangeInfo,
  ChangeViewChangeInfo,
  CherryPickInput,
  CommitId,
  InheritedBooleanInfo,
  isDetailedLabelInfo,
  isQuickLabelInfo,
  LabelInfo,
  NumericChangeId,
  PatchSetNum,
  PropertyType,
  RequestPayload,
  RevertSubmissionInfo,
  ReviewInput,
  ServerInfo,
} from '../../../types/common';
import {GrConfirmAbandonDialog} from '../gr-confirm-abandon-dialog/gr-confirm-abandon-dialog';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {GrCreateChangeDialog} from '../../admin/gr-create-change-dialog/gr-create-change-dialog';
import {GrConfirmSubmitDialog} from '../gr-confirm-submit-dialog/gr-confirm-submit-dialog';
import {
  ConfirmRevertEventDetail,
  GrConfirmRevertDialog,
  RevertType,
} from '../gr-confirm-revert-dialog/gr-confirm-revert-dialog';
import {GrConfirmMoveDialog} from '../gr-confirm-move-dialog/gr-confirm-move-dialog';
import {GrConfirmCherrypickDialog} from '../gr-confirm-cherrypick-dialog/gr-confirm-cherrypick-dialog';
import {GrConfirmCherrypickConflictDialog} from '../gr-confirm-cherrypick-conflict-dialog/gr-confirm-cherrypick-conflict-dialog';
import {
  ConfirmRebaseEventDetail,
  GrConfirmRebaseDialog,
} from '../gr-confirm-rebase-dialog/gr-confirm-rebase-dialog';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {GrButton} from '../../shared/gr-button/gr-button';
import {
  GrChangeActionsElement,
  UIActionInfo,
} from '../../shared/gr-js-api-interface/gr-change-actions-js-api';
import {fireAlert, fireEvent, fireReload} from '../../../utils/event-util';
import {
  getApprovalInfo,
  getVotingRange,
  StandardLabels,
} from '../../../utils/label-util';
import {CommentThread} from '../../../utils/comment-util';
import {ShowAlertEventDetail} from '../../../types/events';
import {
  ActionPriority,
  ActionType,
  ChangeActions,
  PrimaryActionKey,
  RevisionActions,
} from '../../../api/change-actions';
import {ErrorCallback} from '../../../api/rest';
import {GrDropdown} from '../../shared/gr-dropdown/gr-dropdown';

const ERR_BRANCH_EMPTY = 'The destination branch can’t be empty.';
const ERR_COMMIT_EMPTY = 'The commit message can’t be empty.';
const ERR_REVISION_ACTIONS = 'Couldn’t load revision actions.';

enum LabelStatus {
  /**
   * This label provides what is necessary for submission.
   */
  OK = 'OK',
  /**
   * This label prevents the change from being submitted.
   */
  REJECT = 'REJECT',
  /**
   * The label may be set, but it's neither necessary for submission
   * nor does it block submission if set.
   */
  MAY = 'MAY',
  /**
   * The label is required for submission, but has not been satisfied.
   */
  NEED = 'NEED',
  /**
   * The label is required for submission, but is impossible to complete.
   * The likely cause is access has not been granted correctly by the
   * project owner or site administrator.
   */
  IMPOSSIBLE = 'IMPOSSIBLE',
  OPTIONAL = 'OPTIONAL',
}

const ActionLoadingLabels: {[actionKey: string]: string} = {
  abandon: 'Abandoning...',
  cherrypick: 'Cherry-picking...',
  delete: 'Deleting...',
  move: 'Moving..',
  rebase: 'Rebasing...',
  restore: 'Restoring...',
  revert: 'Reverting...',
  submit: 'Submitting...',
};

const ADDITIONAL_ACTION_KEY_PREFIX = '__additionalAction_';

interface QuickApproveUIActionInfo extends UIActionInfo {
  key: string;
  payload?: RequestPayload;
}

const QUICK_APPROVE_ACTION: QuickApproveUIActionInfo = {
  __key: 'review',
  __type: ActionType.CHANGE,
  enabled: true,
  key: 'review',
  label: 'Quick approve',
  method: HttpMethod.POST,
};

function isQuickApproveAction(
  action: UIActionInfo
): action is QuickApproveUIActionInfo {
  return (action as QuickApproveUIActionInfo).key === QUICK_APPROVE_ACTION.key;
}

const DOWNLOAD_ACTION: UIActionInfo = {
  enabled: true,
  label: 'Download patch',
  title: 'Open download dialog',
  __key: 'download',
  __primary: false,
  __type: ActionType.REVISION,
};

const INCLUDED_IN_ACTION: UIActionInfo = {
  enabled: true,
  label: 'Included In',
  title: 'Open Included In dialog',
  __key: 'includedIn',
  __primary: false,
  __type: ActionType.CHANGE,
};

const REBASE_EDIT: UIActionInfo = {
  enabled: true,
  label: 'Rebase edit',
  title: 'Rebase change edit',
  __key: 'rebaseEdit',
  __primary: false,
  __type: ActionType.CHANGE,
  method: HttpMethod.POST,
};

const PUBLISH_EDIT: UIActionInfo = {
  enabled: true,
  label: 'Publish edit',
  title: 'Publish change edit',
  __key: 'publishEdit',
  __primary: false,
  __type: ActionType.CHANGE,
  method: HttpMethod.POST,
};

const DELETE_EDIT: UIActionInfo = {
  enabled: true,
  label: 'Delete edit',
  title: 'Delete change edit',
  __key: 'deleteEdit',
  __primary: false,
  __type: ActionType.CHANGE,
  method: HttpMethod.DELETE,
};

const EDIT: UIActionInfo = {
  enabled: true,
  label: 'Edit',
  title: 'Edit this change',
  __key: 'edit',
  __primary: false,
  __type: ActionType.CHANGE,
};

const STOP_EDIT: UIActionInfo = {
  enabled: true,
  label: 'Stop editing',
  title: 'Stop editing this change',
  __key: 'stopEdit',
  __primary: false,
  __type: ActionType.CHANGE,
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
  ChangeActions.STOP_EDIT,
  QUICK_APPROVE_ACTION.key,
  RevisionActions.REBASE,
  RevisionActions.SUBMIT,
]);

const EDIT_ACTIONS: Set<string> = new Set([
  ChangeActions.DELETE_EDIT,
  ChangeActions.EDIT,
  ChangeActions.PUBLISH_EDIT,
  ChangeActions.REBASE_EDIT,
  ChangeActions.STOP_EDIT,
]);

const AWAIT_CHANGE_ATTEMPTS = 5;
const AWAIT_CHANGE_TIMEOUT_MS = 1000;

const SKIP_ACTION_KEYS: string[] = [
  // REVIEWED/UNREVIEWED is made obsolete by AttentionSet. Once the
  // backend stops supporting (UN)REVIEWED, we can remove these.
  ChangeActions.REVIEWED,
  ChangeActions.UNREVIEWED,
  // REVERT_SUBMISSION is folded into the dialog for REVERT.
  ChangeActions.REVERT_SUBMISSION,
];

export function assertUIActionInfo(action?: ActionInfo): UIActionInfo {
  // TODO(TS): Remove this function. The gr-change-actions adds properties
  // to existing ActionInfo objects instead of creating a new objects. This
  // function checks, that 'action' has all property required by UIActionInfo.
  // In the future, we should avoid updates of an existing ActionInfos and
  // instead create a new object to make code cleaner. However, at the current
  // state this is unsafe, because other code can expect these properties to be
  // set in ActionInfo.
  if (!action) {
    throw new Error('action is undefined');
  }
  const result = action as UIActionInfo;
  if (result.__key === undefined || result.__type === undefined) {
    throw new Error('action is not an UIActionInfo');
  }
  return result;
}

interface MenuAction {
  name: string;
  id: string;
  action: UIActionInfo;
  tooltip?: string;
}

interface OverflowAction {
  type: ActionType;
  key: string;
  overflow?: boolean;
}

interface ActionPriorityOverride {
  type: ActionType.CHANGE | ActionType.REVISION;
  key: string;
  priority: ActionPriority;
}

interface ChangeActionDialog extends HTMLElement {
  resetFocus?(): void;
  init?(): void;
}

export interface GrChangeActions {
  $: {
    mainContent: Element;
    overlay: GrOverlay;
    confirmRebase: GrConfirmRebaseDialog;
    confirmCherrypick: GrConfirmCherrypickDialog;
    confirmCherrypickConflict: GrConfirmCherrypickConflictDialog;
    confirmMove: GrConfirmMoveDialog;
    confirmRevertDialog: GrConfirmRevertDialog;
    confirmAbandonDialog: GrConfirmAbandonDialog;
    confirmSubmitDialog: GrConfirmSubmitDialog;
    createFollowUpDialog: GrDialog;
    createFollowUpChange: GrCreateChangeDialog;
    confirmDeleteDialog: GrDialog;
    confirmDeleteEditDialog: GrDialog;
    moreActions: GrDropdown;
    secondaryActions: HTMLElement;
  };
}

@customElement('gr-change-actions')
export class GrChangeActions
  extends PolymerElement
  implements GrChangeActionsElement
{
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the change should be reloaded.
   *
   * @event reload
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

  // TODO(TS): Ensure that ActionType, ChangeActions and RevisionActions
  // properties are replaced with enums everywhere and remove them from
  // the GrChangeActions class
  ActionType = ActionType;

  ChangeActions = ChangeActions;

  RevisionActions = RevisionActions;

  private readonly reporting = appContext.reportingService;

  private readonly jsAPI = appContext.jsApiService;

  private readonly changeService = appContext.changeService;

  @property({type: Object})
  change?: ChangeViewChangeInfo;

  @property({type: Object})
  actions: ActionNameToActionInfoMap = {};

  @property({type: Array})
  primaryActionKeys: PrimaryActionKey[] = [
    ChangeActions.READY,
    RevisionActions.SUBMIT,
  ];

  @property({type: Boolean})
  disableEdit = false;

  @property({type: Boolean})
  _hasKnownChainState = false;

  @property({type: Boolean})
  _hideQuickApproveAction = false;

  @property({type: Object})
  account?: AccountInfo;

  @property({type: String})
  changeNum?: NumericChangeId;

  @property({type: String})
  changeStatus?: ChangeStatus;

  @property({type: String})
  commitNum?: CommitId;

  @property({type: Boolean, observer: '_computeChainState'})
  hasParent?: boolean;

  @property({type: String})
  latestPatchNum?: PatchSetNum;

  @property({type: String})
  commitMessage = '';

  @property({type: Object, notify: true})
  revisionActions: ActionNameToActionInfoMap = {};

  @property({type: Object, computed: '_getSubmitAction(revisionActions)'})
  _revisionSubmitAction?: ActionInfo | null;

  @property({type: Object, computed: '_getRebaseAction(revisionActions)'})
  _revisionRebaseAction?: ActionInfo | null;

  @property({type: String})
  privateByDefault?: InheritedBooleanInfo;

  @property({type: Boolean})
  _loading = true;

  @property({type: String})
  _actionLoadingMessage = '';

  @property({type: Array})
  commentThreads: CommentThread[] = [];

  @property({
    type: Array,
    computed:
      '_computeAllActions(actions.*, revisionActions.*,' +
      'primaryActionKeys.*, _additionalActions.*, change, ' +
      '_actionPriorityOverrides.*)',
  })
  _allActionValues: UIActionInfo[] = []; // _computeAllActions always returns an array

  @property({
    type: Array,
    computed:
      '_computeTopLevelActions(_allActionValues.*, ' +
      '_hiddenActions.*, editMode, _overflowActions.*)',
    observer: '_filterPrimaryActions',
  })
  _topLevelActions?: UIActionInfo[];

  @property({type: Array})
  _topLevelPrimaryActions?: UIActionInfo[];

  @property({type: Array})
  _topLevelSecondaryActions?: UIActionInfo[];

  @property({
    type: Array,
    computed:
      '_computeMenuActions(_allActionValues.*, ' +
      '_hiddenActions.*, _overflowActions.*)',
  })
  _menuActions?: MenuAction[];

  @property({type: Array})
  _overflowActions: OverflowAction[] = [
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
    {
      type: ActionType.CHANGE,
      key: ChangeActions.INCLUDED_IN,
    },
  ];

  @property({type: Array})
  _actionPriorityOverrides: ActionPriorityOverride[] = [];

  @property({type: Array})
  _additionalActions: UIActionInfo[] = [];

  @property({type: Array})
  _hiddenActions: string[] = [];

  @property({type: Array})
  _disabledMenuActions: string[] = [];

  @property({type: Boolean})
  editPatchsetLoaded = false;

  @property({type: Boolean})
  editMode = false;

  @property({type: Boolean})
  editBasedOnCurrentPatchSet = true;

  @property({type: Object})
  _config?: ServerInfo;

  private readonly restApiService = appContext.restApiService;

  constructor() {
    super();
    this.addEventListener('fullscreen-overlay-opened', () =>
      this._handleHideBackgroundContent()
    );
    this.addEventListener('fullscreen-overlay-closed', () =>
      this._handleShowBackgroundContent()
    );
  }

  override ready() {
    super.ready();
    this.jsAPI.addElement(TargetElement.CHANGE_ACTIONS, this);
    this.restApiService.getConfig().then(config => {
      this._config = config;
    });
    this._handleLoadingComplete();
  }

  _getSubmitAction(revisionActions: ActionNameToActionInfoMap) {
    return this._getRevisionAction(revisionActions, 'submit');
  }

  _getRebaseAction(revisionActions: ActionNameToActionInfoMap) {
    return this._getRevisionAction(revisionActions, 'rebase');
  }

  _getRevisionAction(
    revisionActions: ActionNameToActionInfoMap,
    actionName: string
  ) {
    if (!revisionActions) {
      return undefined;
    }
    if (revisionActions[actionName] === undefined) {
      // Return null to fire an event when reveisionActions was loaded
      // but doesn't contain actionName. undefined doesn't fire an event
      return null;
    }
    return revisionActions[actionName];
  }

  reload() {
    if (!this.changeNum || !this.latestPatchNum || !this.change) {
      return Promise.resolve();
    }
    const change = this.change;

    this._loading = true;
    return this.restApiService
      .getChangeRevisionActions(this.changeNum, this.latestPatchNum)
      .then(revisionActions => {
        if (!revisionActions) {
          return;
        }

        this.revisionActions = revisionActions;
        this._sendShowRevisionActions({
          change,
          revisionActions,
        });
        this._handleLoadingComplete();
      })
      .catch(err => {
        fireAlert(this, ERR_REVISION_ACTIONS);
        this._loading = false;
        throw err;
      });
  }

  _handleLoadingComplete() {
    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => (this._loading = false));
  }

  _sendShowRevisionActions(detail: {
    change: ChangeInfo;
    revisionActions: ActionNameToActionInfoMap;
  }) {
    this.jsAPI.handleEvent(PluginEventType.SHOW_REVISION_ACTIONS, detail);
  }

  @observe('change')
  _changeChanged() {
    this.reload();
  }

  addActionButton(type: ActionType, label: string) {
    if (type !== ActionType.CHANGE && type !== ActionType.REVISION) {
      throw Error(`Invalid action type: ${type}`);
    }
    const action: UIActionInfo = {
      enabled: true,
      label,
      __type: type,
      __key:
        ADDITIONAL_ACTION_KEY_PREFIX + Math.random().toString(36).substr(2),
    };
    this.push('_additionalActions', action);
    return action.__key;
  }

  removeActionButton(key: string) {
    const idx = this._indexOfActionButtonWithKey(key);
    if (idx === -1) {
      return;
    }
    this.splice('_additionalActions', idx, 1);
  }

  setActionButtonProp<T extends keyof UIActionInfo>(
    key: string,
    prop: T,
    value: UIActionInfo[T]
  ) {
    this.set(
      ['_additionalActions', this._indexOfActionButtonWithKey(key), prop],
      value
    );
  }

  setActionOverflow(type: ActionType, key: string, overflow: boolean) {
    if (type !== ActionType.CHANGE && type !== ActionType.REVISION) {
      throw Error(`Invalid action type given: ${type}`);
    }
    const index = this._getActionOverflowIndex(type, key);
    const action: OverflowAction = {
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

  setActionPriority(
    type: ActionType.CHANGE | ActionType.REVISION,
    key: string,
    priority: ActionPriority
  ) {
    if (type !== ActionType.CHANGE && type !== ActionType.REVISION) {
      throw Error(`Invalid action type given: ${type}`);
    }
    const index = this._actionPriorityOverrides.findIndex(
      action => action.type === type && action.key === key
    );
    const action: ActionPriorityOverride = {
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

  setActionHidden(
    type: ActionType.CHANGE | ActionType.REVISION,
    key: string,
    hidden: boolean
  ) {
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

  getActionDetails(actionName: string) {
    if (this.revisionActions[actionName]) {
      return this.revisionActions[actionName];
    } else if (this.actions[actionName]) {
      return this.actions[actionName];
    } else {
      return undefined;
    }
  }

  _indexOfActionButtonWithKey(key: string) {
    for (let i = 0; i < this._additionalActions.length; i++) {
      if (this._additionalActions[i].__key === key) {
        return i;
      }
    }
    return -1;
  }

  _shouldHideActions(
    actions?: PolymerDeepPropertyChange<UIActionInfo[], UIActionInfo[]>,
    loading?: boolean
  ) {
    return loading || !actions || !actions.base || !actions.base.length;
  }

  _keyCount(
    changeRecord?: PolymerDeepPropertyChange<
      ActionNameToActionInfoMap,
      ActionNameToActionInfoMap
    >
  ) {
    return Object.keys(changeRecord?.base || {}).length;
  }

  @observe('actions.*', 'revisionActions.*', '_additionalActions.*')
  _actionsChanged(
    actionsChangeRecord?: PolymerDeepPropertyChange<
      ActionNameToActionInfoMap,
      ActionNameToActionInfoMap
    >,
    revisionActionsChangeRecord?: PolymerDeepPropertyChange<
      ActionNameToActionInfoMap,
      ActionNameToActionInfoMap
    >,
    additionalActionsChangeRecord?: PolymerDeepPropertyChange<
      UIActionInfo[],
      UIActionInfo[]
    >
  ) {
    // Polymer 2: check for undefined
    if (
      actionsChangeRecord === undefined ||
      revisionActionsChangeRecord === undefined ||
      additionalActionsChangeRecord === undefined
    ) {
      return;
    }

    const additionalActions =
      (additionalActionsChangeRecord && additionalActionsChangeRecord.base) ||
      [];
    this.hidden =
      this._keyCount(actionsChangeRecord) === 0 &&
      this._keyCount(revisionActionsChangeRecord) === 0 &&
      additionalActions.length === 0;
    this._actionLoadingMessage = '';
    this._actionLoadingMessage = '';
    this._disabledMenuActions = [];

    const revisionActions = revisionActionsChangeRecord.base || {};
    if (Object.keys(revisionActions).length !== 0) {
      if (!revisionActions.download) {
        this.set('revisionActions.download', DOWNLOAD_ACTION);
      }
    }
    const actions = actionsChangeRecord.base || {};
    if (!actions.includedIn && this.change?.status === ChangeStatus.MERGED) {
      this.set('actions.includedIn', INCLUDED_IN_ACTION);
    }
  }

  _deleteAndNotify(actionName: string) {
    if (this.actions && this.actions[actionName]) {
      delete this.actions[actionName];
      // We assign a fake value of 'false' to support Polymer 2
      // see https://github.com/Polymer/polymer/issues/2631
      this.notifyPath('actions.' + actionName, false);
    }
  }

  @observe(
    'editMode',
    'editPatchsetLoaded',
    'editBasedOnCurrentPatchSet',
    'disableEdit',
    'actions.*',
    'change.*'
  )
  _editStatusChanged(
    editMode: boolean,
    editPatchsetLoaded: boolean,
    editBasedOnCurrentPatchSet: boolean,
    disableEdit: boolean,
    actionsChangeRecord?: PolymerDeepPropertyChange<
      ActionNameToActionInfoMap,
      ActionNameToActionInfoMap
    >,
    changeChangeRecord?: PolymerDeepPropertyChange<ChangeInfo, ChangeInfo>
  ) {
    if (actionsChangeRecord === undefined || changeChangeRecord === undefined) {
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
    const actions = actionsChangeRecord.base;
    const change = changeChangeRecord.base;
    if (actions && editPatchsetLoaded) {
      // Only show actions that mutate an edit if an actual edit patch set
      // is loaded.
      if (changeIsOpen(change)) {
        if (editBasedOnCurrentPatchSet) {
          if (!actions.publishEdit) {
            this.set('actions.publishEdit', PUBLISH_EDIT);
          }
          this._deleteAndNotify('rebaseEdit');
        } else {
          if (!actions.rebaseEdit) {
            this.set('actions.rebaseEdit', REBASE_EDIT);
          }
          this._deleteAndNotify('publishEdit');
        }
      }
      if (!actions.deleteEdit) {
        this.set('actions.deleteEdit', DELETE_EDIT);
      }
    } else {
      this._deleteAndNotify('publishEdit');
      this._deleteAndNotify('rebaseEdit');
      this._deleteAndNotify('deleteEdit');
    }

    if (actions && changeIsOpen(change)) {
      // Only show edit button if there is no edit patchset loaded and the
      // file list is not in edit mode.
      if (editPatchsetLoaded || editMode) {
        this._deleteAndNotify('edit');
      } else {
        if (!actions.edit) {
          this.set('actions.edit', EDIT);
        }
      }
      // Only show STOP_EDIT if edit mode is enabled, but no edit patch set
      // is loaded.
      if (editMode && !editPatchsetLoaded) {
        if (!actions.stopEdit) {
          this.set('actions.stopEdit', STOP_EDIT);
          fireAlert(this, 'Change is in edit mode');
        }
      } else {
        this._deleteAndNotify('stopEdit');
      }
    } else {
      // Remove edit button.
      this._deleteAndNotify('edit');
    }
  }

  _getValuesFor<T>(obj: {[key: string]: T}): T[] {
    return Object.keys(obj).map(key => obj[key]);
  }

  _getLabelStatus(label: LabelInfo): LabelStatus {
    if (isQuickLabelInfo(label)) {
      if (label.approved) {
        return LabelStatus.OK;
      } else if (label.rejected) {
        return LabelStatus.REJECT;
      }
    }
    if (label.optional) {
      return LabelStatus.OPTIONAL;
    } else {
      return LabelStatus.NEED;
    }
  }

  /**
   * Get highest score for last missing permitted label for current change.
   * Returns null if no labels permitted or more than one label missing.
   */
  _getTopMissingApproval() {
    if (!this.change || !this.change.labels || !this.change.permitted_labels) {
      return null;
    }
    if (this.change && this.change.status === ChangeStatus.MERGED) {
      return null;
    }
    let result;
    for (const [label, labelInfo] of Object.entries(this.change.labels)) {
      if (!(label in this.change.permitted_labels)) {
        continue;
      }
      if (this.change.permitted_labels[label].length === 0) {
        continue;
      }
      const status = this._getLabelStatus(labelInfo);
      if (status === LabelStatus.NEED) {
        if (result) {
          // More than one label is missing, so check if Code Review can be
          // given
          result = null;
          break;
        }
        result = label;
      } else if (
        status === LabelStatus.REJECT ||
        status === LabelStatus.IMPOSSIBLE
      ) {
        return null;
      }
    }
    // Allow the user to use quick approve to vote the max score on code review
    // even if it is already granted by someone else. Does not apply if the
    // user owns the change or has already granted the max score themselves.
    const codeReviewLabel = this.change.labels[StandardLabels.CODE_REVIEW];
    const codeReviewPermittedValues =
      this.change.permitted_labels[StandardLabels.CODE_REVIEW];
    if (
      !result &&
      codeReviewLabel &&
      codeReviewPermittedValues &&
      this.account?._account_id &&
      isDetailedLabelInfo(codeReviewLabel) &&
      this._getLabelStatus(codeReviewLabel) === LabelStatus.OK &&
      !isOwner(this.change, this.account) &&
      getApprovalInfo(codeReviewLabel, this.account)?.value !==
        getVotingRange(codeReviewLabel)?.max
    ) {
      result = StandardLabels.CODE_REVIEW;
    }

    if (result) {
      const labelInfo = this.change.labels[result];
      if (!isDetailedLabelInfo(labelInfo)) {
        return null;
      }
      const permittedValues = this.change.permitted_labels[result];
      const usersMaxPermittedScore =
        permittedValues[permittedValues.length - 1];
      const maxScoreForLabel = getVotingRange(labelInfo)?.max;
      if (Number(usersMaxPermittedScore) === maxScoreForLabel) {
        // Allow quick approve only for maximal score.
        return {
          label: result,
          score: usersMaxPermittedScore,
        };
      }
    }
    return null;
  }

  hideQuickApproveAction() {
    if (!this._topLevelSecondaryActions) {
      throw new Error('_topLevelSecondaryActions must be set');
    }
    this._topLevelSecondaryActions = this._topLevelSecondaryActions.filter(
      sa => !isQuickApproveAction(sa)
    );
    this._hideQuickApproveAction = true;
  }

  _getQuickApproveAction(): QuickApproveUIActionInfo | null {
    if (this._hideQuickApproveAction) {
      return null;
    }
    const approval = this._getTopMissingApproval();
    if (!approval) {
      return null;
    }
    const action = {...QUICK_APPROVE_ACTION};
    action.label = approval.label + approval.score;

    const score = Number(approval.score);
    if (isNaN(score)) {
      return null;
    }

    const review: ReviewInput = {
      drafts: DraftsAction.PUBLISH_ALL_REVISIONS,
      labels: {
        [approval.label]: score,
      },
    };
    action.payload = review;
    return action;
  }

  _getActionValues(
    actionsChangeRecord: PolymerDeepPropertyChange<
      ActionNameToActionInfoMap,
      ActionNameToActionInfoMap
    >,
    primariesChangeRecord: PolymerDeepPropertyChange<
      PrimaryActionKey[],
      PrimaryActionKey[]
    >,
    additionalActionsChangeRecord: PolymerDeepPropertyChange<
      UIActionInfo[],
      UIActionInfo[]
    >,
    type: ActionType
  ): UIActionInfo[] {
    if (!actionsChangeRecord || !primariesChangeRecord) {
      return [];
    }

    const actions = actionsChangeRecord.base || {};
    const primaryActionKeys = primariesChangeRecord.base || [];
    const result: UIActionInfo[] = [];
    const values: Array<ChangeActions | RevisionActions> =
      type === ActionType.CHANGE
        ? this._getValuesFor(ChangeActions)
        : this._getValuesFor(RevisionActions);

    const pluginActions: UIActionInfo[] = [];
    Object.keys(actions).forEach(a => {
      const action: UIActionInfo = actions[a] as UIActionInfo;
      action.__key = a;
      action.__type = type;
      action.__primary = primaryActionKeys.includes(a as PrimaryActionKey);
      // Plugin actions always contain ~ in the key.
      if (a.indexOf('~') !== -1) {
        this._populateActionUrl(action);
        pluginActions.push(action);
        // Add server-side provided plugin actions to overflow menu.
        this._overflowActions.push({
          type,
          key: a,
        });
        return;
      } else if (!values.includes(a as PrimaryActionKey)) {
        return;
      }
      action.label = this._getActionLabel(action);

      // Triggers a re-render by ensuring object inequality.
      result.push({...action});
    });

    let additionalActions =
      (additionalActionsChangeRecord && additionalActionsChangeRecord.base) ||
      [];
    additionalActions = additionalActions
      .filter(a => a.__type === type)
      .map(a => {
        a.__primary = primaryActionKeys.includes(a.__key as PrimaryActionKey);
        // Triggers a re-render by ensuring object inequality.
        return {...a};
      });
    return result.concat(additionalActions).concat(pluginActions);
  }

  _populateActionUrl(action: UIActionInfo) {
    const patchNum =
      action.__type === ActionType.REVISION ? this.latestPatchNum : undefined;
    if (!this.changeNum) {
      return;
    }
    this.restApiService
      .getChangeActionURL(this.changeNum, patchNum, '/' + action.__key)
      .then(url => (action.__url = url));
  }

  /**
   * Given a change action, return a display label that uses the appropriate
   * casing or includes explanatory details.
   */
  _getActionLabel(action: UIActionInfo) {
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
   */
  _toSentenceCase(s: string) {
    if (!s.length) {
      return '';
    }
    return s[0].toUpperCase() + s.slice(1).toLowerCase();
  }

  _computeLoadingLabel(action: string) {
    return ActionLoadingLabels[action] || 'Working...';
  }

  _canSubmitChange() {
    if (!this.change) {
      return false;
    }
    return this.jsAPI.canSubmitChange(
      this.change,
      this._getRevision(this.change, this.latestPatchNum)
    );
  }

  _getRevision(change: ChangeViewChangeInfo, patchNum?: PatchSetNum) {
    for (const rev of Object.values(change.revisions)) {
      if (rev._number === patchNum) {
        return rev;
      }
    }
    return null;
  }

  showRevertDialog() {
    const change = this.change;
    if (!change) return;
    // The search is still broken if there is a " in the topic.
    const query = `submissionid: "${change.submission_id}"`;
    /* A chromium plugin expects that the modifyRevertMsg hook will only
    be called after the revert button is pressed, hence we populate the
    revert dialog after revert button is pressed. */
    this.restApiService.getChanges(0, query).then(changes => {
      if (!changes) {
        this.reporting.error(new Error('changes is undefined'));
        return;
      }
      this.$.confirmRevertDialog.populate(change, this.commitMessage, changes);
      this._showActionDialog(this.$.confirmRevertDialog);
    });
  }

  showSubmitDialog() {
    if (!this._canSubmitChange()) {
      return;
    }
    this._showActionDialog(this.$.confirmSubmitDialog);
  }

  _handleActionTap(e: MouseEvent) {
    e.preventDefault();
    let el = (dom(e) as EventApi).localTarget as Element;
    while (el.tagName.toLowerCase() !== 'gr-button') {
      if (!el.parentElement) {
        return;
      }
      el = el.parentElement;
    }

    const key = el.getAttribute('data-action-key');
    if (!key) {
      throw new Error("Button doesn't have data-action-key attribute");
    }
    if (
      key.startsWith(ADDITIONAL_ACTION_KEY_PREFIX) ||
      key.indexOf('~') !== -1
    ) {
      this.dispatchEvent(
        new CustomEvent(`${key}-tap`, {
          detail: {node: el},
          composed: true,
          bubbles: true,
        })
      );
      return;
    }
    const type = el.getAttribute('data-action-type') as ActionType;
    this._handleAction(type, key);
  }

  _handleOverflowItemTap(e: CustomEvent<MenuAction>) {
    e.preventDefault();
    const el = (dom(e) as EventApi).localTarget as Element;
    const key = e.detail.action.__key;
    if (
      key.startsWith(ADDITIONAL_ACTION_KEY_PREFIX) ||
      key.indexOf('~') !== -1
    ) {
      this.dispatchEvent(
        new CustomEvent(`${key}-tap`, {
          detail: {node: el},
          composed: true,
          bubbles: true,
        })
      );
      return;
    }
    this._handleAction(e.detail.action.__type, e.detail.action.__key);
  }

  _handleAction(type: ActionType, key: string) {
    this.reporting.reportInteraction(`${type}-${key}`);
    switch (type) {
      case ActionType.REVISION:
        this._handleRevisionAction(key);
        break;
      case ActionType.CHANGE:
        this._handleChangeAction(key);
        break;
      default:
        this._fireAction(
          this._prependSlash(key),
          assertUIActionInfo(this.actions[key]),
          false
        );
    }
  }

  _handleChangeAction(key: string) {
    switch (key) {
      case ChangeActions.REVERT:
        this.showRevertDialog();
        break;
      case ChangeActions.ABANDON:
        this._showActionDialog(this.$.confirmAbandonDialog);
        break;
      case QUICK_APPROVE_ACTION.key: {
        const action = this._allActionValues.find(isQuickApproveAction);
        if (!action) {
          return;
        }
        this._fireAction(this._prependSlash(key), action, true, action.payload);
        break;
      }
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
      case ChangeActions.INCLUDED_IN:
        this._handleIncludedInTap();
        break;
      default:
        this._fireAction(
          this._prependSlash(key),
          assertUIActionInfo(this.actions[key]),
          false
        );
    }
  }

  _handleRevisionAction(key: string) {
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
        if (!this._canSubmitChange()) {
          return;
        }
        this._showActionDialog(this.$.confirmSubmitDialog);
        break;
      default:
        this._fireAction(
          this._prependSlash(key),
          assertUIActionInfo(this.revisionActions[key]),
          true
        );
    }
  }

  _prependSlash(key: string) {
    return key === '/' ? key : `/${key}`;
  }

  /**
   * _hasKnownChainState set to true true if hasParent is defined (can be
   * either true or false). set to false otherwise.
   */
  _computeChainState() {
    this._hasKnownChainState = true;
  }

  _calculateDisabled(action: UIActionInfo, hasKnownChainState: boolean) {
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
    const dialogEls = this.root!.querySelectorAll('.confirmDialog');
    for (const dialogEl of dialogEls) {
      (dialogEl as HTMLElement).hidden = true;
    }
    this.$.overlay.close();
  }

  _handleRebaseConfirm(e: CustomEvent<ConfirmRebaseEventDetail>) {
    const el = this.$.confirmRebase;
    const payload = {base: e.detail.base};
    this.$.overlay.close();
    el.hidden = true;
    this._fireAction(
      '/rebase',
      assertUIActionInfo(this.revisionActions.rebase),
      true,
      payload
    );
  }

  _handleCherrypickConfirm() {
    this._handleCherryPickRestApi(false);
  }

  _handleCherrypickConflictConfirm() {
    this._handleCherryPickRestApi(true);
  }

  _handleCherryPickRestApi(conflicts: boolean) {
    const el = this.$.confirmCherrypick;
    if (!el.branch) {
      fireAlert(this, ERR_BRANCH_EMPTY);
      return;
    }
    if (!el.message) {
      fireAlert(this, ERR_COMMIT_EMPTY);
      return;
    }
    this.$.overlay.close();
    el.hidden = true;
    this._fireAction(
      '/cherrypick',
      assertUIActionInfo(this.revisionActions.cherrypick),
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
      fireAlert(this, ERR_BRANCH_EMPTY);
      return;
    }
    this.$.overlay.close();
    el.hidden = true;
    this._fireAction('/move', assertUIActionInfo(this.actions.move), false, {
      destination_branch: el.branch,
      message: el.message,
    });
  }

  _handleRevertDialogConfirm(e: CustomEvent<ConfirmRevertEventDetail>) {
    const revertType = e.detail.revertType;
    const message = e.detail.message;
    const el = this.$.confirmRevertDialog;
    this.$.overlay.close();
    el.hidden = true;
    switch (revertType) {
      case RevertType.REVERT_SINGLE_CHANGE:
        this._fireAction(
          '/revert',
          assertUIActionInfo(this.actions.revert),
          false,
          {message}
        );
        break;
      case RevertType.REVERT_SUBMISSION:
        // TODO(dhruvsri): replace with this.actions.revert_submission once
        // BE starts sending it again
        this._fireAction(
          '/revert_submission',
          {__key: 'revert_submission', method: HttpMethod.POST} as UIActionInfo,
          false,
          {message}
        );
        break;
      default:
        this.reporting.error(new Error('invalid revert type'));
    }
  }

  _handleAbandonDialogConfirm() {
    const el = this.$.confirmAbandonDialog;
    this.$.overlay.close();
    el.hidden = true;
    this._fireAction(
      '/abandon',
      assertUIActionInfo(this.actions.abandon),
      false,
      {
        message: el.message,
      }
    );
  }

  _handleCreateFollowUpChange() {
    this.$.createFollowUpChange.handleCreateChange();
    this._handleCloseCreateFollowUpChange();
  }

  _handleCloseCreateFollowUpChange() {
    this.$.overlay.close();
  }

  _handleDeleteConfirm() {
    this._hideAllDialogs();
    this._fireAction(
      '/',
      assertUIActionInfo(this.actions[ChangeActions.DELETE]),
      false
    );
  }

  _handleDeleteEditConfirm() {
    this._hideAllDialogs();

    this._fireAction(
      '/edit',
      assertUIActionInfo(this.actions.deleteEdit),
      false
    );
  }

  _handleSubmitConfirm() {
    if (!this._canSubmitChange()) {
      return;
    }
    this._hideAllDialogs();
    this._fireAction(
      '/submit',
      assertUIActionInfo(this.revisionActions.submit),
      true
    );
  }

  _getActionOverflowIndex(type: string, key: string) {
    return this._overflowActions.findIndex(
      action => action.type === type && action.key === key
    );
  }

  _setLoadingOnButtonWithKey(type: string, key: string) {
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
      this.push(
        '_disabledMenuActions',
        buttonKey === '/' ? 'delete' : buttonKey
      );
      return () => {
        this._actionLoadingMessage = '';
        this._disabledMenuActions = [];
      };
    }

    // Otherwise it's a top-level action.
    const buttonEl = this.shadowRoot!.querySelector(
      `[data-action-key="${buttonKey}"]`
    ) as GrButton;
    if (!buttonEl) {
      throw new Error(`Can't find button by data-action-key '${buttonKey}'`);
    }
    buttonEl.setAttribute('loading', 'true');
    buttonEl.disabled = true;
    return () => {
      this._actionLoadingMessage = '';
      buttonEl.removeAttribute('loading');
      buttonEl.disabled = false;
    };
  }

  _fireAction(
    endpoint: string,
    action: UIActionInfo,
    revAction: boolean,
    payload?: RequestPayload
  ) {
    const cleanupFn = this._setLoadingOnButtonWithKey(
      action.__type,
      action.__key
    );

    this._send(
      action.method,
      payload,
      endpoint,
      revAction,
      cleanupFn,
      action
    ).then(res => this._handleResponse(action, res));
  }

  _showActionDialog(dialog: ChangeActionDialog) {
    this._hideAllDialogs();
    if (dialog.init) dialog.init();
    dialog.hidden = false;
    this.$.overlay.open().then(() => {
      if (dialog.resetFocus) {
        dialog.resetFocus();
      }
    });
  }

  // TODO(rmistry): Redo this after
  // https://bugs.chromium.org/p/gerrit/issues/detail?id=4671 is resolved.
  _setReviewOnRevert(newChangeId: NumericChangeId) {
    const review = this.jsAPI.getReviewPostRevert(this.change);
    if (!review) {
      return Promise.resolve(undefined);
    }
    return this.restApiService.saveChangeReview(newChangeId, CURRENT, review);
  }

  _handleResponse(action: UIActionInfo, response?: Response) {
    if (!response) {
      return;
    }
    return this.restApiService.getResponseObject(response).then(obj => {
      switch (action.__key) {
        case ChangeActions.REVERT: {
          const revertChangeInfo: ChangeInfo = obj as unknown as ChangeInfo;
          this._waitForChangeReachable(revertChangeInfo._number)
            .then(() => this._setReviewOnRevert(revertChangeInfo._number))
            .then(() => {
              GerritNav.navigateToChange(revertChangeInfo);
            });
          break;
        }
        case RevisionActions.CHERRYPICK: {
          const cherrypickChangeInfo: ChangeInfo = obj as unknown as ChangeInfo;
          this._waitForChangeReachable(cherrypickChangeInfo._number).then(
            () => {
              GerritNav.navigateToChange(cherrypickChangeInfo);
            }
          );
          break;
        }
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
          fireReload(this, true);
          break;
        case ChangeActions.REVERT_SUBMISSION: {
          const revertSubmistionInfo = obj as unknown as RevertSubmissionInfo;
          if (
            !revertSubmistionInfo.revert_changes ||
            !revertSubmistionInfo.revert_changes.length
          )
            return;
          /* If there is only 1 change then gerrit will automatically
             redirect to that change */
          GerritNav.navigateToSearchQuery(
            `topic: ${revertSubmistionInfo.revert_changes[0].topic}`
          );
          break;
        }
        default:
          fireReload(this, true);
          break;
      }
    });
  }

  _handleResponseError(
    action: UIActionInfo,
    response: Response | undefined | null,
    body?: RequestPayload
  ) {
    if (!response) {
      return Promise.resolve(() => {
        this.dispatchEvent(
          new CustomEvent('show-error', {
            detail: {message: `Could not perform action '${action.__key}'`},
            composed: true,
            bubbles: true,
          })
        );
      });
    }
    if (action && action.__key === RevisionActions.CHERRYPICK) {
      if (
        response.status === 409 &&
        body &&
        !(body as CherryPickInput).allow_conflicts
      ) {
        return this._showActionDialog(this.$.confirmCherrypickConflict);
      }
    }
    return response.text().then(errText => {
      this.dispatchEvent(
        new CustomEvent('show-error', {
          detail: {message: `Could not perform action: ${errText}`},
          composed: true,
          bubbles: true,
        })
      );
      if (!errText.startsWith('Change is already up to date')) {
        throw Error(errText);
      }
    });
  }

  _send(
    method: HttpMethod | undefined,
    payload: RequestPayload | undefined,
    actionEndpoint: string,
    revisionAction: boolean,
    cleanupFn: () => void,
    action: UIActionInfo
  ): Promise<Response | undefined> {
    const handleError: ErrorCallback = response => {
      cleanupFn.call(this);
      this._handleResponseError(action, response, payload);
    };
    const change = this.change;
    const changeNum = this.changeNum;
    if (!change || !changeNum) {
      return Promise.reject(
        new Error('Properties change and changeNum must be set.')
      );
    }
    return this.changeService.fetchChangeUpdates(change).then(result => {
      if (!result.isLatest) {
        this.dispatchEvent(
          new CustomEvent<ShowAlertEventDetail>('show-alert', {
            detail: {
              message:
                'Cannot set label: a newer patch has been ' +
                'uploaded to this change.',
              action: 'Reload',
              callback: () => fireReload(this, true),
            },
            composed: true,
            bubbles: true,
          })
        );

        // Because this is not a network error, call the cleanup function
        // but not the error handler.
        cleanupFn();

        return Promise.resolve(undefined);
      }
      const patchNum = revisionAction ? this.latestPatchNum : undefined;
      return this.restApiService
        .executeChangeAction(
          changeNum,
          method,
          actionEndpoint,
          patchNum,
          payload,
          handleError
        )
        .then(response => {
          cleanupFn.call(this);
          return response;
        });
    });
  }

  _handleCherrypickTap() {
    if (!this.change) {
      throw new Error('The change property must be set');
    }
    this.$.confirmCherrypick.branch = '' as BranchName;
    const query = `topic: "${this.change.topic}"`;
    const options = listChangesOptionsToHex(
      ListChangesOption.MESSAGES,
      ListChangesOption.ALL_REVISIONS
    );
    this.restApiService
      .getChanges(0, query, undefined, options)
      .then(changes => {
        if (!changes) {
          this.reporting.error(new Error('getChanges returns undefined'));
          return;
        }
        this.$.confirmCherrypick.updateChanges(changes);
        this._showActionDialog(this.$.confirmCherrypick);
      });
  }

  _handleMoveTap() {
    this.$.confirmMove.branch = '' as BranchName;
    this.$.confirmMove.message = '';
    this._showActionDialog(this.$.confirmMove);
  }

  _handleDownloadTap() {
    fireEvent(this, 'download-tap');
  }

  _handleIncludedInTap() {
    fireEvent(this, 'included-tap');
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
    if (!this.actions.wip) {
      return;
    }
    this._fireAction('/wip', assertUIActionInfo(this.actions.wip), false);
  }

  _handlePublishEditTap() {
    if (!this.actions.publishEdit) {
      return;
    }
    this._fireAction(
      '/edit:publish',
      assertUIActionInfo(this.actions.publishEdit),
      false,
      {notify: NotifyType.NONE}
    );
  }

  _handleRebaseEditTap() {
    if (!this.actions.rebaseEdit) {
      return;
    }
    this._fireAction(
      '/edit:rebase',
      assertUIActionInfo(this.actions.rebaseEdit),
      false
    );
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
   */
  _computeAllActions(
    changeActionsRecord: PolymerDeepPropertyChange<
      ActionNameToActionInfoMap,
      ActionNameToActionInfoMap
    >,
    revisionActionsRecord: PolymerDeepPropertyChange<
      ActionNameToActionInfoMap,
      ActionNameToActionInfoMap
    >,
    primariesRecord: PolymerDeepPropertyChange<
      PrimaryActionKey[],
      PrimaryActionKey[]
    >,
    additionalActionsRecord: PolymerDeepPropertyChange<
      UIActionInfo[],
      UIActionInfo[]
    >,
    change?: ChangeInfo
  ): UIActionInfo[] {
    // Polymer 2: check for undefined
    if (
      [
        changeActionsRecord,
        revisionActionsRecord,
        primariesRecord,
        additionalActionsRecord,
        change,
      ].includes(undefined)
    ) {
      return [];
    }

    const revisionActionValues = this._getActionValues(
      revisionActionsRecord,
      primariesRecord,
      additionalActionsRecord,
      ActionType.REVISION
    );
    const changeActionValues = this._getActionValues(
      changeActionsRecord,
      primariesRecord,
      additionalActionsRecord,
      ActionType.CHANGE
    );
    const quickApprove = this._getQuickApproveAction();
    if (quickApprove) {
      changeActionValues.unshift(quickApprove);
    }

    return revisionActionValues
      .concat(changeActionValues)
      .sort((a, b) => this._actionComparator(a, b))
      .map(action => {
        if (ACTIONS_WITH_ICONS.has(action.__key)) {
          action.icon = action.__key;
        }
        return action;
      })
      .filter(action => !this._shouldSkipAction(action));
  }

  _getActionPriority(action: UIActionInfo) {
    if (action.__type && action.__key) {
      const overrideAction = this._actionPriorityOverrides.find(
        i => i.type === action.__type && i.key === action.__key
      );

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
  _actionComparator(actionA: UIActionInfo, actionB: UIActionInfo) {
    const priorityDelta =
      this._getActionPriority(actionA) - this._getActionPriority(actionB);
    // Sort by the button label if same priority.
    if (priorityDelta === 0) {
      return actionA.label > actionB.label ? 1 : -1;
    } else {
      return priorityDelta;
    }
  }

  _shouldSkipAction(action: UIActionInfo) {
    return SKIP_ACTION_KEYS.includes(action.__key);
  }

  _computeTopLevelActions(
    actionRecord: PolymerDeepPropertyChange<UIActionInfo[], UIActionInfo[]>,
    hiddenActionsRecord: PolymerDeepPropertyChange<string[], string[]>,
    editMode: boolean
  ): UIActionInfo[] {
    const hiddenActions = hiddenActionsRecord.base || [];
    return actionRecord.base.filter(a => {
      if (hiddenActions.includes(a.__key)) return false;
      if (editMode) return EDIT_ACTIONS.has(a.__key);
      return this._getActionOverflowIndex(a.__type, a.__key) === -1;
    });
  }

  _filterPrimaryActions(_topLevelActions: UIActionInfo[]) {
    this._topLevelPrimaryActions = _topLevelActions.filter(
      action => action.__primary
    );
    this._topLevelSecondaryActions = _topLevelActions.filter(
      action => !action.__primary
    );
  }

  _computeMenuActions(
    actionRecord: PolymerDeepPropertyChange<UIActionInfo[], UIActionInfo[]>,
    hiddenActionsRecord: PolymerDeepPropertyChange<string[], string[]>
  ): MenuAction[] {
    const hiddenActions = hiddenActionsRecord.base || [];
    return actionRecord.base
      .filter(a => {
        const overflow = this._getActionOverflowIndex(a.__type, a.__key) !== -1;
        return overflow && !hiddenActions.includes(a.__key);
      })
      .map(action => {
        let key = action.__key;
        if (key === '/') {
          key = 'delete';
        }
        return {
          name: action.label,
          id: `${key}-${action.__type}`,
          action,
          tooltip: action.title,
        };
      });
  }

  _computeRebaseOnCurrent(
    revisionRebaseAction: PropertyType<GrChangeActions, '_revisionRebaseAction'>
  ) {
    if (revisionRebaseAction) {
      return !!revisionRebaseAction.enabled;
    }
    return null;
  }

  /**
   * Occasionally, a change created by a change action is not yet known to the
   * API for a brief time. Wait for the given change number to be recognized.
   *
   * Returns a promise that resolves with true if a request is recognized, or
   * false if the change was never recognized after all attempts.
   *
   */
  _waitForChangeReachable(changeNum: NumericChangeId) {
    let attemptsRemaining = AWAIT_CHANGE_ATTEMPTS;
    return new Promise(resolve => {
      const check = () => {
        attemptsRemaining--;
        // Pass a no-op error handler to avoid the "not found" error toast.
        this.restApiService
          .getChange(changeNum, () => {})
          .then(response => {
            // If the response is 404, the response will be undefined.
            if (response) {
              resolve(true);
              return;
            }

            if (attemptsRemaining) {
              setTimeout(check, AWAIT_CHANGE_TIMEOUT_MS);
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

  _computeHasTooltip(title?: string) {
    return !!title;
  }

  _computeHasIcon(action: UIActionInfo) {
    return action.icon ? '' : 'hidden';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-actions': GrChangeActions;
  }
}
