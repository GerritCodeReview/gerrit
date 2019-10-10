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

import hudson.model.*
import hudson.AbortException
import hudson.console.HyperlinkNote
import java.util.concurrent.CancellationException
import groovy.json.*
import java.text.*

class Globals {
    static long curlTimeout = 10000
    static int waitForResultTimeout = 10000
    static String gerritRepositoryNameSha1Suffix = "-a6a0e4682515f3521897c5f950d1394f4619d928"
}

class Change {
    static String sha1 = ""
    static String number = ""
    static String branch = ""
    static String ref = ""
    static String patchNum = ""
    static String url = ""
}

class Builds {
    static Set<String> modes = []
    static Result resultCodeStyle = null
    static Run buildCodeStyle = null
    static Map resultsVerification = [:]
    static Map buildsVerification = [:]
}

class GerritCheck {
    String uuid
    String changeNum
    String sha1
    Object build

    GerritCheck(name, changeNum, sha1, build) {
        this.uuid = "gerritforge:" + name.replaceAll("(bazel/)", "") +
            Globals.gerritRepositoryNameSha1Suffix
        this.changeNum = changeNum
        this.sha1 = sha1
        this.build = build
    }

    def printCheckSummary() {
        println "----------------------------------------------------------------------------"
        println "Gerrit Check: ${uuid}=" + build.result + " to change " + changeNum + "/" + sha1
        println "----------------------------------------------------------------------------"
    }

    def getCheckResultFromBuild() {
        switch(build.result) {
            case Result.SUCCESS:
                return "SUCCESSFUL"
            case Result.ABORTED:
                return "NOT_STARTED"
            case Result.FAILURE:
            case Result.NOT_BUILT:
            case Result.UNSTABLE:
            default:
                return "FAILED"
        }
    }

    def createCheckPayload() {
        return '{"checker_uuid":"' + uuid + '",' +
                '"state":"' + getCheckResultFromBuild() + '",'
                '"url":"' + build.getUrl() + 'consoleText"}'
    }
}

class Gerrit {
    String url
    Script script
    boolean verbose = true

    def postCheck(check) {
        def exitCode = httpPost("a/changes/${check.changeNum}/revisions/${check.sha1}/checks",
            check.createCheckPayload())
        if (exitCode == 0) {
            check.printCheckSummary()
        }
    }

    private def httpPost(path, jsonPayload) {
        def error = ""
        def gerritPostUrl = url + path
        def curl = ['curl',
            '-n', '-s', '-S',
            '-X', 'POST', '-H', 'Content-Type: application/json',
            '--data-binary', jsonPayload, gerritPostUrl ]
        if(verbose) { println "CURL/EXEC> $curl" }
        def proc = curl.execute()
        def sout = new StringBuffer(), serr = new StringBuffer()
        proc.consumeProcessOutput(sout, serr)
        proc.waitForOrKill(Globals.curlTimeout)
        def curlExit = proc.exitValue()
        if(curlExit != 0) {
            error = "$curl **FAILED** with exit code = $curlExit"
            println error
            throw new IOException(error)
        }

        if(!sout.toString().trim().isEmpty() && verbose) {
            println "CURL/OUTPUT> $sout"
        }
        if(!serr.toString().trim().isEmpty() && verbose) {
            println "CURL/ERROR> $serr"
        }

        return 0
    }

    def getChangedFiles(changeNum, sha1) {
        def response = httpRequest "${url}changes/${Change.number}/revisions/${Change.sha1}/files/"
        def files = response.getContent().substring(5)
        def filesJson = new JsonSlurper().parseText(files)
        filesJson.keySet().findAll { it != "/COMMIT_MSG" }
    }

    def getChangeUrl(changeNum, patchNum) {
        return url + "#/c/" + changeNum + "/" + patchNum
    }

    def getChangeQueryUrl(changeId) {
        return "${url}changes/$changeId/?pp=0&O=3"
    }

    private def println(message) {
        script.println(message)
    }
}

def queryChange(){
    def requestedChangeId = env.BRANCH_NAME.split('/')[1]
    def queryUrl = gerrit.getChangeQueryUrl(requestedChangeId)
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
    Change.url = gerrit.getChangeUrl(Change.number, Change.patchNum)
}

