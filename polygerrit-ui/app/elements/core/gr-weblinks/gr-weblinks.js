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
(function(window) {
  'use strict';

  const Weblinks = {

    /** @enum {string} */
    Type: {
      CHANGE: 'change',
      FILE: 'file',
      PATCHSET: 'patchset',
    },

    /**
     * @param {{
     *   type: Weblinks.Type,
     *   repo: string,
     *   file: string,
     *   commit: string,
     *   options: Object,
     * }} info
     * @return {
     *   Array<{label: string, url: string}>|
     *   {label: string, url: string}
     *  }
     */
    _generateWeblinks(info) {
      // This methods is overwritten by calling setup()
      throw new Error('Gerrit.Weblinks not set up!');
    },

    setup(generateWeblinks) {
      this._generateWeblinks = generateWeblinks;
    },

    getFileWebLinks(repo, commit, file, opt_options) {
      const params = {type: Weblinks.Type.FILE, repo, commit, file};
      if (opt_options) {
        params.options = opt_options;
      }
      return [].concat(this._generateWeblinks(params));
    },

    getPatchSetWeblinks(repo, commit, opt_options) {
      const params = {type: Weblinks.Type.PATCHSET, repo, commit};
      if (opt_options) {
        params.options = opt_options;
      }
      return [].concat(this._generateWeblinks(params));
    },

    getChangeWeblinks(repo, commit, opt_options) {
      const params = {type: Weblinks.Type.CHANGE, repo, commit};
      if (opt_options) {
        params.options = opt_options;
      }
      return [].concat(this._generateWeblinks(params));
    },
  };

  window.Gerrit = window.Gerrit || {};
  window.Gerrit.Weblinks = Weblinks;
})(window);
