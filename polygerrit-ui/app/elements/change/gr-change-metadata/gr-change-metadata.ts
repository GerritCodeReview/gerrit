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
import '../../../styles/shared-styles';
import '../../../styles/gr-change-metadata-shared-styles';
import '../../../styles/gr-change-view-integration-shared-styles';
import '../../../styles/gr-voting-styles';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../plugins/gr-external-style/gr-external-style';
import '../../shared/gr-account-chip/gr-account-chip';
import '../../shared/gr-date-formatter/gr-date-formatter';
import '../../shared/gr-editable-label/gr-editable-label';
import '../../shared/gr-icons/gr-icons';
import '../../shared/gr-limited-text/gr-limited-text';
import '../../shared/gr-linked-chip/gr-linked-chip';
import '../../shared/gr-tooltip-content/gr-tooltip-content';
import '../gr-change-requirements/gr-change-requirements';
import '../gr-commit-info/gr-commit-info';
import '../gr-reviewer-list/gr-reviewer-list';
import '../../shared/gr-account-list/gr-account-list';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-change-metadata_html';
import {
  GrReviewerSuggestionsProvider,
  SUGGESTIONS_PROVIDERS_USERS_TYPES,
} from '../../../scripts/gr-reviewer-suggestions-provider/gr-reviewer-suggestions-provider';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {
  ChangeStatus,
  GpgKeyInfoStatus,
  SubmitType,
} from '../../../constants/constants';
import {changeIsOpen} from '../../../utils/change-util';
import {customElement, property, observe} from '@polymer/decorators';
import {
  AccountDetailInfo,
  AccountInfo,
  BranchName,
  CommitId,
  CommitInfo,
  ElementPropertyDeepChange,
  GpgKeyInfo,
  Hashtag,
  LabelNameToInfoMap,
  NumericChangeId,
  ParentCommitInfo,
  PatchSetNum,
  RepoName,
  RevisionInfo,
  ServerInfo,
  TopicName,
} from '../../../types/common';
import {assertNever} from '../../../utils/common-util';
import {GrEditableLabel} from '../../shared/gr-editable-label/gr-editable-label';
import {GrLinkedChip} from '../../shared/gr-linked-chip/gr-linked-chip';
import {appContext} from '../../../services/app-context';
import {KnownExperimentId} from '../../../services/flags/flags';
import {
  Metadata,
  isSectionSet,
  DisplayRules,
} from '../../../utils/change-metadata-util';
import {fireEvent} from '../../../utils/event-util';
import {EditRevisionInfo, ParsedChangeInfo} from '../../../types/types';

const HASHTAG_ADD_MESSAGE = 'Add Hashtag';

enum ChangeRole {
  OWNER = 'owner',
  UPLOADER = 'uploader',
  AUTHOR = 'author',
  COMMITTER = 'committer',
}

export interface CommitInfoWithRequiredCommit extends CommitInfo {
  // gr-change-view always assigns commit to CommitInfo
  commit: CommitId;
}

const SubmitTypeLabel = new Map<SubmitType, string>([
  [SubmitType.FAST_FORWARD_ONLY, 'Fast Forward Only'],
  [SubmitType.MERGE_IF_NECESSARY, 'Merge if Necessary'],
  [SubmitType.REBASE_IF_NECESSARY, 'Rebase if Necessary'],
  [SubmitType.MERGE_ALWAYS, 'Always Merge'],
  [SubmitType.REBASE_ALWAYS, 'Rebase Always'],
  [SubmitType.CHERRY_PICK, 'Cherry Pick'],
]);

const NOT_CURRENT_MESSAGE = 'Not current - rebase possible';

interface PushCertificateValidationInfo {
  class: string;
  icon: string;
  message: string;
}

export interface GrChangeMetadata {
  $: {
    webLinks: HTMLElement;
  };
}

