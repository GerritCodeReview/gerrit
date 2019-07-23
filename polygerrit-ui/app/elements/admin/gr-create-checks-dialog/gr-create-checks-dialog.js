/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

  Polymer({
    is: 'gr-create-checks-dialog',
    _legacyUndefinedCheck: true,

    properties: {
      params: Object,
      _name: String,
      _schema: String,
      _id: String,
      _uuid: {
        type: String,
        value: ""
      },
      _repository: String,
      _description: String
    },

    observers: [
      '_updateUUID(_schema, _id)',
    ],

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    _updateUUID(_schema, _id) {
      this._uuid = _schema + ":" + _id;
    },

    _handleCreateChecker() {
      console.log("create checker");
      this.$.restAPI.createChecker({
        "name" : this._name,
        "description" : this._description,
        "uuid" : this._uuid,
        "repository": this._repository
      }).then(
        res => {
          console.log(res);
        }
      )
    },


  });
})();
