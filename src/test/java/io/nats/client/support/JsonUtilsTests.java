// Copyright 2015-2018 The NATS Authors
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

package io.nats.client.support;

import io.nats.client.ConsumerInfo;
import io.nats.client.ConsumerLister;
import io.nats.client.StreamInfo;
import io.nats.client.impl.DateTimeUtils;
import io.nats.client.impl.JsonUtils;
import io.nats.client.utils.ResourceUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.nats.client.utils.ResourceUtils.dataAsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public final class JsonUtilsTests {

    @Test
    public void testParseStringArray() {
        String[] a = JsonUtils.parseStringArray("fieldName", "...\"fieldName\": [\n      ],...");
        assertNotNull(a);
        assertEquals(0, a.length);

        a = JsonUtils.parseStringArray("fieldName", "...\"fieldName\": [\n      \"value1\"\n    ],...");
        assertNotNull(a);
        assertEquals(1, a.length);
        assertEquals("value1", a[0]);

        a = JsonUtils.parseStringArray("fieldName", "...\"fieldName\": [\n      \"value1\",\n      \"value2\"\n    ],...");
        assertNotNull(a);
        assertEquals(2, a.length);
        assertEquals("value1", a[0]);
        assertEquals("value2", a[1]);
    }

    @Test
    public void testGetJSONArray() {
        String json = ResourceUtils.dataAsString("ConsumerLister.json");
        ConsumerLister cl = new ConsumerLister(json);
        assertEquals(2, cl.getTotal());
        assertEquals(42, cl.getOffset());
        assertEquals(256, cl.getLimit());
        List<ConsumerInfo> consumers = cl.getConsumers();
        assertNotNull(consumers);
        assertEquals(2, consumers.size());
    }

    @Test
    public void testCoverage_JsonUtils_addFld() {
        StringBuilder sb = new StringBuilder();
        assertEquals(0, sb.length());
        String[] strArray = null;
        JsonUtils.addFld(sb, "na", strArray);
        assertEquals(0, sb.length());
        strArray = new String[]{};
        JsonUtils.addFld(sb, "na", strArray);
        assertEquals(0, sb.length());
    }

    @Test
    public void testParseDateTime() {
        assertEquals(1611186068, DateTimeUtils.parseDateTime("2021-01-20T23:41:08.579594Z").toEpochSecond());
        assertEquals(1612293508, DateTimeUtils.parseDateTime("2021-02-02T11:18:28.347722551-08:00").toEpochSecond());
        assertEquals(-62135596800L, DateTimeUtils.parseDateTime("anything-not-valid").toEpochSecond());
    }

    @Test
    public void testCoverage_printable() {
        // doesn't really test anything, this is not production code. just for coverage
        DebugUtil.printable(new ConsumerLister(dataAsString("ConsumerLister.json")));
        DebugUtil.printable(new StreamInfo(dataAsString("StreamInfo.json")));
    }
}
