// TODO: license

package webtest

import (
	"log"
	"net/http"
	"testing"
	"time"

	"github.com/bazelbuild/rules_webtesting/go/webtest"
	"github.com/tebeka/selenium"
)

type FakeGerrit struct {
}

func (g *FakeGerrit) ServeHTTP(rw http.ResponseWriter, req *http.Request) {
	rw.Write([]byte("<html><title>Tietel!</title><body>I love commit based reviews</body></html>"))
}

func StartServer() {
	// TODO: find the JVM, write a debug configuration, start gerrit.war, populate data?
	fg := FakeGerrit{}

	go http.ListenAndServe(":8080", &fg)
}

func TestWebApp(t *testing.T) {
	StartServer()

	for {
		_, err := http.Get("http://localhost:8080/")
		if err == nil {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}
	log.Println("fake gerrit live!")

	wd, err := webtest.NewWebDriverSession(selenium.Capabilities{})
	if err != nil {
		t.Fatalf("NewWebDriverSession %v", err)
	}

	if err := wd.Get("http://localhost:8080/"); err != nil {
		t.Fatalf("Get: %v", err)
	}

	if title, err := wd.Title(); err != nil {
		t.Fatalf("Title: %v", err)
	} else if want := "Tietel!"; title != want {
		t.Fatalf("got %q want %q", title, want)
	}

	if src, err := wd.PageSource(); err != nil {
		t.Fatalf("PageSource", err)
	} else {
		log.Printf("Page source is %q", src)
	}

	if err := wd.Quit(); err != nil {
		t.Logf("Error quitting webdriver: %v", err)
	}
}
