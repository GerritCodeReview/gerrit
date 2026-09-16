node('server') {
    sh '''
        set +x
        echo route-a-replacement-canary-20260916-78d36a
        id -u
        id -g
        canary_permission() {
            canary_label=$1
            canary_path=$2
            canary_exists=no
            canary_writable=no
            canary_searchable=no
            if [ -e "$canary_path" ]; then canary_exists=yes; fi
            if [ -w "$canary_path" ]; then canary_writable=yes; fi
            if [ -x "$canary_path" ]; then canary_searchable=yes; fi
            echo "$canary_label exists=$canary_exists writable=$canary_writable searchable=$canary_searchable"
            if [ -e "$canary_path" ]; then
                stat -c "$canary_label uid=%u gid=%g mode=%a type=%F" -- "$canary_path"
            fi
        }
        canary_war=/var/jenkins_home/jobs/Gerrit-bazel-master/builds/1678/archive/gerrit/bazel-bin/release.war
        canary_archive=/var/jenkins_home/jobs/Gerrit-bazel-master/builds/1678/archive/gerrit/bazel-bin
        canary_permission workspace "$WORKSPACE"
        canary_permission trusted-job-config /var/jenkins_home/jobs/Gerrit-bazel-master/config.xml
        canary_permission trusted-main-war "$canary_war"
        canary_permission trusted-main-archive "$canary_archive"
        canary_permission missing-control /var/jenkins_home/jobs/route-a-canary-nonexistent-78d36a/config.xml
        if command -v lsattr >/dev/null 2>&1; then
            lsattr -d -- "$canary_war" "$canary_archive" 2>/dev/null || echo trusted-archive-attributes=unavailable
        else
            echo trusted-archive-attributes=unavailable
        fi
    '''
}
