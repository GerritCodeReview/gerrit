// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  Polymer({
    is: 'gr-related-changes-list',

    properties: {
      change: Object,
      hasParent: {
        type: Boolean,
        notify: true,
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
      _connectedRevisions: {
        type: Array,
        computed: '_computeConnectedRevisions(change, patchNum, ' +
            '_relatedResponse.changes)',
      },
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
      Gerrit.BaseUrlBehavior,
      Gerrit.RESTClientBehavior,
    ],

    observers: [
      '_resultsChanged(_relatedResponse.changes, _submittedTogether, ' +
          '_conflicts, _cherryPicks, _sameTopic)',
    ],

    clear() {
      this.loading = true;
      this.hidden = true;
    },

    reload() {
      if (!this.change || !this.patchNum) {
        return Promise.resolve();
      }
      this.loading = true;
      const promises = [
        this._getRelatedChanges().then(response => {
          this._relatedResponse = response;

          this.hasParent = this._calculateHasParent(this.change.change_id,
              response.changes);
        }),
        this._getSubmittedTogether().then(response => {
          this._submittedTogether = response;
        }),
        this._getCherryPicks().then(response => {
          this._cherryPicks = response;
        }),
      ];

      // Get conflicts if change is open and is mergeable.
      if (this.changeIsOpen(this.change.status) && this.change.mergeable) {
        promises.push(this._getConflicts().then(response => {
          this._conflicts = response;
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

    /**
     * Determines whether or not the given change has a parent change. If there
     * is a relation chain, and the change id is not the last item of the
     * relation chain, there is a parent.
     * @param  {Number} currentChangeId
     * @param  {Array} relatedChanges
     * @return {Boolean}
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

    _computeChangeURL(changeNum, patchNum) {
      let urlStr = this.getBaseUrl() + '/c/' + changeNum;
      if (patchNum != null) {
        urlStr += '/' + patchNum;
      }
      return urlStr;
    },

    _computeChangeContainerClass(currentChange, relatedChange) {
      const classes = ['changeContainer'];
      if (relatedChange.change_id === currentChange.change_id) {
        classes.push('thisChange');
      }
      return classes.join(' ');
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
        case this.ChangeStatus.DRAFT:
          return 'Draft';
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
        if (change.revisions[rev]._number == patchNum) {
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
    },
  });
})();
