package io.nats.client.impl;

public interface NatsJetStreamConstants {

    String JSAPI_DEFAULT_PREFIX = "$JS.API.";

    // JSAPI_ACCOUNT_INFO is for obtaining general information about JetStream.
    String JSAPI_ACCOUNT_INFO = "INFO";

    // JSAPI_CONSUMER_CREATE is used to create consumers.
    String JSAPI_CONSUMER_CREATE = "CONSUMER.CREATE.%s";

    // JSAPI_DURABLE_CREATE is used to create durable consumers.
    String JSAPI_DURABLE_CREATE = "CONSUMER.DURABLE.CREATE.%s.%s";

    // JSAPI_CONSUMER_INFO is used to create consumers.
    String JSAPI_CONSUMER_INFO = "CONSUMER.INFO.%s.%s";

    // JSAPI_CONSUMER_MSG_NEXT is the prefix for the request next message(s) for a consumer in worker/pull mode.
    String JSAPI_CONSUMER_MSG_NEXT = "CONSUMER.MSG.NEXT.%s.%s";

    // JSAPI_CONSUMER_DELETE is used to delete consumers.
    String JSAPI_CONSUMER_DELETE = "CONSUMER.DELETE.%s.%s";

    // JSAPI_CONSUMER_LIST is used to return all detailed consumer information
    String JSAPI_CONSUMER_LIST = "CONSUMER.LIST.%s";

    // JSAPI_STREAMS can lookup a stream by subject.
    String JSAPI_STREAMS = "STREAM.NAMES";

    // JSAPI_STREAM_CREATE is the endpoint to create new streams.
    String JSAPI_STREAM_CREATE = "STREAM.CREATE.%s";

    // JSAPI_STREAM_INFO is the endpoint to get information on a stream.
    String JSAPI_STREAM_INFO = "STREAM.INFO.%s";

    // JSAPI_STREAM_UPDATE is the endpoint to update existing streams.
    String JSAPI_STREAM_UPDATE = "STREAM.UPDATE.%s";

    // JSAPI_STREAM_DELETE is the endpoint to delete streams.
    String JSAPI_STREAM_DELETE = "STREAM.DELETE.%s";

    // JSAPI_STREAM_PURGE is the endpoint to purge streams.
    String JSAPI_STREAM_PURGE = "STREAM.PURGE.%s";

    // JSAPI_STREAM_LIST is the endpoint that will return all detailed stream information
    String JSAPI_STREAM_LIST = "STREAM.LIST";

    // JSAPI_MSG_DELETE is the endpoint to remove a message.
    String JSAPI_MSG_DELETE = "STREAM.MSG.DELETE.%s";
}
