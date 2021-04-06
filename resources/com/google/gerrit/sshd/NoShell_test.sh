#!/bin/bash
# Compares the number of file descriptors before and after running the NoShell command
# toward the specified Gerrit process, for the given number of times. A user can run
# this script to make sure that the NoShell command doesn't leave any open resources
# after it ends.

usage="
./NoShell_test.sh process_id ssh_port user_name host_name number_of_times
Example:
./NoShell_test.sh 12345 29418 admin localhost 100
"

pid=$1
port=$2
user=$3
host=$4
times=$5

if [ "$#" != 5 ]; then
  echo "$usage"
  exit 1
fi

pidCommand=$(ps -p "${pid}" -o command)
if [[ $pidCommand != *"gerrit.war"* ]]; then
  echo "process_id should be an up and running Gerrit process id (pid)."
  exit 1
fi

fdBefore=$(lsof -p "$pid" | wc -l)

messageReceived=$({ ssh -p "$port" "$user"@"$host"; } 2>&1)
sshErrorMessage="There was an error in the ssh connection to the site.
Make sure that the ssh key is properly registered in Gerrit, that ssh_port
is right and that the host is registered as a known host. For the latter,
this command can be used:
ssh-keyscan -t rsa -p 29418 localhost >> ~/.ssh/known_hosts
-More:
https://gerrit-review.googlesource.com/Documentation/user-upload.html#ssh"

if [[ "$messageReceived" != *"successfully connected"* ]]; then
  echo "$sshErrorMessage"
  exit 1
fi

for ((iterations = 1; iterations < "$times"; iterations++)); do
  ssh -p "$port" "$user"@"$host" &>/dev/null
done
terminationStatus="$?"

fdAfter=$(lsof -p "$pid" | wc -l)

beforeFdCountMessage="File descriptor count before the loop: ${fdBefore}."
afterFdCountMessage="File descriptor count after the loop: ${fdAfter}."

if [ "$fdAfter" -le "$fdBefore" ]; then
  echo "The test is successful. The count of file descriptors didn't increase."
else
  failureMessage="The test fails. The count of file descriptors increased by $((fdAfter - fdBefore))."
  failureReason="-Note that something else from the application might have caused this increase."
  printf "%s\n" "${failureMessage}" "${failureReason}" \
    "${beforeFdCountMessage}" "${afterFdCountMessage}"
fi

if [ "$terminationStatus" != 0 ]; then
  echo "The last executed ssh command exited with non-zero status ${terminationStatus}."
fi
