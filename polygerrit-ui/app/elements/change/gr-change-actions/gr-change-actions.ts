/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../admin/gr-create-change-dialog/gr-create-change-dialog';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-dropdown/gr-dropdown';
import '../../shared/gr-icon/gr-icon';
import '../gr-confirm-abandon-dialog/gr-confirm-abandon-dialog';
import '../gr-confirm-cherrypick-dialog/gr-confirm-cherrypick-dialog';
import '../gr-confirm-cherrypick-conflict-dialog/gr-confirm-cherrypick-conflict-dialog';
import '../gr-confirm-move-dialog/gr-confirm-move-dialog';
import '../gr-confirm-rebase-dialog/gr-confirm-rebase-dialog';
import '../gr-confirm-revert-dialog/gr-confirm-revert-dialog';
import '../gr-confirm-submit-dialog/gr-confirm-submit-dialog';
import '../../../styles/shared-styles';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {getAppContext} from '../../../services/app-context';
import {
  CURRENT,
  hasEditBasedOnCurrentPatchSet,
} from '../../../utils/patch-set-util';
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
import {TargetElement} from '../../../api/plugin';
import {
  AccountInfo,
  ActionInfo,
  ActionNameToActionInfoMap,
  BranchName,
  ChangeActionDialog,
  ChangeInfo,
  ChangeViewChangeInfo,
  CherryPickInput,
  CommitId,
  InheritedBooleanInfo,
  isDetailedLabelInfo,
  isQuickLabelInfo,
  LabelInfo,
  NumericChangeId,
  PatchSetNumber,
  RequestPayload,
  RevertSubmissionInfo,
  ReviewInput,
} from '../../../types/common';
import {GrConfirmAbandonDialog} from '../gr-confirm-abandon-dialog/gr-confirm-abandon-dialog';
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
import {GrButton} from '../../shared/gr-button/gr-button';
import {
  GrChangeActionsElement,
  UIActionInfo,
} from '../../shared/gr-js-api-interface/gr-change-actions-js-api';
import {
  fire,
  fireAlert,
  fireError,
  fireNoBubbleNoCompose,
} from '../../../utils/event-util';
import {
  getApprovalInfo,
  getVotingRange,
  StandardLabels,
} from '../../../utils/label-util';
import {
  ActionPriority,
  ActionType,
  ChangeActions,
  PrimaryActionKey,
  RevisionActions,
} from '../../../api/change-actions';
import {ErrorCallback} from '../../../api/rest';
import {GrDropdown} from '../../shared/gr-dropdown/gr-dropdown';
import {resolve} from '../../../models/dependency';
import {changeModelToken} from '../../../models/change/change-model';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html, nothing} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {ifDefined} from 'lit/directives/if-defined.js';
import {assertIsDefined, queryAll, uuid} from '../../../utils/common-util';
import {Interaction} from '../../../constants/reporting';
import {rootUrl} from '../../../utils/url-util';
import {createSearchUrl} from '../../../models/views/search';
import {createChangeUrl} from '../../../models/views/change';
import {storageServiceToken} from '../../../services/storage/gr-storage_impl';
import {ShowRevisionActionsDetail} from '../../shared/gr-js-api-interface/gr-js-api-types';
import {whenVisible} from '../../../utils/dom-util';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {subscribe} from '../../lit/subscription-controller';

const ERR_BRANCH_EMPTY = 'The destination branch can’t be empty.';
const ERR_COMMIT_EMPTY = 'The commit message can’t be empty.';
const ERR_REVISION_ACTIONS = 'Couldn’t load revision actions.';

