package source

import (
	"encoding/json"
	"fmt"
	"regexp"
	"strings"

	"github.com/go-resty/resty/v2"
)

// ID preserves integer JSON literals without passing through float64.
type ID string

var identifier = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._:-]{0,149}$`)
var integer = regexp.MustCompile(`^[0-9]+$`)

func (id *ID) UnmarshalJSON(data []byte) error {
	var value string
	if len(data) > 0 && data[0] == '"' {
		if err := json.Unmarshal(data, &value); err != nil {
			return fmt.Errorf("invalid source ID")
		}
	} else {
		value = string(data)
		if !integer.MatchString(value) {
			return fmt.Errorf("source ID must be a string or integer")
		}
	}
	if !identifier.MatchString(value) {
		return fmt.Errorf("invalid source ID")
	}
	*id = ID(value)
	return nil
}

type Reference struct {
	ID        ID     `json:"id"`
	Name      string `json:"name"`
	LocalName string `json:"local_name"`
	Avatar    string `json:"avatar"`
	Type      string `json:"type"`
}
type Book struct {
	ID             ID              `json:"id"`
	Name           string          `json:"name"`
	Slug           string          `json:"slug"`
	Status         ID              `json:"status"`
	Kind           *int            `json:"kind"`
	Sex            *int            `json:"sex"`
	Synopsis       string          `json:"synopsis"`
	Poster         json.RawMessage `json:"poster"`
	Note           json.RawMessage `json:"note"`
	ChapterPerWeek int             `json:"chapter_per_week"`
	Author         *Reference      `json:"author"`
	Genres         []Reference     `json:"genres"`
	Tags           []Reference     `json:"tags"`
}
type Group struct {
	Name string            `json:"name"`
	Data map[string]string `json:"data"`
}

func (b *Book) UnmarshalJSON(data []byte) error {
	type plain Book
	var decoded plain
	if err := json.Unmarshal(data, &decoded); err != nil {
		return err
	}
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(data, &fields); err != nil {
		return err
	}
	for _, key := range []string{"author", "genres", "tags"} {
		if _, ok := fields[key]; !ok {
			return fmt.Errorf("source omitted required relation %s", key)
		}
	}
	*b = Book(decoded)
	return nil
}

type Options struct {
	Filter struct {
		Status Group            `json:"status"`
		Genres map[string]Group `json:"genres"`
		Tags   map[string]Group `json:"tags"`
	} `json:"filter"`
}
type Client struct{ HTTP *resty.Client }

func get[T any](c *Client, path string) (*T, error) {
	r, err := c.HTTP.R().Get(path)
	if err != nil || r == nil {
		return nil, fmt.Errorf("source request failed")
	}
	if r.StatusCode() != 200 {
		return nil, fmt.Errorf("source returned HTTP %d", r.StatusCode())
	}
	var envelope struct {
		Data *T `json:"data"`
	}
	if json.Unmarshal(r.Body(), &envelope) != nil || envelope.Data == nil {
		return nil, fmt.Errorf("invalid source response")
	}
	return envelope.Data, nil
}
func (c *Client) Book(id string) (*Book, error) {
	if !identifier.MatchString(id) {
		return nil, fmt.Errorf("invalid source book ID")
	}
	b, err := get[Book](c, "/books/"+id+"?include=author,genres,tags")
	if err != nil {
		return nil, err
	}
	if string(b.ID) != id || strings.TrimSpace(b.Name) == "" || strings.TrimSpace(b.Synopsis) == "" || b.Slug == "" || b.Status == "" {
		return nil, fmt.Errorf("source book missing required metadata or mismatched ID")
	}
	if b.Genres == nil || b.Tags == nil {
		return nil, fmt.Errorf("source book must include genres and tags arrays")
	}
	return b, nil
}
func (c *Client) Options() (*Options, error) { return get[Options](c, "/books/options?v=1") }
