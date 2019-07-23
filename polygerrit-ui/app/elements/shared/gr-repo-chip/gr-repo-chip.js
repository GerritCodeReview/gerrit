(function() {
  'use strict';

  Polymer({
    is: 'gr-repo-chip',
    _legacyUndefinedCheck: true,
    properties: {
      repo: Object,
      removable: {
        type: Boolean,
        value: true,
      },
    },
    attached() {
      console.log("repo chip attached");
    },
    _handleRemoveTap(e) {
      e.preventDefault();
      this.fire('remove', {repo: this.repo});
    },      
    _getBackgroundClass(transparent) {
      return transparent ? 'transparentBackground' : '';
    },
  })
  
})();