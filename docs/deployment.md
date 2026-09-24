# Hướng dẫn deploy MyTruyen microservices

Phạm vi: một máy chủ Linux, Docker Compose, reverse proxy HTTPS trên host. Đây là runbook để vận hành **sau khi người quản trị chủ động triển khai**, không phải ghi nhận hệ thống đã được deploy. Trong phiên này chỉ chạy test không cần Docker; chưa xác minh Docker build, PostgreSQL/RabbitMQ/Meilisearch thật hoặc rollback thật. Stack single-host không HA. Với Kubernetes/multi-host cần thiết kế deployment khác.

## 1. Điều kiện trước triển khai

- Có domain API, DNS trỏ về server, chứng chỉ TLS và Nginx/Caddy; không mở trực tiếp cổng DB, Rabbit AMQP hay Meilisearch ra Internet.
- Docker Engine + Compose v2 hoạt động trên server, có dung lượng cho images, DB, index, outbox và backup. Xác định RAM/disk bằng tải thực, không dùng test nhỏ để suy ra sizing production.
- CI Java/Python xanh ở đúng commit phát hành; build artifact từ checkout sạch. Ghi lại commit SHA, image IDs/digests, schema migration version và bản backup tương ứng. Không dùng nhánh `main` đang thay đổi làm mốc rollback.
- Staging riêng đã rehearsal import, khóa/concurrency PostgreSQL, mất kết nối broker và smoke xuyên Gateway. Unit tests H2/mocks hiện tại chưa thay thế điều kiện này.
- Chỉ đưa Identity, Catalog, Search vào phạm vi API đã chuyển. Engagement vẫn skeleton; Ingestion chưa xử lý crawler thật. **Không khởi động ingestion-worker để nhận command production** vì consumer skeleton chưa thực hiện nghiệp vụ.

## 2. Cấu hình và secret

Tại checkout release, sao chép `.env.example` thành `.env`; file được Git ignore. Linux đặt quyền `chmod 600 .env`; Windows hạn chế ACL cho tài khoản vận hành. Không paste `.env`, JWT/private key hoặc token vào log, issue hay CI artifact.

