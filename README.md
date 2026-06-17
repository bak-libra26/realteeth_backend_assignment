# 백엔드 과제 

**과제 상황**
1. 클라이언트는 본 서버로 이미지 처리 요청을 보낸다.
2. 본 서버는 외부 SaaS에 해당하는 Mock Worker에 이미지 처리를 위임한다.
   - Mock Worker의 처리 시간은 수 초에서 수십 초까지 달라질 수 있다.

이번 과제에서는 Mock Worker의 처리 시간이 길어지는 것과 상관없이 클라이언트의 이미지 처리 요청을 안정적으로 접수하고 처리할 수 있는 구조를 만드는 데 집중했습니다.

## 실행 방법

- 컨테이너 환경에서 실행 가능하게 하기 위하여 Docker와 Docker Compose를 사용하여 애플리케이션을 실행할 수 있도록 했습니다.

```bash
docker --version
docker compose version
```

기본 설정으로 실행하려면 아래 명령을 사용합니다.

```bash
docker compose up --build
```

실행 시 사용되는 주요 설정값은 다음과 같습니다.

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `SERVER_PORT` | `8080` | 애플리케이션 외부 노출 포트 |
| `MYSQL_PORT` | `3306` | MySQL 외부 노출 포트 |
| `MYSQL_DATABASE` | `assignment` | 애플리케이션이 사용할 DB 이름 |
| `MYSQL_USER` | `guest` | 애플리케이션 DB 사용자 |
| `MYSQL_PASSWORD` | `guest` | 애플리케이션 DB 비밀번호 |
| `MYSQL_ROOT_PASSWORD` | `img-proc-root` | MySQL root 비밀번호 |
| `MOCK_WORKER_BASE_URL` | `https://dev.realteeth.ai/mock` | Mock Worker 주소 |
| `CANDIDATE_NAME` | `심정훈` | Mock Worker API Key 발급에 사용할 이름 |
| `CANDIDATE_EMAIL` | `bak.libra26@gmail.com` | Mock Worker API Key 발급에 사용할 이메일 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | 허용할 CORS origin |
| `CORS_ALLOWED_METHODS` | `GET,POST,OPTIONS` | 허용할 HTTP method |
| `CORS_ALLOWED_HEADERS` | `Content-Type,Idempotency-Key` | 허용할 HTTP header |
| `CORS_MAX_AGE` | `1h` | CORS preflight cache 시간 |

실행 후 확인할 수 있는 주소는 다음과 같습니다.

| 항목 | 값 |
|---|---|
| Server | `http://localhost:8080` |
| MySQL | `localhost:3306` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| OpenAPI JSON | `docs/openapi.json` 또는 Swagger UI |

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.4, Spring MVC, Spring Data JPA |
| Database | MySQL 8.4 |
| API Docs | Springdoc OpenAPI, Swagger UI |
| Build / Runtime | Maven, Docker Compose |

기술 스택은 구직 공고에서 확인한 Spring Boot, MySQL 기반 백엔드 환경에 맞춰 구성했습니다.
이번 과제에서는 레디스나 메시지 큐와 같은 별도 인프라를 추가하지 않고 DB 기반으로 요청 접수, 상태 추적, 재시도 흐름을 단순하게 풀어내는 데 집중했습니다.

## 테스트

```bash
./mvnw test
```

주요 비즈니스 흐름과 API 요청 검증을 중심으로 작성했습니다.

## Mock Worker 연동

### API Key 발급 처리하기

Mock Worker에 작업을 제출하거나 상태를 조회하려면 `X-API-KEY` 헤더가 필요합니다.
API Key 발급에 필요한 `candidateName`, `email`은 `application.yaml`에 두고, `@ConfigurationProperties`를 통해 주입받도록 했습니다.

| 항목 | 값 |
|---|---|
| Method | `POST` |
| Path | `/mock/auth/issue-key` |
| Content-Type | `application/json` |
| Body | `candidateName`, `email` |

Mock Worker와의 HTTP 통신은 `MockWorkerClient`가 담당하도록 했습니다.
API Key 발급 요청도 Mock Worker로 보내는 외부 요청이기 때문에 `MockWorkerClient.issueApiKey(...)`에서 처리합니다.

발급된 API Key의 보관과 사용 가능 여부 관리는 `MockWorkerApiKeyProvider`가 담당합니다.
API Key가 필요한 worker는 provider를 통해 값을 가져오도록 해서, Mock Worker 호출 책임과 API Key 관리 책임을 분리했습니다.


