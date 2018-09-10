/**
@license
Copyright (C) 2016 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/rest-client-behavior/rest-client-behavior.js';
import '../../../styles/shared-styles.js';
import '../../../styles/gr-voting-styles.js';
import '../../core/gr-navigation/gr-navigation.js';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../../plugins/gr-endpoint-param/gr-endpoint-param.js';
import '../../plugins/gr-external-style/gr-external-style.js';
import '../../shared/gr-account-chip/gr-account-chip.js';
import '../../shared/gr-account-link/gr-account-link.js';
import '../../shared/gr-date-formatter/gr-date-formatter.js';
import '../../shared/gr-editable-label/gr-editable-label.js';
import '../../shared/gr-limited-text/gr-limited-text.js';
import '../../shared/gr-linked-chip/gr-linked-chip.js';
import '../../shared/gr-tooltip-content/gr-tooltip-content.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-change-requirements/gr-change-requirements.js';
import '../gr-commit-info/gr-commit-info.js';
import '../gr-reviewer-list/gr-reviewer-list.js';

const HASHTAG_ADD_MESSAGE = 'Add Hashtag';

const SubmitTypeLabel = {
  FAST_FORWARD_ONLY: 'Fast Forward Only',
  MERGE_IF_NECESSARY: 'Merge if Necessary',
  REBASE_IF_NECESSARY: 'Rebase if Necessary',
  MERGE_ALWAYS: 'Always Merge',
  REBASE_ALWAYS: 'Rebase Always',
  CHERRY_PICK: 'Cherry Pick',
};

const NOT_CURRENT_MESSAGE = 'Not current - rebase possible';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        display: table;
      }
      section {
        display: table-row;
      }
      section:not(:first-of-type) .title,
      section:not(:first-of-type) .value {
        padding-top: .5em;
      }
      section:not(:first-of-type) {
        margin-top: 1em;
      }
      .title,
      .value {
        display: table-cell;
      }
      .title {
        color: var(--deemphasized-text-color);
        font-family: var(--font-family-bold);
        max-width: 20em;
        padding-left: var(--metadata-horizontal-padding);
        padding-right: .5em;
        word-break: break-word;
      }
      .value {
        padding-right: var(--metadata-horizontal-padding);
      }
      gr-change-requirements {
        --requirements-horizontal-padding: var(--metadata-horizontal-padding);
      }
      gr-account-link {
        max-width: 20ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: middle;
        white-space: nowrap;
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
        margin-bottom: .5em;
      }
      #externalStyle {
        display: block;
      }
      .parentList.merge {
        list-style-type: decimal;
        padding-left: 1em;
      }
      .parentList gr-commit-info {
        display: inline-block;
      }
      .hideDisplay,
      #parentNotCurrentMessage {
        display: none;
      }
      .parentList.notCurrent.nonMerge #parentNotCurrentMessage {
        --arrow-color: #ffa62f;
        display: inline-block;
      }
      .separatedSection {
        border-top: 1px solid var(--border-color);
        margin-top: .5em;
        padding: .5em 0;
      }
    </style>
    <gr-external-style id="externalStyle" name="change-metadata">
      <section>
        <span class="title">Updated</span>
        <span class="value">
          <gr-date-formatter has-tooltip="" date-str="[[change.updated]]"></gr-date-formatter>
        </span>
      </section>
      <section>
        <span class="title">Owner</span>
        <span class="value">
          <gr-account-link account="[[change.owner]]"></gr-account-link>
        </span>
      </section>
      <section class\$="[[_computeShowUploaderHide(change)]]">
        <span class="title">Uploader</span>
        <span class="value">
          <gr-account-link account="[[_computeShowUploader(change)]]"></gr-account-link>
        </span>
      </section>
      <section class="assignee">
        <span class="title">Assignee</span>
        <span class="value">
          <gr-account-list max-count="1" id="assigneeValue" placeholder="Set assignee..." accounts="{{_assignee}}" change="[[change]]" readonly="[[_computeAssigneeReadOnly(_mutable, change)]]" allow-any-user=""></gr-account-list>
        </span>
      </section>
      <template is="dom-if" if="[[_showReviewersByState]]">
        <section>
          <span class="title">Reviewers</span>
          <span class="value">
            <gr-reviewer-list change="{{change}}" mutable="[[_mutable]]" reviewers-only="" max-reviewers-displayed="3"></gr-reviewer-list>
          </span>
        </section>
        <section>
          <span class="title">CC</span>
          <span class="value">
            <gr-reviewer-list change="{{change}}" mutable="[[_mutable]]" ccs-only="" max-reviewers-displayed="3"></gr-reviewer-list>
          </span>
        </section>
      </template>
      <template is="dom-if" if="[[!_showReviewersByState]]">
        <section>
          <span class="title">Reviewers</span>
          <span class="value">
            <gr-reviewer-list change="{{change}}" mutable="[[_mutable]]"></gr-reviewer-list>
          </span>
        </section>
      </template>
      <section>
        <span class="title">Repo</span>
        <span class="value">
          <a href\$="[[_computeProjectURL(change.project)]]">
            <gr-limited-text limit="40" text="[[change.project]]"></gr-limited-text>
          </a>
        </span>
      </section>
      <section>
        <span class="title">Branch</span>
        <span class="value">
          <a href\$="[[_computeBranchURL(change.project, change.branch)]]">
            <gr-limited-text limit="40" text="[[change.branch]]"></gr-limited-text>
          </a>
        </span>
      </section>
      <section>
        <span class="title">[[_computeParentsLabel(_currentParents)]]</span>
        <span class="value">
          <ol class\$="[[_computeParentListClass(_currentParents, parentIsCurrent)]]">
            <template is="dom-repeat" items="[[_currentParents]]" as="parent">
              <li>
                <gr-commit-info change="[[change]]" commit-info="[[parent]]" server-config="[[serverConfig]]"></gr-commit-info>
                <gr-tooltip-content id="parentNotCurrentMessage" has-tooltip="" show-icon="" title\$="[[_notCurrentMessage]]"></gr-tooltip-content>
              </li>
            </template>
          </ol>
        </span>
      </section>
      <section class="topic">
        <span class="title">Topic</span>
        <span class="value">
          <template is="dom-if" if="[[_showTopicChip(change.*, _settingTopic)]]">
            <gr-linked-chip text="[[change.topic]]" limit="40" href="[[_computeTopicURL(change.topic)]]" removable="[[!_topicReadOnly]]" on-remove="_handleTopicRemoved"></gr-linked-chip>
          </template>
          <template is="dom-if" if="[[_showAddTopic(change.*, _settingTopic)]]">
            <gr-editable-label label-text="Add a topic" value="[[change.topic]]" max-length="1024" placeholder="[[_computeTopicPlaceholder(_topicReadOnly)]]" read-only="[[_topicReadOnly]]" on-changed="_handleTopicChanged"></gr-editable-label>
          </template>
        </span>
      </section>
      <section class="strategy" hidden\$="[[_computeHideStrategy(change)]]" hidden="">
        <span class="title">Strategy</span>
        <span class="value">[[_computeStrategy(change)]]</span>
      </section>
      <template is="dom-if" if="[[serverConfig.note_db_enabled]]">
        <section class="hashtag">
          <span class="title">Hashtags</span>
          <span class="value">
            <template is="dom-repeat" items="[[change.hashtags]]">
              <gr-linked-chip class="hashtagChip" text="[[item]]" href="[[_computeHashtagURL(item)]]" removable="[[!_hashtagReadOnly]]" on-remove="_handleHashtagRemoved">
              </gr-linked-chip>
            </template>
            <template is="dom-if" if="[[!_hashtagReadOnly]]">
              <gr-editable-label uppercase="" label-text="Add a hashtag" value="{{_newHashtag}}" placeholder="[[_computeHashtagPlaceholder(_hashtagReadOnly)]]" read-only="[[_hashtagReadOnly]]" on-changed="_handleHashtagChanged"></gr-editable-label>
            </template>
          </span>
        </section>
      </template>
      <div class="separatedSection">
        <gr-change-requirements change="{{change}}" account="[[account]]" mutable="[[_mutable]]"></gr-change-requirements>
      </div>
      <section id="webLinks" hidden\$="[[!_computeWebLinks(commitInfo)]]">
        <span class="title">Links</span>
        <span class="value">
          <template is="dom-repeat" items="[[_computeWebLinks(commitInfo)]]" as="link">
            <a href="[[link.url]]" class="webLink" rel="noopener" target="_blank">
              [[link.name]]
            </a>
          </template>
        </span>
      </section>
      <gr-endpoint-decorator name="change-metadata-item">
        <gr-endpoint-param name="labels" value="[[labels]]"></gr-endpoint-param>
        <gr-endpoint-param name="change" value="[[change]]"></gr-endpoint-param>
        <gr-endpoint-param name="revision" value="[[revision]]"></gr-endpoint-param>
      </gr-endpoint-decorator>
    </gr-external-style>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-change-metadata',

  /**
   * Fired when the change topic is changed.
   *
   * @event topic-changed
   */

  properties: {
    /** @type {?} */
    change: Object,
    labels: {
      type: Object,
      notify: true,
    },
    account: Object,
    /** @type {?} */
    revision: Object,
    commitInfo: Object,
    _mutable: {
      type: Boolean,
      computed: '_computeIsMutable(account)',
    },
    /**
     * @type {{ note_db_enabled: string }}
     */
    serverConfig: Object,
    parentIsCurrent: Boolean,
    _notCurrentMessage: {
      type: String,
      value: NOT_CURRENT_MESSAGE,
      readOnly: true,
    },
    _topicReadOnly: {
      type: Boolean,
      computed: '_computeTopicReadOnly(_mutable, change)',
    },
    _hashtagReadOnly: {
      type: Boolean,
      computed: '_computeHashtagReadOnly(_mutable, change)',
    },
    _showReviewersByState: {
      type: Boolean,
      computed: '_computeShowReviewersByState(serverConfig)',
    },
    _showRequirements: {
      type: Boolean,
      computed: '_computeShowRequirements(change)',
    },

    _assignee: Array,
    _isWip: {
      type: Boolean,
      computed: '_computeIsWip(change)',
    },
    _newHashtag: String,

    _settingTopic: {
      type: Boolean,
      value: false,
    },

    _currentParents: {
      type: Array,
      computed: '_computeParents(change)',
    },
  },

  behaviors: [
    Gerrit.RESTClientBehavior,
  ],

  observers: [
    '_changeChanged(change)',
    '_labelsChanged(change.labels)',
    '_assigneeChanged(_assignee.*)',
  ],

  _labelsChanged(labels) {
    this.labels = Object.assign({}, labels) || null;
  },

  _changeChanged(change) {
    this._assignee = change.assignee ? [change.assignee] : [];
  },

  _assigneeChanged(assigneeRecord) {
    if (!this.change) { return; }
    const assignee = assigneeRecord.base;
    if (assignee.length) {
      const acct = assignee[0];
      if (this.change.assignee &&
          acct._account_id === this.change.assignee._account_id) { return; }
      this.set(['change', 'assignee'], acct);
      this.$.restAPI.setAssignee(this.change._number, acct._account_id);
    } else {
      if (!this.change.assignee) { return; }
      this.set(['change', 'assignee'], undefined);
      this.$.restAPI.deleteAssignee(this.change._number);
    }
  },

  _computeHideStrategy(change) {
    return !this.changeIsOpen(change.status);
  },

  /**
   * @param {Object} commitInfo
   * @return {?Array} If array is empty, returns null instead so
   * an existential check can be used to hide or show the webLinks
   * section.
   */
  _computeWebLinks(commitInfo) {
    if (!commitInfo) { return null; }
    const weblinks = Gerrit.Nav.getChangeWeblinks(
        this.change ? this.change.repo : '',
        commitInfo.commit,
        {weblinks: commitInfo.web_links});
    return weblinks.length ? weblinks : null;
  },

  _computeStrategy(change) {
    return SubmitTypeLabel[change.submit_type];
  },

  _computeLabelNames(labels) {
    return Object.keys(labels).sort();
  },

  _handleTopicChanged(e, topic) {
    const lastTopic = this.change.topic;
    if (!topic.length) { topic = null; }
    this._settingTopic = true;
    this.$.restAPI.setChangeTopic(this.change._number, topic)
        .then(newTopic => {
          this._settingTopic = false;
          this.set(['change', 'topic'], newTopic);
          if (newTopic !== lastTopic) {
            this.dispatchEvent(
                new CustomEvent('topic-changed', {bubbles: true}));
          }
        });
  },

  _showAddTopic(changeRecord, settingTopic) {
    const hasTopic = !!changeRecord && !!changeRecord.base.topic;
    return !hasTopic && !settingTopic;
  },

  _showTopicChip(changeRecord, settingTopic) {
    const hasTopic = !!changeRecord && !!changeRecord.base.topic;
    return hasTopic && !settingTopic;
  },

  _handleHashtagChanged(e) {
    const lastHashtag = this.change.hashtag;
    if (!this._newHashtag.length) { return; }
    const newHashtag = this._newHashtag;
    this._newHashtag = '';
    this.$.restAPI.setChangeHashtag(
        this.change._number, {add: [newHashtag]}).then(newHashtag => {
          this.set(['change', 'hashtags'], newHashtag);
          if (newHashtag !== lastHashtag) {
            this.dispatchEvent(
                new CustomEvent('hashtag-changed', {bubbles: true}));
          }
        });
  },

  _computeTopicReadOnly(mutable, change) {
    return !mutable ||
        !change.actions ||
        !change.actions.topic ||
        !change.actions.topic.enabled;
  },

  _computeHashtagReadOnly(mutable, change) {
    return !mutable ||
        !change.actions ||
        !change.actions.hashtags ||
        !change.actions.hashtags.enabled;
  },

  _computeAssigneeReadOnly(mutable, change) {
    return !mutable ||
        !change.actions ||
        !change.actions.assignee ||
        !change.actions.assignee.enabled;
  },

  _computeTopicPlaceholder(_topicReadOnly) {
    // Action items in Material Design are uppercase -- placeholder label text
    // is sentence case.
    return _topicReadOnly ? 'No topic' : 'ADD TOPIC';
  },

  _computeHashtagPlaceholder(_hashtagReadOnly) {
    return _hashtagReadOnly ? '' : HASHTAG_ADD_MESSAGE;
  },

  _computeShowReviewersByState(serverConfig) {
    return !!serverConfig.note_db_enabled;
  },

  _computeShowRequirements(change) {
    if (change.status !== this.ChangeStatus.NEW) {
      // TODO(maximeg) change this to display the stored
      // requirements, once it is implemented server-side.
      return false;
    }
    const hasRequirements = !!change.requirements &&
        Object.keys(change.requirements).length > 0;
    const hasLabels = !!change.labels &&
        Object.keys(change.labels).length > 0;
    return hasRequirements || hasLabels || !!change.work_in_progress;
  },

  _computeProjectURL(project) {
    return Gerrit.Nav.getUrlForProjectChanges(project);
  },

  _computeBranchURL(project, branch) {
    return Gerrit.Nav.getUrlForBranch(branch, project,
        this.change.status == this.ChangeStatus.NEW ? 'open' :
            this.change.status.toLowerCase());
  },

  _computeTopicURL(topic) {
    return Gerrit.Nav.getUrlForTopic(topic);
  },

  _computeHashtagURL(hashtag) {
    return Gerrit.Nav.getUrlForHashtag(hashtag);
  },

  _handleTopicRemoved(e) {
    const target = Polymer.dom(e).rootTarget;
    target.disabled = true;
    this.$.restAPI.setChangeTopic(this.change._number, null).then(() => {
      target.disabled = false;
      this.set(['change', 'topic'], '');
      this.dispatchEvent(
          new CustomEvent('topic-changed', {bubbles: true}));
    }).catch(err => {
      target.disabled = false;
      return;
    });
  },

  _handleHashtagRemoved(e) {
    e.preventDefault();
    const target = Polymer.dom(e).rootTarget;
    target.disabled = true;
    this.$.restAPI.setChangeHashtag(this.change._number,
        {remove: [target.text]})
        .then(newHashtag => {
          target.disabled = false;
          this.set(['change', 'hashtags'], newHashtag);
        }).catch(err => {
          target.disabled = false;
          return;
        });
  },

  _computeIsWip(change) {
    return !!change.work_in_progress;
  },

  _computeShowUploaderHide(change) {
    return this._computeShowUploader(change) ? '' : 'hideDisplay';
  },

  _computeShowUploader(change) {
    if (!change.current_revision ||
        !change.revisions[change.current_revision]) {
      return null;
    }

    const rev = change.revisions[change.current_revision];

    if (!rev || !rev.uploader ||
      change.owner._account_id === rev.uploader._account_id) {
      return null;
    }

    return rev.uploader;
  },

  _computeParents(change) {
    if (!change.current_revision ||
        !change.revisions[change.current_revision] ||
        !change.revisions[change.current_revision].commit) {
      return undefined;
    }
    return change.revisions[change.current_revision].commit.parents;
  },

  _computeParentsLabel(parents) {
    return parents.length > 1 ? 'Parents' : 'Parent';
  },

  _computeParentListClass(parents, parentIsCurrent) {
    return [
      'parentList',
      parents.length > 1 ? 'merge' : 'nonMerge',
      parentIsCurrent ? 'current' : 'notCurrent',
    ].join(' ');
  },

  _computeIsMutable(account) {
    return !!Object.keys(account).length;
  }
});
