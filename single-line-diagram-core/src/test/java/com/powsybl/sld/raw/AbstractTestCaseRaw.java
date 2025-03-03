/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sld.raw;

import com.powsybl.sld.AbstractTestCase;
import com.powsybl.sld.builders.RawGraphBuilder;
import com.powsybl.sld.layout.LayoutParameters;
import com.powsybl.sld.model.coordinate.Direction;
import com.powsybl.sld.model.graphs.Graph;
import com.powsybl.sld.model.nodes.FeederNode;
import com.powsybl.sld.model.nodes.Node;
import com.powsybl.sld.svg.*;

import java.util.*;
import java.util.stream.Stream;

import static com.powsybl.sld.library.ComponentTypeName.ARROW_ACTIVE;
import static com.powsybl.sld.library.ComponentTypeName.ARROW_REACTIVE;

/**
 * @author Benoit Jeanson <benoit.jeanson at rte-france.com>
 */
public abstract class AbstractTestCaseRaw extends AbstractTestCase {
    protected RawGraphBuilder rawGraphBuilder = new RawGraphBuilder();

    protected RawDiagramLabelProvider getRawLabelProvider(Graph graph) {
        return new RawDiagramLabelProvider(graph.getAllNodesStream(), layoutParameters);
    }

    @Override
    public String toSVG(Graph graph, String filename) {
        return toSVG(graph, filename, getRawLabelProvider(graph), new BasicStyleProvider());
    }

    private static class RawDiagramLabelProvider implements DiagramLabelProvider {
        private final Map<Node, List<NodeLabel>> nodeLabels;
        private final LayoutParameters layoutParameters;

        public RawDiagramLabelProvider(Stream<Node> nodeStream, LayoutParameters layoutParameters) {
            this.nodeLabels = new HashMap<>();
            this.layoutParameters = layoutParameters;
            LabelPosition labelPosition = new LabelPosition("default", 0, -5, true, 0);
            nodeStream.forEach(n -> getLabelOrNameOrId(n).ifPresent(text ->
                    nodeLabels.put(n, Collections.singletonList(new DiagramLabelProvider.NodeLabel(text, labelPosition)))));
        }

        private Optional<String> getLabelOrNameOrId(Node node) {
            return Optional.ofNullable(node.getLabel().orElse(layoutParameters.isUseName() ? node.getName() : node.getId()));
        }

        @Override
        public List<FeederInfo> getFeederInfos(FeederNode node) {
            return Arrays.asList(
                    new DirectionalFeederInfo(ARROW_ACTIVE, LabelDirection.OUT, "", "tata", null),
                    new DirectionalFeederInfo(ARROW_REACTIVE, LabelDirection.IN, "", "tutu", null));
        }

        @Override
        public List<NodeLabel> getNodeLabels(Node node, Direction direction) {
            return nodeLabels.getOrDefault(node, Collections.emptyList());
        }

        @Override
        public List<NodeDecorator> getNodeDecorators(Node node, Direction direction) {
            return new ArrayList<>();
        }
    }
}
