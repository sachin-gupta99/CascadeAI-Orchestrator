package com.cascadeAI.Orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cascadeAI.Orchestrator.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueListenerService {

    private final RedisMessageListenerContainer listenerContainer;
    private final PipelineService pipelineService;
    private final ObjectMapper objectMapper;

    @Value("${pipeline.queue.transcript-done}")   private String transcriptDone;
    @Value("${pipeline.queue.requirements-done}") private String requirementsDone;
    @Value("${pipeline.queue.pr-created}")        private String prCreated;
    @Value("${pipeline.queue.complete}")          private String complete;
    @Value("${pipeline.queue.failed}")            private String failed;

    @PostConstruct
    public void registerListeners() {
        listen(transcriptDone,   TranscriptDoneMessage.class,   pipelineService::onTranscriptDone);
        listen(requirementsDone, RequirementsDoneMessage.class, pipelineService::onRequirementsDone);
        listen(prCreated,        PrCreatedMessage.class,        pipelineService::onPrCreated);
        listen(complete,         PipelineCompleteMessage.class, pipelineService::onPipelineComplete);
        listen(failed,           PipelineFailedMessage.class,   pipelineService::onPipelineFailed);
        log.info("Queue listeners registered on {} channels", 5);
    }

    private <T> void listen(String channel, Class<T> type, java.util.function.Consumer<T> handler) {
        listenerContainer.addMessageListener(
            (Message message, byte[] pattern) -> {
                try {
                    T payload = objectMapper.readValue(message.getBody(), type);
                    handler.accept(payload);
                } catch (Exception e) {
                    log.error("Failed to process message on channel {}: {}", channel, e.getMessage(), e);
                }
            },
            new PatternTopic(channel)
        );
    }
}
