/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import '@polymer/paper-toggle-button/paper-toggle-button.js';
import '../../shared/gr-button/gr-button.js';
import '../gr-message/gr-message.js';
import '../../../styles/shared-styles.js';
import {flush, dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-messages-list_html.js';
import {KeyboardShortcutBehavior} from '../../../behaviors/keyboard-shortcut-behavior/keyboard-shortcut-behavior.js';
import {parseDate} from '../../../utils/date-util.js';
import {appContext} from '../../../services/app-context.js';

const MAX_INITIAL_SHOWN_MESSAGES = 20;
const MESSAGES_INCREMENT = 5;

const ReportingEvent = {
  SHOW_ALL: 'show-all-messages',
  SHOW_MORE: 'show-more-messages',
};

/**
 * The content of the enum is also used in the UI for the button text.
 *
 * @enum {string}
 */
const ExpandAllState = {
  EXPAND_ALL: 'Expand All',
  COLLAPSE_ALL: 'Collapse All',
};

/**
 * @extends PolymerElement
 */
class GrMessagesList extends mixinBehaviors( [
  KeyboardShortcutBehavior,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-messages-list'; }

  static get properties() {
    return {
      changeNum: Number,
      messages: {
        type: Array,
        value() { return []; },
      },
      reviewerUpdates: {
        type: Array,
        value() { return []; },
      },
      changeComments: Object,
      projectName: String,
      showReplyButtons: {
        type: Boolean,
        value: false,
      },
      labels: Object,

      /**
       * Keeps track of the state of the "Expand All" toggle button. Note that
       * you can individually expand/collapse some messages without affecting
       * the toggle button's state.
       *
       * @type {ExpandAllState}
       */
      _expandAllState: {
        type: String,
        value: ExpandAllState.EXPAND_ALL,
      },
      _expandAllTitle: {
        type: String,
        computed: '_computeExpandAllTitle(_expandAllState)',
      },

      _hideAutomated: {
        type: Boolean,
        value: false,
        observer: '_observeHideAutomated',
      },
      /**
       * The messages after processing and including merged reviewer updates.
       */
      _processedMessages: {
        type: Array,
        computed: '_computeItems(messages, reviewerUpdates)',
        observer: '_processedMessagesChanged',
      },
      /**
       * The subset of _processedMessages that is visible to the user.
       */
      _visibleMessages: {
        type: Array,
        value() { return []; },
      },

      _labelExtremes: {
        type: Object,
        computed: '_computeLabelExtremes(labels.*)',
      },
    };
  }

  constructor() {
    super();
    this.reporting = appContext.reportingService;
  }

  scrollToMessage(messageID) {
    let el = this.shadowRoot
        .querySelector('[data-message-id="' + messageID + '"]');
    // If the message is hidden, expand the hidden messages back to that
    // point.
    if (!el) {
      let index;
      for (index = 0; index < this._processedMessages.length; index++) {
        if (this._processedMessages[index].id === messageID) {
          break;
        }
      }
      if (index === this._processedMessages.length) { return; }

      const newMessages = this._processedMessages.slice(index,
          -this._visibleMessages.length);
      // Add newMessages to the beginning of _visibleMessages.
      this.splice(...['_visibleMessages', 0, 0].concat(newMessages));
      // Allow the dom-repeat to stamp.
      flush();
      el = this.shadowRoot
          .querySelector('[data-message-id="' + messageID + '"]');
    }

    el.set('message.expanded', true);
    let top = el.offsetTop;
    for (let offsetParent = el.offsetParent;
      offsetParent;
      offsetParent = offsetParent.offsetParent) {
      top += offsetParent.offsetTop;
    }
    window.scrollTo(0, top);
    this._highlightEl(el);
  }

  _isAutomated(message) {
    return !!(message.reviewer ||
        (message.tag && message.tag.startsWith('autogenerated')));
  }

  _observeHideAutomated(hideAutomated) {
    // We have to call render() such that the dom-repeat filter picks up the
    // change.
    this.$.messageRepeat.render();
  }

  _computeItems(messages, reviewerUpdates) {
    // Polymer 2: check for undefined
    if ([messages, reviewerUpdates].some(arg => arg === undefined)) {
      return [];
    }

    messages = messages || [];
    reviewerUpdates = reviewerUpdates || [];
    let mi = 0;
    let ri = 0;
    let result = [];
    let mDate;
    let rDate;
    for (let i = 0; i < messages.length; i++) {
      messages[i]._index = i;
    }

    while (mi < messages.length || ri < reviewerUpdates.length) {
      if (mi >= messages.length) {
        result = result.concat(reviewerUpdates.slice(ri));
        break;
      }
      if (ri >= reviewerUpdates.length) {
        result = result.concat(messages.slice(mi));
        break;
      }
      mDate = mDate || parseDate(messages[mi].date);
      rDate = rDate || parseDate(reviewerUpdates[ri].date);
      if (rDate < mDate) {
        result.push(reviewerUpdates[ri++]);
        rDate = null;
      } else {
        result.push(messages[mi++]);
        mDate = null;
      }
    }
    result.forEach(m => {
      if (m.expanded === undefined) {
        m.expanded = false;
      }
    });
    return result;
  }

  _updateExpandedStateOfAllMessages(expanded) {
    if (this._processedMessages) {
      for (let i = 0; i < this._processedMessages.length; i++) {
        this._processedMessages[i].expanded = expanded;
      }
    }
    // _visibleMessages is a subarray of _processedMessages
    // _processedMessages contains all items from _visibleMessages
    // At this point all _visibleMessages.expanded values are set,
    // and notifyPath must be used to notify Polymer about changes.
    if (this._visibleMessages) {
      for (let i = 0; i < this._visibleMessages.length; i++) {
        this.notifyPath(`_visibleMessages.${i}.expanded`);
      }
    }
  }

  _computeExpandAllTitle(_expandAllState) {
    if (_expandAllState === ExpandAllState.COLLAPSED_ALL) {
      return this.createTitle(
          this.Shortcut.COLLAPSE_ALL_MESSAGES, this.ShortcutSection.ACTIONS);
    }
    if (_expandAllState === ExpandAllState.EXPAND_ALL) {
      return this.createTitle(
          this.Shortcut.EXPAND_ALL_MESSAGES, this.ShortcutSection.ACTIONS);
    }
    return '';
  }

  _highlightEl(el) {
    const highlightedEls =
        dom(this.root).querySelectorAll('.highlighted');
    for (const highlightedEl of highlightedEls) {
      highlightedEl.classList.remove('highlighted');
    }
    function handleAnimationEnd() {
      el.removeEventListener('animationend', handleAnimationEnd);
      el.classList.remove('highlighted');
    }
    el.addEventListener('animationend', handleAnimationEnd);
    el.classList.add('highlighted');
  }

  /**
   * @param {boolean} expand
   */
  handleExpandCollapse(expand) {
    this._expandAllState = expand ? ExpandAllState.COLLAPSE_ALL
      : ExpandAllState.EXPAND_ALL;
    this._updateExpandedStateOfAllMessages(expand);
  }

  _handleExpandCollapseTap(e) {
    e.preventDefault();
    this.handleExpandCollapse(
        this._expandAllState === ExpandAllState.EXPAND_ALL);
  }

  _handleAnchorClick(e) {
    this.scrollToMessage(e.detail.id);
  }

  _hasAutomatedMessages(messages) {
    if (!messages) { return false; }
    for (const message of messages) {
      if (this._isAutomated(message)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Computes message author's file comments for change's message.
   * Method uses this.messages to find next message and relies on messages
   * to be sorted by date field descending.
   *
   * @param {!Object} changeComments changeComment object, which includes
   *     a method to get all published comments (including robot comments),
   *     which returns a Hash of arrays of comments, filename as key.
   * @param {!Object} message
   * @return {!Object} Hash of arrays of comments, filename as key.
   */
  _computeCommentsForMessage(changeComments, message) {
    if ([changeComments, message].some(arg => arg === undefined)) {
      return {};
    }
    const comments = changeComments.getAllPublishedComments();
    if (message._index === undefined || !comments || !this.messages) {
      return {};
    }
    const messages = this.messages || [];
    const index = message._index;
    const authorId = message.author && message.author._account_id;
    const mDate = parseDate(message.date).getTime();
    // NB: Messages array has oldest messages first.
    let nextMDate;
    if (index > 0) {
      for (let i = index - 1; i >= 0; i--) {
        if (messages[i] && messages[i].author &&
            messages[i].author._account_id === authorId) {
          nextMDate = parseDate(messages[i].date).getTime();
          break;
        }
      }
    }
    const msgComments = {};
    for (const file in comments) {
      if (!comments.hasOwnProperty(file)) { continue; }
      const fileComments = comments[file];
      for (let i = 0; i < fileComments.length; i++) {
        if (fileComments[i].author &&
            fileComments[i].author._account_id !== authorId) {
          continue;
        }
        const cDate = parseDate(fileComments[i].updated).getTime();
        if (cDate <= mDate) {
          if (nextMDate && cDate <= nextMDate) {
            continue;
          }
          msgComments[file] = msgComments[file] || [];
          msgComments[file].push(fileComments[i]);
        }
      }
    }
    return msgComments;
  }

  /**
   * Returns the number of messages to splice to the beginning of
   * _visibleMessages. This is the minimum of the total number of messages
   * remaining in the list and the number of messages needed to display five
   * more visible messages in the list.
   */
  _getDelta(visibleMessages, messages, hideAutomated) {
    if ([visibleMessages, messages].some(arg => arg === undefined)) {
      return 0;
    }

    let delta = MESSAGES_INCREMENT;
    const msgsRemaining = messages.length - visibleMessages.length;

    if (hideAutomated) {
      let counter = 0;
      let i;
      for (i = msgsRemaining; i > 0 && counter < MESSAGES_INCREMENT; i--) {
        if (!this._isAutomated(messages[i - 1])) { counter++; }
      }
      delta = msgsRemaining - i;
    }
    return Math.min(msgsRemaining, delta);
  }

  /**
   * Gets the number of messages that would be visible, but do not currently
   * exist in _visibleMessages.
   */
  _numRemaining(visibleMessages, messages, hideAutomated) {
    if ([visibleMessages, messages].some(arg => arg === undefined)) {
      return 0;
    }

    if (hideAutomated) {
      return this._getHumanMessages(messages).length -
          this._getHumanMessages(visibleMessages).length;
    }
    return messages.length - visibleMessages.length;
  }

  _computeIncrementText(visibleMessages, messages, hideAutomated) {
    let delta = this._getDelta(visibleMessages, messages, hideAutomated);
    delta = Math.min(
        this._numRemaining(visibleMessages, messages, hideAutomated), delta);
    return 'Show ' + Math.min(MESSAGES_INCREMENT, delta) + ' more';
  }

  _getHumanMessages(messages) {
    return messages.filter(msg => !this._isAutomated(msg));
  }

  _computeShowHideTextHidden(visibleMessages, messages,
      hideAutomated) {
    if ([visibleMessages, messages].some(arg => arg === undefined)) {
      return 0;
    }

    if (hideAutomated) {
      messages = this._getHumanMessages(messages);
      visibleMessages = this._getHumanMessages(visibleMessages);
    }
    return visibleMessages.length >= messages.length;
  }

  _handleShowAllTap() {
    this._visibleMessages = this._processedMessages;
    this.reporting.reportInteraction(ReportingEvent.SHOW_ALL);
  }

  _handleIncrementShownMessages() {
    const delta = this._getDelta(this._visibleMessages,
        this._processedMessages, this._hideAutomated);
    const len = this._visibleMessages.length;
    const newMessages = this._processedMessages.slice(-(len + delta), -len);
    // Add newMessages to the beginning of _visibleMessages
    this.splice(...['_visibleMessages', 0, 0].concat(newMessages));
    this.reporting.reportInteraction(ReportingEvent.SHOW_MORE);
  }

  _processedMessagesChanged(messages) {
    if (messages) {
      this._visibleMessages = messages.slice(-MAX_INITIAL_SHOWN_MESSAGES);

      if (messages.length === 0) return;
      const tags = messages.map(message => message.tag || message.type ||
          (message.comments ? 'comments' : 'none'));
      const tagsCounted = tags.reduce((acc, val) => {
        acc[val] = (acc[val] || 0) + 1;
        return acc;
      }, {all: messages.length});
      this.reporting.reportInteraction('messages-count', tagsCounted);
    }
  }

  _computeNumMessagesText(visibleMessages, messages,
      hideAutomated) {
    const total =
        this._numRemaining(visibleMessages, messages, hideAutomated);
    return total === 1 ? 'Show 1 message' : 'Show all ' + total + ' messages';
  }

  _computeIncrementHidden(visibleMessages, messages,
      hideAutomated) {
    const total =
        this._numRemaining(visibleMessages, messages, hideAutomated);
    return total <= this._getDelta(visibleMessages, messages, hideAutomated);
  }

  /**
   * Compute a mapping from label name to objects representing the minimum and
   * maximum possible values for that label.
   */
  _computeLabelExtremes(labelRecord) {
    const extremes = {};
    const labels = labelRecord.base;
    if (!labels) { return extremes; }
    for (const key of Object.keys(labels)) {
      if (!labels[key] || !labels[key].values) { continue; }
      const values = Object.keys(labels[key].values)
          .map(v => parseInt(v, 10));
      values.sort((a, b) => a - b);
      if (!values.length) { continue; }
      extremes[key] = {min: values[0], max: values[values.length - 1]};
    }
    return extremes;
  }
}

customElements.define(GrMessagesList.is, GrMessagesList);
