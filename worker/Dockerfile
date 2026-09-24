FROM golang:1.26.3-alpine AS builder
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -o /out/worker .
RUN CGO_ENABLED=0 go build -trimpath -o /out/import-book ./cmd/import-book

FROM alpine:3.23
RUN apk add --no-cache ca-certificates && adduser -D -u 10001 worker
WORKDIR /app
COPY --from=builder /out/worker ./worker
COPY --from=builder /out/import-book ./import-book
USER worker
ENTRYPOINT ["./worker"]
