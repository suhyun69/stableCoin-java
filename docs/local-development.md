# 로컬 인프라

```sh
cp .env.example .env
docker compose up -d --wait
docker compose ps
```

호스트에서 PostgreSQL `localhost:5433`, Anvil `http://localhost:8545`,
Kafka `localhost:9092`에 연결한다. Kafka 컨테이너 내부 주소는 `kafka:19092`이다.
Compose의 `.env`는 Spring Boot 프로세스에 자동 전달되지 않는다.

Anvil chain ID는 31337이며 2초마다 블록을 생성한다. Anvil 기본 테스트 지갑을 사용한다.
로컬 단일 Kafka는 KRaft 모드이며 자동 토픽 생성을 끈다. 토픽은 애플리케이션에서 선언한다.
이 구성은 로컬 개발용으로 호스트 포트를 loopback에만 바인딩한다.

PostgreSQL, Kafka, Anvil 상태는 각각 named volume에 유지된다. Anvil은 주기적으로
상태를 저장하고 정상 종료 시 마지막 상태를 저장한다. 강제 종료 직전 데이터는 유실될 수 있다.

```sh
docker compose stop
docker compose up -d --wait
```

`docker compose down`은 볼륨을 유지한다. 모든 개발 데이터를 초기화할 때만
`docker compose down -v`를 사용한다. DB만 남기고 체인만 초기화하지 않는다.
