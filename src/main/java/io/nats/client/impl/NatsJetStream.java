package io.nats.client.impl;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.nats.client.ConsumerConfiguration;
import io.nats.client.ConsumerInfo;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamOptions;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.Options;
import io.nats.client.PublishAck;
import io.nats.client.PublishOptions;
import io.nats.client.StreamConfiguration;
import io.nats.client.StreamInfo;
import io.nats.client.SubscribeOptions;
import io.nats.client.impl.JsonUtils.FieldType;

public class NatsJetStream implements JetStream {

    private static final String jSDefaultApiPrefix = "$JS.API.";

	// JSApiAccountInfo is for obtaining general information about JetStream.
	private static final String jSApiAccountInfo = "INFO";
    
    // JSApiStreams can lookup a stream by subject.
	private static final String jSApiStreams = "STREAM.NAMES";
    
    // JSApiConsumerCreateT is used to create consumers.
	private static final String jSApiConsumerCreateT = "CONSUMER.CREATE.%s";
    
    // JSApiDurableCreateT is used to create durable consumers.
	private static final String jSApiDurableCreateT = "CONSUMER.DURABLE.CREATE.%s.%s";
    
    // JSApiConsumerInfoT is used to create consumers.
	private static final String jSApiConsumerInfoT = "CONSUMER.INFO.%s.%s";
    
    // JSApiStreamCreate is the endpoint to create new streams.
	private static final String jSApiStreamCreateT = "STREAM.CREATE.%s";

    private NatsConnection conn = null;
    private String prefix = null;
    private Duration defaultTimeout = Options.DEFAULT_CONNECTION_TIMEOUT;
    private PublishOptions defaultPubOpts = PublishOptions.builder().build();
    private JetStreamOptions options;
    private boolean direct = false;

    public class AccountLimitImpl implements AccountLimits {
        long memory = -1;
        long storage = -1;
        long streams = -1;
        long consumers = 1;
    
        private final Pattern memoryRE = JsonUtils.buildPattern("max_memory", FieldType.jsonNumber);
        private final Pattern storageRE = JsonUtils.buildPattern("max_storage", FieldType.jsonNumber);
        private final Pattern streamsRE = JsonUtils.buildPattern("max_streams", FieldType.jsonString);
        private final Pattern consumersRE = JsonUtils.buildPattern("max_consumers", FieldType.jsonString);    

        AccountLimitImpl(String json) {
            Matcher m = memoryRE.matcher(json);
            if (m.find()) {
                this.memory = Integer.parseInt(m.group(1));
            }

            m = storageRE.matcher(json);
            if (m.find()) {
                this.storage = Integer.parseInt(m.group(1));
            }
            
            m = streamsRE.matcher(json);
            if (m.find()) {
                this.streams = Integer.parseInt(m.group(1));
            }

            m = consumersRE.matcher(json);
            if (m.find()) {
                this.consumers = Integer.parseInt(m.group(1));
            } 
        }

        @Override
        public long getMaxMemory() {
            return memory;
        }

        @Override
        public long getMaxStorage() {
            return storage;
        }

        @Override
        public long getMaxStreams() {
            return streams;
        }

        @Override
        public long getMaxConsumers() {
            return consumers;
        }

    }

    public class AccountStatsImpl implements AccountStatistics {
        long memory = -1;
        long storage = -1;
        long streams = -1;
        long consumers = 1;
    
        private final Pattern memoryRE = JsonUtils.buildPattern("memory", FieldType.jsonNumber);
        private final Pattern storageRE = JsonUtils.buildPattern("storage", FieldType.jsonNumber);
        private final Pattern streamsRE = JsonUtils.buildPattern("streams", FieldType.jsonString);
        private final Pattern consumersRE = JsonUtils.buildPattern("consumers", FieldType.jsonString);

