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
      patchNum: String,
      serverConfig: {
        type: Object,
        observer: '_serverConfigChanged',
      },
      hidden: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },

      _loading: Boolean,
      _resolveServerConfigReady: Function,
      _serverConfigReady: {
        type: Object,
        value: function() {
          return new Promise(function(resolve) {
            this._resolveServerConfigReady = resolve;
          }.bind(this));
        }
      },
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
        this.$.relatedXHR.generateRequest().completes,
        this.$.submittedTogetherXHR.generateRequest().completes,
        this.$.conflictsXHR.generateRequest().completes,
        this.$.cherryPicksXHR.generateRequest().completes,
      ];

      return this._serverConfigReady.then(function() {
        if (this.change.topic &&
            !this.serverConfig.change.submit_whole_topic) {
          return this.$.sameTopicXHR.generateRequest().completes;
        } else {
          this._sameTopic = [];
        }
        return Promise.resolve();
      }.bind(this)).then(Promise.all(promises)).then(function() {
        this._loading = false;
      }.bind(this));
    },

    _computeRelatedURL: function(changeNum, patchNum) {
      return this.changeBaseURL(changeNum, patchNum) + '/related';
    },

    _computeSubmittedTogetherURL: function(changeNum) {
      return this.changeBaseURL(changeNum) + '/submitted_together';
    },

    _computeConflictsQueryParams: function(changeNum) {
      var options = this.listChangesOptionsToHex(
          this.ListChangesOption.CURRENT_REVISION,
          this.ListChangesOption.CURRENT_COMMIT
      );
      return {
        O: options,
        q: 'status:open is:mergeable conflicts:' + changeNum,
      };
    },

    _computeCherryPicksQueryParams: function(project, changeID, changeNum) {
      var options = this.listChangesOptionsToHex(
          this.ListChangesOption.CURRENT_REVISION,
          this.ListChangesOption.CURRENT_COMMIT
      );
      var query = [
        'project:' + project,
        'change:' + changeID,
        '-change:' + changeNum,
        '-is:abandoned',
      ].join(' ');
      return {
        O: options,
        q: query
      }
    },

    _computeSameTopicQueryParams: function(topic) {
      var options = this.listChangesOptionsToHex(
          this.ListChangesOption.LABELS,
          this.ListChangesOption.CURRENT_REVISION,
          this.ListChangesOption.CURRENT_COMMIT,
          this.ListChangesOption.DETAILED_LABELS
      );
      return {
        O: options,
        q: 'status:open topic:' + topic,
      };
    },

    _computeChangeURL: function(changeNum, patchNum) {
      var urlStr = '/c/' + changeNum;
      if (patchNum != null) {
        urlStr += '/' + patchNum;
      }
      return urlStr;
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
      return ''
    },

    _serverConfigChanged: function(config) {
      this._resolveServerConfigReady(config);
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
