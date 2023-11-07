/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.errors.SettableRestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.URLConstants;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.RestconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.Streams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.stream.Access;
import org.opendaylight.yang.gen.v1.urn.opendaylight.device.notification.rev221106.SubscribeDeviceNotificationInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.device.notification.rev221106.SubscribeDeviceNotificationOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateNotificationStreamInput;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev231103.CreateDataChangeEventSubscriptionInput1;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev231103.NotificationOutputTypeGrouping;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev231103.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.NotificationEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This singleton class is responsible for creation, removal and searching for {@link DataTreeChangeStream} or
 * {@link NotificationStream} listeners.
 */
// FIXME: furthermore, this should be tied to ietf-restconf-monitoring, as the Strings used in its maps are stream
//        names. We essentially need a component which deals with allocation of stream names and their lifecycle and
//        the contents of /restconf-state/streams.
public abstract sealed class ListenersBroker {
    /**
     * A ListenersBroker working with Server-Sent Events.
     */
    public static final class ServerSentEvents extends ListenersBroker {
        public ServerSentEvents(final DOMDataBroker dataBroker) {
            super(dataBroker);
        }

        @Override
        public String baseStreamLocation(final UriInfo uriInfo) {
            return uriInfo.getBaseUriBuilder()
                .replacePath(URLConstants.BASE_PATH + '/' + URLConstants.STREAMS_SUBPATH)
                .build()
                .toString();
        }
    }

    /**
     * A ListenersBroker working with WebSockets.
     */
    public static final class WebSockets extends ListenersBroker {
        public WebSockets(final DOMDataBroker dataBroker) {
            super(dataBroker);
        }

        @Override
        public String baseStreamLocation(final UriInfo uriInfo) {
            final var scheme = switch (uriInfo.getAbsolutePath().getScheme()) {
                // Secured HTTP goes to Secured WebSockets
                case "https" -> "wss";
                // Unsecured HTTP and others go to unsecured WebSockets
                default -> "ws";
            };

            return uriInfo.getBaseUriBuilder()
                .scheme(scheme)
                .replacePath(URLConstants.BASE_PATH + '/' + URLConstants.STREAMS_SUBPATH)
                .build()
                .toString();
        }
    }

    /**
     * Factory interface for creating instances of {@link RestconfStream}.
     *
     * @param <T> {@link RestconfStream} type
     */
    @FunctionalInterface
    public interface StreamFactory<T extends RestconfStream<?>> {
        /**
         * Create a stream with the supplied name.
         *
         * @param name Stream name
         * @return An {@link RestconfStream}
         */
        @NonNull T createStream(@NonNull String name);
    }

    private static final Logger LOG = LoggerFactory.getLogger(ListenersBroker.class);
    private static final YangInstanceIdentifier RESTCONF_STATE_STREAMS = YangInstanceIdentifier.of(
        NodeIdentifier.create(RestconfState.QNAME),
        NodeIdentifier.create(Streams.QNAME),
        NodeIdentifier.create(Stream.QNAME));

    @VisibleForTesting
    static final QName NAME_QNAME =  QName.create(Stream.QNAME, "name").intern();
    @VisibleForTesting
    static final QName DESCRIPTION_QNAME = QName.create(Stream.QNAME, "description").intern();
    @VisibleForTesting
    static final QName ENCODING_QNAME =  QName.create(Stream.QNAME, "encoding").intern();
    @VisibleForTesting
    static final QName LOCATION_QNAME =  QName.create(Stream.QNAME, "location").intern();

    private static final NodeIdentifier DATASTORE_NODEID = NodeIdentifier.create(
        QName.create(CreateDataChangeEventSubscriptionInput1.QNAME, "datastore").intern());
    @Deprecated(forRemoval = true)
    private static final NodeIdentifier OUTPUT_TYPE_NODEID = NodeIdentifier.create(
        QName.create(NotificationOutputTypeGrouping.QNAME, "notification-output-type").intern());
    private static final NodeIdentifier DEVICE_NOTIFICATION_PATH_NODEID =
        NodeIdentifier.create(QName.create(SubscribeDeviceNotificationInput.QNAME, "path").intern());
    private static final NodeIdentifier DEVICE_NOTIFICATION_STREAM_PATH_NODEID =
        NodeIdentifier.create(QName.create(SubscribeDeviceNotificationInput.QNAME, "stream-path").intern());

    private static final NodeIdentifier SAL_REMOTE_OUTPUT_NODEID =
        NodeIdentifier.create(CreateDataChangeEventSubscriptionOutput.QNAME);
    private static final NodeIdentifier NOTIFICATIONS =
        NodeIdentifier.create(QName.create(CreateNotificationStreamInput.QNAME, "notifications").intern());
    private static final NodeIdentifier PATH_NODEID =
        NodeIdentifier.create(QName.create(CreateDataChangeEventSubscriptionInput.QNAME, "path").intern());
    private static final NodeIdentifier STREAM_NAME_NODEID =
        NodeIdentifier.create(QName.create(CreateDataChangeEventSubscriptionOutput.QNAME, "stream-name").intern());

