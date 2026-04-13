# CascadeAI Orchestrator

Spring Boot service that orchestrates an AI-powered pipeline for turning meeting transcripts into code changes. It coordinates Python AI agents (via Redis Streams), persists pipeline state in PostgreSQL, and exposes a REST API for a frontend dashboard and human-in-the-loop approval gates.

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
                     PostgreSQL          Redis Streams
                     (state)             (messaging)
                                              |
                         +--------+-----------+-----------+--------+
                         |        |                       |        |
                   Transcript  Requirements          Coding    Mailing
                     Agent       Agent                Agent     Agent
                    (Python)    (Python)             (Python)  (Python)
```

## Pipeline Flow

Each pipeline run progresses through these stages:

```
PENDING -> TRANSCRIBING -> ANALYZING -> AWAITING_APPROVAL -> CODING -> PULL_REQUEST_CREATED -> MAILING -> COMPLETE
                                              |                              |
                                         (rejected) ----+--- (PR rejected) -+---> FAILED
```

1. **Upload** -- A client uploads a transcript file via `POST /api/v1/upload`. The file is stored in Google Drive and the returned `fileId`/`fileName` are used in subsequent steps.
2. **Trigger** -- The frontend publishes a `PipelineStartMessage` to Redis stream `pipeline:start`. The orchestrator creates a `PipelineRun` record. The client then calls `POST /api/v1/start` to begin processing, which publishes a `TranscriptStartMessage` to `pipeline:transcript:start`.
3. **Transcription** -- A Python transcript agent processes the file and publishes back on `pipeline:transcript:done`. The orchestrator moves the run to `ANALYZING`.
4. **Requirements extraction** -- A Python requirements agent analyzes the transcript, extracts structured requirements (title, description, priority, acceptance criteria), and publishes on `pipeline:requirements:done`. The orchestrator persists a `Requirement` entity and moves the run to `AWAITING_APPROVAL`.
5. **Human approval gate** -- A reviewer inspects the extracted requirements via the frontend (with the ability to edit them) and either approves or rejects:
   - **Approve** -- The orchestrator publishes a `CodingApprovedMessage` to `pipeline:coding:approved`, unblocking the coding agent. Run moves to `CODING`.
   - **Reject** -- The run moves to `FAILED` with the reviewer's rejection notes.
6. **Code generation** -- The Python coding agent creates a PR and publishes on `pipeline:pr:created`. Run moves to `PULL_REQUEST_CREATED` and stores the PR URL.
7. **PR review gate** -- A reviewer inspects the pull request via the provided link and either verifies or rejects:
   - **Verify** -- The orchestrator publishes a `PrApprovedMessage` to `pipeline:pr:approved`, unblocking the mailing agent. Run moves to `MAILING`.
   - **Reject** -- The run moves to `FAILED` with the reviewer's rejection notes.
8. **Mailing** -- The Python mailing agent composes and sends an email to the senior engineer about the requirement and PR, then publishes on `pipeline:complete`. Run moves to `COMPLETE`.
9. **Failure** -- Any agent can publish to `pipeline:failed` at any point, which records the failing agent name and error message.

## Tech Stack

| Component            | Technology                  |
|----------------------|-----------------------------|
| Language             | Java 21                     |
| Framework            | Spring Boot 4.0.5           |
| Database             | PostgreSQL                  |
| Messaging            | Redis Streams (consumer groups) |
| File Storage         | Google Drive (OAuth2)       |
| Secrets Management   | AWS SSM Parameter Store     |
| ORM                  | Spring Data JPA / Hibernate |
| Serialization        | Jackson                     |
| Boilerplate          | Lombok                      |
| Build                | Maven (with Maven Wrapper)  |

## Project Structure

```
src/main/java/com/cascadeAI/Orchestrator/
├── OrchestratorApplication.java        # Entry point (@SpringBootApplication, @EnableAsync)
├── config/
│   ├── AwsIAMConfig.java              # AWS IAM credentials from SSM
│   ├── AwsSSMConfig.java              # AWS SSM client bean
│   ├── GDriveProperties.java          # Google Drive config properties
│   ├── PostgreDBConfig.java           # DataSource bean (credentials via SSM)
│   ├── PostgreDBProperties.java       # DB config properties
│   ├── RedisConfig.java               # RedisTemplate & stream listener container
│   ├── RedisStreams.java              # Stream name bindings from config
│   └── WebConfig.java                 # CORS configuration
├── controller/
│   └── PipelineController.java         # REST API endpoints
├── dto/
│   ├── PipelineStartMessage.java       # Inbound: new run from frontend
│   ├── TranscriptStartMessage.java     # Outbound: trigger transcript agent
│   ├── TranscriptDoneMessage.java      # Inbound: transcript agent done
│   ├── RequirementsDoneMessage.java    # Inbound: requirements extracted
│   ├── CodingApprovedMessage.java      # Outbound: unblock coding agent
│   ├── PrCreatedMessage.java           # Inbound: PR created by coding agent
│   ├── PrApprovedMessage.java          # Outbound: unblock mailing agent
│   ├── PipelineCompleteMessage.java    # Inbound: mailing agent done
│   └── PipelineFailedMessage.java      # Inbound: agent failure
├── model/
│   ├── PipelineRun.java                # JPA entity -- pipeline run lifecycle
│   ├── Transcript.java                 # JPA entity -- transcript file reference
│   ├── Requirement.java                # JPA entity -- extracted requirement
│   └── Approval.java                   # JPA entity -- approval decision
├── repository/
│   ├── PipelineRunRepository.java      # Spring Data repo for PipelineRun
│   ├── RequirementRepository.java      # Spring Data repo for Requirement
│   └── ApprovalRepository.java         # Spring Data repo for Approval
└── service/
    ├── PipelineService.java            # Core business logic & state machine
    ├── QueueListenerService.java       # Redis Streams consumer group listeners
    ├── GoogleDriveService.java         # Google Drive file upload (OAuth2)
    └── ParameterStoreService.java      # AWS SSM parameter retrieval
