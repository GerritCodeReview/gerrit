/**
 * @license
 * Copyright (C) 2021 AudioCodes Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { htmlTemplate } from './ac-build_html.js';
import { Jenkins } from './jenkins.js';
import { RelatedChanges } from './findGerritChanges.js';

class AcBuild extends Polymer.Element {
  static get is() { return 'ac-build-dialog'; }

  static get template() { return htmlTemplate; }

  static get properties() {
    return {
      Start: { type: String, value: 'Start' },
      disabled: { type: Boolean, value: true },
      SIP: { type: Boolean, value: true },
      LAB: { type: Boolean, value: true },
      LI: { type: Boolean, value: false },
      git_ver: { type: Boolean, value: true },
      debug: { type: Boolean, value: false },
      waitImage: { type: String, value: 'wait.gif' },
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
      link: { type: String, value: '' },
    };
  }

  constructor() {
    super();
    this.jenkins = new Jenkins;
    this.related = new RelatedChanges;
  }

  connectedCallback() {
    super.connectedCallback();
    this.waitImage = this.plugin.url('/static/wait.gif');
    this.plugin.custom_popup = this;
  }

  resetFocus(e) {
    this.$.dialog.resetFocus();
  }

  buildAll(params) {
    const boards = params.BOARDS;
    const jobParams = {};
    for (const p in params) {
      if (params.hasOwnProperty(p))
        jobParams[p] = params[p];
    }
    // AWS servers are named gerrit and test-gerrit. Other servers (rebaser, sip-linux) should use rebaser.
    if (window.document.URL.includes('rebaser')) {
      delete jobParams.BOARDS;
      const psos_boards = ['TP260_UN', 'TP1610', 'TP8410', 'TP6310', 'M1000', 'MP118', 'MP124', 'MP124E'];

      for (const board of boards) {
        jobParams.BOARD = board;
        if (board == 'HostedTP') {
          jobParams.TYPE = 'ssbc';
        } else if (psos_boards.indexOf(board) == -1) {
          jobParams.TYPE = 'octeon';
        } else {
          jobParams.TYPE = 'psos';
        }
        this.jenkins.build(this, 'https://rebaser/build', 'GWApp', jobParams, board, board + ' [queued]');
      }
    } else {
      jobParams.BOARDS = boards.join(' ');
      jobParams.GERRIT_REFSPEC = jobParams.REFSPEC;
      this.jenkins.build(this, 'https://test-jenkins', 'sbc-user-build', jobParams, 'link', 'queued');
    }
  }

  allChecked() {
    return [].filter.call(this.boards, c => {
      return c.value;
    });
  }

  updateStartButton() {
    this.disabled = (this.allChecked().length === 0);
  }

  init(change, revision) {
    const linux_boards = ['MP500_MSBG', 'M4000', 'HostedTP'];
    let psos_boards = ['TP260_UN', 'TP1610', 'TP8410', 'TP6310', 'M1000'];

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

    const Allboards = [];
    this.is_tp = change.project === 'TrunkPackRam' || change.branch === '7.0';
    this.show_li = change.project === 'TP/GWApp';

    for (const b of linux_boards)
      Allboards.push({ key: b, label: b.replace('_', ' '), type: Boolean, value: false });
    if (change.branch <= '7.0') {
      for (const b of psos_boards)
        Allboards.push({ key: b, label: b.replace('_', ' '), type: Boolean, value: false });
    }
    this.boards = Allboards;

    this.header = 'Build';
    this.buildName = change._number + '/' + revision._number;
    const CurrentUser = change.owner;
    const labelName = 'Build';
    this.related.find(this, change, labelName, revision.commit.commit, CurrentUser.email);
  }

  _handleBoardClick(e) {
    console.log(e.target.getAttribute('board-index'));
    this.boards[e.target.getAttribute('board-index')].value ^= true;
    this.updateStartButton();
  }

  _enableStart(value) {
    this.Start = value ? 'Start' : 'Close';
    this.$.build.$.cancel.disabled = !value;
  }

  _handleConfirmTap(e) {
    // e.preventDefault();

    if (this.Start == 'Close') {
      this.plugin.custom_popup_promise.close();
      this.plugin.custom_popup_promise = null;
    } else {
      for (let i = 0; i < this.boards.length; i++) {
        if (this.boards[i].value)
          console.log(this.boards[i].key);
      }

      this._enableStart(false);

      const c = this.gerritChange;

      for (const element of this.SubModules) {
        const changeNumber = element.selectedBranch;
        if (changeNumber != 0) {
          const project = c.AllCommits[changeNumber].project;
          let SHA1 = '';
          if (this.related.superModules.includes(project)) {
            c.refspec = c.AllCommits[changeNumber].sha;
          } else {
            SHA1 = c.AllCommits[changeNumber].sha;
            this.SMchanges = this.SMchanges.concat(project + ':' + SHA1 + ' ');
          }
        }
      }

      console.log(this.SMchanges);

      const params = {
        BUILD_NAME: this.buildName,
        BOARDS: this.allChecked().map(c => c.key),
        SIP: this.SIP,
        LAB: this.LAB,
        LI: this.LI,
        REMOTE: 'origin',
        EXTRA_RECIPIENTS: this.recipients,
        USE_GIT_VER: this.git_ver,
        DEBUG_VERSION: this.debug,
        CHANGES: this.SMchanges,
      };
      params.REFSPEC = c.refspec;

      this.buildAll(params);
    }
  }

  _handleCancelTap(e) {
    e.preventDefault();
    this.plugin.custom_popup_promise.close();
    this.plugin.custom_popup_promise = null;
  }
}

customElements.define(AcBuild.is, AcBuild);