## 이미지 처리 요청 / REQ

### 외부 처리 시간과 요청 응답 시간 분리하기

이미지 처리 요청에서 가장 먼저 신경 쓴 부분은 클라이언트 요청이 Mock Worker의 처리 시간에 영향을 크게 받지 않도록 하는 것이었습니다.
Mock Worker는 외부 SaaS처럼 동작하고, 처리 시간도 수 초에서 수십 초까지 달라질 수 있기 때문에 클라이언트가 수십 초의 시간을 기다리지 않게 하는 것이 중요하다고 생각했습니다.

그래서 본 서버는 요청을 받으면 먼저 이미지 처리 요청을 `Job` 엔티티로 변환하고 DB에 저장한 뒤 바로 응답하게 하고
Mock Worker에게 이미지 처리 작업을 위임하는 것은 클라이언트 응답 이후에 내부 worker가 진행하도록 했습니다.

이렇게 클라이언트에게 이미지 처리 요청을 받고 응답을 주는 작업과 Mock Worker에게 실제 이미지 처리 요청을 위임하는 작업을 나누면 아래와 같은 이점을 얻을 수 있다고 생각했습니다. 

- 클라이언트는 Mock Worker가 이미지 처리 작업에 소요되는 시간과 관계없이, 서버가 DB에 작업을 저장한 직후 응답을 받을 수 있습니다.
- Mock Worker에 장애가 나더라도 이미지 처리 요청 접수 자체는 실패하지 않고, Mock Worker 장애가 해결된 이후에 내부 worker가 다시 이미지 처리 요청을 재처리할 수 있습니다.
- 많은 요청이 한 번에 몰려도 모든 요청을 즉시 Mock Worker로 넘기지 않고, Job으로 저장한 뒤 worker 처리량에 맞춰 처리할 수 있습니다.
- 작업 상태가 DB에 남기 때문에 서버 재시작이나 외부 호출 실패 이후에도 다시 처리할 수 있습니다.

요청 본문도 이 구조에 맞춰 최소화했습니다.
응답 이후 worker가 Mock Worker에 작업을 위임할 수 있으면 충분하므로, 요청 정보는 Job 엔티티로 저장하고 본문에는 `imageUrl`만 받도록 했습니다.

### 작업 생성 API 설계하기

#### Mock Worker 의 이미지 처리 요청 규격

| 항목             | 값                     |
|----------------|-----------------------|
| Method         | `POST`                |
| Path           | `/mock/process`       |
| Header         | `X-API-KEY: {apiKey}` |
| Content-Type   | `application/json`    |
| Body           | `"imageUrl": "https://example.com/image.png"`          |


Mock Worker의 이미지 처리 요청은 파일 자체가 아니라 `imageUrl`을 받는 구조였습니다.
본 서버도 이 흐름에 맞춰 작업 생성 요청 본문을 `imageUrl` 하나로 제한했습니다.
파일 업로드까지 직접 처리하면 저장소, 파일 크기 제한, 업로드 실패 처리까지 서버가 책임져야 하므로, 이번 과제에서는 이미지 처리 요청 작업을 어떻게 다룰지와 상태 관리에 집중했습니다.

클라이언트가 본 서버로 보내는 이미지 처리 요청은 다음과 같습니다.

| 항목             | 값                                      |
|----------------|----------------------------------------|
| Method         | `POST`                                 |
| Path           | `/api/v1/jobs`                         |
| Header         | `Idempotency-Key: {idempotencyKey}`    |
| Content-Type   | `application/json`                     |
| Body           | `"imageUrl": "https://example.com/image.png"` |
| Success Status | `202 Accepted`                         |

여기서 `Idempotency-Key`를 별도로 받은 이유는 Mock Worker의 동작 방식 때문입니다.
Mock Worker는 이미지 처리 요청을 받을 때마다 내부 작업 식별자와 상태를 반환합니다.
동일한 `imageUrl`로 여러 번 요청해도 Mock Worker는 각 요청을 별도의 작업으로 보고 새로운 worker job을 생성할 수 있습니다.

그래서 클라이언트 요청의 중복 여부는 본 서버에서 먼저 판단해야 했습니다.
본 서버는 `Idempotency-Key`를 기준으로 이미 접수된 요청인지 확인하고, 같은 요청이 다시 들어오면 새 `Job`을 만들지 않고 기존 `Job`을 반환합니다.

