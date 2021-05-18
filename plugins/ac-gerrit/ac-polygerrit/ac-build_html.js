<dom-module id="ac-build-dialog">
  <template>
    <style include="shared-styles">
      #dialog {
        min-width: 40em;
      }

      p {
        margin-bottom: 1em;
      }

      .chapter-border {
        border-bottom: 1px solid #ddd;
        padding-bottom: 10px;
      }

      @media screen and (max-width: 50em) {
        #dialog {
          min-width: inherit;
          width: 100%;
        }
      }

      .tooltip {
        position: relative;
        display: inline-block;
      }

      .tooltip .tooltiptext {
        visibility: hidden;
        width: 250px;
        background-color: #f8f8b6;
        color: #949468;
        text-align: center;
        padding: 5px 0;
        margin-left: 250px;
        /* Position the tooltip */
        position: absolute;
        z-index: 1;
      }

      .tooltip:hover .tooltiptext {
        visibility: visible;
      }
    </style>
    <gr-dialog id="build" confirm-label$="[[Start]]" confirm-on-enter on-cancel="_handleCancelTap"
      on-confirm="_handleConfirmTap" disabled$="{{disabled}}">
      <div class="header" slot="header">[[header]]</div>
      <div class="main" slot="main">
        <img src="wait.gif" width="70" height="70" hidden$="[[hiddenImage]]">
        <div class="com-google-gerrit-client-change-Resources-Style-popupContent">
          <br>
          <div class="tooltip">
            <span class="tooltiptext">No need to change the default value.
              <br>If this is a customer debug version then insert customer name.
              <br>You can set the FIX number by adding ":"<br> For example: nuera:701
            </span>
            <label>Build Name&nbsp;&nbsp;</label>
            <input type="text" value="{{buildName::change}}" />
          </div>
          <br>
          <div style="border-style: groove;">
            <br>
            <h5>Boards</h5>
            <template is="dom-repeat" items="[[boards]]" as="board">
              <label><input type="checkbox" name="board" checked$="{{board.value}}" board-index$="[[index]]"
                on-click="_handleBoardClick" />[[board.label]]</label>
            </template>
          </div>
          <br />
          <template is="dom-if" if="{{is_tp}}"><label><input type="checkbox" checked="{{SIP::change}}" />SIP</label>&emsp;</template>
          <label><input type="checkbox" checked="{{LAB::change}}" />LAB </label>&emsp;
          <template is="dom-if" if="{{show_li}}"><label><input type="checkbox" checked="{{LI::change}}" />LI </label>&emsp;</template>
          <br />

          <div class="com-google-gerrit-client-change-Resources-Style-popupContent" hidden$="[[hidenChange]]">
            <p style="padding-top: 10px; margin-bottom: -10px; font-weight: bold; color: green" >There are open changes in other repositories:</p>
            <div style="border-style: groove;">
              <template is="dom-repeat" items="{{SubModules}}" as="subModule">
                  <p class="main" style="color: rgb(153, 0, 255); padding-top: 15px; margin-bottom: 5px;">[[subModule.name]]:</p>
                  <select value="{{subModule.selectedBranch::change}}">
                    <template is="dom-repeat" items="{{subModule.branches}}" as="branch">
                      <option value="{{branch.value}}">{{branch.desc}}</option>
                    </template>
                  </select>
              </template>
              <div class="chapter-border"></div>
              <br>
            </div>
          </div>

          <div class="tooltip">
            <span class="tooltiptext">Comma-separated list of additional recipients on build success/failure<br>
              (The requester always receives an email).
            </span>
            <label>Extra recipients&nbsp;&nbsp;</label>

          <input type="text" value="{{recipients::change}}">
          </div>
          <br>
          <div></div>
            <div class="tooltip">
              <span class="tooltiptext">Adds the build number to the version number.<br />
                For example: 7.20A-u7282.252.236.<br />
                This enables easy tracking of the version running on the board.
              </span>
              <label><input type="checkbox" checked="{{git_ver::change}}" />&nbsp;&nbsp;Use git version</label>
            </div>
            <br>
            <div class="tooltip">
              <span class="tooltiptext">If checked, it will use the 'Build Name' as a customer name<br />
                and also will increase the FIX number by 500,<br />
                unless the FIX number was already mentioned in the 'Build Name' field.<br />
                Customer versions are never deleted from artifactory (unlike other builds).
              </span>
              <label><input type="checkbox" checked="{{debug::change}}" />&nbsp;&nbsp;Customer debug version</label>
            </div>

          <br>
          <div></div>
        </div>
        <p class="main" style="color: red;font-weight: bold;">[[errorMessage]]</p>
        <p class="main" style="color: red;font-weight: bold;">[[rebase]]</p>
        <p class="main" style="margin-bottom: 2em;">[[buttonText]]</p>
        <a style="color: green;" target=-blank href=[[url]]>[[link]]</a>
      </div>
    </gr-dialog>
  </template>
  <script>
    'use strict';

    const superModule = "TP/GWApp IPP/SFB GWApp";

    const acbuilddialog = Polymer({
      is: 'ac-build-dialog',

      properties: {
        Start: { type: String, value: 'Start' },
        disabled: { type: Boolean, value: true },
        SIP: { type: Boolean, value: true },
        LAB: { type: Boolean, value: true },
        LI: { type: Boolean, value: false },
        git_ver: { type: Boolean, value: true },
        debug: { type: Boolean, value: false },
        header: { type: String, value: '...wait...' },
        buildName: { type: String, value: '' },
        recipients: { type: String, value: '', notify: true },
        errorMessage: { type: String, value: '' },
        boards: { type: Array, value: [] },
        rebase: { type: String, value: '' },
        hidenChange: { type: Boolean, value: true },
        hiddenImage: { type: Boolean, value: true },
        is_tp: { type: Boolean, value: false },
        show_li: { type: Boolean, value: false },
        buttonText: { type: String, value: '' },
        url: { type: String, value: '' },
        link: { type: String, value: '' }
      },

      attached: function () {
        this.plugin.custom_popup = this;
      },

      resetFocus(e) {
        this.$.dialog.resetFocus();
      },

      buildAll: function(params) {
        var boards = params.BOARDS;
        var jobParams = {};
        for(var p in params)
          jobParams[p] = params[p];
        // AWS servers are named gerrit and test-gerrit. Other servers (rebaser, sip-linux) should use rebaser.
        if (window.document.URL.includes("rebaser")) {
          delete jobParams.BOARDS;
          var psos_boards = ['TP260_UN', 'TP1610', 'TP8410', 'TP6310', 'M1000', 'MP118', 'MP124', 'MP124E'];

          for (var b = 0; b < boards.length; ++b) {
            var board = boards[b];
            jobParams.BOARD = board;
            if (board == 'HostedTP') {
              jobParams.TYPE = 'ssbc';
            } else if (psos_boards.indexOf(board) == -1) {
              jobParams.TYPE = 'octeon';
            } else {
              jobParams.TYPE = 'psos';
            }
            jenkinsBuild(this, 'https://rebaser/build', 'GWApp', jobParams, board, board + ' [queued]');
          }
        } else {
          jobParams.BOARDS = boards.join(' ');
          jobParams.GERRIT_REFSPEC = jobParams.REFSPEC;
          jenkinsBuild(this, 'https://test-jenkins', 'sbc-user-build', jobParams, 'link', 'queued');
        }
      },

      allChecked: function() {
        return [].filter.call(this.boards, c => {
          return c.value;
        });
      },

      updateStartButton: function() {
        this.disabled = (this.allChecked().length === 0);
      },

      init(change, revision) {

        var linux_boards = ['MP500_MSBG', 'M4000', 'HostedTP'];
        var psos_boards = ['TP260_UN', 'TP1610', 'TP8410', 'TP6310', 'M1000'];

        if (change.branch >= '6.8')
           linux_boards.unshift('MP500_ESBC');
        if (change.branch < '7.0')
           psos_boards = ['MP118', 'MP124', 'MP124E'].concat(psos_boards);
        else
           linux_boards.push('MP1288');

        if (change.branch.startsWith('NextGenCPE') || change.branch.includes('NGC')) {
           linux_boards.push('MP5XXNG');
           linux_boards.push('M1000_MSBG');
        } else {
          if (change.branch >= '7.4') linux_boards.push('HostedTP8');
          if (change.branch >= '7.2.258') linux_boards.push('M1000_ESBC');
          else linux_boards.push('M1000_MSBG');
        }

        if (change.branch == 'feature/M3100')
          linux_boards.push('M3100');

        var Allboards = [];
        this.is_tp = change.project === 'TrunkPackRam' || change.branch === '7.0';
        this.show_li = change.project === 'TP/GWApp';

        linux_boards.forEach(b => Allboards.push({ "key": b, "label": b.replace('_', ' '), "type": Boolean, "value": false }))
        if (change.branch <= '7.0')
          psos_boards.forEach(b => Allboards.push({ "key": b, "label": b.replace('_', ' '), "type": Boolean, "value": false }))
        this.boards = Allboards;

        this.header = 'Build';
        this.buildName = change._number + '/' + revision._number;
        var CurrentUser = change.owner;
        labelName = "Build";
        findGerritChanges(this, change, labelName, revision.commit.commit, CurrentUser.email);
      },

      _handleBoardClick(e) {
        console.log(e.target.getAttribute('board-index'));
        this.boards[e.target.getAttribute('board-index')].value ^= true;
        this.updateStartButton();
      },

      _enableStart(value) {
        this.Start = value ? "Start" : "Close";
        this.$.build.$.cancel.disabled = !value
      },

      _handleConfirmTap(e) {
        //e.preventDefault();

        if (this.Start == "Close") {
           this.plugin.custom_popup_promise.close();
           this.plugin.custom_popup_promise = null;
        } else {

        var i;
        for (i = 0; i < this.boards.length; i++) {
          if (this.boards[i].value)
            console.log(this.boards[i].key);
        }

        this._enableStart(false);

        var c = this.gerritChange;

        for (let element of this.SubModules) {
           let changeNumber = element.selectedBranch;
           if (changeNumber != 0) {
             let project = c.AllCommits[changeNumber].project;
             let SHA1 = "";
             if (superModule.includes(project)) {
               c.refspec = c.AllCommits[changeNumber].sha;
             } else {
               SHA1 = c.AllCommits[changeNumber].sha;
               SMchanges = SMchanges.concat(project + ':' + SHA1 + ' ');
             }
           }
        }

        console.log(SMchanges);

        const params = {
          BUILD_NAME: this.buildName,
          BOARDS: this.allChecked().map(function(c) { return c.key; }),
          SIP: this.SIP,
          LAB: this.LAB,
          LI: this.LI,
          REMOTE: 'origin',
          EXTRA_RECIPIENTS: this.recipients,
          USE_GIT_VER: this.git_ver,
          DEBUG_VERSION: this.debug,
          CHANGES: SMchanges,
        };
        params.REFSPEC = c.refspec;

        this.buildAll(params);
        }
      },

      _handleCancelTap(e) {
        e.preventDefault();
        this.plugin.custom_popup_promise.close();
        this.plugin.custom_popup_promise = null;
      },
    });
  </script>
</dom-module>
