package org.ndexbio.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.servlet.http.HttpServletRequest;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentPool;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.FramedGraphFactory;
import com.tinkerpop.frames.modules.gremlingroovy.GremlinGroovyModule;
import com.tinkerpop.frames.modules.typedgraph.TypedGraphModuleBuilder;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.ndexbio.common.models.object.*;
import org.ndexbio.common.models.data.*;
import org.ndexbio.rest.services.NetworkService;

public class NetworkFromExcelTest
{
    private static FramedGraphFactory _graphFactory = null;
    private static ODatabaseDocumentTx _ndexDatabase = null;
    private static FramedGraph<OrientBaseGraph> _orientDbGraph = null;
    private static Integer _jdexId = 0;

    private static final HttpServletRequest _mockRequest = EasyMock.createMock(HttpServletRequest.class);
    private static final NetworkService _networkService = new NetworkService(_mockRequest);
    private static final Properties _testProperties = new Properties();

    

    @BeforeClass
    public static void initializeTests() throws Exception
    {
        final InputStream propertiesStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("ndex.properties");
        _testProperties.load(propertiesStream);

        try
        {
            _graphFactory = new FramedGraphFactory(new GremlinGroovyModule(),
                new TypedGraphModuleBuilder()
                    .withClass(IGroup.class)
                    .withClass(IUser.class)
                    .withClass(IGroupMembership.class)
                    .withClass(INetworkMembership.class)
                    .withClass(IGroupInvitationRequest.class)
                    .withClass(IJoinGroupRequest.class)
                    .withClass(INetworkAccessRequest.class)
                    .withClass(IBaseTerm.class)
                    .withClass(IFunctionTerm.class)
                    .build());
            
            _ndexDatabase = ODatabaseDocumentPool.global().acquire("remote:localhost/ndex", "admin", "admin");
            _orientDbGraph = _graphFactory.create((OrientBaseGraph)new OrientGraph(_ndexDatabase));
            NdexSchemaManager.INSTANCE.init(_orientDbGraph.getBaseGraph());

            final User loggedInUser = getUser("dexterpratt");
            setLoggedInUser(loggedInUser);
        }
        catch (Exception e)
        {
            Assert.fail("Failed to initialize database. Cause: " + e.getMessage());
            e.printStackTrace();
        }
    }



    @Test
    public void createExcelNetwork()
    {
        try
        {
            final URL testNetworkUrl = getClass().getResource("/resources/large-excel-network.xls");
            final File file = new File(testNetworkUrl.toURI());
            final FileInputStream networkFileStream = new FileInputStream(file);
    
            final HSSFWorkbook excelWorkbook = new HSSFWorkbook(networkFileStream);
    
            final Network networkToCreate = deserializeNetwork(excelWorkbook.getSheetAt(0));
            networkToCreate.setTitle("Example Protein Interactions");
            networkToCreate.setSource("Ideker Lab");
    
            final Network newNetwork = _networkService.createNetwork(networkToCreate);
            Assert.assertNotNull(newNetwork);
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    
    
    private Network deserializeNetwork(HSSFSheet sheet)
    {
        final Network network = new Network();
        final Iterator<Row> rowIterator = sheet.iterator();
        rowIterator.next();
        network.setFormat("SIF");

        final Map<String, BaseTerm> termMap = new HashMap<String, BaseTerm>();
        final Map<String, Node> nodeMap = new HashMap<String, Node>();

        while (rowIterator.hasNext())
        {
            final Row row = rowIterator.next();
            final String subjectIdentifier = getCellText(row.getCell(0));
            final String predicateIdentifier = getCellText(row.getCell(1));
            final String objectIdentifier = getCellText(row.getCell(2));

            if (!subjectIdentifier.isEmpty() && !predicateIdentifier.isEmpty() && !objectIdentifier.isEmpty())
            {
                final BaseTerm predicate = findOrCreateBaseTerm(predicateIdentifier, termMap, network);
                final BaseTerm subjectTerm = findOrCreateBaseTerm(subjectIdentifier, termMap, network);
                final Node subjectNode = findOrCreateNode(subjectTerm, nodeMap, network);
                final BaseTerm objectTerm = findOrCreateBaseTerm(objectIdentifier, termMap, network);
                final Node objectNode = findOrCreateNode(objectTerm, nodeMap, network);
                
                createEdge(subjectNode, predicate, objectNode, network);
            }
        }

        return network;
    }

    private String getCellText(Cell cell)
    {
        switch (cell.getCellType())
        {
            case Cell.CELL_TYPE_BOOLEAN:
                return Boolean.toString(cell.getBooleanCellValue());
            case Cell.CELL_TYPE_NUMERIC:
                return Double.toString(cell.getNumericCellValue());
            case Cell.CELL_TYPE_STRING:
                return cell.getStringCellValue();
        }
        
        return "";
    }

    private void createEdge(Node subjectNode, Term predicate, Node objectNode, Network network)
    {
        final Edge newEdge = new Edge();
        newEdge.setObjectId(objectNode.getId());
        newEdge.setPredicateId(predicate.getId());
        newEdge.setSubjectId(subjectNode.getId());
        
        _jdexId++;
        newEdge.setId(_jdexId.toString());
        
        network.getEdges().put(newEdge.getId(), newEdge);
    }

    private Node findOrCreateNode(BaseTerm term, Map<String, Node> nodeMap, Network network)
    {
        Node newNode = nodeMap.get(term.getName());
        if (newNode != null)
            return newNode;
        
        _jdexId++;
        newNode = new Node();
        newNode.setId(_jdexId.toString());
        newNode.setRepresents(term.getId());
        newNode.setName(term.getName());
        
        network.getNodes().put(newNode.getId(), newNode);
        nodeMap.put(newNode.getName(), newNode);
        
        return newNode;
    }

    private BaseTerm findOrCreateBaseTerm(String identifier, Map<String, BaseTerm> termMap, Network network)
    {
        BaseTerm newTerm = termMap.get(identifier);
        if (newTerm != null)
            return newTerm;
        
        _jdexId++;
        newTerm = new BaseTerm();
        newTerm.setId(_jdexId.toString());
        newTerm.setName(identifier);
        network.getTerms().put(newTerm.getId(), newTerm);
        termMap.put(identifier, newTerm);
        return newTerm;
    }

    private static User getUser(final String username)
    {
        final List<ODocument> matchingUsers = _ndexDatabase.query(new OSQLSynchQuery<Object>("select from User where username = '" + username + "'"));
        if (!matchingUsers.isEmpty())
            return new User(_orientDbGraph.getVertex(matchingUsers.get(0), IUser.class), true);
        else
            return null;
    }
    
    private static void setLoggedInUser(final User loggedInUser)
    {
        EasyMock.expect(_mockRequest.getAttribute("User"))
        .andReturn(loggedInUser)
        .anyTimes();

        EasyMock.replay(_mockRequest);
    }
}