Trong shell Linux dùng để deploy, chọn cả cấu hình gốc và overlay restart/log rotation:

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.production.yml
export COMPOSE_PROJECT_NAME=mytruyen-prod
```

Giữ cùng project name qua các release để gắn đúng volumes; nếu hệ thống đã chạy với tên khác thì dùng **tên hiện tại**, không đổi tùy tiện. Overlay đặt `restart: unless-stopped` và giới hạn JSON logs 3 file x 10 MB/service; nó không bổ sung HA/TLS hoặc tự thay secret. Các lệnh Compose bên dưới chạy trong shell có hai biến này.

| Biến | Cách cấu hình |
|---|---|
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | User/password mạnh cho stack hiện tại; đặt trước lần khởi tạo volume đầu tiên |
| `IDENTITY_POSTGRES_DB`, `CATALOG_POSTGRES_DB`, `ENGAGEMENT_POSTGRES_DB` | Ba database thuộc từng service; giữ tên ổn định qua các release |
| `JWT_PRIVATE_KEY_BASE64` | RSA ít nhất 2048-bit, DER PKCS#8 base64; chỉ Identity được nhận |
| `JWT_PUBLIC_KEY_BASE64` | DER X.509 SubjectPublicKeyInfo base64; Identity/Catalog/Gateway dùng cùng key |
| `JWT_ISSUER`, `JWT_AUDIENCE` | Cùng giá trị ở issuer và các verifier; mặc định `mytruyen-auth` / `mytruyen-api` |
| `JWT_ACCESS_SECONDS`, `REFRESH_TOKEN_DAYS` | Mặc định 900 giây / 7 ngày |
| `BOOTSTRAP_ADMIN_*` | Chỉ bật tạm khi tạo admin đầu tiên; xem bước 4 |
| `RABBITMQ_DEFAULT_USER`, `RABBITMQ_DEFAULT_PASS` | Credential broker; dùng secret hex/base64url để không phá cú pháp AMQP URL |
| `MEILI_MASTER_KEY` | Secret quản trị Meili; tối thiểu 16 byte, nên ngẫu nhiên 32 byte trở lên |
| `MEILI_SEARCH_KEY` | Key chỉ có quyền search trên index `books`, dành cho search-service |
| `MEILI_WRITE_KEY` | Key indexer: đọc/tạo index `books`, cập nhật settings, thêm/xóa documents và đọc task status |
| `REDIS_PASSWORD` | Secret riêng; Redis chưa tham gia luồng Search mới |
| `FRONTEND_ORIGIN` | Origin frontend HTTPS chính xác, không wildcard |
| `GATEWAY_BIND_ADDRESS`, `GATEWAY_PORT` | `127.0.0.1`, `8080` khi proxy ở host; không dùng loopback này nếu proxy chạy container khác |
| `RABBITMQ_MANAGEMENT_PORT` | Mặc định 15672, Compose chỉ bind loopback; dùng SSH tunnel khi cần quản trị từ xa |

Sinh JWT trên máy quản trị Windows:

```powershell
.\scripts\generate-jwt-keys.ps1
```

Copy hai giá trị vào secret store/`.env` bảo mật, không commit `.env.jwt.local`. Không sinh lại JWT key sau mỗi deploy; thay key hiện tại sẽ làm access token cũ không hợp lệ và cần kế hoạch rotation riêng.

Trên Linux có thể sinh RSA bằng OpenSSL rồi chuyển private sang DER PKCS#8 (`openssl pkcs8 -topk8 -nocrypt -outform DER`) và public sang DER (`openssl pkey -pubout -outform DER`); base64 phải một dòng. Không nhầm PEM có header với DER base64.

Meili restricted keys được tạo qua API quản trị `/keys` từ mạng nội bộ bằng master key. Tạo key search và writer theo quyền trong bảng, giới hạn `indexes=["books"]`; giữ expiration/rotation trong secret manager. Không dùng search key cho rebuild. Compose hỗ trợ tách hai key, nhưng để trống sẽ **fallback master chỉ nhằm local/bootstrap**. Trước mở traffic thật phải thay bằng restricted keys và recreate Search/indexer. Rebuild cần maintenance key cho index tạm `books_rebuild_*`, get/settings/stats/tasks và swap, không cấp quyền này cho API đọc.

Lưu ý: đổi password trong `.env` **không đổi password của user trong volume PostgreSQL/Rabbit đã tồn tại**. Rotation cần đổi credential tại hệ thống tương ứng rồi cập nhật/recreate client phối hợp. Không xóa volume để “sửa password”. Compose hiện dùng chung user/password DB cho local topology; production nên provision user/quyền riêng mỗi DB và secrets riêng qua deployment override.

## 3. Backup và build release

Trước nâng cấp một hệ thống đã có dữ liệu: đưa chức năng ghi vào maintenance ở ingress, dừng crawler/import/worker ghi liên quan, đợi transaction đang chạy hoàn tất. Ghi nhận backlog outbox/DLQ trước khi dừng indexer.

Backup từng DB bằng `pg_dump -Fc`; ví dụ Linux với **tên user/DB mặc định**:

```bash
umask 077
mkdir -p backups
docker compose exec -T identity-db pg_dump -U mytruyen -d mytruyen_identity -Fc > backups/identity-before-release.dump
docker compose exec -T catalog-db pg_dump -U mytruyen -d mytruyen_catalog -Fc > backups/catalog-before-release.dump
docker compose exec -T engagement-db pg_dump -U mytruyen -d mytruyen_engagement -Fc > backups/engagement-before-release.dump
```

Thay đúng user/database nếu khác mặc định. Dùng tên backup có timestamp/release riêng, không ghi đè bản trước. Mã hóa và copy ra ngoài server; thử restore vào DB riêng trước khi xem backup là dùng được. Với PowerShell dùng công cụ/phiên bản bảo toàn binary, không redirect binary qua bản PowerShell cũ. Meili snapshot/dump hoặc backup volume lúc đã stop sạch theo phiên bản đang dùng; Rabbit cần lưu definitions và dữ liệu queue phù hợp. Không chép raw PostgreSQL volume khi DB đang ghi.

```bash
docker compose config --quiet
docker compose build identity-service catalog-service search-service search-indexer engagement-service mytruyen-gateway
```

Không in `docker compose config` đầy đủ vào log vì có thể lộ secret. Search image cài từ `uv.lock`, bỏ dev dependencies và chạy non-root. Khi release chính thức nên publish images với commit tag, pin base/dependency image digest đã kiểm chứng và deploy các artifact đó; repo hiện vẫn build tại host. Lưu image của release trước để rollback ứng dụng.

## 4. Khởi động và admin đầu tiên

Với môi trường mới, đặt tạm `BOOTSTRAP_ADMIN_ENABLED=true`, email/password mạnh. Với DB đã có admin hợp lệ, để false. Đừng dùng credential mẫu/test cho production.

```bash
docker compose up -d identity-db catalog-db engagement-db rabbitmq meilisearch
docker compose up -d identity-service catalog-service engagement-service search-service mytruyen-gateway
docker compose ps
docker compose logs --tail=100 identity-service catalog-service
```

Flyway tự chạy khi Java service khởi động; Hibernate chỉ validate schema, không tự tạo/sửa. Catalog hiện có V1–V4, V4 là `search_outbox`. Nếu migration/validation lỗi, giữ maintenance, xem log đã che secret; không sửa checksum hoặc xóa `flyway_schema_history` để ép chạy tiếp.

Đăng nhập admin tại `POST /api/v1/auth/login` bằng JSON `email/password`. Kiểm tra JWT dùng được với `GET /api/v1/admin/catalog/stats/books/count`. Sau khi xác nhận admin hoạt động:

1. Đặt `BOOTSTRAP_ADMIN_ENABLED=false`, xóa email/password bootstrap khỏi config hoạt động.
2. `docker compose up -d --force-recreate identity-service`.
3. Đăng nhập lại để kiểm tra. Không để bootstrap account là tài khoản quản trị dùng chung lâu dài.

Không truyền password/token trực tiếp trong command-line được lưu shell history; dùng công cụ API/secret store an toàn. `/auth/register` chỉ tạo USER, không cho người dùng tự cấp ADMIN.

## 5. Import dữ liệu và tạo Search index

Nếu chuyển từ monolith: **chưa mở ghi đồng thời ở hai backend**. Snapshot nguồn, đối soát UUID/ID, FK, số lượng/checksum; reset sequences sau khi giữ ID cũ. Identity có script import tham khảo trong README riêng; importer Book/Chapter đầy đủ vẫn là phần còn lại, không được coi đã hoàn thành chỉ vì schema/API đã có.

Trước rebuild luôn pause toàn bộ Catalog writers (ingress write routes, admin tools, SQL import, crawler) và stop **mọi** index writer. Public Catalog GET phải còn chạy để rebuild đọc dữ liệu. Flag CLI không tự khóa hệ thống.

```bash
docker compose stop search-indexer
# Cấp MEILI_MASTER_KEY từ secret manager vào môi trường shell hiện tại:
# đó phải là maintenance key, không phải search-only/writer key giới hạn.
docker compose run --rm --no-deps -e MEILI_MASTER_KEY search-indexer \
  python -m app.rebuild --catalog-writes-paused
