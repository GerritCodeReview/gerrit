/**
 * @fileoverview Description of this file.
 */
(function() {
  'use strict';
  Polymer({
    is: 'gr-apply-fix-dialog',
    _legacyUndefinedCheck: true,
    properties: {
      prefs: Array,
      change: Object,
      changeNum: String,
      patchNum: Number,
      robotId: String,
      currentFix: {type: Object, value: null},
      currentPreviews: {type: Array, value: []},
      fixSuggestions: Array, // all available fixes from single comment
    },
    behaviors: [
      Gerrit.FireBehavior,
    ],
    open(e) {
      this.patchNum = e.detail.patchNum;
      const fixSuggestions = e.detail.comment.fix_suggestions;
      this.fixSuggestions = fixSuggestions;
      this.robotId = e.detail.comment.robot_id;
      // select and fetch preview of first fix
      if (fixSuggestions != null && fixSuggestions[0] &&
        this.changeNum != null && this.patchNum != null) {
        this.showSelectedFixSuggestion(this.changeNum, this.patchNum,
            fixSuggestions[0]);
        this.$.applyFixOverlay.open()
        .then(() => {
          // ensures gr-overlay repositions overlay in center
          this.$.applyFixOverlay.fire('iron-resize');
        });
      }
    },
    showSelectedFixSuggestion(changeNum, patchNum, fixSuggestion) {
      this.currentFix = fixSuggestion;
      this.fetchFixPreview(changeNum, patchNum, fixSuggestion.fix_id);
    },
    fetchFixPreview(changeNum, patchNum, fixId) {
      this.$.restAPI.getRobotCommentFixPreview(changeNum, patchNum, fixId)
          .then(res => {
            if (res != null) {
              const previews = Object.keys(res).map(key =>
                ({filepath: key, preview: res[key]}));
              this.currentPreviews = previews;
            }
          }).catch(err => {
            console.error(err);
            this.dispatchEvent(new CustomEvent('show-error', {
              bubbles: true,
              composed: true,
              detail: {message: `Error generating fix preview: ${err}`},
            }));
            this.onCancel();
          });
    },
    overridePartialPrefs(prefs) {
      // generate a smaller gr-diff than fullscreen for dialog
      return Object.assign({}, prefs, {line_length: 50});
    },
    onCancel(e) {
      if (e) {
        e.stopPropagation();
      }
      // reset preview
      this.currentFix = null;
      this.currentPreviews = [];
      this.$.applyFixOverlay.close();
    },
    handleApplyFix(e) {
      e.stopPropagation();
      this.$.restAPI.applyFixSuggestion(this.changeNum, this.patchNum,
          this.currentFix.fix_id).then(res => {
            Gerrit.Nav.navigateToChange(this.change, 'edit', this.patchNum);
            this.$.applyFixOverlay.close();
          }).catch(err => {
            console.error(err);
            this.dispatchEvent(new CustomEvent('show-error', {
              bubbles: true,
              composed: true,
              detail: {message: `Error applying fix suggestion: ${err}`},
            }));

          });
    },

  });
})();
