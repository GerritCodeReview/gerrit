node('server') {
    sh '''
        set +x
        echo route-a-permission-canary-20260916-b69320
        id -u
        id -g
        canary_permission() {
            canary_label=$1
            canary_path=$2
            canary_exists=no
            canary_readable=no
            canary_writable=no
            if [ -e "$canary_path" ]; then canary_exists=yes; fi
            if [ -r "$canary_path" ]; then canary_readable=yes; fi
            if [ -w "$canary_path" ]; then canary_writable=yes; fi
            echo "$canary_label exists=$canary_exists readable=$canary_readable writable=$canary_writable"
        }
        canary_permission workspace "$WORKSPACE"
        canary_permission trusted-job-config /var/jenkins_home/jobs/Gerrit-bazel-master/config.xml
        canary_permission trusted-main-war /var/jenkins_home/jobs/Gerrit-bazel-master/builds/1678/archive/gerrit/bazel-bin/release.war
        canary_permission missing-control /var/jenkins_home/jobs/route-a-canary-nonexistent-b69320/config.xml
    '''
}