```

## REST API

All endpoints are prefixed with `/api/v1`.

| Method | Path                          | Description                        | Request Body                                     |
|--------|-------------------------------|------------------------------------|--------------------------------------------------|
| POST   | `/upload`                     | Upload file to Google Drive        | `multipart/form-data` with `file` field          |
| GET    | `/runs`                       | List all pipeline runs (newest first) | --                                            |
| GET    | `/runs/{id}`                  | Get a single run by UUID           | --                                               |
| POST   | `/start`                      | Start transcript processing        | `{ "runId": "..." }`                             |
| GET    | `/runs/{id}/requirements`     | Get requirements for a run         | --                                               |
| PUT    | `/runs/{runId}/requirements/{reqId}` | Edit a requirement           | `{ "title": "...", "description": "...", "priority": "...", "acceptanceCriteria": [...] }` |
| POST   | `/runs/{id}/requirements/approve` | Approve requirements           | `{ "reviewerEmail": "...", "notes": "...", "repoFullName": "..." }` |
| POST   | `/runs/{id}/requirements/reject` | Reject requirements               | `{ "reviewerEmail": "...", "notes": "..." }`     |
| POST   | `/runs/{id}/pr/verify`        | Verify PR, trigger mailing agent   | `{ "reviewerEmail": "...", "notes": "..." }`     |
| POST   | `/runs/{id}/pr/reject`        | Reject pull request                | `{ "reviewerEmail": "...", "notes": "..." }`     |
| GET    | `/health`                     | Health check                       | --                                               |

## Redis Streams

Communication between the orchestrator and Python agents uses Redis Streams with consumer groups (group: `orchestrator`, consumer: `orchestrator-1`). Messages are published as `{"payload": "<json>"}` map entries.

**Outbound (Orchestrator -> Python agents):**

| Stream                         | Message DTO              | Trigger                        |
|--------------------------------|--------------------------|--------------------------------|
| `pipeline:transcript:start`    | `TranscriptStartMessage` | Pipeline run started via REST  |
| `pipeline:coding:approved`     | `CodingApprovedMessage`  | Reviewer approves requirements |
| `pipeline:pr:approved`         | `PrApprovedMessage`      | Reviewer verifies pull request |

**Inbound (Python agents -> Orchestrator):**

| Stream                        | Message DTO                | Effect                                     |
|-------------------------------|----------------------------|--------------------------------------------|
| `pipeline:start`              | `PipelineStartMessage`     | Creates `PipelineRun` record               |
| `pipeline:transcript:done`    | `TranscriptDoneMessage`    | Run status -> `ANALYZING`                  |
| `pipeline:requirements:done`  | `RequirementsDoneMessage`  | Saves requirements, status -> `AWAITING_APPROVAL` |
| `pipeline:pr:created`         | `PrCreatedMessage`         | Run status -> `PULL_REQUEST_CREATED`, stores PR URL |
| `pipeline:complete`           | `PipelineCompleteMessage`  | Run status -> `COMPLETE`                   |
| `pipeline:failed`             | `PipelineFailedMessage`    | Run status -> `FAILED`, records error      |

## Database Schema

The application uses Hibernate with `ddl-auto: update`, so tables are auto-created/updated on startup. The schema consists of these tables:

### `pipeline_runs`

| Column                | Type         | Notes                          |
|-----------------------|--------------|--------------------------------|
| `id`                  | `UUID`       | PK, auto-generated             |
| `status`              | `VARCHAR`    | Enum: PENDING, TRANSCRIBING, ANALYZING, AWAITING_APPROVAL, CODING, PULL_REQUEST_CREATED, MAILING, COMPLETE, FAILED |
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
- **PostgreSQL** -- connection details configured via AWS SSM Parameter Store
- **Redis** -- running on `localhost:6379`
- **AWS credentials** -- with access to SSM parameters under `/CascadeAI/`
- **Google OAuth credentials** -- `gdriveCredentials.json` in `src/main/resources/`

## Getting Started

### 1. Configure AWS SSM Parameters

Ensure the following SSM parameters exist in your AWS account (`ap-south-1`):

| Parameter Path                                  | Description             |
|-------------------------------------------------|-------------------------|
| `/CascadeAI/access-key`                         | AWS IAM access key      |
| `/CascadeAI/secret-key`                         | AWS IAM secret key      |
| `/CascadeAI/Orchestrator/postgres-db/url`       | JDBC connection URL     |
| `/CascadeAI/Orchestrator/postgres-db/username`  | Database username       |
| `/CascadeAI/Orchestrator/postgres-db/password`  | Database password       |
| `/CascadeAI/gdrive/folderId`                    | Google Drive folder ID  |

### 2. Set up Google Drive credentials

Place your OAuth2 credentials file at `src/main/resources/gdriveCredentials.json`. On first startup, the app will open a browser for OAuth consent (port 8888).

### 3. Start Redis

```bash
redis-server
```

### 4. Run the application

Using the Maven Wrapper (no Maven installation required):

```bash
# Unix/macOS
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

