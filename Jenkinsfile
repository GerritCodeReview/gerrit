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

//TODO: Implement job in Jenkins
//TODO: buck builds
//TODO: Codestyle checks

node('bazel-debian'){
  try {
    gerritReview labels: [Verified: 0]

    stage('Fetching Submodules'){
      //TODO: Do we still need the fallback behaviour?
      sh(script: "git submodule update")
    }
    stage('Build'){
      sh(script: ". set-java.sh 8");

      sh(script: "bazel build ${Config.bazel_opts} //...");
      sh(script: "tools/eclipse/project.py");
    }
    stage('Test ReviewDB'){
      sh(script: "bazel test --test_env=GERRIT_NOTEDB=OFF ${bazel_opts} ${bazel_test_opts} //..."
    }
    stage('Test NoteDB'){
      //If tetsing both DBs, do we need to clean up inbetween?
      sh(script: "bazel test --test_env=GERRIT_NOTEDB=ON ${bazel_opts} ${bazel_test_opts} //..."
    }
    stage('Test PolyGerrit UI'){
      when {
        anyOf {
          branch = "master"
          branch = "stable-2.16"
          branch = "stable-2.15"
          branch = "stable-2.14"
        }
      }
      sh(script: 'test -z "$DISPLAY" ||' +
        '(bash ./polygerrit-ui/app/run_test.sh || touch ~/polygerrit-failed)')
      sh(script: '(test -z "$SAUCE_USERNAME" || test -z "$SAUCE_ACCESS_KEY")' +
        '(WCT_ARGS="--plugin sauce" bash ./polygerrit-ui/app/run_test.sh || touch ~/polygerrit-failed)')
    }
    //TODO: publish artifacts and test results
    gerritReview labels: [Verified: 1]
  } except e {
    gerritReview labels: [Verified: -1]
  }
}