```

`-e MEILI_MASTER_KEY` đọc secret từ môi trường, không đưa giá trị secret vào command-line. Nếu chưa export, dừng lại để cấu hình; không chạy với key trống. DB mới thực sự chưa có truyện có thể thêm `--allow-empty` để tạo index rỗng; không dùng cờ này để che lỗi kết nối nhầm database. Dataset > 1 triệu cần chủ động tăng `--max-documents` sau kiểm tra tải.

Rebuild dùng index staging, đợi task, đối soát số lượng rồi swap; giữ index trước đó ở tên `previous_index`. Ghi lại task IDs và tên index để rollback. Nếu timeout khi swap, **không chạy swap lại mù quáng**: kiểm tra task trên Meili và index hiện hành trước khi quyết định.

Sau rebuild thành công:

```bash
docker compose up -d search-indexer
docker compose logs --tail=100 search-indexer
```

Đợi pending outbox xử lý xong. Job còn lại đọc trạng thái mới nhất nên có thể xử lý lại sau rebuild. Sau đó mới mở ghi/traffic. Dữ liệu đã có trước V4 hoặc import SQL trực tiếp không tự sinh Search job: rebuild là bước bắt buộc khi cutover.

## 6. Ingress HTTPS và xác nhận trước mở traffic

Reverse proxy chỉ chuyển API tới `http://127.0.0.1:8080`. Cài TLS/certificate renewal theo hệ thống vận hành; redirect HTTP sang HTTPS, giới hạn request body phù hợp content tối đa 1 triệu ký tự, đặt timeout và rate limit riêng cho login/register/search. Không log Authorization, password hoặc nội dung request nhạy cảm. Gateway hiện chưa tự rate-limit; Redis chỉ được dự phòng, không đồng nghĩa giới hạn tốc độ đã bật.

Các kiểm tra read-only qua domain HTTPS:

