/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
import '../gr-commit-info/gr-commit-info';
import '../gr-reviewer-list/gr-reviewer-list';
import '../../shared/gr-account-list/gr-account-list';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {
  ChangeStatus,
  GpgKeyInfoStatus,
  InheritedBooleanInfoConfiguredValue,
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
  ConfigInfo,
  GpgKeyInfo,
  Hashtag,
  isAccount,
  isDetailedLabelInfo,
  LabelInfo,
  LabelNameToInfoMap,
  NumericChangeId,
  PARENT,
  ParentCommitInfo,
  RevisionPatchSetNum,
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
import {getApprovalInfo, getCodeReviewLabel} from '../../../utils/label-util';
import {LitElement, css, html, nothing, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import {fontStyles} from '../../../styles/gr-font-styles';
import {changeMetadataStyles} from '../../../styles/gr-change-metadata-shared-styles';
import {when} from 'lit/directives/when';
import {ifDefined} from 'lit/directives/if-defined';

const HASHTAG_ADD_MESSAGE = 'Add Hashtag';

export enum ChangeRole {
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

  @property({type: Object}) change?: ParsedChangeInfo;

  @property({type: Object}) revertedChange?: ChangeInfo;

  @property({type: Object}) account?: AccountDetailInfo;

  @property({type: Object}) revision?: RevisionInfo | EditRevisionInfo;

  @property({type: Object}) commitInfo?: CommitInfoWithRequiredCommit;

  @property({type: Object}) serverConfig?: ServerInfo;

  @property({type: Boolean}) parentIsCurrent?: boolean;

  @property({type: Object}) repoConfig?: ConfigInfo;

  // private but used in test
  @state() mutable = false;

  @state() private readonly notCurrentMessage = NOT_CURRENT_MESSAGE;

  // private but used in test
  @state() topicReadOnly = true;

  // private but used in test
  @state() hashtagReadOnly = true;

  @state() private pushCertificateValidation?: PushCertificateValidationInfo;

  // private but used in test
  @state() settingTopic = false;

  // private but used in test
  @state() currentParents: ParentCommitInfo[] = [];

  @state() private showAllSections = false;

  @state() private queryTopic?: AutocompleteQuery;

  @state() private queryHashtag?: AutocompleteQuery;

  private restApiService = getAppContext().restApiService;

  private readonly reporting = getAppContext().reportingService;

  constructor() {
    super();
    this.queryTopic = (input: string) => this.getTopicSuggestions(input);
    this.queryHashtag = (input: string) => this.getHashtagSuggestions(input);
  }

  static override styles = [
    sharedStyles,
    fontStyles,
    changeMetadataStyles,
    css`
      :host {
        display: table;
      }
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
    if (!this.change) return nothing;
    return html`<div>
      <div class="metadata-header">
        <h3 class="metadata-title heading-3">Change Info</h3>
        ${this.renderShowAllButton()}
      </div>
      ${this.renderSubmitted()} ${this.renderUpdated()} ${this.renderOwner()}
      ${this.renderNonOwner(ChangeRole.UPLOADER)}
      ${this.renderNonOwner(ChangeRole.AUTHOR)}
      ${this.renderNonOwner(ChangeRole.COMMITTER)} ${this.renderReviewers()}
      ${this.renderCCs()} ${this.renderProjectBranch()} ${this.renderParent()}
      ${this.renderMergedAs()} ${this.renderShowReverCreatedAs()}
      ${this.renderTopic()} ${this.renderCherryPickOf()}
      ${this.renderStrategy()} ${this.renderHashTags()}
      ${this.renderSubmitRequirements()} ${this.renderWeblinks()}
      <gr-endpoint-decorator name="change-metadata-item">
        <gr-endpoint-param
          name="labels"
          .value=${{...this.change?.labels}}
        ></gr-endpoint-param>
        <gr-endpoint-param
          name="change"
          .value=${this.change}
        ></gr-endpoint-param>
        <gr-endpoint-param
          name="revision"
          .value=${this.revision}
        ></gr-endpoint-param>
      </gr-endpoint-decorator>
    </div>`;
  }

  private renderShowAllButton() {
    return html`<gr-button
      link
      class="show-all-button"
      @click=${this.onShowAllClick}
      >${this.showAllSections ? 'Show less' : 'Show all'}
      <iron-icon
        icon="gr-icons:expand-more"
        ?hidden=${this.showAllSections}
      ></iron-icon
      ><iron-icon
        icon="gr-icons:expand-less"
        ?hidden=${!this.showAllSections}
      ></iron-icon>
    </gr-button>`;
  }

  private renderSubmitted() {
    if (!this.change!.submitted) return nothing;
    return html`<section class=${this.computeDisplayState(Metadata.SUBMITTED)}>
      <span class="title">Submitted</span>
      <span class="value">
        <gr-date-formatter
          withTooltip
          .dateStr=${this.change!.submitted}
          showYesterday
        ></gr-date-formatter>
      </span>
    </section> `;
  }

  private renderUpdated() {
    return html`<section class=${this.computeDisplayState(Metadata.UPDATED)}>
      <span class="title">
        <gr-tooltip-content
          has-tooltip
          title="Last update of (meta)data for this change."
        >
          Updated
        </gr-tooltip-content>
      </span>
      <span class="value">
        <gr-date-formatter
          withTooltip
          .dateStr=${this.change!.updated}
          showYesterday
        ></gr-date-formatter>
      </span>
    </section>`;
  }

  private renderOwner() {
    const change = this.change!;
    return html`<section class=${this.computeDisplayState(Metadata.OWNER)}>
      <span class="title">
        <gr-tooltip-content
          has-tooltip
          title="This user created or uploaded the first patchset of this change."
        >
          Owner
        </gr-tooltip-content>
      </span>
      <span class="value">
        <gr-account-chip
          .account=${change.owner}
          .change=${change}
          highlightAttention
          .vote=${this.computeVote(change.owner)}
          .label=${this.computeCodeReviewLabel()}
        >
          <gr-vote-chip
            slot="vote-chip"
            .vote=${this.computeVote(change.owner)}
            .label=${this.computeCodeReviewLabel()}
            circle-shape
          ></gr-vote-chip>
        </gr-account-chip>
        ${when(
          this.pushCertificateValidation,
          () => html`<gr-tooltip-content
            has-tooltip
            title=${this.pushCertificateValidation!.message}
          >
            <iron-icon
              class="icon ${this.pushCertificateValidation!.class}"
              icon=${this.pushCertificateValidation!.icon}
            >
            </iron-icon>
          </gr-tooltip-content>`
        )}
      </span>
    </section>`;
  }

  renderNonOwner(role: ChangeRole) {
    if (!this.getNonOwnerRole(role)) return nothing;
    let title = '';
    let name = '';
    if (role === ChangeRole.UPLOADER) {
      title =
        "This user uploaded the patchset to Gerrit (typically by running the 'git push' command).";
      name = 'Uploader';
    } else if (role === ChangeRole.AUTHOR) {
      title = 'This user wrote the code change.';
      name = 'Author';
    } else if (role === ChangeRole.COMMITTER) {
      title =
        'This user committed the code change to the Git repository (typically to the local Git repo before uploading).';
      name = 'Committer';
    }
    return html`<section>
      <span class="title">
        <gr-tooltip-content has-tooltip .title=${title}>
          ${name}
        </gr-tooltip-content>
      </span>
      <span class="value">
        <gr-account-chip
          .account=${this.getNonOwnerRole(role)}
          .change=${this.change}
          ?highlightAttention=${role === ChangeRole.UPLOADER}
          .vote=${this.computeVoteForRole(role)}
          .label=${this.computeCodeReviewLabel()}
        >
          <gr-vote-chip
            slot="vote-chip"
            .vote=${this.computeVoteForRole(role)}
            .label=${this.computeCodeReviewLabel()}
            circle-shape
          ></gr-vote-chip>
        </gr-account-chip>
      </span>
    </section>`;
  }

  private renderReviewers() {
    return html`<section class=${this.computeDisplayState(Metadata.REVIEWERS)}>
      <span class="title">Reviewers</span>
      <span class="value">
        <gr-reviewer-list
          .change=${this.change}
          ?mutable=${this.mutable}
          reviewers-only
          .account=${this.account}
        ></gr-reviewer-list>
      </span>
    </section>`;
  }

  private renderCCs() {
    return html`<section class=${this.computeDisplayState(Metadata.CC)}>
      <span class="title">CC</span>
      <span class="value">
        <gr-reviewer-list
          .change=${this.change}
          ?mutable=${this.mutable}
          ccs-only
          .account=${this.account}
        ></gr-reviewer-list>
      </span>
    </section>`;
  }

  private renderProjectBranch() {
    const change = this.change!;
    return when(
      this.computeShowRepoBranchTogether(),
      () =>
        html`<section class=${this.computeDisplayState(Metadata.REPO_BRANCH)}>
          <span class="title">Repo | Branch</span>
          <span class="value">
            <a href=${this.computeProjectUrl(change.project)}
              >${change.project}</a
            >
            |
            <a href=${this.computeBranchUrl(change.project, change.branch)}
              >${change.branch}</a
            >
          </span>
        </section>`,

      () => html` <section
          class=${this.computeDisplayState(Metadata.REPO_BRANCH)}
        >
          <span class="title">Repo</span>
          <span class="value">
            <a href=${this.computeProjectUrl(change.project)}>
              <gr-limited-text
                limit="40"
                .text=${change.project}
              ></gr-limited-text>
            </a>
          </span>
        </section>
        <section class=${this.computeDisplayState(Metadata.REPO_BRANCH)}>
          <span class="title">Branch</span>
          <span class="value">
            <a href=${this.computeBranchUrl(change.project, change.branch)}>
              <gr-limited-text
                limit="40"
                .text=${change.branch}
              ></gr-limited-text>
            </a>
          </span>
        </section>`
    );
  }

  private renderParent() {
    return html`<section class=${this.computeDisplayState(Metadata.PARENT)}>
      <span class="title"
        >${this.currentParents.length > 1 ? 'Parents' : PARENT}</span
      >
      <span class="value">
        <ol class=${this.computeParentListClass()}>
          ${this.currentParents.map(
            parent => html` <li>
              <gr-commit-info
                .change=${this.change}
                .commitInfo=${parent}
                .serverConfig=${this.serverConfig}
              ></gr-commit-info>
              <gr-tooltip-content
                id="parentNotCurrentMessage"
                has-tooltip
                show-icon
                .title=${this.notCurrentMessage}
              ></gr-tooltip-content>
            </li>`
          )}
        </ol>
      </span>
    </section>`;
  }

  private renderMergedAs() {
    const changeMerged = this.change?.status === ChangeStatus.MERGED;
    if (!changeMerged) return nothing;
    return html`<section class=${this.computeDisplayState(Metadata.MERGED_AS)}>
      <span class="title">Merged As</span>
      <span class="value">
        <gr-commit-info
          .change=${this.change}
          .commitInfo=${this.computeMergedCommitInfo(
            this.change?.current_revision,
            this.change?.revisions
          )}
          .serverConfig=${this.serverConfig}
        ></gr-commit-info>
      </span>
    </section>`;
  }

  private renderShowReverCreatedAs() {
    if (!this.showRevertCreatedAs()) return nothing;

    return html`<section
      class=${this.computeDisplayState(Metadata.REVERT_CREATED_AS)}
    >
      <span class="title">${this.getRevertSectionTitle()}</span>
      <span class="value">
        <gr-commit-info
          .change=${this.change}
          .commitInfo=${this.computeRevertCommit()}
          .serverConfig=${this.serverConfig}
        ></gr-commit-info>
      </span>
    </section>`;
  }

  private renderTopic() {
    const showTopic = this.change?.topic || !this.topicReadOnly;
    if (!showTopic) return nothing;

    return html`<section
      class="topic ${this.computeDisplayState(Metadata.TOPIC, this.account)}"
    >
      <span class="title">Topic</span>
      <span class="value">
        ${when(
          this.showTopicChip(),
          () => html` <gr-linked-chip
            .text=${this.change?.topic}
            limit="40"
            href=${GerritNav.getUrlForTopic(this.change!.topic!)}
            ?removable=${!this.topicReadOnly}
            @remove=${this.handleTopicRemoved}
          ></gr-linked-chip>`
        )}
        ${when(
          this.showAddTopic(),
          () =>
            html` <gr-editable-label
              class="topicEditableLabel"
              labelText="Add a topic"
              .value=${this.change?.topic}
              maxLength="1024"
              .placeholder=${this.computeTopicPlaceholder()}
              ?readOnly=${this.topicReadOnly}
              @changed=${this.handleTopicChanged}
              showAsEditPencil
              autocomplete
              .query=${this.queryTopic}
            ></gr-editable-label>`
        )}
      </span>
    </section>`;
  }

  private renderCherryPickOf() {
    if (!this.showCherryPickOf()) return nothing;
    return html` <section
      class=${this.computeDisplayState(Metadata.CHERRY_PICK_OF)}
    >
      <span class="title">Cherry pick of</span>
      <span class="value">
        <a
          href=${this.computeCherryPickOfUrl(
            this.change?.cherry_pick_of_change,
            this.change?.cherry_pick_of_patch_set,
            this.change?.project
          )}
        >
          <gr-limited-text
            text="${this.change?.cherry_pick_of_change},${this.change
              ?.cherry_pick_of_patch_set}"
            limit="40"
          >
          </gr-limited-text>
        </a>
      </span>
    </section>`;
  }

  private renderStrategy() {
    if (!changeIsOpen(this.change)) return nothing;
    return html`<section
      class="strategy ${this.computeDisplayState(Metadata.STRATEGY)}"
    >
      <span class="title">Strategy</span>
      <span class="value">${this.computeStrategy()}</span>
    </section>`;
  }

  private renderHashTags() {
    return html`<section
      class="hashtag ${this.computeDisplayState(Metadata.HASHTAGS)}"
    >
      <span class="title">Hashtags</span>
      <span class="value">
        ${(this.change?.hashtags ?? []).map(
          hashtag => html`<gr-linked-chip
            class="hashtagChip"
            .text=${hashtag}
            href=${this.computeHashtagUrl(hashtag)}
            ?removable=${!this.hashtagReadOnly}
            @remove=${this.handleHashtagRemoved}
            limit="40"
          >
          </gr-linked-chip>`
        )}
        ${when(
          !this.hashtagReadOnly,
          () => html`
            <gr-editable-label
              uppercase
              labelText="Add a hashtag"
              .placeholder=${this.computeHashtagPlaceholder()}
              .readOnly=${this.hashtagReadOnly}
              @changed=${this.handleHashtagChanged}
              showAsEditPencil
              autocomplete
              .query=${this.queryHashtag}
            ></gr-editable-label>
          `
        )}
      </span>
    </section>`;
  }

  private renderSubmitRequirements() {
    return html`<div class="separatedSection">
      <gr-submit-requirements
        .change=${this.change}
        .account=${this.account}
        .mutable=${this.mutable}
      ></gr-submit-requirements>
    </div>`;
  }

  private renderWeblinks() {
    const webLinks = this.computeWebLinks();
    if (!webLinks.length) return nothing;
    return html`<section id="webLinks">
      <span class="title">Links</span>
      <span class="value">
        ${webLinks.map(
          link => html`<a
            href=${ifDefined(link.url)}
            class="webLink"
            rel="noopener"
            target="_blank"
          >
            ${link.name}
          </a>`
        )}
      </span>
    </section>`;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('account')) {
      this.mutable = this.computeIsMutable();
    }
    if (changedProperties.has('mutable') || changedProperties.has('change')) {
      this.topicReadOnly = this.computeTopicReadOnly();
      this.hashtagReadOnly = this.computeHashtagReadOnly();
    }
    if (changedProperties.has('change')) {
      this.settingTopic = false;
    }
    if (
      changedProperties.has('serverConfig') ||
      changedProperties.has('change') ||
      changedProperties.has('repoConfig')
    ) {
      this.pushCertificateValidation = this.computePushCertificateValidation();
    }
    if (changedProperties.has('revision') || changedProperties.has('change')) {
      this.currentParents = this.computeParents();
    }
  }

  /**
   * @return If array is empty, returns undefined instead so
   * an existential check can be used to hide or show the webLinks
   * section.
   * private but used in test
   */
  computeWebLinks() {
    if (!this.commitInfo) return [];
    const weblinks = GerritNav.getChangeWeblinks(
      this.change ? this.change.project : ('' as RepoName),
      this.commitInfo.commit,
      {
        weblinks: this.commitInfo.web_links,
        config: this.serverConfig,
      }
    );
    return weblinks.length ? weblinks : [];
  }

  private computeStrategy() {
    if (!this.change?.submit_type) {
      return '';
    }

    return SubmitTypeLabel.get(this.change.submit_type);
  }

  // private but used in test
  computeLabelNames(labels?: LabelNameToInfoMap) {
    return labels ? Object.keys(labels).sort() : [];
  }

  // private but used in test
  handleTopicChanged(e: CustomEvent<string>) {
    if (!this.change) {
      throw new Error('change must be set');
    }
    const lastTopic = this.change.topic;
    const topic = e.detail.length ? e.detail : undefined;
    this.settingTopic = true;
    const topicChangedForChangeNumber = this.change._number;
    const change = this.change;
    this.restApiService
      .setChangeTopic(topicChangedForChangeNumber, topic)
      .then(newTopic => {
        if (this.change?._number !== topicChangedForChangeNumber) return;
        this.settingTopic = false;
        if (this.change === change) {
          this.change.topic = newTopic as TopicName;
          this.requestUpdate();
        }
        if (newTopic !== lastTopic) {
          fireEvent(this, 'topic-changed');
        }
      });
  }

  // private but used in test
  showAddTopic() {
    const hasTopic = !!this.change?.topic;
    return !hasTopic && !this.settingTopic && this.topicReadOnly === false;
  }

  // private but used in test
  showTopicChip() {
    const hasTopic = !!this.change?.topic;
    return hasTopic && !this.settingTopic;
  }

  // private but used in test
  showCherryPickOf() {
    const hasCherryPickOf =
      !!this.change?.cherry_pick_of_change &&
      !!this.change?.cherry_pick_of_patch_set;
    return hasCherryPickOf;
  }

  // private but used in test
  handleHashtagChanged(e: CustomEvent<string>) {
    if (!this.change) {
      throw new Error('change must be set');
    }
    const newHashtag = e.detail.length ? e.detail : undefined;
    if (!newHashtag?.length) {
      return;
    }
    const change = this.change;
    this.restApiService
      .setChangeHashtag(this.change._number, {add: [newHashtag as Hashtag]})
      .then(newHashtag => {
        if (this.change === change) {
          this.change.hashtags = newHashtag;
          this.requestUpdate();
          fireEvent(this, 'hashtag-changed');
        }
      });
  }

  // private but used in test
  computeTopicReadOnly() {
    return !this.mutable || !this.change?.actions?.topic?.enabled;
  }

  // private but used in test
  computeHashtagReadOnly() {
    return !this.mutable || !this.change?.actions?.hashtags?.enabled;
  }

  private computeTopicPlaceholder() {
    // Action items in Material Design are uppercase -- placeholder label text
    // is sentence case.
    return this.topicReadOnly ? 'No topic' : 'ADD TOPIC';
  }

  private computeHashtagPlaceholder() {
    return this.hashtagReadOnly ? '' : HASHTAG_ADD_MESSAGE;
  }

  /**
   * private but used in test
   *
   * @return object representing data for the push validation.
   */
  computePushCertificateValidation():
    | PushCertificateValidationInfo
    | undefined {
    if (!this.change || !this.serverConfig?.receive?.enable_signed_push)
      return undefined;

    if (!this.isEnabledSignedPushOnRepo()) {
      return undefined;
    }
    const rev = this.change.revisions[this.change.current_revision];
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
          message: this.problems('Push certificate is invalid', key),
        };
      case GpgKeyInfoStatus.OK:
        return {
          class: 'notTrusted',
          icon: 'gr-icons:info',
          message: this.problems(
            'Push certificate is valid, but key is not trusted',
            key
          ),
        };
      case GpgKeyInfoStatus.TRUSTED:
        return {
          class: 'trusted',
          icon: 'gr-icons:check',
          message: this.problems(
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

  // private but used in test
  isEnabledSignedPushOnRepo() {
    if (!this.repoConfig?.enable_signed_push) return false;

    const enableSignedPush = this.repoConfig.enable_signed_push;
    return (
      (enableSignedPush.configured_value ===
        InheritedBooleanInfoConfiguredValue.INHERIT &&
        enableSignedPush.inherited_value) ||
      enableSignedPush.configured_value ===
        InheritedBooleanInfoConfiguredValue.TRUE
    );
  }

  private problems(msg: string, key: GpgKeyInfo) {
    if (!key?.problems || key.problems.length === 0) {
      return msg;
    }

    return [msg + ':'].concat(key.problems).join('\n');
  }

  private computeShowRepoBranchTogether() {
    const {project, branch} = this.change!;
    return !!project && !!branch && project.length + branch.length < 40;
  }

  private computeProjectUrl(project?: RepoName) {
    if (!project) return '';
    return GerritNav.getUrlForProjectChanges(project);
  }

  private computeBranchUrl(project?: RepoName, branch?: BranchName) {
    if (!project || !branch || !this.change || !this.change.status) return '';
    return GerritNav.getUrlForBranch(
      branch,
      project,
      this.change.status === ChangeStatus.NEW
        ? 'open'
        : this.change.status.toLowerCase()
    );
  }

  private computeCherryPickOfUrl(
    change?: NumericChangeId,
    patchset?: RevisionPatchSetNum,
    project?: RepoName
  ) {
    if (!change || !project) {
      return '';
    }
    return GerritNav.getUrlForChangeById(change, project, patchset);
  }

  private computeHashtagUrl(hashtag: Hashtag) {
    return GerritNav.getUrlForHashtag(hashtag);
  }

  private handleTopicRemoved(e: CustomEvent) {
    if (!this.change) {
      throw new Error('change must be set');
    }
    const target = e.composedPath()[0] as GrLinkedChip;
    target.disabled = true;
    const change = this.change;
    this.restApiService
      .setChangeTopic(this.change._number)
      .then(() => {
        target.disabled = false;
        if (this.change === change) {
          this.change.topic = '' as TopicName;
          this.requestUpdate();
          fireEvent(this, 'topic-changed');
        }
      })
      .catch(() => {
        target.disabled = false;
      });
  }

  // private but used in test
  handleHashtagRemoved(e: CustomEvent) {
    e.preventDefault();
    if (!this.change) {
      throw new Error('change must be set');
    }
    const target = e.target as GrLinkedChip;
    target.disabled = true;
    const change = this.change;
    this.restApiService
      .setChangeHashtag(change._number, {remove: [target.text as Hashtag]})
      .then(newHashtags => {
        target.disabled = false;
        if (this.change === change) {
          this.change.hashtags = newHashtags;
          this.requestUpdate();
        }
      })
      .catch(() => {
        target.disabled = false;
      });
  }

  private computeDisplayState(section: Metadata, account?: AccountDetailInfo) {
    // special case for Topic - show always for owners, others when set
    if (section === Metadata.TOPIC) {
      if (
        this.showAllSections ||
        isOwner(this.change, account) ||
        isSectionSet(section, this.change)
      ) {
        return '';
      } else {
        return 'hideDisplay';
      }
    }
    if (
      this.showAllSections ||
      DisplayRules.ALWAYS_SHOW.includes(section) ||
      (DisplayRules.SHOW_IF_SET.includes(section) &&
        isSectionSet(section, this.change))
    ) {
      return '';
    }
    return 'hideDisplay';
  }

  // private but used in test
  computeMergedCommitInfo(
    currentrevision?: CommitId,
    revisions?: {[revisionId: string]: RevisionInfo | EditRevisionInfo}
  ): CommitInfo | undefined {
    if (!currentrevision || !revisions) return;
    const rev = revisions[currentrevision];
    if (!rev || !rev.commit) return;
    // CommitInfo.commit is optional. Set commit in all cases to avoid error
    // in <gr-commit-info>. @see Issue 5337
    if (!rev.commit.commit) {
      rev.commit.commit = currentrevision;
    }
    return rev.commit;
  }

  private getRevertSectionTitle() {
    return this.revertedChange?.status === ChangeStatus.MERGED
      ? 'Revert Submitted As'
      : 'Revert Created As';
  }

  // private but used in test
  showRevertCreatedAs() {
    if (!this.change?.messages) return false;
    return getRevertCreatedChangeIds(this.change.messages).length > 0;
  }

  // private but used in test
  computeRevertCommit(): CommitInfo | undefined {
    const {revertedChange, change} = this;
    if (revertedChange?.current_revision && revertedChange?.revisions) {
      // TODO(TS): Fix typing
      return {
        commit: this.computeMergedCommitInfo(
          revertedChange.current_revision,
          revertedChange.revisions
        ),
      } as CommitInfo;
    }
    if (!change?.messages) return undefined;
    // TODO(TS): Fix typing
    return {
      commit: getRevertCreatedChangeIds(change.messages)?.[0],
    } as unknown as CommitInfo;
  }

  // private but used in test
  onShowAllClick() {
    this.showAllSections = !this.showAllSections;
    this.reporting.reportInteraction(Interaction.TOGGLE_SHOW_ALL_BUTTON, {
      sectionName: 'metadata',
      toState: this.showAllSections ? 'Show all' : 'Show less',
    });
  }

  /**
   * Get the user with the specified role on the change. Returns undefined if the
   * user with that role is the same as the owner.
   * private but used in test
   */
  getNonOwnerRole(role: ChangeRole) {
    if (!this.change?.revisions?.[this.change.current_revision])
      return undefined;

    const rev = this.change.revisions[this.change.current_revision];
    if (!rev) return undefined;

    if (
      role === ChangeRole.UPLOADER &&
      rev.uploader &&
      this.change.owner._account_id !== rev.uploader._account_id
    ) {
      return rev.uploader;
    }

    if (
      role === ChangeRole.AUTHOR &&
      rev.commit?.author &&
      this.change.owner.email !== rev.commit.author.email
    ) {
      return rev.commit.author;
    }

    if (
      role === ChangeRole.COMMITTER &&
      rev.commit?.committer &&
      this.change.owner.email !== rev.commit.committer.email &&
      !(
        rev.uploader?.email && rev.uploader.email === rev.commit.committer.email
      )
    ) {
      return rev.commit.committer;
    }

    return undefined;
  }

  // private but used in test
  computeParents(): ParentCommitInfo[] {
    const {change, revision} = this;
    if (!revision?.commit) {
      if (!change?.current_revision) return [];
      const newRevision = change.revisions[change.current_revision];
      return newRevision?.commit?.parents ?? [];
    }
    return revision?.commit?.parents ?? [];
  }

  // private but used in test
  computeParentListClass() {
    return [
      'parentList',
      this.currentParents.length > 1 ? 'merge' : 'nonMerge',
      this.parentIsCurrent ? 'current' : 'notCurrent',
    ].join(' ');
  }

  private computeIsMutable() {
    return !!this.account && !!Object.keys(this.account).length;
  }

  editTopic() {
    if (this.topicReadOnly || !this.change || this.change.topic) {
      return;
    }
    // Cannot use `this.$.ID` syntax because the element exists inside of a
    // dom-if.
    (
      this.shadowRoot!.querySelector('.topicEditableLabel') as GrEditableLabel
    ).open();
  }

  private getTopicSuggestions(
    input: string
  ): Promise<AutocompleteSuggestion[]> {
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

  private getHashtagSuggestions(
    input: string
  ): Promise<AutocompleteSuggestion[]> {
    return this.restApiService
      .getChangesWithSimilarHashtag(input)
      .then(response =>
        (response ?? [])
          .flatMap(change => change.hashtags ?? [])
          .filter(notUndefined)
          .filter(unique)
          .map(hashtag => {
            return {name: hashtag, value: hashtag};
          })
      );
  }

  private computeVoteForRole(role: ChangeRole) {
    const reviewer = this.getNonOwnerRole(role);
    if (reviewer && isAccount(reviewer)) {
      return this.computeVote(reviewer);
    } else {
      return;
    }
  }

  private computeVote(reviewer: AccountInfo): ApprovalInfo | undefined {
    const codeReviewLabel = this.computeCodeReviewLabel();
    if (!codeReviewLabel || !isDetailedLabelInfo(codeReviewLabel)) return;
    return getApprovalInfo(codeReviewLabel, reviewer);
  }

  private computeCodeReviewLabel(): LabelInfo | undefined {
    if (!this.change?.labels) return;
    return getCodeReviewLabel(this.change.labels);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-metadata': GrChangeMetadata;
  }
}