멱등성 처리 기준은 다음과 같이 두었습니다.

- 같은 `Idempotency-Key`와 같은 `imageUrl`이면 동일 요청으로 보고 기존 `Job`을 반환한다.
- 같은 `Idempotency-Key`지만 다른 `imageUrl`이면 잘못된 재사용으로 보고 `409 Conflict`를 반환한다.
- 같은 `imageUrl`이라도 다른 `Idempotency-Key`를 사용하면 별도의 요청으로 보고 새로운 `Job`을 생성할 수 있다.

작업 생성 API는 실제 이미지 처리 완료를 의미하지 않기 때문에 `200 OK`가 아니라 `202 Accepted`를 반환하도록 했습니다.
이 응답은 서버가 요청을 접수했고, 이후 내부 worker가 해당 작업의 처리를 이어간다는 의미입니다.


### 상태 모델 정의하기

이미지 처리 요청은 서버 내부에서 `Job`이라는 작업 단위로 저장됩니다.
이 Job은 단순히 요청 정보를 보관하는 용도뿐 아니라, worker가 다음에 어떤 작업을 처리해야 하는지 판단하는 기준으로도 사용됩니다.

예를 들어 어떤 Job은 아직 Mock Worker에 제출해야 하고, 어떤 Job은 이미 제출되어 결과 조회를 기다리고 있으며, 어떤 Job은 완료 또는 실패로 더 이상 처리하지 않아야 합니다.
이 흐름을 구분하기 위해 Job의 상태 모델이 필요했습니다.

그래서 Job의 상태를 `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` 네 가지로 나눴습니다.
클라이언트 요청을 받은 직후에는 아직 Mock Worker에 제출된 상태가 아니므로 `PENDING`으로 저장합니다.
이후 Mock Worker에 정상 제출되면 `PROCESSING`, 최종 결과에 따라 `COMPLETED` 또는 `FAILED`가 됩니다.
이 상태값은 클라이언트도 조회할 수 있습니다.

| 상태 | 의미 |
|---|---|
| `PENDING` | 서버가 클라이언트 요청을 접수했고, 아직 Mock Worker에 작업을 제출하지 않은 상태 |
| `PROCESSING` | Mock Worker에 작업이 제출되었고, 처리 결과를 기다리는 상태 |
| `COMPLETED` | Mock Worker 처리가 성공적으로 완료되어 결과를 조회할 수 있는 상태 |
| `FAILED` | 작업 제출 또는 처리 과정에서 실패가 확정된 상태 |

이 상태값은 내부 worker의 처리 기준으로도 사용됩니다.
예를 들어 `PENDING` 상태의 Job은 Mock Worker에 제출할 대상이고, `PROCESSING` 상태의 Job은 Mock Worker에 결과를 조회할 대상입니다.
`COMPLETED`와 `FAILED`는 최종 상태로 보고 더 이상 worker가 처리하지 않습니다.

상태가 잘못 건너뛰지 않도록 다음 전이는 허용하지 않았습니다.

- `COMPLETED`, `FAILED` 상태의 Job은 다시 다른 상태로 변경하지 않습니다.
- `PENDING` 상태에서 바로 `COMPLETED`로 변경하지 않습니다.
- `PROCESSING` 상태는 Mock Worker에 제출된 이후의 상태이므로 `workerJobId`가 있어야 합니다.

### Mock Worker에 작업 처리 요청 보내기

작업 생성 API는 Job을 DB에 저장한 뒤 바로 클라이언트에게 응답합니다.
따라서 Mock Worker에 실제로 작업을 제출하는 일은 내부 worker가 맡습니다.

스케줄러 방식의 `JobSubmitWorker`는 일정 주기로 `PENDING` 상태의 Job을 조회하고 Mock Worker에 작업을 제출합니다.
제출에 성공하면 Mock Worker가 반환한 `workerJobId`를 Job에 저장하고 상태를 `PROCESSING`으로 변경합니다.

이 흐름에서 클라이언트 요청은 Job 생성 시점에 끝나고, Mock Worker 제출은 worker가 별도로 이어서 처리합니다.
즉, 클라이언트의 작업 생성 요청과 외부 Mock Worker 제출 시점을 분리한 구조입니다.
`PROCESSING` 상태 이후의 결과 조회는 별도 poll worker 흐름에서 처리합니다.

