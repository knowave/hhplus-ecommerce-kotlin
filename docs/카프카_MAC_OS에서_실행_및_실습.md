# 🛠️ Apache Kafka 로컬 환경 실행 및 CLI 실습 가이드

이 글에서는 Apache Kafka를 로컬 환경에 띄우고, CLI를 사용하여 토픽 생성, 메시지 발생 및 구독을 직접 실습하는 방법을 적어보려고 한다.

(이 가이드는 **Docker Descktop**이 설치된 MacOS 환경을 기준으로 작성되어있다.)

## 1. 환경 설정: Docker-Compose Kafka 클러스터 실행

카프카는 안정적인 실행을 위해 메터데이터를 관리하는 Zookeper와 함께 구동된다. Docker를 사용하여 이 두 서버를 동시에 실행한다.

### 1.1. `docker-compose.yml` 파일 작성

작업 폴더를 생성한 후, 아래 내용을 `docker-compose.yml` 파일을 작성한다. 이 설정은 카프카 브로커를 `localhost:9092` 포트로 외부에 노출한다.

```yaml
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092" # 호스트 포트: 컨테이너 포트
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
```

### 1.2. 컨테이너 실행
터미널에서 `docker-compose.yml` 파일이 있는 디렉토리로 이동하여 실행한다.
```bash
# 백그라운드에서 컨테이너 실행
docker-compose up -d

# 실행 상태 확인
docker ps
```

> 💡 확인: `kafka`와 `zookeeper` 두 컨네이터 상태가 `Up`으로 표시되면 성공.

---

## 2. CLI를 이용하여 실시간 메시지 흐름 실습

컨테이너가 정상적으로 실행되었다면, 이제 카프카의 기본 명령어 도구를 사용하여 메시지 흐름을 알아보도록하자. 모든 명령은 카프카 컨테이너 내부에서 실행된다.

### 2.1. 컨테이너 접속 및 토픽 생성

메시지를 주고받기 위해 **study-topic**이라는 이름의 토픽을 먼저 생성한다.
```bash
# 카프카 컨테이너 내부 쉘 접속
docker exec -it kafka /bin/bash

# 토픽 생성 명령어
kafka-topics --create --topic study-topic --bootstrap-server localhost:9092 --replication-factor 1
```

### 2.2. Consumper (구독자) 실행

새로운 터미널 탭을 열고, 컨테이너에 다시 접속한 뒤 Consumer를 실행한다. Consumer는 `--from-beginnig` 옵션으로 기존에 저장된 메시지부터 읽기 시작한다.
```bash
# 새 터미널 탭에서 카프카 컨테이너 접속
docker exec -it kafka /bin/bash

# Consumer 실행 (구독 시작)
kafka-console-consumer --topic study-topic --from-beginning --bootstrap-server localhost:9092
# 이제 이 창은 메시지를 기다리는 상태가 된다.
```

### 2.3. Producer (발행자) 실행

또 다른 새로운 터미널 탭을 열고, Producer를 실행하여 메시지를 발행한다.
```bash
# 세 번째 터미널 탭에서 카프카 컨테이너 접속
docker exec -it kafka /bin/bash

# Producer 실행 (메시지 발행 시작)
kafka-console-producer --topic study-topic --bootstrap-server localhost:9092
```

📌 **실습 및 확인**
1. Producer 터미널에 메시지를 입력하고 **Enter**.
2. 메시지를 입력하는 즉시 Consumer 터미널에 메시지가 출력되는 것을 확인.
3. **Consumper 터미널을 종료하지 않은 채** Producer로 메시지를 계속 보내보면 이것이 **실시간 스트리밍**의 핵심이라고 알 수 있다.

### 2.4. 영속성 (Durability) 확인

Consumper를 종료한 후, Producer로 메시지를 5개 더 보내본다. 그리고 Consumper를 다시 실행한다.
```bash
# Consumer를 다시 실행 (새로운 Consumer)
docker exec -it kafka /bin/bash
kafka-console-consumer --topic study-topic --from-beginning --bootstrap-server localhost:9092
```

> 💡 확인: Consumer가 꺼져있을 때 전송했던 5개의 메시지를 포함하여 모든 메시지가 다시 출력된다.
> 이는 카프카가 메시지를 **디스크에 안전하게 저장(영속성)** 하기 때문이다.

---

## 3. 실습 환경 정리

실습을 마쳤다면, 불필요한 리소스 사용을 막기 위해 컨테이너를 종료한다.
```bash
# docker-compose.yml이 있는 폴더에서 실행
docker-compose down
```
이제 카프카의 기본적인 메시지 발생/구독 흐름을 이해하게 됐다. 실제 어플리케이션에 카프카를 적용하여 사용해보면서 심화적인 과정을 배우면 될 거 같다.