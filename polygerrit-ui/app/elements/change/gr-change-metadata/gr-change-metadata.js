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
import '../../../styles/shared-styles.js';
import '../../../styles/gr-change-metadata-shared-styles.js';
import '../../../styles/gr-change-view-integration-shared-styles.js';
import '../../../styles/gr-voting-styles.js';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../../plugins/gr-endpoint-param/gr-endpoint-param.js';
import '../../plugins/gr-external-style/gr-external-style.js';
import '../../shared/gr-account-chip/gr-account-chip.js';
import '../../shared/gr-account-link/gr-account-link.js';
import '../../shared/gr-date-formatter/gr-date-formatter.js';
import '../../shared/gr-editable-label/gr-editable-label.js';
import '../../shared/gr-icons/gr-icons.js';
import '../../shared/gr-limited-text/gr-limited-text.js';
import '../../shared/gr-linked-chip/gr-linked-chip.js';
import '../../shared/gr-tooltip-content/gr-tooltip-content.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-change-requirements/gr-change-requirements.js';
import '../gr-commit-info/gr-commit-info.js';
import '../gr-reviewer-list/gr-reviewer-list.js';
import '../../shared/gr-account-list/gr-account-list.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-change-metadata_html.js';
import {GrReviewerSuggestionsProvider, SUGGESTIONS_PROVIDERS_USERS_TYPES} from '../../../scripts/gr-reviewer-suggestions-provider/gr-reviewer-suggestions-provider.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {ChangeStatus} from '../../../constants/constants.js';
import {changeIsOpen} from '../../../utils/change-util.js';

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

/**
 * @enum {string}
 */
const CertificateStatus = {
  /**
   * This certificate status is bad.
   */
  BAD: 'BAD',
  /**
   * This certificate status is OK.
   */
  OK: 'OK',
  /**
   * This certificate status is TRUSTED.
   */
  TRUSTED: 'TRUSTED',
};

/**
 * @extends PolymerElement
 */
