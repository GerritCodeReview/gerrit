(function() {
  'use strict';

  function CoveragePlugin(gerritPlugin) {
    this.gerritPlugin_ = gerritPlugin;
  }

  CoveragePlugin.prototype.addEventListeners = function() {
    this.gerritPlugin_.on('annotatediff', this.getLayer.bind(this));
  };


  CoveragePlugin.prototype.getLayer = function(path, changeNum, patchNum) {
    // TODO(rmistry): Make one call to code coverage server with path,
    // changeNum, patchNum when page loads.
    const isLineMatching = function(el, line) {
      if (line.afterNumber % 2 == 0) {
        return false;
      }
      if (line.type === GrDiffLine.Type.ADD || (
          line.type === GrDiffLine.Type.BOTH &&
          el.getAttribute('data-side') === 'right')) {
        return true;
      }
    };

    const layerFunc = function(el, line) {
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
  };

  // Gerrit may not be defined (say, in a test environment).
  if (window.Gerrit) {
    Gerrit.install(function(self) {
      // coverage.js is only supported in PolyGerrit.
      if (!window.Polymer) { return; }

      const coveragePlugin = new CoveragePlugin(self);
      coveragePlugin.addEventListeners();
    }, '0.1');
  }

  window.__CoveragePlugin = window.__CoveragePlugin || CoveragePlugin;
})(window);