- `GET /api/v1/stats/books/count`, `/api/v1/stats/chapters/count`, `/api/v1/stats/chapter_content/count` trả 200 với `data` số nguyên.
- `GET /api/v1/search/meili?query=&limit=10&page=1` trả 200; thử thêm truyện đã biết để kiểm tra index, không chỉ query rỗng.
- `GET /api/v1/admin/catalog/stats/books/count` không token phải 401, token USER phải 403, ADMIN phải 200.
- `GET /api/v1/books/topboxes?kind=1&limit=10` phụ thuộc nhà cung cấp bên ngoài; API nhận limit 5–50. Không coi lỗi upstream là lỗi DB.
- Kiểm tra CORS bằng origin frontend thật, drafts/deleted không xuất hiện công khai, cookie/token client không bị cấu hình sai ở proxy.

Kiểm tra có ghi (publish/unpublish, đổi tác giả, xóa, refresh token) chạy trước trên **staging dùng dữ liệu thử** theo [verification](migration/verification.md). Không chạy `smoke-migration.py` lên production vì script chủ động tạo/sửa/xóa dữ liệu. CI job integration chỉ chạy khi workflow được bật thủ công với `run_integration=true`; push/PR mặc định không khởi động Docker.

## 7. Theo dõi và khôi phục Search

- Theo dõi health từng service. Search `/health` chỉ báo process sống, không đảm bảo Meili/Catalog/Rabbit đã sẵn sàng.
- Catalog pending/oldest: `SELECT count(*), min(occurred_at) FROM search_outbox WHERE published_at IS NULL;`.
- Rabbit queue `mytruyen.search.sync.v1`: ready, unacked, consumer; `.dead` > 0 phải cảnh báo và điều tra.
- Logs `search_outbox deferred` và `search_sync retry/dead_letter` giúp xác định pipeline lỗi. Chưa có collector/dashboard/alert server cấu hình sẵn; phải nối hệ giám sát của môi trường trước cutover.
- Đảm bảo retention/backup cho outbox đã publish; không purge pending/DLQ để làm số đo đẹp.

Sau khi sửa lỗi dependency/quyền/index/schema, replay có giới hạn:

```bash
docker compose exec search-indexer python -m app.sync.replay --max-messages 100
```

Replay chỉ ACK DLQ sau khi queue chính xác nhận đã nhận; có thể trùng nếu crash nhưng không dùng snapshot cũ. Event sai schema làm replay dừng để xử lý thủ công. Nếu Meili mất index/dữ liệu hoặc không chắc đã hội tụ, thực hiện lại rebuild có maintenance như bước 5, không xóa database Catalog.

## 8. Rollback

1. Giữ/chuyển maintenance, dừng indexer và writers; ghi lại lỗi, commit/image IDs, migration/task IDs và backlog.
2. Nếu schema vẫn tương thích release trước, deploy lại **artifact đã lưu của release trước**, không chạy `git reset --hard` hoặc build từ nhánh đang thay đổi. Validate health/API trước mở traffic.
3. Flyway V4 là additive nhưng điều đó không chứng minh mọi release rollback an toàn. Code cũ không ghi search_outbox sẽ tạo khoảng trống đồng bộ; phải rebuild khi nâng lại. Không tự drop V4 khi rollback ứng dụng.
4. Nếu bắt buộc rollback dữ liệu, restore bản backup đã kiểm tra vào DB/volume mới, đối soát rồi chuyển kết nối có kiểm soát. Đó là thao tác có thể mất các ghi sau backup, cần người chịu trách nhiệm chấp thuận; không restore đè tùy tiện DB đang chạy.
5. Search chỉ swap về `previous_index` sau khi biết chắc task swap trước đã hoàn tất và writers đã pause. Swap là thao tác đảo trạng thái, chạy lần hai sẽ đảo lại. Nếu dữ liệu Catalog đã tiến lên, rebuild từ Catalog thường phù hợp hơn phục hồi index cũ.
6. Sau rollback: xác nhận login, public/admin stats, Search, outbox/DLQ; giữ backup/index cũ tới hết cửa sổ theo dõi.

**Không dùng `docker compose down --volumes` hoặc `docker system prune --volumes` trên production.** Lệnh dọn volume chỉ nằm trong helper kiểm thử có project tên ngẫu nhiên. Không tự xóa index backup/outbox history khi chưa có retention đã duyệt.

## 9. Trạng thái xác minh hiện tại

Test không Docker: Catalog 33, Gateway 14, Identity 16, Search 60 đều qua; Java bootJar thành công. Engagement build được nhưng chưa có test/nghiệp vụ thật. Topboxes probe trực tiếp đã nhận 200 với tham số hợp lệ. Docker integration, image runtime, restore, fault injection, load test và frontend cutover **chưa được chạy/xác nhận trong phiên này**, theo yêu cầu không bật Docker. Hướng dẫn không thay thế release gate staging.