    private final ConcurrentMap<String, RestconfStream<?>> streams = new ConcurrentHashMap<>();
    private final DOMDataBroker dataBroker;

    private ListenersBroker(final DOMDataBroker dataBroker) {
        this.dataBroker = requireNonNull(dataBroker);
    }

    /**
     * Get a {@link RestconfStream} by its name.
     *
     * @param streamName Stream name.
     * @return A {@link RestconfStream}, or {@code null} if the stream with specified name does not exist.
     * @throws NullPointerException if {@code streamName} is {@code null}
     */
    public final @Nullable RestconfStream<?> getStream(final String streamName) {
        return streams.get(streamName);
    }

    /**
     * Create a {@link RestconfStream} with a unique name. This method will atomically generate a stream name, create
     * the corresponding instance and register it.
     *
     * @param <T> Stream type
     * @param baseStreamLocation base streams location
     * @param factory Factory for creating the actual stream instance
     * @return A {@link RestconfStream} instance
     * @throws NullPointerException if {@code factory} is {@code null}
     */
    final <T extends RestconfStream<?>> @NonNull RestconfFuture<T> createStream(final String description,
            final String baseStreamLocation, final StreamFactory<T> factory) {
        String name;
        T stream;
        do {
            // Use Type 4 (random) UUID. While we could just use it as a plain string, be nice to observers and anchor
            // it into UUID URN namespace as defined by RFC4122
            name = "urn:uuid:" + UUID.randomUUID().toString();
            stream = factory.createStream(name);
        } while (streams.putIfAbsent(name, stream) != null);

        // final captures for use with FutureCallback
        final var streamName = name;
        final var finalStream = stream;

        // Now issue a put operation
        final var ret = new SettableRestconfFuture<T>();
        final var tx = dataBroker.newWriteOnlyTransaction();

        tx.put(LogicalDatastoreType.OPERATIONAL, restconfStateStreamPath(streamName),
            streamEntry(streamName, description, baseStreamLocation + '/' + streamName, ""));
        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Stream {} added", streamName);
                ret.set(finalStream);
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.debug("Failed to add stream {}", streamName, cause);
                streams.remove(streamName, finalStream);
                ret.setFailure(new RestconfDocumentedException("Failed to allocate stream " + streamName, cause));
            }
        }, MoreExecutors.directExecutor());
        return ret;
    }

    /**
     * Remove a particular stream and remove its entry from operational datastore.
     *
     * @param stream Stream to remove
     */
    final void removeStream(final RestconfStream<?> stream) {
        // Defensive check to see if we are still tracking the stream
        final var streamName = stream.name();
        if (streams.get(streamName) != stream) {
            LOG.warn("Stream {} does not match expected instance {}, skipping datastore update", streamName, stream);
            return;
        }

        // Now issue a delete operation while the name is still protected by being associated in the map.
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, restconfStateStreamPath(streamName));
        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Stream {} removed", streamName);
                streams.remove(streamName, stream);
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.warn("Failed to remove stream {}, operational datastore may be inconsistent", streamName, cause);
                streams.remove(streamName, stream);
            }
        }, MoreExecutors.directExecutor());
    }

    private static @NonNull YangInstanceIdentifier restconfStateStreamPath(final String streamName) {
        return RESTCONF_STATE_STREAMS.node(NodeIdentifierWithPredicates.of(Stream.QNAME, NAME_QNAME, streamName));
    }

    /**
     * Return the base location URL of the streams service based on request URI.
     *
     * @param uriInfo request URL information
     * @return location URL
     */
    public abstract @NonNull String baseStreamLocation(UriInfo uriInfo);

    /**
     * Create data-change-event stream with POST operation via RPC.
     *
     * @param input Input of RPC - example in JSON (data-change-event stream):
     *              <pre>
     *              {@code
     *                  {
     *                      "input": {
     *                          "path": "/toaster:toaster/toaster:toasterStatus",
     *                          "sal-remote-augment:datastore": "OPERATIONAL",
     *                      }
     *                  }
     *              }
     *              </pre>
     * @param modelContext Reference to {@link EffectiveModelContext}.
     * @return {@link DOMRpcResult} - Output of RPC - example in JSON:
     *     <pre>
     *     {@code
     *         {
     *             "output": {
     *                 "stream-name": "toaster:toaster/toaster:toasterStatus/datastore=OPERATIONAL/scope=ONE"
     *             }
     *         }
     *     }
     *     </pre>
     */
    // FIXME: this really should be a normal RPC implementation
    public final RestconfFuture<Optional<ContainerNode>> createDataChangeNotifiStream(
            final DatabindProvider databindProvider, final UriInfo uriInfo, final ContainerNode input,
            final EffectiveModelContext modelContext) {
        final var datastoreName = extractStringLeaf(input, DATASTORE_NODEID);
        final var datastore = datastoreName != null ? LogicalDatastoreType.valueOf(datastoreName)
            : LogicalDatastoreType.CONFIGURATION;
        final var path = preparePath(input);

        final var outputType = prepareOutputType(input);
        return createStream(
            "Events occuring in " + datastore + " datastore under /" + IdentifierCodec.serialize(path, modelContext),
            baseStreamLocation(uriInfo),
            name -> new DataTreeChangeStream(this, name, outputType, databindProvider, datastore, path))
            .transform(stream -> Optional.of(Builders.containerBuilder()
                .withNodeIdentifier(SAL_REMOTE_OUTPUT_NODEID)
                .withChild(ImmutableNodes.leafNode(STREAM_NAME_NODEID, stream.name()))
                .build()));
    }