        AccountStatsImpl(String json) {
            Matcher m = memoryRE.matcher(json);
            if (m.find()) {
                this.memory = Integer.parseInt(m.group(1));
            }

            m = storageRE.matcher(json);
            if (m.find()) {
                this.storage = Integer.parseInt(m.group(1));
            }
            
            m = streamsRE.matcher(json);
            if (m.find()) {
                this.streams = Integer.parseInt(m.group(1));
            }

            m = consumersRE.matcher(json);
            if (m.find()) {
                this.consumers = Integer.parseInt(m.group(1));
            }             
        }
        @Override
        public long getMemory() {
            return memory;
        }

        @Override
        public long getStorage() {
            return storage;
        }

        @Override
        public long getStreams() {
            return streams;
        }

        @Override
        public long getConsumers() {
            return consumers;
        }
    }

    private static boolean isJetstreamEnabled(Message msg) {
        if (msg == null) {
            return false;
        }

        JetstreamAPIResponse apiResp = new JetstreamAPIResponse(msg.getData());
        if (apiResp.getCode() == 503 || apiResp.getError() != null) {
            return false;
        }
        // TODO - check headers for no responders.
        return true;
    }

    NatsJetStream(NatsConnection connection, JetStreamOptions jsOptions) throws InterruptedException, TimeoutException {
        if (jsOptions == null) {
            options = JetStreamOptions.builder().build();
        } else {
            options = jsOptions;
        }
        conn = connection;
        prefix = options.getPrefix();
        direct = options.isDirectMode();

        // override request style.
        conn.getOptions().setOldRequestStyle(true);

        String subj = appendPre(String.format(jSApiAccountInfo));
        Message resp = conn.request(subj, null, defaultTimeout);
        if (resp == null) {
            throw new TimeoutException("No response from the NATS server");
        }
        if (!isJetstreamEnabled(resp)) {
            throw new IllegalStateException("Jetstream is not enabled.");
        }
 
        // check the response
        new AccountStatsImpl(new String(resp.getData()));
    }    

    String appendPre(String subject) {
        if (prefix == null) {
            return jSDefaultApiPrefix + subject;
        }
        return prefix + subject;
    }
    
    private ConsumerInfo createOrUpdateConsumer(String streamName, ConsumerConfiguration config) throws TimeoutException, InterruptedException, IOException {
        String durable = config.getDurable();        
        String requestJSON = config.toJSON(streamName);

        String subj;
        if (durable == null) {
            subj = String.format(jSApiConsumerCreateT, streamName);
        } else {
            subj = String.format(jSApiDurableCreateT, streamName, durable);
        }
        
        Message resp = null;
        resp = conn.request(appendPre(subj), requestJSON.getBytes(), conn.getOptions().getConnectionTimeout());

        if (resp == null) {
            throw new TimeoutException("Consumer request to jetstream timed out.");
        }

        JetstreamAPIResponse jsResp = new JetstreamAPIResponse(resp.getData());
        if (jsResp.hasError()) {
            throw new IOException(jsResp.getError());
        }

        return new ConsumerInfo(jsResp.getResponse());
    }

    @Override
    public StreamInfo addStream(StreamConfiguration config) throws TimeoutException, InterruptedException {
        if (config == null) {
            throw new IllegalArgumentException("configuration cannot be null.");
        }
        String streamName = config.getName();
        if (streamName == null || streamName.isEmpty()) {
            throw new IllegalArgumentException("Configuration must have a valid name");
        }

        String subj = appendPre(String.format(jSApiStreamCreateT, streamName));
        Message resp = conn.request(subj, config.toJSON().getBytes(), defaultTimeout);
        if (resp == null) {
            throw new TimeoutException("No response from the NATS server");
        }
        JetstreamAPIResponse apiResp = new JetstreamAPIResponse(resp.getData());
        if (apiResp.hasError()) {
            throw new IllegalStateException(String.format("Could not create stream. %d : %s",
                apiResp.getCode(), apiResp.getDescription()));
        }
 
        return new StreamInfo(new String(resp.getData()));
    }

