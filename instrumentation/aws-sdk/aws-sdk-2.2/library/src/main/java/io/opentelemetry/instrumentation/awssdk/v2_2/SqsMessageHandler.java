/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.messagehandler.MessageHandler;
import java.util.Collection;
import javax.annotation.Nullable;

public abstract class SqsMessageHandler<MessageType> extends MessageHandler<MessageType> {
  private final OpenTelemetry openTelemetry;
  private final String destination;
  private final SpanKindExtractor<Collection<MessageType>> spanKindExtractor;

  public SqsMessageHandler(OpenTelemetry openTelemetry, String destination, SpanKindExtractor<Collection<MessageType>> spanKindExtractor) {
    this.openTelemetry = openTelemetry;
    this.destination = destination;
    this.spanKindExtractor = spanKindExtractor;
  }

  @Override
  protected Instrumenter<Collection<MessageType>, Void> getMessageInstrumenter() {
    return Instrumenter.<Collection<MessageType>, Void>builder(
            openTelemetry, "io.opentelemetry.aws-sdk-2.2", getSpanNameExtractor())
        .addAttributesExtractor(getAttributesExtractor())
        .addSpanLinksExtractor(getSpanLinksExtractor())
        .buildInstrumenter(spanKindExtractor);
  }

  protected SpanNameExtractor<Collection<MessageType>> getSpanNameExtractor() {
    return MessagingSpanNameExtractor.create(
        getMessageingAttributesGetter(), MessageOperation.PROCESS);
  }

  protected MessagingAttributesGetter<Collection<MessageType>, Void>
      getMessageingAttributesGetter() {
    String destination = this.destination;

    return new MessagingAttributesGetter<Collection<MessageType>, Void>() {
      @Nullable
      @Override
      public String getSystem(Collection<MessageType> v) {
        return "AmazonSQS";
      }

      @Nullable
      @Override
      @SuppressWarnings({"deprecation"}) // Inheriting from interface
      public String getDestinationKind(Collection<MessageType> v) {
        return null;
      }

      @Nullable
      @Override
      public String getDestination(Collection<MessageType> v) {
        return destination;
      }

      @Override
      public boolean isTemporaryDestination(Collection<MessageType> v) {
        return false;
      }

      @Nullable
      @Override
      public String getConversationId(Collection<MessageType> v) {
        return null;
      }

      @Nullable
      @Override
      public Long getMessagePayloadSize(Collection<MessageType> v) {
        long total = 0;

        for (MessageType message : v) {
          total += SqsMessageDelegates.get(message).getPayloadSize(message);
        }

        return total;
      }

      @Nullable
      @Override
      public Long getMessagePayloadCompressedSize(Collection<MessageType> v) {
        return null;
      }

      @Nullable
      @Override
      public String getMessageId(Collection<MessageType> request, Void v) {
        return null;
      }
    };
  }

  protected AttributesExtractor<Collection<MessageType>, Void> getAttributesExtractor() {
    return MessagingAttributesExtractor.create(
        getMessageingAttributesGetter(), MessageOperation.PROCESS);
  }

  @SuppressWarnings("unchecked")
  protected SpanLinksExtractor<Collection<MessageType>> getSpanLinksExtractor() {
    return (spanLinks, parentContext, request) -> {
      for (MessageType message : request) {
        SpanContext messageSpanCtx = SqsMessageDelegates.get(message).getUpstreamContext(openTelemetry, message);

        if (messageSpanCtx.isValid()) {
          spanLinks.addLink(messageSpanCtx);
        }
      }
    };
  }
}