### Worker 처리 지연 완화하기

스케줄러 기반 worker는 구조가 단순하지만, 요청이 몰리면 Job이 DB에 쌓일 수 있습니다.
특히 worker가 가져온 Job을 반복문 안에서 하나씩 동기 방식으로 Mock Worker에 요청하는 경우에는 앞선 요청이 끝날 때까지 다음 요청을 시작하지 못합니다.
Mock Worker 응답이 느린 상황에서는 이 지연이 더 커질 수 있습니다.

이를 줄이기 위해 worker는 Job을 batch 단위로 가져오고, 각 Job 처리는 `CompletableFuture`와 별도 executor에 위임했습니다.
한 번에 여러 Mock Worker 요청을 보낼 수 있게 하되, 제한 없이 늘어나지는 않도록 했습니다.

`batch-size`는 한 번에 가져올 Job 수를 의미하고, `concurrency`는 동시에 실행할 worker 작업 수를 의미합니다.
worker 실행 주기도 설정으로 분리해두어, 트래픽 상황이나 Mock Worker의 응답 특성에 따라 처리량과 외부 API 부하를 조정할 수 있게 했습니다.

이렇게 해서 한 서버 인스턴스 안에서도 Mock Worker 요청을 순차 처리하지 않고, 설정한 동시성 범위 안에서 병렬로 처리합니다.

다만 단일 서버 인스턴스의 병렬 처리만으로는 트래픽 증가에 한계가 있을 수 있습니다.
그래서 서버 인스턴스가 늘어나는 상황에서도 같은 DB의 Job을 나누어 처리할 수 있는 구조가 필요했습니다.

### 여러 서버 인스턴스에서 같은 Job을 처리하지 않도록 하기

서버 인스턴스를 여러 대로 늘리면 여러 worker가 같은 조건으로 Job을 조회할 수 있습니다.
이때 같은 Job을 동시에 가져가면 Mock Worker 제출이 중복될 수 있으므로, 처리 대상 Job을 선점하는 순간에는 DB row lock을 사용했습니다.

조회 쿼리에는 `FOR UPDATE SKIP LOCKED`를 사용했습니다.
이미 다른 worker가 lock을 잡은 row는 기다리지 않고 건너뛰기 때문에, 여러 worker가 동시에 실행되어도 서로 다른 Job을 가져갈 수 있습니다.

다만 row lock은 Job을 선점하는 짧은 트랜잭션 구간에서만 사용했습니다.
Mock Worker 호출은 수 초에서 수십 초까지 걸릴 수 있기 때문에, 외부 호출이 끝날 때까지 DB 트랜잭션과 row lock을 유지하면 DB connection과 lock이 오래 점유되어 전체 처리량이 떨어질 수 있다고 판단했습니다.

그래서 row lock은 처리할 Job을 고르는 구간에서만 사용하고, 실제 Mock Worker 호출은 트랜잭션 밖에서 수행했습니다.

대신 트랜잭션이 끝난 뒤에도 해당 Job이 처리 중이라는 사실은 DB에 남겨야 했습니다.
이를 위해 Job에 점유(Lease) 개념을 두었습니다.
row lock은 짧은 순간의 동시 선점을 막는 장치이고, lease는 트랜잭션 이후에도 유지되는 작업 점유 상태입니다.

| 필드 | 의미 |
|---|---|
| `leaseType` | 현재 Job이 어떤 worker 단계에서 처리 중인지 나타냄 |
| `leasedUntil` | 해당 처리 권한이 언제 만료되는지 나타냄 |

`leaseType`은 다음 값을 가집니다.

| leaseType | 의미 |
|---|---|
| `NONE` | 어떤 worker도 처리 중이지 않은 상태 |
| `SUBMIT` | Mock Worker에 작업 제출을 시도 중인 상태 |
| `POLL` | Mock Worker에 결과 조회를 시도 중인 상태 |

이를 포함한 Mock Worker 제출 흐름은 다음과 같습니다.

| 단계 | status | leaseType | 의미 |
|---|---|---|---|
| 작업 생성 직후 | `PENDING` | `NONE` | 클라이언트 요청을 Job으로 저장한 상태 |
| submit worker가 작업 점유 | `PENDING` | `SUBMIT` | Mock Worker에 작업 제출 중 |
| Mock Worker 제출 성공 | `PROCESSING` | `NONE` | Mock Worker에 제출이 완료되어 결과 조회를 기다리는 상태 |