export enum LabelStatus {
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

// Set of keys that have icons.
const ACTIONS_WITH_ICONS = new Map<
  string,
  Pick<UIActionInfo, 'filled' | 'icon'>
>([
  [ChangeActions.ABANDON, {icon: 'block'}],
  [ChangeActions.DELETE_EDIT, {icon: 'delete', filled: true}],
  [ChangeActions.EDIT, {icon: 'edit', filled: true}],
  [ChangeActions.PUBLISH_EDIT, {icon: 'publish', filled: true}],
  [ChangeActions.READY, {icon: 'visibility', filled: true}],
  [ChangeActions.REBASE_EDIT, {icon: 'rebase_edit'}],
  [RevisionActions.REBASE, {icon: 'rebase'}],
  [ChangeActions.RESTORE, {icon: 'history'}],
  [ChangeActions.REVERT, {icon: 'undo'}],
  [ChangeActions.STOP_EDIT, {icon: 'stop', filled: true}],
  [QUICK_APPROVE_ACTION.key, {icon: 'check'}],
  [RevisionActions.SUBMIT, {icon: 'done_all'}],
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

@customElement('gr-change-actions')
export class GrChangeActions
  extends LitElement
  implements GrChangeActionsElement
{
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

  @query('#mainContent') mainContent?: Element;

  @query('#actionsModal') actionsModal?: HTMLDialogElement;

  @query('#confirmRebase') confirmRebase?: GrConfirmRebaseDialog;

  @query('#confirmCherrypick') confirmCherrypick?: GrConfirmCherrypickDialog;

  @query('#confirmCherrypickConflict')
  confirmCherrypickConflict?: GrConfirmCherrypickConflictDialog;

  @query('#confirmMove') confirmMove?: GrConfirmMoveDialog;

  @query('#confirmRevertDialog') confirmRevertDialog?: GrConfirmRevertDialog;

  @query('#confirmAbandonDialog') confirmAbandonDialog?: GrConfirmAbandonDialog;

  @query('#confirmSubmitDialog') confirmSubmitDialog?: GrConfirmSubmitDialog;

  @query('#createFollowUpDialog') createFollowUpDialog?: GrDialog;

  @query('#createFollowUpChange') createFollowUpChange?: GrCreateChangeDialog;

  @query('#confirmDeleteDialog') confirmDeleteDialog?: GrDialog;

  @query('#confirmDeleteEditDialog') confirmDeleteEditDialog?: GrDialog;

  @query('#moreActions') moreActions?: GrDropdown;

  @query('#secondaryActions') secondaryActions?: HTMLElement;

  // TODO(TS): Ensure that ActionType, ChangeActions and RevisionActions
  // properties are replaced with enums everywhere and remove them from
  // the GrChangeActions class
  ActionType = ActionType;

  ChangeActions = ChangeActions;

  RevisionActions = RevisionActions;

  @property({type: Object})
  change?: ChangeViewChangeInfo;

  @state()
  actions: ActionNameToActionInfoMap = {};

  @property({type: Array})
  primaryActionKeys: PrimaryActionKey[] = [
    ChangeActions.READY,
    RevisionActions.SUBMIT,
  ];

  @property({type: Boolean})
  disableEdit = false;

  // private but used in test
  @state() _hideQuickApproveAction = false;

  @property({type: Object})
  account?: AccountInfo;

  @property({type: String})
  changeNum?: NumericChangeId;

  @property({type: String})
  changeStatus?: ChangeStatus;

  @property({type: String})
  commitNum?: CommitId;

  @state() latestPatchNum?: PatchSetNumber;

  @property({type: String})
  commitMessage = '';

  @property({type: Object})
  revisionActions: ActionNameToActionInfoMap = {};

  @state() private revisionSubmitAction?: ActionInfo | null;

  // used as a proprty type so cannot be private
  @state() revisionRebaseAction?: ActionInfo | null;

  @property({type: String})
  privateByDefault?: InheritedBooleanInfo;

  // private but used in test
  @state() loading = true;

  // private but used in test
  @state() actionLoadingMessage = '';

  @state() private inProgressActionKeys = new Set<string>();

  // _computeAllActions always returns an array
  // private but used in test
  @state() allActionValues: UIActionInfo[] = [];

  // private but used in test
  @state() topLevelActions?: UIActionInfo[];

  // private but used in test
  @state() topLevelPrimaryActions?: UIActionInfo[];

  // private but used in test
  @state() topLevelSecondaryActions?: UIActionInfo[];

  @state() private menuActions?: MenuAction[];

  @state() private overflowActions: OverflowAction[] = [
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

  @state() private actionPriorityOverrides: ActionPriorityOverride[] = [];

  @state() private additionalActions: UIActionInfo[] = [];

  // private but used in test
  @state() hiddenActions: string[] = [];

  // private but used in test
  @state() disabledMenuActions: string[] = [];

  // private but used in test
  @state()
  editPatchsetLoaded = false;

  @property({type: Boolean})
  editMode = false;

  // private but used in test
  @state()
  editBasedOnCurrentPatchSet = true;

  @property({type: Boolean})
  loggedIn = false;

  private readonly restApiService = getAppContext().restApiService;

  private readonly reporting = getAppContext().reportingService;

  private readonly getPluginLoader = resolve(this, pluginLoaderToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getStorage = resolve(this, storageServiceToken);

  private readonly getNavigation = resolve(this, navigationToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChangeModel().latestPatchNum$,
      x => (this.latestPatchNum = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().patchsets$,
      x => (this.editBasedOnCurrentPatchSet = hasEditBasedOnCurrentPatchSet(x))
    );
    subscribe(
      this,
      () => this.getChangeModel().patchNum$,
      x => (this.editPatchsetLoaded = x === 'edit')
    );
  }

  override connectedCallback() {
    super.connectedCallback();
    this.getPluginLoader().jsApiService.addElement(
      TargetElement.CHANGE_ACTIONS,
      this
    );
    this.handleLoadingComplete();
  }

  static override get styles() {
    return [
      sharedStyles,
      modalStyles,
      css`
        :host {
          display: flex;
          font-family: var(--font-family);
        }
        #actionLoadingMessage,
        #mainContent,
        section {
          display: flex;
        }
        #actionLoadingMessage,
        gr-button,
        gr-dropdown {
          /* px because don't have the same font size */
          margin-left: 8px;
        }
        gr-button {
          display: block;
        }
        #actionLoadingMessage {
          align-items: center;
          color: var(--deemphasized-text-color);
        }
        #confirmSubmitDialog .changeSubject {
          margin: var(--spacing-l);
          text-align: center;
        }
        gr-icon {
          color: inherit;
          margin-right: var(--spacing-xs);
        }
        #moreActions gr-icon {
          margin: 0;
        }
        #moreMessage,
        .hidden {
          display: none;
        }
        @media screen and (max-width: 50em) {
          #mainContent {
            flex-wrap: wrap;
          }
          gr-button {
            --gr-button-padding: var(--spacing-m);
            white-space: nowrap;
          }
          gr-button,
          gr-dropdown {
            margin: 0;
          }
          #actionLoadingMessage {
            margin: var(--spacing-m);
            text-align: center;
          }
          #moreMessage {
            display: inline;
          }
        }
      `,
    ];
  }

  override render() {
    if (!this.change) return nothing;
    return html`
      <div id="mainContent">
        <span id="actionLoadingMessage" ?hidden=${!this.actionLoadingMessage}>
          ${this.actionLoadingMessage}
        </span>
        <section
          id="primaryActions"
          ?hidden=${this.loading ||
          !this.topLevelActions ||
          !this.topLevelActions.length}
        >
          ${this.topLevelPrimaryActions?.map(action =>
            this.renderUIAction(action)
          )}
        </section>
        <section
          id="secondaryActions"
          ?hidden=${this.loading ||
          !this.topLevelActions ||
          !this.topLevelActions.length}
        >
          ${this.topLevelSecondaryActions?.map(action =>
            this.renderUIAction(action)
          )}
        </section>
        <gr-button ?hidden=${!this.loading}>Loading actions...</gr-button>
        <gr-dropdown
          id="moreActions"
          link
          .verticalOffset=${32}
          .horizontalAlign=${'right'}
          @tap-item=${this.handleOverflowItemTap}
          ?hidden=${this.loading ||
          !this.menuActions ||
          !this.menuActions.length}
          .disabledIds=${this.disabledMenuActions}
          .items=${this.menuActions}
        >
          <gr-icon icon="more_vert" aria-labelledby="moreMessage"></gr-icon>
          <span id="moreMessage">More</span>
        </gr-dropdown>
      </div>
      <dialog id="actionsModal" tabindex="-1">
        <gr-confirm-rebase-dialog
          id="confirmRebase"
          class="confirmDialog"
          @confirm-rebase=${this.handleRebaseConfirm}
          @cancel=${this.handleConfirmDialogCancel}
          .disableActions=${this.inProgressActionKeys.has(
            RevisionActions.REBASE
          )}
          .branch=${this.change?.branch}
          .rebaseOnCurrent=${this.revisionRebaseAction
            ? !!this.revisionRebaseAction.enabled
            : null}
        ></gr-confirm-rebase-dialog>
        <gr-confirm-cherrypick-dialog
          id="confirmCherrypick"
          class="confirmDialog"
          .changeStatus=${this.changeStatus}
          .commitMessage=${this.commitMessage}
          .commitNum=${this.commitNum}
          @confirm=${this.handleCherrypickConfirm}
          @cancel=${this.handleConfirmDialogCancel}
          .project=${this.change?.project}
        ></gr-confirm-cherrypick-dialog>
        <gr-confirm-cherrypick-conflict-dialog
          id="confirmCherrypickConflict"
          class="confirmDialog"
          @confirm=${this.handleCherrypickConflictConfirm}
          @cancel=${this.handleConfirmDialogCancel}
        ></gr-confirm-cherrypick-conflict-dialog>
        <gr-confirm-move-dialog
          id="confirmMove"
          class="confirmDialog"
          @confirm=${this.handleMoveConfirm}
          @cancel=${this.handleConfirmDialogCancel}
          .project=${this.change?.project}
        ></gr-confirm-move-dialog>
        <gr-confirm-revert-dialog
          id="confirmRevertDialog"
          class="confirmDialog"
          @confirm-revert=${this.handleRevertDialogConfirm}
          @cancel=${this.handleConfirmDialogCancel}
        ></gr-confirm-revert-dialog>
        <gr-confirm-abandon-dialog
          id="confirmAbandonDialog"
          class="confirmDialog"
          @confirm=${this.handleAbandonDialogConfirm}
          @cancel=${this.handleConfirmDialogCancel}
        ></gr-confirm-abandon-dialog>
        <gr-confirm-submit-dialog
          id="confirmSubmitDialog"
          class="confirmDialog"
          .action=${this.revisionSubmitAction}
          @cancel=${this.handleConfirmDialogCancel}
          @confirm=${this.handleSubmitConfirm}
        ></gr-confirm-submit-dialog>
        <gr-dialog
          id="createFollowUpDialog"
          class="confirmDialog"
          confirm-label="Create"
          @confirm=${this.handleCreateFollowUpChange}
          @cancel=${this.handleCloseCreateFollowUpChange}
        >
          <div class="header" slot="header">Create Follow-Up Change</div>
          <div class="main" slot="main">
            <gr-create-change-dialog
              id="createFollowUpChange"
              .branch=${this.change?.branch}
              .baseChange=${this.change?.id}
              .repoName=${this.change?.project}
              .privateByDefault=${this.privateByDefault}
            ></gr-create-change-dialog>
          </div>
        </gr-dialog>
        <gr-dialog
          id="confirmDeleteDialog"
          class="confirmDialog"
          confirm-label="Delete"
          confirm-on-enter=""
          @cancel=${this.handleConfirmDialogCancel}
          @confirm=${this.handleDeleteConfirm}
        >
          <div class="header" slot="header">Delete Change</div>
          <div class="main" slot="main">
            Do you really want to delete the change?
          </div>
        </gr-dialog>
        <gr-dialog
          id="confirmDeleteEditDialog"
          class="confirmDialog"
          confirm-label="Delete"
          confirm-on-enter=""
          @cancel=${this.handleConfirmDialogCancel}
          @confirm=${this.handleDeleteEditConfirm}
        >
          <div class="header" slot="header">Delete Change Edit</div>
          <div class="main" slot="main">
            Do you really want to delete the edit?
          </div>
        </gr-dialog>
      </dialog>
    `;
  }

  private renderUIAction(action: UIActionInfo) {
    return html`
      <gr-tooltip-content
        title=${ifDefined(action.title)}
        .hasTooltip=${!!action.title}
        ?position-below=${true}
      >
        <gr-button
          link
          class=${action.__key}
          data-action-key=${action.__key}
          data-label=${action.label}
          ?disabled=${this.calculateDisabled(action)}
          @click=${(e: MouseEvent) =>
            this.handleActionTap(e, action.__key, action.__type)}
        >
          ${this.renderUIActionIcon(action)} ${action.label}
        </gr-button>
      </gr-tooltip-content>
    `;
  }

  private renderUIActionIcon(action: UIActionInfo) {
    if (!action.icon) return nothing;
    return html`
      <gr-icon icon=${action.icon} ?filled=${action.filled}></gr-icon>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('change')) {
      this.reload();
      this.actions = this.change?.actions ?? {};
    }

    this.editStatusChanged();

    this.actionsChanged();
    this.allActionValues = this.computeAllActions();
    this.topLevelActions = this.allActionValues.filter(a => {
      if (this.hiddenActions.includes(a.__key)) return false;
      if (this.editMode) return EDIT_ACTIONS.has(a.__key);
      return this.getActionOverflowIndex(a.__type, a.__key) === -1;
    });
    this.topLevelPrimaryActions = this.topLevelActions.filter(
      action => action.__primary
    );
    this.topLevelSecondaryActions = this.topLevelActions.filter(
      action => !action.__primary
    );
    this.menuActions = this.computeMenuActions();
    this.revisionSubmitAction = this.getSubmitAction(this.revisionActions);
    this.revisionRebaseAction = this.getRebaseAction(this.revisionActions);
  }

  private getSubmitAction(revisionActions: ActionNameToActionInfoMap) {
    return this.getRevisionAction(revisionActions, 'submit');
  }

  private getRebaseAction(revisionActions: ActionNameToActionInfoMap) {
    return this.getRevisionAction(revisionActions, 'rebase');
  }

  private getRevisionAction(
    revisionActions: ActionNameToActionInfoMap,
    actionName: string
  ) {
    if (!revisionActions) {
      return undefined;
    }
    if (revisionActions[actionName] === undefined) {
      // Return null to fire an event when revisionActions was loaded
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

    this.loading = true;
    return this.restApiService
      .getChangeRevisionActions(this.changeNum, this.latestPatchNum)
      .then(revisionActions => {
        if (!revisionActions) {
          return;
        }

        this.revisionActions = revisionActions;
        this.sendShowRevisionActions({
          change,
          revisionActions,
        });
        this.handleLoadingComplete();
      })
      .catch(err => {
        fireAlert(this, ERR_REVISION_ACTIONS);
        this.loading = false;
        throw err;
      });
  }

  private handleLoadingComplete() {
    this.getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => (this.loading = false));
  }

  // private but used in test
  sendShowRevisionActions(detail: ShowRevisionActionsDetail) {
    this.getPluginLoader().jsApiService.handleShowRevisionActions(detail);
  }

  addActionButton(type: ActionType, label: string) {
    if (type !== ActionType.CHANGE && type !== ActionType.REVISION) {
      throw Error(`Invalid action type: ${type}`);
    }
    const action: UIActionInfo = {
      enabled: true,
      label,
      __type: type,
      __key: ADDITIONAL_ACTION_KEY_PREFIX + uuid(),
    };
    this.additionalActions.push(action);
    this.requestUpdate('additionalActions');
    return action.__key;
  }

  removeActionButton(key: string) {
    const idx = this.indexOfActionButtonWithKey(key);
    if (idx === -1) {
      return;
    }
    this.additionalActions.splice(idx, 1);
    this.requestUpdate('additionalActions');
  }

  setActionButtonProp<T extends keyof UIActionInfo>(
    key: string,
    prop: T,
    value: UIActionInfo[T]
  ) {
    this.additionalActions[this.indexOfActionButtonWithKey(key)][prop] = value;
    this.requestUpdate('additionalActions');
  }

  setActionOverflow(type: ActionType, key: string, overflow: boolean) {
    if (type !== ActionType.CHANGE && type !== ActionType.REVISION) {
      throw Error(`Invalid action type given: ${type}`);
    }
    const index = this.getActionOverflowIndex(type, key);
    const action: OverflowAction = {
      type,
      key,
      overflow,
    };
    if (!overflow && index !== -1) {
      this.overflowActions.splice(index, 1);
      this.requestUpdate('overflowActions');
    } else if (overflow) {
      this.overflowActions.push(action);
      this.requestUpdate('overflowActions');
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
    const index = this.actionPriorityOverrides.findIndex(
      action => action.type === type && action.key === key
    );
    const action: ActionPriorityOverride = {
      type,
      key,
      priority,
    };
    if (index !== -1) {
      this.actionPriorityOverrides[index] = action;
      this.requestUpdate('actionPriorityOverrides');
    } else {
      this.actionPriorityOverrides.push(action);
      this.requestUpdate('actionPriorityOverrides');
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

    const idx = this.hiddenActions.indexOf(key);
    if (hidden && idx === -1) {
      this.hiddenActions.push(key);
      this.requestUpdate('hiddenActions');
    } else if (!hidden && idx !== -1) {
      this.hiddenActions.splice(idx, 1);
      this.requestUpdate('hiddenActions');
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

  private indexOfActionButtonWithKey(key: string) {
    for (let i = 0; i < this.additionalActions.length; i++) {
      if (this.additionalActions[i].__key === key) {
        return i;
      }
    }
    return -1;
  }

  private actionsChanged() {
    this.hidden =
      Object.keys(this.actions).length === 0 &&
      Object.keys(this.revisionActions).length === 0 &&
      this.additionalActions.length === 0;
    this.actionLoadingMessage = '';
    this.disabledMenuActions = [];

    if (Object.keys(this.revisionActions).length !== 0) {
      if (!this.revisionActions.download) {
        this.revisionActions = {
          ...this.revisionActions,
          download: DOWNLOAD_ACTION,
        };
        fire(this, 'revision-actions-changed', {
          value: this.revisionActions,
        });
      }
    }
    if (
      !this.actions.includedIn &&
      this.change?.status === ChangeStatus.MERGED
    ) {
      this.actions = {...this.actions, includedIn: INCLUDED_IN_ACTION};
    }
  }

  private editStatusChanged() {
    // Hide change edits if not logged in
    if (this.change === undefined || !this.loggedIn) {
      return;
    }
    if (this.disableEdit) {
      delete this.actions.rebaseEdit;
      delete this.actions.publishEdit;
      delete this.actions.deleteEdit;
      delete this.actions.stopEdit;
      delete this.actions.edit;
      return;
    }
    if (this.editPatchsetLoaded) {
      // Only show actions that mutate an edit if an actual edit patch set
      // is loaded.
      if (changeIsOpen(this.change)) {
        if (this.editBasedOnCurrentPatchSet) {
          if (!this.actions.publishEdit) {
            this.actions = {...this.actions, publishEdit: PUBLISH_EDIT};
          }
          delete this.actions.rebaseEdit;
        } else {
          if (!this.actions.rebaseEdit) {
            this.actions = {...this.actions, rebaseEdit: REBASE_EDIT};
          }
          delete this.actions.publishEdit;
        }
      }
      if (!this.actions.deleteEdit) {
        this.actions = {...this.actions, deleteEdit: DELETE_EDIT};
      }
    } else {
      delete this.actions.rebaseEdit;
      delete this.actions.publishEdit;
      delete this.actions.deleteEdit;
    }

    if (changeIsOpen(this.change)) {
      // Only show edit button if there is no edit patchset loaded and the
      // file list is not in edit mode.
      if (this.editPatchsetLoaded || this.editMode) {
        delete this.actions.edit;
      } else {
        if (!this.actions.edit) {
          this.actions = {...this.actions, edit: EDIT};
        }
      }
      // Only show STOP_EDIT if edit mode is enabled, but no edit patch set
      // is loaded.
      if (this.editMode && !this.editPatchsetLoaded) {
        if (!this.actions.stopEdit) {
          this.actions = {...this.actions, stopEdit: STOP_EDIT};
          fireAlert(this, 'Change is in edit mode');
        }
      } else {
        delete this.actions.stopEdit;
      }
    } else {
      // Remove edit button.
      delete this.actions.edit;
    }
  }

  private getValuesFor<T>(obj: {[key: string]: T}): T[] {
    return Object.keys(obj).map(key => obj[key]);
  }

  private getLabelStatus(label: LabelInfo): LabelStatus {
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
  private getTopMissingApproval() {
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
      const status = this.getLabelStatus(labelInfo);
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
    if (!this.topLevelSecondaryActions) {
      throw new Error('topLevelSecondaryActions must be set');
    }
    this.topLevelSecondaryActions = this.topLevelSecondaryActions.filter(
      sa => !isQuickApproveAction(sa)
    );
    this._hideQuickApproveAction = true;
  }

  private getQuickApproveAction(): QuickApproveUIActionInfo | null {
    if (this._hideQuickApproveAction) {
      return null;
    }
    const approval = this.getTopMissingApproval();
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

  private getActionValues(
    actionsChange: ActionNameToActionInfoMap,
    primariesChange: PrimaryActionKey[],
    additionalActionsChange: UIActionInfo[],
    type: ActionType
  ): UIActionInfo[] {
    if (!actionsChange || !primariesChange) {
      return [];
    }

    const actions = actionsChange;
    const primaryActionKeys = primariesChange;
    const result: UIActionInfo[] = [];
    const values: Array<ChangeActions | RevisionActions> =
      type === ActionType.CHANGE
        ? this.getValuesFor(ChangeActions)
        : this.getValuesFor(RevisionActions);

    const pluginActions: UIActionInfo[] = [];
    Object.keys(actions).forEach(a => {
      const action: UIActionInfo = actions[a] as UIActionInfo;
      action.__key = a;
      action.__type = type;
      action.__primary = primaryActionKeys.includes(a as PrimaryActionKey);
      // Plugin actions always contain ~ in the key.
      if (a.indexOf('~') !== -1) {
        this.populateActionUrl(action);
        pluginActions.push(action);
        // Add server-side provided plugin actions to overflow menu.
        this.overflowActions.push({
          type,
          key: a,
        });
        this.requestUpdate('overflowActions');
        return;
      } else if (!values.includes(a as PrimaryActionKey)) {
        return;
      }
      action.label = this.getActionLabel(action);

      // Triggers a re-render by ensuring object inequality.
      result.push({...action});
    });

    let additionalActions = additionalActionsChange;
    additionalActions = additionalActions
      .filter(a => a.__type === type)
      .map(a => {
        a.__primary = primaryActionKeys.includes(a.__key as PrimaryActionKey);
        // Triggers a re-render by ensuring object inequality.
        return {...a};
      });
    return result.concat(additionalActions).concat(pluginActions);
  }

  private populateActionUrl(action: UIActionInfo) {
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
  private getActionLabel(action: UIActionInfo) {
    if (action.label === 'Delete') {
      // This label is common within change and revision actions. Make it more
      // explicit to the user.
      return 'Delete change';
    } else if (action.label === 'WIP') {
      return 'Mark as work in progress';
    }
    // Otherwise, just map the name to sentence case.
    return this.toSentenceCase(action.label);
  }

  /**
   * Capitalize the first letter and lowercase all others.
   *
   * private but used in test
   */
  toSentenceCase(s: string) {
    if (!s.length) {
      return '';
    }
    return s[0].toUpperCase() + s.slice(1).toLowerCase();
  }

  private computeLoadingLabel(action: string) {
    return ActionLoadingLabels[action] || 'Working...';
  }

  // private but used in test
  canSubmitChange() {
    if (!this.change) {
      return false;
    }
    return this.getPluginLoader().jsApiService.canSubmitChange(
      this.change,
      this.getRevision(this.change, this.latestPatchNum)
    );
  }

  // private but used in test
  getRevision(change: ChangeViewChangeInfo, patchNum?: PatchSetNumber) {
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
    const query = `submissionid: "${change.submission_id}"`;
    /* A chromium plugin expects that the modifyRevertMsg hook will only
    be called after the revert button is pressed, hence we populate the
    revert dialog after revert button is pressed. */
    this.restApiService.getChanges(0, query).then(changes => {
      if (!changes) {
        this.reporting.error(
          'Change Actions',
          new Error('getChanges returns undefined')
        );
        return;
      }
      assertIsDefined(this.confirmRevertDialog, 'confirmRevertDialog');
      this.confirmRevertDialog.populate(
        change,
        this.commitMessage,
        changes.length
      );
      this.showActionDialog(this.confirmRevertDialog);
    });
  }

  showSubmitDialog() {
    if (!this.canSubmitChange()) {
      return;
    }
    assertIsDefined(this.confirmSubmitDialog, 'confirmSubmitDialog');
    this.showActionDialog(this.confirmSubmitDialog);
  }

  private handleActionTap(e: MouseEvent, key: string, type: string) {
    e.preventDefault();
    let el = e.target as Element;
    while (el.tagName.toLowerCase() !== 'gr-button') {
      if (!el.parentElement) {
        return;
      }
      el = el.parentElement;
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
    this.handleAction(type as ActionType, key);
  }

  private handleOverflowItemTap(e: CustomEvent<MenuAction>) {
    e.preventDefault();
    const el = e.target as Element;
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
    this.handleAction(e.detail.action.__type, e.detail.action.__key);
  }

  // private but used in test
  handleAction(type: ActionType, key: string) {
    this.reporting.reportInteraction(`${type}-${key}`);
    switch (type) {
      case ActionType.REVISION:
        this.handleRevisionAction(key);
        break;
      case ActionType.CHANGE:
        this.handleChangeAction(key);
        break;
      default:
        this.fireAction(
          this.prependSlash(key),
          assertUIActionInfo(this.actions[key]),
          false
        );
    }
  }

  // private but used in test
  handleChangeAction(key: string) {
    switch (key) {
      case ChangeActions.REVERT:
        this.showRevertDialog();
        break;
      case ChangeActions.ABANDON:
        assertIsDefined(this.confirmAbandonDialog, 'confirmAbandonDialog');
        this.showActionDialog(this.confirmAbandonDialog);
        break;
      case QUICK_APPROVE_ACTION.key: {
        const action = this.allActionValues.find(isQuickApproveAction);
        if (!action) {
          return;
        }
        this.fireAction(this.prependSlash(key), action, true, action.payload);
        break;
      }
      case ChangeActions.EDIT:
        this.handleEditTap();
        break;
      case ChangeActions.STOP_EDIT:
        this.handleStopEditTap();
        break;
      case ChangeActions.DELETE:
        this.handleDeleteTap();
        break;
      case ChangeActions.DELETE_EDIT:
        this.handleDeleteEditTap();
        break;
      case ChangeActions.FOLLOW_UP:
        this.handleFollowUpTap();
        break;
      case ChangeActions.WIP:
        this.handleWipTap();
        break;
      case ChangeActions.MOVE:
        this.handleMoveTap();
        break;
      case ChangeActions.PUBLISH_EDIT:
        this.handlePublishEditTap();
        break;
      case ChangeActions.REBASE_EDIT:
        this.handleRebaseEditTap();
        break;
      case ChangeActions.INCLUDED_IN:
        this.handleIncludedInTap();
        break;
      default:
        this.fireAction(
          this.prependSlash(key),
          assertUIActionInfo(this.actions[key]),
          false
        );
    }
  }

  private handleRevisionAction(key: string) {
    switch (key) {
      case RevisionActions.REBASE:
        assertIsDefined(this.confirmRebase, 'confirmRebase');
        this.showActionDialog(this.confirmRebase);
        this.confirmRebase.fetchRecentChanges();
        break;
      case RevisionActions.CHERRYPICK:
        this.handleCherrypickTap();
        break;
      case RevisionActions.DOWNLOAD:
        this.handleDownloadTap();
        break;
      case RevisionActions.SUBMIT:
        if (!this.canSubmitChange()) {
          return;
        }
        assertIsDefined(this.confirmSubmitDialog, 'confirmSubmitDialog');
        this.showActionDialog(this.confirmSubmitDialog);
        break;
      default:
        this.fireAction(
          this.prependSlash(key),
          assertUIActionInfo(this.revisionActions[key]),
          true
        );
    }
  }

  private prependSlash(key: string) {
    return key === '/' ? key : `/${key}`;
  }

  private calculateDisabled(action: UIActionInfo) {
    // TODO(b/270972983): Remove this special casing once the backend is more
    // aggressive about setting`enabled:true`.
    if (action.__key === 'rebase') return false;
    return !action.enabled;
  }

  private handleConfirmDialogCancel() {
    this.hideAllDialogs();
  }

  private hideAllDialogs() {
    assertIsDefined(this.confirmSubmitDialog, 'confirmSubmitDialog');
    const dialogEls = queryAll(this, '.confirmDialog');
    for (const dialogEl of dialogEls) {
      (dialogEl as HTMLElement).hidden = true;
    }
    assertIsDefined(this.actionsModal, 'actionsModal');
    this.actionsModal.close();
  }

  // private but used in test
  handleRebaseConfirm(e: CustomEvent<ConfirmRebaseEventDetail>) {
    assertIsDefined(this.confirmRebase, 'confirmRebase');
    assertIsDefined(this.actionsModal, 'actionsModal');
    const payload = {
      base: e.detail.base,
      allow_conflicts: e.detail.allowConflicts,
      on_behalf_of_uploader: e.detail.onBehalfOfUploader,
    };
    const rebaseChain = !!e.detail.rebaseChain;
    this.fireAction(
      rebaseChain ? '/rebase:chain' : '/rebase',
      assertUIActionInfo(this.revisionActions.rebase),
      rebaseChain ? false : true,
      payload,
      {
        allow_conflicts: payload.allow_conflicts,
        on_behalf_of_uploader: payload.on_behalf_of_uploader,
      }
    );
  }

  // private but used in test
  handleCherrypickConfirm() {
    this.handleCherryPickRestApi(false);
  }

  // private but used in test
  handleCherrypickConflictConfirm() {
    this.handleCherryPickRestApi(true);
  }

  private handleCherryPickRestApi(conflicts: boolean) {
    assertIsDefined(this.confirmCherrypick, 'confirmCherrypick');
    assertIsDefined(this.actionsModal, 'actionsModal');
    const el = this.confirmCherrypick;
    if (!el.branch) {
      fireAlert(this, ERR_BRANCH_EMPTY);
      return;
    }
    if (!el.message) {
      fireAlert(this, ERR_COMMIT_EMPTY);
      return;
    }
    this.actionsModal.close();
    el.hidden = true;
    this.fireAction(
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

  // private but used in test
  handleMoveConfirm() {
    assertIsDefined(this.confirmMove, 'confirmMove');
    assertIsDefined(this.actionsModal, 'actionsModal');
    const el = this.confirmMove;
    if (!el.branch) {
      fireAlert(this, ERR_BRANCH_EMPTY);
      return;
    }
    this.actionsModal.close();
    el.hidden = true;
    this.fireAction('/move', assertUIActionInfo(this.actions.move), false, {
      destination_branch: el.branch,
      message: el.message,
    });
  }

  private handleRevertDialogConfirm(e: CustomEvent<ConfirmRevertEventDetail>) {
    assertIsDefined(this.confirmRevertDialog, 'confirmRevertDialog');
    assertIsDefined(this.actionsModal, 'actionsModal');
    const revertType = e.detail.revertType;
    const message = e.detail.message;
    const el = this.confirmRevertDialog;
    this.actionsModal.close();
    el.hidden = true;
    switch (revertType) {
      case RevertType.REVERT_SINGLE_CHANGE:
        this.fireAction(
          '/revert',
          assertUIActionInfo(this.actions.revert),
          false,
          {message}
        );
        break;
      case RevertType.REVERT_SUBMISSION:
        // TODO(dhruvsri): replace with this.actions.revert_submission once
        // BE starts sending it again
        this.fireAction(
          '/revert_submission',
          {__key: 'revert_submission', method: HttpMethod.POST} as UIActionInfo,
          false,
          {message}
        );
        break;
      default:
        this.reporting.error(
          'Change Actions',
          new Error('invalid revert type')
        );
    }
  }

  // private but used in test
  handleAbandonDialogConfirm() {
    assertIsDefined(this.confirmAbandonDialog, 'confirmAbandonDialog');
    assertIsDefined(this.actionsModal, 'actionsModal');
    const el = this.confirmAbandonDialog;
    this.actionsModal.close();
    el.hidden = true;
    this.fireAction(
      '/abandon',
      assertUIActionInfo(this.actions.abandon),
      false,
      {
        message: el.message,
      }
    );
  }

  private handleCreateFollowUpChange() {
    assertIsDefined(this.createFollowUpChange, 'createFollowUpChange');
    this.createFollowUpChange.handleCreateChange();
    this.handleCloseCreateFollowUpChange();
  }

  private handleCloseCreateFollowUpChange() {
    assertIsDefined(this.actionsModal, 'actionsModal');
    this.actionsModal.close();
  }

  private handleDeleteConfirm() {
    this.hideAllDialogs();
    this.fireAction(
      '/',
      assertUIActionInfo(this.actions[ChangeActions.DELETE]),
      false
    );
  }

  private handleDeleteEditConfirm() {
    this.hideAllDialogs();

    // We need to make sure that all cached version of a change
    // edit are deleted.
    this.getStorage().eraseEditableContentItemsForChangeEdit(this.changeNum);

    this.fireAction(
      '/edit',
      assertUIActionInfo(this.actions.deleteEdit),
      false
    );
  }

  // private but used in test
  handleSubmitConfirm() {
    if (!this.canSubmitChange()) {
      return;
    }
    this.hideAllDialogs();
    this.fireAction(
      '/submit',
      assertUIActionInfo(this.revisionActions.submit),
      true
    );
  }

  private getActionOverflowIndex(type: string, key: string) {
    return this.overflowActions.findIndex(
      action => action.type === type && action.key === key
    );
  }

  // private but used in test
  setLoadingOnButtonWithKey(action: UIActionInfo) {
    const key = action.__key;
    this.inProgressActionKeys.add(key);
    this.actionLoadingMessage = this.computeLoadingLabel(key);
    let buttonKey = key;
    // TODO(dhruvsri): clean this up later
    // If key is revert-submission, then button key should be 'revert'
    if (buttonKey === ChangeActions.REVERT_SUBMISSION) {
      // Revert submission button no longer exists
      buttonKey = ChangeActions.REVERT;
    }

    // If the action appears in the overflow menu.
    if (this.getActionOverflowIndex(action.__type, buttonKey) !== -1) {
      this.disabledMenuActions.push(buttonKey === '/' ? 'delete' : buttonKey);
      this.requestUpdate('disabledMenuActions');
      return () => {
        this.inProgressActionKeys.delete(key);
        this.actionLoadingMessage = '';
        this.disabledMenuActions = [];
        this.requestUpdate();
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
      this.inProgressActionKeys.delete(action.__key);
      this.actionLoadingMessage = '';
      buttonEl.removeAttribute('loading');
      buttonEl.disabled = false;
      this.requestUpdate();
    };
  }

  // private but used in test
  fireAction(
    endpoint: string,
    action: UIActionInfo,
    revAction: boolean,
    payload?: RequestPayload,
    toReport?: Object
  ) {
    const cleanupFn = this.setLoadingOnButtonWithKey(action);
    this.reporting.reportInteraction(Interaction.CHANGE_ACTION_FIRED, {
      endpoint,
      toReport,
    });

    this.send(
      action.method,
      payload,
      endpoint,
      revAction,
      cleanupFn,
      action
    ).then(res => this.handleResponse(action, res));
  }

  // private but used in test
  showActionDialog(dialog: ChangeActionDialog) {
    this.hideAllDialogs();
    if (dialog.init) dialog.init();
    dialog.hidden = false;
    assertIsDefined(this.actionsModal, 'actionsModal');
    this.actionsModal.showModal();
    whenVisible(dialog, () => {
      if (dialog.resetFocus) {
        dialog.resetFocus();
      }
    });
  }

  // TODO(rmistry): Redo this after
  // https://issues.gerritcodereview.com/issues/40004936 is resolved.
  // private but used in test
  setReviewOnRevert(newChangeId: NumericChangeId) {
    const review = this.getPluginLoader().jsApiService.getReviewPostRevert(
      this.change
    );
    if (!review) {
      return Promise.resolve(undefined);
    }
    return this.restApiService.saveChangeReview(newChangeId, CURRENT, review);
  }

  // private but used in test
  handleResponse(action: UIActionInfo, response?: Response) {
    if (!response) {
      return;
    }
    // response is guaranteed to be ok (due to semantics of rest-api methods)
    return this.restApiService.getResponseObject(response).then(obj => {
      switch (action.__key) {
        case ChangeActions.REVERT: {
          const revertChangeInfo: ChangeInfo = obj as unknown as ChangeInfo;
          this.waitForChangeReachable(revertChangeInfo._number)
            .then(() => this.setReviewOnRevert(revertChangeInfo._number))
            .then(() => {
              this.getNavigation().setUrl(
                createChangeUrl({change: revertChangeInfo})
              );
            });
          break;
        }
        case RevisionActions.CHERRYPICK: {
          const cherrypickChangeInfo: ChangeInfo = obj as unknown as ChangeInfo;
          this.waitForChangeReachable(cherrypickChangeInfo._number).then(() => {
            this.getNavigation().setUrl(
              createChangeUrl({change: cherrypickChangeInfo})
            );
          });
          break;
        }
        case ChangeActions.DELETE:
          if (action.__type === ActionType.CHANGE) {
            this.getNavigation().setUrl(rootUrl());
          }
          break;
        case ChangeActions.WIP:
        case ChangeActions.DELETE_EDIT:
        case ChangeActions.PUBLISH_EDIT:
        case ChangeActions.REBASE_EDIT:
        case ChangeActions.REBASE:
        case ChangeActions.SUBMIT:
          // Hide rebase dialog only if the action succeeds
          this.actionsModal?.close();
          this.hideAllDialogs();
          this.getChangeModel().navigateToChangeResetReload();
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
          const topic = revertSubmistionInfo.revert_changes[0].topic;
          this.getNavigation().setUrl(createSearchUrl({topic}));
          break;
        }
        default:
          this.getChangeModel().navigateToChangeResetReload();
          break;
      }
    });
  }

  // private but used in test
  handleResponseError(
    action: UIActionInfo,
    response: Response | undefined | null,
    body?: RequestPayload
  ) {
    if (!response) {
      return Promise.resolve(() => {
        fireError(this, `Could not perform action '${action.__key}'`);
      });
    }
    if (action && action.__key === RevisionActions.CHERRYPICK) {
      if (
        response.status === 409 &&
        body &&
        !(body as CherryPickInput).allow_conflicts
      ) {
        assertIsDefined(
          this.confirmCherrypickConflict,
          'confirmCherrypickConflict'
        );
        this.showActionDialog(this.confirmCherrypickConflict);
        return;
      }
    }
    return response.text().then(errText => {
      fireError(this, `Could not perform action: ${errText}`);
      if (!errText.startsWith('Change is already up to date')) {
        throw Error(errText);
      }
    });
  }

  // private but used in test
  send(
    method: HttpMethod | undefined,
    payload: RequestPayload | undefined,
    actionEndpoint: string,
    revisionAction: boolean,
    cleanupFn: () => void,
    action: UIActionInfo
  ): Promise<Response | undefined> {
    const handleError: ErrorCallback = response => {
      cleanupFn.call(this);
      this.handleResponseError(action, response, payload);
    };
    const change = this.change;
    const changeNum = this.changeNum;
    if (!change || !changeNum) {
      return Promise.reject(
        new Error('Properties change and changeNum must be set.')
      );
    }
    return this.getChangeModel()
      .fetchChangeUpdates(change)
      .then(result => {
        if (!result.isLatest) {
          fire(this, 'show-alert', {
            message:
              'Cannot set label: a newer patch has been ' +
              'uploaded to this change.',
            action: 'Reload',
            callback: () => this.getChangeModel().navigateToChangeResetReload(),
          });

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

  // private but used in test
  handleCherrypickTap() {
    if (!this.change) {
      throw new Error('The change property must be set');
    }
    assertIsDefined(this.confirmCherrypick, 'confirmCherrypick');
    this.confirmCherrypick.branch = '' as BranchName;
    const query = `topic: "${this.change.topic}"`;
    const options = listChangesOptionsToHex(
      ListChangesOption.MESSAGES,
      ListChangesOption.ALL_REVISIONS
    );
    this.restApiService
      .getChanges(0, query, undefined, options)
      .then(changes => {
        if (!changes) {
          this.reporting.error(
            'Change Actions',
            new Error('getChanges returns undefined')
          );
          return;
        }
        this.confirmCherrypick!.updateChanges(changes);
        this.showActionDialog(this.confirmCherrypick!);
      });
  }

  // private but used in test
  handleMoveTap() {
    assertIsDefined(this.confirmMove, 'confirmMove');
    this.confirmMove.branch = '' as BranchName;
    this.confirmMove.message = '';
    this.showActionDialog(this.confirmMove);
  }

  // private but used in test
  handleDownloadTap() {
    fire(this, 'download-tap', {});
  }

  // private but used in test
  handleIncludedInTap() {
    fire(this, 'included-tap', {});
  }

  // private but used in test
  handleDeleteTap() {
    assertIsDefined(this.confirmDeleteDialog, 'confirmDeleteDialog');
    this.showActionDialog(this.confirmDeleteDialog);
  }

  // private but used in test
  handleDeleteEditTap() {
    assertIsDefined(this.confirmDeleteEditDialog, 'confirmDeleteEditDialog');
    this.showActionDialog(this.confirmDeleteEditDialog);
  }

  private handleFollowUpTap() {
    assertIsDefined(this.createFollowUpDialog, 'createFollowUpDialog');
    this.showActionDialog(this.createFollowUpDialog);
  }

  private handleWipTap() {
    if (!this.actions.wip) {
      return;
    }
    this.fireAction('/wip', assertUIActionInfo(this.actions.wip), false);
  }

  private handlePublishEditTap() {
    if (!this.actions.publishEdit) return;

    // We need to make sure that all cached version of a change
    // edit are deleted.
    this.getStorage().eraseEditableContentItemsForChangeEdit(this.changeNum);

    this.fireAction(
      '/edit:publish',
      assertUIActionInfo(this.actions.publishEdit),
      false,
      {notify: NotifyType.NONE}
    );
  }

  private handleRebaseEditTap() {
    if (!this.actions.rebaseEdit) {
      return;
    }
    this.fireAction(
      '/edit:rebase',
      assertUIActionInfo(this.actions.rebaseEdit),
      false
    );
  }

  /**
   * Merge sources of change actions into a single ordered array of action
   * values.
   */
  private computeAllActions(): UIActionInfo[] {
    if (this.change === undefined) {
      return [];
    }

    const revisionActionValues = this.getActionValues(
      this.revisionActions,
      this.primaryActionKeys,
      this.additionalActions,
      ActionType.REVISION
    );
    const changeActionValues = this.getActionValues(
      this.actions,
      this.primaryActionKeys,
      this.additionalActions,
      ActionType.CHANGE
    );
    const quickApprove = this.getQuickApproveAction();
    if (quickApprove) {
      changeActionValues.unshift(quickApprove);
    }

    return revisionActionValues
      .concat(changeActionValues)
      .sort((a, b) => this.actionComparator(a, b))
      .map(action => {
        return {
          ...action,
          ...(ACTIONS_WITH_ICONS.get(action.__key) ?? {}),
        };
      })
      .filter(action => !this.shouldSkipAction(action));
  }

  private getActionPriority(action: UIActionInfo) {
    if (action.__type && action.__key) {
      const overrideAction = this.actionPriorityOverrides.find(
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
   *
   * private but used in test
   */
  actionComparator(actionA: UIActionInfo, actionB: UIActionInfo) {
    const priorityDelta =
      this.getActionPriority(actionA) - this.getActionPriority(actionB);
    // Sort by the button label if same priority.
    if (priorityDelta === 0) {
      return actionA.label > actionB.label ? 1 : -1;
    } else {
      return priorityDelta;
    }
  }

  private shouldSkipAction(action: UIActionInfo) {
    return SKIP_ACTION_KEYS.includes(action.__key);
  }

  private computeMenuActions(): MenuAction[] {
    return this.allActionValues
      .filter(a => {
        const overflow = this.getActionOverflowIndex(a.__type, a.__key) !== -1;
        return overflow && !this.hiddenActions.includes(a.__key);
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

  /**
   * Occasionally, a change created by a change action is not yet known to the
   * API for a brief time. Wait for the given change number to be recognized.
   *
   * Returns a promise that resolves with true if a request is recognized, or
   * false if the change was never recognized after all attempts.
   *
   * private but used in test
   */
  waitForChangeReachable(changeNum: NumericChangeId) {
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

  private handleEditTap() {
    fireNoBubbleNoCompose(this, 'edit-tap', {});
  }

  private handleStopEditTap() {
    fireNoBubbleNoCompose(this, 'stop-edit-tap', {});
  }
}

declare global {
  interface HTMLElementEventMap {
    'download-tap': CustomEvent<{}>;
    'edit-tap': CustomEvent<{}>;
    'included-tap': CustomEvent<{}>;
    'revision-actions-changed': CustomEvent<{value: ActionNameToActionInfoMap}>;
    'stop-edit-tap': CustomEvent<{}>;
  }
  interface HTMLElementTagNameMap {
    'gr-change-actions': GrChangeActions;
  }
}
