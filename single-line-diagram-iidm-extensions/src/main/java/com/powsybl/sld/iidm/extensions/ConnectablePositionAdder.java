/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sld.iidm.extensions;

import com.powsybl.commons.extensions.ExtensionAdder;
import com.powsybl.iidm.network.Connectable;
import com.powsybl.sld.iidm.extensions.ConnectablePosition.Direction;

/**
 * @author Jon Harper <jon.harper at rte-france.com>
 */
public interface ConnectablePositionAdder<C extends Connectable<C>>
        extends ExtensionAdder<C, ConnectablePosition<C>> {

    default Class<ConnectablePosition> getExtensionClass() {
        return ConnectablePosition.class;
    }

    interface InfoAdder<C extends Connectable<C>> {

        public InfoAdder<C> withName(String name);

        public InfoAdder<C> withOrder(int order);

        public InfoAdder<C> withDirection(Direction direction);

        public ConnectablePositionAdder<C> add();

    }

    InfoAdder<C> newInfo();

    InfoAdder<C> newInfo1();

    InfoAdder<C> newInfo2();

    InfoAdder<C> newInfo3();

}
