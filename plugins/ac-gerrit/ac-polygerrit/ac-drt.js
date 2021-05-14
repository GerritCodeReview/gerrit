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

import { htmlTemplate } from './ac-drt_html.js';
import { RelatedChanges } from './findGerritChanges.js';
import { Jenkins } from './jenkins.js';

class AcDrtDialog extends Polymer.Element {
  constructor() {
    super();
    this.runningJobs = [];
    this.runningJobsIDs = [];
    this.jenkins = new Jenkins;
    this.related = new RelatedChanges;
  }

  static get is() { return 'ac-drt-dialog'; }

  static get template() { return htmlTemplate; }

  static get properties() {
    return {
      Start: { type: String, value: 'OK' },
      disabled: { type: Boolean, value: true },
      waitImage: { type: String, value: 'wait.gif' },
      header: { type: String, value: '...wait...' },
      sanityCheck: { type: Array, value: [] },
      errorMessage: { type: String, value: '' },
      rebase: { type: String, value: '' },
      hidenChange: { type: Boolean, value: true },
      hidenSanity: { type: Boolean, value: true },
      hiddenImage: { type: Boolean, value: true },
      buttonText: { type: String, value: '' },
      url: { type: String, value: '' },
      link: { type: String, value: '' },
    };
  }

  connectedCallback() {
    super.connectedCallback();
    this.waitImage = this.plugin.url('/static/wait.gif');
    this.plugin.custom_popup = this;
  }

  resetFocus(e) {
    this.$.dialog.resetFocus();
  }

  displayRunningJobs(change, refspec) {
    const verified = change.labels.Verified.rejected;
    this.hiddenImage = true;

    if (this.runningJobs.length > 0) {
      let stage = '';
      if (this.runningJobs[0].search('-build') >= 0) stage = 'building cmp files';
      else if (this.runningJobs[0].search('-copy') >= 0) stage = 'copying cmp files';
      else if (this.runningJobs[0].search('-request') >= 0) stage = 'in ' + this.plugin.labelName + ' system';
      else if (this.runningJobs[0].search('ipp_AT_build') >= 0) stage = 'building images files';
      else if (this.runningJobs[0].search('ipp_AT_copyfiles') >= 0) stage = 'copying images files';
      else if (this.runningJobs[0].search('ipp_AT') >= 0) stage = 'in Automation Test system';
      this.header = this.plugin.labelName + ' is already in progress, do you want to stop it? (Currently is ' + stage + ')';
      this.Start = 'Yes';
      this.disabled = false;
    } else if (verified != undefined) {
      this.header = 'You can not execute ' + this.plugin.labelName + ' if Verified = -1';
    } else {
      const CurrentUser = change.owner;
      this.related.find(this, change, this.plugin.labelName, refspec, CurrentUser.email);
    }
  }

  async checkInQueue(change, jenkins, job, refspec, changeNum) {
    this.runningJobsIDs = [];
    if (this.runningJobs.length > 0)
      this.displayRunningJobs(change, refspec);
    try {
      const headers = await this.jenkins.headers(jenkins);
      const queueData = await ((await fetch(jenkins + '/queue/api/json?tree=items[id,params,task[name]]', {
        credentials: 'include',
        headers,
      })).json());
      queueData.items.forEach(item => {
        if (item.task.name.indexOf(job) >= 0) {
          if (item.params.indexOf(changeNum) >= 0) {
            this.runningJobs.push(item.task.name);
            this.runningJobsIDs.push(item.id);
          }
        }
      });
      this.displayRunningJobs(change, refspec);
    } catch (err) {
      console.error(err);
    }
  }

