/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sld.model.nodes;

import com.fasterxml.jackson.core.JsonGenerator;
import com.powsybl.sld.model.coordinate.Point;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author Benoit Jeanson <benoit.jeanson at rte-france.com>
 * @author Nicolas Duchene
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class Edge {

    private final List<Node> nodes;

    public Edge(Node node1, Node node2) {
        this.nodes = Arrays.asList(node1, node2);
    }

    public Node getNode(int index) {
        return nodes.get(index);
    }

    public Node getNode1() {
        return getNode(0);
    }

    public Node getNode2() {
        return getNode(1);
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public void writeJson(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        for (int i = 1; i <= nodes.size(); ++i) {
            generator.writeStringField("node" + i, nodes.get(i - 1).getId());
        }
        generator.writeEndObject();
    }

    public boolean isZeroLength() {
        Point node1 = getNode1().getCoordinates();
        Point node2 = getNode2().getCoordinates();
        return node1.getX() == node2.getX() && node1.getY() == node2.getY();
    }
}
