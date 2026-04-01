# CascadeAI Orchestrator

Spring Boot service that orchestrates an AI-powered pipeline for turning meeting transcripts into code changes. It coordinates Python AI agents (via Redis pub/sub), persists pipeline state in PostgreSQL, and exposes a REST API for a frontend dashboard and human-in-the-loop approval gates.

## Architecture

```
                         +-----------------------+
                         |   React Frontend      |
                         |   (localhost:5173)     |
                         +-----------+-----------+
                                     |
                                REST API (HTTP)
                                     |
                         +-----------+-----------+
                         |   Orchestrator        |
                         |   (Spring Boot)       |
                         +-----------+-----------+
                            |                 |
                     PostgreSQL          Redis Pub/Sub
                     (state)             (messaging)
                                              |
                         +--------------------+--------------------+
                         |                    |                    |
                   Transcript Agent   Requirements Agent    Coding Agent
                      (Python)            (Python)           (Python)
```

## Pipeline Flow

Each pipeline run progresses through these stages:

```
PENDING -> TRANSCRIBING -> ANALYZING -> AWAITING_APPROVAL -> CODING -> SUPERVISING -> COMPLETE
                                              |                                          |
                                         (rejected) --------------------------------> FAILED
```

1. **Trigger** -- A client POSTs a transcript file reference to `/api/v1/runs`. The orchestrator creates a `PipelineRun` record and publishes a `PipelineStartMessage` to Redis channel `pipeline:start`.
2. **Transcription** -- A Python transcript agent processes the file and publishes back on `pipeline:transcript:done`. The orchestrator moves the run to `ANALYZING`.
3. **Requirements extraction** -- A Python requirements agent analyzes the transcript, extracts structured requirements (title, description, priority, affected files, acceptance criteria), and publishes on `pipeline:requirements:done`. The orchestrator persists a `Requirement` entity and moves the run to `AWAITING_APPROVAL`.
4. **Human approval gate** -- A reviewer (typically a senior engineer) inspects the extracted requirements via the frontend and either approves or rejects:
   - **Approve** -- The orchestrator publishes a `CodingApprovedMessage` to `pipeline:coding:approved`, unblocking the coding agent. Run moves to `CODING`.
   - **Reject** -- The run moves to `FAILED` with the reviewer's rejection notes.
5. **Code generation** -- The Python coding agent creates a PR and publishes on `pipeline:pr:created`. Run moves to `SUPERVISING`.
6. **Completion** -- Once the pipeline finishes end-to-end, a message on `pipeline:complete` moves the run to `COMPLETE`.
7. **Failure** -- Any agent can publish to `pipeline:failed` at any point, which records the failing agent name and error message.

## Tech Stack

| Component            | Technology                  |
|----------------------|-----------------------------|
| Language             | Java 21                     |
| Framework            | Spring Boot 4.0.5           |
| Database             | PostgreSQL                  |
| Messaging            | Redis Pub/Sub               |
| ORM                  | Spring Data JPA / Hibernate |
| Serialization        | Jackson                     |
| Boilerplate          | Lombok                      |
| Build                | Maven (with Maven Wrapper)  |

## Project Structure

```
src/main/java/com/cascadeAI/Orchestrator/
├── OrchestratorApplication.java        # Entry point (@SpringBootApplication, @EnableAsync)
├── config/
│   ├── RedisConfig.java                # RedisTemplate & listener container beans
│   └── WebConfig.java                  # CORS configuration (allows localhost:5173)
├── controller/
│   └── PipelineController.java         # REST API endpoints
├── dto/
│   ├── TranscriptDoneMessage.java      # Inbound: transcript agent done
│   ├── RequirementsDoneMessage.java    # Inbound: requirements extracted
│   ├── PrCreatedMessage.java           # Inbound: PR created by coding agent
│   ├── PipelineCompleteMessage.java    # Inbound: pipeline finished
│   ├── PipelineFailedMessage.java      # Inbound: agent failure
│   ├── PipelineStartMessage.java       # Outbound: trigger transcript agent
│   └── CodingApprovedMessage.java      # Outbound: unblock coding agent
├── model/
│   ├── PipelineRun.java                # JPA entity -- pipeline run lifecycle
│   ├── Requirement.java                # JPA entity -- extracted requirement
│   └── Approval.java                   # JPA entity -- approval decision
├── repository/
│   ├── PipelineRunRepository.java      # Spring Data repo for PipelineRun
│   └── RequirementRepository.java      # Spring Data repo for Requirement
└── service/
    ├── PipelineService.java            # Core business logic & state machine
    └── QueueListenerService.java       # Registers Redis channel listeners
```

## REST API

All endpoints are prefixed with `/api/v1`.

| Method | Path                          | Description                        | Request Body                                     |
|--------|-------------------------------|------------------------------------|--------------------------------------------------|
| GET    | `/runs`                       | List all pipeline runs (newest first) | --                                            |
| GET    | `/runs/{id}`                  | Get a single run by UUID           | --                                               |
| POST   | `/runs`                       | Start a new pipeline run           | `{ "transcriptFileId": "...", "transcriptFileName": "..." }` |
| GET    | `/runs/{id}/requirements`     | Get requirements for a run         | --                                               |
| POST   | `/runs/{id}/approve`          | Approve requirements               | `{ "reviewerEmail": "...", "notes": "..." }`     |
| POST   | `/runs/{id}/reject`           | Reject requirements                | `{ "reviewerEmail": "...", "notes": "..." }`     |
| GET    | `/health`                     | Health check                       | --                                               |

## Redis Channels

Communication between the orchestrator and Python agents uses Redis Pub/Sub.

