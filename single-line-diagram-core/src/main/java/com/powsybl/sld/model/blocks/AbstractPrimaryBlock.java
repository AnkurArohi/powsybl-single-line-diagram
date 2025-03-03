/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sld.model.blocks;

import com.fasterxml.jackson.core.JsonGenerator;
import com.powsybl.commons.PowsyblException;
import com.powsybl.sld.model.coordinate.Orientation;
import com.powsybl.sld.model.nodes.BusNode;
import com.powsybl.sld.model.nodes.Node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Benoit Jeanson <benoit.jeanson at rte-france.com>
 * @author Nicolas Duchene
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public abstract class AbstractPrimaryBlock extends AbstractBlock implements PrimaryBlock {

    protected final List<Node> nodes;

    /**
     * Constructor.
     * A layout.block primary is oriented in order to have :
     * <ul>
     * <li>BUS - when in the layout.block - as starting node
     * <li>FEEDER - when in the layout.block - as ending node
     * </ul>
     *
     * @param nodes nodes
     */

    protected AbstractPrimaryBlock(Type type, List<Node> nodes) {
        super(type);
        if (nodes.isEmpty()) {
            throw new PowsyblException("Empty node list");
        }
        this.nodes = new ArrayList<>(nodes);
        setCardinality(Extremity.START, 1);
        setCardinality(Extremity.END, 1);
    }

    public static PrimaryBlock createPrimaryBlock(List<Node> primaryPattern) {
        Node.NodeType firstNodeType = primaryPattern.get(0).getType();
        Node.NodeType lastNodeType = primaryPattern.get(primaryPattern.size() - 1).getType();
        if (firstNodeType == Node.NodeType.BUS || lastNodeType == Node.NodeType.BUS) {
            return new LegPrimaryBlock(primaryPattern);
        }
        if (firstNodeType == Node.NodeType.FEEDER || lastNodeType == Node.NodeType.FEEDER) {
            return new FeederPrimaryBlock(primaryPattern);
        }
        return BodyPrimaryBlock.createBodyPrimaryBlockInBusCell(primaryPattern);
    }

    @Override
    public boolean isEmbeddingNodeType(Node.NodeType type) {
        return nodes.stream().anyMatch(n -> n.getType() == type);
    }

    @Override
    public List<Block> findBlockEmbeddingNode(Node node) {
        List<Block> result = new ArrayList<>();
        if (nodes.contains(node)) {
            result.add(this);
        }
        return result;
    }

    public List<Node> getNodes() {
        return new ArrayList<>(nodes);
    }

    @Override
    public void reverseBlock() {
        Collections.reverse(nodes);
    }

    @Override
    public Node getExtremityNode(Extremity extremity) {
        if (extremity == Extremity.START) {
            return nodes.get(0);
        }
        if (extremity == Extremity.END) {
            return nodes.get(nodes.size() - 1);
        }
        return null;
    }

    @Override
    public void setOrientation(Orientation orientation) {
        super.setOrientation(orientation);
        setOrientation(orientation, true);
    }

    @Override
    public void setOrientation(Orientation orientation, boolean recursively) {
        super.setOrientation(orientation);
        if (recursively) {
            nodes.stream().filter(n -> !(n instanceof BusNode))
                    .forEach(n -> n.setOrientation(orientation));
        }
    }

    @Override
    protected void writeJsonContent(JsonGenerator generator, boolean includeCoordinates) throws IOException {
        generator.writeFieldName("nodes");
        generator.writeStartArray();
        for (int i = 1; i <= nodes.size(); ++i) {
            generator.writeString(nodes.get(i - 1).getId());
        }
        generator.writeEndArray();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + nodes;
    }
}
