// Copyright (C) 2016 The Android Open Source Project
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

package main

import (
	"fmt"
	"io/ioutil"
	"log"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

var (
	// Define regex to find a comment in the build files
	commentRE = regexp.MustCompile("#.*")
	// Define regexes to extract the lib name and sha1
	mvnRE     = regexp.MustCompile("maven_jar([^)]*)")
	sha1RE    = regexp.MustCompile("sha1=[\"'](?P<SHA1>[^,]*)[\"']")
	bSha1RE   = regexp.MustCompile("bin_sha1=[\"'](?P<SHA1>[^,]*)[\"']")
	libNameRE = regexp.MustCompile("name=[\"'](?P<NAME>[^,]*)[\"']")
)

func sanitize(s string) string {
	// Strip out comments
	s = commentRE.ReplaceAllString(s, "")
	// Remove newlines and blanks
	s = strings.Replace(s, "\n", "", -1)
	s = strings.Replace(s, " ", "", -1)
	// WORKSPACE syntax disallows the dash char in artifact name and we use an underscore
	// So we make this a consistent underscore in all files
	s = strings.Replace(s, "-", "_", -1)
	return s
}

func main() {
	// Load bazel WORKSPACE file
	bzlDat, err := ioutil.ReadFile("WORKSPACE")
	if err != nil {
		log.Fatal(err)
	}
	bzlStr := sanitize(string(bzlDat))

	// Walk all files nested under lib. Find, load and sanitize BUCK files
	bckStrs := []string{}
	err = filepath.Walk("lib/", func(path string, f os.FileInfo, err error) error {
		bckFile := filepath.Join(path, "BUCK")
		if _, err := os.Stat(bckFile); err == nil {
			bckDat, err := ioutil.ReadFile(bckFile)
			if err != nil {
				return err
			}
			bckStrs = append(bckStrs, sanitize(string(bckDat)))
		}
		return nil
	})
	if err != nil {
		log.Fatal(err)
	}
	bckStr := strings.Join(bckStrs, "")

	// Find all bazel dependencies
	// bzlVersions maps from a lib name to the referenced sha1
	bzlVersions := make(map[string]string)
	for _, mvn := range mvnRE.FindAllString(bzlStr, -1) {
		sha1s := sha1RE.FindStringSubmatch(mvn)
		names := libNameRE.FindStringSubmatch(mvn)
		if len(sha1s) > 1 && len(names) > 1 {
			bzlVersions[names[1]] = sha1RE.FindStringSubmatch(mvn)[1]
		} else {
			fmt.Printf("Can't parse lib sha1/name of target %s\n", mvn)
		}
	}

	// Find all buck dependencies and check if we have the correct bazel dependency on file
	for _, mvn := range mvnRE.FindAllString(bckStr, -1) {
		sha1s := bSha1RE.FindStringSubmatch(mvn)
		if len(sha1s) < 2 {
			// Buck knows two dep version representations: just a SHA1 or a bin_sha1 and src_sha1
			// We try to extract the bin_sha1 first. If that fails, we use the sha1
			sha1s = sha1RE.FindStringSubmatch(mvn)
		}
		names := libNameRE.FindStringSubmatch(mvn)
		if len(sha1s) > 1 && len(names) > 1 {
			if _, ok := bzlVersions[names[1]]; !ok {
				// TODO(hiesel) This produces too many false positives.
				//fmt.Printf("Don't have lib %s in bazel\n", names[1])
			} else if bzlVersions[names[1]] != sha1s[1] {
				fmt.Printf("SHA1 of lib %s does not match: buck has %s while bazel has %s\n", names[1], sha1s[1], bzlVersions[names[1]])
			}
		} else {
			fmt.Printf("Can't parse lib sha1/name on target %s\n", mvn)
		}
	}
}
