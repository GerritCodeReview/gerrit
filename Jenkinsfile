#!/usr/bin/env groovy

// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

class Globals {
    static final String gerritUrl = "https://gerrit-review.googlesource.com/"
    static final String gerritCredentialsId = "gerrit-review.googlesource.com"
    static final long curlTimeout = 10000
    static final int waitForResultTimeout = 10000
    static final String gerritRepositoryNameSha1Suffix = "-a6a0e4682515f3521897c5f950d1394f4619d928"
    static final resTicks = [ 'ABORTED':'\u26aa', 'SUCCESS':'\u2705', 'FAILURE':'\u274c' ]
}

class Change {
    static String sha1 = ""
    static String number = ""
    static String branch = ""
    static String ref = ""
    static String patchNum = ""
    static String url = ""
}

class Build {
    String url
    String result

    Build(url, result) {
        this.url = url
        this.result = result
    }
}

class Builds {
    static Set<String> modes = []
    static Build codeStyle = null
    static Map verification = [:]
}

class GerritCheck {
    String uuid
    String changeNum
    String sha1
    Build build

    GerritCheck(name, changeNum, sha1, build) {
        this.uuid = "gerritforge:" + name.replaceAll("(bazel/)", "") +
            Globals.gerritRepositoryNameSha1Suffix
        this.changeNum = changeNum
        this.sha1 = sha1
        this.build = build
    }

    def getCheckResultFromBuild() {
        switch(build.result) {
            case 'SUCCESS':
                return "SUCCESSFUL"
            case 'NOT_BUILT':
            case 'ABORTED':
                return "NOT_STARTED"
            case 'FAILURE':
            case 'UNSTABLE':
            default:
                return "FAILED"
        }
    }

    def createCheckPayload() {
        return JsonOutput.toJson([
            checker_uuid: uuid,
            state: getCheckResultFromBuild(),
            url: "${build.url}consoleText"
        ])
    }
}

def hasChangeNumber() {
    env.GERRIT_CHANGE_NUMBER?.trim()
}

def postCheck(check) {
    def gerritPostUrl = Globals.gerritUrl +
        "a/changes/${check.changeNum}/revisions/${check.sha1}/checks"

    try {
        def json = check.createCheckPayload()
        httpRequest(httpMode: 'POST', authentication: Globals.gerritCredentialsId,
            contentType: 'APPLICATION_JSON', requestBody: json,
            validResponseCodes: '200', url: gerritPostUrl)
        echo "----------------------------------------------------------------------------"
        echo "Gerrit Check: ${check.uuid}=" + check.build.result + " to change " +
            check.changeNum + "/" + check.sha1
        echo "----------------------------------------------------------------------------"
    } catch(Exception e) {
        echo "ERROR> Failed to post check results to Gerrit: ${e}"
    }
}

def queryChangedFiles(url, changeNum, sha1) {
    def queryUrl = "${url}changes/${Change.number}/revisions/${Change.sha1}/files/"
    def response = httpRequest queryUrl
    def files = response.getContent().substring(5)
    def filesJson = new JsonSlurper().parseText(files)
    return filesJson.keySet().findAll { it != "/COMMIT_MSG" }
}

def queryChange(){
    def requestedChangeId = env.BRANCH_NAME.split('/')[1]
    def queryUrl = "${Globals.gerritUrl}changes/${requestedChangeId}/?pp=0&O=3"
    def response = httpRequest queryUrl
    def jsonSlurper = new JsonSlurper()
    return jsonSlurper.parseText(response.getContent().substring(5))
}

def getChangeMetaData(){
    def changeJson = queryChange()
    Change.sha1 = changeJson.current_revision
    Change.number = changeJson._number
    Change.branch = changeJson.branch
    def revision = changeJson.revisions.get(Change.sha1)
    Change.ref = revision.ref
    Change.patchNum = revision._number
    Change.url = Globals.gerritUrl + "#/c/" + Change.number + "/" + Change.patchNum
}

def collectBuildModes() {
    Builds.modes = ["notedb"]
    def changedFiles = queryChangedFiles(Globals.gerritUrl, Change.number, Change.sha1)
    def polygerritFiles = changedFiles.findAll { it.startsWith("polygerrit-ui") ||
        it.startsWith("lib/js") }

    if(polygerritFiles.size() > 0) {
        if(changedFiles.size() == polygerritFiles.size()) {
            println "Only PolyGerrit UI changes detected, skipping other test modes..."
            Builds.modes = ["polygerrit"]
        } else {
            println "PolyGerrit UI changes detected, adding 'polygerrit' validation..."
            Builds.modes += "polygerrit"
        }
    } else if(changedFiles.contains("WORKSPACE")) {
        println "WORKSPACE file changes detected, adding 'polygerrit' validation..."
        Builds.modes += "polygerrit"
    }
}