// FIXME: NETCONF-1102: this part needs to be invoked from subscriber
//    /**
//     * Register listener by streamName in identifier to listen to data change notifications, and put or delete
//     * information about listener to DS according to ietf-restconf-monitoring.
//     *
//     * @param identifier              Identifier as stream name.
//     * @param uriInfo                 Base URI information.
//     * @param notificationQueryParams Query parameters of notification.
//     * @param handlersHolder          Holder of handlers for notifications.
//     * @return Location for listening.
//     */
//    public final URI subscribeToDataStream(final String identifier, final UriInfo uriInfo,
//            final ReceiveEventsParams notificationQueryParams, final HandlersHolder handlersHolder) {
//        final var streamName = createStreamNameFromUri(identifier);
//        final var listener = dataChangeListenerFor(streamName);
//        if (listener == null) {
//            throw new RestconfDocumentedException("No listener found for stream " + streamName,
//                ErrorType.APPLICATION, ErrorTag.DATA_MISSING);
//        }
//
//        listener.setQueryParams(notificationQueryParams);
//        listener.listen(dataBroker);
//    }

    // FIXME: this really should be a normal RPC implementation
    public final RestconfFuture<Optional<ContainerNode>> createNotificationStream(
            final DatabindProvider databindProvider, final UriInfo uriInfo, final ContainerNode input,
            final EffectiveModelContext modelContext) {
        final var qnames = ((LeafSetNode<String>) input.getChildByArg(NOTIFICATIONS)).body().stream()
            .map(LeafSetEntryNode::body)
            .map(QName::create)
            .sorted()
            .collect(ImmutableSet.toImmutableSet());

        final var description = new StringBuilder("YANG notifications matching any of {");
        var haveFirst = false;
        for (var qname : qnames) {
            final var module = modelContext.findModuleStatement(qname.getModule())
                .orElseThrow(() -> new RestconfDocumentedException(qname + " refers to an unknown module",
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE));
            final var stmt = module.findSchemaTreeNode(qname)
                .orElseThrow(() -> new RestconfDocumentedException(qname + " refers to an unknown notification",
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE));
            if (!(stmt instanceof NotificationEffectiveStatement)) {
                throw new RestconfDocumentedException(qname + " refers to a non-notification",
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE);
            }

            if (haveFirst) {
                description.append(",\n");
            } else {
                haveFirst = true;
            }
            description.append("\n  ")
                .append(module.argument().getLocalName()).append(':').append(qname.getLocalName());
        }
        description.append("\n}");

        // registration of the listener
        final var outputType = prepareOutputType(input);
        return createStream(description.toString(), baseStreamLocation(uriInfo),
            name -> new NotificationStream(this, name, outputType, databindProvider, qnames))
            .transform(stream -> Optional.of(Builders.containerBuilder()
                .withNodeIdentifier(SAL_REMOTE_OUTPUT_NODEID)
                .withChild(ImmutableNodes.leafNode(STREAM_NAME_NODEID, stream.name()))
                .build()));
    }

    /**
     * Create device notification stream.
     *
     * @param input RPC input
     * @param mountPointService dom mount point service
     * @return {@link DOMRpcResult} - Output of RPC - example in JSON
     */
    // FIXME: this should be an RPC invocation
    public final RestconfFuture<Optional<ContainerNode>> createDeviceNotificationStream(final UriInfo uriInfo,
            final ContainerNode input, final EffectiveModelContext modelContext,
            final DOMMountPointService mountPointService) {
        // parsing out of container with settings and path
        // FIXME: ugly cast
        final var path = (YangInstanceIdentifier) input.findChildByArg(DEVICE_NOTIFICATION_PATH_NODEID)
                .map(DataContainerChild::body)
                .orElseThrow(() -> new RestconfDocumentedException("No path specified", ErrorType.APPLICATION,
                    ErrorTag.DATA_MISSING));

        if (!(path.getLastPathArgument() instanceof NodeIdentifierWithPredicates listId)) {
            throw new RestconfDocumentedException("Path does not refer to a list item", ErrorType.APPLICATION,
                ErrorTag.INVALID_VALUE);
        }
        if (listId.size() != 1) {
            throw new RestconfDocumentedException("Target list uses multiple keys", ErrorType.APPLICATION,
                ErrorTag.INVALID_VALUE);
        }

        final DOMMountPoint mountPoint = mountPointService.getMountPoint(path)
            .orElseThrow(() -> new RestconfDocumentedException("Mount point not available", ErrorType.APPLICATION,
                ErrorTag.OPERATION_FAILED));

        final DOMNotificationService mountNotifService = mountPoint.getService(DOMNotificationService.class)
            .orElseThrow(() -> new RestconfDocumentedException("Mount point does not support notifications",
                ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED));

        final var mountModelContext = mountPoint.getService(DOMSchemaService.class)
            .orElseThrow(() -> new RestconfDocumentedException("Mount point schema not available",
                ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED))
            .getGlobalContext();
        final var notificationPaths = mountModelContext.getModuleStatements().values().stream()
            .flatMap(module -> module.streamEffectiveSubstatements(NotificationEffectiveStatement.class))
            .map(notification -> Absolute.of(notification.argument()))
            .collect(ImmutableSet.toImmutableSet());
        if (notificationPaths.isEmpty()) {
            throw new RestconfDocumentedException("Device does not support notification", ErrorType.APPLICATION,
                ErrorTag.OPERATION_FAILED);
        }

        final var baseStreamsUri = baseStreamLocation(uriInfo);
        final var outputType = prepareOutputType(input);
        return createStream(
            "All YANG notifications occuring on mount point /" + IdentifierCodec.serialize(path, modelContext),
            baseStreamsUri,
            streamName -> new DeviceNotificationStream(this, streamName, outputType, mountModelContext,
                mountPointService, mountPoint.getIdentifier()))
            .transform(stream -> {
                stream.listen(mountNotifService, notificationPaths);
                return Optional.of(Builders.containerBuilder()
                    .withNodeIdentifier(new NodeIdentifier(SubscribeDeviceNotificationOutput.QNAME))
                    .withChild(ImmutableNodes.leafNode(DEVICE_NOTIFICATION_STREAM_PATH_NODEID,
                        baseStreamsUri + '/' + stream.name()))
                    .build());
            });
    }

    /**
     * Prepare {@link NotificationOutputType}.
     *
     * @param data Container with stream settings (RPC create-stream).
     * @return Parsed {@link NotificationOutputType}.
     */
    @Deprecated(forRemoval = true)
    private static NotificationOutputType prepareOutputType(final ContainerNode data) {
        final String outputName = extractStringLeaf(data, OUTPUT_TYPE_NODEID);
        return outputName != null ? NotificationOutputType.valueOf(outputName) : NotificationOutputType.XML;
    }

    /**
     * Prepare {@link YangInstanceIdentifier} of stream source.
     *
     * @param data Container with stream settings (RPC create-stream).
     * @return Parsed {@link YangInstanceIdentifier} of data element from which the data-change-event notifications
     *         are going to be generated.
     */
    private static YangInstanceIdentifier preparePath(final ContainerNode data) {
        final var pathLeaf = data.childByArg(PATH_NODEID);
        if (pathLeaf != null && pathLeaf.body() instanceof YangInstanceIdentifier pathValue) {
            return pathValue;
        }

        throw new RestconfDocumentedException("Instance identifier was not normalized correctly",
            ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED);
    }

    private static @Nullable String extractStringLeaf(final ContainerNode data, final NodeIdentifier childName) {
        return data.childByArg(childName) instanceof LeafNode<?> leafNode && leafNode.body() instanceof String str
            ? str : null;
    }

    @VisibleForTesting
    static @NonNull MapEntryNode streamEntry(final String name, final String description, final String location,
            final String outputType) {
        return Builders.mapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(Stream.QNAME, NAME_QNAME, name))
            .withChild(ImmutableNodes.leafNode(NAME_QNAME, name))
            .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, description))
            .withChild(createAccessList(outputType, location))
            .build();
    }

    private static MapNode createAccessList(final String outputType, final String location) {
        return Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(Access.QNAME))
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(Access.QNAME, ENCODING_QNAME, outputType))
                .withChild(ImmutableNodes.leafNode(ENCODING_QNAME, outputType))
                .withChild(ImmutableNodes.leafNode(LOCATION_QNAME, location))
                .build())
            .build();
    }
}
