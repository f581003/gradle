/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.changedetection.state.mirror;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PhysicalDirectorySnapshot implements PhysicalSnapshot {
    private final ConcurrentMap<String, PhysicalSnapshot> children = new ConcurrentHashMap<String, PhysicalSnapshot>();
    private final String name;

    public PhysicalDirectorySnapshot(String name) {
        this.name = name;
    }

    @Override
    public PhysicalSnapshot find(String[] segments, int offset) {
        if (segments.length == offset) {
            return this;
        }
        PhysicalSnapshot child = children.get(segments[offset]);
        return child != null ? child.find(segments, offset + 1) : null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public PhysicalSnapshot add(String[] segments, int offset, PhysicalSnapshot snapshot) {
        if (segments.length == offset) {
            return this;
        }
        String currentSegment = segments[offset];
        PhysicalSnapshot child = children.get(currentSegment);
        if (child == null) {
            PhysicalSnapshot newChild;
            if (segments.length == offset + 1) {
                newChild = snapshot;
            } else {
                newChild = new PhysicalDirectorySnapshot(currentSegment);
            }
            child = children.putIfAbsent(currentSegment, newChild);
            if (child == null) {
                child = newChild;
            }
        }
        return child.add(segments, offset + 1, snapshot);
    }
}
