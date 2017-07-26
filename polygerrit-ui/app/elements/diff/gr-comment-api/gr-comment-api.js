// Copyright (C) 2017 The Android Open Source Project
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

  const PARENT = 'PARENT';

  Polymer({
    is: 'gr-comment-api',

    properties: {
      _comments: Object,
      _drafts: Object,
      _robotComments: Object,
    },

    behaviors: [
      Gerrit.PatchSetBehavior,
    ],

    loadAll(changeNum) {
      this._comments = undefined;
      this._drafts = undefined;
      this._robotComments = undefined;
      const promises = [];

      promises.push(this.$.restAPI.getDiffComments(changeNum)
          .then(comments => { this._comments = comments; }));

      promises.push(this.$.restAPI.getDiffRobotComments(changeNum)
          .then(robotComments => { this._robotComments = robotComments; }));

      promises.push(
          this.$.restAPI.getLoggedIn()
          .then(loggedIn => {
            if (!loggedIn) { return Promise.resolve({}); }
            return this.$.restAPI.getDiffDrafts(changeNum);
          })
          .then(drafts => { this._drafts = drafts;}));

      return Promise.all(promises);
    },

    getPaths(patchRange) {
      const filterByRange = c => {
        return this.patchNumEquals(c.patch_set, patchRange.patchNum) ||
            this.patchNumEquals(c.patch_set, patchRange.basePatchNum);
      };

      const responses = [this._comments, this._drafts, this._robotComments];
      const commentMap = {};
      for (const response of responses) {
        for (const path in response) {
          if (response.hasOwnProperty(path) &&
              response[path].filter(filterByRange).length) {
            commentMap[path] = true;
          }
        }
      }
      return commentMap;
    },

    getCommentsForPath(path, patchRange) {
      const comments = this._comments[path] || [];
      const drafts = this._drafts[path] || [];
      const robotComments = this._robotComments[path] || [];

      drafts.forEach(d => { d.__draft = true; });

      const all = comments.concat(drafts).concat(robotComments);

      let baseFilter;
      if (patchRange.basePatchNum === PARENT) {
        baseFilter = c => c.side === PARENT;
      } else {
        baseFilter = c => c.side !== PARENT &&
            this.patchNumEquals(c.patch_set, patchRange.basePatchNum);
      }

      const baseComments = all.filter(baseFilter);
      const revisionComments = all.filter(c => c.side !== PARENT &&
          this.patchNumEquals(c.patch_set, patchRange.patchNum));

      return {
        meta: {
          path: path,
          patchRange: patchRange,
        },
        left: baseComments,
        right: revisionComments,
      };
    },
  });
})();
