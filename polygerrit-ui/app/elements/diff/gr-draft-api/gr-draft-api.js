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

  Polymer({
    is: 'gr-draft-api',
    properties: {
      _commentActionQueues: {
        type: Object,
        value: {}, // Intended shared property.
      },
    },

    saveDraft(changeNum, patchNum, comment) {
      return this._enqueue(changeNum, patchNum, comment, () =>
        this.$.restAPI.saveDiffDraft(changeNum, patchNum, comment)
            .then(response => {
              if (!response.ok) { return response; }
              return this.$.restAPI.getResponseObject(response).then(obj => {
                obj.__draft = true;
                // Maintain the ephemeral draft ID for identification by other
                // elements.
                if (comment.__draftID) {
                  obj.__draftID = comment.__draftID;
                }
                return obj;
              });
            })
      );
    },

    discardDraft(changeNum, patchNum, comment) {
      return this._enqueue(changeNum, patchNum, comment, () => {
        return this.$.restAPI.deleteDiffDraft(changeNum, patchNum, comment)
      })
    },

    /**
     * Enqueue an action for the given comment.
     * @param {!number} changeNum
     * @param {!number} patchNum
     * @param {!object} comment The comment object as it will appear after the
     *     action has been completed.
     * @param {!function(): Promise} Action a function to erform the action that
     *     returns a promise.
     * @return {Promise}
     */
    _enqueue(changeNum, patchNum, comment, action) {
      const key = this._getDraftKey(changeNum, patchNum, comment);

      // Compose the action function with a cleanup routine.
      const actionWithCleanup = x => action(x).then(y => {
        this._commentActionQueues[key].outstanding--;
        if (!this._commentActionQueues[key].outstanding) {
          console.log('deleting queue');
          delete this._commentActionQueues[key];
        }
        return y;
      });

      let promise;
      const queue = this._commentActionQueues[key];

      if (queue) {
        // If a queue exists for this draft, then chain to the existing promise.
        promise = queue.promise.then(actionWithCleanup);
        queue.promise = promise;
        queue.comment = comment;
        queue.outstanding++;
        console.log('queueing with ' + queue.outstanding + ' other actions');
      } else {
        // Otherwise start a new promise by executing the action.
        promise = actionWithCleanup();
        this._commentActionQueues[key] = {
          promise,
          comment,
          outstanding: 1,
        };
        console.log('initialized queue');
      }
      return promise;
    },

    _getDraftKey(changeNum, patchNum, comment) {
      let key = [changeNum, patchNum, comment.path, comment.line || '']
          .join(':');
      if (comment.range) {
        key += ':' + [
          comment.range.start_line,
          comment.range.start_character,
          comment.range.end_character,
          comment.range.end_line,
        ].join('-');
      }
      return key;
    },
  });
})();
