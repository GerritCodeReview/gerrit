// Copyright (C) 2015 The Android Open Source Project
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

(function(document) {
  'use strict';

  // See https://github.com/Polymer/polymer/issues/1381
  window.addEventListener('WebComponentsReady', function() {
    // Middleware
    function scrollToTop(ctx, next) {
      document.body.scrollTop = 0;
      next();
    }

    // Routes.
    page('/', function() {
      page.redirect('/q/status:open');
    });

    function queryHandler(data) {
      app.route = 'gr-change-list';
      app.params = data.params;
    }

    page('/q/:query,:offset', scrollToTop, queryHandler);
    page('/q/:query', scrollToTop, queryHandler);

    page(/^\/(\d+)\/?/, scrollToTop, function(ctx) {
      page.redirect('/c/' + ctx.params[0]);
    });

    page('/c/:changeNum', scrollToTop, function(data) {
      app.route = 'gr-change-view';
      app.params = data.params;
    });

    page(/^\/c\/(\d+)\/((\d+)(\.\.(\d+))?)\/(.+)/, scrollToTop, function(ctx) {
      app.route = 'gr-diff-view';
      var params = {
        changeNum: ctx.params[0],
        basePatchNum: ctx.params[2],
        patchNum: ctx.params[4],
        path: ctx.params[5]
      };
      // Don’t allow diffing the same patch number against itself because WHY?
      if (params.basePatchNum == params.patchNum) {
        page.redirect('/c/' + params.changeNum + '/' + params.patchNum + '/' +
            params.path);
        return;
      }
      if (!params.patchNum) {
        params.patchNum = params.basePatchNum;
        delete(params.basePatchNum);
      }
      app.params = params;
    });

    page.start();
  });

})(document);
