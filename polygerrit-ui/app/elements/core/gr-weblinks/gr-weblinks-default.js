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

  let restAPI;
  function getRestAPI() {
    if (!restAPI) {
      restAPI = document.createElement('gr-rest-api-interface');
    }
    return restAPI;
  }

  function getServerConfig() {
    return getRestAPI().getConfig();
  }

  function getPatchSetWeblinks({repo, commit, options: {weblinks}}) {
    const name = commit && commit.slice(0, 7);
    const gitwebConfigUrl = configBasedCommitUrl(repo, commit);
    if (gitwebConfigUrl) {
      return {
        name,
        url: gitwebConfigUrl,
      };
    }
    return getSupportedWeblinks(weblinks).slice(0, 1).map(({url}) => {
      return {name, url};
    });
  }

  function configBasedCommitUrl(repo, commit) {
    const serverConfig = getServerConfig();
    if (serverConfig.gitweb && serverConfig.gitweb.url &&
        serverConfig.gitweb.type && serverConfig.gitweb.type.revision) {
      return serverConfig.gitweb.url + serverConfig.gitweb.type.revision
          .replace('${project}', repo)
          .replace('${commit}', commit);
    }
  }

  function isDirectCommit(link) {
    // This is a whitelist of web link types that provide direct links to
    // the commit in the url property.
    return link.name === 'gitiles' || link.name === 'gitweb';
  }

  function getSupportedWeblinks(weblinks) {
    if (!weblinks) return [];
    return weblinks.filter(isDirectCommit).map(({name, url}) => {
      if (url.startsWith('http')) {
        return {name, url};
      } else {
        return {
          name,
          url: `../../${url}`,
        };
      }
    });
  }

  function getChangeWeblinks({repo, commit, options: {weblinks}}) {
    if (!weblinks || !weblinks.length) return [];
    return weblinks.filter(weblink => !isDirectCommit(weblink)).map(
        ({name, url}) => {
          if (url.startsWith('http')) {
            return {name, url};
          } else {
            return {
              name,
              url: `../../${url}`,
            };
          }
        });
  }

  function getFileWebLinks({repo, commit, file, options: {weblinks}}) {
    return weblinks;
  }

  Gerrit.Weblinks.setup(params => {
    const type = params.type;
    switch (type) {
      case Gerrit.Weblinks.Type.FILE:
        return getFileWebLinks(params);
      case Gerrit.Weblinks.Type.CHANGE:
        return getChangeWeblinks(params);
      case Gerrit.Weblinks.Type.PATCHSET:
        return getPatchSetWeblinks(params);
      default:
        console.warn(`Unsupported weblink ${type}!`);
    }
  });
})(window);