이후 결과 조회는 poll worker가 `PROCESSING + NONE` 상태의 Job을 대상으로 별도로 수행합니다.

이렇게 상태와 lease를 함께 관리하면 DB만 보더라도 Job이 단순히 대기 중인지, Mock Worker에 제출 중인지, 결과 조회를 기다리는 중인지 구분할 수 있습니다.
또한 worker가 처리 중 종료되더라도 `leasedUntil`을 기준으로 점유가 만료된 Job을 다시 처리 대상으로 되돌릴 수 있습니다.

### Mock Worker 제출 중 실패와 재시작 복구

이미지 처리 요청 단계에서 row lock은 Job을 선점하는 짧은 구간에서만 사용하고, 실제 Mock Worker 호출은 트랜잭션 밖에서 수행합니다.
그래서 Mock Worker 호출 중 문제가 생기면 DB row lock이 아니라 Job에 남겨둔 lease 정보를 기준으로 복구 여부를 판단해야 했습니다.

Mock Worker 작업 제출은 외부 네트워크를 통해 이루어지기 때문에 다음과 같은 예외 상황이 발생할 수 있습니다.

- Mock Worker 제출 요청 중 네트워크 오류가 발생한 경우
- Mock Worker가 `429 Too Many Requests` 또는 `5xx`를 반환한 경우
- submit worker가 Job을 선점한 뒤 서버가 종료된 경우
- 서버가 Mock Worker에 제출 요청을 보냈지만 응답을 받기 전에 종료된 경우

Mock Worker 제출에 실패하더라도 케이스가 달라 모두 같은 방식으로 처리할 수 없었는데
일시적인 실패로 볼 수 있는 경우는 다시 시도할 수 있게 남겨두고, 요청 자체가 잘못된 경우처럼 재시도 의미가 낮은 경우만 실패로 처리했습니다.

| 상황 | 처리 |
|---|---|
| 네트워크 오류 | `PENDING + SUBMIT` 유지 후 lease 만료 시 재시도 |
| `429 Too Many Requests` | `PENDING + SUBMIT` 유지 후 lease 만료 시 재시도 |
| `5xx` | `PENDING + SUBMIT` 유지 후 lease 만료 시 재시도 |
| 응답 파싱 실패 또는 비정상 응답 | `PENDING + SUBMIT` 유지 후 lease 만료 시 재시도 |
| 재시도해도 의미가 낮은 `4xx` | `FAILED + NONE`으로 실패 확정 |

`PENDING + SUBMIT`은 submit worker가 해당 Job을 선점한 뒤 Mock Worker 제출을 시도 중이라는 의미입니다.
네트워크 오류, `429`, `5xx`처럼 일시적인 실패가 발생하면 Job을 바로 `FAILED`로 바꾸지 않고 이 상태를 유지합니다.
외부 시스템의 순간적인 장애나 네트워크 문제 때문에 클라이언트의 작업이 최종 실패로 끝나는 상황을 줄이기 위해서입니다.

정상적으로 제출이 끝나면 `PROCESSING + NONE`으로 변경되지만, 처리 중 예외가 발생하거나 서버가 종료되면 `PENDING + SUBMIT`으로 남을 수 있습니다.
이를 복구하기 위해 `JobLeaseRecoveryWorker`가 주기적으로 만료된 lease를 조회하고, `leaseType`을 `NONE`으로 되돌립니다.
그러면 `PENDING + SUBMIT`으로 멈춰 있던 Job은 `PENDING + NONE`이 되고, 다시 submit worker의 처리 대상이 됩니다.

서버가 재시작되더라도 Job 상태와 lease 정보는 DB에 남아 있습니다.
따라서 서버는 메모리에 있던 작업 목록에 의존하지 않고, DB에 저장된 상태를 기준으로 다시 처리할 수 있습니다.

- `PENDING + NONE`: Mock Worker에 제출할 수 있음
- `PENDING + SUBMIT`: submit worker가 제출을 시도 중이거나, 제출 중 중단된 상태
- 만료된 `PENDING + SUBMIT`: recovery worker가 `PENDING + NONE`으로 되돌린 뒤 다시 제출 대상이 됨

### 요청 단계의 처리 보장 모델

