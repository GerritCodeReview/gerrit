// Copyright (C) 2021 The Android Open Source Project
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

// package webtest shows how to write a browser-based end-to-end test for gerrit.
// Use as follows:
//
// * To run against an existing Gerrit server.
//
//     bazelisk test --test_arg=--gerrit_port=8080 --test_output=streamed :webtest_test
//
// The server should be configured as DEVELOPMENT_BECOME_ANY_ACCOUNT,
// with 'admin' as administrative user.
//
// * To run "hermetically", ie. with a fresh Gerrit server,
//
//     bazelisk test --test_output=streamed :webtest_test
//
// This will configure a Gerrit server from scratch. Initializing the server takes about 20 seconds,
// so this is not recommended for interactive development.
package webtest

import (
	"flag"
	"fmt"
	"io/ioutil"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"testing"
	"time"

	// TODO: use bazel.io import path?
	"github.com/bazelbuild/rules_webtesting/go/webtest"
	"github.com/tebeka/selenium"
)

var (
	// if set, selenium dumps RPC traffic. As this is mostly opaque UUIDs, not terribly useful.
	seleniumDebug = false
)

var (
	gerritPort = flag.Int("gerrit_port", 0, "use running Gerrit instance. If 0, spawn a Gerrit server.")
	gerritDir  = flag.String("gerrit_dir", "", "use existing Gerrit instance. If dir does not exist, init gerrit there")
)

func gerritWAR() string {
	return filepath.Join(os.Getenv("RUNFILES_DIR"), "gerrit", "gerrit.war")
}

// InitGerrit sets up a development Gerrit site
func InitGerrit(dir string) error {
	if err := os.MkdirAll(dir+"/etc", 0755); err != nil {
		return err
	}

	// Do this first to trigger admin account creation.
	if err := ioutil.WriteFile(dir+"/etc/gerrit.config", []byte(`
[auth]
        type = DEVELOPMENT_BECOME_ANY_ACCOUNT
`), 0644); err != nil {
		return err
	}

	cmd := exec.Command("java", "-jar", gerritWAR(), "init", "-d", dir,
		"--no-auto-start", "--batch")
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr

	if err := cmd.Run(); err != nil {
		return err
	}

	return nil
}

// gerritServer represents a running Gerrit server.
type gerritServer struct {
	url   string
	Close func()
}

// StartGerrit starts up a gerrit server with the config from the given dir.
func StartGerrit(dir string, port int) (*gerritServer, error) {
	s := gerritServer{
		url: fmt.Sprintf("http://localhost:%d/", port),
	}
	cmd := exec.Command("git", "config", "-f", dir+"/etc/gerrit.config",
		"httpd.listenUrl", s.url)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	log.Printf("Running %#v", cmd)
	if err := cmd.Run(); err != nil {
		return nil, err
	}

	cmd = exec.Command("java", "-jar", gerritWAR(),
		"daemon", "--disable-sshd", "--console-log", "-d", dir)

	cmd.Stderr = os.Stderr
	cmd.Stdout = os.Stdout
	log.Printf("Running %v in %q", cmd.Args, cmd.Dir)
	if err := cmd.Start(); err != nil {
		return nil, err
	}

	s.Close = func() {
		cmd.Process.Kill()
	}

	delay := 10 * time.Millisecond
	start := time.Now()
	var err error
	for i := 0; i < 1000; i++ {
		_, err = http.Get(s.url)
		if err == nil {
			break
		}

		time.Sleep(delay)
	}

	if err != nil {
		return nil, fmt.Errorf("Gerrit not up after %v", time.Now().Sub(start))
	}
	return &s, nil
}

func pickUnusedPort() (int, error) {
	l, err := net.Listen("tcp", "localhost:0")
	if err != nil {
		return 0, err
	}

	p := l.Addr().(*net.TCPAddr).Port
	l.Close()
	return p, nil
}

// StartServer starts a gerrit server, using the --gerrit_dir option.
// It will pick a fresh port and overwrite the port setting in the
// configuration.
func StartServer() (*gerritServer, error) {
	log.Printf(`
***
Starting fresh Gerrit. This takes a while;
Use --test_arg=-gerrit_port=8080 for debugging
***

`)

	var err error
	if *gerritDir == "" {
		*gerritDir, err = ioutil.TempDir("", "gerrit-webtest-*")
		if err != nil {
			return nil, err
		}

		log.Printf("gerrit config in %s", *gerritDir)
	}

	if _, err := os.Stat(*gerritDir + "/etc/gerrit.config"); os.IsNotExist(err) {
		if err := InitGerrit(*gerritDir); err != nil {
			return nil, err
		}
	}

	*gerritPort, err = pickUnusedPort()
	if err != nil {
		return nil, err
	}

	s, err := StartGerrit(*gerritDir, *gerritPort)
	log.Printf(`
***
Gerrit started!
***
`)

	return s, err
}

// TestServer returns the gerritServer to use for the test.
func TestServer() (*gerritServer, error) {
	if *gerritPort != 0 {
		return &gerritServer{
			Close: func() {},
			url:   fmt.Sprintf("http://localhost:%d/", *gerritPort),
		}, nil
	}
	return StartServer()
}

// dump puts a screenshot in /tmp. Unfortunately, wd.PageSource() just
// returns the initial source code, which doesn't reflect later DOM
// modifications, so it's pretty useless.
func dump(wd selenium.WebDriver) {
	selenium.SetDebug(false)
	defer selenium.SetDebug(seleniumDebug)
	ss := "/tmp/screenshot.png"
	image, err := wd.Screenshot()
	if err != nil {
		log.Fatalf("Screenshot: %v", err)
	}
	if err := ioutil.WriteFile(ss, image, 0644); err != nil {
		log.Fatalf("WriteFile: %v")
	}

	log.Printf("wrote screenshot %s", ss)
}

