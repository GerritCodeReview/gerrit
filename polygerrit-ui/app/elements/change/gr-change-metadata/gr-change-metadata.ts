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
import '../../../styles/gr-font-styles';
import '../../../styles/gr-change-metadata-shared-styles';
import '../../../styles/gr-change-view-integration-shared-styles';
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
import '../gr-submit-requirements/gr-submit-requirements';
import '../gr-change-requirements/gr-change-requirements';
import '../gr-commit-info/gr-commit-info';
import '../gr-reviewer-list/gr-reviewer-list';
import '../../shared/gr-account-list/gr-account-list';
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
import {changeIsOpen, isOwner} from '../../../utils/change-util';
import {
  AccountDetailInfo,
  AccountInfo,
  ApprovalInfo,
  BranchName,
  ChangeInfo,
  CommitId,
  CommitInfo,
  ElementPropertyDeepChange,
  GpgKeyInfo,
  Hashtag,
  isAccount,
  isDetailedLabelInfo,
  LabelInfo,
  LabelNameToInfoMap,
  NumericChangeId,
  ParentCommitInfo,
  PatchSetNum,
  RepoName,
  RevisionInfo,
  ServerInfo,
  TopicName,
} from '../../../types/common';
import {assertNever, unique} from '../../../utils/common-util';
import {GrEditableLabel} from '../../shared/gr-editable-label/gr-editable-label';
import {GrLinkedChip} from '../../shared/gr-linked-chip/gr-linked-chip';
import {getAppContext} from '../../../services/app-context';
import {
  Metadata,
  isSectionSet,
  DisplayRules,
} from '../../../utils/change-metadata-util';
import {fireEvent} from '../../../utils/event-util';
import {
  EditRevisionInfo,
  notUndefined,
  ParsedChangeInfo,
} from '../../../types/types';
import {
  AutocompleteQuery,
  AutocompleteSuggestion,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {getRevertCreatedChangeIds} from '../../../utils/message-util';
import {Interaction} from '../../../constants/reporting';
import { 
  getApprovalInfo,
  getCodeReviewLabel,
  showNewSubmitRequirements,
} from '../../../utils/label-util';
import {LitElement, css, html, nothing, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import {fontStyles} from '../../../styles/gr-font-styles';
import {changeMetadataStyles} from '../../../styles/gr-change-metadata-shared-styles';

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

@customElement('gr-change-metadata')
export class GrChangeMetadata extends LitElement {
  /**
   * Fired when the change topic is changed.
   *
   * @event topic-changed
   */
  @query('#webLinks') webLinks?: HTMLElement;

  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: Object})
  revertedChange?: ChangeInfo;

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

  @property({type: Boolean, computed: '_computeIsWip(change)'})
  _isWip = false;

  @property({type: Boolean})
  _settingTopic = false;

  @property({type: Array, computed: '_computeParents(change, revision)'})
  _currentParents: ParentCommitInfo[] = [];

  @property({type: Boolean})
  _showAllSections = false;

  @property({type: Object})
  queryTopic?: AutocompleteQuery;

  restApiService = getAppContext().restApiService;

  private readonly reporting = getAppContext().reportingService;

  private readonly flagsService = getAppContext().flagsService;

  constructor() {
    super();
    this.queryTopic = (input: string) => this._getTopicSuggestions(input);
  }

  static override styles = [
    sharedStyles,
    fontStyles,
    changeMetadataStyles,
    css`
      :host {
        display: table;
      }
      gr-change-requirements,
      gr-submit-requirements {
        --requirements-horizontal-padding: var(--metadata-horizontal-padding);
      }
      gr-editable-label {
        max-width: 9em;
      }
      .webLink {
        display: block;
      }
      gr-account-chip[disabled],
      gr-linked-chip[disabled] {
        opacity: 0;
        pointer-events: none;
      }
      .hashtagChip {
        padding-bottom: var(--spacing-s);
      }
      /* consistent with section .title, .value */
      .hashtagChip:not(last-of-type) {
        padding-bottom: var(--spacing-s);
      }
      .hashtagChip:last-of-type {
        display: inline;
        vertical-align: top;
      }
      .parentList.merge {
        list-style-type: decimal;
        padding-left: var(--spacing-l);
      }
      .parentList gr-commit-info {
        display: inline-block;
      }
      .hideDisplay,
      #parentNotCurrentMessage {
        display: none;
      }
      .icon {
        margin: -3px 0;
      }
      .icon.help,
      .icon.notTrusted {
        color: var(--warning-foreground);
      }
      .icon.invalid {
        color: var(--negative-red-text-color);
      }
      .icon.trusted {
        color: var(--positive-green-text-color);
      }
      .parentList.notCurrent.nonMerge #parentNotCurrentMessage {
        --arrow-color: var(--warning-foreground);
        display: inline-block;
      }
      .oldSeparatedSection {
        margin-top: var(--spacing-l);
        padding: var(--spacing-m) 0;
      }
      .separatedSection {
        padding: var(--spacing-m) 0;
      }
      .hashtag gr-linked-chip,
      .topic gr-linked-chip {
        --linked-chip-text-color: var(--link-color);
      }
      gr-reviewer-list {
        --account-max-length: 100px;
        max-width: 285px;
      }
      .metadata-title {
        color: var(--deemphasized-text-color);
        padding-left: var(--metadata-horizontal-padding);
      }
      .metadata-header {
        display: flex;
        justify-content: space-between;
        align-items: flex-end;
        /* The goal is to achieve alignment of the owner account chip and the
         commit message box. Their top border should be on the same line. */
        margin-bottom: var(--spacing-s);
      }
      .show-all-button iron-icon {
        color: inherit;
        --iron-icon-height: 18px;
        --iron-icon-width: 18px;
      }
      gr-vote-chip {
        --gr-vote-chip-width: 14px;
        --gr-vote-chip-height: 14px;
      }
    `,
  ];

  override render() {
    const {change} = this;
    if (!change) return nothing;
    return html`<div>
      <div class="metadata-header">
        <h3 class="metadata-title heading-3">Change Info</h3>
        <gr-button link="" class="show-all-button" @click="_onShowAllClick"
          >${this._showAllSections ? 'Show less' : 'Show all'}
          <iron-icon
            icon="gr-icons:expand-more"
            hidden$="${this._showAllSections}"
          ></iron-icon
          ><iron-icon
            icon="gr-icons:expand-less"
            hidden$="${!this._showAllSections}"
          ></iron-icon>
        </gr-button>
      </div>
      <template is="dom-if" if="${change.submitted}">
        <section
          class$="${this._computeDisplayState(Metadata.SUBMITTED)}"
        >
          <span class="title">Submitted</span>
          <span class="value">
            <gr-date-formatter
              withTooltip
              date-str="${change.submitted}"
              showYesterday=""
            ></gr-date-formatter>
          </span>
        </section>
      </template>
      <section
        class$="${this._computeDisplayState(Metadata.UPDATED)}"
      >
        <span class="title">
          <gr-tooltip-content
            has-tooltip=""
            title="Last update of (meta)data for this change."
          >
            Updated
          </gr-tooltip-content>
        </span>
        <span class="value">
          <gr-date-formatter
            withTooltip
            date-str="${change.updated}"
            showYesterday
          ></gr-date-formatter>
        </span>
      </section>
      <section
        class$="${this._computeDisplayState(Metadata.OWNER)}"
      >
        <span class="title">
          <gr-tooltip-content
            has-tooltip=""
            title="This user created or uploaded the first patchset of this change."
          >
            Owner
          </gr-tooltip-content>
        </span>
        <span class="value">
          <gr-account-chip
            account="${change.owner}"
            change="${change}"
            highlightAttention
            vote="${this._computeVote(change.owner, change)}"
            label="${this._computeCodeReviewLabel(change)}"
          >
            <template is="dom-if" if="${this._showNewSubmitRequirements(change)}">
              <gr-vote-chip
                slot="vote-chip"
                vote="${this._computeVote(change.owner, change)}"
                label="${this._computeCodeReviewLabel(change)}"
                circle-shape
              ></gr-vote-chip>
            </template>
          </gr-account-chip>
          <template is="dom-if" if="${this._pushCertificateValidation}">
            <gr-tooltip-content
              has-tooltip=""
              title$="${this._pushCertificateValidation.message}"
            >
              <iron-icon
                class$="icon ${this._pushCertificateValidation.class}"
                icon="${this._pushCertificateValidation.icon}"
              >
              </iron-icon>
            </gr-tooltip-content>
          </template>
        </span>
      </section>
      <section
        class$="${this._computeShowRoleClass(change, ChangeRole.UPLOADER)}"
      >
        <span class="title">
          <gr-tooltip-content
            has-tooltip=""
            title="This user uploaded the patchset to Gerrit (typically by running the 'git push' command)."
          >
            Uploader
          </gr-tooltip-content>
        </span>
        <span class="value">
          <gr-account-chip
            account="${this._getNonOwnerRole(change, ChangeRole.UPLOADER)}"
            change="${change}"
            highlightAttention
            vote="${this._computeVoteForRole(ChangeRole.UPLOADER, change)}"
            label="${this._computeCodeReviewLabel(change)}"
          >
            <template is="dom-if" if="${this._showNewSubmitRequirements(change)}">
              <gr-vote-chip
                slot="vote-chip"
                vote="${this._computeVoteForRole(ChangeRole.UPLOADER, change)}"
                label="${this._computeCodeReviewLabel(change)}"
                circle-shape
              ></gr-vote-chip>
            </template>
          </gr-account-chip>
        </span>
      </section>
      <section class$="${this._computeShowRoleClass(change, ChangeRole.AUTHOR)}">
        <span class="title">
          <gr-tooltip-content
            has-tooltip=""
            title="This user wrote the code change."
          >
            Author
          </gr-tooltip-content>
        </span>
        <span class="value">
          <gr-account-chip
            account="${this._getNonOwnerRole(change, ChangeRole.AUTHOR)}"
            change="${change}"
            vote="${this._computeVoteForRole(ChangeRole.AUTHOR, change)}"
            label="${this._computeCodeReviewLabel(change)}"
          >
            <template is="dom-if" if="${this._showNewSubmitRequirements(change)}">
              <gr-vote-chip
                slot="vote-chip"
                vote="${this._computeVoteForRole(ChangeRole.AUTHOR, change)}"
                label="${this._computeCodeReviewLabel(change)}"
                circle-shape
              ></gr-vote-chip>
            </template>
          </gr-account-chip>
        </span>
      </section>
      <section
        class$="${this._computeShowRoleClass(change, ChangeRole.COMMITTER)}"
      >
        <span class="title">
          <gr-tooltip-content
            has-tooltip=""
            title="This user committed the code change to the Git repository (typically to the local Git repo before uploading)."
          >
            Committer
          </gr-tooltip-content>
        </span>
        <span class="value">
          <gr-account-chip
            account="${this._getNonOwnerRole(change, ChangeRole.COMMITTER)}"
            change="${change}"
            vote="${this._computeVoteForRole(ChangeRole.COMMITTER, change)}"
            label="${this._computeCodeReviewLabel(change)}"
          >
            <template is="dom-if" if="${this._showNewSubmitRequirements(change)}">
              <gr-vote-chip
                slot="vote-chip"
                vote="${this._computeVoteForRole(ChangeRole.COMMITTER, change)}"
                label="${this._computeCodeReviewLabel(change)}"
                circle-shape
              ></gr-vote-chip>
            </template>
          </gr-account-chip>
        </span>
      </section>
      <section
        class$="${this._computeDisplayState(Metadata.REVIEWERS)}"
      >
        <span class="title">Reviewers</span>
        <span class="value">
          <gr-reviewer-list
            change="{{change}}"
            mutable="${this._mutable}"
            reviewers-only=""
            account="${this.account}"
          ></gr-reviewer-list>
        </span>
      </section>
      <section
        class$="${this._computeDisplayState(Metadata.CC)}"
      >
        <span class="title">CC</span>
        <span class="value">
          <gr-reviewer-list
            change="{{change}}"
            mutable="${this._mutable}"
            ccs-only=""
            account="${this.account}"
          ></gr-reviewer-list>
        </span>
      </section>
      <template
        is="dom-if"
        if="${this._computeShowRepoBranchTogether(change.project, change.branch)}"
      >
        <section
          class$="${this._computeDisplayState(Metadata.REPO_BRANCH)}"
        >
          <span class="title">Repo | Branch</span>
          <span class="value">
            <a href$="${this._computeProjectUrl(change.project)}"
              >${change.project}</a
            >
            |
            <a href$="${this._computeBranchUrl(change.project, change.branch)}"
              >${change.branch}</a
            >
          </span>
        </section>
      </template>
      <template
        is="dom-if"
        if="${!this._computeShowRepoBranchTogether(change.project, change.branch)}"
      >
        <section
          class$="${this._computeDisplayState(Metadata.REPO_BRANCH)}"
        >
          <span class="title">Repo</span>
          <span class="value">
            <a href$="${this._computeProjectUrl(change.project)}">
              <gr-limited-text
                limit="40"
                text="${change.project}"
              ></gr-limited-text>
            </a>
          </span>
        </section>
        <section
          class$="${this._computeDisplayState(Metadata.REPO_BRANCH)}"
        >
          <span class="title">Branch</span>
          <span class="value">
            <a href$="${this._computeBranchUrl(change.project, change.branch)}">
              <gr-limited-text
                limit="40"
                text="${change.branch}"
              ></gr-limited-text>
            </a>
          </span>
        </section>
      </template>
      <section
        class$="${this._computeDisplayState(Metadata.PARENT)}"
      >
        <span class="title">${this._computeParentsLabel(_currentParents)}</span>
        <span class="value">
          <ol
            class$="${this._computeParentListClass(_currentParents, parentIsCurrent)}"
          >
            <template is="dom-repeat" items="${this._currentParents}" as="parent">
              <li>
                <gr-commit-info
                  change="${change}"
                  commit-info="${this.parent}"
                  server-config="${this.serverConfig}"
                ></gr-commit-info>
                <gr-tooltip-content
                  id="parentNotCurrentMessage"
                  has-tooltip
                  show-icon
                  title$="${this._notCurrentMessage}"
                ></gr-tooltip-content>
              </li>
            </template>
          </ol>
        </span>
      </section>
      <template is="dom-if" if="${this._isChangeMerged(change)}">
        <section
          class$="${this._computeDisplayState(Metadata.MERGED_AS)}"
        >
          <span class="title">Merged As</span>
          <span class="value">
            <gr-commit-info
              change="${change}"
              commit-info="${this._computeMergedCommitInfo(change.current_revision, change.revisions)}"
              server-config="${this.serverConfig}"
            ></gr-commit-info>
          </span>
        </section>
      </template>
      <template is="dom-if" if="${this._showRevertCreatedAs(change)}">
        <section
          class$="${this._computeDisplayState(Metadata.REVERT_CREATED_AS)}"
        >
          <span class="title"
            >${this._getRevertSectionTitle(change, revertedChange)}</span
          >
          <span class="value">
            <gr-commit-info
              change="${change}"
              commit-info="${this._computeRevertCommit(change, revertedChange)}"
              server-config="${this.serverConfig}"
            ></gr-commit-info>
          </span>
        </section>
      </template>
      <template is="dom-if" if="${this._showTopic(change, _topicReadOnly)}">
        <section
          class$="topic ${this._computeDisplayState(Metadata.TOPIC, account)}"
        >
          <span class="title">Topic</span>
          <span class="value">
            <template
              is="dom-if"
              if="${this._showTopicChip(change.*, _settingTopic)}"
            >
              <gr-linked-chip
                text="${change.topic}"
                limit="40"
                href="${this._computeTopicUrl(change.topic)}"
                removable="${!this._topicReadOnly}"
                @remove="_handleTopicRemoved"
              ></gr-linked-chip>
            </template>
            <template
              is="dom-if"
              if="${this._showAddTopic(change.*, _settingTopic, _topicReadOnly)}"
            >
              <gr-editable-label
                class="topicEditableLabel"
                labelText="Add a topic"
                value="${change.topic}"
                maxLength="1024"
                placeholder="${this._computeTopicPlaceholder(_topicReadOnly)}"
                read-only="${this._topicReadOnly}"
                @changed="_handleTopicChanged"
                showAsEditPencil
                autocomplete="true"
                query="${this.queryTopic}"
              ></gr-editable-label>
            </template>
          </span>
        </section>
      </template>
      <template is="dom-if" if="${this._showCherryPickOf(change.*)}">
        <section
          class$="${this._computeDisplayState(Metadata.CHERRY_PICK_OF)}"
        >
          <span class="title">Cherry pick of</span>
          <span class="value">
            <a
              href$="${this._computeCherryPickOfUrl(change.cherry_pick_of_change, change.cherry_pick_of_patch_set, change.project)}"
            >
              <gr-limited-text
                text="${change.cherry_pick_of_change},${change.cherry_pick_of_patch_set}"
                limit="40"
              >
              </gr-limited-text>
            </a>
          </span>
        </section>
      </template>
      <section
        class$="strategy ${this._computeDisplayState(Metadata.STRATEGY)}"
        hidden$="${this._computeHideStrategy(change)}"
      >
        <span class="title">Strategy</span>
        <span class="value">${this._computeStrategy(change)}</span>
      </section>
      <section
        class$="hashtag ${this._computeDisplayState(Metadata.HASHTAGS)}"
      >
        <span class="title">Hashtags</span>
        <span class="value">
          <template is="dom-repeat" items="${change.hashtags}">
            <gr-linked-chip
              class="hashtagChip"
              text="${this.item}"
              href="${this._computeHashtagUrl(item)}"
              removable="${!this._hashtagReadOnly}"
              @remove="_handleHashtagRemoved"
              limit="40"
            >
            </gr-linked-chip>
          </template>
          <template is="dom-if" if="${!this._hashtagReadOnly}">
            <gr-editable-label
              uppercase=""
              labelText="Add a hashtag"
              placeholder="${this._computeHashtagPlaceholder(_hashtagReadOnly)}"
              read-only="${this._hashtagReadOnly}"
              @changed="_handleHashtagChanged"
              showAsEditPencil
            ></gr-editable-label>
          </template>
        </span>
      </section>
      <template is="dom-if" if="${this._showNewSubmitRequirements(change)}">
        <div class="separatedSection">
          <gr-submit-requirements
            change="${change}"
            account="${this.account}"
            mutable="${this._mutable}"
          ></gr-submit-requirements>
        </div>
      </template>
      <template is="dom-if" if="${!this._showNewSubmitRequirements(change)}">
        <div class="oldSeparatedSection">
          <gr-change-requirements
            change="{{change}}"
            account="${this.account}"
            mutable="${this._mutable}"
          ></gr-change-requirements>
        </div>
      </template>
      <section
        id="webLinks"
        hidden$="${!this._computeWebLinks(commitInfo, serverConfig)}"
      >
        <span class="title">Links</span>
        <span class="value">
          <template
            is="dom-repeat"
            items="${this._computeWebLinks(commitInfo, serverConfig)}"
            as="link"
          >
            <a
              href="${this.link.url}"
              class="webLink"
              rel="noopener"
              target="_blank"
            >
              ${this.link.name}
            </a>
          </template>
        </span>
      </section>
      <gr-endpoint-decorator name="change-metadata-item">
        <gr-endpoint-param name="labels" value="${this.labels}"></gr-endpoint-param>
        <gr-endpoint-param name="change" value="${change}"></gr-endpoint-param>
        <gr-endpoint-param
          name="revision"
          value="${this.revision}"
        ></gr-endpoint-param>
      </gr-endpoint-decorator>
    </div>`;
  }

  @observe('change.labels')
  _labelsChanged(labels?: LabelNameToInfoMap) {
    this.labels = {...labels};
  }

  @observe('change')
  _changeChanged(_: ParsedChangeInfo) {
    this._settingTopic = false;
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

  _isChangeMerged(change?: ParsedChangeInfo) {
    return change?.status === ChangeStatus.MERGED;
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
    settingTopic?: boolean,
    topicReadOnly?: boolean
  ) {
    const hasTopic = !!changeRecord?.base?.topic;
    return !hasTopic && !settingTopic && topicReadOnly === false;
  }

  _showTopic(
    changeRecord?: ElementPropertyDeepChange<GrChangeMetadata, 'change'>,
    topicReadOnly?: boolean
  ) {
    const hasTopic = !!changeRecord?.base?.topic;
    return hasTopic || !topicReadOnly;
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

  _handleHashtagChanged(e: CustomEvent<string>) {
    if (!this.change) {
      throw new Error('change must be set');
    }
    const newHashtag = e.detail.length ? e.detail : undefined;
    if (!newHashtag?.length) {
      return;
    }
    this.restApiService
      .setChangeHashtag(this.change._number, {add: [newHashtag as Hashtag]})
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
    const target = e.composedPath()[0] as GrLinkedChip;
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
    section: Metadata,
    account?: AccountDetailInfo
  ) {
    // special case for Topic - show always for owners, others when set
    if (section === Metadata.TOPIC) {
      if (
        this._showAllSections ||
        isOwner(this.change, account) ||
        isSectionSet(section, this.change)
      ) {
        return '';
      } else {
        return 'hideDisplay';
      }
    }
    if (
      this._showAllSections ||
      DisplayRules.ALWAYS_SHOW.includes(section) ||
      (DisplayRules.SHOW_IF_SET.includes(section) &&
        isSectionSet(section, this.change))
    ) {
      return '';
    }
    return 'hideDisplay';
  }

  _computeMergedCommitInfo(
    current_revision: CommitId,
    revisions: {[revisionId: string]: RevisionInfo}
  ) {
    const rev = revisions[current_revision];
    if (!rev || !rev.commit) {
      return {};
    }
    // CommitInfo.commit is optional. Set commit in all cases to avoid error
    // in <gr-commit-info>. @see Issue 5337
    if (!rev.commit.commit) {
      rev.commit.commit = current_revision;
    }
    return rev.commit;
  }

  _getRevertSectionTitle(
    _change?: ParsedChangeInfo,
    revertedChange?: ChangeInfo
  ) {
    return revertedChange?.status === ChangeStatus.MERGED
      ? 'Revert Submitted As'
      : 'Revert Created As';
  }

  _showRevertCreatedAs(change?: ParsedChangeInfo) {
    if (!change?.messages) return false;
    return getRevertCreatedChangeIds(change.messages).length > 0;
  }

  _computeRevertCommit(change?: ParsedChangeInfo, revertedChange?: ChangeInfo) {
    if (revertedChange?.current_revision && revertedChange?.revisions) {
      return {
        commit: this._computeMergedCommitInfo(
          revertedChange.current_revision,
          revertedChange.revisions
        ),
      };
    }
    if (!change?.messages) return undefined;
    return {commit: getRevertCreatedChangeIds(change.messages)?.[0]};
  }

  _onShowAllClick() {
    this._showAllSections = !this._showAllSections;
    this.reporting.reportInteraction(Interaction.TOGGLE_SHOW_ALL_BUTTON, {
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
    (
      this.shadowRoot!.querySelector('.topicEditableLabel') as GrEditableLabel
    ).open();
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

  _getTopicSuggestions(input: string): Promise<AutocompleteSuggestion[]> {
    return this.restApiService
      .getChangesWithSimilarTopic(input)
      .then(response =>
        (response ?? [])
          .map(change => change.topic)
          .filter(notUndefined)
          .filter(unique)
          .map(topic => {
            return {name: topic, value: topic};
          })
      );
  }

  _showNewSubmitRequirements(change?: ParsedChangeInfo) {
    return showNewSubmitRequirements(this.flagsService, change);
  }

  _computeVoteForRole(role?: ChangeRole, change?: ParsedChangeInfo) {
    const reviewer = this._getNonOwnerRole(change, role);
    if (reviewer && isAccount(reviewer)) {
      return this._computeVote(reviewer, change);
    } else {
      return;
    }
  }

  _computeVote(
    reviewer: AccountInfo,
    change?: ParsedChangeInfo
  ): ApprovalInfo | undefined {
    const codeReviewLabel = this._computeCodeReviewLabel(change);
    if (!codeReviewLabel || !isDetailedLabelInfo(codeReviewLabel)) return;
    return getApprovalInfo(codeReviewLabel, reviewer);
  }

  _computeCodeReviewLabel(change?: ParsedChangeInfo): LabelInfo | undefined {
    if (!change || !change.labels) return;
    return getCodeReviewLabel(change.labels);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-metadata': GrChangeMetadata;
  }
}
