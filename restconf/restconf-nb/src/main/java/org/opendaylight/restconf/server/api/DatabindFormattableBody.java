/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;

/**
 * A {@link FormattableBody} which has an attached {@link DatabindContext}.
 */
@NonNullByDefault
public abstract class DatabindFormattableBody extends FormattableBody {
    private final DatabindContext databind;

    protected DatabindFormattableBody(final DatabindContext databind) {
        this.databind = requireNonNull(databind);
    }

    @Override
    public final void formatToJSON(final PrettyPrintParam prettyPrint, final OutputStream out) throws IOException {
        formatToJSON(databind, prettyPrint, out);
    }

    protected abstract void formatToJSON(DatabindContext databind, PrettyPrintParam prettyPrint, OutputStream out)
        throws IOException;

    @Override
    public final void formatToXML(final PrettyPrintParam prettyPrint, final OutputStream out) throws IOException {
        formatToXML(databind, prettyPrint, out);
    }

    protected abstract void formatToXML(DatabindContext databind, PrettyPrintParam prettyPrint, OutputStream out)
        throws IOException;
}