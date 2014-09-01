<dom-module id="ac-submit-with-super-dialog">
  <template>
    <style include="shared-styles">
      #dialog {
        min-width: 40em;
      }
      p {
        margin-bottom: 1em;
      }
      @media screen and (max-width: 50em) {
        #dialog {
          min-width: inherit;
          width: 100%;
        }
      }
    </style>
    <gr-confirm-submit-dialog
        id="dialog"
        confirm-label="Continue"
        confirm-on-enter
        change="[[change]]"
        action="[[action]]"
        on-cancel="_handleCancelTap"
        on-confirm="_handleConfirmTap" />
  </template>
  <script>
    'use strict';

    var acconfirmdialog = Polymer({
      is: 'ac-submit-with-super-dialog',

      properties: {
        change: Object,
        action: Object
      },

      attached: function() {
        this.plugin.custom_popup = this;
      },

      resetFocus(e) {
        this.$.dialog.resetFocus();
      },

      doSubmit: function(changeNum) {
        return this.plugin.restApi().post('/changes/' + changeNum + '/revisions/current/submit');
      },

      doSubmitToSubModule: function(e) {
        this.$.dialog.disabled = true;
        e.preventDefault();
        this.plugin.restApi().post(this.get('action').__url, {})
        .then((ok_resp) => {
          this.$.dialog.disabled = false;
          this.plugin.custom_popup_promise.close();
          this.plugin.custom_popup_promise = null;
          window.location.reload(true);
        }).catch((errText) => {
          this.fire('show-error', {
            message: `Could not perform action: ${errText}`
          });
          this.$.dialog.disabled = false;
        });
      },

      _handleConfirmTap(e) {

        findGerritChanges(this, this.change, "Submit", "", "").then(() => {
          for (var j = 0; j < this.SubModules.length; j++)
            if (superModules.includes(this.SubModules[j].name)) {
              const changeNum = this.SubModules[j].branches[0].value
              if (this.plugin.otherChange != changeNum) {
                alert ("An error was detected, please refresh your Gerrit page");
                console.log ("Error, user need refresh page, found chage: " + changeNum + ", instead of change: " + this.plugin.otherChange);
                return;
              }
              this.doSubmit(changeNum).then(() => {
                console.log ("Change " + changeNum + " was Submited");
                this.doSubmitToSubModule(e);
              }, alert);
              return;
            }

        this.doSubmitToSubModule(e);
      });
      },

      _handleCancelTap(e) {
        e.preventDefault();
        this.plugin.custom_popup_promise.close();
        this.plugin.custom_popup_promise = null;
      },
    });
  </script>
</dom-module>

