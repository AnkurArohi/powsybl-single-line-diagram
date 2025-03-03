/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sld.builders;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.util.ServiceLoaderCache;
import com.powsybl.iidm.network.*;
import com.powsybl.sld.iidm.extensions.BusbarSectionPosition;
import com.powsybl.sld.iidm.extensions.ConnectablePosition;
import com.powsybl.sld.model.coordinate.Direction;
import com.powsybl.sld.model.nodes.*;
import com.powsybl.sld.model.graphs.*;
import com.powsybl.sld.postprocessor.GraphBuildPostProcessor;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.powsybl.sld.library.ComponentTypeName.*;
import static com.powsybl.sld.model.coordinate.Direction.*;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class NetworkGraphBuilder implements GraphBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkGraphBuilder.class);

    private static final ServiceLoaderCache<GraphBuildPostProcessor> POST_PROCESSOR_LOADER = new ServiceLoaderCache<>(GraphBuildPostProcessor.class);

    private final Network network;  // IIDM network

    public NetworkGraphBuilder(Network network) {
        this.network = Objects.requireNonNull(network);
    }

    private static boolean isInternalToVoltageLevel(Branch<?> branch) {
        return branch.getTerminal1().getVoltageLevel().getId().equals(branch.getTerminal2().getVoltageLevel().getId());
    }

    private static boolean isNotInternalToVoltageLevel(Branch<?> branch) {
        return !isInternalToVoltageLevel(branch);
    }

    private static boolean isInternalToVoltageLevel(ThreeWindingsTransformer transformer) {
        return transformer.getLeg1().getTerminal().getVoltageLevel().getId().equals(transformer.getLeg2().getTerminal().getVoltageLevel().getId())
                && transformer.getLeg2().getTerminal().getVoltageLevel().getId().equals(transformer.getLeg3().getTerminal().getVoltageLevel().getId());
    }

    private static boolean isNotInternalToVoltageLevel(ThreeWindingsTransformer transformer) {
        return !isInternalToVoltageLevel(transformer);
    }

    private static boolean isInternalToSubstation(Branch<?> branch) {
        Optional<Substation> substation1 = branch.getTerminal1().getVoltageLevel().getSubstation();
        Optional<Substation> substation2 = branch.getTerminal2().getVoltageLevel().getSubstation();
        return substation1.isPresent() && substation2.isPresent() && substation1.get() == substation2.get();
    }

    private static boolean isNotInternalToSubstation(Branch<?> branch) {
        return !isInternalToSubstation(branch);
    }

    @Override
    public VoltageLevelGraph buildVoltageLevelGraph(String id, Graph parentGraph) {
        // get the voltageLevel from id
        VoltageLevel vl = network.getVoltageLevel(id);
        if (vl == null) {
            throw new PowsyblException("Voltage level '" + id + "' not found !!");
        }

        // build the graph from the voltage level
        VoltageLevelGraph graph = new VoltageLevelGraph(new VoltageLevelInfos(vl.getId(), vl.getNameOrId(), vl.getNominalV()), parentGraph);
        buildGraph(graph, vl);

        return graph;
    }

    @Override
    public VoltageLevelGraph buildVoltageLevelGraph(String id) {
        return buildVoltageLevelGraph(id, null);
    }

    private void buildGraph(VoltageLevelGraph graph, VoltageLevel vl) {
        LOGGER.info("Building '{}' graph...", vl.getId());

        switch (vl.getTopologyKind()) {
            case BUS_BREAKER:
                buildBusBreakerGraph(graph, vl);
                break;
            case NODE_BREAKER:
                buildNodeBreakerGraph(graph, vl);
                break;
            default:
                throw new AssertionError("Unknown topology kind: " + vl.getTopologyKind());
        }

        // Add snake edges in the same voltage level
        addSnakeEdges(graph, vl);

        LOGGER.info("{} nodes, {} edges", graph.getNodes().size(), graph.getEdges().size());

        handleGraphPostProcessors(graph);

        handleConnectedComponents(graph);
    }

    private void addSnakeEdges(VoltageLevelGraph graph, VoltageLevel vl) {
        addLineEdges(graph, vl.getConnectableStream(Line.class)
                .filter(NetworkGraphBuilder::isInternalToVoltageLevel)
                .collect(Collectors.toList()));

        add2wtEdges(graph, vl.getConnectableStream(TwoWindingsTransformer.class)
                .filter(NetworkGraphBuilder::isInternalToVoltageLevel)
                .collect(Collectors.toList()));

        add3wtEdges(graph, vl.getConnectableStream(ThreeWindingsTransformer.class)
                .filter(t -> t.getLeg1().getTerminal().getVoltageLevel().getId().equals(t.getLeg2().getTerminal().getVoltageLevel().getId())
                        && t.getLeg2().getTerminal().getVoltageLevel().getId().equals(t.getLeg3().getTerminal().getVoltageLevel().getId()))
                .collect(Collectors.toList()));

    }

    @Override
    public SubstationGraph buildSubstationGraph(String id, ZoneGraph parentGraph) {
        // get the substation from id
        Substation substation = network.getSubstation(id);
        if (substation == null) {
            throw new PowsyblException("Substation '" + id + "' not found !!");
        }

        // build the substation graph from the substation
        SubstationGraph graph = SubstationGraph.create(substation.getId(), parentGraph);
        buildSubstationGraph(graph, substation);

        return graph;
    }

    @Override
    public SubstationGraph buildSubstationGraph(String id) {
        return buildSubstationGraph(id, null);
    }

    private void buildSubstationGraph(SubstationGraph graph, Substation substation) {
        // building the graph for each voltageLevel (ordered by descending voltageLevel nominalV)
        substation.getVoltageLevelStream()
                .sorted(Comparator.comparing(VoltageLevel::getNominalV)
                        .reversed())
                .forEach(v -> {
                    VoltageLevelGraph vlGraph = new VoltageLevelGraph(new VoltageLevelInfos(v.getId(), v.getNameOrId(), v.getNominalV()), graph);
                    buildGraph(vlGraph, v);
                    graph.addVoltageLevel(vlGraph);
                });

        // Add snake edges between different voltageLevels in the same substation
        addSnakeEdges(graph, substation);

        LOGGER.info("Number of voltage levels: {} ", graph.getVoltageLevels().size());
    }

    private void addSnakeEdges(SubstationGraph graph, Substation substation) {
        addLineEdges(graph, substation.getVoltageLevelStream()
                .flatMap(voltageLevel -> voltageLevel.getConnectableStream(Line.class))
                .filter(NetworkGraphBuilder::isInternalToSubstation)
                .filter(NetworkGraphBuilder::isNotInternalToVoltageLevel)
                .collect(Collectors.toList()));

        add2wtEdges(graph, substation.getTwoWindingsTransformerStream()
                .filter(NetworkGraphBuilder::isNotInternalToVoltageLevel)
                .collect(Collectors.toList()));

        add3wtEdges(graph, substation.getThreeWindingsTransformerStream()
                .filter(NetworkGraphBuilder::isNotInternalToVoltageLevel)
                .collect(Collectors.toList()));
    }

    private abstract static class AbstractGraphBuilder extends DefaultTopologyVisitor {

        protected final VoltageLevelGraph graph;

        protected AbstractGraphBuilder(VoltageLevelGraph graph) {
            this.graph = graph;
        }

        protected abstract void addFeeder(FeederNode node, Terminal terminal);

        protected abstract void add3wtFeeder(Middle3WTNode middleNode, Feeder3WTLegNode firstOtherLegNode,
                                             Feeder3WTLegNode secondOtherLegNode, Terminal terminal);

        private FeederNode createFeederLineNode(VoltageLevelGraph graph, Line line, Branch.Side side) {
            Objects.requireNonNull(graph);
            Objects.requireNonNull(line);

            String id = line.getId() + "_" + side.name();
            String name = line.getNameOrId();
            String equipmentId = line.getId();
            FeederWithSideNode.Side s = FeederWithSideNode.Side.valueOf(side.name());
            Branch.Side otherSide = side == Branch.Side.ONE ? Branch.Side.TWO : Branch.Side.ONE;
            VoltageLevel vlOtherSide = line.getTerminal(otherSide).getVoltageLevel();
            return NodeFactory.createFeederLineNode(graph, id, name, equipmentId, s, new VoltageLevelInfos(vlOtherSide.getId(), vlOtherSide.getNameOrId(), vlOtherSide.getNominalV()));
        }

        private FeederNode createFeederNode(VoltageLevelGraph graph, Injection<?> injection) {
            Objects.requireNonNull(graph);
            Objects.requireNonNull(injection);
            switch (injection.getType()) {
                case GENERATOR:
                    return NodeFactory.createGenerator(graph, injection.getId(), injection.getNameOrId());
                case LOAD:
                    return NodeFactory.createLoad(graph, injection.getId(), injection.getNameOrId());
                case STATIC_VAR_COMPENSATOR:
                    return NodeFactory.createStaticVarCompensator(graph, injection.getId(), injection.getNameOrId());
                case SHUNT_COMPENSATOR:
                    // FIXME(mathbagu): Non linear shunt can be capacitor or inductor depending on the number of section enabled
                    return ((ShuntCompensator) injection).getB() >= 0 ? NodeFactory.createCapacitor(graph, injection.getId(), injection.getNameOrId())
                            : NodeFactory.createInductor(graph, injection.getId(), injection.getNameOrId());
                case DANGLING_LINE:
                    return NodeFactory.createDanglingLine(graph, injection.getId(), injection.getNameOrId());
                default:
                    throw new IllegalStateException();
            }
        }

        private FeederNode createFeederNode(VoltageLevelGraph graph, HvdcConverterStation<?> hvdcStation) {
            Objects.requireNonNull(graph);
            Objects.requireNonNull(hvdcStation);

            HvdcLine hvdcLine = hvdcStation.getHvdcLine();
            var optOtherStation = hvdcStation.getOtherConverterStation();
            if (optOtherStation.isEmpty()) {
                return NodeFactory.createVscConverterStation(graph, hvdcStation.getId(), hvdcStation.getNameOrId(), null, null, null);
            } else {
                var side = hvdcLine.getConverterStation1() == hvdcStation ? FeederWithSideNode.Side.ONE : FeederWithSideNode.Side.TWO;
                var vlOtherSide = optOtherStation.get().getTerminal().getVoltageLevel();
                VoltageLevelInfos otherSideVoltageLevelInfos = new VoltageLevelInfos(vlOtherSide.getId(), vlOtherSide.getNameOrId(), vlOtherSide.getNominalV());

                return NodeFactory.createVscConverterStation(graph, hvdcStation.getId(), hvdcStation.getNameOrId(), hvdcLine.getId(), side, otherSideVoltageLevelInfos);
            }
        }

        private FeederNode createFeeder2wtNode(VoltageLevelGraph graph,
                                               TwoWindingsTransformer branch,
                                               Branch.Side side) {
            Objects.requireNonNull(graph);
            Objects.requireNonNull(branch);

            String id = branch.getId() + "_" + side.name();
            String name = branch.getNameOrId();
            String equipmentId = branch.getId();
            Branch.Side otherSide = side == Branch.Side.ONE ? Branch.Side.TWO : Branch.Side.ONE;
            VoltageLevel vlOtherSide = branch.getTerminal(otherSide).getVoltageLevel();
            VoltageLevelInfos otherSideVoltageLevelInfos = new VoltageLevelInfos(vlOtherSide.getId(), vlOtherSide.getNameOrId(), vlOtherSide.getNominalV());

            if (graph.isForVoltageLevelDiagram() && isNotInternalToVoltageLevel(branch)) {
                if (!branch.hasPhaseTapChanger()) {
                    return NodeFactory.createFeeder2WTNode(graph, id, name, equipmentId, FeederWithSideNode.Side.valueOf(side.name()), otherSideVoltageLevelInfos);
                } else {
                    return NodeFactory.createFeeder2WTNodeWithPhaseShifter(graph, id, name, equipmentId, FeederWithSideNode.Side.valueOf(side.name()), otherSideVoltageLevelInfos);
                }
            } else {
                if (!branch.hasPhaseTapChanger()) {
                    return NodeFactory.createFeeder2WTLegNode(graph, id, name, equipmentId, FeederWithSideNode.Side.valueOf(side.name()));
                } else {
                    return NodeFactory.createFeeder2WTLegNodeWithPhaseShifter(graph, id, name, equipmentId, FeederWithSideNode.Side.valueOf(side.name()));
                }
            }
        }

        private void addFeeder3wtNode(VoltageLevelGraph graph,
                                      ThreeWindingsTransformer transformer,
                                      ThreeWindingsTransformer.Side side) {
            if (graph.isForVoltageLevelDiagram() && isNotInternalToVoltageLevel(transformer)) {
                // in a voltageLevel diagram we represent 3 windings transformers by a double feeder cell:
                //   - a transformer middle node at double feeder fork
                //   - a feeder for first other leg
                //   - a feeder for second other leg

                Map<FeederWithSideNode.Side, VoltageLevelInfos> voltageLevelInfosBySide
                        = Map.of(FeederWithSideNode.Side.ONE, createVoltageLevelInfos(transformer.getLeg1().getTerminal()),
                        FeederWithSideNode.Side.TWO, createVoltageLevelInfos(transformer.getLeg2().getTerminal()),
                        FeederWithSideNode.Side.THREE, createVoltageLevelInfos(transformer.getLeg3().getTerminal()));

                FeederWithSideNode.Side vlLegSide;
                FeederWithSideNode.Side firstOtherLegSide;
                FeederWithSideNode.Side secondOtherLegSide;
                switch (side) {
                    case ONE:
                        vlLegSide = FeederWithSideNode.Side.ONE;
                        firstOtherLegSide = FeederWithSideNode.Side.TWO;
                        secondOtherLegSide = FeederWithSideNode.Side.THREE;
                        break;
                    case TWO:
                        vlLegSide = FeederWithSideNode.Side.TWO;
                        firstOtherLegSide = FeederWithSideNode.Side.ONE;
                        secondOtherLegSide = FeederWithSideNode.Side.THREE;
                        break;
                    case THREE:
                        vlLegSide = FeederWithSideNode.Side.THREE;
                        firstOtherLegSide = FeederWithSideNode.Side.ONE;
                        secondOtherLegSide = FeederWithSideNode.Side.TWO;
                        break;
                    default:
                        throw new IllegalStateException();
                }

                // create first other leg feeder node
                String firstOtherLegNodeId = transformer.getId() + "_" + firstOtherLegSide.name();
                Feeder3WTLegNode firstOtherLegNode = NodeFactory.createFeeder3WTLegNodeForVoltageLevelDiagram(graph, firstOtherLegNodeId, transformer.getNameOrId(),
                        transformer.getId(), firstOtherLegSide, voltageLevelInfosBySide.get(firstOtherLegSide));

                // create second other leg feeder node
                String secondOtherLegNodeId = transformer.getId() + "_" + secondOtherLegSide.name();
                Feeder3WTLegNode secondOtherLegNode = NodeFactory.createFeeder3WTLegNodeForVoltageLevelDiagram(graph, secondOtherLegNodeId, transformer.getNameOrId(),
                        transformer.getId(), secondOtherLegSide, voltageLevelInfosBySide.get(secondOtherLegSide));

                // create middle node
                Middle3WTNode middleNode = NodeFactory.createMiddle3WTNode(graph, transformer.getId(), transformer.getNameOrId(),
                        vlLegSide, firstOtherLegNode, secondOtherLegNode,
                        voltageLevelInfosBySide.get(FeederWithSideNode.Side.ONE),
                        voltageLevelInfosBySide.get(FeederWithSideNode.Side.TWO),
                        voltageLevelInfosBySide.get(FeederWithSideNode.Side.THREE));

                add3wtFeeder(middleNode, firstOtherLegNode, secondOtherLegNode, transformer.getTerminal(side));
            } else {
                // in substation diagram, we only represent the leg node within the voltage level (3wt node will be on the snake line)
                String id = transformer.getId() + "_" + side.name();
                Feeder3WTLegNode legNode = NodeFactory.createFeeder3WTLegNodeForSubstationDiagram(graph, id, transformer.getNameOrId(), transformer.getId(),
                        FeederWithSideNode.Side.valueOf(side.name()));

                addFeeder(legNode, transformer.getTerminal(side));
            }
        }

        @Override
        public void visitLoad(Load load) {
            addFeeder(createFeederNode(graph, load), load.getTerminal());
        }

        @Override
        public void visitGenerator(Generator generator) {
            addFeeder(createFeederNode(graph, generator), generator.getTerminal());
        }

        @Override
        public void visitShuntCompensator(ShuntCompensator sc) {
            addFeeder(createFeederNode(graph, sc), sc.getTerminal());
        }

        @Override
        public void visitDanglingLine(DanglingLine danglingLine) {
            addFeeder(createFeederNode(graph, danglingLine), danglingLine.getTerminal());
        }

        @Override
        public void visitHvdcConverterStation(HvdcConverterStation<?> converterStation) {
            addFeeder(createFeederNode(graph, converterStation), converterStation.getTerminal());
        }

        @Override
        public void visitStaticVarCompensator(StaticVarCompensator staticVarCompensator) {
            addFeeder(createFeederNode(graph, staticVarCompensator), staticVarCompensator.getTerminal());
        }

        @Override
        public void visitTwoWindingsTransformer(TwoWindingsTransformer transformer,
                                                Branch.Side side) {
            addFeeder(createFeeder2wtNode(graph, transformer, side), transformer.getTerminal(side));
        }

        @Override
        public void visitLine(Line line, Branch.Side side) {
            addFeeder(createFeederLineNode(graph, line, side), line.getTerminal(side));
        }

        private static VoltageLevelInfos createVoltageLevelInfos(Terminal terminal) {
            VoltageLevel vl = terminal.getVoltageLevel();
            return new VoltageLevelInfos(vl.getId(), vl.getNameOrId(), vl.getNominalV());
        }

        @Override
        public void visitThreeWindingsTransformer(ThreeWindingsTransformer transformer,
                                                  ThreeWindingsTransformer.Side side) {
            addFeeder3wtNode(graph, transformer, side);
        }
    }

    private static class NodeBreakerGraphBuilder extends AbstractGraphBuilder {

        private final Map<Integer, Node> nodesByNumber;

        NodeBreakerGraphBuilder(VoltageLevelGraph graph, Map<Integer, Node> nodesByNumber) {
            super(graph);
            this.nodesByNumber = Objects.requireNonNull(nodesByNumber);
        }

        public ConnectablePosition.Feeder getFeeder(Terminal terminal) {
            Connectable<?> connectable = terminal.getConnectable();
            ConnectablePosition<?> position = (ConnectablePosition<?>) connectable.getExtension(ConnectablePosition.class);
            if (position == null) {
                return null;
            }
            if (connectable instanceof Injection) {
                return position.getFeeder();
            } else if (connectable instanceof Branch) {
                Branch<?> branch = (Branch<?>) connectable;
                if (branch.getTerminal1() == terminal) {
                    return position.getFeeder1();
                } else if (branch.getTerminal2() == terminal) {
                    return position.getFeeder2();
                } else {
                    throw new AssertionError();
                }
            } else if (connectable instanceof ThreeWindingsTransformer) {
                ThreeWindingsTransformer twt = (ThreeWindingsTransformer) connectable;
                if (twt.getLeg1().getTerminal() == terminal) {
                    return position.getFeeder1();
                } else if (twt.getLeg2().getTerminal() == terminal) {
                    return position.getFeeder2();
                } else if (twt.getLeg3().getTerminal() == terminal) {
                    return position.getFeeder3();
                } else {
                    throw new AssertionError();
                }
            } else {
                throw new AssertionError();
            }
        }

        protected void addFeeder(FeederNode node, Terminal terminal) {
            ConnectablePosition.Feeder feeder = getFeeder(terminal);
            if (feeder != null) {
                feeder.getOrder().ifPresent(node::setOrder);
                node.setLabel(feeder.getName());
                Direction dir = Direction.valueOf(feeder.getDirection().toString());
                node.setDirection(dir == UNDEFINED ? TOP : dir);
            }
            nodesByNumber.put(terminal.getNodeBreakerView().getNode(), node);
        }

        @Override
        protected void add3wtFeeder(Middle3WTNode middleNode, Feeder3WTLegNode firstOtherLegNode, Feeder3WTLegNode secondOtherLegNode, Terminal terminal) {
            ConnectablePosition.Feeder feeder = getFeeder(terminal);
            if (feeder != null) {
                middleNode.setDirection(Direction.valueOf(feeder.getDirection().toString()));
                feeder.getOrder().ifPresent(order -> {
                    firstOtherLegNode.setOrder(order);
                    secondOtherLegNode.setOrder(order + 1);
                });
                firstOtherLegNode.setLabel(feeder.getName());
                secondOtherLegNode.setLabel(feeder.getName());
            }

            nodesByNumber.put(terminal.getNodeBreakerView().getNode(), middleNode);
        }

        @Override
        public void visitBusbarSection(BusbarSection busbarSection) {
            BusbarSectionPosition extension = busbarSection.getExtension(BusbarSectionPosition.class);
            BusNode node = NodeFactory.createBusNode(graph, busbarSection.getId(), busbarSection.getNameOrId());
            if (extension != null) {
                node.setBusBarIndexSectionIndex(extension.getBusbarIndex(), extension.getSectionIndex());
            }
            nodesByNumber.put(busbarSection.getTerminal().getNodeBreakerView().getNode(), node);
        }
    }

    private static class BusBreakerGraphBuilder extends AbstractGraphBuilder {

        private final Map<String, Node> nodesByBusId;

        private int order = 1;

        BusBreakerGraphBuilder(VoltageLevelGraph graph, Map<String, Node> nodesByBusId) {
            super(graph);
            this.nodesByBusId = Objects.requireNonNull(nodesByBusId);
        }

        private void connectToBus(Node node, Terminal terminal) {
            String busId = terminal.getBusBreakerView().getConnectableBus().getId();
            graph.addEdge(nodesByBusId.get(busId), node);
        }

        protected void addFeeder(FeederNode node, Terminal terminal) {
            node.setOrder(order++);
            node.setDirection(order % 2 == 0 ? Direction.TOP : Direction.BOTTOM);
            connectToBus(node, terminal);
        }

        @Override
        protected void add3wtFeeder(Middle3WTNode middleNode, Feeder3WTLegNode firstOtherLegNode, Feeder3WTLegNode secondOtherLegNode, Terminal terminal) {
            Direction direction = order % 2 == 0 ? Direction.TOP : Direction.BOTTOM;

            firstOtherLegNode.setOrder(order++);
            firstOtherLegNode.setDirection(direction);

            secondOtherLegNode.setOrder(order++);
            secondOtherLegNode.setDirection(direction);

            connectToBus(middleNode, terminal);
        }
    }

    private void buildBusBreakerGraph(VoltageLevelGraph graph, VoltageLevel vl) {
        Map<String, Node> nodesByBusId = new HashMap<>();

        int v = 1;
        for (Bus b : vl.getBusBreakerView().getBuses()) {
            BusNode busNode = NodeFactory.createBusNode(graph, b.getId(), b.getNameOrId());
            nodesByBusId.put(b.getId(), busNode);
            busNode.setBusBarIndexSectionIndex(v++, 1);
        }

        // visit equipments
        vl.visitEquipments(new BusBreakerGraphBuilder(graph, nodesByBusId));

        // switches
        for (Switch sw : vl.getBusBreakerView().getSwitches()) {
            SwitchNode n = createSwitchNodeFromSwitch(graph, sw);

            Bus bus1 = vl.getBusBreakerView().getBus1(sw.getId());
            Bus bus2 = vl.getBusBreakerView().getBus2(sw.getId());
            graph.addEdge(nodesByBusId.get(bus1.getId()), n);
            graph.addEdge(n, nodesByBusId.get(bus2.getId()));
        }
    }

    private void buildNodeBreakerGraph(VoltageLevelGraph graph, VoltageLevel vl) {
        Map<Integer, Node> nodesByNumber = new HashMap<>();

        // visit equipments
        vl.visitEquipments(new NodeBreakerGraphBuilder(graph, nodesByNumber));

        // switches
        for (Switch sw : vl.getNodeBreakerView().getSwitches()) {
            SwitchNode n = createSwitchNodeFromSwitch(graph, sw);

            int node1 = vl.getNodeBreakerView().getNode1(sw.getId());
            int node2 = vl.getNodeBreakerView().getNode2(sw.getId());

            ensureNodeExists(graph, node1, nodesByNumber);
            ensureNodeExists(graph, node2, nodesByNumber);

            graph.addEdge(nodesByNumber.get(node1), n);
            graph.addEdge(n, nodesByNumber.get(node2));
        }

        // internal connections
        vl.getNodeBreakerView().getInternalConnectionStream().forEach(internalConnection -> {
            int node1 = internalConnection.getNode1();
            int node2 = internalConnection.getNode2();

            ensureNodeExists(graph, node1, nodesByNumber);
            ensureNodeExists(graph, node2, nodesByNumber);

            graph.addEdge(nodesByNumber.get(node1), nodesByNumber.get(node2));
        });
    }

    private void ensureNodeExists(VoltageLevelGraph graph, int n, Map<Integer, Node> nodesByNumber) {
        nodesByNumber.computeIfAbsent(n, k -> NodeFactory.createInternalNode(graph, k));
    }

    /**
     * Check if the graph is connected or not
     */
    private void handleConnectedComponents(VoltageLevelGraph graph) {
        List<Set<Node>> connectedSets = new ConnectivityInspector<>(graph.toJgrapht()).connectedSets();
        if (connectedSets.size() != 1) {
            LOGGER.warn("{} connected components found", connectedSets.size());
            connectedSets.stream()
                    .sorted(Comparator.comparingInt(Set::size))
                    .map(setNodes -> setNodes.stream().map(Node::getId).collect(Collectors.toSet()))
                    .forEach(strings -> LOGGER.warn("   - {}", strings));
        }
        connectedSets.forEach(s -> ensureOneBusInConnectedComponent(graph, s));
    }

    private void ensureOneBusInConnectedComponent(VoltageLevelGraph graph, Set<Node> nodes) {
        if (nodes.stream().anyMatch(node -> node.getType() == Node.NodeType.BUS)) {
            return;
        }
        FictitiousNode biggestFn = nodes.stream()
                .filter(node -> node.getType() == Node.NodeType.FICTITIOUS)
                .sorted(Comparator.<Node>comparingInt(node -> node.getAdjacentEdges().size())
                        .reversed()
                        .thenComparing(Node::getId)) // for stable fictitious node selection, also sort on id
                .map(FictitiousNode.class::cast)
                .findFirst()
                .orElseThrow(() -> new PowsyblException("Empty node set"));
        graph.substituteNode(biggestFn, NodeFactory.createFictitiousBusNode(graph, biggestFn.getId() + "FictitiousBus"));
    }

    /**
     * Discover and apply postprocessor plugins to add custom nodes
     **/
    private void handleGraphPostProcessors(VoltageLevelGraph graph) {
        List<GraphBuildPostProcessor> listPostProcessors = POST_PROCESSOR_LOADER.getServices();
        for (GraphBuildPostProcessor gbp : listPostProcessors) {
            LOGGER.info("Graph post-processor id '{}' : Adding custom node in graph '{}'",
                    gbp.getId(), graph.getVoltageLevelInfos().getId());
            gbp.addNode(graph, network);
        }
    }

    private void addLineEdges(Graph graph, List<Line> lines) {
        Set<String> linesIds = new HashSet<>();
        lines.forEach(line -> {
            if (!linesIds.contains(line.getId())) {
                Terminal t1 = line.getTerminal1();
                Terminal t2 = line.getTerminal2();

                VoltageLevel vl1 = t1.getVoltageLevel();
                VoltageLevel vl2 = t2.getVoltageLevel();

                VoltageLevelGraph g1 = graph.getVoltageLevel(vl1.getId());
                VoltageLevelGraph g2 = graph.getVoltageLevel(vl2.getId());

                Node n1 = g1.getNode(line.getId() + "_" + line.getSide(t1).name());
                Node n2 = g2.getNode(line.getId() + "_" + line.getSide(t2).name());
                graph.addLineEdge(line.getId(), n1, n2);
                linesIds.add(line.getId());
            }
        });
    }

    private void add2wtEdges(BaseGraph graph, List<TwoWindingsTransformer> twoWindingsTransformers) {
        twoWindingsTransformers.forEach(transfo -> {
            Terminal t1 = transfo.getTerminal1();
            Terminal t2 = transfo.getTerminal2();

            String id1 = transfo.getId() + "_" + transfo.getSide(t1).name();
            String id2 = transfo.getId() + "_" + transfo.getSide(t2).name();

            VoltageLevel vl1 = t1.getVoltageLevel();
            VoltageLevel vl2 = t2.getVoltageLevel();

            VoltageLevelGraph g1 = graph.getVoltageLevel(vl1.getId());
            VoltageLevelGraph g2 = graph.getVoltageLevel(vl2.getId());

            Node n1 = g1.getNode(id1);
            Node n2 = g2.getNode(id2);

            // creation of the middle node and the edges linking the transformer leg nodes to this middle node
            VoltageLevelInfos voltageLevelInfos1 = new VoltageLevelInfos(vl1.getId(), vl1.getNameOrId(), vl1.getNominalV());
            VoltageLevelInfos voltageLevelInfos2 = new VoltageLevelInfos(vl2.getId(), vl2.getNameOrId(), vl2.getNominalV());

            NodeFactory.createMiddle2WTNode(graph, transfo.getId(), transfo.getNameOrId(),
                    (Feeder2WTLegNode) n1, (Feeder2WTLegNode) n2, voltageLevelInfos1, voltageLevelInfos2,
                    transfo.hasPhaseTapChanger());
        });
    }

    private void add3wtEdges(BaseGraph graph, List<ThreeWindingsTransformer> threeWindingsTransformers) {
        threeWindingsTransformers.forEach(transfo -> {
            List<Feeder3WTLegNode> feederNodes = transfo.getLegStream().map(leg -> {
                String vlId = leg.getTerminal().getVoltageLevel().getId();
                String idLeg = transfo.getId() + "_" + transfo.getSide(leg.getTerminal()).name();
                return (Feeder3WTLegNode) graph.getVoltageLevel(vlId).getNode(idLeg);
            }).collect(Collectors.toList());

            NodeFactory.createMiddle3WTNode(graph, transfo.getId(), transfo.getNameOrId(),
                feederNodes.get(0), feederNodes.get(1), feederNodes.get(2));
        });
    }

    private SwitchNode createSwitchNodeFromSwitch(VoltageLevelGraph graph, Switch aSwitch) {
        Objects.requireNonNull(graph);
        Objects.requireNonNull(aSwitch);
        String componentType;
        switch (aSwitch.getKind()) {
            case BREAKER:
                componentType = BREAKER;
                break;
            case DISCONNECTOR:
                componentType = DISCONNECTOR;
                break;
            case LOAD_BREAK_SWITCH:
                componentType = LOAD_BREAK_SWITCH;
                break;
            default:
                throw new AssertionError();
        }
        SwitchNode.SwitchKind sk = SwitchNode.SwitchKind.valueOf(aSwitch.getKind().name());
        return NodeFactory.createSwitchNode(graph, aSwitch.getId(), aSwitch.getNameOrId(), componentType, aSwitch.isFictitious(), sk, aSwitch.isOpen());
    }

    @Override
    public ZoneGraph buildZoneGraph(List<String> substationIds) {
        Objects.requireNonNull(substationIds);

        List<Substation> zone = substationIds.stream().map(substationId -> {
            Substation substation = network.getSubstation(substationId);
            if (substation == null) {
                throw new PowsyblException("Substation '" + substationId + "' not in network " + network.getId());
            }
            return substation;
        }).collect(Collectors.toList());

        ZoneGraph graph = ZoneGraph.create(substationIds);
        buildZoneGraph(graph, zone);

        return graph;
    }

    private void buildZoneGraph(ZoneGraph zoneGraph, List<Substation> zone) {
        if (zone.isEmpty()) {
            LOGGER.warn("No substations in the zone: skipping graph building");
            return;
        }
        // add nodes -> substation graphs
        GraphBuilder graphBuilder = new NetworkGraphBuilder(network);
        zone.forEach(substation -> {
            LOGGER.info("Adding substation {} to zone graph", substation.getId());
            SubstationGraph sGraph = graphBuilder.buildSubstationGraph(substation.getId(), zoneGraph);
            zoneGraph.addSubstation(sGraph);
        });
        // Add snake edges between different substations in the same zone
        addLineEdges(zoneGraph, zone.stream().flatMap(Substation::getVoltageLevelStream)
                .flatMap(voltageLevel -> voltageLevel.getConnectableStream(Line.class))
                .filter(NetworkGraphBuilder::isNotInternalToSubstation)
                .collect(Collectors.toList()));
    }
}
