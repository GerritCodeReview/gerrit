(function(window, document) {
  'use strict';

  const CR1 = {
    side: 'right',
    type: 'COVERED',
    code_range: {
      start_line: 1,
      end_line: 3,
    },
  };
  const CR2 = {
    side: 'right',
    type: 'NOT_COVERED',
    code_range: {
      start_line: 7,
      end_line: 9,
    },
  };
  const CR3 = {
    side: 'right',
    type: 'PARTIALLY_COVERED',
    code_range: {
      start_line: 13,
      end_line: 15,
    },
  };

  if (window.Gerrit) {
    Gerrit.install(function(plugin) {
      const annotationApi = plugin.annotationApi();
      annotationApi.setCoverageProvider(
          (changeNum, path, basePatchNum, patchNum) =>
              [CR1, CR2, CR3],
      );
    });
  }
})(window, document);
