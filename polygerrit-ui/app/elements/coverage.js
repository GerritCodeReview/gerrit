// This will not be checked in. It is for testing what a plugin would look like.
(function(window) {
  'use strict';

  // Gerrit may not be defined (say, in a test environment).
  if (window.Gerrit) {

    Gerrit.install(plugin => {
      // High-level API
      plugin.layerHelper()
          .addLayer((element) => {

            // TODO(rmistry): Make a call to the code coverage server with
            // element.changeNum, element.patchNum, element.path.

            var isLineMatching = function(el, line) {
              if (line.afterNumber % 2 == 0) {
                return false
              }
              if (line.type === GrDiffLine.Type.ADD || (
                  line.type === GrDiffLine.Type.BOTH &&
                  el.getAttribute('data-side') === 'right')) {
                return true
              }
            };

            var layerFunc = function(el, line) {
              if (!isLineMatching(el, line)) {
                return;
              }

              // TODO(rmistry): How do we dynamically add a custom CSS
              // class to the below?
              GrAnnotation.annotateElement(
                  el, 0, GrAnnotation.getStringLength(line.text),
                  'style-scope gr-diff trailing-whitespace');
            };

            return layerFunc;
          });
      });
  }
})(window);