// dumpElement puts a screenshot of the element into /tmp/elt.png for
// debugging.
func dumpElement(we selenium.WebElement) {
	selenium.SetDebug(false)
	defer selenium.SetDebug(seleniumDebug)
	png, err := we.Screenshot(false)
	if err != nil {
		log.Printf("Screenshot error: %v", err)
	} else {
		fn := "/tmp/elt.png"
		ioutil.WriteFile(fn, png, 0644)
		log.Printf("wrote %s", fn)
	}
}

// Selenium doesn't support shadow root very well. We get around this
// by using JS to obtain the shadow root. The result only supports FindElement() ByCSSSelector.
func shadowRootOf(wd selenium.WebDriver, host selenium.WebElement) (selenium.WebElement, error) {
	root, err := wd.ExecuteScript("return arguments[0].shadowRoot", []interface{}{host})
	if err != nil {
		return nil, err
	}

	// The following is ugly, but works.

	asMap := root.(map[string]interface{})

	// this key is defined in W3C spec, so should be OK to hardcode.
	key := "element-6066-11e4-a52e-4f735466cecf"
	idStr := asMap[key].(string)

	elt, err := wd.DecodeElement([]byte(fmt.Sprintf(`{"Value": {"%s": "%s"}}`, key, idStr)))
	if err != nil {
		return nil, err
	}

	return elt, nil
}

// findInShadowRootByID finds elements in nested shadow DOMs. It
// returns an element for the shadow-root
func findInShadowRoot(wd selenium.WebDriver, we selenium.WebElement, by string, ids []string) (selenium.WebElement, error) {
	var err error
	for len(ids) > 0 {
		id := ids[0]
		ids = ids[1:]
		if we == nil {
			we, err = wd.FindElement(by, id)
		} else {
			we, err = we.FindElement(by, id)
		}
		if err != nil {
			return nil, fmt.Errorf("FindElement %s: %v", id, err)
		}

		we, err = shadowRootOf(wd, we)
		if err != nil {
			return nil, fmt.Errorf("shadowRootOf %s: %v", id, err)
		}
	}

	return we, err
}

// gerritLoginAs clicks the sign-in button on the become-any-account page
func gerritLoginAs(wd selenium.WebDriver, userName string) error {
	header, err := findInShadowRoot(wd, nil, selenium.ByID, []string{"app", "app-element", "mainHeader"})
	if err != nil {
		return fmt.Errorf("findInShadowRoot(mainHeader): %v", err)
	}

	button, err := header.FindElement(selenium.ByCSSSelector, "a.loginButton")
	if err != nil {
		return fmt.Errorf("FindElement(loginButton): %v", err)
	}

	if err := button.Click(); err != nil {
		return fmt.Errorf("button.Click: %v", err)
	}

	user, err := wd.FindElement(selenium.ByLinkText, userName)
	if err != nil {
		return fmt.Errorf("FindElement(admin)", err)
	}
	if err := user.Click(); err != nil {
		return fmt.Errorf("admin.Click: %v", err)
	}

	return nil
}

// gerritLogout clicks the signout button. We must be logged in (or we
// won't find the "Sign out" button).
func gerritLogout(wd selenium.WebDriver) error {
	header, err := findInShadowRoot(wd, nil, selenium.ByID, []string{"app", "app-element", "mainHeader"})
	if err != nil {
		return fmt.Errorf("findInShadowRoot(mainHeader): %v", err)
	}

	dropDown, err := header.FindElement(selenium.ByCSSSelector, "gr-account-dropdown")
	if err != nil {
		return fmt.Errorf("findElt (gr-account-dropdown): %v", err)
	}

	// Perhaps not necessary, as the dropdown is only hidden?
	if err := dropDown.Click(); err != nil {
		return fmt.Errorf("dropDown.Click: %v", err)
	}

	grDropDown, err := findInShadowRoot(wd, header, selenium.ByCSSSelector,
		[]string{"gr-account-dropdown",
			"gr-dropdown",
		})
	if err != nil {
		return fmt.Errorf("findInShadowRoot(gr-dropdown): %v", err)
	}

	elts, err := grDropDown.FindElements(selenium.ByCSSSelector, "gr-tooltip-content")
	if err != nil {
		return fmt.Errorf("FindElements(gr-tooltip-content): %v", err)
	}

	if len(elts) != 3 {
		return fmt.Errorf("want 3 entries")
	}

	if got, err := elts[2].Text(); err != nil || got != "Sign out" {
		return fmt.Errorf("got %q want 'Sign out'", got)
	}

	if err := elts[2].Click(); err != nil {
		return fmt.Errorf("click: %v", err)
	}

	return nil
}

func TestLoginLogout(t *testing.T) {
	// TODO: could start in parallel with selenium.
	server, err := TestServer()
	if err != nil {
		t.Fatalf("TestServer %v", err)
	}
	defer server.Close()

	selenium.SetDebug(seleniumDebug)
	wd, err := webtest.NewWebDriverSession(selenium.Capabilities{})
	if err != nil {
		t.Fatalf("NewWebDriverSession %v", err)
	}
	defer wd.Quit()

	if err := wd.SetImplicitWaitTimeout(time.Second); err != nil {
		t.Fatalf("SetImplicitWaitTimeout: %v", err)
	}
	url := fmt.Sprintf("http://localhost:%d/", *gerritPort)

	if wd.Get(url); err != nil {
		t.Fatalf("Get: %v", err)
	}

	if err := gerritLoginAs(wd, "admin"); err != nil {
		t.Fatalf("gerritLoginAs: %v", err)
	}

	if err := gerritLogout(wd); err != nil {
		t.Fatalf("gerritLogout: %v", err)
	}
}