작업 생성 API에서는 `Idempotency-Key`를 기준으로 같은 요청이 중복 생성되지 않게 했습니다.
같은 `Idempotency-Key`와 같은 `imageUrl`이면 기존 Job을 반환하고, 같은 `Idempotency-Key`로 다른 `imageUrl`을 보내면 충돌로 처리합니다.

다만 이 처리는 본 서버의 Job 생성 중복을 막는 범위에 가깝습니다.
Mock Worker를 통해 이미지 처리 작업을 exactly-once로 보장하기는 어렵습니다.
Mock Worker의 `/process` API는 요청을 식별할 수 있는 멱등성 키를 받지 않고, 동일한 `imageUrl` 요청도 새로운 작업으로 처리할 수 있습니다.
그리고 서버가 Mock Worker에 제출 요청을 보낸 뒤 응답을 받기 전에 종료되면, 본 서버는 Mock Worker가 실제로 요청을 받았는지 확정할 수 없습니다.

이 경우 본 서버는 lease 만료 후 `PENDING + NONE`으로 복구하고 다시 제출을 시도합니다.
내부 Job을 유실하지 않고 다시 처리한다는 관점에서는 at-least-once에 가깝지만, Mock Worker 쪽 worker job이 중복 생성될 가능성은 남습니다.

## 이미지 처리 결과 조회 / RES

### Mock Worker 처리 결과를 미리 동기화하기

Mock Worker API는 작업이 끝났을 때 본 서버로 결과를 보내주는 callback/webhook 구조가 아니라 본 서버가 `workerJobId`로 상태를 직접 조회해야 하는 polling 구조였습니다.

클라이언트가 결과 조회 API를 호출할 때마다 본 서버가 Mock Worker에 상태를 조회하고, 그 결과를 DB에 반영한 뒤 반환하는 방식도 생각할 수 있었습니다만 이 방식은 결과 동기화가 클라이언트 조회 요청에 의해 발생하기 때문에, 첫 조회 요청은 여전히 Mock Worker 응답 시간을 기다려야 했습니다.
또 같은 Job에 대한 조회가 동시에 들어오면 Mock Worker 상태 조회가 중복될 수 있고, 클라이언트가 조회하지 않는 Job은 상태 반영이 늦어질 수 있습니다.

그래서 본 서버는 클라이언트 조회 요청과 별개로, 내부 poll worker가 주기적으로 Mock Worker의 처리 상태를 조회하고 그 결과를 DB의 Job 상태와 `result`에 미리 반영하도록 했습니다.
클라이언트의 결과 조회 API는 Mock Worker를 직접 호출하지 않고, DB에 저장된 Job 상태와 결과를 조회해 반환합니다.

이렇게 하면 결과 조회 요청은 DB 조회만 수행하므로 Mock Worker의 응답 지연에 직접 묶이지 않습니다.
클라이언트가 같은 Job을 여러 번 조회하더라도 매번 Mock Worker를 호출하지 않고, poll worker의 interval, batch size, concurrency 기준으로 조회량을 조절할 수 있습니다.
또 Mock Worker 상태를 내부 Job 상태로 변환해 저장하기 때문에, 클라이언트는 본 서버의 Job 상태 모델만 보면 됩니다.
`workerJobId`와 Job 상태가 DB에 남아 있어 서버 재시작 후에도 `PROCESSING` 상태의 Job을 다시 polling 대상으로 삼을 수 있습니다.

### Poll Worker의 상태 반영 흐름

Mock Worker에 이미지 처리 작업 제출이 완료된 Job은 `PROCESSING + NONE` 상태로 남습니다.
이 상태는 Mock Worker에 작업은 제출되었지만, 아직 결과 조회 worker가 해당 Job을 점유하지 않은 상태입니다.

`JobPollWorker`는 일정 주기로 `PROCESSING + NONE` 상태이면서 `workerJobId`가 있는 Job을 조회합니다.
poll worker가 Job을 선점하면 `PROCESSING + POLL`로 변경한 뒤 Mock Worker에 상태를 조회합니다.

Mock Worker 응답에 따른 내부 반영은 다음과 같습니다.

| Mock Worker 응답 | 내부 처리 |
|---|---|
| `PROCESSING` | 아직 완료되지 않은 상태로 보고, 현재 poll lease가 만료된 뒤 다시 조회 대상으로 둡니다 |
| `COMPLETED` | `COMPLETED + NONE`으로 변경하고 `result`를 저장합니다 |
| `FAILED` | `FAILED + NONE`으로 변경하고 실패 내용을 저장합니다 |

