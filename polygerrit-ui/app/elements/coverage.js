(function() {
  'use strict';

  function CoveragePlugin(gerritPlugin) {
    this.gerritPlugin_ = gerritPlugin;

    var coverageData = {};
    this._annotationApi = this.gerritPlugin_.annotationApi()
      .addNotifier(notifyFunc => {
        new Promise(resolve => setTimeout(resolve, 3000)).then(
          () => {
            populateWithDummyData(coverageData);
            Object.keys(coverageData).forEach(function(file) {
              notifyFunc(file, 0, coverageData[file].totalLines, 'right');
            });
          });
      }).addLayer(context => {
        if (Object.keys(coverageData).length === 0) {
          // Coverage data is not ready yet.
          return;
        }
        const path = context.path;
        const line = context.line;
        // Highlight lines missing coverage with this background color.
        const cssClass = Gerrit.css('background-color: #EF9B9B');
        if (coverageData[path] &&
            coverageData[path].changeNum === context.changeNum &&
            coverageData[path].patchNum === context.patchNum) {
          var linesMissingCoverage = coverageData[path].linesMissingCoverage;
          if (linesMissingCoverage.includes(line.afterNumber)) {
            context.annotateRange(0, line.text.length, cssClass);
          }
        }
      });
  }

  function populateWithDummyData(coverageData) {
    coverageData['NewFile'] = {
        'linesMissingCoverage': [1, 2, 3],
        'totalLines': 5,
        'changeNum': 94,
        'patchNum': 2,
    };
    coverageData['/COMMIT_MSG'] = {
        'linesMissingCoverage': [3, 4, 7, 14],
        'totalLines': 14,
        'changeNum': 94,
        'patchNum': 2,
    };
  }

  // Gerrit may not be defined (say, in a test environment).
  if (window.Gerrit) {
    Gerrit.install(function(self) {
      // coverage.js is only supported in PolyGerrit.
      if (!window.Polymer) { return; }

      const coveragePlugin = new CoveragePlugin(self);
    }, '0.1');
  }

  window.__CoveragePlugin = window.__CoveragePlugin || CoveragePlugin;
})(window);
