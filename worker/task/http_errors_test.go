package task

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/go-resty/resty/v2"
)

func TestTaxonomyLookupFailureDoesNotCreate(t *testing.T) {
	for _, status := range []int{401, 403, 429, 500} {
		t.Run(fmt.Sprint(status), func(t *testing.T) {
			creates := 0
			s := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.Method == http.MethodPost {
					creates++
				}
				w.WriteHeader(status)
			}))
			defer s.Close()
			client := resty.New().SetBaseURL(s.URL)
			input := map[string]any{"name": "test"}
			if _, err := GetOrCreateAuthor(client, input, 1); err == nil {
				t.Error("author accepted error")
			}
			if _, err := GetOrCreateGenre(client, input, 1); err == nil {
				t.Error("genre accepted error")
			}
			if _, err := GetOrCreateTag(client, input, 1); err == nil {
				t.Error("tag accepted error")
			}
			if creates != 0 {
				t.Fatalf("created after failed lookup: %d", creates)
			}
		})
	}
}

func TestChapterValidationFailureIsNotSuccess(t *testing.T) {
	source := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"data":[{"index":1,"name":"chapter"}]}`)
	}))
	defer source.Close()
	backend := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodGet {
			w.WriteHeader(404)
		} else {
			w.WriteHeader(422)
		}
	}))
	defer backend.Close()
	if ChaptersHandler(resty.New().SetBaseURL(source.URL), resty.New().SetBaseURL(backend.URL), 1, 1) {
		t.Fatal("422 reported success")
	}
}
