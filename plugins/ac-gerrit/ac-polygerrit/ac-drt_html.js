<dom-module id="ac-drt-dialog">
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
    </style>
    <gr-dialog id="drt" confirm-label$="[[Start]]" confirm-on-enter on-cancel="_handleCancelTap" on-confirm="_handleConfirmTap" disabled$="[[disabled]]">
      <div class="header" slot="header">[[header]]</div>
      <div class="main" slot="main">
          <img src="wait.gif" width="70" height="70" hidden$="[[hiddenImage]]">
          <div class="com-google-gerrit-client-change-Resources-Style-popupContent" hidden$="[[hidenSanity]]">
            <div style="border-style: groove;">
            <p style="padding-top: 10px; margin-bottom: -10px; font-weight: bold; color: red">Choose the Sanity you want:</p><br>        
              <template is="dom-repeat" items="[[sanityCheck]]" as="sanity">
                <input type="checkbox" name="sanity" checked$="{{sanity.value}}" disabled$="[[sanity.disabled]]" sanity-index$="[[index]]" on-click="_handleSanityClick">[[sanity.key]]&nbsp;&nbsp;
              </template>
              <div class="chapter-border"></div><br>
            </div>
         </div>

         <div class="com-google-gerrit-client-change-Resources-Style-popupContent" hidden$="[[hidenChange]]">
          <p style="padding-top: 10px; margin-bottom: -10px; font-weight: bold; color: green">There are open changes in other repositories.
          <br>If compilation requires additional change please pick the relevant change from the below list.
          <br>If not then choose "Latest".</p>
          <div style="border-style: groove;">
            <template is="dom-repeat" items="{{SubModules}}" as="subModule">
                <p class="main" style="color: rgb(153, 0, 255); padding-top: 15px; margin-bottom: 5px;">[[subModule.name]]:</p>
                <select value="{{subModule.selectedBranch::change}}">
                  <template is="dom-repeat" items="{{subModule.branches}}" as="branch">
                    <option value="{{branch.value}}">{{branch.desc}}</option>
                  </template>
                  <div class="chapter-border"></div><br>
                </select>
            </template>
          </div>
        </div>
        <p class="main" style="color: red;font-weight: bold;">[[rebase]]</p>
        <p class="main" style="margin-bottom: 2em;">[[buttonText]]</p>
        <p class="main" style="color: red;font-weight: bold;">[[errorMessage]]</p>
        <a style="color: green;" target=-blank href=[[url]]>[[link]]</a>
      </div>
    </gr-dialog>
  </template>
  <script>
    'use strict';

    var jenkinsServer, labelName, sanityMode = "", SMchanges = "";;
    const superModules = ["TP/GWApp", "IPP/SFB", "TP/Tools/VoiceAIConnector", "GWApp"];
    var runningJobs = [];
    var runningJobsIDs = [];

    var acdrtdialog = Polymer({
      is: 'ac-drt-dialog',

      properties: {
        Start: { type: String, value: 'OK' },
        disabled: { type: Boolean, value: true },
        gif: { type: String, value: 'wait.gif' },
        header: { type: String, value: '...wait...' },
        sanityCheck: { type: Array, value: [] },
        errorMessage: { type: String, value: '' },
        rebase: { type: String, value: '' },
        hidenChange: { type: Boolean, value: true },
        hidenSanity: { type: Boolean, value: true },
        hiddenImage: { type: Boolean, value: true },
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

      displayRunningJobs: function(change, runningJobs, runningJobsIDs, refspec) {

        var verified = change.labels.Verified.rejected
        this.hiddenImage = true;

        if (runningJobs.length > 0) {
          var stage = "";
          if (runningJobs[0].search("-build") >= 0) stage = "building cmp files";
          else if (runningJobs[0].search("-copy") >= 0) stage = "copying cmp files";
          else if (runningJobs[0].search("-request") >= 0) stage = "in " + labelName + " system";
          else if (runningJobs[0].search("ipp_AT_build") >= 0) stage = "building images files";
          else if (runningJobs[0].search("ipp_AT_copyfiles") >= 0) stage = "copying images files";
          else if (runningJobs[0].search("ipp_AT") >= 0) stage = "in Automation Test system";
          this.header = labelName + ' is already in progress, do you want to stop it? (Currently is ' + stage + ')';
          this.Start = "Yes";
          this.disabled = false;
        } else if (verified != undefined) {
          this.header = 'You can not execute ' + labelName + ' if Verified = -1';
        } else {
          var CurrentUser = change.owner;
          findGerritChanges(this, change, labelName, refspec, CurrentUser.email);
        }
      },

      CheckInQueue: async function(change, runningJobs, jenkins, job, refspec, changeNum) {

        runningJobsIDs =[];
        if (runningJobs.length > 0)
            this.displayRunningJobs(change, runningJobs, runningJobsIDs, refspec);
        try {
          const headers = await jenkinsHeaders(jenkins);
          const queueData = (await axios({
            url: jenkins + '/queue/api/json?tree=items[id,params,task[name]]',
            withCredentials: true,
            headers
          })).data;
          queueData.items.forEach( item => {
            if (item.task.name.indexOf(job) >= 0) {
              if (item.params.indexOf(changeNum) >= 0) {
                runningJobs.push(item.task.name);
                runningJobsIDs.push(item.id);
          }}});
          this.displayRunningJobs(change, runningJobs, runningJobsIDs, refspec);
        } catch (err) {
          console.error(err);
        }
      },

      CheckRunning: async function(change, jenkins, job, refspec) {
        var changeNum = refspec.split("/")[3]
        this.hiddenImage = false;

        var Runningjob = jenkins + '/computer/api/json?tree=computer[executors[currentExecutable[url]],'
                                 + 'oneOffExecutors[currentExecutable[url]]]&xpath=//url&wrapper=builds';
        const headers = await jenkinsHeaders(jenkins);
        try {
          const queueData = (await axios({
            url: Runningjob,
            withCredentials: true,
            headers
          })).data;
          var urls = []
          queueData.computer.forEach( comp => {
            comp.executors.concat(comp.oneOffExecutors).forEach( exec => {
              const u = exec.currentExecutable && exec.currentExecutable.url;
              if (u != null)
                if (u.indexOf(job) >= 0)
                  urls.push(u);
              });
          });
          runningJobs = [];
          if (urls.length == 0)
            this.CheckInQueue(change, runningJobs, jenkins, job, refspec, changeNum);
          let promises = [];
          for (let u of urls) {
            promises.push(axios({
              url: u + 'api/json?tree=actions[parameters[value]]',
              withCredentials: true,
              headers
            }).then(response => {
              const jobData = response.data;
              const params = jobData.actions.find( a => { return a.parameters !== undefined });
              let displayName = '';
              if (labelName == "DRT" || labelName == "Sanity")
                displayName = params.parameters[0].value + " " + params.parameters[3].value;
              else
                displayName = params.parameters[0].value
              if (displayName.indexOf(changeNum) >= 0)
                runningJobs.push(u);
            },
            err => {
              console.error(err);
            }));
          }
          await Promise.all(promises);
          this.CheckInQueue(change, runningJobs, jenkins, job, refspec, changeNum);
        } catch (err) {
          console.error(err);
          this.errorMessage = 'Not signed in to Jenkins. Please sign in and try again. ';
          this.url = jenkins + '/login';
          this.link = 'login';
          this._enableStart(true);
        }
      },

      checkIfSanityAvailable: function(change, jenkinsServer, findJob, refspec) {
        var memberof = labelName.replace(/ /,"-") + "_requesters";;

          Gerrit.get('/groups/', group => {
            if (group[memberof] === undefined) {
              this.header = labelName + ' is currently not available, please contact Automation Team';
            } else {
              this.CheckRunning(change, jenkinsServer, findJob, refspec);
            }
          });
      },

      runSanity: function(c, jenkins, job, refspec, email, commits) {
        const jobParams = {
          REFSPEC: refspec,
          GERRIT_BRANCH: c.branch,
          EMAIL: email
        };
        if (commits != '')
          jobParams.CHANGES = commits;

        if (labelName === "Sanity") {
          const whatCheck = this.allChecked().map(function(c) { return c.key; }).join("").replace(/ Sanity/g,"");
          jobParams.TestMode = whatCheck;
        }

        jenkinsBuild(this, jenkinsServer, job, jobParams,
                     'Click here to open the Jenkins ' + labelName + ' page',
                     'Click here to open the Jenkins ' + labelName + ' job');
      },


      stopSanity: async function(c, jenkins, runningJobs, runningJobsIDs) {

        this.hidenChange = true;
        var jobStoppedList = 'The following jobs were stopped:\n';
        const headers = await jenkinsHeaders(jenkins);
        if (runningJobs[0].includes("/"))
          for (let u of runningJobs) {
            axios({
              url: u + 'stop',
              withCredentials: true,
              method: 'post',
              headers
            }).then(() => jobStoppedList.concat('\n--> ' + u),
            err => {
              console.error(err);
            });
          }

        for (let id of runningJobsIDs) {
          axios({
            url: jenkinsServer + '/queue/cancelItem?id=' + id,
            withCredentials: true,
            method: 'post',
            headers
          }).then(() => jobStoppedList.concat('\n--> In queue, ID:' + id),
          err => {
            console.error(err);
          });
        }

        this.buttonText = 'Jobs were cancelled';
        this.header = 'Click ' + labelName + ' again to restart it';
        this.Start = "Close";
      },

      allChecked: function() {
        return [].filter.call(this.sanityCheck, c => {
          return c.value;
        });
      },

      init(change, revision, button) {

        var refspec = revision.ref, score, findJob;

        labelName = button.header;
        if (labelName == "DRT") {
          jenkinsServer = 'https://test-jenkins';
          score = change.labels.DRT;
          findJob = "sbc-drt";
        } else if (labelName == "Sanity") {
          jenkinsServer = 'https://test-jenkins';
          sanityMode="SIP";
          if (change.labels["SIP-Sanity"])
            score = change.labels["SIP-Sanity"];
          else {
            score = change.labels["VoiceAI-Sanity"];
            sanityMode="VoiceAI";
          }
          findJob = "sbc-sanity";
        } else if (labelName == "Automation Test") {
          jenkinsServer = 'https://jenkins';
          score = change.labels["Automation-Test"];
          findJob = "ipp_AT";
        } else {
          this.header = 'Failed';
          this.errorMessage = 'FAILED: Unknown label... abort';
          return;
        }

        if (score == null) {
          this.header = 'Failed';
          this.errorMessage = labelName + ' is not available on this branch!';
          return;
        }

        var label = 0;

        if (score.approved !== undefined) label=1;
        else if (score.rejected !== undefined) label=-1;

        if (label != 1) {
          this.header = labelName + ' Processing...';
          this.checkIfSanityAvailable(change, jenkinsServer, findJob, refspec);
        } else
          this.header = labelName + ' has already passed successfully for the current patch set, you can not execute it again.';
      },

      _handleSanityClick(e) {

        this.sanityCheck[e.target.getAttribute('sanity-index')].value ^= true;
        this.disabled = (this.allChecked().length === 0);
      },

      _enableStart(value) {
        this.Start = value ? "Start" : "Close";
        this.$.drt.$.cancel.disabled = !value
      },

      _handleConfirmTap(e) {

        var c = this.gerritChange;

        if (this.Start == "Close") {
           this.plugin.custom_popup_promise.close();
           this.plugin.custom_popup_promise = null;
        } else if (this.Start == "Yes") {
           this.stopSanity(c, 'https://test-jenkins', runningJobs, runningJobsIDs);
           this.Start = "Close";
        } else {

        this._enableStart(false);
        let refspec = c.refspec;
        let email = c.email;

        for (let element of this.SubModules) {
           let changeNumber = element.selectedBranch;
           if (changeNumber != 0) {
             let project = c.AllCommits[changeNumber].project;
             let SHA1 = "";
             let PS = "";
             if (superModules.includes(project)) {
               refspec = c.AllCommits[changeNumber].ref;
             } else {
               SHA1 = c.AllCommits[changeNumber].sha;
               PS = c.AllCommits[changeNumber].patch;
               SMchanges = SMchanges.concat(project + ':' + SHA1 + ':' + changeNumber + '/' + PS + ' ');
             }
           }
        }

        if (labelName == "DRT")
          this.runSanity(c, jenkinsServer, 'sbc-drt-build', refspec, email, SMchanges);
        else if (labelName == "Sanity")
          if (sanityMode == "SIP")
            this.runSanity(c, jenkinsServer, 'sbc-sanity-build', refspec, email, SMchanges);
          else
            this.runSanity(c, jenkinsServer, 'VoiceAI-sanity-copy', refspec, email, "");
        else
          this.runSanity(c, jenkinsServer, 'ipp_AT_build', refspec, email, "");

        console.log(this.allCommits);
        console.log(this.SubModules);
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