def prepareBuildsForMode(buildName, mode="notedb", retryTimes = 1) {
    return {
        stage("${buildName}/${mode}") {
            def slaveBuild = null
            for (int i = 1; i <= retryTimes; i++) {
                try {
                    slaveBuild = build job: "${buildName}", parameters: [
                        string(name: 'REFSPEC', value: Change.ref),
                        string(name: 'BRANCH', value: Change.sha1),
                        string(name: 'CHANGE_URL', value: Change.url),
                        string(name: 'MODE', value: mode),
                        string(name: 'TARGET_BRANCH', value: Change.branch)
                    ], propagate: false
                } finally {
                    if (buildName == "Gerrit-codestyle"){
                        Builds.codeStyle = new Build(
                            slaveBuild.getAbsoluteUrl(), slaveBuild.getResult())
                    } else {
                        Builds.verification[mode] = new Build(
                            slaveBuild.getAbsoluteUrl(), slaveBuild.getResult())
                    }
                    if (slaveBuild.getResult() == "SUCCESS") {
                        break
                    }
                }
            }
        }
    }
}

def collectBuilds() {
    def builds = [:]
    if (hasChangeNumber()) {
       builds["Gerrit-codestyle"] = prepareBuildsForMode("Gerrit-codestyle")
       Builds.modes.each {
          builds["Gerrit-verification(${it})"] = prepareBuildsForMode("Gerrit-verifier-bazel", it)
       }
    } else {
       builds["java8"] = { -> build "Gerrit-bazel-${env.BRANCH_NAME}" }

       if (env.BRANCH_NAME == "master") {
          builds["java11"] = { -> build "Gerrit-bazel-java11-${env.BRANCH_NAME}" }
       }
    }
    return builds
}

def findFlakyBuilds() {
    def flaky = Builds.verification.findAll { it.value.result == null ||
        it.value.result != 'SUCCESS' }

    if(flaky.size() == Builds.verification.size()) {
        return []
    }

    def retryBuilds = []
    flaky.each {
        def mode = it.key
        Builds.verification = Builds.verification.findAll { it.key != mode }
        retryBuilds += mode
    }

    return retryBuilds
}

def getLabelValue(acc, res) {
    if(res == null || res == 'ABORTED') {
        return 0
    }
    switch(acc) {
        case 0: return 0
        case 1:
            if(res == null) {
                return 0;
            }
            switch(res) {
                case 'SUCCESS': return +1;
                case 'FAILURE': return -1;
                default: return 0;
            }
        case -1: return -1
    }
}

def setResult(resultVerify, resultCodeStyle) {
    if (resultVerify == 0 || resultCodeStyle == 0) {
        currentBuild.result = 'ABORTED'
    } else if (resultVerify == -1 || resultCodeStyle == -1) {
        currentBuild.result = 'FAILURE'
    } else {
        currentBuild.result = 'SUCCESS'
    }
}

def findCodestyleFilesInLog(build) {
    def codeStyleFiles = []
    def needsFormatting = false
    def response = httpRequest "${build.url}consoleText"
    response.content.eachLine {
        needsFormatting = needsFormatting || (it ==~ /.*Need Formatting.*/)
        if(needsFormatting && it ==~ /\[.*\]/) {
            codeStyleFiles += it.substring(1,it.length()-1)
        }
    }

    return codeStyleFiles
}

def createCodeStyleMsgBody(build, label) {
    def codeStyleFiles = findCodestyleFilesInLog(build)
    def formattingMsg = label < 0 ? ('The following files need formatting:\n    ' +
        codeStyleFiles.join('\n    ')) : 'All files are correctly formatted'
    def url = build.url + "consoleText"

    return "${Globals.resTicks[build.result]} $formattingMsg\n    (${url})"
}

def createVerifyMsgBody(builds) {
    def msgList = builds.collect { type, build -> [
        'type': type, 'res': build.result, 'url': build.url + "consoleText" ]
    }

    return msgList.collect {
        "${Globals.resTicks[it.res]} ${it.type} : ${it.res}\n    (${it.url})"
    } .join('\n')
}

node ('master') {

    if (hasChangeNumber()) {
        stage('Preparing'){
            gerritReview labels: ['Verified': 0, 'Code-Style': 0]

            getChangeMetaData()
            collectBuildModes()
        }
    }

    parallel(collectBuilds())

    if (hasChangeNumber()) {
        stage('Retry Flaky Builds'){
            def flakyBuildsModes = findFlakyBuilds()
            if (flakyBuildsModes.size() > 0){
                parallel flakyBuildsModes.collectEntries {
                    ["Gerrit-verification(${it})" :
                        prepareBuildsForMode("Gerrit-verifier-bazel", it, 3)]
                }
            }
        }

        stage('Report to Gerrit'){
            resCodeStyle = getLabelValue(1, Builds.codeStyle.result)
            gerritReview(
                labels: ['Code-Style': resCodeStyle],
                message: createCodeStyleMsgBody(Builds.codeStyle, resCodeStyle))
            postCheck(new GerritCheck("codestyle", Change.number, Change.sha1, Builds.codeStyle))

            def verificationResults = Builds.verification.collect { k, v -> v }
            def resVerify = verificationResults.inject(1) {
                acc, build -> getLabelValue(acc, build.result)
            }
            gerritReview(
                labels: ['Verified': resVerify],
                message: createVerifyMsgBody(Builds.verification))

            Builds.verification.each { type, build -> postCheck(
                new GerritCheck(type, Change.number, Change.sha1, build)
            )}

            setResult(resVerify, resCodeStyle)
        }
    }
}
