/**
 * @fileoverview Description of this file.
 */
(function() {
  'use strict';
  Polymer({
    is: 'gr-apply-fix-dialog',
    _legacyUndefinedCheck: true,
    properties: {
      changeNum: String,
    },

    behaviors: [
      Gerrit.FireBehavior,
    ],
    open(e) {
      const changeNum = this.changeNum;
      const patchNum = e.detail.patchNum;
      const fixSuggestions = e.detail.comment.fix_suggestions;
      // only show fix preview of first fix suggestion
      if (fixSuggestions != null && fixSuggestions[0] &&
        changeNum != null && patchNum != null) {
        this._fetchFixPreview(changeNum, patchNum, fixSuggestions[0].fix_id);
        this.$.applyFixOverlay.open();
      }
    },
    _fetchFixPreview(changeNum, patchNum, fixId) {
      this.$.restAPI.getRobotCommentFixPreview(changeNum, patchNum, fixId)
          .then(res => {
            console.log(res);
          });
    },

  });
})();