  async checkRunning(change, jenkins, job, refspec) {
    const changeNum = refspec.split('/')[3];
    this.hiddenImage = false;

    const Runningjob = jenkins + '/computer/api/json?tree=computer[executors[currentExecutable[url]],' +
      'oneOffExecutors[currentExecutable[url]]]&xpath=//url&wrapper=builds';
    const headers = await this.jenkins.headers(jenkins);
    try {
      const queueData = await ((await fetch(Runningjob, {
        credentials: 'include',
        headers,
      })).json());
      const urls = [];
      queueData.computer.forEach(comp => {
        comp.executors.concat(comp.oneOffExecutors).forEach(exec => {
          const u = exec.currentExecutable && exec.currentExecutable.url;
          if (u != null)
            if (u.indexOf(job) >= 0)
              urls.push(u);
        });
      });
      this.runningJobs = [];
      if (urls.length == 0)
        this.checkInQueue(change, this.runningJobs, jenkins, job, refspec, changeNum);
      const promises = [];
      for (const u of urls) {
        promises.push(fetch(u + 'api/json?tree=actions[parameters[value]]', {
          credentials: 'include',
          headers,
        }).then(async response => {
          const jobData = await response.json();
          const params = jobData.actions.find(a => { return a.parameters !== undefined; });
          let displayName = '';
          if (this.plugin.labelName == 'DRT' || this.plugin.labelName == 'Sanity')
            displayName = params.parameters[0].value + ' ' + params.parameters[3].value;
          else
            displayName = params.parameters[0].value;
          if (displayName.indexOf(changeNum) >= 0)
            this.runningJobs.push(u);
        }, console.error));
      }
      await Promise.all(promises);
      this.checkInQueue(change, this.runningJobs, jenkins, job, refspec, changeNum);
    } catch (err) {
      console.error(err);
      this.errorMessage = 'Not signed in to Jenkins. Please sign in and try again. ';
      this.url = jenkins + '/login';
      this.link = 'login';
      this._enableStart(true);
    }
  }

  checkIfSanityAvailable(change, findJob, refspec) {
    const memberof = this.plugin.labelName.replace(/ /, '-') + '_requesters';

    Gerrit.get('/groups/', group => {
      if (group[memberof] === undefined) {
        this.header = this.plugin.labelName + ' is currently not available, please contact Automation Team';
      } else {
        this.checkRunning(change, this.jenkinsServer, findJob, refspec);
      }
    });
  }

  runSanity(c, jenkins, job, refspec, email, commits) {
    const jobParams = {
      REFSPEC: refspec,
      GERRIT_BRANCH: c.branch,
      EMAIL: email,
    };
    if (commits != '')
      jobParams.CHANGES = commits;

    if (this.plugin.labelName === 'Sanity') {
      const whatCheck = this.allChecked().map(c => c.key).join('').replace(/ Sanity/g, '');
      jobParams.TestMode = whatCheck;
    }

    const title = type => `Click here to open the Jenkins ${this.plugin.labelName} ${type}`;
    this.jenkins.build(this, this.jenkinsServer, job, jobParams, title('page'), title('job'));
  }

  async stopSanity(c, jenkins) {
    this.hidenChange = true;
    const jobStoppedList = 'The following jobs were stopped:\n';
    const headers = await this.jenkins.headers(jenkins);
    if (this.runningJobs[0].includes('/'))
      for (const u of this.runningJobs) {
        fetch(u + 'stop', {
          credentials: 'include',
          method: 'post',
          headers,
        }).then(() => jobStoppedList.concat('\n--> ' + u), console.error);
      }

    for (const id of this.runningJobsIDs) {
      fetch(this.jenkinsServer + '/queue/cancelItem?id=' + id, {
        credentials: 'include',
        method: 'post',
        headers,
      }).then(() => jobStoppedList.concat('\n--> In queue, ID:' + id), console.error);
    }

    this.buttonText = 'Jobs were cancelled';
    this.header = 'Click ' + this.plugin.labelName + ' again to restart it';
    this.Start = 'Close';
  }

  allChecked() {
    return [].filter.call(this.sanityCheck, c => c.value);
  }

