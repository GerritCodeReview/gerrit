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
  var app = document.querySelector('#app');
  var restAPI = document.createElement('gr-rest-api-interface');

  window.addEventListener('WebComponentsReady', function() {
    // Middleware
    page(function(ctx, next) {
      document.body.scrollTop = 0;

      // Fire asynchronously so that the URL is changed by the time the event
      // is processed.
      app.async(function() {
        app.fire('location-change');
      }, 1);
      next();
    });

    function loadUser(ctx, next) {
      restAPI.getLoggedIn().then(function() {
        next();
      });
    }

    // Routes.
    page('/', loadUser, function(data) {
      if (data.querystring.match(/^closeAfterLogin/)) {
        // Close child window on redirect after login.
        window.close();
      }
      // For backward compatibility with GWT links.
      if (data.hash) {
        page.redirect(data.hash);
        return;
      }
      restAPI.getLoggedIn().then(function(loggedIn) {
        if (loggedIn) {
          page.redirect('/dashboard/self');
        } else {
          page.redirect('/q/status:open');
        }
      });
    });

    page('/dashboard/(.*)', loadUser, function(data) {
      restAPI.getLoggedIn().then(function(loggedIn) {
        if (loggedIn) {
          data.params.view = 'gr-dashboard-view';
          app.params = data.params;
        } else {
          page.redirect('/login/' + encodeURIComponent(data.canonicalPath));
        }
      });
    });

    function queryHandler(data) {
      data.params.view = 'gr-change-list-view';
      app.params = data.params;
    }

    page('/q/:query,:offset', queryHandler);
    page('/q/:query', queryHandler);

    page(/^\/(\d+)\/?/, function(ctx) {
      page.redirect('/c/' + encodeURIComponent(ctx.params[0]));
    });

    function normalizePatchRangeParams(params) {
      if (params.basePatchNum && !params.patchNum) {
        params.patchNum = params.basePatchNum;
        params.basePatchNum = null;
      }
    }

    // Matches /c/<changeNum>/[<basePatchNum>..][<patchNum>].
    page(/^\/c\/(\d+)\/?(((\d+)(\.\.(\d+))?))?$/, function(ctx) {
      // Parameter order is based on the regex group number matched.
      var params = {
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
    page(/^\/c\/(\d+)\/((\d+)(\.\.(\d+))?)\/(.+)/, function(ctx) {
      // Parameter order is based on the regex group number matched.
      var params = {
        changeNum: ctx.params[0],
        basePatchNum: ctx.params[2],
        patchNum: ctx.params[4],
        path: ctx.params[5],
        view: 'gr-diff-view',
      };
      // Don't allow diffing the same patch number against itself.
      if (params.basePatchNum === params.patchNum) {
        page.redirect('/c/' +
            encodeURIComponent(params.changeNum) +
            '/' +
            encodeURIComponent(params.patchNum) +
            '/' +
            encodeURIComponent(params.path));
        return;
      }
      normalizePatchRangeParams(params);
      app.params = params;
    });

    page(/^\/settings\/?/, function(data) {
      restAPI.getLoggedIn().then(function(loggedIn) {
        if (loggedIn) {
          app.params = {view: 'gr-settings-view'};
        } else {
          page.show('/login/' + encodeURIComponent(data.canonicalPath));
        }
      });
    });

    page.start();
  });
})();
