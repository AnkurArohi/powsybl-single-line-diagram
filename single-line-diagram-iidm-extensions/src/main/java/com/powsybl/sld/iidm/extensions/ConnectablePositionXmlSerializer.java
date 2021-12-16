/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sld.iidm.extensions;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.ExtensionXmlSerializer;
import com.powsybl.commons.xml.XmlReaderContext;
import com.powsybl.commons.xml.XmlUtil;
import com.powsybl.commons.xml.XmlWriterContext;
import com.powsybl.iidm.network.Connectable;

import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@AutoService(ExtensionXmlSerializer.class)
public class ConnectablePositionXmlSerializer<C extends Connectable<C>> implements ExtensionXmlSerializer<C, ConnectablePosition<C>> {

    @Override
    public String getExtensionName() {
        return  ConnectablePosition.NAME;
    }

    @Override
    public String getCategoryName() {
        return "network";
    }

    @Override
    public Class<? super ConnectablePosition> getExtensionClass() {
        return ConnectablePosition.class;
    }

    @Override
    public boolean hasSubElements() {
        return true;
    }

    @Override
    public InputStream getXsdAsStream() {
        return getClass().getResourceAsStream("/xsd/connectablePosition.xsd");
    }

    @Override
    public String getNamespaceUri() {
        return "http://www.itesla_project.eu/schema/iidm/ext/connectable_position/1_0";
    }

    @Override
    public String getNamespacePrefix() {
        return "cp";
    }

    private void writePosition(ConnectablePosition.Info info, Integer i, XmlWriterContext context) throws XMLStreamException {
        context.getExtensionsWriter().writeEmptyElement(getNamespaceUri(), "info" + (i != null ? i : ""));
        context.getExtensionsWriter().writeAttribute("name", info.getName());
        Optional<Integer> oOrder = info.getOrder();
        if (oOrder.isPresent()) {
            XmlUtil.writeInt("order", oOrder.get(), context.getExtensionsWriter());
        }
        context.getExtensionsWriter().writeAttribute("direction", info.getDirection().name());
    }

    @Override
    public void write(ConnectablePosition connectablePosition, XmlWriterContext context) throws XMLStreamException {
        if (connectablePosition.getInfo() != null) {
            writePosition(connectablePosition.getInfo(), null, context);
        }
        if (connectablePosition.getInfo1() != null) {
            writePosition(connectablePosition.getInfo1(), 1, context);
        }
        if (connectablePosition.getInfo2() != null) {
            writePosition(connectablePosition.getInfo2(), 2, context);
        }
        if (connectablePosition.getInfo3() != null) {
            writePosition(connectablePosition.getInfo3(), 3, context);
        }
    }

    private void readPosition(XmlReaderContext context, ConnectablePositionAdder.InfoAdder adder) {
        String name = context.getReader().getAttributeValue(null, "name");
        Optional.ofNullable(XmlUtil.readOptionalIntegerAttribute(context.getReader(), "order")).
                ifPresent(adder::withOrder);
        ConnectablePosition.Direction direction = ConnectablePosition.Direction.valueOf(context.getReader().getAttributeValue(null, "direction"));
        adder.withName(name).withDirection(direction).add();
    }

    @Override
    public ConnectablePosition read(Connectable connectable, XmlReaderContext context) throws XMLStreamException {
        ConnectablePositionAdder adder = ((Connectable<?>) connectable).newExtension(ConnectablePositionAdder.class);
        XmlUtil.readUntilEndElement(getExtensionName(), context.getReader(), () -> {

            switch (context.getReader().getLocalName()) {
                case "info":
                    readPosition(context, adder.newInfo());
                    break;

                case "info1":
                    readPosition(context, adder.newInfo1());
                    break;

                case "info2":
                    readPosition(context, adder.newInfo2());
                    break;

                case "info3":
                    readPosition(context, adder.newInfo3());
                    break;

                default:
                    throw new AssertionError();
            }
        });
        adder.add();
        return ((Connectable<?>) connectable).getExtension(ConnectablePosition.class);
    }
}
