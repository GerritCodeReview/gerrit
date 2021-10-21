/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="gr-change-metadata-shared-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="gr-font-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="shared-styles">
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
    #externalStyle {
      display: block;
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
    .separatedSection {
      margin-top: var(--spacing-l);
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
    .submit-requirement-error {
      color: var(--deemphasized-text-color);
      padding-left: var(--metadata-horizontal-padding);
    }
  </style>
  <gr-external-style id="externalStyle" name="change-metadata">
    <div class="metadata-header">
      <h3 class="metadata-title heading-3">Change Info</h3>
      <gr-button link="" class="show-all-button" on-click="_onShowAllClick"
        >[[_computeShowAllLabelText(_showAllSections)]]
        <iron-icon
          icon="gr-icons:expand-more"
          hidden$="[[_showAllSections]]"
        ></iron-icon
        ><iron-icon
          icon="gr-icons:expand-less"
          hidden$="[[!_showAllSections]]"
        ></iron-icon>
      </gr-button>
    </div>
    <template is="dom-if" if="[[change.submitted]]">
      <section
        class$="[[_computeDisplayState(_showAllSections, change, _SECTION.SUBMITTED)]]"
      >
        <span class="title">Submitted</span>
        <span class="value">
          <gr-date-formatter
            withTooltip
            date-str="[[change.submitted]]"
            showYesterday=""
          ></gr-date-formatter>
        </span>
      </section>
    </template>
    <section
      class$="[[_computeDisplayState(_showAllSections, change, _SECTION.UPDATED)]]"
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
          date-str="[[change.updated]]"
          showYesterday
        ></gr-date-formatter>
      </span>
    </section>
    <section
      class$="[[_computeDisplayState(_showAllSections, change, _SECTION.OWNER)]]"
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
          account="[[change.owner]]"
          change="[[change]]"
          highlightAttention
        ></gr-account-chip>
        <template is="dom-if" if="[[_pushCertificateValidation]]">
          <gr-tooltip-content
            has-tooltip=""
            title$="[[_pushCertificateValidation.message]]"
          >
            <iron-icon
              class$="icon [[_pushCertificateValidation.class]]"
              icon="[[_pushCertificateValidation.icon]]"
            >
            </iron-icon>
          </gr-tooltip-content>
        </template>
      </span>
    </section>
    <section class$="[[_computeShowRoleClass(change, _CHANGE_ROLE.UPLOADER)]]">
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
          account="[[_getNonOwnerRole(change, _CHANGE_ROLE.UPLOADER)]]"
          change="[[change]]"
          highlightAttention
        ></gr-account-chip>
      </span>
    </section>
    <section class$="[[_computeShowRoleClass(change, _CHANGE_ROLE.AUTHOR)]]">
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
          account="[[_getNonOwnerRole(change, _CHANGE_ROLE.AUTHOR)]]"
          change="[[change]]"
        ></gr-account-chip>
      </span>
    </section>
    <section class$="[[_computeShowRoleClass(change, _CHANGE_ROLE.COMMITTER)]]">
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
          account="[[_getNonOwnerRole(change, _CHANGE_ROLE.COMMITTER)]]"
          change="[[change]]"
        ></gr-account-chip>
      </span>
    </section>
    <section
      class$="[[_computeDisplayState(_showAllSections, change, _SECTION.REVIEWERS)]]"
    >
      <span class="title">Reviewers</span>
      <span class="value">
        <gr-reviewer-list
          change="{{change}}"
          mutable="[[_mutable]]"
          reviewers-only=""
          account="[[account]]"
          server-config="[[serverConfig]]"
        ></gr-reviewer-list>
      </span>
    </section>
    <section
      class$="[[_computeDisplayState(_showAllSections, change, _SECTION.CC)]]"
    >
      <span class="title">CC</span>
      <span class="value">
        <gr-reviewer-list
          change="{{change}}"
          mutable="[[_mutable]]"
          ccs-only=""
          account="[[account]]"
          server-config="[[serverConfig]]"
        ></gr-reviewer-list>
      </span>
    </section>
    <template
      is="dom-if"
      if="[[_computeShowRepoBranchTogether(change.project, change.branch)]]"
    >
      <section
        class$="[[_computeDisplayState(_showAllSections, change, _SECTION.REPO_BRANCH)]]"
      >
        <span class="title">Repo | Branch</span>
        <span class="value">
          <a href$="[[_computeProjectUrl(change.project)]]"
            >[[change.project]]</a
          >
          |
          <a href$="[[_computeBranchUrl(change.project, change.branch)]]"
            >[[change.branch]]</a
          >
        </span>
      </section>
    </template>
    <template
      is="dom-if"
      if="[[!_computeShowRepoBranchTogether(change.project, change.branch)]]"
    >
      <section
        class$="[[_computeDisplayState(_showAllSections, change, _SECTION.REPO_BRANCH)]]"
      >
        <span class="title">Repo</span>
        <span class="value">
          <a href$="[[_computeProjectUrl(change.project)]]">
            <gr-limited-text
              limit="40"
              text="[[change.project]]"
            ></gr-limited-text>
          </a>
        </span>
      </section>
      <section
        class$="[[_computeDisplayState(_showAllSections, change, _SECTION.REPO_BRANCH)]]"
      >
        <span class="title">Branch</span>
        <span class="value">
          <a href$="[[_computeBranchUrl(change.project, change.branch)]]">
            <gr-limited-text
              limit="40"
              text="[[change.branch]]"
            ></gr-limited-text>
          </a>
        </span>
      </section>
    </template>
    <section
      class$="[[_computeDisplayState(_showAllSections, change, _SECTION.PARENT)]]"
    >
      <span class="title">[[_computeParentsLabel(_currentParents)]]</span>
      <span class="value">
        <ol
          class$="[[_computeParentListClass(_currentParents, parentIsCurrent)]]"
        >
          <template is="dom-repeat" items="[[_currentParents]]" as="parent">
            <li>
              <gr-commit-info
                change="[[change]]"
                commit-info="[[parent]]"
                server-config="[[serverConfig]]"
              ></gr-commit-info>
              <gr-tooltip-content
                id="parentNotCurrentMessage"
                has-tooltip
                show-icon
                title$="[[_notCurrentMessage]]"
              ></gr-tooltip-content>
            </li>
          </template>
        </ol>
      </span>
    </section>
    <template is="dom-if" if="[[_isChangeMerged(change)]]">
      <section
        class$="[[_computeDisplayState(_showAllSections, change, _SECTION.MERGED_AS)]]"
      >
        <span class="title">Merged As</span>
        <span class="value">
          <gr-commit-info
            change="[[change]]"
            commit-info="[[_computeMergedCommitInfo(change.current_revision, change.revisions)]]"
            server-config="[[serverConfig]]"
          ></gr-commit-info>
        </span>
      </section>
    </template>
    <template is="dom-if" if="[[_showRevertCreatedAs(change)]]">
      <section
        class$="[[_computeDisplayState(_showAllSections, change, _SECTION.REVERT_CREATED_AS)]]"
      >
        <span class="title"
          >[[_getRevertSectionTitle(change, revertedChange)]]</span
        >
        <span class="value">
          <gr-commit-info
            change="[[change]]"
            commit-info="[[_computeRevertCommit(change, revertedChange)]]"
            server-config="[[serverConfig]]"
          ></gr-commit-info>
        </span>
      </section>
    </template>
    <section
      class$="topic [[_computeDisplayState(_showAllSections, change, _SECTION.TOPIC)]]"
    >
      <span class="title">Topic</span>
      <span class="value">
        <template is="dom-if" if="[[_showTopicChip(change.*, _settingTopic)]]">
          <gr-linked-chip
            text="[[change.topic]]"
            limit="40"
            href="[[_computeTopicUrl(change.topic)]]"
            removable="[[!_topicReadOnly]]"
            on-remove="_handleTopicRemoved"
          ></gr-linked-chip>
        </template>
        <template is="dom-if" if="[[_showAddTopic(change.*, _settingTopic)]]">
          <gr-editable-label
            class="topicEditableLabel"
            label-text="Add a topic"
            value="[[change.topic]]"
            max-length="1024"
            placeholder="[[_computeTopicPlaceholder(_topicReadOnly)]]"
            read-only="[[_topicReadOnly]]"
            on-changed="_handleTopicChanged"
            show-as-edit-pencil="true"
            autocomplete="true"
            query="[[queryTopic]]"
          ></gr-editable-label>
        </template>
      </span>
    </section>
    <template is="dom-if" if="[[_showCherryPickOf(change.*)]]">
      <section
        class$="[[_computeDisplayState(_showAllSections, change, _SECTION.CHERRY_PICK_OF)]]"
      >
        <span class="title">Cherry pick of</span>
        <span class="value">
          <a
            href$="[[_computeCherryPickOfUrl(change.cherry_pick_of_change, change.cherry_pick_of_patch_set, change.project)]]"
          >
            <gr-limited-text
              text="[[change.cherry_pick_of_change]],[[change.cherry_pick_of_patch_set]]"
              limit="40"
            >
            </gr-limited-text>
          </a>
        </span>
      </section>
    </template>
    <section
      class$="strategy [[_computeDisplayState(_showAllSections, change, _SECTION.STRATEGY)]]"
      hidden$="[[_computeHideStrategy(change)]]"
    >
      <span class="title">Strategy</span>
      <span class="value">[[_computeStrategy(change)]]</span>
    </section>
    <section
      class$="hashtag [[_computeDisplayState(_showAllSections, change, _SECTION.HASHTAGS)]]"
    >
      <span class="title">Hashtags</span>
      <span class="value">
        <template is="dom-repeat" items="[[change.hashtags]]">
          <gr-linked-chip
            class="hashtagChip"
            text="[[item]]"
            href="[[_computeHashtagUrl(item)]]"
            removable="[[!_hashtagReadOnly]]"
            on-remove="_handleHashtagRemoved"
            limit="40"
          >
          </gr-linked-chip>
        </template>
        <template is="dom-if" if="[[!_hashtagReadOnly]]">
          <gr-editable-label
            uppercase=""
            label-text="Add a hashtag"
            value="{{_newHashtag}}"
            placeholder="[[_computeHashtagPlaceholder(_hashtagReadOnly)]]"
            read-only="[[_hashtagReadOnly]]"
            on-changed="_handleHashtagChanged"
            show-as-edit-pencil="true"
          ></gr-editable-label>
        </template>
      </span>
    </section>
    <div class="separatedSection">
      <template is="dom-if" if="[[_showNewSubmitRequirements(change)]]">
        <gr-submit-requirements
          change="[[change]]"
          account="[[account]]"
          mutable="[[_mutable]]"
        ></gr-submit-requirements>
      </template>
      <template is="dom-if" if="[[!_showNewSubmitRequirements(change)]]">
        <gr-change-requirements
          change="{{change}}"
          account="[[account]]"
          mutable="[[_mutable]]"
        ></gr-change-requirements>
      </template>
      <template is="dom-if" if="[[_showNewSubmitRequirementWarning(change)]]">
        <div class="submit-requirement-error">
          New Submit Requirements don't work on this change.
        </div>
      </template>
    </div>
    <section
      id="webLinks"
      hidden$="[[!_computeWebLinks(commitInfo, serverConfig)]]"
    >
      <span class="title">Links</span>
      <span class="value">
        <template
          is="dom-repeat"
          items="[[_computeWebLinks(commitInfo, serverConfig)]]"
          as="link"
        >
          <a href="[[link.url]]" class="webLink" rel="noopener" target="_blank">
            [[link.name]]
          </a>
        </template>
      </span>
    </section>
    <gr-endpoint-decorator name="change-metadata-item">
      <gr-endpoint-param name="labels" value="[[labels]]"></gr-endpoint-param>
      <gr-endpoint-param name="change" value="[[change]]"></gr-endpoint-param>
      <gr-endpoint-param
        name="revision"
        value="[[revision]]"
      ></gr-endpoint-param>
    </gr-endpoint-decorator>
  </gr-external-style>
`;