class GrChangeMetadata extends GestureEventListeners(
    LegacyElementMixin(PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-change-metadata'; }
  /**
   * Fired when the change topic is changed.
   *
   * @event topic-changed
   */

  static get properties() {
    return {
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
      /** @type {?} */
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
      /**
       * @type {Gerrit.PushCertificateValidation}
       */
      _pushCertificateValidation: {
        type: Object,
        computed: '_computePushCertificateValidation(serverConfig, change)',
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
        computed: '_computeParents(change, revision)',
      },

      /** @type {?} */
      _CHANGE_ROLE: {
        type: Object,
        readOnly: true,
        value: {
          OWNER: 'owner',
          UPLOADER: 'uploader',
          AUTHOR: 'author',
          COMMITTER: 'committer',
        },
      },
    };
  }

  static get observers() {
    return [
      '_changeChanged(change)',
      '_labelsChanged(change.labels)',
      '_assigneeChanged(_assignee.*)',
    ];
  }

  _labelsChanged(labels) {
    this.labels = ({...labels}) || null;
  }

  _changeChanged(change) {
    this._assignee = change.assignee ? [change.assignee] : [];
  }

  _assigneeChanged(assigneeRecord) {
    if (!this.change || !this._isAssigneeEnabled(this.serverConfig)) {
      return;
    }
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
  }

  _computeHideStrategy(change) {
    return !changeIsOpen(change);
  }

  /**
   * @param {Object} commitInfo
   * @return {?Array} If array is empty, returns null instead so
   * an existential check can be used to hide or show the webLinks
   * section.
   */
  _computeWebLinks(commitInfo, serverConfig) {
    if (!commitInfo) { return null; }
    const weblinks = GerritNav.getChangeWeblinks(
        this.change ? this.change.repo : '',
        commitInfo.commit,
        {
          weblinks: commitInfo.web_links,
          config: serverConfig,
        });
    return weblinks.length ? weblinks : null;
  }

  _isAssigneeEnabled(serverConfig) {
    return serverConfig && serverConfig.change
        && !!serverConfig.change.enable_assignee;
  }

  _computeStrategy(change) {
    return SubmitTypeLabel[change.submit_type];
  }

  _computeLabelNames(labels) {
    return Object.keys(labels).sort();
  }

  _handleTopicChanged(e, topic) {
    const lastTopic = this.change.topic;
    if (!topic.length) { topic = null; }
    this._settingTopic = true;
    this.$.restAPI.setChangeTopic(this.change._number, topic)
        .then(newTopic => {
          this._settingTopic = false;
          this.set(['change', 'topic'], newTopic);
          if (newTopic !== lastTopic) {
            this.dispatchEvent(new CustomEvent(
                'topic-changed', {bubbles: true, composed: true}));
          }
        });
  }

  _showAddTopic(changeRecord, settingTopic) {
    const hasTopic = !!changeRecord &&
        !!changeRecord.base && !!changeRecord.base.topic;
    return !hasTopic && !settingTopic;
  }

  _showTopicChip(changeRecord, settingTopic) {
    const hasTopic = !!changeRecord &&
        !!changeRecord.base && !!changeRecord.base.topic;
    return hasTopic && !settingTopic;
  }

  _showCherryPickOf(changeRecord) {
    const hasCherryPickOf = !!changeRecord &&
        !!changeRecord.base && !!changeRecord.base.cherry_pick_of_change &&
        !!changeRecord.base.cherry_pick_of_patch_set;
    return hasCherryPickOf;
  }

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
            new CustomEvent('hashtag-changed', {
              bubbles: true, composed: true}));
      }
    });
  }

  _computeTopicReadOnly(mutable, change) {
    return !mutable ||
        !change ||
        !change.actions ||
        !change.actions.topic ||
        !change.actions.topic.enabled;
  }

  _computeHashtagReadOnly(mutable, change) {
    return !mutable ||
        !change ||
        !change.actions ||
        !change.actions.hashtags ||
        !change.actions.hashtags.enabled;
  }

  _computeAssigneeReadOnly(mutable, change) {
    return !mutable ||
        !change ||
        !change.actions ||
        !change.actions.assignee ||
        !change.actions.assignee.enabled;
  }

  _computeTopicPlaceholder(_topicReadOnly) {
    // Action items in Material Design are uppercase -- placeholder label text
    // is sentence case.
    return _topicReadOnly ? 'No topic' : 'ADD TOPIC';
  }

  _computeHashtagPlaceholder(_hashtagReadOnly) {
    return _hashtagReadOnly ? '' : HASHTAG_ADD_MESSAGE;
  }

  _computeShowRequirements(change) {
    if (change.status !== ChangeStatus.NEW) {
      // TODO(maximeg) change this to display the stored
      // requirements, once it is implemented server-side.
      return false;
    }
    const hasRequirements = !!change.requirements &&
        Object.keys(change.requirements).length > 0;
    const hasLabels = !!change.labels &&
        Object.keys(change.labels).length > 0;
    return hasRequirements || hasLabels || !!change.work_in_progress;
  }

  /**
   * @return {?Gerrit.PushCertificateValidation} object representing data for
   *     the push validation.
   */
  _computePushCertificateValidation(serverConfig, change) {
    if (!change || !serverConfig || !serverConfig.receive ||
        !serverConfig.receive.enable_signed_push) {
      return null;
    }
    const rev = change.revisions[change.current_revision];
    if (!rev.push_certificate || !rev.push_certificate.key) {
      return {
        class: 'help',
        icon: 'gr-icons:help',
        message: 'This patch set was created without a push certificate',
      };
    }

    const key = rev.push_certificate.key;
    switch (key.status) {
      case CertificateStatus.BAD:
        return {
          class: 'invalid',
          icon: 'gr-icons:close',
          message: this._problems('Push certificate is invalid', key),
        };
      case CertificateStatus.OK:
        return {
          class: 'notTrusted',
          icon: 'gr-icons:info',
          message: this._problems(
              'Push certificate is valid, but key is not trusted', key),
        };
      case CertificateStatus.TRUSTED:
        return {
          class: 'trusted',
          icon: 'gr-icons:check',
          message: this._problems(
              'Push certificate is valid and key is trusted', key),
        };
      default:
        throw new Error(`unknown certificate status: ${key.status}`);
    }
  }

  _problems(msg, key) {
    if (!key || !key.problems || key.problems.length === 0) {
      return msg;
    }

    return [msg + ':'].concat(key.problems).join('\n');
  }

  _computeShowRepoBranchTogether(repo, branch) {
    return !!repo && !!branch && repo.length + branch.length < 40;
  }

  _computeProjectUrl(project) {
    return GerritNav.getUrlForProjectChanges(project);
  }

  _computeBranchUrl(project, branch) {
    if (!this.change || !this.change.status) return '';
    return GerritNav.getUrlForBranch(branch, project,
        this.change.status == ChangeStatus.NEW ? 'open' :
          this.change.status.toLowerCase());
  }

  _computeCherryPickOfUrl(change, patchset, project) {
    return GerritNav.getUrlForChangeById(change, project, patchset);
  }

  _computeTopicUrl(topic) {
    return GerritNav.getUrlForTopic(topic);
  }

  _computeHashtagUrl(hashtag) {
    return GerritNav.getUrlForHashtag(hashtag);
  }

  _handleTopicRemoved(e) {
    const target = dom(e).rootTarget;
    target.disabled = true;
    this.$.restAPI.setChangeTopic(this.change._number, null)
        .then(() => {
          target.disabled = false;
          this.set(['change', 'topic'], '');
          this.dispatchEvent(
              new CustomEvent('topic-changed',
                  {bubbles: true, composed: true}));
        })
        .catch(err => {
          target.disabled = false;
          return;
        });
  }

  _handleHashtagRemoved(e) {
    e.preventDefault();
    const target = dom(e).rootTarget;
    target.disabled = true;
    this.$.restAPI.setChangeHashtag(this.change._number,
        {remove: [target.text]})
        .then(newHashtag => {
          target.disabled = false;
          this.set(['change', 'hashtags'], newHashtag);
        })
        .catch(err => {
          target.disabled = false;
          return;
        });
  }

  _computeIsWip(change) {
    return !!change.work_in_progress;
  }

  _computeShowRoleClass(change, role) {
    return this._getNonOwnerRole(change, role) ? '' : 'hideDisplay';
  }

  /**
   * Get the user with the specified role on the change. Returns null if the
   * user with that role is the same as the owner.
   *
   * @param {!Object} change
   * @param {string} role One of the values from _CHANGE_ROLE
   * @return {Object|null} either an account or null.
   */
  _getNonOwnerRole(change, role) {
    if (!change || !change.current_revision ||
        !change.revisions[change.current_revision]) {
      return null;
    }

    const rev = change.revisions[change.current_revision];
    if (!rev) { return null; }

    if (role === this._CHANGE_ROLE.UPLOADER &&
        rev.uploader &&
        change.owner._account_id !== rev.uploader._account_id) {
      return rev.uploader;
    }

    if (role === this._CHANGE_ROLE.AUTHOR &&
        rev.commit && rev.commit.author &&
        change.owner.email !== rev.commit.author.email) {
      return rev.commit.author;
    }

    if (role === this._CHANGE_ROLE.COMMITTER &&
        rev.commit && rev.commit.committer &&
        change.owner.email !== rev.commit.committer.email) {
      return rev.commit.committer;
    }

    return null;
  }

  _computeParents(change, revision) {
    if (!revision || !revision.commit) {
      if (!change || !change.current_revision) { return []; }
      revision = change.revisions[change.current_revision];
      if (!revision || !revision.commit) { return []; }
    }
    return revision.commit.parents;
  }

  _computeParentsLabel(parents) {
    return parents && parents.length > 1 ? 'Parents' : 'Parent';
  }

  _computeParentListClass(parents, parentIsCurrent) {
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

  _computeIsMutable(account) {
    return !!Object.keys(account).length;
  }

  editTopic() {
    if (this._topicReadOnly || this.change.topic) { return; }
    // Cannot use `this.$.ID` syntax because the element exists inside of a
    // dom-if.
    this.shadowRoot.querySelector('.topicEditableLabel').open();
  }

  _getReviewerSuggestionsProvider(change) {
    const provider = GrReviewerSuggestionsProvider.create(this.$.restAPI,
        change._number, SUGGESTIONS_PROVIDERS_USERS_TYPES.ANY);
    provider.init();
    return provider;
  }
}

customElements.define(GrChangeMetadata.is, GrChangeMetadata);
