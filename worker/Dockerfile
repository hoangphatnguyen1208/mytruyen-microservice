FROM golang:1.26.3-alpine AS builder
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -o /out/worker .

FROM alpine:3.23
RUN apk add --no-cache ca-certificates && adduser -D -u 10001 worker
WORKDIR /app
COPY --from=builder /out/worker ./worker
USER worker
ENTRYPOINT ["./worker"]
