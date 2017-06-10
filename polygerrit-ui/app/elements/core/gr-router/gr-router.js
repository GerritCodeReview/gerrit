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

  function startRouter() {
    const base = window.Gerrit.BaseUrlBehavior.getBaseUrl();
    if (base) {
      page.base(base);
    }

    const restAPI = document.createElement('gr-rest-api-interface');
    const reporting = getReporting();

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
        let newUrl = data.hash;
        if (newUrl.startsWith('/VE/')) {
          newUrl = '/settings' + data.hash;
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

    page('/dashboard/(.*)', loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          data.params.view = 'gr-dashboard-view';
          app.params = data.params;
        } else {
          if (base) {
            page('/login/' + encodeURIComponent(
                data.canonicalPath.substring(base.length)));
          } else {
            page.redirect('/login/' + encodeURIComponent(data.canonicalPath));
          }
        }
      });
    });

    // Matches /admin/groups[,<offset>][/].
    page(/^\/admin\/groups(,(\d+))?(\/)?$/, loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          app.params = {
            view: 'gr-admin-group-list',
            offset: data.params[1] || 0,
            filter: null,
          };
        } else {
          if (base) {
            page('/login/' + encodeURIComponent(
                data.canonicalPath.substring(base.length)));
          } else {
            page.redirect('/login/' + encodeURIComponent(data.canonicalPath));
          }
        }
      });
    });

    page('/admin/groups/q/filter::filter,:offset', loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          app.params = {
            view: 'gr-admin-group-list',
            offset: data.params.offset,
            filter: data.params.filter,
          };
        } else {
          if (base) {
            page('/login/' + encodeURIComponent(
                data.canonicalPath.substring(base.length)));
          } else {
            page.redirect('/login/' + encodeURIComponent(data.canonicalPath));
          }
        }
      });
    });

    page('/admin/groups/q/filter::filter', loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          app.params = {
            view: 'gr-admin-group-list',
            filter: data.params.filter || null,
          };
        } else {
          if (base) {
            page('/login/' + encodeURIComponent(
                data.canonicalPath.substring(base.length)));
          } else {
            page.redirect('/login/' + encodeURIComponent(data.canonicalPath));
          }
        }
      });
    });

    // Matches /admin/projects[,<offset>][/].
    page(/^\/admin\/projects(,(\d+))?(\/)?$/, loadUser, data => {
      app.params = {
        view: 'gr-admin-project-list',
        offset: data.params[1] || 0,
        filter: null,
      };
    });

    page('/admin/projects/q/filter::filter,:offset', loadUser, data => {
      app.params = {
        view: 'gr-admin-project-list',
        offset: data.params.offset,
        filter: data.params.filter,
      };
    });

    page('/admin/projects/q/filter::filter', loadUser, data => {
      app.params = {
        view: 'gr-admin-project-list',
        filter: data.params.filter || null,
      };
    });

    // Matches /admin/projects/<project>
    page(/^\/admin\/projects\/(.+)$/, loadUser, data => {
      app.params = {
        view: 'gr-admin-project',
        project: data.params[0],
      };
    });

    page(/^\/admin\/plugins(\/)?$/, loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          app.params = {
            view: 'gr-admin-plugin-list',
          };
        } else {
          page.redirect('/login/' + encodeURIComponent(data.canonicalPath));
        }
      });
    });

    page('/admin/(.*)', loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          data.params.view = 'gr-admin-view';
          app.params = data.params;
        } else {
          if (base) {
            page('/login/' + encodeURIComponent(
                data.canonicalPath.substring(base.length)));
          } else {
            page.redirect('/login/' + encodeURIComponent(data.canonicalPath));
          }
        }
      });
    });

    function queryHandler(data) {
      data.params.view = 'gr-change-list-view';
      app.params = data.params;
    }

    page('/q/:query,:offset', queryHandler);
    page('/q/:query', queryHandler);

    page(/^\/(\d+)\/?/, ctx => {
      page.redirect('/c/' + encodeURIComponent(ctx.params[0]));
    });

    function normalizePatchRangeParams(params) {
      if (params.basePatchNum && !params.patchNum) {
        params.patchNum = params.basePatchNum;
        params.basePatchNum = null;
      }
    }

    // Matches /c/<changeNum>/[<basePatchNum>..][<patchNum>].
    page(/^\/c\/(\d+)\/?(((\d+)(\.\.(\d+))?))?$/, ctx => {
      // Parameter order is based on the regex group number matched.
      const params = {
        changeNum: ctx.params[0],
        basePatchNum: ctx.params[3],
        patchNum: ctx.params[5],
        view: 'gr-change-view',
      };

      // Don't allow diffing the same patch number against itself.
      if (params.basePatchNum != null &&
          params.basePatchNum === params.patchNum) {
        page.redirect('/c/' +
            encodeURIComponent(params.changeNum) +
            '/' +
            encodeURIComponent(params.patchNum) +
            '/');
        return;
      }
      normalizePatchRangeParams(params);
      app.params = params;
    });

    // Matches /c/<changeNum>/[<basePatchNum>..]<patchNum>/<path>.
    page(/^\/c\/(\d+)\/((\d+)(\.\.(\d+))?)\/(.+)/, ctx => {
      // Parameter order is based on the regex group number matched.
      const params = {
        changeNum: ctx.params[0],
        basePatchNum: ctx.params[2],
        patchNum: ctx.params[4],
        path: ctx.params[5],
        view: 'gr-diff-view',
      };
      // Don't allow diffing the same patch number against itself.
      if (params.basePatchNum === params.patchNum) {
        // TODO(kaspern): Utilize gr-url-encoding-behavior.html when the router
        // is replaced with a Polymer counterpart.
        // @see Issue 4255 regarding double-encoding.
        let path = encodeURIComponent(encodeURIComponent(params.path));
        // @see Issue 4577 regarding more readable URLs.
        path = path.replace(/%252F/g, '/');
        path = path.replace(/%2520/g, '+');

        page.redirect('/c/' +
            encodeURIComponent(params.changeNum) +
            '/' +
            encodeURIComponent(params.patchNum) +
            '/' +
            path);
        return;
      }

      // Check if path has an '@' which indicates it was using GWT style line
      // numbers. Even if the filename had an '@' in it, it would have already
      // been URI encoded. Redirect to hash version of path.
      if (ctx.path.includes('@')) {
        page.redirect(ctx.path.replace('@', '#'));
        return;
      }

      normalizePatchRangeParams(params);
      app.params = params;
    });

    page(/^\/settings\/(agreements|new-agreement)/, loadUser, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          data.params.view = 'gr-cla-view';
          app.params = data.params;
        } else {
          page.redirect('/login/' + encodeURIComponent(data.canonicalPath));
        }
      });
    });

    page(/^\/settings\/VE\/(\S+)/, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          app.params = {
            view: 'gr-settings-view',
            emailToken: data.params[0],
          };
        } else {
          page.show('/login/' + encodeURIComponent(data.canonicalPath));
        }
      });
    });

    page(/^\/settings\/?/, data => {
      restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          app.params = {view: 'gr-settings-view'};
        } else {
          page.show('/login/' + encodeURIComponent(data.canonicalPath));
        }
      });
    });

    page(/^\/register(\/.*)?/, ctx => {
      app.params = {justRegistered: true};
      const path = ctx.params[0] || '/';
      page.show(path);
    });

    page.start();
  }

  Polymer({
    is: 'gr-router',
    start() {
      if (!app) { return; }
      startRouter();
    },
  });
})();
