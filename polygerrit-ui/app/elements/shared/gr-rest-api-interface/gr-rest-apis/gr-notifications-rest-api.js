/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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
(function(window) {
  'use strict';
  class GrNotificationsRestApi {
    /**
     * @param {GrRestApiHelper} restApiHelper
     */
    constructor(restApiHelper) {
      this._restApiHelper = restApiHelper;
    }

    getRepoNotifications(repo, opt_errFn) {
      return this._restApiHelper.fetchSharedCacheURL({
        url: `/projects/${encodeURIComponent(repo)}/config`,
        errFn: opt_errFn,
        anonymizedUrl: '/projects/*/config',
      }).then(res => {
        if (!res || !res.notify_configs) {
          return Promise.resolve({});
        }
        return Promise.resolve({
          description: res.description,
          notify_configs: res.notify_configs,
        });
      });
    }

    changeRepoNotifications(repo, description, removals, additions, opt_errFn) {
      const url = `/projects/${encodeURIComponent(repo)}/config`;
      this._restApiHelper._cache.delete(url);
      const updates = {
        description,
        notify_config_updates: {
          notify_config_removals: removals,
          notify_config_additions: additions,
        },
      };
      return this._restApiHelper.send({
        method: 'PUT',
        url,
        body: updates,
        errFn: opt_errFn,
        anonymizedUrl: '/projects/*/config',
      });
    }
  }
  window.GrNotificationsRestApi = GrNotificationsRestApi;
})(window);

