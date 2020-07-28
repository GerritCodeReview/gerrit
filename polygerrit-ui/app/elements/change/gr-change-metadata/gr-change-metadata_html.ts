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
  <style include="shared-styles">
    :host {
      display: table;
      --account-max-length: 20ch;
    }
    gr-change-requirements {
      --requirements-horizontal-padding: var(--metadata-horizontal-padding);
    }
    gr-editable-label {
      max-width: 9em;
    }
    .webLink {
      display: block;
    }
    /* CSS Mixins should be applied last. */
    section.assignee {
      @apply --change-metadata-assignee;
    }
    section.strategy {
      @apply --change-metadata-strategy;
    }
    section.topic {
      @apply --change-metadata-topic;
    }
    gr-account-chip[disabled],
    gr-linked-chip[disabled] {
      opacity: 0;
      pointer-events: none;
    }
    .hashtagChip {
      margin-bottom: var(--spacing-m);
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
      color: #ffa62f;
    }
    .icon.invalid {
      color: var(--negative-red-text-color);
    }
    .icon.trusted {
      color: var(--positive-green-text-color);
    }
    .parentList.notCurrent.nonMerge #parentNotCurrentMessage {
      --arrow-color: #ffa62f;
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
      max-width: 200px;
    }
  </style>
  <gr-external-style id="externalStyle" name="change-metadata">
    <section>
      <span class="title">Updated</span>
      <span class="value">
        <gr-date-formatter
          has-tooltip=""
          date-str="[[change.updated]]"
        ></gr-date-formatter>
      </span>
    </section>
    <section>
      <span class="title">Owner</span>
      <span class="value">
        <gr-account-link
          account="[[change.owner]]"
          change="[[change]]"
          highlight-attention
        ></gr-account-link>
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
      <span class="title">Uploader</span>
      <span class="value">
        <gr-account-link
          account="[[_getNonOwnerRole(change, _CHANGE_ROLE.UPLOADER)]]"
          change="[[change]]"
          highlight-attention
        ></gr-account-link>
      </span>
    </section>
    <section class$="[[_computeShowRoleClass(change, _CHANGE_ROLE.AUTHOR)]]">
      <span class="title">Author</span>
      <span class="value">
        <gr-account-link
          account="[[_getNonOwnerRole(change, _CHANGE_ROLE.AUTHOR)]]"
          change="[[change]]"
        ></gr-account-link>
      </span>
    </section>
    <section class$="[[_computeShowRoleClass(change, _CHANGE_ROLE.COMMITTER)]]">
      <span class="title">Committer</span>
      <span class="value">
        <gr-account-link
          account="[[_getNonOwnerRole(change, _CHANGE_ROLE.COMMITTER)]]"
          change="[[change]]"
        ></gr-account-link>
      </span>
    </section>
    <template is="dom-if" if="[[_isAssigneeEnabled(serverConfig)]]">
      <section class="assignee">
        <span class="title">Assignee</span>
        <span class="value">
          <gr-account-list
            id="assigneeValue"
            placeholder="Set assignee..."
            max-count="1"
            skip-suggest-on-empty=""
            accounts="{{_assignee}}"
            readonly="[[_computeAssigneeReadOnly(_mutable, change)]]"
            suggestions-provider="[[_getReviewerSuggestionsProvider(change)]]"
          >
          </gr-account-list>
        </span>
      </section>
    </template>
    <section>
      <span class="title">Reviewers</span>
      <span class="value">
        <gr-reviewer-list
          change="{{change}}"
          mutable="[[_mutable]]"
          reviewers-only=""
          server-config="[[serverConfig]]"
        ></gr-reviewer-list>
      </span>
    </section>
    <section>
      <span class="title">CC</span>
      <span class="value">
        <gr-reviewer-list
          change="{{change}}"
          mutable="[[_mutable]]"
          ccs-only=""
          server-config="[[serverConfig]]"
        ></gr-reviewer-list>
      </span>
    </section>
    <template
      is="dom-if"
      if="[[_computeShowRepoBranchTogether(change.project, change.branch)]]"
    >
      <section>
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
      <section>
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
      <section>
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
    <section>
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
                has-tooltip=""
                show-icon=""
                title$="[[_notCurrentMessage]]"
              ></gr-tooltip-content>
            </li>
          </template>
        </ol>
      </span>
    </section>
    <section class="topic">
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
          ></gr-editable-label>
        </template>
      </span>
    </section>
    <template is="dom-if" if="[[_showCherryPickOf(change.*)]]">
      <section>
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
      class="strategy"
      hidden$="[[_computeHideStrategy(change)]]"
      hidden=""
    >
      <span class="title">Strategy</span>
      <span class="value">[[_computeStrategy(change)]]</span>
    </section>
    <section class="hashtag">
      <span class="title">Hashtags</span>
      <span class="value">
        <template is="dom-repeat" items="[[change.hashtags]]">
          <gr-linked-chip
            class="hashtagChip"
            text="[[item]]"
            href="[[_computeHashtagUrl(item)]]"
            removable="[[!_hashtagReadOnly]]"
            on-remove="_handleHashtagRemoved"
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
          ></gr-editable-label>
        </template>
      </span>
    </section>
    <div class="separatedSection">
      <gr-change-requirements
        change="{{change}}"
        account="[[account]]"
        mutable="[[_mutable]]"
      ></gr-change-requirements>
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
  <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
