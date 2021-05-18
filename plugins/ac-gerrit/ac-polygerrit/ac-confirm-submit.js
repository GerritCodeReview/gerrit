<dom-module id="submit-for-another">
  <template>
    <template is="dom-if" if="[[otherUser]]">
      <p style="color:red"><strong>Heads Up!</strong> Submitting [[otherUser]]'s change.</p>
    </template>
    <template is="dom-if" if="[[otherChange]]">
      <p style="color:red"><strong>Heads Up!</strong> Change was found in SuperModule. This will also submit change [[otherChange]].</p>
    </template>
  </template>
  <script>
    Polymer({
      is: "submit-for-another",
      properties: {
        otherUser: String,
        otherChange: String,
        change: Object
      },

      attached() {
        this.otherChange = this.plugin.otherChange;
        Gerrit.get('/accounts/self', u => {
          const accountID = u._account_id;
          if (accountID != this.change.owner._account_id)
            this.otherUser = this.change.owner.name;
          else
            this.otherUser = null;
        });
      }
    });
  </script>
</dom-module>
