package backend

import (
	"encoding/json"
	"fmt"
	"regexp"
	"strconv"

	"github.com/go-resty/resty/v2"
)

var Identifier = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._:-]{0,149}$`)

type Client struct{ HTTP *resty.Client }
type APIError struct{ Status int }

func (e *APIError) Error() string { return fmt.Sprintf("Catalog returned HTTP %d", e.Status) }

type Author struct {
	Name      string `json:"name"`
	LocalName string `json:"local_name"`
	Avatar    string `json:"avatar"`
}
type Taxon struct {
	Name        string  `json:"name"`
	Slug        string  `json:"slug"`
	Description string  `json:"description"`
	Type        *string `json:"type"`
}
type Metadata struct {
	Name           string         `json:"name"`
	Slug           string         `json:"slug"`
	AuthorID       *string        `json:"author_id"`
	StatusID       int64          `json:"status_id"`
	Kind           int            `json:"kind"`
	Sex            int            `json:"sex"`
	Synopsis       string         `json:"synopsis"`
	Poster         map[string]any `json:"poster"`
	Note           *string        `json:"note"`
	ChapterPerWeek int            `json:"chapter_per_week"`
	GenreIDs       []int64        `json:"genre_ids"`
	TagIDs         []int64        `json:"tag_ids"`
}
type BookResult struct {
	Source     string `json:"source"`
	ExternalID string `json:"external_id"`
	BookID     int64  `json:"book_id"`
	Version    *int64 `json:"version"`
	Outcome    string `json:"outcome"`
}
type ReferenceResult struct {
	Source     string `json:"source"`
	ExternalID string `json:"external_id"`
	Kind       string `json:"kind"`
	ID         string `json:"reference_id"`
	Outcome    string `json:"outcome"`
}

func path(kind, id string) (string, error) {
	if !Identifier.MatchString(id) {
		return "", fmt.Errorf("invalid external ID")
	}
	return "/internal/import/" + kind + "/metruyencv/" + id, nil
}
func execute[T any](c *Client, method, path string, body any) (*T, error) {
	r := c.HTTP.R()
	if body != nil {
		r.SetBody(body)
	}
	response, err := r.Execute(method, path)
	if response != nil && response.StatusCode() >= 300 {
		return nil, &APIError{response.StatusCode()}
	}
	if err != nil {
		return nil, fmt.Errorf("Catalog request failed")
	}
	if response == nil || response.StatusCode() != 200 {
		return nil, fmt.Errorf("unexpected Catalog response")
	}
	var envelope struct {
		Success bool `json:"success"`
		Data    *T   `json:"data"`
	}
	if json.Unmarshal(response.Body(), &envelope) != nil || !envelope.Success || envelope.Data == nil {
		return nil, fmt.Errorf("invalid Catalog response envelope")
	}
	return envelope.Data, nil
}
func validateBook(r *BookResult, id string) error {
	if r.Source != "metruyencv" || r.ExternalID != id || r.BookID <= 0 || r.Version == nil || *r.Version < 0 {
		return fmt.Errorf("invalid book mapping response")
	}
	return nil
}
func (c *Client) LookupBook(id string) (*BookResult, error) {
	p, err := path("books", id)
	if err != nil {
		return nil, err
	}
	r, err := execute[BookResult](c, "GET", p, nil)
	if e, ok := err.(*APIError); ok && e.Status == 404 {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	if err = validateBook(r, id); err != nil {
		return nil, err
	}
	if r.Outcome != "mapped" {
		return nil, fmt.Errorf("book mapping requires manual review")
	}
	return r, nil
}
func (c *Client) ImportBook(id string, metadata Metadata, expected *int64) (*BookResult, error) {
	p, err := path("books", id)
	if err != nil {
		return nil, err
	}
	body := struct {
		Metadata        Metadata `json:"metadata"`
		ExpectedVersion *int64   `json:"expected_version,omitempty"`
	}{metadata, expected}
	r, err := execute[BookResult](c, "PUT", p, body)
	if err != nil {
		return nil, err
	}
	if err = validateBook(r, id); err != nil {
		return nil, err
	}
	if r.Outcome != "created" && r.Outcome != "updated" && r.Outcome != "unchanged" {
		return nil, fmt.Errorf("unexpected import outcome")
	}
	return r, nil
}
func (c *Client) reference(kind, id string, body any) (string, error) {
	p, err := path(kind, id)
	if err != nil {
		return "", err
	}
	r, err := execute[ReferenceResult](c, "PUT", p, body)
	if err != nil {
		return "", err
	}
	if r.Source != "metruyencv" || r.ExternalID != id || r.Kind != kind || r.ID == "" || (r.Outcome != "created" && r.Outcome != "unchanged") {
		return "", fmt.Errorf("invalid reference mapping response")
	}
	return r.ID, nil
}
func (c *Client) ImportAuthor(id string, a Author) (string, error) {
	value, err := c.reference("authors", id, a)
	if err != nil {
		return "", err
	}
	if !regexp.MustCompile(`^[a-fA-F0-9]{8}-(?:[a-fA-F0-9]{4}-){3}[a-fA-F0-9]{12}$`).MatchString(value) {
		return "", fmt.Errorf("invalid author UUID")
	}
	return value, nil
}
func (c *Client) ImportTaxon(kind, id string, t Taxon) (int64, error) {
	if kind != "genres" && kind != "tags" && kind != "book-statuses" {
		return 0, fmt.Errorf("invalid taxonomy kind")
	}
	value, err := c.reference(kind, id, t)
	if err != nil {
		return 0, err
	}
	n, err := strconv.ParseInt(value, 10, 64)
	if err != nil || n <= 0 {
		return 0, fmt.Errorf("invalid taxonomy ID")
	}
	return n, nil
}