결과 조회 이후의 상태 흐름은 다음과 같습니다.

| 단계 | status | leaseType | 의미 |
|---|---|---|---|
| 결과 조회 대기 | `PROCESSING` | `NONE` | Mock Worker에 제출되었고 결과 조회를 기다리는 상태 |
| poll worker가 작업 점유 | `PROCESSING` | `POLL` | Mock Worker에 결과 조회 중 |
| 성공 결과 반영 | `COMPLETED` | `NONE` | 결과 조회가 가능한 최종 성공 상태 |
| 실패 결과 반영 | `FAILED` | `NONE` | 실패가 확정된 최종 상태 |

### Mock Worker 결과 조회 중 실패와 재시작 복구

결과 조회 역시 외부 네트워크 요청이므로 일시적인 실패가 발생할 수 있습니다.

- Mock Worker 결과 조회 중 네트워크 오류가 발생한 경우
- Mock Worker가 `429 Too Many Requests` 또는 `5xx`를 반환한 경우
- poll worker가 Job을 선점한 뒤 서버가 종료된 경우
- 서버가 Mock Worker에 결과 조회 요청을 보냈지만 응답을 받기 전에 종료된 경우

이런 상황에서는 Job을 바로 `FAILED`로 확정하지 않고 `PROCESSING + POLL` 상태로 남기는데 Mock Worker 처리가 실제로 실패한 것이 아니라, 상태 조회 과정에서 일시적인 문제가 생긴 것일 수 있기 때문입니다.
만약 poll worker가 정상적으로 결과를 받으면 Mock Worker 응답에 따라 `COMPLETED + NONE` 또는 `FAILED + NONE`으로 변경합니다.

처리 중 예외가 발생하거나 서버가 종료되어 `PROCESSING + POLL` lease가 남은 경우에는 `JobLeaseRecoveryWorker`가 만료된 lease를 해제하고
lease가 해제된 `PROCESSING + NONE` Job은 다시 poll worker의 결과 조회 대상이 됩니다.

서버가 재시작되더라도 결과 조회 대상은 DB 상태를 기준으로 복구할 수 있습니다.

- `PROCESSING + NONE`: Mock Worker 상태를 조회할 수 있음
- `PROCESSING + POLL`: lease 만료 전까지는 다른 worker가 처리하지 않음
- 만료된 `PROCESSING + POLL`: recovery worker가 `PROCESSING + NONE`으로 되돌린 뒤 다시 조회 대상이 됨

### 클라이언트 결과 조회 API 설계하기

클라이언트는 작업 생성 응답으로 받은 `jobId`를 사용해 작업 상태와 결과를 조회할 수 있습니다.

| 항목 | 값 |
|---|---|
| Method | `GET` |
| Path | `/api/v1/jobs/{jobId}` |
| Success Status | `200 OK` |

`PENDING` 또는 `PROCESSING` 상태에서는 아직 최종 결과가 없고, `COMPLETED`와 `FAILED` 상태에서는 `result`에 처리 결과가 담깁니다.

여러 작업을 한 번에 확인할 수 있도록 목록 조회 API도 제공합니다.

| 항목 | 값 |
|---|---|
| Method | `GET` |
| Path | `/api/v1/jobs?page=0&size=20` |
| Success Status | `200 OK` |


## 한계와 개선 여지

이번 과제에서 DB 기반으로 이미지 처리 요청을 다루고 lease만으로 요청 접수, 상태 추적, 재시도, 복구 흐름을 구성해서 흐름을 단순하게 유지하도록 구현했지만, 운영 규모가 커지면 아래와 같은 개선 지점이 남습니다.

### 스케줄러 기반 처리로 인한 지연

현재 worker는 일정 주기마다 DB를 조회해 처리할 Job을 가져옵니다.
이 방식은 구현이 단순하지만, Job이 생성된 직후 바로 처리되지는 않습니다.
그래서 Job이 생성되거나 Mock Worker 처리가 완료되더라도, 다음 worker 실행 시점까지 대기할 수 있습니다.

기본 설정 기준으로는 worker 실행 주기 때문에 다음 정도의 대기 시간이 생길 수 있습니다.

- 최초 Mock Worker 제출: 다음 submit worker 실행까지 최대 약 `5s`
- 최초 결과 조회: 다음 poll worker 실행까지 최대 약 `10s`