  init(change, revision, button) {
    const refspec = revision.ref;
    let score;
    let findJob;

    this.plugin.labelName = button.header;
    if (this.plugin.labelName == 'DRT') {
      this.jenkinsServer = 'https://test-jenkins';
      score = change.labels.DRT;
      findJob = 'sbc-drt';
    } else if (this.plugin.labelName == 'Sanity') {
      this.jenkinsServer = 'https://test-jenkins';
      this.related.sanityMode = 'SIP';
      if (change.labels['SIP-Sanity'])
        score = change.labels['SIP-Sanity'];
      else {
        score = change.labels['VoiceAI-Sanity'];
        this.related.sanityMode = 'VoiceAI';
      }
      findJob = 'sbc-sanity';
    } else if (this.plugin.labelName == 'Automation Test') {
      this.jenkinsServer = 'https://jenkins';
      score = change.labels['Automation-Test'];
      findJob = 'ipp_AT';
    } else {
      this.header = 'Failed';
      this.errorMessage = 'FAILED: Unknown label... abort';
      return;
    }

    if (score == null) {
      this.header = 'Failed';
      this.errorMessage = this.plugin.labelName + ' is not available on this branch!';
      return;
    }

    let label = 0;

    if (score.approved !== undefined) label = 1;
    else if (score.rejected !== undefined) label = -1;

    if (label != 1) {
      this.header = this.plugin.labelName + ' Processing...';
      this.checkIfSanityAvailable(change, findJob, refspec);
    } else
      this.header = this.plugin.labelName + ' has already passed successfully for the current patch set, you can not execute it again.';
  }

  _handleSanityClick(e) {
    this.sanityCheck[e.target.getAttribute('sanity-index')].value ^= true;
    this.disabled = (this.allChecked().length === 0);
  }

  _enableStart(value) {
    this.Start = value ? 'Start' : 'Close';
    this.$.drt.$.cancel.disabled = !value;
  }

  _handleConfirmTap(e) {
    const c = this.gerritChange;

    if (this.Start == 'Close') {
      this.plugin.custom_popup_promise.close();
      this.plugin.custom_popup_promise = null;
    } else if (this.Start == 'Yes') {
      this.stopSanity(c, 'https://test-jenkins');
      this.Start = 'Close';
    } else {
      this._enableStart(false);
      let refspec = c.refspec;
      const email = c.email;

      for (const element of this.SubModules) {
        const changeNumber = element.selectedBranch;
        if (changeNumber != 0) {
          const project = c.AllCommits[changeNumber].project;
          let SHA1 = '';
          let PS = '';
          if (this.related.superModules.includes(project)) {
            refspec = c.AllCommits[changeNumber].ref;
          } else {
            SHA1 = c.AllCommits[changeNumber].sha;
            PS = c.AllCommits[changeNumber].patch;
            this.SMchanges = this.SMchanges.concat(project + ':' + SHA1 + ':' + changeNumber + '/' + PS + ' ');
          }
        }
      }

      if (this.plugin.labelName == 'DRT')
        this.runSanity(c, this.jenkinsServer, 'sbc-drt-build', refspec, email, this.SMchanges);
      else if (this.plugin.labelName == 'Sanity')
        if (this.related.sanityMode == 'SIP')
          this.runSanity(c, this.jenkinsServer, 'sbc-sanity-build', refspec, email, this.SMchanges);
        else
          this.runSanity(c, this.jenkinsServer, 'VoiceAI-sanity-copy', refspec, email, '');
      else
        this.runSanity(c, this.jenkinsServer, 'ipp_AT_build', refspec, email, '');

      console.log(this.allCommits);
      console.log(this.SubModules);
    }
  }

  _handleCancelTap(e) {
    e.preventDefault();
    this.plugin.custom_popup_promise.close();
    this.plugin.custom_popup_promise = null;
  }
}

customElements.define(AcDrtDialog.is, AcDrtDialog);