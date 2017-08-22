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
  const EDIT_NAME = window.Gerrit.PatchSetBehavior.EDIT_NAME;

  function startRouter(generateUrl) {
    const base = window.Gerrit.BaseUrlBehavior.getBaseUrl();
    if (base) {
      page.base(base);
    }

    const restAPI = document.createElement('gr-rest-api-interface');
    const reporting = getReporting();

    Gerrit.Nav.setup(url => { page.show(url); }, generateUrl);

    /**
     * Given a set of params without a project, gets the project from the rest
     * API project lookup and then sets the app params.
     *
     * @param {?Object} params
     */
    const normalizeLegacyRouteParams = params => {
      if (!params.changeNum) { return; }

      restAPI.getFromProjectLookup(params.changeNum).then(project => {
        params.project = project;
        normalizePatchRangeParams(params);
        page.redirect(generateUrl(params));
      });
    };

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
      restAPI.getLoggedIn().then(() => { next(); });
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

    // Matches /admin/groups/<group>,info (backwords compat with gwtui)
    // Redirects to /admin/groups/<group>
    page(/^\/admin\/groups\/(.+),info$/, loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          page.redirect('/admin/groups/' + encodeURIComponent(data.params[0]));
        } else {
          redirectToLogin(data.canonicalPath);
        }
      });
    });

    // Matches /admin/groups/<group>,audit-log
    page(/^\/admin\/groups\/(.+),audit-log$/, loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          app.params = {
            view: Gerrit.Nav.View.ADMIN,
            adminView: 'gr-group-audit-log',
            detailType: 'audit-log',
            groupId: data.params[0],
          };
        } else {
          page.redirect('/login/' + encodeURIComponent(data.canonicalPath));
        }
      });
    });

    // Matches /admin/groups/<group>,members
    page(/^\/admin\/groups\/(.+),members$/, loadUser, data => {
      app.params = {
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-group-members',
        detailType: 'members',
        groupId: data.params[0],
      };
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

    // Matches /admin/groups/<group>
    page(/^\/admin\/groups\/([^,]+)$/, loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          app.params = {
            view: Gerrit.Nav.View.ADMIN,
            adminView: 'gr-group',
            groupId: data.params[0],
          };
        } else {
          redirectToLogin(data.canonicalPath);
        }
      });
    });

    // Matches /admin/projects/<project>,commands.
    page(/^\/admin\/projects\/(.+),commands$/, loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          app.params = {
            view: Gerrit.Nav.View.ADMIN,
            adminView: 'gr-project-commands',
            detailType: 'commands',
            project: data.params[0],
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
    page(/^\/admin\/projects\/([^,]+)$/, loadUser, data => {
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

    /**
     * Normalizes the params object, and determines if the URL needs to be
     * modified to fit the proper schema.
     *
     * @param {*} params
     * @return {boolean} whether or not the URL needs to be upgraded.
     */
    const normalizePatchRangeParams = params => {
      let needsRedirect = false;
      if (params.basePatchNum &&
          patchNumEquals(params.basePatchNum, params.patchNum)) {
        needsRedirect = true;
        params.basePatchNum = null;
      } else if (params.basePatchNum && !params.patchNum) {
        // Regexes set basePatchNum instead of patchNum when only one is
        // specified. Redirect is not needed in this case.
        params.patchNum = params.basePatchNum;
        params.basePatchNum = null;
      }
      // In GWTUI, edits are represented in URLs with either 0 or 'edit'.
      // TODO(kaspern): Remove this normalization when GWT UI is gone.
      if (patchNumEquals(params.basePatchNum, 0)) {
        params.basePatchNum = EDIT_NAME;
        needsRedirect = true;
      }
      if (patchNumEquals(params.patchNum, 0)) {
        params.patchNum = EDIT_NAME;
        needsRedirect = true;
      }
      return needsRedirect;
    };

    // Matches
    // /c/<project>/+/<changeNum>/[<basePatchNum|edit>..][<patchNum|edit>]/[path].
    // TODO(kaspern): Migrate completely to project based URLs, with backwards
    // compatibility for change-only.
    // eslint-disable-next-line max-len
    page(/^\/c\/(.+)\/\+\/(\d+)(\/?((\d+|edit)(\.\.(\d+|edit))?(\/(.+))?))?\/?$/,
        ctx => {
          // Parameter order is based on the regex group number matched.
          const params = {
            project: ctx.params[0],
            changeNum: ctx.params[1],
            basePatchNum: ctx.params[4],
            patchNum: ctx.params[6],
            path: ctx.params[8],
            view: ctx.params[8] ? Gerrit.Nav.View.DIFF : Gerrit.Nav.View.CHANGE,
            hash: ctx.hash,
          };
          const needsRedirect = normalizePatchRangeParams(params);
          if (needsRedirect) {
            page.redirect(generateUrl(params));
          } else {
            app.params = params;
            restAPI.setInProjectLookup(params.changeNum, params.project);
          }
        });

    // Matches /c/<changeNum>/[<basePatchNum>..][<patchNum>][/].
    page(/^\/c\/(\d+)\/?(((\d+|edit)(\.\.(\d+|edit))?))?\/?$/, ctx => {
      // Parameter order is based on the regex group number matched.
      const params = {
        changeNum: ctx.params[0],
        basePatchNum: ctx.params[3],
        patchNum: ctx.params[5],
        view: Gerrit.Nav.View.CHANGE,
      };

      normalizeLegacyRouteParams(params);
    });

    // Matches /c/<changeNum>/[<basePatchNum>..]<patchNum>/<path>.
    page(/^\/c\/(\d+)\/((\d+|edit)(\.\.(\d+|edit))?)\/(.+)/, ctx => {
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

      normalizeLegacyRouteParams(params);
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
        if (params.project) {
          url = `/c/${params.project}/+/${params.changeNum}${range}`;
        } else {
          url = `/c/${params.changeNum}${range}`;
        }
      } else if (params.view === Gerrit.Nav.View.DIFF) {
        let range = this._getPatchRangeExpression(params);
        if (range.length) { range = '/' + range; }

        let suffix = `${range}/${encode(params.path, true)}`;
        if (params.lineNum) {
          suffix += '#';
          if (params.leftSide) { suffix += 'b'; }
          suffix += params.lineNum;
        }

        if (params.project) {
          url = `/c/${params.project}/+/${params.changeNum}${suffix}`;
        } else {
          url = `/c/${params.changeNum}${suffix}`;
        }
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
