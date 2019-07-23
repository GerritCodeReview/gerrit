(function() {
  'use strict';
  Polymer({
    is: 'gr-checks-list',
    properties: {
      /**
       * URL params passed from the router.
       */
      params: {
          type: Object,
          observer: '_paramsChanged',
      },

      /**
       * Offset of currently visible query results.
       */
      _offset: Number,
      _checks: Array,
      _loading: {
        type: Boolean,
        value: true,
      },
      _filter: {
        type: String,
        value: '',
      },
      _checksPerPage: {
        type: Number,
        value: 25,
      },
      _path: {
        type: String,
        readOnly: true,
        value: '/admin/checks',
      },
      _shownChecks: {
        type: Array,
      },
      _createNewCapability: {
        type: Boolean,
        value: false,
      },
    },
    observers: [
      '_showChecks(_checks, _filter)',
    ],
    behaviors: [
      Gerrit.ListViewBehavior,
    ],      

    attached() {
      this._getChecks('');
      this._getCreateCheckerCapability()
    },

    _paramsChanged() {
      this._filter = this.params.filter || "";
    },

    _showChecks(_checks, _filter) {
      this._shownChecks = this._checks.filter(
        check => { return check.name.toLowerCase().indexOf(this._filter) !== -1 } 
      )
    },

    _getChecks(filter, checksPerPage, offset) {
      this._checks = [];
      return this.$.restAPI.getChecks(filter, checksPerPage, offset)
        .then(checks => {
          // Late response.
          if (filter !== this._filter || !checks) { return; }
          this._checks = checks;
          this._loading = false;
        });
    },

    computeBlocking(check) {
      return (check && check.blocking && check.blocking.length > 0)? "YES": "NO";
    },

    _getCreateCheckerCapability() {
      return this.$.restAPI.getAccount().then(account => {
        if (!account) { return; }
        return this.$.restAPI.getAccountCapabilities(['checks-administrateCheckers'])
            .then(capabilities => {
              console.log(capabilities);
              if (capabilities['checks-administrateCheckers']) {
                this._createNewCapability = true;
              }
            });
      });
    },

    _handleCreateChecker() {
      this.$.createNewModal._handleCreateChecker();
    },

    _handleCreateClicked() {
      this.$.createOverlay.open();
    },

    _handleCloseCreate() {
      this.$.createOverlay.close();
    },

    _handleOnCancel() {
      this.$.createOverlay.close();
    },

  })
})();