    @Override
    public ConsumerInfo addConsumer(String stream, ConsumerConfiguration config) throws InterruptedException, IOException, TimeoutException {
        checkName(stream, false, "subject");
        checkNull(config, "config");
        return addConsumer(null, stream, config);
    }

    private ConsumerInfo addConsumer(String subject, String stream, ConsumerConfiguration config) throws InterruptedException, IOException, TimeoutException {
        checkName(stream, false, "stream");
        checkNull(config, "config");
        if (subject != null) {
            config.setDeliverSubject(subject);
        }
        return createOrUpdateConsumer(stream, config);
    }

    @Override
    public PublishAck publish(String subject, byte[] payload) throws IOException, InterruptedException, TimeoutException {
        return this.publish(subject, payload, defaultPubOpts);
    }

    @Override
    public PublishAck publish(String subject, byte[] payload, PublishExpectation expects) throws IOException, InterruptedException, TimeoutException {
        return this.publish(subject, payload, defaultPubOpts, expects);
    }    

    private static boolean isStreamSpecified(String streamName) {
        return streamName != null && !PublishOptions.unspecifiedStream.equals(streamName);
    }

    @Override
    public PublishAck publish(String subject, byte[] payload, PublishOptions options) throws IOException, InterruptedException, TimeoutException{
        return publishInternal(subject, payload, options, null);
    }

    @Override
    public PublishAck publish(String subject, byte[] payload, PublishOptions options, PublishExpectation expects) throws IOException, InterruptedException, TimeoutException{
        return publishInternal(subject, payload, options, null);
    }    

    private PublishAck publishInternal(String subject, byte[] payload, PublishOptions options, PublishExpectationImpl expects) throws IOException, InterruptedException, TimeoutException{
        PublishOptions opts;
        if (options == null) {
            opts = defaultPubOpts;
        } else {
            opts = options;
        }
        Duration timeout = opts.getStreamTimeout();

        // TODO:  Add expects here.. this code is to satisfy the linter for CI.
        //if (expects.seq != -1) {
        //    return null;
        //}

        Message resp = conn.request(subject, payload, timeout);
        if (resp == null) {
            throw new TimeoutException("timeout waiting for jetstream");
        }
        NatsPublishAck ack = new NatsPublishAck(resp.getData());
            
        String ackStream = ack.getStream();
        if (ackStream == null || ackStream.length() == 0 || ack.getSeqno() == 0) {
            throw new IOException("Invalid jetstream ack.");
        }

        String pubStream = opts.getStream(); 
        if (isStreamSpecified(pubStream) && !pubStream.equals(ackStream)) {
            throw new IOException("Expected ack from stream " + pubStream + ", received from: " + ackStream);
        }

        return ack;    
    }

    ConsumerInfo getConsumerInfo(String stream, String consumer) throws IOException, TimeoutException,
            InterruptedException {
        String ccInfoSubj = this.appendPre(String.format(jSApiConsumerInfoT, stream, consumer));
        Message resp = conn.request(ccInfoSubj, null, defaultTimeout);
        if (resp == null) {
            throw new TimeoutException("Consumer request to jetstream timed out.");
        }

        JetstreamAPIResponse jsResp = new JetstreamAPIResponse(resp.getData());
        if (jsResp.hasError()) {
            throw new IllegalStateException(jsResp.getError());
        }

        return new ConsumerInfo(jsResp.getResponse());
    }

    private String lookupStreamBySubject(String subject) throws InterruptedException, IOException, TimeoutException {
        if (subject == null) {
            throw new IllegalArgumentException("stream cannot be null.");
        }
        String streamRequest = String.format("{\"subject\":\"%s\"}", subject);

        Message resp = conn.request(appendPre(jSApiStreams), streamRequest.getBytes(), defaultTimeout);
        if (resp == null) {
            throw new TimeoutException("Consumer request to jetstream timed out.");
        }

        JetstreamAPIResponse jsResp = new JetstreamAPIResponse(resp.getData());
        if (jsResp.hasError()) {
            throw new IOException(jsResp.getError());
        }

        String[] streams = JsonUtils.parseStringArray("streams", jsResp.getResponse());
        if (streams.length != 1) {
            throw new IllegalStateException("No matching streams.");
        }
        return streams[0];
    }

