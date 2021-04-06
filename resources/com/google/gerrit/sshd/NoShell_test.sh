#!/bin/bash
# Compares the number of file descriptors before and after running the NoShell command
# toward the specified Gerrit process, for the given number of times. A user can run
# this script to make sure that the NoShell command doesn't leave any open ressources
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

gerritWar="gerrit.war"

pidCommand=$(ps -p "${pid}" -o command)
GerritPidMismatchMessage="Make sure that Gerrit is up and running and that the PID corresponds to the one running gerrit."
if [[ $pidCommand != *"$gerritWar"* ]]; then
  echo "$GerritPidMismatchMessage"
  exit 1
fi

fdCountBeforeLoop=$(lsof -p "$pid" | wc -l)

ssh -p "$port" "$user"@"$host" &>/dev/null
commandExitCode="$?"
echo "Iteration: 1."
sshErrorMessage="There is an error in the ssh connection to your site.
Please make sure that the ssh key is properly registered in Gerrit, that you entered the right ssh port
and that the host is registered as a known host. For the latter case, you can use this command:
ssh-keyscan -t rsa -p 29418 localhost > ~/.ssh/known_hosts"
if [[ "$commandExitCode" != 127 ]]; then
  echo "$sshErrorMessage"
  exit 1
fi

for ((iterations = 1; iterations < "$times"; iterations++)); do
  ssh -p "$port" "$user"@"$host" &>/dev/null
  echo "Iteration: $((iterations + 1))."
done
fdCountAfterLoop=$(lsof -p "$pid" | wc -l)

beforeFdCountMessage="File descriptor count before the loop: ${fdCountBeforeLoop}."
afterFdCountMessage="File descriptor count after the loop: ${fdCountAfterLoop}."

if [ "$fdCountAfterLoop" -le "$fdCountBeforeLoop" ]; then
  echo "The test is successful. The count of file descriptors didn't increase."
else
  failureMessage="The test fails. The count of file descriptors increased by $((fdCountAfterLoop - fdCountBeforeLoop)).
  -Note that something else from the application might have caused this increase."
  printf "%s\n%s\n%s\n" "${failureMessage}" "${beforeFdCountMessage}" "${afterFdCountMessage}"
fi

terminationStatus="$?"
if [ "$terminationStatus" != 0 ]; then
  echo "The last executed ssh command exited with non-zero status ${terminationStatus}."
fi
