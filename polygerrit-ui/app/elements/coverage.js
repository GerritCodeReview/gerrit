(function() {
  'use strict';

  function CoveragePlugin(gerritPlugin) {
    this.gerritPlugin_ = gerritPlugin;

    var coverageData = {}
    this._annotationApi = this.gerritPlugin_.annotationApi()
      .addNotifier(notifyFunc => {
        new Promise(resolve => setTimeout(resolve, 3000)).then(
          () => {
            populateWithDummyData(coverageData);
            Object.keys(coverageData).forEach(function(file) {
              notifyFunc(file, 0, coverageData[file].totalLines, 'right');
            });
          });
      }).addLayer((el, line, path, changeNum, patchNum, annotateRangeFunc) => {
        if (Object.keys(coverageData).length === 0) {
          // Coverage data is not ready yet.
          return;
        }
        if (coverageData[path] &&
            coverageData[path].changeNum === parseInt(changeNum) &&
            coverageData[path].patchNum === parseInt(patchNum)) {
          var linesMissingCoverage = coverageData[path].linesMissingCoverage
          if (linesMissingCoverage.includes(line.afterNumber)) {
            annotateRangeFunc(0, line.text.length, el, 'trailing-whitespace');
          }
        }
      });
  }

  function populateWithDummyData(coverageData) {
    coverageData['NewFile'] = {
        'linesMissingCoverage': [1,2,3],
        'totalLines': 5,
        'changeNum': 94,
        'patchNum': 2,
    }
    coverageData['/COMMIT_MSG'] = {
        'linesMissingCoverage': [3, 4, 7, 14],
        'totalLines': 14,
        'changeNum': 94,
        'patchNum': 2,
    }
  }

  CoveragePlugin.prototype.addEventListeners = function() {
    this.gerritPlugin_.on('annotatediff', this._annotationApi);
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