@customElement('gr-change-metadata')
export class GrChangeMetadata extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the change topic is changed.
   *
   * @event topic-changed
   */

  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: Object, notify: true})
  labels?: LabelNameToInfoMap;

  @property({type: Object})
  account?: AccountDetailInfo;

  @property({type: Object})
  revision?: RevisionInfo | EditRevisionInfo;

  @property({type: Object})
  commitInfo?: CommitInfoWithRequiredCommit;

  @property({type: Boolean, computed: '_computeIsMutable(account)'})
  _mutable = false;

  @property({type: Object})
  serverConfig?: ServerInfo;

  @property({type: Boolean})
  parentIsCurrent?: boolean;

  @property({type: String})
  readonly _notCurrentMessage = NOT_CURRENT_MESSAGE;

  @property({
    type: Boolean,
    computed: '_computeTopicReadOnly(_mutable, change)',
  })
  _topicReadOnly = true;

  @property({
    type: Boolean,
    computed: '_computeHashtagReadOnly(_mutable, change)',
  })
  _hashtagReadOnly = true;

  @property({
    type: Object,
    computed: '_computePushCertificateValidation(serverConfig, change)',
  })
  _pushCertificateValidation?: PushCertificateValidationInfo;

  @property({type: Boolean, computed: '_computeShowRequirements(change)'})
  _showRequirements = false;

  @property({type: Array})
  _assignee?: AccountInfo[];

  @property({type: Boolean, computed: '_computeIsWip(change)'})
  _isWip = false;

  @property({type: String})
  _newHashtag?: Hashtag;

  @property({type: Boolean})
  _settingTopic = false;

  @property({type: Array, computed: '_computeParents(change, revision)'})
  _currentParents: ParentCommitInfo[] = [];

  @property({type: Object})
  _CHANGE_ROLE = ChangeRole;

  @property({type: Object})
  _SECTION = Metadata;

  @property({type: Boolean})
  _showAllSections = false;

  @property({type: Boolean})
  _isNewChangeSummaryUiEnabled = false;

  flagsService = appContext.flagsService;

  restApiService = appContext.restApiService;

  private readonly reporting = appContext.reportingService;

  /** @override */
  ready() {
    super.ready();
    this._isNewChangeSummaryUiEnabled = this.flagsService.isEnabled(
      KnownExperimentId.NEW_CHANGE_SUMMARY_UI
    );
  }

  @observe('change.labels')
  _labelsChanged(labels?: LabelNameToInfoMap) {
    this.labels = {...labels};
  }

  @observe('change')
  _changeChanged(change?: ParsedChangeInfo) {
    this._assignee = change?.assignee ? [change.assignee] : [];
    this._settingTopic = false;
  }

  @observe('_assignee.*')
  _assigneeChanged(
    assigneeRecord: ElementPropertyDeepChange<GrChangeMetadata, '_assignee'>
  ) {
    if (!this.change || !this._isAssigneeEnabled(this.serverConfig)) {
      return;
    }
    const assignee = assigneeRecord.base;
    if (assignee?.length) {
      const acct = assignee[0];
      if (
        !acct._account_id ||
        (this.change.assignee &&
          acct._account_id === this.change.assignee._account_id)
      ) {
        return;
      }
      this.set(['change', 'assignee'], acct);
      this.restApiService.setAssignee(this.change._number, acct._account_id);
    } else {
      if (!this.change.assignee) {
        return;
      }
      this.set(['change', 'assignee'], undefined);
      this.restApiService.deleteAssignee(this.change._number);
    }
  }

  _computeHideStrategy(change?: ParsedChangeInfo) {
    return !changeIsOpen(change);
  }

  /**
   * @return If array is empty, returns undefined instead so
   * an existential check can be used to hide or show the webLinks
   * section.
   */
  _computeWebLinks(
    commitInfo?: CommitInfoWithRequiredCommit,
    serverConfig?: ServerInfo
  ) {
    if (!commitInfo) return undefined;
    const weblinks = GerritNav.getChangeWeblinks(
      this.change ? this.change.project : ('' as RepoName),
      commitInfo.commit,
      {
        weblinks: commitInfo.web_links,
        config: serverConfig,
      }
    );
    return weblinks.length ? weblinks : undefined;
  }

  _isAssigneeEnabled(serverConfig?: ServerInfo) {
    return !!serverConfig?.change?.enable_assignee;
  }

  _computeStrategy(change?: ParsedChangeInfo) {
    if (!change?.submit_type) {
      return '';
    }

    return SubmitTypeLabel.get(change.submit_type);
  }

  _computeLabelNames(labels?: LabelNameToInfoMap) {
    return labels ? Object.keys(labels).sort() : [];
  }

  _handleTopicChanged(e: CustomEvent<string>) {
    if (!this.change) {
      throw new Error('change must be set');
    }
    const lastTopic = this.change.topic;
    const topic = e.detail.length ? e.detail : undefined;
    this._settingTopic = true;
    const topicChangedForChangeNumber = this.change._number;
    this.restApiService
      .setChangeTopic(topicChangedForChangeNumber, topic)
      .then(newTopic => {
        if (this.change?._number !== topicChangedForChangeNumber) return;
        this._settingTopic = false;
        this.set(['change', 'topic'], newTopic);
        if (newTopic !== lastTopic) {
          fireEvent(this, 'topic-changed');
        }
      });
  }

  _showAddTopic(
    changeRecord?: ElementPropertyDeepChange<GrChangeMetadata, 'change'>,
    settingTopic?: boolean
  ) {
    const hasTopic = !!changeRecord?.base?.topic;
    return !hasTopic && !settingTopic;
  }

  _showTopicChip(
    changeRecord?: ElementPropertyDeepChange<GrChangeMetadata, 'change'>,
    settingTopic?: boolean
  ) {
    const hasTopic = !!changeRecord?.base?.topic;
    return hasTopic && !settingTopic;
  }

  _showCherryPickOf(
    changeRecord?: ElementPropertyDeepChange<GrChangeMetadata, 'change'>
  ) {
    const hasCherryPickOf =
      !!changeRecord?.base?.cherry_pick_of_change &&
      !!changeRecord?.base?.cherry_pick_of_patch_set;
    return hasCherryPickOf;
  }

  _handleHashtagChanged() {
    if (!this.change) {
      throw new Error('change must be set');
    }
    if (!this._newHashtag?.length) {
      return;
    }
    const newHashtag = this._newHashtag;
    this._newHashtag = '' as Hashtag;
    this.restApiService
      .setChangeHashtag(this.change._number, {add: [newHashtag]})
      .then(newHashtag => {
        this.set(['change', 'hashtags'], newHashtag);
        fireEvent(this, 'hashtag-changed');
      });
  }

  _computeTopicReadOnly(mutable?: boolean, change?: ParsedChangeInfo) {
    return !mutable || !change?.actions?.topic?.enabled;
  }

  _computeHashtagReadOnly(mutable?: boolean, change?: ParsedChangeInfo) {
    return !mutable || !change?.actions?.hashtags?.enabled;
  }

  _computeAssigneeReadOnly(mutable?: boolean, change?: ParsedChangeInfo) {
    return !mutable || !change?.actions?.assignee?.enabled;
  }

  _computeTopicPlaceholder(_topicReadOnly?: boolean) {
    // Action items in Material Design are uppercase -- placeholder label text
    // is sentence case.
    return _topicReadOnly ? 'No topic' : 'ADD TOPIC';
  }

  _computeHashtagPlaceholder(_hashtagReadOnly?: boolean) {
    return _hashtagReadOnly ? '' : HASHTAG_ADD_MESSAGE;
  }

  _computeShowRequirements(change?: ParsedChangeInfo) {
    if (!change) {
      return false;
    }
    if (change.status !== ChangeStatus.NEW) {
      // TODO(maximeg) change this to display the stored
      // requirements, once it is implemented server-side.
      return false;
    }
    const hasRequirements =
      !!change.requirements && Object.keys(change.requirements).length > 0;
    const hasLabels = !!change.labels && Object.keys(change.labels).length > 0;
    return hasRequirements || hasLabels || !!change.work_in_progress;
  }

  /**
   * @return object representing data for the push validation.
   */
  _computePushCertificateValidation(
    serverConfig?: ServerInfo,
    change?: ParsedChangeInfo
  ): PushCertificateValidationInfo | undefined {
    if (!change || !serverConfig?.receive?.enable_signed_push) return undefined;

    const rev = change.revisions[change.current_revision];
    if (!rev.push_certificate?.key) {
      return {
        class: 'help',
        icon: 'gr-icons:help',
        message: 'This patch set was created without a push certificate',
      };
    }

    const key = rev.push_certificate.key;
    switch (key.status) {
      case GpgKeyInfoStatus.BAD:
        return {
          class: 'invalid',
          icon: 'gr-icons:close',
          message: this._problems('Push certificate is invalid', key),
        };
      case GpgKeyInfoStatus.OK:
        return {
          class: 'notTrusted',
          icon: 'gr-icons:info',
          message: this._problems(
            'Push certificate is valid, but key is not trusted',
            key
          ),
        };
      case GpgKeyInfoStatus.TRUSTED:
        return {
          class: 'trusted',
          icon: 'gr-icons:check',
          message: this._problems(
            'Push certificate is valid and key is trusted',
            key
          ),
        };
      case undefined:
        // TODO(TS): Process it correctly
        throw new Error('deleted certificate');
      default:
        assertNever(key.status, `unknown certificate status: ${key.status}`);
    }
  }

  _problems(msg: string, key: GpgKeyInfo) {
    if (!key?.problems || key.problems.length === 0) {
      return msg;
    }

    return [msg + ':'].concat(key.problems).join('\n');
  }

  _computeShowRepoBranchTogether(repo?: RepoName, branch?: BranchName) {
    return !!repo && !!branch && repo.length + branch.length < 40;
  }

  _computeProjectUrl(project?: RepoName) {
    if (!project) return '';
    return GerritNav.getUrlForProjectChanges(project);
  }

  _computeBranchUrl(project?: RepoName, branch?: BranchName) {
    if (!project || !branch || !this.change || !this.change.status) return '';
    return GerritNav.getUrlForBranch(
      branch,
      project,
      this.change.status === ChangeStatus.NEW
        ? 'open'
        : this.change.status.toLowerCase()
    );
  }

  _computeCherryPickOfUrl(
    change?: NumericChangeId,
    patchset?: PatchSetNum,
    project?: RepoName
  ) {
    if (!change || !project) {
      return '';
    }
    return GerritNav.getUrlForChangeById(change, project, patchset);
  }

  _computeTopicUrl(topic: TopicName) {
    return GerritNav.getUrlForTopic(topic);
  }

  _computeHashtagUrl(hashtag: Hashtag) {
    return GerritNav.getUrlForHashtag(hashtag);
  }

  _handleTopicRemoved(e: CustomEvent) {
    if (!this.change) {
      throw new Error('change must be set');
    }
    const target = (dom(e) as EventApi).rootTarget as GrLinkedChip;
    target.disabled = true;
    this.restApiService
      .setChangeTopic(this.change._number)
      .then(() => {
        target.disabled = false;
        this.set(['change', 'topic'], '');
        fireEvent(this, 'topic-changed');
      })
      .catch(() => {
        target.disabled = false;
      });
  }

  _handleHashtagRemoved(e: CustomEvent) {
    e.preventDefault();
    if (!this.change) {
      throw new Error('change must be set');
    }
    const target = (dom(e) as EventApi).rootTarget as GrLinkedChip;
    target.disabled = true;
    this.restApiService
      .setChangeHashtag(this.change._number, {remove: [target.text as Hashtag]})
      .then(newHashtags => {
        target.disabled = false;
        this.set(['change', 'hashtags'], newHashtags);
      })
      .catch(() => {
        target.disabled = false;
      });
  }

  _computeIsWip(change?: ParsedChangeInfo) {
    return !!change?.work_in_progress;
  }

  _computeShowRoleClass(change?: ParsedChangeInfo, role?: ChangeRole) {
    return this._getNonOwnerRole(change, role) ? '' : 'hideDisplay';
  }

  _computeDisplayState(
    showAllSections: boolean,
    change: ParsedChangeInfo | undefined,
    section: Metadata
  ) {
    if (
      !this._isNewChangeSummaryUiEnabled ||
      showAllSections ||
      DisplayRules.ALWAYS_SHOW.includes(section) ||
      (DisplayRules.SHOW_IF_SET.includes(section) &&
        isSectionSet(section, change))
    ) {
      return '';
    }
    return 'hideDisplay';
  }

  _computeShowAllLabelText(showAllSections: boolean) {
    if (showAllSections) {
      return 'Show less';
    } else {
      return 'Show all';
    }
  }

  _onShowAllClick() {
    this._showAllSections = !this._showAllSections;
    this.reporting.reportInteraction('toggle show all button', {
      sectionName: 'metadata',
      toState: this._showAllSections ? 'Show all' : 'Show less',
    });
  }

  /**
   * Get the user with the specified role on the change. Returns undefined if the
   * user with that role is the same as the owner.
   */
  _getNonOwnerRole(change?: ParsedChangeInfo, role?: ChangeRole) {
    if (!change?.revisions?.[change.current_revision]) return undefined;

    const rev = change.revisions[change.current_revision];
    if (!rev) return undefined;

    if (
      role === ChangeRole.UPLOADER &&
      rev.uploader &&
      change.owner._account_id !== rev.uploader._account_id
    ) {
      return rev.uploader;
    }

    if (
      role === ChangeRole.AUTHOR &&
      rev.commit?.author &&
      change.owner.email !== rev.commit.author.email
    ) {
      return rev.commit.author;
    }

    if (
      role === ChangeRole.COMMITTER &&
      rev.commit?.committer &&
      change.owner.email !== rev.commit.committer.email &&
      !(
        rev.uploader?.email && rev.uploader.email === rev.commit.committer.email
      )
    ) {
      return rev.commit.committer;
    }

    return undefined;
  }

  _computeParents(
    change?: ParsedChangeInfo,
    revision?: RevisionInfo | EditRevisionInfo
  ): ParentCommitInfo[] {
    if (!revision || !revision.commit) {
      if (!change || !change.current_revision) {
        return [];
      }
      revision = change.revisions[change.current_revision];
      if (!revision || !revision.commit) {
        return [];
      }
    }
    return revision.commit.parents;
  }

  _computeParentsLabel(parents?: ParentCommitInfo[]) {
    return parents && parents.length > 1 ? 'Parents' : 'Parent';
  }

  _computeParentListClass(
    parents?: ParentCommitInfo[],
    parentIsCurrent?: boolean
  ) {
    // Undefined check for polymer 2
    if (parents === undefined || parentIsCurrent === undefined) {
      return '';
    }

    return [
      'parentList',
      parents && parents.length > 1 ? 'merge' : 'nonMerge',
      parentIsCurrent ? 'current' : 'notCurrent',
    ].join(' ');
  }

  _computeIsMutable(account?: AccountDetailInfo) {
    return account && !!Object.keys(account).length;
  }

  editTopic() {
    if (this._topicReadOnly || !this.change || this.change.topic) {
      return;
    }
    // Cannot use `this.$.ID` syntax because the element exists inside of a
    // dom-if.
    (this.shadowRoot!.querySelector(
      '.topicEditableLabel'
    ) as GrEditableLabel).open();
  }

  _getReviewerSuggestionsProvider(change?: ParsedChangeInfo) {
    if (!change) {
      return undefined;
    }
    const provider = GrReviewerSuggestionsProvider.create(
      this.restApiService,
      change._number,
      SUGGESTIONS_PROVIDERS_USERS_TYPES.ANY
    );
    provider.init();
    return provider;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-metadata': GrChangeMetadata;
  }
}