다만 Mock Worker가 아직 `PROCESSING` 상태를 반환한 경우에는 바로 다음 poll 주기에 다시 조회하지 않습니다.
현재 구현에서는 해당 Job이 `PROCESSING + POLL` 상태로 남고, poll lease가 만료된 뒤 recovery worker가 lease를 해제해야 다시 `PROCESSING + NONE` 상태가 됩니다.

즉, 같은 Job을 다시 조회하려면 다음 순서를 거쳐야 합니다.

1. poll lease 만료
2. recovery worker가 만료된 lease 해제
3. 다음 poll worker 실행 시 다시 조회

그래서 이후 재조회 지연은 poll interval뿐 아니라 poll lease-timeout과 recovery interval의 영향도 함께 받습니다.

실제 지연 시간은 여기에 더해 앞선 batch 처리 대기, worker concurrency 제한, Mock Worker 응답 시간의 영향을 함께 받습니다.

이를 줄이려면 worker interval을 짧게 조정할 수 있지만, 그만큼 DB 조회 빈도가 늘어납니다.
트래픽이 더 커지는 상황에서는 DB polling 방식 대신 message queue를 도입해 Job 생성 시점에 바로 worker로 전달하는 구조를 고려할 수 있습니다.

### Job 테이블이 Queue와 이력 저장소 역할을 함께 하는 구조

현재 `jobs` 테이블은 worker가 처리할 대상을 찾는 queue 역할과, 완료/실패된 작업 이력을 저장하는 역할을 함께 맡고 있습니다.
트래픽이 증가하고 완료된 Job이 계속 누적되면 후보 Job 조회, row lock, 상태 update, 목록 조회가 모두 같은 테이블에 몰릴 수 있습니다.

이를 완화하려면 현재 처리 중인 Job과 완료된 Job을 분리하는 방식이 필요합니다.
예를 들어 `jobs` 테이블은 `PENDING`, `PROCESSING` 같은 활성 작업 중심으로 유지하고, `COMPLETED`, `FAILED` 상태의 Job은 별도 이력 테이블로 이관할 수 있습니다.
보관 기간이 지난 데이터는 아카이빙하거나 삭제하는 정책도 함께 둘 수 있습니다.

### 재시도와 복구 정책의 단순함

현재 재시도는 lease가 만료되면 recovery worker가 `leaseType`을 `NONE`으로 되돌리고, 이후 submit/poll worker가 다시 처리하는 방식입니다.
서버 재시작이나 일시적인 외부 장애에는 대응할 수 있지만, 실패 횟수나 실패 원인에 따라 재시도 간격을 다르게 조정하지는 않습니다.

장애가 길어지는 상황에서는 같은 Job이 반복적으로 재시도되면서 Mock Worker에 부담을 줄 수 있습니다.
운영 상황까지 고려한다면 `retryCount`, `lastAttemptAt`, `nextRetryAt` 같은 필드를 두고, 실패 횟수에 따라 backoff를 적용하거나 최대 재시도 횟수를 지정하는 방식이 필요합니다.

### Mock Worker 제출 구간의 exactly-once 보장 한계

본 서버는 이미지 처리 요청의 헤더에 `Idempotency-Key`를 통해 클라이언트의 중복 요청은 막고 있습니다.
하지만 이 키는 본 서버 안에서 Job 생성을 제어하기 위한 값이므로, Mock Worker 제출 구간까지 exactly-once로 보장하지는 못합니다.

서버 내부에서 동일한 `imageUrl`이 이미 존재하면 중복 요청으로 간주하는 방법도 생각할 수 있었지만 같은 이미지를 여러 번 처리해야 한다면 정상 요청까지 막을 수 있었습니다.
그래서 이번 구현에서는 `imageUrl`을 중복 판단 기준으로 사용하지 않고, 클라이언트가 요청 단위를 명확히 식별할 수 있도록 `Idempotency-Key`를 별도로 받았습니다.

다만 이 `Idempotency-Key` 값은 본 서버의 Job 생성 중복을 막기 위한 값으로 Mock Worker의 `/process` API에는 전달되지 않아 서버가 Mock Worker에 제출 요청을 보낸 뒤 응답을 받기 전에 종료된 경우,
본 서버에서는 해당 요청이 Mock Worker에 도달했는지 알 수 없어 lease 만료 후 다시 요청하면 Mock Worker 쪽에 중복 worker job이 생성될 가능성이 남아 있다는 문제가 남아있습니다.
