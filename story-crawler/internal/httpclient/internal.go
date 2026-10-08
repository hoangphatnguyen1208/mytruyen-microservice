package httpclient

import (
	"fmt"
	"net/url"
	"strings"

	"github.com/go-resty/resty/v2"
)

// UseInternalImportEndpoints routes crawler data and tasks through the internal API.
func UseInternalImportEndpoints(client *resty.Client, baseURL string) {
	base, _ := url.Parse(strings.TrimRight(baseURL, "/")) // Validated by config.Load.
	client.OnBeforeRequest(func(_ *resty.Client, r *resty.Request) error {
		u, err := url.Parse(r.URL)
		if err != nil {
			return fmt.Errorf("invalid backend request URL")
		}
		path := u.EscapedPath()
		prefix := ""
		if u.IsAbs() {
			if base == nil || u.Scheme != base.Scheme || u.Host != base.Host {
				return fmt.Errorf("unexpected backend request origin")
			}
			prefix = strings.TrimRight(base.EscapedPath(), "/")
			if !strings.HasPrefix(path, prefix+"/") {
				return fmt.Errorf("unexpected backend request path")
			}
			path = strings.TrimPrefix(path, prefix)
		}
		path = strings.TrimLeft(path, "/")
		resource := strings.SplitN(path, "/", 2)[0]
		target := ""
		switch resource {
		case "books", "authors", "genres", "tags", "book-statuses", "chapters":
			target = "/internal/import/crawler/" + path
		case "rabbitmq":
			target = "/internal/import/crawler/tasks/" + strings.TrimPrefix(path, "rabbitmq/")
		}
		if target != "" {
			newPath := prefix + target
			decoded, err := url.PathUnescape(newPath)
			if err != nil {
				return fmt.Errorf("invalid encoded backend path")
			}
			u.Path = decoded
			u.RawPath = newPath
			r.URL = u.String()
		}
		return nil
	})
}
