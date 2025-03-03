/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sld.iidm;

import com.powsybl.commons.extensions.Extendable;
import com.powsybl.iidm.network.*;
import com.powsybl.sld.AbstractTestCase;
import com.powsybl.sld.builders.GraphBuilder;
import com.powsybl.sld.iidm.extensions.BusbarSectionPositionAdder;
import com.powsybl.sld.iidm.extensions.ConnectablePosition;
import com.powsybl.sld.iidm.extensions.ConnectablePositionAdder;
import com.powsybl.sld.layout.HorizontalSubstationLayoutFactory;
import com.powsybl.sld.layout.SmartVoltageLevelLayoutFactory;
import com.powsybl.sld.model.graphs.*;
import com.powsybl.sld.svg.BasicStyleProvider;
import com.powsybl.sld.svg.DefaultDiagramLabelProvider;
import com.powsybl.sld.svg.DiagramLabelProvider;
import com.powsybl.sld.svg.DiagramStyleProvider;

/**
 * @author Benoit Jeanson <benoit.jeanson at rte-france.com>
 * @author Nicolas Duchene
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public abstract class AbstractTestCaseIidm extends AbstractTestCase {

    protected Network network;
    protected VoltageLevel vl;
    protected Substation substation;
    protected GraphBuilder graphBuilder;

    @Override
    public String toSVG(Graph g, String filename) {
        return toSVG(g, filename, getDefaultDiagramLabelProvider(), getDefaultDiagramStyleProvider());
    }

    protected DiagramLabelProvider getDefaultDiagramLabelProvider() {
        return new DefaultDiagramLabelProvider(network, componentLibrary, layoutParameters);
    }

    protected DiagramStyleProvider getDefaultDiagramStyleProvider() {
        return new BasicStyleProvider();
    }

    protected static Substation createSubstation(Network n, String id, String name, Country country) {
        return n.newSubstation()
                .setId(id)
                .setName(name)
                .setCountry(country)
                .add();
    }

    protected static VoltageLevel createVoltageLevel(Substation s, String id, String name,
                                                     TopologyKind topology, double vNom, int nodeCount) {
        VoltageLevel vl = s.newVoltageLevel()
                .setId(id)
                .setName(name)
                .setTopologyKind(topology)
                .setNominalV(vNom)
                .add();
        return vl;
    }

    protected static void createSwitch(VoltageLevel vl, String id, String name, SwitchKind kind, boolean retained, boolean open, boolean fictitious, int node1, int node2) {
        vl.getNodeBreakerView().newSwitch()
                .setId(id)
                .setName(name)
                .setKind(kind)
                .setRetained(retained)
                .setOpen(open)
                .setFictitious(fictitious)
                .setNode1(node1)
                .setNode2(node2)
                .add();
    }

    protected static void createInternalConnection(VoltageLevel vl, int node1, int node2) {
        vl.getNodeBreakerView().newInternalConnection()
                .setNode1(node1)
                .setNode2(node2)
                .add();
    }

    protected static void createBusBarSection(VoltageLevel vl, String id, String name, int node, int busbarIndex, int sectionIndex) {
        BusbarSection bbs = vl.getNodeBreakerView().newBusbarSection()
                .setId(id)
                .setName(name)
                .setNode(node)
                .add();
        bbs.newExtension(BusbarSectionPositionAdder.class)
                .withBusbarIndex(busbarIndex)
                .withSectionIndex(sectionIndex)
                .add();
    }

    protected static void createLoad(VoltageLevel vl, String id, String name, String feederName, Integer feederOrder,
                                     ConnectablePosition.Direction direction, int node, double p0, double q0) {
        Load load = vl.newLoad()
                .setId(id)
                .setName(name)
                .setNode(node)
                .setP0(p0)
                .setQ0(q0)
                .add();
        addFeederPosition(load, feederName, feederOrder, direction);
    }

    protected static void createGenerator(VoltageLevel vl, String id, String name, String feederName, Integer feederOrder,
                                          ConnectablePosition.Direction direction, int node,
                                          double minP, double maxP, boolean voltageRegulator,
                                          double targetP, double targetQ) {
        Generator gen = vl.newGenerator()
                .setId(id)
                .setName(name)
                .setNode(node)
                .setMinP(minP)
                .setMaxP(maxP)
                .setVoltageRegulatorOn(voltageRegulator)
                .setTargetP(targetP)
                .setTargetQ(targetQ)
                .add();
        addFeederPosition(gen, feederName, feederOrder, direction);
    }

    protected static void createShunt(VoltageLevel vl, String id, String name, String feederName, Integer feederOrder,
                                      ConnectablePosition.Direction direction, int node,
                                      double bPerSection, int maximumSectionCount, int currentSectionCount) {
        ShuntCompensator shunt = vl.newShuntCompensator()
                .setId(id)
                .setName(name)
                .setNode(node)
                .setSectionCount(currentSectionCount)
                .newLinearModel()
                    .setBPerSection(bPerSection)
                    .setMaximumSectionCount(maximumSectionCount)
                .add()
                .add();
        addFeederPosition(shunt, feederName, feederOrder, direction);
    }

    protected static void createTwoWindingsTransformer(Substation s, String id, String name,
                                                       double r, double x, double g, double b,
                                                       double ratedU1, double ratedU2,
                                                       int node1, int node2,
                                                       String idVoltageLevel1, String idVoltageLevel2,
                                                       String feederName1, Integer feederOrder1, ConnectablePosition.Direction direction1,
                                                       String feederName2, Integer feederOrder2, ConnectablePosition.Direction direction2) {
        TwoWindingsTransformer t = s.newTwoWindingsTransformer()
                .setId(id)
                .setName(name)
                .setR(r)
                .setX(x)
                .setG(g)
                .setB(b)
                .setRatedU1(ratedU1)
                .setRatedU2(ratedU2)
                .setNode1(node1)
                .setVoltageLevel1(idVoltageLevel1)
                .setNode2(node2)
                .setVoltageLevel2(idVoltageLevel2)
                .add();
        addTwoFeedersPosition(t, feederName1, feederOrder1, direction1, feederName2, feederOrder2, direction2);
    }

    protected static void createThreeWindingsTransformer(Substation s, String id, String name,
                                                         String vl1, String vl2, String vl3,
                                                         double r1, double r2, double r3,
                                                         double x1, double x2, double x3,
                                                         double g1, double b1,
                                                         double ratedU1, double ratedU2, double ratedU3,
                                                         int node1, int node2, int node3,
                                                         String feederName1, Integer feederOrder1, ConnectablePosition.Direction direction1,
                                                         String feederName2, Integer feederOrder2, ConnectablePosition.Direction direction2,
                                                         String feederName3, Integer feederOrder3, ConnectablePosition.Direction direction3) {
        ThreeWindingsTransformer t = s.newThreeWindingsTransformer()
                .setId(id)
                .setName(name)
                .newLeg1()
                .setR(r1)
                .setX(x1)
                .setG(g1)
                .setB(b1)
                .setRatedU(ratedU1)
                .setVoltageLevel(vl1)
                .setNode(node1)
                .add()
                .newLeg2()
                .setR(r2)
                .setX(x2)
                .setRatedU(ratedU2)
                .setVoltageLevel(vl2)
                .setNode(node2)
                .add()
                .newLeg3()
                .setR(r3)
                .setX(x3)
                .setRatedU(ratedU3)
                .setVoltageLevel(vl3)
                .setNode(node3)
                .add()
                .add();

        addThreeFeedersPosition(t, feederName1, feederOrder1, direction1, feederName2, feederOrder2, direction2, feederName3, feederOrder3, direction3);
    }

    protected static void createLine(Network network,
                                     String id, String name,
                                     double r, double x,
                                     double g1, double b1,
                                     double g2, double b2,
                                     int node1, int node2,
                                     String idVoltageLevel1, String idVoltageLevel2,
                                     String feederName1, Integer feederOrder1, ConnectablePosition.Direction direction1,
                                     String feederName2, Integer feederOrder2, ConnectablePosition.Direction direction2) {
        Line line = network.newLine()
                .setId(id)
                .setName(name)
                .setR(r)
                .setX(x)
                .setG1(g1)
                .setB1(b1)
                .setG2(g2)
                .setB2(b2)
                .setNode1(node1)
                .setVoltageLevel1(idVoltageLevel1)
                .setNode2(node2)
                .setVoltageLevel2(idVoltageLevel2)
                .add();
        addTwoFeedersPosition(line, feederName1, feederOrder1, direction1, feederName2, feederOrder2, direction2);
    }

    private static void addFeederPosition(Extendable<?> extendable, String feederName, Integer feederOrder, ConnectablePosition.Direction direction) {
        ConnectablePositionAdder.FeederAdder feederAdder = extendable.newExtension(ConnectablePositionAdder.class).newFeeder();
        if (feederOrder != null) {
            feederAdder.withOrder(feederOrder);
        }
        feederAdder.withDirection(direction).withName(feederName).add()
                .add();
    }

    private static void addTwoFeedersPosition(Extendable<?> extendable,
                                              String feederName1, Integer feederOrder1, ConnectablePosition.Direction direction1,
                                              String feederName2, Integer feederOrder2, ConnectablePosition.Direction direction2) {
        ConnectablePositionAdder extensionAdder = extendable.newExtension(ConnectablePositionAdder.class);
        ConnectablePositionAdder.FeederAdder feederAdder1 = extensionAdder.newFeeder1();
        if (feederOrder1 != null) {
            feederAdder1.withOrder(feederOrder1);
        }
        feederAdder1.withName(feederName1).withDirection(direction1).add();
        ConnectablePositionAdder.FeederAdder feederAdder2 = extensionAdder.newFeeder2();
        if (feederOrder2 != null) {
            feederAdder2.withOrder(feederOrder2);
        }
        feederAdder2.withName(feederName2).withDirection(direction2).add();
        extensionAdder.add();
    }

    private static void addThreeFeedersPosition(Extendable<?> extendable,
                                                String feederName1, Integer feederOrder1, ConnectablePosition.Direction direction1,
                                                String feederName2, Integer feederOrder2, ConnectablePosition.Direction direction2,
                                                String feederName3, Integer feederOrder3, ConnectablePosition.Direction direction3) {
        ConnectablePositionAdder extensionAdder = extendable.newExtension(ConnectablePositionAdder.class);
        ConnectablePositionAdder.FeederAdder feederAdder1 = extensionAdder.newFeeder1();
        if (feederOrder1 != null) {
            feederAdder1.withOrder(feederOrder1);
        }
        feederAdder1.withName(feederName1).withDirection(direction1).add();
        ConnectablePositionAdder.FeederAdder feederAdder2 = extensionAdder.newFeeder2();
        if (feederOrder2 != null) {
            feederAdder2.withOrder(feederOrder2);
        }
        feederAdder2.withName(feederName2).withDirection(direction2).add();
        ConnectablePositionAdder.FeederAdder feederAdder3 = extensionAdder.newFeeder3();
        if (feederOrder3 != null) {
            feederAdder3.withOrder(feederOrder3);
        }
        feederAdder3.withName(feederName3).withDirection(direction3).add();
        extensionAdder.add();
    }

    @Override
    protected void voltageLevelGraphLayout(VoltageLevelGraph voltageLevelGraph) {
        new SmartVoltageLevelLayoutFactory(network).create(voltageLevelGraph).run(layoutParameters);
    }

    @Override
    protected void substationGraphLayout(SubstationGraph substationGraph) {
        new HorizontalSubstationLayoutFactory().create(substationGraph, new SmartVoltageLevelLayoutFactory(network)).run(layoutParameters);
    }
}
