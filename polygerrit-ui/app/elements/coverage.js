(function(window) {
  'use strict';

  // Gerrit may not be defined (say, in a test environment).
  if (window.Gerrit) {

    Gerrit.install(plugin => {
      // High-level API
      plugin.diffHelper()
          .createCommand((content) => {
            console.log('-- Logs from coverage.js plugin --');
            console.log(content);
            console.log(content.changeNum);
            console.log(document.getElementsByClassName("style-scope gr-diff right lineNum").length)
          });

      // Low-level API
      // plugin.registerCustomComponent(
      //     'project-command', 'project-command-low');
    });
  }
})(window);
