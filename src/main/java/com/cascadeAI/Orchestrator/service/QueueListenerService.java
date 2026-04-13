package com.cascadeAI.Orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cascadeAI.Orchestrator.config.RedisStreams;
import com.cascadeAI.Orchestrator.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueListenerService {

    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamContainer;
    private final StringRedisTemplate redis;
    private final PipelineService pipelineService;
    private final ObjectMapper objectMapper;
    private final RedisStreams redisStreams;

    private static final String GROUP = "orchestrator";
    private static final String CONSUMER_NAME = "orchestrator-1";

    @PostConstruct
    public void registerListeners() {
        listenStream(redisStreams.getPipelineStart(), PipelineStartMessage.class, pipelineService::onPipelineStart);
        listenStream(redisStreams.getTranscriptDone(), TranscriptDoneMessage.class, pipelineService::onTranscriptDone);
        listenStream(redisStreams.getRequirementsDone(), RequirementsDoneMessage.class,
                pipelineService::onRequirementsDone);
        listenStream(redisStreams.getPrCreated(), PrCreatedMessage.class,
                pipelineService::onPrCreated);
        listenStream(redisStreams.getComplete(), PipelineCompleteMessage.class,
                pipelineService::onPipelineComplete);
        listenStream(redisStreams.getFailed(), PipelineFailedMessage.class,
                pipelineService::onPipelineFailed);
        log.info("Stream listeners registered on {} streams", 6);
    }

    private void ensureGroupExists(String streamKey) {
        try {
            redis.opsForStream().createGroup(streamKey, ReadOffset.from("0"), GROUP);
            log.info("Created consumer group '{}' on stream '{}'", GROUP, streamKey);
        } catch (RedisSystemException e) {
            // BUSYGROUP: group already exists, or stream needs to be created first
            String msg = e.getCause() != null ? e.getCause().getMessage() : "";
            if (msg.contains("BUSYGROUP")) {
                log.debug("Consumer group '{}' already exists on stream '{}'", GROUP, streamKey);
            } else {
                // Stream doesn't exist — create with MKSTREAM via low-level command
                try {
                    redis.execute((RedisCallback<Void>) connection -> {
                        connection.streamCommands().xGroupCreate(
                                streamKey.getBytes(), GROUP, ReadOffset.from("0"), true);
                        return null;
                    });
                    log.info("Created stream '{}' with consumer group '{}'", streamKey, GROUP);
                } catch (RedisSystemException e2) {
                    log.debug("Stream/group '{}' on '{}' already set up", GROUP, streamKey);
                }
            }
        }
    }

    private <T> void listenStream(String streamKey, Class<T> type, java.util.function.Consumer<T> handler) {
        ensureGroupExists(streamKey);

        streamContainer.receive(
                Consumer.from(GROUP, CONSUMER_NAME),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                message -> {
                    try {
                        String json = message.getValue().get("payload");
                        T payload = objectMapper.readValue(json, type);
                        handler.accept(payload);
                    } catch (Exception e) {
                        log.error("Failed to process stream message on {}: {}", streamKey, e.getMessage(), e);
                    } finally {
                        redis.opsForStream().acknowledge(streamKey, GROUP, message.getId());
                    }
                });
    }
}