    private class AutoAckMessageHandler implements MessageHandler {

        MessageHandler mh;

        // caller must ensure userMH is not null
        AutoAckMessageHandler(MessageHandler userMH) {
            mh = userMH;
        }

        @Override
        public void onMessage(Message msg) throws InterruptedException {
            try  {
                mh.onMessage(msg);
                msg.ack();
            } catch (Exception e) {
                // ignore??  schedule async error?
            }
        }
    }

    NatsJetStreamSubscription createSubscription(String subject, String queueName, NatsDispatcher dispatcher, MessageHandler handler, SubscribeOptions options) throws InterruptedException, TimeoutException, IOException{

        // setup the configuration, use a default.
        SubscribeOptions o = (options == null) ? SubscribeOptions.builder().build() : new SubscribeOptions(options);
        if (o.getConsumerConfiguration() == null) {
            o.setConfiguration(ConsumerConfiguration.builder().build());
        }

        ConsumerConfiguration cfg = o.getConsumerConfiguration();

        boolean isPullMode = (o.getPullBatchSize() > 0);
        if (handler != null && isPullMode) {
            throw new IllegalStateException("Pull mode is not allowed with dispatcher.");
        }

        boolean shouldAttach = o.getStream() != null && o.getConsumer() != null || o.getConsumerConfiguration().getDeliverSubject() != null;
        boolean shouldCreate = !shouldAttach;

        if (shouldAttach && this.direct) {
            throw new IllegalStateException("Direct mode is rewquired to attach.");
        }

        String deliver = null;
        String stream = null;
        ConsumerConfiguration ccfg = null;

        if (direct) {
            String s = o.getConsumerConfiguration().getDeliverSubject();
            if (s == null) {
                deliver = conn.createInbox();
            } else {
                deliver = s;
            }
        } else if (shouldAttach) {
            ccfg = getConsumerInfo(o.getStream(), o.getConsumer()).getConsumerConfiguration();

            // Make sure the subject matches or is a subset...
            if (ccfg.getFilterSubject() != null && !ccfg.getFilterSubject().equals(subject)) {
                throw new IllegalArgumentException(String.format("Subject %s mismatches consumer configuration %s.",
                    subject, ccfg.getFilterSubject()));
            }

            String s = ccfg.getDeliverSubject();
            deliver = s != null ? s : conn.createInbox();
        } else {
            stream = lookupStreamBySubject(subject);
            deliver = conn.createInbox();
            if (!isPullMode) {
                cfg.setDeliverSubject(deliver);
            }
            cfg.setFilterSubject(subject);
        }

        NatsJetStreamSubscription sub;
        if (dispatcher != null) {
            MessageHandler mh;
            if (options == null || options.isAutoAck()) {
                mh = new AutoAckMessageHandler(handler);
            } else {
                mh = handler;
            } 
            sub = (NatsJetStreamSubscription) dispatcher.subscribeImpl(deliver, queueName, mh, true);
        } else {
            sub = (NatsJetStreamSubscription) conn.createSubscription(deliver, queueName, dispatcher, true);
        }

        // if we're updating or creating the consumer, give it a go here.
        if (shouldCreate) {
            // Defaults should set the right ack pending.

            // if we have acks and the maxAckPending is not set, set it
            // to the internal Max.
            // TODO:  too high value?
            if (cfg.getMaxAckPending() == 0) {
                cfg.setMaxAckPending(sub.getPendingMessageLimit());
            }

            try  {
                ConsumerInfo ci = createOrUpdateConsumer(stream, cfg);
                sub.setupJetStream(this, ci.getName(), ci.getStreamName(),
                    deliver, o.getPullBatchSize());
            } catch (Exception e) {
                sub.unsubscribe();
                throw e;
            }
        } else {
            String s = direct ? o.getConsumerConfiguration().getDeliverSubject() : ccfg.getDeliverSubject();
            if (s == null) {
                s = deliver;
            }
            sub.setupJetStream(this, o.getConsumer(), o.getStream(), s, o.getPullBatchSize());
        }

        if (isPullMode) {
            sub.poll();
        }

        return sub;
    }

