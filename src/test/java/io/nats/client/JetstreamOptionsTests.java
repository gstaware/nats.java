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

package io.nats.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

public class JetstreamOptionsTests {
    
	@Test
    public void testOptions() {
        JetStreamOptions jo = JetStreamOptions.builder().requestTimeout(Duration.ofSeconds(42)).prefix("pre").direct(true).build();
        assertEquals("pre", jo.getPrefix());
        assertEquals(Duration.ofSeconds(42), jo.getRequestTimeout());
        assertEquals(true, jo.isDirectMode());
    }

    @Test
    public void testInvalidPrefix() {
        assertThrows(IllegalArgumentException.class, () -> { JetStreamOptions.builder().prefix(">").build();});
        assertThrows(IllegalArgumentException.class, () -> { JetStreamOptions.builder().prefix("*").build();});
    }
    
    public void testDefaults() {

    }
}