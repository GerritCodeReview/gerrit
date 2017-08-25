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

  const RoutePattern = {
    ROOT: '/',
    DASHBOARD: '/dashboard/(.*)',
    GROUP_INFO: /^\/admin\/groups\/(.+),info$/,
    GROUP_AUDIT_LOG: /^\/admin\/groups\/(.+),audit-log$/,
    GROUP_MEMBERS: /^\/admin\/groups\/(.+),members$/,
    GROUP_LIST: /^\/admin\/groups(,(\d+))?(\/)?$/,
  };

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

  Polymer({
    is: 'gr-router',

    properties: {
      _restAPI: {
        type: Object,
        value: () => document.createElement('gr-rest-api-interface'),
      },
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.PatchSetBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    start() {
      if (!app) { return; }
      this._startRouter();
    },

    _setParams(params) {
      app.params = params;
    },

    _redirect(url) {
      page.redirect(url);
    },

    _generateUrl(params) {
      const base = this.getBaseUrl();
      let url = '';

      if (params.view === Gerrit.Nav.View.SEARCH) {
        const operators = [];
        if (params.owner) {
          operators.push('owner:' + this.encodeURL(params.owner, false));
        }
        if (params.project) {
          operators.push('project:' + this.encodeURL(params.project, false));
        }
        if (params.branch) {
          operators.push('branch:' + this.encodeURL(params.branch, false));
        }
        if (params.topic) {
          operators.push('topic:"' + this.encodeURL(params.topic, false) + '"');
        }
        if (params.hashtag) {
          operators.push('hashtag:"' +
              this.encodeURL(params.hashtag.toLowerCase(), false) + '"');
        }
        if (params.statuses) {
          if (params.statuses.length === 1) {
            operators.push(
                'status:' + this.encodeURL(params.statuses[0], false));
          } else if (params.statuses.length > 1) {
            operators.push(
                '(' +
                params.statuses.map(s => `status:${this.encodeURL(s, false)}`)
                    .join(' OR ') +
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
      } else if (params.view === Gerrit.Nav.View.DASHBOARD) {
        url = `/dashboard/${params.user || 'self'}`;
      } else if (params.view === Gerrit.Nav.View.DIFF) {
        let range = this._getPatchRangeExpression(params);
        if (range.length) { range = '/' + range; }

        let suffix = `${range}/${this.encodeURL(params.path, true)}`;
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

    /**
     * Given a set of params without a project, gets the project from the rest
     * API project lookup and then sets the app params.
     *
     * @param {?Object} params
     */
    _normalizeLegacyRouteParams(params) {
      if (!params.changeNum) { return Promise.resolve(); }

      return this._restAPI.getFromProjectLookup(params.changeNum)
          .then(project => {
            params.project = project;
            this._normalizePatchRangeParams(params);
            this._redirect(this._generateUrl(params));
          });
    },

    /**
     * Normalizes the params object, and determines if the URL needs to be
     * modified to fit the proper schema.
     *
     * @param {*} params
     * @return {boolean} whether or not the URL needs to be upgraded.
     */
    _normalizePatchRangeParams(params) {
      const hasBasePatchNum = params.basePatchNum !== null &&
          params.basePatchNum !== undefined;
      const hasPatchNum = params.patchNum !== null &&
          params.patchNum !== undefined;
      let needsRedirect = false;
      if (hasBasePatchNum &&
          this.patchNumEquals(params.basePatchNum, params.patchNum)) {
        needsRedirect = true;
        params.basePatchNum = null;
      } else if (hasBasePatchNum && !hasPatchNum) {
        // Regexes set basePatchNum instead of patchNum when only one is
        // specified. Redirect is not needed in this case.
        params.patchNum = params.basePatchNum;
        params.basePatchNum = null;
      }
      // In GWTUI, edits are represented in URLs with either 0 or 'edit'.
      // TODO(kaspern): Remove this normalization when GWT UI is gone.
      if (this.patchNumEquals(params.basePatchNum, 0)) {
        params.basePatchNum = this.EDIT_NAME;
        needsRedirect = true;
      }
      if (this.patchNumEquals(params.patchNum, 0)) {
        params.patchNum = this.EDIT_NAME;
        needsRedirect = true;
      }
      return needsRedirect;
    },

    _redirectToLogin(data) {
      const basePath = this.getBaseUrl() || '';
      this._redirect(
          '/login/' + encodeURIComponent(data.substring(basePath.length)));
    },

    /**
     * Hashes parsed by page.js exclude "inner" hashes, so a URL like "/a#b#c"
     * is parsed to have a hash of "b" rather than "b#c". Instead, this method
     * parses hashes correctly. Will return an empty string if there is no hash.
     * @param {!string} canonicalPath
     * @return {!string} Everything after the first '#' ("a#b#c" -> "b#c").
     */
    _getHashFromCanonicalPath(canonicalPath) {
      return canonicalPath.split('#').slice(1).join('#');
    },

    _redirectIfNotLoggedIn(data) {
      return this._restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          return Promise.resolve();
        } else {
          this._redirectToLogin(data.canonicalPath);
          // Return a promise that never resolves.
          return new Promise(() => {});
        }
      });
    },

    /**
     * @param {!Object} data
     * @return {Promise?} if handling the route involves asynchrony, then a
     *     promise is returned. Otherwise, synchronous handling returns
     *     undefined.
     */
    _handleRootRoute(data) {
      if (data.querystring.match(/^closeAfterLogin/)) {
        // Close child window on redirect after login.
        window.close();
        return;
      }
      let hash = this._getHashFromCanonicalPath(data.canonicalPath);
      // For backward compatibility with GWT links.
      if (hash) {
        // In certain login flows the server may redirect to a hash without
        // a leading slash, which page.js doesn't handle correctly.
        if (hash[0] !== '/') {
          hash = '/' + hash;
        }
        if (hash.includes('/ /') && data.canonicalPath.includes('/+/')) {
          // Path decodes all '+' to ' ' -- this breaks project-based URLs.
          // See Issue 6888.
          hash = hash.replace('/ /', '/+/');
        }
        const base = this.getBaseUrl();
        let newUrl = base + hash;
        if (hash.startsWith('/VE/')) {
          newUrl = base + '/settings' + hash;
        }
        this._redirect(newUrl);
        return;
      }
      return this._restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          this._redirect('/dashboard/self');
        } else {
          this._redirect('/q/status:open');
        }
      });
    },

    _handleDashboardRoute(data) {
      if (!data.params[0]) {
        this._redirect('/dashboard/self');
        return;
      }

      return this._restAPI.getLoggedIn().then(loggedIn => {
        if (!loggedIn) {
          if (data.params[0].toLowerCase() === 'self') {
            this._redirectToLogin(data.canonicalPath);
          } else {
            // TODO: encode user or use _generateUrl.
            this._redirect('/q/owner:' + data.params[0]);
          }
        } else {
          this._setParams({
            view: Gerrit.Nav.View.DASHBOARD,
            user: data.params[0],
          });
        }
      });
    },

    _handleGroupInfoRoute(data) {
      this._redirect('/admin/groups/' + encodeURIComponent(data.params[0]));
    },

    _handleGroupAuditLogRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-group-audit-log',
        detailType: 'audit-log',
        groupId: data.params[0],
      });
    },

    _handleGroupMembersRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-group-members',
        detailType: 'members',
        groupId: data.params[0],
      });
    },

    _handleGroupListRoute(data) {
      this._setParams({
        view: Gerrit.Nav.View.ADMIN,
        adminView: 'gr-admin-group-list',
        offset: data.params[1] || 0,
        filter: null,
      });
    },

    _loadUserMiddleware(ctx, next) {
      this._restAPI.getLoggedIn().then(() => { next(); });
    },

    /**
     * Map a route to a method on the router.
     *
     * @param {!string|!Regex} pattern The page.js pattern for the route.
     * @param {!string} handlerName The method name for the handler. If the
     *     route is matched, the handler will be executed with `this` referring
     *     to the component.
     * @param  {?boolean} opt_authRedirect If true, then auth is checked before
     *     executing the hanler. If the user is not logged in, it will redirect
     *     to the login flow and the handler will not be executed. The login
     *     redirect specifies the matched URL to be used after successfull auth.
     */
    _mapRoute(pattern, handlerName, opt_authRedirect) {
      page(pattern, this._loadUserMiddleware.bind(this), data => {
        const promise = opt_authRedirect ?
          this._redirectIfNotLoggedIn(data) : Promise.resolve();
        promise.then(() => { this[handlerName](data); });
      });
    },

    _startRouter() {
      const base = this.getBaseUrl();
      if (base) {
        page.base(base);
      }

      const reporting = getReporting();

      Gerrit.Nav.setup(url => { page.show(url); },
          this._generateUrl.bind(this));

      // Middleware
      page((ctx, next) => {
        document.body.scrollTop = 0;

        // Fire asynchronously so that the URL is changed by the time the event
        // is processed.
        this.async(() => {
          this.fire('location-change', {
            hash: window.location.hash,
            pathname: window.location.pathname,
          });
          reporting.locationChanged();
        }, 1);
        next();
      });

      const loadUser = (ctx, next) => {
        this._restAPI.getLoggedIn().then(() => { next(); });
      };

      // Routes.
      this._mapRoute(RoutePattern.ROOT, '_handleRootRoute');

      this._mapRoute(RoutePattern.DASHBOARD, '_handleDashboardRoute');

      // Matches /admin/groups/<group>,info (backwords compat with gwtui)
      // Redirects to /admin/groups/<group>
      this._mapRoute(RoutePattern.GROUP_INFO, '_handleGroupInfoRoute', true);

      // Matches /admin/groups/<group>,audit-log
      this._mapRoute(RoutePattern.GROUP_AUDIT_LOG, '_handleGroupAuditLogRoute',
          true);

      // Matches /admin/groups/<group>,members
      this._mapRoute(RoutePattern.GROUP_MEMBERS, '_handleGroupMembersRoute');

      // Matches /admin/groups[,<offset>][/].
      this._mapRoute(RoutePattern.GROUP_LIST, '_handleGroupListRoute', true);

      page('/admin/groups/q/filter::filter,:offset', loadUser, data => {
        this._restAPI.getLoggedIn().then(loggedIn => {
          if (loggedIn) {
            this._setParams({
              view: Gerrit.Nav.View.ADMIN,
              adminView: 'gr-admin-group-list',
              offset: data.params.offset,
              filter: data.params.filter,
            });
          } else {
            this._redirectToLogin(data.canonicalPath);
          }
        });
      });

      page('/admin/groups/q/filter::filter', loadUser, data => {
        this._restAPI.getLoggedIn().then(loggedIn => {
          if (loggedIn) {
            this._setParams({
              view: Gerrit.Nav.View.ADMIN,
              adminView: 'gr-admin-group-list',
              filter: data.params.filter || null,
            });
          } else {
            this._redirectToLogin(data.canonicalPath);
          }
        });
      });

      // Matches /admin/groups/<group>
      page(/^\/admin\/groups\/([^,]+)$/, loadUser, data => {
        this._restAPI.getLoggedIn().then(loggedIn => {
          if (loggedIn) {
            this._setParams({
              view: Gerrit.Nav.View.ADMIN,
              adminView: 'gr-group',
              groupId: data.params[0],
            });
          } else {
            this._redirectToLogin(data.canonicalPath);
          }
        });
      });

      // Matches /admin/projects/<project>,commands.
      page(/^\/admin\/projects\/(.+),commands$/, loadUser, data => {
        this._restAPI.getLoggedIn().then(loggedIn => {
          if (loggedIn) {
            this._setParams({
              view: Gerrit.Nav.View.ADMIN,
              adminView: 'gr-project-commands',
              detailType: 'commands',
              project: data.params[0],
            });
          } else {
            this._redirectToLogin(data.canonicalPath);
          }
        });
      });

      // Matches /admin/projects/<project>,branches[,<offset>].
      page(/^\/admin\/projects\/(.+),branches(,(.+))?$/, loadUser, data => {
        this._setParams({
          view: Gerrit.Nav.View.ADMIN,
          adminView: 'gr-project-detail-list',
          detailType: 'branches',
          project: data.params[0],
          offset: data.params[2] || 0,
          filter: null,
        });
      });

      page('/admin/projects/:project,branches/q/filter::filter,:offset',
          loadUser, data => {
            this._setParams({
              view: Gerrit.Nav.View.ADMIN,
              adminView: 'gr-project-detail-list',
              detailType: 'branches',
              project: data.params.project,
              offset: data.params.offset,
              filter: data.params.filter,
            });
          });

      page('/admin/projects/:project,branches/q/filter::filter',
          loadUser, data => {
            this._setParams({
              view: Gerrit.Nav.View.ADMIN,
              adminView: 'gr-project-detail-list',
              detailType: 'branches',
              project: data.params.project,
              filter: data.params.filter || null,
            });
          });

      // Matches /admin/projects/<project>,tags[,<offset>].
      page(/^\/admin\/projects\/(.+),tags(,(.+))?$/, loadUser, data => {
        this._setParams({
          view: Gerrit.Nav.View.ADMIN,
          adminView: 'gr-project-detail-list',
          detailType: 'tags',
          project: data.params[0],
          offset: data.params[2] || 0,
          filter: null,
        });
      });

      page('/admin/projects/:project,tags/q/filter::filter,:offset',
          loadUser, data => {
            this._setParams({
              view: Gerrit.Nav.View.ADMIN,
              adminView: 'gr-project-detail-list',
              detailType: 'tags',
              project: data.params.project,
              offset: data.params.offset,
              filter: data.params.filter,
            });
          });

      page('/admin/projects/:project,tags/q/filter::filter',
          loadUser, data => {
            this._setParams({
              view: Gerrit.Nav.View.ADMIN,
              adminView: 'gr-project-detail-list',
              detailType: 'tags',
              project: data.params.project,
              filter: data.params.filter || null,
            });
          });

      // Matches /admin/projects[,<offset>][/].
      page(/^\/admin\/projects(,(\d+))?(\/)?$/, loadUser, data => {
        this._setParams({
          view: Gerrit.Nav.View.ADMIN,
          adminView: 'gr-admin-project-list',
          offset: data.params[1] || 0,
          filter: null,
        });
      });

      page('/admin/projects/q/filter::filter,:offset', loadUser, data => {
        this._setParams({
          view: Gerrit.Nav.View.ADMIN,
          adminView: 'gr-admin-project-list',
          offset: data.params.offset,
          filter: data.params.filter,
        });
      });

      page('/admin/projects/q/filter::filter', loadUser, data => {
        this._setParams({
          view: Gerrit.Nav.View.ADMIN,
          adminView: 'gr-admin-project-list',
          filter: data.params.filter || null,
        });
      });

      // Matches /admin/projects/<project>
      page(/^\/admin\/projects\/([^,]+)$/, loadUser, data => {
        this._setParams({
          view: Gerrit.Nav.View.ADMIN,
          project: data.params[0],
          adminView: 'gr-project',
        });
      });

      // Matches /admin/plugins[,<offset>][/].
      page(/^\/admin\/plugins(,(\d+))?(\/)?$/, loadUser, data => {
        this._restAPI.getLoggedIn().then(loggedIn => {
          if (loggedIn) {
            this._setParams({
              view: Gerrit.Nav.View.ADMIN,
              adminView: 'gr-plugin-list',
              offset: data.params[1] || 0,
              filter: null,
            });
          } else {
            this._redirectToLogin(data.canonicalPath);
          }
        });
      });

      page('/admin/plugins/q/filter::filter,:offset', loadUser, data => {
        this._restAPI.getLoggedIn().then(loggedIn => {
          if (loggedIn) {
            this._setParams({
              view: Gerrit.Nav.View.ADMIN,
              adminView: 'gr-plugin-list',
              offset: data.params.offset,
              filter: data.params.filter,
            });
          } else {
            this._redirectToLogin(data.canonicalPath);
          }
        });
      });

      page('/admin/plugins/q/filter::filter', loadUser, data => {
        this._restAPI.getLoggedIn().then(loggedIn => {
          if (loggedIn) {
            this._setParams({
              view: Gerrit.Nav.View.ADMIN,
              adminView: 'gr-plugin-list',
              filter: data.params.filter || null,
            });
          } else {
            this._redirectToLogin(data.canonicalPath);
          }
        });
      });

      page(/^\/admin\/plugins(\/)?$/, loadUser, data => {
        this._restAPI.getLoggedIn().then(loggedIn => {
          if (loggedIn) {
            this._setParams({
              view: Gerrit.Nav.View.ADMIN,
              adminView: 'gr-plugin-list',
            });
          } else {
            this._redirectToLogin(data.canonicalPath);
          }
        });
      });

      page('/admin/(.*)', loadUser, data => {
        this._restAPI.getLoggedIn().then(loggedIn => {
          if (loggedIn) {
            data.params.view = Gerrit.Nav.View.ADMIN;
            data.params.placeholder = true;
            this._setParams(data.params);
          } else {
            this._redirectToLogin(data.canonicalPath);
          }
        });
      });

      const queryHandler = data => {
        data.params.view = Gerrit.Nav.View.SEARCH;
        this._setParams(data.params);
      };

      page('/q/:query,:offset', queryHandler);
      page('/q/:query', queryHandler);

      page(/^\/(\d+)\/?/, ctx => {
        this._redirect('/c/' + encodeURIComponent(ctx.params[0]));
      });

      // Matches
      // /c/<project>/+/<changeNum>/
      //     [<basePatchNum|edit>..][<patchNum|edit>]/[path].
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
              view: ctx.params[8] ?
                Gerrit.Nav.View.DIFF : Gerrit.Nav.View.CHANGE,
              hash: ctx.hash,
            };
            const needsRedirect = this._normalizePatchRangeParams(params);
            if (needsRedirect) {
              this._redirect(this._generateUrl(params));
            } else {
              this._setParams(params);
              this._restAPI.setInProjectLookup(params.changeNum,
                  params.project);
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

        this._normalizeLegacyRouteParams(params);
      });

      // Matches /c/<changeNum>/[<basePatchNum>..]<patchNum>/<path>.
      page(/^\/c\/(\d+)\/((\d+|edit)(\.\.(\d+|edit))?)\/(.+)/, ctx => {
        // Check if path has an '@' which indicates it was using GWT style line
        // numbers. Even if the filename had an '@' in it, it would have already
        // been URI encoded. Redirect to hash version of path.
        if (ctx.path.includes('@')) {
          this._redirect(ctx.path.replace('@', '#'));
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

        this._normalizeLegacyRouteParams(params);
      });

      page(/^\/settings\/(agreements|new-agreement)/, loadUser, data => {
        this._restAPI.getLoggedIn().then(loggedIn => {
          if (loggedIn) {
            data.params.view = Gerrit.Nav.View.AGREEMENTS;
            this._setParams(data.params);
          } else {
            this._redirectToLogin(data.canonicalPath);
          }
        });
      });

      page(/^\/settings\/VE\/(\S+)/, data => {
        this._restAPI.getLoggedIn().then(loggedIn => {
          if (loggedIn) {
            this._setParams({
              view: Gerrit.Nav.View.SETTINGS,
              emailToken: data.params[0],
            });
          } else {
            this._redirectToLogin(data.canonicalPath);
          }
        });
      });

      page(/^\/settings\/?/, data => {
        this._restAPI.getLoggedIn().then(loggedIn => {
          if (loggedIn) {
            this._setParams({view: Gerrit.Nav.View.SETTINGS});
          } else {
            this._redirectToLogin(data.canonicalPath);
          }
        });
      });

      page(/^\/register(\/.*)?/, ctx => {
        this._setParams({justRegistered: true});
        const path = ctx.params[0] || '/';
        if (path[0] !== '/') { return; }
        page.show(base + path);
      });

      page.start();
    },
  });
})();
