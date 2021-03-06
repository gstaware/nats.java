// Copyright 2020 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.nats.examples;

import io.nats.client.*;
import io.nats.client.impl.JetStreamApiException;
import io.nats.client.impl.NatsMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * This example will demonstrate a pull subscription.
 *
 * Usage: java NatsJsSub [-s server]
 *   Use tls:// or opentls:// to require tls, via the Default SSLContext
 *   Set the environment variable NATS_NKEY to use challenge response authentication by setting a file containing your private key.
 *   Set the environment variable NATS_CREDS to use JWT/NKey authentication by setting a file containing your user creds.
 *   Use the URL for user/pass/token authentication.
 */
public class NatsJsPullSub {

    // STREAM, SUBJECT, DURABLE are required.
    static final String STREAM = "pull-stream";
    static final String SUBJECT = "pull-subject";
    static final String DURABLE = "pull-durable";

    public static void main(String[] args) {

        try (Connection nc = Nats.connect(ExampleUtils.createExampleOptions(args))) {
            NatsJsUtils.createOrUpdateStream(nc, STREAM, SUBJECT);

            // Create our JetStream context to receive JetStream messages.
            JetStream js = nc.jetStream();

            // Build our subscription options. Durable is REQUIRED for pull based subscriptions
            PullSubscribeOptions pullOptions = PullSubscribeOptions.builder()
                    .durable(DURABLE)      // required
                    // .configuration(...) // if you want a custom io.nats.client.ConsumerConfiguration
                    .build();

            // 0.1 Initialize. subscription
            // 0.2 Start the pull, you don't have to call this again
            // 0.3 Flush outgoing communication with/to the server, useful when app is both publishing and subscribing.
            System.out.println("\n----------\n0. Initialize the subscription and pull.");
            JetStreamSubscription sub = js.subscribe(SUBJECT, pullOptions);
            sub.pull(10);
            nc.flush(Duration.ofSeconds(1));

            // 1. Publish some that is less than the batch size.
            // -  Read what is available.
            // -  When we ack a batch message the server starts preparing or adding to the next batch.
            System.out.println("\n----------\n1. Publish some amount of messages, but not entire batch size.");
            publish(js, SUBJECT, "A", 4);
            readMessagesAck(sub);

            // 2. Publish some more covering our pull size...
            // -  Read what is available, expect all messages.
            System.out.println("----------\n2. Publish more than the batch size.");
            publish(js, SUBJECT, "B", 6);
            readMessagesAck(sub);

            // 3. Read when there are no messages available.
            System.out.println("----------\n3. Read when there are no messages available.");
            readMessagesAck(sub);

            // 4. Publish some more...
            // -  Read what is available, expect all messages.
            System.out.println("----------\n4. Publish more then read.");
            publish(js, SUBJECT, "C", 12);
            readMessagesAck(sub);

            // 5. Read when there are no messages available.
            System.out.println("----------\n5. Read when there are no messages available.");
            readMessagesAck(sub);

            System.out.println("----------\n");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void publish(JetStream js, String subject, String prefix, int count) throws IOException, JetStreamApiException {
        System.out.print("Publish ->");
        for (int x = 1; x <= count; x++) {
            String data = "#" + prefix + "." + x;
            System.out.print(" " + data);
            Message msg = NatsMessage.builder()
                    .subject(subject)
                    .data(data.getBytes(StandardCharsets.US_ASCII))
                    .build();
            js.publish(msg);
        }
        System.out.println(" <-");
    }

    public static void readMessagesAck(JetStreamSubscription sub) throws InterruptedException {
        Message msg = sub.nextMessage(Duration.ofSeconds(1));
        boolean first = true;
        while (msg != null) {
            if (first) {
                first = false;
                System.out.print("Read/Ack ->");
            }
            if (msg.isJetStream()) {
                msg.ack();
                System.out.print(" " + new String(msg.getData()));
                msg = sub.nextMessage(Duration.ofSeconds(1));
            }
            else {
                msg = null; // so we break the loop
                System.out.print(" !Status! ");
            }
        }
        if (first) {
            System.out.println("No messages available.");
        }
        else {
            System.out.println(" <- ");
        }
    }
}