def collectBuildModes() {
    Builds.modes = ["reviewdb"]
    def changedFiles = gerrit.getChangedFiles(Change.number, Change.sha1)
    def polygerritFiles = changedFiles.findAll { it.startsWith("polygerrit-ui") ||
        it.startsWith("lib/js") }

    if(polygerritFiles.size() > 0 || changedFiles.contains("WORKSPACE")) {
        if(changedFiles.size() == polygerritFiles.size()) {
            println "Only PolyGerrit UI changes detected, skipping other test modes..."
            Builds.modes = ["polygerrit"]
        } else {
            println "PolyGerrit UI changes detected, adding 'polygerrit' validation..."
            Builds.modes += "polygerrit"
        }
    }
}

def prepareBuildsForMode(buildName, mode="reviewdb", retryTimes = 1) {
    def propagate = retryTimes == 1 ? false : true
    return {
        stage("${buildName}/${mode}") {
            catchError{
                retry(retryTimes){
                    def slaveBuild = build job: "${buildName}", parameters: [
                        string(name: 'REFSPEC', value: Change.ref),
                        string(name: 'BRANCH', value: Change.sha1),
                        string(name: 'CHANGE_URL', value: Change.url),
                        string(name: 'MODE', value: mode),
                        string(name: 'TARGET_BRANCH', value: Change.branch)
                    ], propagate: propagate
                    if (buildName == "Gerrit-codestyle"){
                        Builds.buildCodeStyle = slaveBuild.rawBuild
                        Builds.resultCodeStyle = slaveBuild.rawBuild.result
                    } else {
                        Builds.buildsVerification[mode] = slaveBuild.rawBuild
                        Builds.resultsVerification[mode] = slaveBuild.rawBuild.result
                    }
                }
            }
        }
    }
}

def collectBuilds() {
    def builds = [:]
    builds["Gerrit-codestyle"] = prepareBuildsForMode("Gerrit-codestyle")
    Builds.modes.each {
        builds["Gerrit-verification(${it})"] = prepareBuildsForMode("Gerrit-verifier-bazel", it)
    }
    return builds
}

def findFlakyBuilds() {
    def flaky = Builds.buildsVerification.findAll { it.value.result == null ||
        it.value.result != Result.SUCCESS }
    if(flaky.size() == Builds.resultsVerification.size()) {
        return []
    }
    def retryBuilds = []
    flaky.each {
        def mode = it.key
        Builds.resultsVerification.remove(mode)
        Builds.buildsVerification.remove(mode)
        retryBuilds += mode
    }
    return retryBuilds
}

def getLabelValue(acc, res) {
    if(res == null || res == Result.ABORTED) {
        return 0
    }
    switch(acc) {
        case 0: return 0
        case 1:
        if(res == null) {
            return 0;
        }
        switch(res) {
            case Result.SUCCESS: return +1;
            case Result.FAILURE: return -1;
            default: return 0;
        }
        case -1: return -1
    }
}

def setResult(resultVerify, resultCodeStyle) {
    def resAll = resultCodeStyle ?
        getLabelValue(resultVerify, resultCodeStyle) : resultVerify
    switch(resAll) {
        case 0: currentBuild.rawBuild.result = Result.ABORTED
                break
        case 1: currentBuild.rawBuild.result = Result.SUCCESS
                break
        case -1: currentBuild.rawBuild.result = Result.FAILURE
                break
    }
}

node ('master') {

    stage('Preparing'){
        gerritReview labels: ['Verified': 0, 'Code-Style': 0]
        gerrit = new Gerrit(script:this, url:"https://gerrit-review.googlesource.com/")

        checkout scm
        getChangeMetaData()
        collectBuildModes()
    }

    parallel(collectBuilds())

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
        if(Builds.resultCodeStyle) {
            gerritReview labels: ['Code-Style': getLabelValue(1, Builds.resultCodeStyle)]
            gerrit.postCheck(new GerritCheck(
                "codestyle", Change.number, Change.sha1, Builds.buildCodeStyle))
        }

        def verificationResults = Builds.resultsVerification.collect {
            k, v -> v
        }
        def resVerify = verificationResults.inject(1) {
            acc, buildResult -> getLabelValue(acc, buildResult)
        }
        gerritReview labels: ['Verified': resVerify]

        Builds.buildsVerification.each { type, build -> gerrit.postCheck(
            new GerritCheck(type, Change.number, Change.sha1, build)
        )}

        setResult(resVerify, Builds.resultCodeStyle)
    }
}
