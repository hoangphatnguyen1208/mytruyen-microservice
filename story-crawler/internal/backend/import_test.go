package backend

import (
	"fmt"
	"github.com/go-resty/resty/v2"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestLookupRejectsInvalidResponseEnvelopes(t *testing.T) {
	for _, body := range []string{`{`, `{}`, `{"success":false,"data":{}}`,
		`{"success":true,"data":{"source":"metruyencv","external_id":"1","book_id":2,"outcome":"mapped"}}`,
		`{"success":true,"data":{"source":"metruyencv","external_id":"1","book_id":2,"version":0,"outcome":"manual_review"}}`} {
		s := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { fmt.Fprint(w, body) }))
		c := Client{HTTP: resty.New().SetBaseURL(s.URL)}
		if _, err := c.LookupBook("1"); err == nil {
			t.Errorf("accepted %s", body)
		}
		s.Close()
	}
}
func TestUnsafeExternalIDNeverMakesRequest(t *testing.T) {
	c := Client{HTTP: resty.New().SetBaseURL("http://127.0.0.1:1")}
	if _, err := c.LookupBook("../secret"); err == nil {
		t.Fatal("accepted unsafe ID")
	}
}
