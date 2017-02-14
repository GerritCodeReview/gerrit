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

      _loading: Boolean,
      _connectedRevisions: {
        type: Array,
        computed: '_computeConnectedRevisions(change, patchNum, ' +
            '_relatedResponse.changes)',
      },
      _relatedResponse: Object,
      _submittedTogether: Array,
      _conflicts: Array,
      _cherryPicks: Array,
      _sameTopic: Array,
    },

    behaviors: [
      Gerrit.RESTClientBehavior,
    ],

    observers: [
      '_resultsChanged(_relatedResponse.changes, _submittedTogether, ' +
          '_conflicts, _cherryPicks, _sameTopic)',
    ],

    reload: function() {
      if (!this.change || !this.patchNum) {
        return Promise.resolve();
      }
      this._loading = true;
      var promises = [
        this._getRelatedChanges().then(function(response) {
          this._relatedResponse = response;

          this.hasParent = this._calculateHasParent(this.change.change_id,
            response.changes);

        }.bind(this)),
        this._getSubmittedTogether().then(function(response) {
          this._submittedTogether = response;
        }.bind(this)),
        this._getCherryPicks().then(function(response) {
          this._cherryPicks = response;
        }.bind(this)),
      ];

      // Get conflicts if change is open and is mergeable.
      if (this.changeIsOpen(this.change.status) && this.change.mergeable) {
        promises.push(this._getConflicts().then(function(response) {
          this._conflicts = response;
        }.bind(this)));
      }

      promises.push(this._getServerConfig().then(function(config) {
        if (this.change.topic && !config.change.submit_whole_topic) {
          return this._getChangesWithSameTopic().then(function(response) {
            this._sameTopic = response;
          }.bind(this));
        } else {
          this._sameTopic = [];
        }
        return this._sameTopic;
      }.bind(this)));

      return Promise.all(promises).then(function() {
        this._loading = false;
      }.bind(this));
    },

    /**
     * Determines whether or not the given change has a parent change. If there
     * is a relation chain, and the change id is not the last item of the
     * relation chain, there is a parent.
     * @param  {Number} currentChangeId
     * @param  {Array} relatedChanges
     * @return {Boolean}
     */
    _calculateHasParent: function(currentChangeId, relatedChanges) {
      return relatedChanges.length > 0 &&
          relatedChanges[relatedChanges.length - 1].change_id !==
          currentChangeId;
    },

    _getRelatedChanges: function() {
      return this.$.restAPI.getRelatedChanges(this.change._number,
          this.patchNum);
    },

    _getSubmittedTogether: function() {
      return this.$.restAPI.getChangesSubmittedTogether(this.change._number);
    },

    _getServerConfig: function() {
      return this.$.restAPI.getConfig();
    },

    _getConflicts: function() {
      return this.$.restAPI.getChangeConflicts(this.change._number);
    },

    _getCherryPicks: function() {
      return this.$.restAPI.getChangeCherryPicks(this.change.project,
          this.change.change_id, this.change._number);
    },

    _getChangesWithSameTopic: function() {
      return this.$.restAPI.getChangesWithSameTopic(this.change.topic);
    },

    _computeChangeURL: function(changeNum, patchNum) {
      var urlStr = '/c/' + changeNum;
      if (patchNum != null) {
        urlStr += '/' + patchNum;
      }
      return urlStr;
    },

    _computeChangeContainerClass: function(currentChange, relatedChange) {
      var classes = ['changeContainer'];
      if (relatedChange.change_id === currentChange.change_id) {
        classes.push('thisChange');
      }
      return classes.join(' ');
    },

    _computeLinkClass: function(change) {
      if (change.status == this.ChangeStatus.ABANDONED) {
        return 'strikethrough';
      }
    },

    _computeChangeStatusClass: function(change) {
      var classes = ['status'];
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

    _computeChangeStatus: function(change) {
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

    _resultsChanged: function(related, submittedTogether, conflicts,
        cherryPicks, sameTopic) {
      var results = [
        related,
        submittedTogether,
        conflicts,
        cherryPicks,
        sameTopic
      ];
      for (var i = 0; i < results.length; i++) {
        if (results[i].length > 0) {
          this.hidden = false;
          return;
        }
      }
      this.hidden = true;
    },

    _isIndirectAncestor: function(change) {
      return this._connectedRevisions.indexOf(change.commit.commit) == -1;
    },

    _computeConnectedRevisions: function(change, patchNum, relatedChanges) {
      var connected = [];
      var changeRevision;
      for (var rev in change.revisions) {
        if (change.revisions[rev]._number == patchNum) {
          changeRevision = rev;
        }
      }
      var commits = relatedChanges.map(function(c) { return c.commit; });
      var pos = commits.length - 1;

      while (pos >= 0) {
        var commit = commits[pos].commit;
        connected.push(commit);
        if (commit == changeRevision) {
          break;
        }
        pos--;
      }
      while (pos >= 0) {
        for (var i = 0; i < commits[pos].parents.length; i++) {
          if (connected.indexOf(commits[pos].parents[i].commit) != -1) {
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
