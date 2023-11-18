/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@ExtendWith(MockitoExtension.class)
class RestconfOperationsPostTest extends AbstractRestconfTest {
    private static final URI RESTCONF_URI = URI.create("/restconf");
    private static final QName RPC = QName.create("invoke:rpc:module", "2013-12-03", "rpc-test");
    private static final ContainerNode INPUT = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(RPC, "input")))
        .withChild(ImmutableNodes.leafNode(QName.create(RPC, "content"), "test"))
        .build();
    private static final ContainerNode OUTPUT = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(RPC, "output")))
        .withChild(ImmutableNodes.leafNode(QName.create(RPC, "content"), "operation result"))
        .build();
    private static final EffectiveModelContext MODEL_CONTEXT =
        YangParserTestUtils.parseYangResourceDirectory("/invoke-rpc");
    private static final OperationInput OPER_INPUT = new OperationInput(DatabindContext.ofModel(MODEL_CONTEXT),
            SchemaInferenceStack.of(MODEL_CONTEXT, Absolute.of(RPC)).toInference(), INPUT);

    @Mock
    private DOMNotificationService notificationService;

    @Override
    EffectiveModelContext modelContext() {
        return MODEL_CONTEXT;
    }

    @Test
    void testInvokeRpcWithNonEmptyOutput() {
        final var result = mock(ContainerNode.class);
        doReturn(false).when(result).isEmpty();

        prepNNC(result);
        final var ar = mock(AsyncResponse.class);
        final var captor = ArgumentCaptor.forClass(Response.class);
        restconf.operationsXmlPOST("invoke-rpc-module:rpc-test", stringInputStream("""
            <input xmlns="invoke:rpc:module"/>
            """), mock(UriInfo.class), ar);
        verify(ar).resume(captor.capture());

        final var response = captor.getValue();
        assertEquals(200, response.getStatus());
        final var entity = (NormalizedNodePayload) response.getEntity();
        assertSame(result, entity.data());
    }

    @Test
    void testInvokeRpcWithEmptyOutput() {
        final var result = mock(ContainerNode.class);
        doReturn(true).when(result).isEmpty();

        prepNNC(result);
        final var ar = mock(AsyncResponse.class);
        final var response = ArgumentCaptor.forClass(Response.class);
        restconf.operationsJsonPOST("invoke-rpc-module:rpc-test", stringInputStream("""
            {
              "invoke-rpc-module:input" : {
              }
            }
            """), mock(UriInfo.class), ar);
        verify(ar).resume(response.capture());

        assertEquals(204, response.getValue().getStatus());
    }

    @Test
    void invokeRpcTest() throws Exception {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(OUTPUT, List.of()))).when(rpcService)
            .invokeRpc(RPC, INPUT);
        assertEquals(OUTPUT,
            Futures.getDone(server.getRestconfStrategy(MODEL_CONTEXT, null).invokeRpc(RESTCONF_URI, RPC, OPER_INPUT))
            .output());
    }

    @Test
    void invokeRpcErrorsAndCheckTestTest() throws Exception {
        final var errorRpc = QName.create(RPC, "error-rpc");
        final var exception = new DOMRpcImplementationNotAvailableException(
                "No implementation of RPC " + errorRpc + " available.");
        doReturn(Futures.immediateFailedFuture(exception)).when(rpcService).invokeRpc(errorRpc, INPUT);
        final var ex = assertInstanceOf(RestconfDocumentedException.class,
            assertThrows(ExecutionException.class,
                () -> Futures.getDone(server.getRestconfStrategy(MODEL_CONTEXT, null)
                    .invokeRpc(RESTCONF_URI, errorRpc, OPER_INPUT))).getCause());
        final var errorList = ex.getErrors();
        assertEquals(1, errorList.size());
        final var actual = errorList.iterator().next();
        assertEquals("No implementation of RPC " + errorRpc + " available.", actual.getErrorMessage());
        assertEquals(ErrorType.RPC, actual.getErrorType());
        assertEquals(ErrorTag.OPERATION_FAILED, actual.getErrorTag());
    }

    @Test
    void invokeRpcViaMountPointTest() throws Exception {
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(OUTPUT, List.of()))).when(rpcService)
            .invokeRpc(RPC, INPUT);
        assertEquals(OUTPUT,
            Futures.getDone(
                server.getRestconfStrategy(MODEL_CONTEXT, mountPoint).invokeRpc(RESTCONF_URI, RPC, OPER_INPUT))
            .output());
    }

    @Test
    void invokeRpcMissingMountPointServiceTest() {
        doReturn(Optional.empty()).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        final var strategy = server.getRestconfStrategy(MODEL_CONTEXT, mountPoint);
        final var ex = assertInstanceOf(RestconfDocumentedException.class,
            assertThrows(ExecutionException.class,
                () -> Futures.getDone(strategy.invokeRpc(RESTCONF_URI, RPC, OPER_INPUT))).getCause());
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.OPERATION_NOT_SUPPORTED, error.getErrorTag());
        assertEquals("RPC invocation is not available", error.getErrorMessage());
    }

    @Test
    void checkResponseTest() throws Exception {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(OUTPUT, List.of())))
            .when(rpcService).invokeRpc(RPC, INPUT);
        assertEquals(OUTPUT,
            Futures.getDone(server.getRestconfStrategy(MODEL_CONTEXT, null).invokeRpc(RESTCONF_URI, RPC, OPER_INPUT))
            .output());
    }

    private void prepNNC(final ContainerNode result) {
        final var qname = QName.create("invoke:rpc:module", "2013-12-03", "rpc-test");
        final var domRpcResult = mock(DOMRpcResult.class);
        doReturn(Futures.immediateFuture(domRpcResult)).when(rpcService).invokeRpc(eq(qname), any(ContainerNode.class));
        doReturn(result).when(domRpcResult).value();
    }
}