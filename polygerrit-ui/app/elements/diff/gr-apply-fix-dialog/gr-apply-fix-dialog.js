/**
 * @fileoverview Description of this file.
 */
(function() {
    'use strict'

    Polymer({
        is: 'gr-apply-fix-dialog',
        _legacyUndefinedCheck: true,
        properties: {
        },

        behaviors: [
        Gerrit.FireBehavior,
        ],
        open() {
            this.$.applyFixOverlay.open();
        }

    })
})();
