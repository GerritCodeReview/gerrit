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

  // Polymer makes `app` intrinsically defined on the window by virtue of the
  // custom element having the id "app", but it is made explicit here.
  const app = document.querySelector('#app');
  if (!app) {
    console.log('No gr-app found (running tests)');
  }

  let _reporting;
  function getReporting() {
    if (!_reporting) {
      _reporting = document.createElement('gr-reporting');
    }
    return _reporting;
  }

  document.onload = function() {
    getReporting().pageLoaded();
  };

  window.addEventListener('WebComponentsReady', () => {
    getReporting().timeEnd('WebComponentsReady');
  });

  const encode = window.Gerrit.URLEncodingBehavior.encodeURL;
  const patchNumEquals = window.Gerrit.PatchSetBehavior.patchNumEquals;

  function startRouter(generateUrl) {
    const base = window.Gerrit.BaseUrlBehavior.getBaseUrl();
    if (base) {
      page.base(base);
    }

    /**
     * While resolving Issue 6708, the need for some way to upgrade obsolete
     * URLs in-place without page reloads became evident.
     *
     * This function aims to update the app params and the URL when the URL is
     * found to be obsolete.
     */
    const upgradeUrl = params => {
      const url = generateUrl(params);
      if (url !== window.location.pathname) {
        history.replaceState(null, null, url);
        app.params = params;
      }
    };

    const restAPI = document.createElement('gr-rest-api-interface');
    const reporting = getReporting();

    Gerrit.Nav.setup(url => { page.show(url); }, generateUrl, upgradeUrl);

    // Middleware
    page((ctx, next) => {
      document.body.scrollTop = 0;

      // Fire asynchronously so that the URL is changed by the time the event
      // is processed.
      app.async(() => {
        app.fire('location-change', {
          hash: window.location.hash,
          pathname: window.location.pathname,
        });
        reporting.locationChanged();
      }, 1);
      next();
    });

    function loadUser(ctx, next) {
      restAPI.getLoggedIn().then(() => {
        next();
      });
    }

    // Routes.
    page('/', loadUser, data => {
      if (data.querystring.match(/^closeAfterLogin/)) {
        // Close child window on redirect after login.
        window.close();
      }
      // For backward compatibility with GWT links.
      if (data.hash) {
        // In certain login flows the server may redirect to a hash without
        // a leading slash, which page.js doesn't handle correctly.
        if (data.hash[0] !== '/') {
          data.hash = '/' + data.hash;
        }
        if (data.hash.includes('/ /') && data.canonicalPath.includes('/+/')) {
          // Path decodes all '+' to ' ' -- this breaks project-based URLs.
          // See Issue 6888.
          data.hash = data.hash.replace('/ /', '/+/');
        }
        const hash = data.hash;
        let newUrl = base + hash;
        if (hash.startsWith('/VE/')) {
          newUrl = base + '/settings' + data.hash;
        }
        page.redirect(newUrl);
        return;
      }
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          page.redirect('/dashboard/self');
        } else {
          page.redirect('/q/status:open');
        }
      });
    });

    function redirectToLogin(data) {
      const basePath = base || '';
      page('/login/' + encodeURIComponent(data.substring(basePath.length)));
    }

    page('/dashboard/(.*)', loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          data.params.view = Gerrit.Nav.View.DASHBOARD;
          app.params = data.params;
        } else {
          redirectToLogin(data.canonicalPath);
        }
      });
    });

    // Matches /admin/groups[,<offset>][/].
    page(/^\/admin\/groups(,(\d+))?(\/)?$/, loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          app.params = {
            view: Gerrit.Nav.View.ADMIN,
            adminView: 'gr-admin-group-list',
            offset: data.params[1] || 0,
            filter: null,
          };
        } else {
          redirectToLogin(data.canonicalPath);
        }
      });
    });

    page('/admin/groups/q/filter::filter,:offset', loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          app.params = {
            view: Gerrit.Nav.View.ADMIN,
            adminView: 'gr-admin-group-list',
            offset: data.params.offset,
            filter: data.params.filter,
          };
        } else {
          redirectToLogin(data.canonicalPath);
        }
      });
    });

    page('/admin/groups/q/filter::filter', loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          app.params = {
            view: Gerrit.Nav.View.ADMIN,
            adminView: 'gr-admin-group-list',
            filter: data.params.filter || null,
          };
        } else {
          redirectToLogin(data.canonicalPath);
        }
      });
    });

    // Matches /admin/projects/<project>,branches[,<offset>].
    page(/^\/admin\/projects\/(.+),branches(,(.+))?$/, loadUser, data => {
      app.params = {
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-project-detail-list',
        detailType: 'branches',
        project: data.params[0],
        offset: data.params[2] || 0,
        filter: null,
      };
    });

    page('/admin/projects/:project,branches/q/filter::filter,:offset',
        loadUser, data => {
          app.params = {
            view: Gerrit.Nav.View.ADMIN,
            adminView: 'gr-project-detail-list',
            detailType: 'branches',
            project: data.params.project,
            offset: data.params.offset,
            filter: data.params.filter,
          };
        });

    page('/admin/projects/:project,branches/q/filter::filter',
        loadUser, data => {
          app.params = {
            view: Gerrit.Nav.View.ADMIN,
            adminView: 'gr-project-detail-list',
            detailType: 'branches',
            project: data.params.project,
            filter: data.params.filter || null,
          };
        });

    // Matches /admin/projects/<project>,tags[,<offset>].
    page(/^\/admin\/projects\/(.+),tags(,(.+))?$/, loadUser, data => {
      app.params = {
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-project-detail-list',
        detailType: 'tags',
        project: data.params[0],
        offset: data.params[2] || 0,
        filter: null,
      };
    });

    page('/admin/projects/:project,tags/q/filter::filter,:offset',
        loadUser, data => {
          app.params = {
            view: Gerrit.Nav.View.ADMIN,
            adminView: 'gr-project-detail-list',
            detailType: 'tags',
            project: data.params.project,
            offset: data.params.offset,
            filter: data.params.filter,
          };
        });

    page('/admin/projects/:project,tags/q/filter::filter',
        loadUser, data => {
          app.params = {
            view: Gerrit.Nav.View.ADMIN,
            adminView: 'gr-project-detail-list',
            detailType: 'tags',
            project: data.params.project,
            filter: data.params.filter || null,
          };
        });

    // Matches /admin/projects[,<offset>][/].
    page(/^\/admin\/projects(,(\d+))?(\/)?$/, loadUser, data => {
      app.params = {
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-admin-project-list',
        offset: data.params[1] || 0,
        filter: null,
      };
    });

    page('/admin/projects/q/filter::filter,:offset', loadUser, data => {
      app.params = {
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-admin-project-list',
        offset: data.params.offset,
        filter: data.params.filter,
      };
    });

    page('/admin/projects/q/filter::filter', loadUser, data => {
      app.params = {
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-admin-project-list',
        filter: data.params.filter || null,
      };
    });

    // Matches /admin/projects/<project>
    page(/^\/admin\/projects\/(.+)$/, loadUser, data => {
      app.params = {
        view: Gerrit.Nav.View.ADMIN,
        project: data.params[0],
        adminView: 'gr-project',
      };
    });

    // Matches /admin/plugins[,<offset>][/].
    page(/^\/admin\/plugins(,(\d+))?(\/)?$/, loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          app.params = {
            view: Gerrit.Nav.View.ADMIN,
            adminView: 'gr-plugin-list',
            offset: data.params[1] || 0,
            filter: null,
          };
        } else {
          redirectToLogin(data.canonicalPath);
        }
      });
    });

    page('/admin/plugins/q/filter::filter,:offset', loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          app.params = {
            view: Gerrit.Nav.View.ADMIN,
            adminView: 'gr-plugin-list',
            offset: data.params.offset,
            filter: data.params.filter,
          };
        } else {
          redirectToLogin(data.canonicalPath);
        }
      });
    });

    page('/admin/plugins/q/filter::filter', loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          app.params = {
            view: Gerrit.Nav.View.ADMIN,
            adminView: 'gr-plugin-list',
            filter: data.params.filter || null,
          };
        } else {
          redirectToLogin(data.canonicalPath);
        }
      });
    });

    page(/^\/admin\/plugins(\/)?$/, loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          app.params = {
            view: Gerrit.Nav.View.ADMIN,
            adminView: 'gr-plugin-list',
          };
        } else {
          redirectToLogin(data.canonicalPath);
        }
      });
    });

    page('/admin/(.*)', loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          data.params.view = Gerrit.Nav.View.ADMIN;
          data.params.placeholder = true;
          app.params = data.params;
        } else {
          redirectToLogin(data.canonicalPath);
        }
      });
    });

    function queryHandler(data) {
      data.params.view = Gerrit.Nav.View.SEARCH;
      app.params = data.params;
    }

    page('/q/:query,:offset', queryHandler);
    page('/q/:query', queryHandler);

    page(/^\/(\d+)\/?/, ctx => {
      page.redirect('/c/' + encodeURIComponent(ctx.params[0]));
    });

    const normalizePatchRangeParams = params => {
      if (params.basePatchNum &&
          patchNumEquals(params.basePatchNum, params.patchNum)) {
        params.basePatchNum = null;
        upgradeUrl(params);
      } else if (params.basePatchNum && !params.patchNum) {
        params.patchNum = params.basePatchNum;
        params.basePatchNum = null;
      }
    };

    // Matches
    // /c/<project>/+/<changeNum>/[<basePatchNum>..][<patchNum>]/[path].
    // TODO(kaspern): Migrate completely to project based URLs, with backwards
    // compatibility for change-only.
    page(/^\/c\/(.+)\/\+\/(\d+)(\/?((\d+)(\.\.(\d+))?(\/(.+))?))?\/?$/,
        ctx => {
          // Parameter order is based on the regex group number matched.
          const params = {
            project: ctx.params[0],
            changeNum: ctx.params[1],
            basePatchNum: ctx.params[4],
            patchNum: ctx.params[6],
            path: ctx.params[8],
            view: ctx.params[8] ? Gerrit.Nav.View.DIFF : Gerrit.Nav.View.CHANGE,
          };
          normalizePatchRangeParams(params);
          app.params = params;
          upgradeUrl(params);
        });

    // Matches /c/<changeNum>/[<basePatchNum>..][<patchNum>][/].
    page(/^\/c\/(\d+)\/?(((\d+)(\.\.(\d+))?))?\/?$/, ctx => {
      // Parameter order is based on the regex group number matched.
      const params = {
        changeNum: ctx.params[0],
        basePatchNum: ctx.params[3],
        patchNum: ctx.params[5],
        view: Gerrit.Nav.View.CHANGE,
      };

      normalizePatchRangeParams(params);
      app.params = params;
    });

    // Matches /c/<changeNum>/[<basePatchNum>..]<patchNum>/<path>.
    page(/^\/c\/(\d+)\/((\d+)(\.\.(\d+))?)\/(.+)/, ctx => {
      // Check if path has an '@' which indicates it was using GWT style line
      // numbers. Even if the filename had an '@' in it, it would have already
      // been URI encoded. Redirect to hash version of path.
      if (ctx.path.includes('@')) {
        page.redirect(ctx.path.replace('@', '#'));
        return;
      }

      // Parameter order is based on the regex group number matched.
      const params = {
        changeNum: ctx.params[0],
        basePatchNum: ctx.params[2],
        patchNum: ctx.params[4],
        path: ctx.params[5],
        hash: ctx.hash,
        view: Gerrit.Nav.View.DIFF,
      };

      normalizePatchRangeParams(params);
      app.params = params;
    });

    page(/^\/settings\/(agreements|new-agreement)/, loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          data.params.view = Gerrit.Nav.View.AGREEMENTS;
          app.params = data.params;
        } else {
          redirectToLogin(data.canonicalPath);
        }
      });
    });

    page(/^\/settings\/VE\/(\S+)/, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          app.params = {
            view: Gerrit.Nav.View.SETTINGS,
            emailToken: data.params[0],
          };
        } else {
          redirectToLogin(data.canonicalPath);
        }
      });
    });

    page(/^\/settings\/?/, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          app.params = {view: Gerrit.Nav.View.SETTINGS};
        } else {
          redirectToLogin(data.canonicalPath);
        }
      });
    });

    page(/^\/register(\/.*)?/, ctx => {
      app.params = {justRegistered: true};
      const path = ctx.params[0] || '/';
      if (path[0] !== '/') { return; }
      page.show(base + path);
    });

    page.start();
  }

  Polymer({
    is: 'gr-router',
    behaviors: [Gerrit.PatchSetBehavior],
    start() {
      if (!app) { return; }
      startRouter(this._generateUrl.bind(this));
    },

    _generateUrl(params) {
      const base = window.Gerrit.BaseUrlBehavior.getBaseUrl();
      let url = '';

      if (params.view === Gerrit.Nav.View.SEARCH) {
        const operators = [];
        if (params.owner) {
          operators.push('owner:' + encode(params.owner));
        }
        if (params.project) {
          operators.push('project:' + encode(params.project));
        }
        if (params.branch) {
          operators.push('branch:' + encode(params.branch));
        }
        if (params.topic) {
          operators.push('topic:"' + encode(params.topic) + '"');
        }
        if (params.hashtag) {
          operators.push('hashtag:"' +
              encode(params.hashtag.toLowerCase()) + '"');
        }
        if (params.statuses) {
          if (params.statuses.length === 1) {
            operators.push('status:' + encode(params.statuses[0]));
          } else if (params.statuses.length > 1) {
            operators.push(
                '(' +
                params.statuses.map(s => `status:${encode(s)}`).join(' OR ') +
                ')');
          }
        }
        url = '/q/' + operators.join('+');
      } else if (params.view === Gerrit.Nav.View.CHANGE) {
        let range = this._getPatchRangeExpression(params);
        if (range.length) { range = '/' + range; }

        url = `/c/${params.changeNum}${range}`;
      } else if (params.view === Gerrit.Nav.View.DIFF) {
        let range = this._getPatchRangeExpression(params);
        if (range.length) { range = '/' + range; }

        url = `/c/${params.changeNum}${range}/${encode(params.path, true)}`;
      } else {
        throw new Error('Can\'t generate');
      }

      return base + url;
    },

    _getPatchRangeExpression(params) {
      let range = '';
      if (params.patchNum) { range = '' + params.patchNum; }
      if (params.basePatchNum) { range = params.basePatchNum + '..' + range; }
      return range;
    },
  });
})();
