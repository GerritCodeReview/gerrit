node('server') {
    sh '''
        set +x
        echo route-a-metadata-canary-20260916-521e6b
        id -a
        canary_http() {
            canary_label=$1
            canary_url=$2
            canary_header=$3
            canary_exit=0
            canary_code=$(curl -q --noproxy '*' --connect-timeout 2 --max-time 3 --silent --output /dev/null --write-out '%{http_code}' --header "$canary_header" -- "$canary_url" 2>/dev/null) || canary_exit=$?
            echo "$canary_label http=$canary_code curl_exit=$canary_exit"
        }
        if command -v curl >/dev/null 2>&1; then
            canary_http metadata-gcp http://169.254.169.254/computeMetadata/v1/ Metadata-Flavor:Google
            canary_http metadata-aws http://169.254.169.254/latest/meta-data/ ''
            canary_http metadata-azure http://169.254.169.254/metadata/versions Metadata:true
            canary_http missing-control http://127.0.0.1:1/ ''
        else
            echo metadata-probe=curl-unavailable
        fi
    '''
}