    private static void checkName(String s, boolean allowEmpty, String varName) {
        if (!allowEmpty && (s == null || s.isEmpty())) {
            throw new IllegalArgumentException(varName + " cannot be null or empty.");
        }

        if (s == null) {
            return;
        }

        for (int i = 0; i < s.length(); i++){
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                throw new IllegalArgumentException(varName + " cannot contain spaces.");
            }
        }
    }

    private static void checkNull(Object s, String name) {
        if (s == null) {
            throw new IllegalArgumentException(name + "cannot be null");
        }
    }

    static boolean isValidStreamName(String s) {
        if (s == null) {
            return false;
        }
        return !s.contains(".") && !s.contains("*") && !s.contains(">");
    }

    @Override
    public JetStreamSubscription subscribe(String subject) throws InterruptedException, TimeoutException, IOException {
        checkName(subject, true, "subject");
        return createSubscription(subject, null, null, null, SubscribeOptions.builder().build()); 
    }

    @Override
    public JetStreamSubscription subscribe(String subject, SubscribeOptions options)
            throws InterruptedException, TimeoutException, IOException {
        checkName(subject, true, "subject");
        return createSubscription(subject, null, null, null, options); 
    }

    @Override
    public JetStreamSubscription subscribe(String subject, String queue, SubscribeOptions options)
            throws InterruptedException, TimeoutException, IOException {
        checkName(subject, true, "subject");
        checkName(queue, false, "queue");
        checkNull(options, "options");
        return createSubscription(subject, queue, null, null, options); 
    }

    @Override
    public JetStreamSubscription subscribe(String subject, Dispatcher dispatcher, MessageHandler handler) throws InterruptedException, TimeoutException, IOException {
        checkName(subject, true, "subject");
        checkNull(dispatcher, "dispatcher");
        return createSubscription(subject, null, (NatsDispatcher) dispatcher, handler, null);
    }

    @Override
    public JetStreamSubscription subscribe(String subject, Dispatcher dispatcher, MessageHandler handler, SubscribeOptions options) throws InterruptedException, TimeoutException, IOException {
        checkName(subject, true, "subject");
        checkNull(dispatcher, "dispatcher");
        checkNull(handler, "handler");
        checkNull(options, "options");           
        return createSubscription(subject, null, (NatsDispatcher) dispatcher, handler, options);
    }

    @Override
    public JetStreamSubscription subscribe(String subject, String queue, Dispatcher dispatcher, MessageHandler handler) throws InterruptedException, TimeoutException, IOException {
        checkName(subject, true, "subject");
        checkName(queue, false, "queue");
        checkNull(dispatcher, "dispatcher");
        checkNull(handler, "handler");
        return createSubscription(subject, queue, (NatsDispatcher) dispatcher, handler, null);
    }

    @Override
    public JetStreamSubscription subscribe(String subject, String queue, Dispatcher dispatcher, MessageHandler handler, SubscribeOptions options) throws InterruptedException, TimeoutException, IOException {
        checkName(subject, true, "subject");
        checkName(queue, false, "queue");
        checkNull(dispatcher, "dispatcher");
        checkNull(handler, "handler");
        checkNull(options, "options");
        return createSubscription(subject, queue, (NatsDispatcher) dispatcher, handler, options);

    }

    @Override
    public PublishExpectation createPublishExpectation() {
        return new PublishExpectationImpl();
    }
}
