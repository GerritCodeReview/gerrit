class Config {
  static String bazel_opts =
    "--spawn_strategy=standalone " +
    "--genrule_strategy=standalone  " +
    "--java_toolchain //tools:error_prone_warnings_toolchain";
  static String bazel_test_opts =
    "--test_output errors " +
    "--test_summary detailed " +
    "--flaky_test_attempts 3 " +
    "--test_verbose_timeout_warnings " +
    "--build_tests_only " +
    "--test_timeout 3600 " +
    "--test_tag_filters=-flaky,-docker"
}

//TODO: buck builds

node('bazel-debian'){
  gerritReview labels: [Verified: 0]
  gerritReview labels: [Code-Style: 0]
  try {
    stage('Fetching Submodules'){
      sh(script: "git submodule update")
    }
    stage('Setting Java Version'){
      sh(script: ". set-java.sh 8");
    }
  } catch (e) {
    gerritReview labels: [Verified: -1]
    throw e
  }

  try {
    stage('Google-Java-Format check'){
      sh(script: '''
        git show --diff-filter=AM --name-only --pretty="" HEAD | \
          grep java$ | \
          xargs -r ~/format/google-java-format-1.6 -n --set-exit-if-changed
        ''')
    }
    stage('PolyGerrit Lint Check'){
      when {
        anyof {
          branch = "master"
          branch = "stable-2.16"
          branch = "stable-2.15"
          branch = "stable-2.14"
        }
        changeset "polygerrit-ui"
      }
      sh(script: "bazel test //polygerrit-ui/app:lint_test --test_output errors")
      sh(script: "bazel test //polygerrit-ui/app:polylint_test --test_output errors")
    }
    stage('PolyGerrit Template Test'){
      when {
        anyof {
          branch = "master"
          branch = "stable-2.16"
          branch = "stable-2.15"
          branch = "stable-2.14"
        }
        changeset "polygerrit-ui"
      }
      sh(script: "bazel test //polygerrit-ui/app:all --test_tag_filters=template --test_output errors")
    }
    stage('Buildifier Check'){
      sh(script: '''
        EXITCODE=0
        for buildfile in $((git show --diff-filter=AM --name-only --pretty="" HEAD | grep --regex "WORKSPACE\|BUILD\|\.bzl$") || true)
        do
          BUILDIFIER_OUTPUT_FILE="$(mktemp)_buildifier_output.log"
          buildifier -showlog -v -mode=check $buildfile 2>&1 | tee ${BUILDIFIER_OUTPUT_FILE}
          if [[ -s ${BUILDIFIER_OUTPUT_FILE} ]]; then
            echo "Need Formatting:"
            echo "[$buildfile]"
            echo "Please fix manually or run buildifier $buildfile to auto-fix."
            buildifier -showlog -v -mode=diff $buildfile
            rm -rf ${BUILDIFIER_OUTPUT_FILE}
            EXITCODE=1
          fi
        done
        exit $EXITCODE
        '''
      )
    }
    gerritReview labels: [Code-Style: 1]
  } catch (e) {
    gerritReview labels: [Code-Style: -1]
  }

  try {
    stage('Build (Buck'){
      when {
        anyOf {
          branch = "stable-2.13"
          branch = "stable-2.12"
        }
      }
      sh(script: 'buck build -v 3 api plugins:core release')
      sh(script: '''
        if [ -f tools/maven/api.sh ]
        then
          tools/maven/api.sh install buck
        else
          buck build -v 3 api_install
        fi
      ''')
    }
    stage('Build (Bazel)'){
      when {
        anyOf {
          branch = "master"
          branch = "stable-2.16"
          branch = "stable-2.15"
          branch = "stable-2.14"
        }
      }
      sh(script: "bazel build ${Config.bazel_opts} //...");
      sh(script: "tools/eclipse/project.py");
    }
    stage('Test (Buck)'){
      when {
        anyOf {
          branch = "stable-2.13"
          branch = "stable-2.12"
        }
      }
      sh(script: 'buck test --no-results-cache --exclude flaky')
    }
    stage('Test ReviewDB (Bazel)'){
      when {
        anyOf {
          branch = "master"
          branch = "stable-2.16"
          branch = "stable-2.15"
          branch = "stable-2.14"
        }
      }
      sh(script: "bazel test --test_env=GERRIT_NOTEDB=OFF ${Config.bazel_opts} ${Config.bazel_test_opts} //..."
    }
    stage('Test NoteDB (Bazel)'){
      when {
        anyOf {
          branch = "master"
          branch = "stable-2.16"
          branch = "stable-2.15"
          branch = "stable-2.14"
        }
      }
      //If tetsing both DBs, do we need to clean up inbetween?
      sh(script: "bazel test --test_env=GERRIT_NOTEDB=ON ${Config.bazel_opts} ${Config.bazel_test_opts} //..."
    }
    stage('Test PolyGerrit UI (Bazel)'){
      when {
        anyOf {
          branch = "master"
          branch = "stable-2.16"
          branch = "stable-2.15"
          branch = "stable-2.14"
        }
      }
      sh(script: '''
        test -z "$DISPLAY" || \
        (bash ./polygerrit-ui/app/run_test.sh || touch ~/polygerrit-failed)
        ''')
      sh(script: '''
        (test -z "$SAUCE_USERNAME" || test -z "$SAUCE_ACCESS_KEY") \
        (WCT_ARGS="--plugin sauce" bash ./polygerrit-ui/app/run_test.sh || touch ~/polygerrit-failed)
        ''')
    }
    //TODO: publish artifacts and test results
    gerritReview labels: [Verified: 1]
  } catch (e) {
    gerritReview labels: [Verified: -1]
    throw e
  }
}