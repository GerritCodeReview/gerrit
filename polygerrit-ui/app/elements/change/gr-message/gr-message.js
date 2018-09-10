/**
@license
Copyright (C) 2015 The Android Open Source Project

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

import '../../../../@polymer/iron-icon/iron-icon.js';
import '../../shared/gr-account-label/gr-account-label.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-date-formatter/gr-date-formatter.js';
import '../../shared/gr-formatted-text/gr-formatted-text.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/shared-styles.js';
import '../../../styles/gr-voting-styles.js';
import '../gr-comment-list/gr-comment-list.js';

const PATCH_SET_PREFIX_PATTERN = /^Patch Set (\d+):[ ]?/;
const COMMENTS_COUNT_PATTERN = /^\((\d+)( inline)? comments?\)$/;
const LABEL_TITLE_SCORE_PATTERN = /^([A-Za-z0-9-]+)([+-]\d+)$/;

Polymer({
  _template: Polymer.html`
    <style include="gr-voting-styles"></style>
    <style include="shared-styles">
      :host {
        border-bottom: 1px solid var(--border-color);
        display: block;
        position: relative;
        cursor: pointer;
      }
      :host(.expanded) {
        cursor: auto;
      }
      :host > div {
        padding: 0 var(--default-horizontal-margin);
      }
      gr-avatar {
        position: absolute;
        left: var(--default-horizontal-margin);
      }
      .collapsed .contentContainer {
        align-items: baseline;
        color: var(--deemphasized-text-color);
        display: flex;
        white-space: nowrap;
      }
      .contentContainer {
        margin-left: calc(var(--default-horizontal-margin) + 2.5em);
        padding: 10px 0;
      }
      .showAvatar.collapsed .contentContainer {
        margin-left: calc(var(--default-horizontal-margin) + 1.75em);
      }
      .hideAvatar.collapsed .contentContainer,
      .hideAvatar.expanded .contentContainer {
        margin-left: 0;
      }
      .showAvatar.collapsed .contentContainer,
      .hideAvatar.collapsed .contentContainer,
      .hideAvatar.expanded .contentContainer {
        padding: .75em 0;
      }
      .collapsed gr-avatar {
        top: .5em;
        height: 1.75em;
        width: 1.75em;
      }
      .expanded gr-avatar {
        top: 12px;
        height: 2.5em;
        width: 2.5em;
      }
      .name {
        font-family: var(--font-family-bold);
      }
      .message {
        --gr-formatted-text-prose-max-width: 80ch;
      }
      .collapsed .message {
        max-width: none;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .collapsed .author,
      .collapsed .content,
      .collapsed .message,
      .collapsed .updateCategory,
      gr-account-chip {
        display: inline;
      }
      gr-button {
        margin: 0 -4px;
      }
      .collapsed gr-comment-list,
      .collapsed .replyContainer,
      .collapsed .hideOnCollapsed,
      .hideOnOpen {
        display: none;
      }
      .collapsed .hideOnOpen {
        display: block;
      }
      .collapsed .content {
        flex: 1;
        margin-right: .25em;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .collapsed .dateContainer {
        position: static;
      }
      .collapsed .author {
        color: var(--primary-text-color);
        margin-right: .4em;
      }
      .expanded .author {
        cursor: pointer;
        margin-bottom: .4em;
      }
      .dateContainer {
        position: absolute;
        right: var(--default-horizontal-margin);
        top: 10px;
      }
      .date {
        color: var(--deemphasized-text-color);
      }
      .dateContainer iron-icon {
        cursor: pointer;
      }
      .replyContainer {
        padding: .5em 0 0 0;
      }
      .score {
        border: 1px solid rgba(0,0,0,.12);
        border-radius: 3px;
        color: var(--primary-text-color);
        display: inline-block;
        margin: -.1em 0;
        padding: 0 .1em;
      }
      .score.negative {
        background-color: var(--vote-color-disliked);
      }
      .score.negative.min {
        background-color: var(--vote-color-rejected);
      }
      .score.positive {
        background-color: var(--vote-color-recommended);
      }
      .score.positive.max {
        background-color: var(--vote-color-approved);
      }
      gr-account-label {
        --gr-account-label-text-style: {
          font-family: var(--font-family-bold);
        };
      }
    </style>
    <div class\$="[[_computeClass(_expanded, showAvatar, message)]]">
      <gr-avatar account="[[author]]" image-size="100"></gr-avatar>
      <div class="contentContainer">
        <div class="author" on-tap="_handleAuthorTap">
          <span hidden\$="[[!showOnBehalfOf]]">
            <span class="name">[[message.real_author.name]]</span>
            on behalf of
          </span>
          <gr-account-label account="[[author]]" hide-avatar=""></gr-account-label>
          <template is="dom-if" if="[[_successfulParse]]">
            <template is="dom-if" if="[[_parsedVotes.length]]">voted</template>
            <template is="dom-repeat" items="[[_parsedVotes]]" as="score">
              <span class\$="score [[_computeScoreClass(score, labelExtremes)]]">
                [[score.label]] [[score.value]]
              </span>
            </template>
            [[_computeConversationalString(_parsedVotes, _parsedPatchNum, _parsedCommentCount)]]
          </template>
        </div>
        <template is="dom-if" if="[[message.message]]">
          <div class="content">
            <template is="dom-if" if="[[_successfulParse]]">
              <div class="message hideOnOpen">[[_parsedChangeMessage]]</div>
              <gr-formatted-text no-trailing-margin="" class="message hideOnCollapsed" content="[[_parsedChangeMessage]]" config="[[_projectConfig.commentlinks]]"></gr-formatted-text>
            </template>
            <template is="dom-if" if="[[!_successfulParse]]">
              <div class="message hideOnOpen">[[message.message]]</div>
                <gr-formatted-text no-trailing-margin="" class="message hideOnCollapsed" content="[[message.message]]" config="[[_projectConfig.commentlinks]]"></gr-formatted-text>
            </template>
            <div class="replyContainer" hidden\$="[[!showReplyButton]]" hidden="">
              <gr-button link="" small="" on-tap="_handleReplyTap">Reply</gr-button>
            </div>
            <gr-comment-list comments="[[comments]]" change-num="[[changeNum]]" patch-num="[[message._revision_number]]" project-name="[[projectName]]" project-config="[[_projectConfig]]"></gr-comment-list>
          </div>
        </template>
        <template is="dom-if" if="[[_computeIsReviewerUpdate(message)]]">
          <div class="content">
            <template is="dom-repeat" items="[[message.updates]]" as="update">
              <div class="updateCategory">
                [[update.message]]
                <template is="dom-repeat" items="[[update.reviewers]]" as="reviewer">
                  <gr-account-chip account="[[reviewer]]">
                  </gr-account-chip>
                </template>
              </div>
            </template>
          </div>
        </template>
        <span class="dateContainer">
          <template is="dom-if" if="[[!message.id]]">
            <span class="date">
              <gr-date-formatter has-tooltip="" show-date-and-time="" date-str="[[message.date]]"></gr-date-formatter>
            </span>
          </template>
          <template is="dom-if" if="[[message.id]]">
            <a class="date" href\$="[[_computeMessageHash(message)]]" on-tap="_handleLinkTap">
              <gr-date-formatter has-tooltip="" show-date-and-time="" date-str="[[message.date]]"></gr-date-formatter>
            </a>
          </template>
          <iron-icon id="expandToggle" on-tap="_toggleExpanded" title="Toggle expanded state" icon="[[_computeExpandToggleIcon(_expanded)]]">
        </iron-icon></span>
      </div>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-message',

  /**
   * Fired when this message's permalink is tapped.
   *
   * @event scroll-to
   */

  /**
   * Fired when this message's reply link is tapped.
   *
   * @event reply
   */

  listeners: {
    tap: '_handleTap',
  },

  properties: {
    changeNum: Number,
    /** @type {?} */
    message: Object,
    author: {
      type: Object,
      computed: '_computeAuthor(message)',
    },
    comments: {
      type: Object,
      observer: '_commentsChanged',
    },
    config: Object,
    hideAutomated: {
      type: Boolean,
      value: false,
    },
    hidden: {
      type: Boolean,
      computed: '_computeIsHidden(hideAutomated, isAutomated)',
      reflectToAttribute: true,
    },
    isAutomated: {
      type: Boolean,
      computed: '_computeIsAutomated(message)',
    },
    showAvatar: {
      type: Boolean,
      computed: '_computeShowAvatar(author, config)',
    },
    showOnBehalfOf: {
      type: Boolean,
      computed: '_computeShowOnBehalfOf(message)',
    },
    showReplyButton: {
      type: Boolean,
      computed: '_computeShowReplyButton(message, _loggedIn)',
    },
    projectName: {
      type: String,
      observer: '_projectNameChanged',
    },

    /**
     * A mapping from label names to objects representing the minimum and
     * maximum possible values for that label.
     */
    labelExtremes: Object,

    /**
     * @type {{ commentlinks: Array }}
     */
    _projectConfig: Object,
    // Computed property needed to trigger Polymer value observing.
    _expanded: {
      type: Object,
      computed: '_computeExpanded(message.expanded)',
    },
    _loggedIn: {
      type: Boolean,
      value: false,
    },

    _parsedPatchNum: String,
    _parsedCommentCount: String,
    _parsedVotes: Array,
    _parsedChangeMessage: String,
    _successfulParse: Boolean,
  },

  observers: [
    '_updateExpandedClass(message.expanded)',
    '_consumeMessage(message.message)',
  ],

  ready() {
    this.$.restAPI.getConfig().then(config => {
      this.config = config;
    });
    this.$.restAPI.getLoggedIn().then(loggedIn => {
      this._loggedIn = loggedIn;
    });
  },

  _updateExpandedClass(expanded) {
    if (expanded) {
      this.classList.add('expanded');
    } else {
      this.classList.remove('expanded');
    }
  },

  _computeAuthor(message) {
    return message.author || message.updated_by;
  },

  _computeShowAvatar(author, config) {
    return !!(author && config && config.plugin && config.plugin.has_avatars);
  },

  _computeShowOnBehalfOf(message) {
    const author = message.author || message.updated_by;
    return !!(author && message.real_author &&
        author._account_id != message.real_author._account_id);
  },

  _computeShowReplyButton(message, loggedIn) {
    return !!message.message && loggedIn &&
        !this._computeIsAutomated(message);
  },

  _computeExpanded(expanded) {
    return expanded;
  },

  /**
   * If there is no value set on the message object as to whether _expanded
   * should be true or not, then _expanded is set to true if there are
   * inline comments (otherwise false).
   */
  _commentsChanged(value) {
    if (this.message && this.message.expanded === undefined) {
      this.set('message.expanded', Object.keys(value || {}).length > 0);
    }
  },

  _handleTap(e) {
    if (this.message.expanded) { return; }
    e.stopPropagation();
    this.set('message.expanded', true);
  },

  _handleAuthorTap(e) {
    if (!this.message.expanded) { return; }
    e.stopPropagation();
    this.set('message.expanded', false);
  },

  _computeIsAutomated(message) {
    return !!(message.reviewer ||
        this._computeIsReviewerUpdate(message) ||
        (message.tag && message.tag.startsWith('autogenerated')));
  },

  _computeIsHidden(hideAutomated, isAutomated) {
    return hideAutomated && isAutomated;
  },

  _computeIsReviewerUpdate(event) {
    return event.type === 'REVIEWER_UPDATE';
  },

  _computeScoreClass(score, labelExtremes) {
    const classes = [];
    if (score.value > 0) {
      classes.push('positive');
    } else if (score.value < 0) {
      classes.push('negative');
    }
    const extremes = labelExtremes[score.label];
    if (extremes) {
      const intScore = parseInt(score.value, 10);
      if (intScore === extremes.max) {
        classes.push('max');
      } else if (intScore === extremes.min) {
        classes.push('min');
      }
    }
    return classes.join(' ');
  },

  _computeClass(expanded, showAvatar, message) {
    const classes = [];
    classes.push(expanded ? 'expanded' : 'collapsed');
    classes.push(showAvatar ? 'showAvatar' : 'hideAvatar');
    return classes.join(' ');
  },

  _computeMessageHash(message) {
    return '#message-' + message.id;
  },

  _handleLinkTap(e) {
    e.preventDefault();

    this.fire('scroll-to', {message: this.message}, {bubbles: false});

    const hash = this._computeMessageHash(this.message);
    // Don't add the hash to the window history if it's already there.
    // Otherwise you mess up expected back button behavior.
    if (window.location.hash == hash) { return; }
    // Change the URL but don’t trigger a nav event. Otherwise it will
    // reload the page.
    page.show(window.location.pathname + hash, null, false);
  },

  _handleReplyTap(e) {
    e.preventDefault();
    this.fire('reply', {message: this.message});
  },

  _projectNameChanged(name) {
    this.$.restAPI.getProjectConfig(name).then(config => {
      this._projectConfig = config;
    });
  },

  _computeExpandToggleIcon(expanded) {
    return expanded ? 'gr-icons:expand-less' : 'gr-icons:expand-more';
  },

  _toggleExpanded(e) {
    e.stopPropagation();
    this.set('message.expanded', !this.message.expanded);
  },

  /**
   * Attempts to consume a change message to create a shorter and more legible
   * format. If the function encounters unexpected characters at any point, it
   * sets the _successfulParse flag to false and terminates, causing the UI to
   * fall back to displaying the entirety of the change message.
   *
   * A successful parse results in a one-liner that reads:
   * `${AVATAR} voted ${VOTES} and left ${NUM} comment(s) on ${PATCHSET}`
   *
   * @param {string} text
   */
  _consumeMessage(text) {
    // If this variable is defined, the parsing process has already executed.
    if (this._successfulParse !== undefined) { return; }

    this._parsedPatchNum = '';
    this._parsedCommentCount = '';
    this._parsedChangeMessage = '';
    this._parsedVotes = [];
    if (!text) {
      // No message body means nothing to parse.
      this._successfulParse = false;
      return;
    }
    const lines = text.split('\n');
    const messageLines = lines.shift().split(PATCH_SET_PREFIX_PATTERN);
    if (!messageLines[1]) {
      // Message is in an unexpected format.
      this._successfulParse = false;
      return;
    }
    this._parsedPatchNum = messageLines[1];
    if (messageLines[2]) {
      // Content after the colon is always vote information. If it is in the
      // most up to date schema, parse it. Otherwise, cancel the parsing
      // completely.
      let match;
      for (const score of messageLines[2].split(' ')) {
        match = score.match(LABEL_TITLE_SCORE_PATTERN);
        if (!match || match.length !== 3) {
          this._successfulParse = false;
          return;
        }
        this._parsedVotes.push({label: match[1], value: match[2]});
      }
    }
    // Remove empty line.
    lines.shift();
    if (lines.length) {
      const commentMatch = lines[0].match(COMMENTS_COUNT_PATTERN);
      if (commentMatch) {
        this._parsedCommentCount = commentMatch[1];
        // Remove comment line and the following empty line.
        lines.splice(0, 2);
      }
      this._parsedChangeMessage = lines.join('\n');
    }
    this._successfulParse = true;
  },

  _computeConversationalString(votes, patchNum, commentCount) {
    let clause = ' on Patch Set ' + patchNum;
    if (commentCount) {
      let commentStr = ' comment';
      if (parseInt(commentCount, 10) > 1) { commentStr += 's'; }
      clause = ' left ' + commentCount + commentStr + clause;
      if (votes.length) { clause = ' and' + clause; }
    }
    return clause;
  }
});
