(function() {
  'use strict';

  function CoveragePlugin(gerritPlugin) {
    this.gerritPlugin_ = gerritPlugin;
  }

  CoveragePlugin.prototype.addEventListeners = function() {
    this.gerritPlugin_.on('annotatediff', this.getLayer.bind(this));
  };

  CoveragePlugin.prototype.getLayer = function(path, changeNum, patchNum) {
    return new AnnotationLayer(path, changeNum, patchNum);
  };

  /* Instance of the Annotation Layer interface */

  function AnnotationLayer(path, changeNum, patchNum) {
    this._listeners = [];
    this._path = path;
    this._changeNum = changeNum;
    this._patchNum = patchNum;

    this._receivedCoverageData = false;
    this.callCoverageServer();
  }

  AnnotationLayer.prototype.callCoverageServer = function() {
    // TODO(rmistry): Simulating a delay from the server here.
    var p = new Promise(resolve => setTimeout(resolve, 3000));
    p.then(function() {
      // TODO(rmistry): Make one call to code coverage server with path,
      // changeNum, patchNum when page loads.
      const linesInFile = 100;  // TODO(rmistry): Will get this from code coverage server.
      this._receivedCoverageData = true;
      this.notifyUpdate(linesInFile);
    }.bind(this));
  };

  AnnotationLayer.prototype.addListener = function(fn) {
    this._listeners.push(fn);
  };

  AnnotationLayer.prototype.notifyUpdate = function(linesInFile) {
    for (const listener of this._listeners) {
      listener(0, linesInFile, 'right');
    }
  };

  AnnotationLayer.prototype.annotate = function(el, line) {
    if (!this._receivedCoverageData) {
      // We do not have coverage data yet.
      return;
    }

    const isLineMatching = function(el, line) {
      if (line.afterNumber % 2 == 0) {
        return false;
      }

      if (line.type === 'add' || line.type === 'both') {
        return true;
      }
    };

    if (!isLineMatching(el, line)) {
      return;
    }

    // TODO(rmistry): How do I create and use my own CSS class?
    line.annotateElement(el, 'trailing-whitespace');
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
