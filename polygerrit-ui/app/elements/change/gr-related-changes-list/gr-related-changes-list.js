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

import '../../../behaviors/gr-patch-set-behavior/gr-patch-set-behavior.js';
import '../../../behaviors/rest-client-behavior/rest-client-behavior.js';
import '../../core/gr-navigation/gr-navigation.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/shared-styles.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        display: block;
      }
      h3 {
        margin: .5em 0 0;
      }
      section {
        margin-bottom: 1.4em; /* Same as line height for collapse purposes */
      }
      a {
        display: block;
      }
      .changeContainer,
      a {
        max-width: 100%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .changeContainer {
        display: flex;
      }
      .changeContainer.thisChange:before {
        content: '➔';
        width: 1.2em;
      }
      h4,
      section div {
        display: flex;
      }
      h4:before,
      section div:before {
        content: ' ';
        flex-shrink: 0;
        width: 1.2em;
      }
      .relatedChanges a {
        display: inline-block;
      }
      .strikethrough {
        color: var(--deemphasized-text-color);
        text-decoration: line-through;
      }
      .status {
        color: var(--deemphasized-text-color);
        font-family: var(--font-family-bold);
        margin-left: .25em;
      }
      .notCurrent {
        color: #e65100;
      }
      .indirectAncestor {
        color: #33691e;
      }
      .submittable {
        color: #1b5e20;
      }
      .submittableCheck {
        color: var(--vote-text-color-recommended);
        display: none;
      }
      .submittableCheck.submittable {
        display: inline;
      }
      .hidden,
      .mobile {
        display: none;
      }
       @media screen and (max-width: 60em) {
        .mobile {
          display: block;
        }
      }
    </style>
    <div>
      <section class="relatedChanges" hidden\$="[[!_relatedResponse.changes.length]]" hidden="">
        <h4>Relation chain</h4>
        <template is="dom-repeat" items="[[_relatedResponse.changes]]" as="related">
          <div class\$="rightIndent [[_computeChangeContainerClass(change, related)]]">
            <a href\$="[[_computeChangeURL(related._change_number, related.project, related._revision_number)]]" class\$="[[_computeLinkClass(related)]]" title\$="[[related.commit.subject]]">
              [[related.commit.subject]]
            </a>
            <span class\$="[[_computeChangeStatusClass(related)]]">
              ([[_computeChangeStatus(related)]])
            </span>
          </div>
        </template>
      </section>
      <section hidden\$="[[!_submittedTogether.length]]" hidden="">
        <h4>Submitted together</h4>
        <template is="dom-repeat" items="[[_submittedTogether]]" as="related">
          <div class\$="[[_computeChangeContainerClass(change, related)]]">
            <a href\$="[[_computeChangeURL(related._number, related.project)]]" class\$="[[_computeLinkClass(related)]]" title\$="[[related.project]]: [[related.branch]]: [[related.subject]]">
              [[related.project]]: [[related.branch]]: [[related.subject]]
            </a>
            <span tabindex="-1" title="Submittable" class\$="submittableCheck [[_computeLinkClass(related)]]">✓</span>
          </div>
        </template>
      </section>
      <section hidden\$="[[!_sameTopic.length]]" hidden="">
        <h4>Same topic</h4>
        <template is="dom-repeat" items="[[_sameTopic]]" as="change">
          <div>
            <a href\$="[[_computeChangeURL(change._number, change.project)]]" class\$="[[_computeLinkClass(change)]]" title\$="[[change.project]]: [[change.branch]]: [[change.subject]]">
              [[change.project]]: [[change.branch]]: [[change.subject]]
            </a>
          </div>
        </template>
      </section>
      <section hidden\$="[[!_conflicts.length]]" hidden="">
        <h4>Merge conflicts</h4>
        <template is="dom-repeat" items="[[_conflicts]]" as="change">
          <div>
            <a href\$="[[_computeChangeURL(change._number, change.project)]]" class\$="[[_computeLinkClass(change)]]" title\$="[[change.subject]]">
              [[change.subject]]
            </a>
          </div>
        </template>
      </section>
      <section hidden\$="[[!_cherryPicks.length]]" hidden="">
        <h4>Cherry picks</h4>
        <template is="dom-repeat" items="[[_cherryPicks]]" as="change">
          <div>
            <a href\$="[[_computeChangeURL(change._number, change.project)]]" class\$="[[_computeLinkClass(change)]]" title\$="[[change.branch]]: [[change.subject]]">
              [[change.branch]]: [[change.subject]]
            </a>
          </div>
        </template>
      </section>
    </div>
    <div hidden\$="[[!loading]]">Loading...</div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-related-changes-list',

  /**
   * Fired when a new section is loaded so that the change view can determine
   * a show more button is needed, sometimes before all the sections finish
   * loading.
   *
   * @event new-section-loaded
   */

  properties: {
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
    _submittedTogether: {
      type: Array,
      value() { return []; },
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
  },

  behaviors: [
    Gerrit.PatchSetBehavior,
    Gerrit.RESTClientBehavior,
  ],

  observers: [
    '_resultsChanged(_relatedResponse.changes, _submittedTogether, ' +
        '_conflicts, _cherryPicks, _sameTopic)',
  ],

  clear() {
    this.loading = true;
    this.hidden = true;

    this._relatedResponse = {changes: []};
    this._submittedTogether = [];
    this._conflicts = [];
    this._cherryPicks = [];
    this._sameTopic = [];
  },

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
    if (this.changeIsOpen(this.change.status) && this.mergeable) {
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
  },

  _fireReloadEvent() {
    // The listener on the change computes height of the related changes
    // section, so they have to be rendered first, and inside a dom-repeat,
    // that requires a flush.
    Polymer.dom.flush();
    this.dispatchEvent(new CustomEvent('new-section-loaded'));
  },

  /**
   * Determines whether or not the given change has a parent change. If there
   * is a relation chain, and the change id is not the last item of the
   * relation chain, there is a parent.
   * @param  {number} currentChangeId
   * @param  {!Array} relatedChanges
   * @return {boolean}
   */
  _calculateHasParent(currentChangeId, relatedChanges) {
    return relatedChanges.length > 0 &&
        relatedChanges[relatedChanges.length - 1].change_id !==
        currentChangeId;
  },

  _getRelatedChanges() {
    return this.$.restAPI.getRelatedChanges(this.change._number,
        this.patchNum);
  },

  _getSubmittedTogether() {
    return this.$.restAPI.getChangesSubmittedTogether(this.change._number);
  },

  _getServerConfig() {
    return this.$.restAPI.getConfig();
  },

  _getConflicts() {
    return this.$.restAPI.getChangeConflicts(this.change._number);
  },

  _getCherryPicks() {
    return this.$.restAPI.getChangeCherryPicks(this.change.project,
        this.change.change_id, this.change._number);
  },

  _getChangesWithSameTopic() {
    return this.$.restAPI.getChangesWithSameTopic(this.change.topic);
  },

  /**
   * @param {number} changeNum
   * @param {string} project
   * @param {number=} opt_patchNum
   * @return {string}
   */
  _computeChangeURL(changeNum, project, opt_patchNum) {
    return Gerrit.Nav.getUrlForChangeById(changeNum, project, opt_patchNum);
  },

  _computeChangeContainerClass(currentChange, relatedChange) {
    const classes = ['changeContainer'];
    if (this._changesEqual(relatedChange, currentChange)) {
      classes.push('thisChange');
    }
    return classes.join(' ');
  },

  /**
   * Do the given objects describe the same change? Compares the changes by
   * their numbers.
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
  },

  /**
   * Get the change number from either a ChangeInfo (such as those included in
   * SubmittedTogetherInfo responses) or get the change number from a
   * RelatedChangeAndCommitInfo (such as those included in a
   * RelatedChangesInfo response).
   * @see /Documentation/rest-api-changes.html#change-info
   * @see /Documentation/rest-api-changes.html#related-change-and-commit-info
   *
   * @param {!Object} change Either a ChangeInfo or a
   *     RelatedChangeAndCommitInfo object.
   * @return {number}
   */
  _getChangeNumber(change) {
    if (change.hasOwnProperty('_change_number')) {
      return change._change_number;
    }
    return change._number;
  },

  _computeLinkClass(change) {
    const statuses = [];
    if (change.status == this.ChangeStatus.ABANDONED) {
      statuses.push('strikethrough');
    }
    if (change.submittable) {
      statuses.push('submittable');
    }
    return statuses.join(' ');
  },

  _computeChangeStatusClass(change) {
    const classes = ['status'];
    if (change._revision_number != change._current_revision_number) {
      classes.push('notCurrent');
    } else if (this._isIndirectAncestor(change)) {
      classes.push('indirectAncestor');
    } else if (change.submittable) {
      classes.push('submittable');
    } else if (change.status == this.ChangeStatus.NEW) {
      classes.push('hidden');
    }
    return classes.join(' ');
  },

  _computeChangeStatus(change) {
    switch (change.status) {
      case this.ChangeStatus.MERGED:
        return 'Merged';
      case this.ChangeStatus.ABANDONED:
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
  },

  _resultsChanged(related, submittedTogether, conflicts,
      cherryPicks, sameTopic) {
    const results = [
      related,
      submittedTogether,
      conflicts,
      cherryPicks,
      sameTopic,
    ];
    for (let i = 0; i < results.length; i++) {
      if (results[i].length > 0) {
        this.hidden = false;
        this.fire('update', null, {bubbles: false});
        return;
      }
    }
    this.hidden = true;
  },

  _isIndirectAncestor(change) {
    return !this._connectedRevisions.includes(change.commit.commit);
  },

  _computeConnectedRevisions(change, patchNum, relatedChanges) {
    const connected = [];
    let changeRevision;
    for (const rev in change.revisions) {
      if (this.patchNumEquals(change.revisions[rev]._number, patchNum)) {
        changeRevision = rev;
      }
    }
    const commits = relatedChanges.map(c => { return c.commit; });
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
});
