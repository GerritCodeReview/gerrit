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

    page(/^\/c\/(\d+)\/(\d+)\/(.+)/, scrollToTop, function(ctx) {
      app.route = 'gr-diff-view';
      var params = {
        changeNum: ctx.params[0],
        patchNum: ctx.params[1],
        path: ctx.params[2]
      };
      app.params = params;
    });

    page.start();
  });

})(document);
