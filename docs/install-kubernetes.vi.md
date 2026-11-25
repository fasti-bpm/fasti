[English](install-kubernetes.md) | Tiếng Việt

# Cài đặt Fasti BPM trên Kubernetes

Fasti BPM chạy trên Kubernetes 1.29 trở lên bằng image container chính thức và một cơ sở dữ liệu
bên ngoài, qua **Helm chart** (`distro/kubernetes/helm/fasti-bpm`) hoặc **manifest thuần**
(`distro/kubernetes/manifests`, Kustomize). Fasti BPM được xây dựng trên Camunda 7 CE 7.24.0 và
là dự án độc lập, không liên kết với Camunda Services GmbH.

> **Có từ 1.1.0.** Helm chart và manifest được phát hành cùng Fasti từ bản 1.1.0 và mặc định
> chạy image cùng phiên bản.

Tham chiếu: [README của chart](../distro/kubernetes/helm/fasti-bpm/README.md) (mọi giá trị, tiếng
Anh), [tham chiếu image](../distro/docker/README.vi.md) (mọi biến môi trường),
[hướng dẫn Docker](install-docker.vi.md), [chuyển từ Camunda 7](migrate-from-camunda-7.vi.md).

<a id="1-requirements"></a>

## 1. Yêu cầu

- Kubernetes 1.29 trở lên, `kubectl`, và Helm 3 hoặc 4 cho chart (đã kiểm tra với 3.22 và 4.3).
- Một cơ sở dữ liệu mà cluster truy cập được: PostgreSQL, MariaDB, MySQL, SQL Server hoặc Oracle
  (xem [bảng đã thử](migrate-from-camunda-7.vi.md#limits)). Chart không bao giờ khởi động H2 có
  sẵn trong image; hãy chạy cơ sở dữ liệu dưới dạng dịch vụ managed hoặc bằng operator tùy chọn.
- Mỗi pod: ít nhất 2 CPU và 2 GiB bộ nhớ cho tải thật (mặc định chart yêu cầu 1 CPU và 2 GiB).
- HTTPS phía trước Fasti (Ingress controller có TLS, hoặc load balancer kết thúc TLS): chế độ
  production đặt cookie phiên `Secure`, trình duyệt chỉ gửi cookie này qua HTTPS.

<a id="2-database-and-secrets"></a>

## 2. Cơ sở dữ liệu và secret

Tạo cơ sở dữ liệu và người dùng cho Fasti, ví dụ trên PostgreSQL:

```sql
CREATE USER fasti WITH PASSWORD '<mật khẩu cơ sở dữ liệu>';
CREATE DATABASE fasti OWNER fasti;
```

Lưu thông tin đăng nhập trong Secret; cả chart lẫn manifest đều không chứa mật khẩu:

```bash
kubectl create namespace fasti
kubectl -n fasti create secret generic fasti-db \
  --from-literal=username=fasti --from-literal=password='<mật khẩu cơ sở dữ liệu>'
# quản trị viên cho cơ sở dữ liệu mới, trống (chính sách mật khẩu ở chế độ production: ít nhất
# 10 ký tự, có chữ hoa, chữ thường, chữ số và ký tự đặc biệt)
kubectl -n fasti create secret generic fasti-admin \
  --from-literal=password="Fasti-1-$(openssl rand -hex 12)"
```

Các công cụ quản lý secret (External Secrets Operator, Sealed Secrets, Vault) dùng được theo cùng
cách, miễn Secret có các khóa `username` và `password` (đổi được trong chart).

<a id="3-install-with-helm"></a>

## 3. Cài bằng Helm

Ghi cấu hình vào một file values, ví dụ `fasti-values.yaml`:

```yaml
image:
  tag: "1.3.1"
  # digest: sha256:...            # nên ghim image theo digest
database:
  url: jdbc:postgresql://postgres.db.svc.cluster.local:5432/fasti
  existingSecret: fasti-db
  schemaUpdate: true               # chỉ với cơ sở dữ liệu mới: tạo bảng
  waitFor: postgres.db.svc.cluster.local:5432
admin:                             # chỉ với cơ sở dữ liệu mới
  user: admin
  existingSecret: fasti-admin
engine:
  timezone: UTC                    # giống nhau trên mọi node dùng chung cơ sở dữ liệu
ingress:
  enabled: true
  className: nginx
  hosts:
    - host: fasti.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: fasti-tls        # ví dụ do cert-manager cấp
      hosts: [fasti.example.com]
```

```bash
helm install fasti-bpm distro/kubernetes/helm/fasti-bpm -n fasti -f fasti-values.yaml
kubectl -n fasti rollout status deployment/fasti-bpm
helm test fasti-bpm -n fasti      # kiểm tra readiness qua Service
```

Mở `https://fasti.example.com/fasti/` và đăng nhập bằng `admin`. REST API ở
`https://fasti.example.com/engine-rest` và cần thông tin Basic hoặc bearer token
([hướng dẫn REST API](api/README.vi.md)). Không có Ingress:
`kubectl -n fasti port-forward svc/fasti-bpm 8080:8080`, rồi mở `http://localhost:8080/fasti/`.

Chart chạy ở chế độ production, hệ thống file gốc chỉ đọc, uid 1000, không capability nào và đạt
Pod Security Standard `restricted`, nên namespace có thể bắt buộc chuẩn này:

```bash
kubectl label namespace fasti pod-security.kubernetes.io/enforce=restricted
```

<a id="4-install-with-plain-manifests"></a>

## 4. Cài bằng manifest thuần

`distro/kubernetes/manifests` gồm Namespace (có nhãn Pod Security `restricted`), ServiceAccount,
Deployment, Service và PodDisruptionBudget, thêm `ingress.yaml` và `networkpolicy.yaml` tùy chọn
và `secret-example.yaml` (không được áp dụng). Sửa `DB_URL`, tag image, `TZ` và tài nguyên trong
`deployment.yaml` (hoặc trong một overlay Kustomize), tạo các Secret ở mục 2, rồi:

```bash
kubectl apply -k distro/kubernetes/manifests
kubectl -n fasti rollout status deployment/fasti
```

Overlay giữ thay đổi của bạn tách khỏi file của dự án:

```yaml
# my-fasti/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../fasti/distro/kubernetes/manifests
images:
  - name: fastidev/fasti-bpm
    newTag: "1.3.1"
patches:
  - target: {kind: Deployment, name: fasti}
    patch: |
      - op: replace
        path: /spec/template/spec/containers/0/env/1/value
        value: jdbc:postgresql://db.example.com:5432/fasti
```

<a id="5-existing-camunda-7-database"></a>

## 5. Nối vào cơ sở dữ liệu Camunda 7 có sẵn

Trước hết làm theo các bước kiểm tra trong [hướng dẫn Docker, mục 6](install-docker.vi.md#6-connecting-to-an-existing-camunda-7-database):
sao lưu, phiên bản schema `7.24.x`, phân quyền, `TZ`, mức lịch sử, tiền tố bảng. Sau đó bỏ
`database.schemaUpdate` và `admin.*` (manifest: bỏ `FASTI_DB_SCHEMA_UPDATE`, `FASTI_ADMIN_USER`,
`FASTI_ADMIN_PASSWORD_FILE` và nguồn volume `fasti-admin`). Log phải có dòng
`ENGINE-03115 Database schema version 7.24.0 matches this engine.` Nếu các node Camunda 7 vẫn chạy
trên cùng cơ sở dữ liệu, đọc [chạy song song với node Camunda 7](install-docker.vi.md#7-side-by-side-with-camunda-7-nodes).

<a id="6-several-replicas"></a>

## 6. Nhiều bản sao và tính sẵn sàng cao

```yaml
replicaCount: 2
podAntiAffinityPreset: soft        # hard: không bao giờ hai pod trên một node
topologySpreadConstraints:
  - maxSkew: 1
    topologyKey: topology.kubernetes.io/zone
    whenUnsatisfiable: ScheduleAnyway
ingress:
  annotations:
    nginx.ingress.kubernetes.io/affinity: cookie
database:
  pool:
    maxActive: 30
```

- Job executor của mọi pod dùng chung các bảng job; khóa lạc quan (optimistic locking) bảo đảm mỗi
  job chỉ chạy trên một pod. Trong lần thử của hướng dẫn này (k3s 1.33, PostgreSQL 16, hai pod),
  240 job bất đồng bộ đều chạy đúng một lần, chia trên cả hai pod.
- `engine.jobExecutor.deploymentAware` giữ `false` khi mọi pod chạy cùng image và plugin. Chỉ đặt
  `true` trên mọi node khi cơ sở dữ liệu dùng chung với các node có process application chứa lớp
  Java mà các pod này không có.
- Web UI giữ phiên theo từng pod: cấu hình cookie affinity ở Ingress controller (annotation trên
  dành cho ingress-nginx), hoặc `service.sessionAffinity: ClientIP`. Client REST dùng Basic hoặc
  bearer token không cần.
- PodDisruptionBudget (`maxUnavailable: 1`) giữ mọi pod trừ một pod khi drain node; rolling update
  khởi động pod mới trước khi dừng pod cũ (`maxSurge: 1`, `maxUnavailable: 0`).
- Tổng connection pool của mọi pod phải nhỏ hơn `max_connections` của cơ sở dữ liệu.
- Tự co giãn theo CPU (tùy chọn): `autoscaling.enabled: true` (cần metrics server).

<a id="7-single-sign-on"></a>

## 7. Đăng nhập một lần và bearer token

```bash
kubectl -n fasti create secret generic fasti-oidc --from-literal=client-secret='<client secret>'
```

```yaml
oidc:
  enabled: true
  clientName: Keycloak
  issuerUri: https://sso.example.com/realms/fasti
  clientId: fasti
  existingSecret: fasti-oidc
  groupsClaim: groups
  adminGroups: [fasti-admins]
  rest:
    bearerEnabled: true
    audiences: [fasti-api]
```

Nhà cung cấp phải cho phép redirect URI `https://fasti.example.com/login/oauth2/code/oidc`. Pod
phải truy cập được issuer qua HTTPS (thêm vào `networkPolicy.egress` nếu giới hạn egress). Thêm
tùy chọn: [README của chart](../distro/kubernetes/helm/fasti-bpm/README.md#single-sign-on-openid-connect)
và [hướng dẫn đăng nhập một lần](single-sign-on.vi.md).

<a id="8-network-policy"></a>

## 8. Network policy

```yaml
networkPolicy:
  enabled: true
  ingressFrom:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: ingress-nginx
  egress:
    - to:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: db
      ports:
        - port: 5432
          protocol: TCP
```

Khi đặt `egress`, pod chỉ còn tới được DNS và các đích đã liệt kê: thêm nhà cung cấp OIDC, máy chủ
mail và mọi dịch vụ mà quy trình của bạn gọi. Cần CNI có thực thi NetworkPolicy.

<a id="9-health-and-metrics"></a>

## 9. Health check và metrics

| Endpoint | Probe |
|---|---|
| `/actuator/health/liveness` | startup probe (tối đa 5 phút cho lần khởi động đầu) và liveness probe |
| `/actuator/health/readiness` | readiness probe; gồm kết nối cơ sở dữ liệu và engine |

Không cần đăng nhập và ngoài các endpoint này actuator không mở gì khác. Image 1.0.x chưa có
endpoint metrics; vì vậy `metrics.serviceMonitor` tắt theo mặc định. Từ 1.1.0, khi bật metrics
(`FASTI_METRICS_PROMETHEUS_ENABLED=true` cùng token scrape hoặc cổng management, xem
[hướng dẫn Docker](install-docker.vi.md#13-observability)), image phục vụ metrics Prometheus tại
`/actuator/prometheus`. Đặt `management.port` (ví dụ `9464`) để actuator chạy trên cổng riêng:
probe, `helm test`, Service và NetworkPolicy đi theo cổng này, và
`metrics.serviceMonitor.port: management` scrape cổng đó. Chart không gửi token scrape (xem
README của chart).

<a id="10-upgrade-and-rollback"></a>

## 10. Nâng cấp và quay lại

```bash
helm upgrade fasti-bpm distro/kubernetes/helm/fasti-bpm -n fasti -f fasti-values.yaml \
  --set image.tag=<phiên bản mới>
kubectl -n fasti rollout status deployment/fasti-bpm
helm rollback fasti-bpm -n fasti          # về revision trước
```

Các pod được thay lần lượt từng cái và Service vẫn trả lời (đã thử: không request nào lỗi trong
một lần rolling upgrade hai pod). Trong dòng 1.x schema giữ ở 7.24, nên pod cũ và mới chạy song
song được. Đọc [CHANGELOG](../CHANGELOG.md) trước, nhất là các mục **BREAKING**. Thay đổi `config`
hoặc `oidc` tự động khởi động lại pod (annotation checksum); Secret thay đổi thì cần
`kubectl -n fasti rollout restart deployment/fasti-bpm`.

<a id="11-troubleshooting"></a>

## 11. Xử lý sự cố

`kubectl -n fasti logs deployment/fasti-bpm` và `kubectl -n fasti describe pod -l app.kubernetes.io/name=fasti-bpm`.

| Triệu chứng | Nguyên nhân và cách sửa |
|---|---|
| `helm install` báo `database.url … is required` | đặt `database.url` (hoặc `urlKey`) và `database.existingSecret` |
| Pod mãi ở `Pending` | không đủ CPU hoặc bộ nhớ cho requests, hoặc `podAntiAffinityPreset: hard` khi số node ít hơn số bản sao |
| Lỗi `Invalid value 'tcp://…' for configuration property 'server.port'` | có Service tên `fasti` trong namespace và service links đang bật: Kubernetes đặt `FASTI_PORT=tcp://…`, image 1.0.x hiểu đó là cổng HTTP (từ 1.1.0 image bỏ qua giá trị đó kèm cảnh báo). Giữ `enableServiceLinks: false` (mặc định của chart và manifest) |
| `… cannot be read` cho biến `_FILE` | thiếu Secret hoặc khóa; kiểm tra `database.existingSecret` và tên khóa |
| `password authentication failed` | Secret không khớp mật khẩu cơ sở dữ liệu |
| Readiness không lên, log có `ENGINE-03111` | schema cũ hơn 7.24: chạy script nâng cấp (xem hướng dẫn Docker) |
| Startup probe lỗi khi cơ sở dữ liệu chậm | tăng `startupProbe.failureThreshold` |
| Đăng nhập web không giữ được phiên | không có HTTPS phía trước (cookie Secure), hoặc nhiều pod mà không có session affinity |
| `OOMKilled` hoặc `OutOfMemoryError` | tăng `resources.limits.memory`; heap bằng 75 % giới hạn |
| `Insecure configuration refused` | một thiết lập trong `config` hoặc `extraEnv` tắt xác thực REST hoặc phân quyền |

Nguyên nhân khác: [hướng dẫn Docker, xử lý sự cố](install-docker.vi.md#11-troubleshooting).
