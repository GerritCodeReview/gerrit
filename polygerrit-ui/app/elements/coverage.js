// This will not be checked in. It is for testing what a plugin would look like.
(function(window) {
  'use strict';

  // Gerrit may not be defined (say, in a test environment).
  if (window.Gerrit) {

    Gerrit.install(plugin => {
      // High-level API
      plugin.layerHelper()
          .addSyntaxLayer((content) => {

            // TODO(rmistry): Make a call to the code coverage server with
            // content.changeNum, content.patchNum, content.diffPath.

            content.addLayer(function() {
              return {
                annotate(el, line) {
                  if (line.afterNumber % 2 == 0) {
                    return
                  }
                  if (line.type === GrDiffLine.Type.ADD || (
                      line.type === GrDiffLine.Type.BOTH &&
                      el.getAttribute('data-side') === 'right')) {
                    // TODO(rmistry): How do we dynamically add a custom CSS
                    //class to the below?
                    GrAnnotation.annotateElement(
                        el, 0, GrAnnotation.getStringLength(line.text),
                        'style-scope gr-diff trailing-whitespace');
                  }
                },
              };
            });
          });
        });
  }
})(window);
