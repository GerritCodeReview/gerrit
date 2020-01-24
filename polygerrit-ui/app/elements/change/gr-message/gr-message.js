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
(function() {
  'use strict';

  const PATCH_SET_PREFIX_PATTERN = /^Patch Set \d+: /;
  const LABEL_TITLE_SCORE_PATTERN = /^([A-Za-z0-9-]+)([+-]\d+)$/;

  /**
   * @appliesMixin Gerrit.FireMixin
   * @extends Polymer.Element
   */
  class GrMessage extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-message'; }
    /**
     * Fired when this message's reply link is tapped.
     *
     * @event reply
     */

    /**
     * Fired when the message's timestamp is tapped.
     *
     * @event message-anchor-tap
     */

    static get properties() {
      return {
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
        _messageContent: {
          type: String,
          computed: '_computeMessageContent(message.message)',
        },
        _numberOfComments: {
          type: Number,
          computed: '_computeNumberOfComments(comments)',
        },
        _loggedIn: {
          type: Boolean,
          value: false,
        },
      };
    }

    static get observers() {
      return [
        '_updateExpandedClass(message.expanded)',
      ];
    }

    /** @override */
    created() {
      super.created();
      this.addEventListener('click',
          e => this._handleClick(e));
    }

    /** @override */
    ready() {
      super.ready();
      this.$.restAPI.getConfig().then(config => {
        this.config = config;
      });
      this.$.restAPI.getLoggedIn().then(loggedIn => {
        this._loggedIn = loggedIn;
      });
    }

    _updateExpandedClass(expanded) {
      if (expanded) {
        this.classList.add('expanded');
      } else {
        this.classList.remove('expanded');
      }
    }

    _computeNumberOfComments(comments) {
      if (!comments) return undefined;
      let count = 0;
      for (const file in comments) {
        if (comments.hasOwnProperty(file)) {
          const commentArray = comments[file] || [];
          count += commentArray.length;
        }
      }
      if (count === 0) {
        return undefined;
      } else if (count === 1) {
        return '1 comment';
      } else {
        return count + ' comments';
      }
    }

    _computeMessageContent(content) {
      if (!content) return '';
      const lines = content.split('\n');
      const newLines = [];
      lines.forEach(line => {
        if (line.startsWith('Patch Set ')) return;
        if (line.startsWith('(') && line.endsWith(' comment)')) return;
        if (line.startsWith('(') && line.endsWith(' comments)')) return;
        newLines.push(line);
      });
      return newLines.join('\n').trim();
    }

    _isMessageContentEmpty(content) {
      return this._computeMessageContent(content).trim().length === 0;
    }

    _computeAuthor(message) {
      return message.author || message.updated_by;
    }

    _computeShowOnBehalfOf(message) {
      const author = message.author || message.updated_by;
      return !!(author && message.real_author &&
          author._account_id != message.real_author._account_id);
    }

    _computeShowReplyButton(message, loggedIn) {
      return message && !!message.message && loggedIn &&
          !this._computeIsAutomated(message);
    }

    _computeExpanded(expanded) {
      return expanded;
    }

    /**
     * If there is no value set on the message object as to whether _expanded
     * should be true or not, then _expanded is set to true if there are
     * inline comments (otherwise false).
     */
    _commentsChanged(value) {
      if (this.message && this.message.expanded === undefined) {
        this.set('message.expanded', Object.keys(value || {}).length > 0);
      }
    }

    _handleClick(e) {
      if (this.message.expanded) { return; }
      e.stopPropagation();
      this.set('message.expanded', true);
    }

    _handleAuthorClick(e) {
      if (!this.message.expanded) { return; }
      e.stopPropagation();
      this.set('message.expanded', false);
    }

    _computeIsAutomated(message) {
      return !!(message.reviewer ||
          this._computeIsReviewerUpdate(message) ||
          (message.tag && message.tag.startsWith('autogenerated')));
    }

    _computeIsHidden(hideAutomated, isAutomated) {
      return hideAutomated && isAutomated;
    }

    _computeIsReviewerUpdate(event) {
      return event.type === 'REVIEWER_UPDATE';
    }

    _getScores(message) {
      if (!message.message) { return []; }
      const line = message.message.split('\n', 1)[0];
      const patchSetPrefix = PATCH_SET_PREFIX_PATTERN;
      if (!line.match(patchSetPrefix)) { return []; }
      const scoresRaw = line.split(patchSetPrefix)[1];
      if (!scoresRaw) { return []; }
      return scoresRaw.split(' ')
          .map(s => s.match(LABEL_TITLE_SCORE_PATTERN))
          .filter(ms => ms && ms.length === 3)
          .map(ms => { return {label: ms[1], value: ms[2]}; });
    }

    _computeScoreClass(score, labelExtremes) {
      // Polymer 2: check for undefined
      if ([score, labelExtremes].some(arg => arg === undefined)) {
        return '';
      }
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
    }

    _computeClass(expanded) {
      const classes = [];
      classes.push(expanded ? 'expanded' : 'collapsed');
      return classes.join(' ');
    }

    _handleAnchorClick(e) {
      e.preventDefault();
      this.dispatchEvent(new CustomEvent('message-anchor-tap', {
        bubbles: true,
        composed: true,
        detail: {id: this.message.id},
      }));
    }

    _handleReplyTap(e) {
      e.preventDefault();
      this.fire('reply', {message: this.message});
    }

    _projectNameChanged(name) {
      this.$.restAPI.getProjectConfig(name).then(config => {
        this._projectConfig = config;
      });
    }

    _computeExpandToggleIcon(expanded) {
      return expanded ? 'gr-icons:expand-less' : 'gr-icons:expand-more';
    }

    _toggleExpanded(e) {
      e.stopPropagation();
      this.set('message.expanded', !this.message.expanded);
    }
  }

  customElements.define(GrMessage.is, GrMessage);
})();
