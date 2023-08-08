/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodes;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class XmlBodyReaderTest extends AbstractBodyReaderTest {
    private static final QName TOP_LEVEL_LIST = QName.create("foo", "2017-08-09", "top-level-list");
    private static final MediaType MEDIA_TYPE = new MediaType(MediaType.APPLICATION_XML, null);

    private static EffectiveModelContext schemaContext;

    private final XmlNormalizedNodeBodyReader xmlBodyReader;

    public XmlBodyReaderTest() {
        super(schemaContext);
        xmlBodyReader = new XmlNormalizedNodeBodyReader(databindProvider, mountPointService);
    }

    @BeforeClass
    public static void initialization() throws Exception {
        final var testFiles = loadFiles("/instanceidentifier/yang");
        testFiles.addAll(loadFiles("/modules"));
        testFiles.addAll(loadFiles("/foo-xml-test/yang"));
        schemaContext = YangParserTestUtils.parseYangFiles(testFiles);
    }

    @Test
    public void postXmlTest() throws Exception {
        mockPostBodyReader("", xmlBodyReader);
        runXmlTest();
    }

    private void runXmlTest() throws Exception {
        final NormalizedNodePayload payload = xmlBodyReader.readFrom(null, null, null, MEDIA_TYPE, null,
            XmlBodyReaderTest.class.getResourceAsStream("/foo-xml-test/foo.xml"));
        assertNotNull(payload);

        final InstanceIdentifierContext iid = payload.getInstanceIdentifierContext();
        assertEquals(YangInstanceIdentifier.of(
            new NodeIdentifier(TOP_LEVEL_LIST),
            NodeIdentifierWithPredicates.of(TOP_LEVEL_LIST, QName.create(TOP_LEVEL_LIST, "key-leaf"), "key-value")),
            iid.getInstanceIdentifier());

        assertThat(payload.getData(), instanceOf(MapEntryNode.class));
        final MapEntryNode data = (MapEntryNode) payload.getData();
        assertEquals(2, data.size());
        for (final DataContainerChild child : data.body()) {
            switch (child.name().getNodeType().getLocalName()) {
                case "key-leaf":
                    assertEquals("key-value", child.body());
                    break;
                case "ordinary-leaf":
                    assertEquals("leaf-value", child.body());
                    break;
                default:
                    fail();
            }
        }
    }

    @Test
    public void moduleSubContainerDataPostTest() throws Exception {
        final DataSchemaNode dataSchemaNode = schemaContext
                .getDataChildByName(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
        final QName cont1QName = QName.create(dataSchemaNode.getQName(), "cont1");
        final YangInstanceIdentifier dataII = YangInstanceIdentifier.of(dataSchemaNode.getQName()).node(cont1QName);
        final String uri = "instance-identifier-module:cont";
        mockPostBodyReader(uri, xmlBodyReader);
        final NormalizedNodePayload payload = xmlBodyReader.readFrom(null, null, null, MEDIA_TYPE, null,
            XmlBodyReaderTest.class.getResourceAsStream("/instanceidentifier/xml/xml_sub_container.xml"));
        checkNormalizedNodePayload(payload);
        checkExpectValueNormalizeNodeContext(dataSchemaNode, payload, dataII);
    }

    @Test
    public void moduleSubContainerDataPostActionTest() throws Exception {
        final var dataSchemaNode = schemaContext
            .getDataChildByName(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
        final QName cont1QName = QName.create(dataSchemaNode.getQName(), "cont1");
        final QName actionQName = QName.create(dataSchemaNode.getQName(), "reset");
        final YangInstanceIdentifier dataII = YangInstanceIdentifier.of(dataSchemaNode.getQName())
            .node(cont1QName).node(actionQName);
        final String uri = "instance-identifier-module:cont/cont1/reset";
        mockPostBodyReader(uri, xmlBodyReader);
        final NormalizedNodePayload payload = xmlBodyReader.readFrom(null, null, null, MEDIA_TYPE, null,
            XmlBodyReaderTest.class.getResourceAsStream("/instanceidentifier/xml/xml_cont_action.xml"));
        checkNormalizedNodePayload(payload);
        assertThat(payload.getInstanceIdentifierContext().getSchemaNode(), instanceOf(ActionDefinition.class));
    }

    @Test
    public void moduleSubContainerAugmentDataPostTest() throws Exception {
        final DataSchemaNode dataSchemaNode = schemaContext
                .getDataChildByName(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
        final Module augmentModule = schemaContext.findModules(XMLNamespace.of("augment:module")).iterator().next();
        final QName contAugmentQName = QName.create(augmentModule.getQNameModule(), "cont-augment");
        final YangInstanceIdentifier dataII = YangInstanceIdentifier.of(dataSchemaNode.getQName(), contAugmentQName);
        final String uri = "instance-identifier-module:cont";
        mockPostBodyReader(uri, xmlBodyReader);
        final NormalizedNodePayload payload = xmlBodyReader.readFrom(null, null, null, MEDIA_TYPE, null,
            XmlBodyReaderTest.class.getResourceAsStream("/instanceidentifier/xml/xml_augment_container.xml"));
        checkNormalizedNodePayload(payload);
        checkExpectValueNormalizeNodeContext(dataSchemaNode, payload, dataII);
    }

    @Test
    public void moduleSubContainerChoiceAugmentDataPostTest() throws Exception {
        final DataSchemaNode dataSchemaNode = schemaContext
                .getDataChildByName(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
        final Module augmentModule = schemaContext.findModules(XMLNamespace.of("augment:module")).iterator().next();
        final QName augmentChoice1QName = QName.create(augmentModule.getQNameModule(), "augment-choice1");
        final QName augmentChoice2QName = QName.create(augmentChoice1QName, "augment-choice2");
        final YangInstanceIdentifier dataII = YangInstanceIdentifier.of(dataSchemaNode.getQName())
            .node(augmentChoice1QName)
            .node(augmentChoice2QName)
            .node(QName.create(augmentChoice1QName, "case-choice-case-container1"));
        final String uri = "instance-identifier-module:cont";
        mockPostBodyReader(uri, xmlBodyReader);
        final NormalizedNodePayload payload = xmlBodyReader.readFrom(null, null, null, MEDIA_TYPE, null,
            XmlBodyReaderTest.class.getResourceAsStream("/instanceidentifier/xml/xml_augment_choice_container.xml"));
        checkNormalizedNodePayload(payload);
        checkExpectValueNormalizeNodeContext(dataSchemaNode, payload, dataII);
    }

    private static void checkExpectValueNormalizeNodeContext(final DataSchemaNode dataSchemaNode,
            final NormalizedNodePayload nnContext, final YangInstanceIdentifier dataNodeIdent) {
        assertEquals(dataSchemaNode, nnContext.getInstanceIdentifierContext().getSchemaNode());
        assertEquals(dataNodeIdent, nnContext.getInstanceIdentifierContext().getInstanceIdentifier());
        assertNotNull(NormalizedNodes.findNode(nnContext.getData(), dataNodeIdent));
    }

    /**
     * Test when container with the same name is placed in two modules
     * (foo-module and bar-module). Namespace must be used to distinguish
     * between them to find correct one. Check if container was found not only
     * according to its name but also by correct namespace used in payload.
     */
    @Test
    public void findFooContainerUsingNamespaceTest() throws Exception {
        mockPostBodyReader("", xmlBodyReader);
        final NormalizedNodePayload payload = xmlBodyReader.readFrom(null, null, null, MEDIA_TYPE, null,
            XmlBodyReaderTest.class.getResourceAsStream("/instanceidentifier/xml/xmlDataFindFooContainer.xml"));

        // check return value
        checkNormalizedNodePayload(payload);
        // check if container was found both according to its name and namespace
        final var payloadNodeType = payload.getData().name().getNodeType();
        assertEquals("foo-bar-container", payloadNodeType.getLocalName());
        assertEquals("foo:module", payloadNodeType.getNamespace().toString());
    }

    /**
     * Test when container with the same name is placed in two modules
     * (foo-module and bar-module). Namespace must be used to distinguish
     * between them to find correct one. Check if container was found not only
     * according to its name but also by correct namespace used in payload.
     */
    @Test
    public void findBarContainerUsingNamespaceTest() throws Exception {
        mockPostBodyReader("", xmlBodyReader);
        final NormalizedNodePayload payload = xmlBodyReader.readFrom(null, null, null, MEDIA_TYPE, null,
            XmlBodyReaderTest.class.getResourceAsStream("/instanceidentifier/xml/xmlDataFindBarContainer.xml"));

        // check return value
        checkNormalizedNodePayload(payload);
        // check if container was found both according to its name and namespace
        final var payloadNodeType = payload.getData().name().getNodeType();
        assertEquals("foo-bar-container", payloadNodeType.getLocalName());
        assertEquals("bar:module", payloadNodeType.getNamespace().toString());
    }
}