The server starts on **http://localhost:8080**. Hibernate will auto-create/update tables (`ddl-auto: update`).

### 5. Verify

```bash
curl http://localhost:8080/api/v1/health
# => ok
```

## Configuration

All configuration lives in `src/main/resources/application.yaml`. Sensitive values (DB credentials, API keys, Drive folder ID) are stored in **AWS SSM Parameter Store** and fetched at runtime.

| Property                          | Default / SSM Path                      | Description                          |
|-----------------------------------|-----------------------------------------|--------------------------------------|
| `db.url`                         | `/CascadeAI/Orchestrator/postgres-db/url` | PostgreSQL connection URL (SSM)    |
| `db.username`                    | `/CascadeAI/Orchestrator/postgres-db/username` | Database user (SSM)           |
| `db.password`                    | `/CascadeAI/Orchestrator/postgres-db/password` | Database password (SSM)       |
| `aws.ssm.region`                 | `ap-south-1`                            | AWS region for SSM client            |
| `aws.iam-user.access-key`       | `/CascadeAI/access-key`                 | AWS access key (SSM path)            |
| `aws.iam-user.secret-key`       | `/CascadeAI/secret-key`                 | AWS secret key (SSM path)            |
| `spring.data.redis.host`         | `localhost`                             | Redis host                           |
| `spring.data.redis.port`         | `6379`                                  | Redis port                           |
| `server.port`                    | `8080`                                  | HTTP server port                     |
| `pipeline.senior-engineer-email` | --                                      | Notification recipient email         |
| `pipeline.queue.*`               | `pipeline:*`                            | Redis stream names (see above)       |
| `google.drive.credentials-path` | `classpath:gdriveCredentials.json`       | Google OAuth credentials file        |
| `google.drive.folder-id`        | `/CascadeAI/gdrive/folderId`            | Drive upload folder ID (SSM path)    |

Override any property via environment variables (e.g., `SPRING_DATA_REDIS_HOST`) or a `application-local.yaml` profile.
