/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.util.concurrent.EventExecutor;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.callhome.protocol.CallHomeChannelActivator;
import org.opendaylight.netconf.callhome.protocol.CallHomeProtocolSessionContext;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

public class CallHomeMountFactoryTest {
    private String topologyId;
    private EventExecutor mockExecutor;
    private ScheduledThreadPool mockKeepAlive;
    private ThreadPool mockProcessingExecutor;
    private SchemaResourceManager mockSchemaRepoProvider;

    private CallHomeMountFactory instance;
    private DataBroker mockDataBroker;
    private DOMMountPointService mockMount;

    private CallHomeMountSessionManager mockSessMgr;
    private CallHomeTopology mockTopology;
    private CallHomeProtocolSessionContext mockProtoSess;
    private NetconfClientConfigurationBuilderFactory mockBuilderFactory;
    private BaseNetconfSchemas mockBaseSchemas;

    @Before
    public void setup() {
        topologyId = "";
        mockExecutor = mock(EventExecutor.class);
        mockKeepAlive = mock(ScheduledThreadPool.class);
        mockProcessingExecutor = mock(ThreadPool.class);
        mockSchemaRepoProvider = mock(SchemaResourceManager.class);
        mockDataBroker = mock(DataBroker.class);
        mockMount = mock(DOMMountPointService.class);
        mockSessMgr = mock(CallHomeMountSessionManager.class);
        mockTopology = mock(CallHomeTopology.class);
        mockProtoSess = mock(CallHomeProtocolSessionContext.class);
        mockBuilderFactory = mock(NetconfClientConfigurationBuilderFactory .class);
        mockBaseSchemas = mock(BaseNetconfSchemas.class);

        instance = new CallHomeMountFactory(topologyId, mockExecutor, mockKeepAlive,
                mockProcessingExecutor, mockSchemaRepoProvider, mockBaseSchemas, mockDataBroker, mockMount,
                mockBuilderFactory) {
            @Override
            CallHomeMountSessionManager sessionManager() {
                return mockSessMgr;
            }

            @Override
            void createTopology() {
                topology = mockTopology;
            }
        };
    }

    NetconfClientConfiguration someConfiguration(final InetSocketAddress address) {
        // NetconfClientConfiguration has mostly final methods, making it un-mock-able

        final NetconfClientConfiguration.NetconfClientProtocol protocol =
                NetconfClientConfiguration.NetconfClientProtocol.SSH;
        final NetconfHelloMessageAdditionalHeader additionalHeader = mock(NetconfHelloMessageAdditionalHeader.class);
        final NetconfClientSessionListener sessionListener = mock(NetconfClientSessionListener.class);
        final AuthenticationHandler authHandler = mock(AuthenticationHandler.class);

        return NetconfClientConfigurationBuilder.create().withProtocol(protocol).withAddress(address)
                .withConnectionTimeoutMillis(0).withAdditionalHeader(additionalHeader)
                .withSessionListener(sessionListener).withAuthHandler(authHandler).build();
    }

    @Test
    public void canCreateASessionFromAConfiguration() {
        // given
        final CallHomeMountSessionContext mockContext = mock(CallHomeMountSessionContext.class);
        final InetSocketAddress someAddress = InetSocketAddress.createUnresolved("1.2.3.4", 123);
        doReturn(mockContext).when(mockSessMgr).getByAddress(eq(someAddress));

        final NetconfClientConfiguration someCfg = someConfiguration(someAddress);
        // when
        instance.createClient(someCfg);
        // then
        verify(mockContext, times(1)).activateNetconfChannel(any(NetconfClientSessionListener.class));
    }

    @Test
    public void noSessionIsCreatedWithoutAContextAvailableForAGivenAddress() throws Exception {
        // given
        final InetSocketAddress someAddress = InetSocketAddress.createUnresolved("1.2.3.4", 123);
        final NetconfClientConfiguration someCfg = someConfiguration(someAddress);
        // when
        final var future = instance.createClient(someCfg);
        // then
        assertNotNull(future);
        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    public void nodeIsInsertedIntoTopologyWhenSubsystemIsOpened() throws UnknownHostException {
        // given
        final NodeId mockNodeId = mock(NodeId.class);
        final Node mockNode = mock(Node.class);
        final CallHomeMountSessionContext mockDevCtxt = mock(CallHomeMountSessionContext.class);
        doReturn(mockNodeId).when(mockDevCtxt).getId();
        doReturn(mockNode).when(mockDevCtxt).getConfigNode();
        doReturn(mockDevCtxt).when(mockSessMgr).createSession(any(CallHomeProtocolSessionContext.class),
                any(CallHomeChannelActivator.class), any(CallHomeMountSessionContext.CloseCallback.class));
        final CallHomeChannelActivator activator = mock(CallHomeChannelActivator.class);
        instance.createTopology();
        // when
        instance.onNetconfSubsystemOpened(mockProtoSess, activator);
        // then
        verify(instance.topology, times(1)).connectNode(any(Node.class));
    }
}