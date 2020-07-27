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
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/shared-styles.js';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../../plugins/gr-endpoint-param/gr-endpoint-param.js';
import '../../plugins/gr-endpoint-slot/gr-endpoint-slot.js';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-related-changes-list_html.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {ChangeStatus} from '../../../constants/constants.js';
import {patchNumEquals} from '../../../utils/patch-set-util.js';
import {changeIsOpen} from '../../../utils/change-util.js';
import {pluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints.js';

/**
 * @extends PolymerElement
 */
class GrRelatedChangesList extends GestureEventListeners(
    LegacyElementMixin(PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-related-changes-list'; }
  /**
   * Fired when a new section is loaded so that the change view can determine
   * a show more button is needed, sometimes before all the sections finish
   * loading.
   *
   * @event new-section-loaded
   */

  static get properties() {
    return {
      change: Object,
      hasParent: {
        type: Boolean,
        notify: true,
        value: false,
      },
      patchNum: String,
      parentChange: Object,
      hidden: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
      loading: {
        type: Boolean,
        notify: true,
      },
      mergeable: Boolean,
      _connectedRevisions: {
        type: Array,
        computed: '_computeConnectedRevisions(change, patchNum, ' +
          '_relatedResponse.changes)',
      },
      /** @type {?} */
      _relatedResponse: {
        type: Object,
        value() { return {changes: []}; },
      },
      /** @type {?} */
      _submittedTogether: {
        type: Object,
        value() { return {changes: []}; },
      },
      _conflicts: {
        type: Array,
        value() { return []; },
      },
      _cherryPicks: {
        type: Array,
        value() { return []; },
      },
      _sameTopic: {
        type: Array,
        value() { return []; },
      },
    };
  }

  static get observers() {
    return [
      '_resultsChanged(_relatedResponse, _submittedTogether, ' +
        '_conflicts, _cherryPicks, _sameTopic)',
    ];
  }

  clear() {
    this.loading = true;
    this.hidden = true;

    this._relatedResponse = {changes: []};
    this._submittedTogether = {changes: []};
    this._conflicts = [];
    this._cherryPicks = [];
    this._sameTopic = [];
  }

  reload() {
    if (!this.change || !this.patchNum) {
      return Promise.resolve();
    }
    this.loading = true;
    const promises = [
      this._getRelatedChanges().then(response => {
        this._relatedResponse = response;
        this._fireReloadEvent();
        this.hasParent = this._calculateHasParent(this.change.change_id,
            response.changes);
      }),
      this._getSubmittedTogether().then(response => {
        this._submittedTogether = response;
        this._fireReloadEvent();
      }),
      this._getCherryPicks().then(response => {
        this._cherryPicks = response;
        this._fireReloadEvent();
      }),
    ];

    // Get conflicts if change is open and is mergeable.
    if (changeIsOpen(this.change) && this.mergeable) {
      promises.push(this._getConflicts().then(response => {
        // Because the server doesn't always return a response and the
        // template expects an array, always return an array.
        this._conflicts = response ? response : [];
        this._fireReloadEvent();
      }));
    }

    promises.push(this._getServerConfig().then(config => {
      if (this.change.topic && !config.change.submit_whole_topic) {
        return this._getChangesWithSameTopic().then(response => {
          this._sameTopic = response;
        });
      } else {
        this._sameTopic = [];
      }
      return this._sameTopic;
    }));

    return Promise.all(promises).then(() => {
      this.loading = false;
    });
  }

  _fireReloadEvent() {
    // The listener on the change computes height of the related changes
    // section, so they have to be rendered first, and inside a dom-repeat,
    // that requires a flush.
    flush();
    this.dispatchEvent(new CustomEvent('new-section-loaded'));
  }

  /**
   * Determines whether or not the given change has a parent change. If there
   * is a relation chain, and the change id is not the last item of the
   * relation chain, there is a parent.
   *
   * @param  {number} currentChangeId
   * @param  {!Array} relatedChanges
   * @return {boolean}
   */
  _calculateHasParent(currentChangeId, relatedChanges) {
    return relatedChanges.length > 0 &&
        relatedChanges[relatedChanges.length - 1].change_id !==
        currentChangeId;
  }

  _getRelatedChanges() {
    return this.$.restAPI.getRelatedChanges(this.change._number,
        this.patchNum);
  }

  _getSubmittedTogether() {
    return this.$.restAPI.getChangesSubmittedTogether(this.change._number);
  }

  _getServerConfig() {
    return this.$.restAPI.getConfig();
  }

  _getConflicts() {
    return this.$.restAPI.getChangeConflicts(this.change._number);
  }

  _getCherryPicks() {
    return this.$.restAPI.getChangeCherryPicks(this.change.project,
        this.change.change_id, this.change._number);
  }

  _getChangesWithSameTopic() {
    return this.$.restAPI.getChangesWithSameTopic(this.change.topic,
        this.change._number);
  }

  /**
   * @param {number} changeNum
   * @param {string} project
   * @param {number=} opt_patchNum
   * @return {string}
   */
  _computeChangeURL(changeNum, project, opt_patchNum) {
    return GerritNav.getUrlForChangeById(changeNum, project, opt_patchNum);
  }

  _computeChangeContainerClass(currentChange, relatedChange) {
    const classes = ['changeContainer'];
    if ([relatedChange, currentChange].includes(undefined)) {
      return classes;
    }
    if (this._changesEqual(relatedChange, currentChange)) {
      classes.push('thisChange');
    }
    return classes.join(' ');
  }

  /**
   * Do the given objects describe the same change? Compares the changes by
   * their numbers.
   *
   * @see /Documentation/rest-api-changes.html#change-info
   * @see /Documentation/rest-api-changes.html#related-change-and-commit-info
   * @param {!Object} a Either ChangeInfo or RelatedChangeAndCommitInfo
   * @param {!Object} b Either ChangeInfo or RelatedChangeAndCommitInfo
   * @return {boolean}
   */
  _changesEqual(a, b) {
    const aNum = this._getChangeNumber(a);
    const bNum = this._getChangeNumber(b);
    return aNum === bNum;
  }

  /**
   * Get the change number from either a ChangeInfo (such as those included in
   * SubmittedTogetherInfo responses) or get the change number from a
   * RelatedChangeAndCommitInfo (such as those included in a
   * RelatedChangesInfo response).
   *
   * @see /Documentation/rest-api-changes.html#change-info
   * @see /Documentation/rest-api-changes.html#related-change-and-commit-info
   *
   * @param {!Object} change Either a ChangeInfo or a
   *     RelatedChangeAndCommitInfo object.
   * @return {number}
   */
  _getChangeNumber(change) {
    // Default to 0 if change property is not defined.
    if (!change) return 0;

    if (change.hasOwnProperty('_change_number')) {
      return change._change_number;
    }
    return change._number;
  }

  _computeLinkClass(change) {
    const statuses = [];
    if (change.status == ChangeStatus.ABANDONED) {
      statuses.push('strikethrough');
    }
    if (change.submittable) {
      statuses.push('submittable');
    }
    return statuses.join(' ');
  }

  _computeChangeStatusClass(change) {
    const classes = ['status'];
    if (change._revision_number != change._current_revision_number) {
      classes.push('notCurrent');
    } else if (this._isIndirectAncestor(change)) {
      classes.push('indirectAncestor');
    } else if (change.submittable) {
      classes.push('submittable');
    } else if (change.status == ChangeStatus.NEW) {
      classes.push('hidden');
    }
    return classes.join(' ');
  }

  _computeChangeStatus(change) {
    switch (change.status) {
      case ChangeStatus.MERGED:
        return 'Merged';
      case ChangeStatus.ABANDONED:
        return 'Abandoned';
    }
    if (change._revision_number != change._current_revision_number) {
      return 'Not current';
    } else if (this._isIndirectAncestor(change)) {
      return 'Indirect ancestor';
    } else if (change.submittable) {
      return 'Submittable';
    }
    return '';
  }

  /** @override */
  attached() {
    super.attached();
    // We listen to `new-section-loaded` events to allow plugins to trigger
    // visibility computations, if their content or visibility changed.
    this.addEventListener('new-section-loaded',
        () => this._handleNewSectionLoaded());
  }

  _handleNewSectionLoaded() {
    // A plugin sent a `new-section-loaded` event, so its visibility likely
    // changed. Hence, we update our visibility if needed.
    this._resultsChanged(this._relatedResponse, this._submittedTogether,
        this._conflicts, this._cherryPicks, this._sameTopic);
  }

  _resultsChanged(related, submittedTogether, conflicts,
      cherryPicks, sameTopic) {
    // Polymer 2: check for undefined
    if ([
      related,
      submittedTogether,
      conflicts,
      cherryPicks,
      sameTopic,
    ].includes(undefined)) {
      return;
    }

    const results = [
      related && related.changes,
      // If there are either visible or non-visible changes, we need a
      // non-empty list to fire the event and set visibility.
      submittedTogether && ((submittedTogether.changes || [])
          + (submittedTogether.non_visible_changes ? [{}] : [])),
      conflicts,
      cherryPicks,
      sameTopic,
    ];
    for (let i = 0; i < results.length; i++) {
      if (results[i] && results[i].length > 0) {
        this.hidden = false;
        this.dispatchEvent(new CustomEvent('update', {
          composed: true, bubbles: false,
        }));
        return;
      }
    }

    this._computeHidden();
  }

  _computeHidden() {
    // None of the built-in change lists had elements. So all of them are
    // hidden. But since plugins might have injected visible content, we need
    // to check for that and stay visible if we find any such visible content.
    // (We consider plugins visible except if it's main element has the hidden
    // attribute set to true.)
    const plugins = pluginEndpoints.getDetails('related-changes-section');
    this.hidden = !(plugins.some(plugin => (
      (!plugin.domHook)
        || plugin.domHook.getAllAttached().some(
            instance => !instance.hidden))));
  }

  _isIndirectAncestor(change) {
    return !this._connectedRevisions.includes(change.commit.commit);
  }

  _computeConnectedRevisions(change, patchNum, relatedChanges) {
    // Polymer 2: check for undefined
    if ([change, patchNum, relatedChanges].includes(undefined)) {
      return undefined;
    }

    const connected = [];
    let changeRevision;
    if (!change) { return []; }
    for (const rev in change.revisions) {
      if (patchNumEquals(change.revisions[rev]._number, patchNum)) {
        changeRevision = rev;
      }
    }
    const commits = relatedChanges.map(c => c.commit);
    let pos = commits.length - 1;

    while (pos >= 0) {
      const commit = commits[pos].commit;
      connected.push(commit);
      if (commit == changeRevision) {
        break;
      }
      pos--;
    }
    while (pos >= 0) {
      for (let i = 0; i < commits[pos].parents.length; i++) {
        if (connected.includes(commits[pos].parents[i].commit)) {
          connected.push(commits[pos].commit);
          break;
        }
      }
      --pos;
    }
    return connected;
  }

  _computeSubmittedTogetherClass(submittedTogether) {
    if (!submittedTogether || (
      submittedTogether.changes.length === 0 &&
        !submittedTogether.non_visible_changes)) {
      return 'hidden';
    }
    return '';
  }

  _computeNonVisibleChangesNote(n) {
    const noun = n === 1 ? 'change' : 'changes';
    return `(+ ${n} non-visible ${noun})`;
  }
}

customElements.define(GrRelatedChangesList.is, GrRelatedChangesList);