**Outbound (Orchestrator -> Python agents):**

| Channel                      | Message DTO              | Trigger                        |
|------------------------------|--------------------------|--------------------------------|
| `pipeline:start`             | `PipelineStartMessage`   | New run created via REST API   |
| `pipeline:coding:approved`   | `CodingApprovedMessage`  | Reviewer approves requirements |

**Inbound (Python agents -> Orchestrator):**

| Channel                       | Message DTO                | Effect                                     |
|-------------------------------|----------------------------|--------------------------------------------|
| `pipeline:transcript:done`    | `TranscriptDoneMessage`    | Run status -> `ANALYZING`                  |
| `pipeline:requirements:done`  | `RequirementsDoneMessage`  | Saves requirements, status -> `AWAITING_APPROVAL` |
| `pipeline:pr:created`         | `PrCreatedMessage`         | Run status -> `SUPERVISING`                |
| `pipeline:complete`           | `PipelineCompleteMessage`  | Run status -> `COMPLETE`                   |
| `pipeline:failed`             | `PipelineFailedMessage`    | Run status -> `FAILED`, records error      |

## Database Schema

The application uses Hibernate with `ddl-auto: validate`, so the schema must exist before the app starts. Three tables are required:

### `pipeline_runs`

| Column                | Type         | Notes                          |
|-----------------------|--------------|--------------------------------|
| `id`                  | `UUID`       | PK, auto-generated             |
| `status`              | `VARCHAR`    | Enum: PENDING, TRANSCRIBING, ANALYZING, AWAITING_APPROVAL, CODING, SUPERVISING, COMPLETE, FAILED |
| `transcript_file_id`  | `VARCHAR`    |                                |
| `transcript_file_name`| `VARCHAR`    |                                |
| `meeting_date`        | `TIMESTAMP`  |                                |
| `error_message`       | `TEXT`        |                                |
| `created_at`          | `TIMESTAMP`  | Auto-set on insert             |
| `updated_at`          | `TIMESTAMP`  | Auto-set on update             |

### `requirements`

| Column               | Type         | Notes                          |
|----------------------|--------------|--------------------------------|
| `id`                 | `UUID`       | PK, auto-generated             |
| `run_id`             | `UUID`       | FK -> `pipeline_runs.id`       |
| `title`              | `VARCHAR`    | NOT NULL                       |
| `description`        | `TEXT`        |                                |
| `priority`           | `VARCHAR`    | Enum: LOW, MEDIUM, HIGH        |
| `affected_files`     | `TEXT[]`      | PostgreSQL array               |
| `acceptance_criteria`| `TEXT[]`      | PostgreSQL array               |
| `raw_spec`           | `JSONB`       | Full JSON spec from agent      |
| `created_at`         | `TIMESTAMP`  | Auto-set on insert             |

### `approvals`

| Column               | Type         | Notes                          |
|----------------------|--------------|--------------------------------|
| `id`                 | `UUID`       | PK, auto-generated             |
| `run_id`             | `UUID`       | FK -> `pipeline_runs.id`       |
| `requirement_id`     | `UUID`       | FK -> `requirements.id`        |
| `status`             | `VARCHAR`    | Enum: PENDING, APPROVED, REJECTED |
| `reviewer_email`     | `VARCHAR`    |                                |
| `notes`              | `TEXT`        |                                |
| `decided_at`         | `TIMESTAMP`  |                                |
| `created_at`         | `TIMESTAMP`  | Auto-set on insert             |

## Prerequisites

- **Java 21**
- **PostgreSQL** -- running on `localhost:5432` with a database named `pipeline`
- **Redis** -- running on `localhost:6379`

## Getting Started

### 1. Set up PostgreSQL

Create the database and user:

```sql
CREATE USER pipeline WITH PASSWORD 'pipeline_secret';
CREATE DATABASE pipeline OWNER pipeline;
```

Create the tables (Hibernate validates but does not create them):

```sql
CREATE TABLE pipeline_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    transcript_file_id VARCHAR(255),
    transcript_file_name VARCHAR(255),
    meeting_date TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE requirements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES pipeline_runs(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    affected_files TEXT[],
    acceptance_criteria TEXT[],
    raw_spec JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE approvals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES pipeline_runs(id),
    requirement_id UUID REFERENCES requirements(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewer_email VARCHAR(255),
    notes TEXT,
    decided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

### 2. Start Redis

```bash
redis-server
```

### 3. Run the application

Using the Maven Wrapper (no Maven installation required):

```bash
# Unix/macOS
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

The server starts on **http://localhost:8080**.

### 4. Verify

```bash
curl http://localhost:8080/api/v1/health
# => ok
```

## Configuration

All configuration lives in `src/main/resources/application.yaml`. Key properties:

| Property                          | Default                    | Description                          |
|-----------------------------------|----------------------------|--------------------------------------|
| `spring.datasource.url`          | `jdbc:postgresql://localhost:5432/pipeline` | PostgreSQL connection URL |
| `spring.datasource.username`     | `pipeline`                 | Database user                        |
| `spring.datasource.password`     | `pipeline_secret`          | Database password                    |
| `spring.data.redis.host`         | `localhost`                | Redis host                           |
| `spring.data.redis.port`         | `6379`                     | Redis port                           |
| `server.port`                    | `8080`                     | HTTP server port                     |
| `pipeline.senior-engineer-email` | `senior.engineer@yourcompany.com` | Notification recipient       |
| `pipeline.queue.*`               | `pipeline:*`               | Redis channel names (see above)      |

Override any property via environment variables (e.g., `SPRING_DATASOURCE_URL`) or a `application-local.yaml` profile.
